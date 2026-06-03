package com.medkernel.engine.integration.fhir;

import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Pattern;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.medkernel.engine.context.CanonicalResource;
import com.medkernel.engine.context.CanonicalResourceRepository;
import com.medkernel.engine.context.ClinicalEventRequest;
import com.medkernel.engine.context.ClinicalEventService;
import com.medkernel.engine.context.ClinicalEventType;
import com.medkernel.engine.context.QualityStatus;
import com.medkernel.engine.integration.domain.IntegrationAdapter;
import com.medkernel.engine.integration.dto.IntegrationOutboundRequestDto;
import com.medkernel.engine.integration.dto.IntegrationOutboundResultDto;
import com.medkernel.engine.integration.repository.IntegrationAdapterRepository;
import com.medkernel.engine.integration.repository.IntegrationWebhookConfigRepository;
import com.medkernel.engine.integration.service.IntegrationService;
import com.medkernel.shared.context.OrgScope;
import com.medkernel.shared.context.RequestContext;
import com.medkernel.shared.runtime.task.RuntimeTaskMode;
import com.medkernel.shared.runtime.task.RuntimeTaskResponse;
import com.medkernel.shared.runtime.task.RuntimeTaskService;
import com.medkernel.shared.runtime.task.RuntimeTaskSubmitRequest;

/**
 * OPT-01 FHIR 运行门面编排服务。
 *
 * <p>只做协议转换、安全准入、标准资源落库和临床事件回流；不直写医嘱、病历、
 * 法定上报、支付或设备控制。
 */
@Service
public class FhirFacadeService {

    private static final String ACTIVE = "ACTIVE";
    private static final String PROTOCOL_FHIR = "FHIR";
    private static final String STATUS_NOT_CONNECTED = "NOT_CONNECTED";
    private static final String TASK_PHYSICIAN_CONFIRMATION = "FHIR_PHYSICIAN_CONFIRMATION";
    private static final long SIGNATURE_MAX_SKEW_SECONDS = 300L;
    private static final Pattern SAFE_ID = Pattern.compile("[^A-Za-z0-9._-]+");

    private final FhirR4CanonicalMapper r4Mapper;
    private final FhirR5CanonicalMapper r5Mapper;
    private final FhirCapabilityStatementService capabilities;
    private final FhirOperationOutcomeFactory outcomes;
    private final CanonicalResourceRepository resources;
    private final FhirResourceMappingRepository mappings;
    private final IntegrationAdapterRepository adapters;
    private final IntegrationWebhookConfigRepository webhookSecrets;
    private final ClinicalEventService events;
    private final IntegrationService integration;
    private final RuntimeTaskService tasks;
    private final ObjectMapper json;

    public FhirFacadeService(FhirR4CanonicalMapper r4Mapper,
                             FhirR5CanonicalMapper r5Mapper,
                             FhirCapabilityStatementService capabilities,
                             FhirOperationOutcomeFactory outcomes,
                             CanonicalResourceRepository resources,
                             FhirResourceMappingRepository mappings,
                             IntegrationAdapterRepository adapters,
                             IntegrationWebhookConfigRepository webhookSecrets,
                             ClinicalEventService events,
                             IntegrationService integration,
                             RuntimeTaskService tasks,
                             ObjectMapper json) {
        this.r4Mapper = r4Mapper;
        this.r5Mapper = r5Mapper;
        this.capabilities = capabilities;
        this.outcomes = outcomes;
        this.resources = resources;
        this.mappings = mappings;
        this.adapters = adapters;
        this.webhookSecrets = webhookSecrets;
        this.events = events;
        this.integration = integration;
        this.tasks = tasks;
        this.json = json;
    }

    public JsonNode metadata(FhirVersion version) {
        return capabilities.runtimeCapability(version);
    }

    @Transactional
    public FhirFacadeResponse create(FhirFacadeCreateCommand command) {
        String tenantId = currentTenant();
        String resourceType = canonicalResourceType(command);
        String fhirId = text(command.resource().path("id"));
        if (fhirId.isBlank()) {
            return error(HttpStatus.BAD_REQUEST, "invalid", "FHIR " + resourceType + ".id 不能为空");
        }

        SecurityDecision security = verifySecurity(tenantId, command);
        if (!security.allowed()) {
            return error(security.status(), security.code(), security.message());
        }

        if (isHighRiskWrite(resourceType)) {
            return createPhysicianConfirmationTask(command, security.adapter(), resourceType, fhirId);
        }
        if (!"Observation".equals(resourceType)) {
            return error(HttpStatus.BAD_REQUEST, "not-supported",
                "OPT-01 PR3 运行门面暂未开放该 FHIR create 资源: " + resourceType);
        }

        return createObservation(tenantId, command, security.adapter(), fhirId);
    }

    private FhirFacadeResponse createObservation(String tenantId,
                                                 FhirFacadeCreateCommand command,
                                                 IntegrationAdapter adapter,
                                                 String fhirId) {
        String snapshotId = firstNonBlank(command.snapshotId(), stableId(command.version(), "Observation", fhirId));
        String traceId = RequestContext.currentTraceId();
        String patientId;
        String encounterId;
        try {
            patientId = patientId(command.resource());
            encounterId = encounterId(command.resource());
        } catch (IllegalArgumentException ex) {
            return error(HttpStatus.BAD_REQUEST, "invalid", ex.getMessage());
        }
        String packageVersion = firstNonBlank(command.packageVersion(), fhirConfig(adapter).defaultPackageVersion());
        if (packageVersion.isBlank()) {
            return error(HttpStatus.BAD_REQUEST, "invalid",
                "FHIR Observation create 必须提供 packageVersion 或适配器默认包版本");
        }
        Optional<FhirResourceMapping> existing = mappings.findByTenantIdAndFhirVersionAndFhirResourceTypeAndFhirId(
            tenantId, command.version(), "Observation", fhirId);
        if (existing.isPresent()) {
            ObjectNode idempotent = outcome(List.of(new FhirOperationOutcomeIssue(
                "information", "informational",
                "FHIR Observation create 已按幂等键返回既有映射，不重复写入")));
            idempotent.put("idempotentReplay", true);
            idempotent.put("mappingId", String.valueOf(existing.get().id()));
            return new FhirFacadeResponse(HttpStatus.OK, idempotent);
        }

        CanonicalResourceMappingResult mapped;
        try {
            mapped = mapper(command.version()).from(new FhirCanonicalMappingRequest(
                tenantId, snapshotId, 0, traceId, Instant.now(), command.resource()));
        } catch (RuntimeException ex) {
            return error(HttpStatus.BAD_REQUEST, "invalid", ex.getMessage());
        }

        CanonicalResource saved = resources.save(withStableResourceId(
            mapped.resource(), stableId(command.version(), "Observation", fhirId), snapshotId));
        FhirResourceMapping mapping = mappings.save(new FhirResourceMapping(
            null,
            tenantId,
            orgPath(RequestContext.currentOrgScope()),
            command.version(),
            "Observation",
            fhirId,
            saved.id(),
            saved.resourceType(),
            mapped.mappingRate(),
            mapped.issues().size(),
            mapped.issues().isEmpty() ? FhirMappingStatus.ACTIVE : FhirMappingStatus.UNMAPPED_WARNING,
            traceId,
            Instant.now(),
            RequestContext.currentUserId().orElse("system"),
            Instant.now(),
            RequestContext.currentUserId().orElse("system")
        ));

        events.receiveAsync(new ClinicalEventRequest(
            "evt-" + stableId(command.version(), "Observation", fhirId),
            ClinicalEventType.REPORT,
            patientId,
            encounterId,
            "FHIR_" + command.version().name(),
            packageVersion,
            eventPayload(saved, mapping, command),
            mapped.resource().eventTime() == null ? Instant.now() : mapped.resource().eventTime()));

        IntegrationOutboundResultDto outbound = integration.enqueueOutboundMessage(tenantId, new IntegrationOutboundRequestDto(
            stableId(command.version(), "Observation", fhirId),
            traceId,
            adapter.adapterId(),
            adapter.name(),
            PROTOCOL_FHIR,
            "FHIR Observation create 已回流标准引擎并登记外部补偿",
            outboundPayload(saved, mapping, command),
            3
        ));

        ObjectNode body = outcome(merge(mapped.issues(), new FhirOperationOutcomeIssue(
            "information", "informational",
            "FHIR Observation 已保存为标准资源并进入临床事件引擎；总线状态 " + outbound.status())));
        body.put("canonicalResourceId", String.valueOf(saved.id()));
        body.put("fhirMappingId", String.valueOf(mapping.id()));
        body.put("integrationStatus", outbound.status());
        body.put("desensitized", fhirConfig(adapter).desensitizeResponse());
        return new FhirFacadeResponse(HttpStatus.CREATED, body);
    }

    private FhirFacadeResponse createPhysicianConfirmationTask(FhirFacadeCreateCommand command,
                                                               IntegrationAdapter adapter,
                                                               String resourceType,
                                                               String fhirId) {
        try {
            patientId(command.resource());
        } catch (IllegalArgumentException ex) {
            return error(HttpStatus.BAD_REQUEST, "invalid", ex.getMessage());
        }
        RuntimeTaskResponse task = tasks.submit(new RuntimeTaskSubmitRequest(
            RuntimeTaskMode.ASYNC,
            TASK_PHYSICIAN_CONFIRMATION,
            writeJson(confirmationPayload(command, adapter, resourceType, fhirId)),
            List.of(),
            3
        ));

        ObjectNode body = outcome(List.of(new FhirOperationOutcomeIssue(
            "information",
            "business-rule",
            "FHIR " + resourceType + " 属于高风险写入，已登记医师确认任务 " + task.taskId()
                + "；门面不自动写医嘱/申请单")));
        body.put("physicianConfirmationTaskId", task.taskId());
        body.put("taskStatus", task.status().name());
        return new FhirFacadeResponse(HttpStatus.ACCEPTED, body);
    }

    private SecurityDecision verifySecurity(String tenantId, FhirFacadeCreateCommand command) {
        if (command.adapterId() == null || command.adapterId().isBlank()) {
            return SecurityDecision.denied(HttpStatus.SERVICE_UNAVAILABLE, "not-connected",
                "FHIR 适配器未指定，状态 " + STATUS_NOT_CONNECTED);
        }
        Optional<IntegrationAdapter> adapter = adapters.findByAdapterIdAndTenantId(command.adapterId(), tenantId);
        if (adapter.isEmpty()) {
            return SecurityDecision.denied(HttpStatus.SERVICE_UNAVAILABLE, "not-connected",
                "FHIR 适配器不存在: " + command.adapterId() + "，状态 " + STATUS_NOT_CONNECTED);
        }
        IntegrationAdapter found = adapter.get();
        if (!ACTIVE.equals(found.status()) || !PROTOCOL_FHIR.equalsIgnoreCase(found.protocolType())) {
            return SecurityDecision.denied(HttpStatus.SERVICE_UNAVAILABLE, "not-connected",
                "FHIR 适配器未启用或协议不是 FHIR，状态 " + STATUS_NOT_CONNECTED);
        }
        FhirAdapterConfig config;
        try {
            config = fhirConfig(found);
        } catch (IllegalArgumentException ex) {
            return SecurityDecision.denied(HttpStatus.SERVICE_UNAVAILABLE, "invalid", ex.getMessage());
        }
        if (!config.enabled()) {
            return SecurityDecision.denied(HttpStatus.SERVICE_UNAVAILABLE, "not-connected",
                "FHIR 门面已关闭，状态 " + STATUS_NOT_CONNECTED);
        }
        if (!config.allowedSourceIps().isEmpty()
            && (command.sourceIp() == null || !config.allowedSourceIps().contains(command.sourceIp()))) {
            return SecurityDecision.denied(HttpStatus.FORBIDDEN, "forbidden",
                "FHIR 来源 IP 不在白名单内");
        }
        if (!freshTimestamp(command.timestamp())) {
            return SecurityDecision.denied(HttpStatus.UNAUTHORIZED, "security",
                "FHIR 签名时间戳缺失或已过期");
        }
        String secretKey = signatureSecretKey(tenantId, config);
        if (secretKey.isBlank()) {
            return SecurityDecision.denied(HttpStatus.SERVICE_UNAVAILABLE, "not-connected",
                "FHIR 适配器签名密钥引用不存在，状态 " + STATUS_NOT_CONNECTED);
        }
        if (!signatureValid(command.timestamp(), command.signature(), command.resource(), secretKey)) {
            return SecurityDecision.denied(HttpStatus.UNAUTHORIZED, "security",
                "FHIR 消息签名校验失败");
        }
        return SecurityDecision.allowed(found);
    }

    private FhirAdapterConfig fhirConfig(IntegrationAdapter adapter) {
        try {
            JsonNode root = adapter.configJson() == null || adapter.configJson().isBlank()
                ? json.createObjectNode()
                : json.readTree(adapter.configJson());
            JsonNode fhir = root.has("fhir") ? root.path("fhir") : root;
            String signatureWebhookId = firstNonBlank(
                text(fhir.path("signatureWebhookId")),
                text(fhir.path("webhookId")));
            if (signatureWebhookId.isBlank()) {
                throw new IllegalArgumentException("FHIR 适配器缺少签名 webhookId 引用");
            }
            List<String> allowedIps = new ArrayList<>();
            JsonNode ips = fhir.path("allowedSourceIps");
            if (ips instanceof ArrayNode array) {
                array.forEach(ip -> {
                    String value = text(ip);
                    if (!value.isBlank()) {
                        allowedIps.add(value);
                    }
                });
            }
            return new FhirAdapterConfig(
                fhir.path("enabled").asBoolean(true),
                signatureWebhookId,
                List.copyOf(allowedIps),
                text(fhir.path("defaultPackageVersion")),
                fhir.path("desensitizeResponse").asBoolean(true));
        } catch (JsonProcessingException ex) {
            throw new IllegalArgumentException("FHIR 适配器配置不是合法 JSON", ex);
        }
    }

    private VersionedMapper mapper(FhirVersion version) {
        return switch (version) {
            case R4 -> r4Mapper::fromR4;
            case R5 -> r5Mapper::fromR5;
        };
    }

    private ObjectNode eventPayload(CanonicalResource saved, FhirResourceMapping mapping, FhirFacadeCreateCommand command) {
        ObjectNode payload = json.createObjectNode();
        payload.put("source", "FHIR");
        payload.put("fhirVersion", command.version().name());
        payload.put("fhirResourceType", mapping.fhirResourceType());
        payload.put("fhirId", mapping.fhirId());
        payload.put("canonicalResourceId", String.valueOf(saved.id()));
        payload.put("canonicalResourceType", saved.resourceType().name());
        payload.put("qualityStatus", saved.qualityStatus().name());
        payload.put("mappingStatus", mapping.mappingStatus().name());
        payload.set("canonicalPayload", readJson(saved.resourcePayloadJson()));
        return payload;
    }

    private ObjectNode outboundPayload(CanonicalResource saved, FhirResourceMapping mapping, FhirFacadeCreateCommand command) {
        ObjectNode payload = json.createObjectNode();
        payload.put("messageType", "FHIR_CREATE_COMPENSATION");
        payload.put("fhirVersion", command.version().name());
        payload.put("fhirResourceType", mapping.fhirResourceType());
        payload.put("fhirId", mapping.fhirId());
        payload.put("canonicalResourceId", String.valueOf(saved.id()));
        payload.put("fhirMappingId", String.valueOf(mapping.id()));
        payload.put("degradeStatus", STATUS_NOT_CONNECTED);
        return payload;
    }

    private ObjectNode confirmationPayload(FhirFacadeCreateCommand command,
                                           IntegrationAdapter adapter,
                                           String resourceType,
                                           String fhirId) {
        ObjectNode payload = json.createObjectNode();
        payload.put("source", "FHIR");
        payload.put("adapterId", adapter.adapterId());
        payload.put("fhirVersion", command.version().name());
        payload.put("fhirResourceType", resourceType);
        payload.put("fhirId", fhirId);
        payload.put("patientId", patientId(command.resource()));
        payload.put("policy", "PHYSICIAN_CONFIRMATION_REQUIRED");
        payload.put("safety", "不自动写医嘱/病历/申请单");
        payload.set("resource", command.resource().deepCopy());
        return payload;
    }

    private String signatureSecretKey(String tenantId, FhirAdapterConfig config) {
        return webhookSecrets.findByWebhookIdAndTenantId(config.signatureWebhookId(), tenantId)
            .filter(webhook -> ACTIVE.equals(webhook.status()))
            .map(webhook -> firstNonBlank(webhook.secretKey()))
            .orElse("");
    }

    private String patientId(JsonNode resource) {
        String reference = text(resource.path("subject").path("reference"));
        if (reference.startsWith("Patient/")) {
            String patient = reference.substring("Patient/".length());
            if (!patient.isBlank()) {
                return patient;
            }
        }
        String identifier = text(resource.path("subject").path("identifier").path("value"));
        if (!identifier.isBlank()) {
            return identifier;
        }
        throw new IllegalArgumentException("FHIR create 必须携带 subject.reference=Patient/<mpi> 或 subject.identifier.value");
    }

    private String encounterId(JsonNode resource) {
        String reference = text(resource.path("encounter").path("reference"));
        return reference.startsWith("Encounter/") ? reference.substring("Encounter/".length()) : null;
    }

    private CanonicalResource withStableResourceId(CanonicalResource resource, String resourceId, String snapshotId) {
        return new CanonicalResource(
            resource.id(), resourceId, snapshotId, resource.tenantId(), resource.resourceType(),
            resource.resourcePayloadJson(), resource.sourceSystem(), resource.sourceRecordId(),
            resource.mappedVersion(), resource.eventTime(), resource.receivedTime(),
            resource.qualityStatus() == null ? QualityStatus.PARTIAL : resource.qualityStatus(),
            resource.seqNo(), resource.traceId());
    }

    private ObjectNode outcome(List<FhirOperationOutcomeIssue> issues) {
        return (ObjectNode) outcomes.fromIssues(issues);
    }

    private FhirFacadeResponse error(HttpStatus status, String code, String diagnostics) {
        return new FhirFacadeResponse(status, outcome(List.of(new FhirOperationOutcomeIssue(
            status.is4xxClientError() || status.is5xxServerError() ? "error" : "warning",
            code,
            diagnostics))));
    }

    private List<FhirOperationOutcomeIssue> merge(List<FhirOperationOutcomeIssue> issues,
                                                  FhirOperationOutcomeIssue extra) {
        List<FhirOperationOutcomeIssue> merged = new ArrayList<>(issues == null ? List.of() : issues);
        merged.add(extra);
        return List.copyOf(merged);
    }

    private JsonNode readJson(String payload) {
        try {
            return json.readTree(payload);
        } catch (JsonProcessingException ex) {
            throw new IllegalArgumentException("标准资源 payload 不是合法 JSON", ex);
        }
    }

    private String writeJson(JsonNode payload) {
        try {
            return json.writeValueAsString(payload);
        } catch (JsonProcessingException ex) {
            throw new IllegalArgumentException("FHIR 任务 payload 无法序列化", ex);
        }
    }

    private boolean signatureValid(String timestamp, String signature, JsonNode resource, String secretKey) {
        if (signature == null || signature.isBlank()) {
            return false;
        }
        try {
            String expected = hmacSha256(timestamp.trim() + "." + json.writeValueAsString(resource), secretKey);
            String actual = normalizeSignature(signature);
            return MessageDigest.isEqual(
                expected.getBytes(StandardCharsets.UTF_8),
                actual.getBytes(StandardCharsets.UTF_8));
        } catch (JsonProcessingException | NoSuchAlgorithmException | InvalidKeyException ex) {
            throw new IllegalArgumentException("FHIR 签名校验失败", ex);
        }
    }

    private boolean freshTimestamp(String timestamp) {
        if (timestamp == null || timestamp.isBlank()) {
            return false;
        }
        try {
            long signedAt = Long.parseLong(timestamp.trim());
            long delta = Instant.now().getEpochSecond() - signedAt;
            return delta >= -SIGNATURE_MAX_SKEW_SECONDS && delta <= SIGNATURE_MAX_SKEW_SECONDS;
        } catch (NumberFormatException ex) {
            return false;
        }
    }

    private String hmacSha256(String data, String key) throws NoSuchAlgorithmException, InvalidKeyException {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(key.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        return HexFormat.of().formatHex(mac.doFinal(data.getBytes(StandardCharsets.UTF_8)));
    }

    private String normalizeSignature(String signature) {
        String normalized = signature.trim().toLowerCase(Locale.ROOT);
        return normalized.startsWith("sha256=") ? normalized.substring("sha256=".length()) : normalized;
    }

    private String stableId(FhirVersion version, String resourceType, String fhirId) {
        String raw = "fhir-" + version.name().toLowerCase(Locale.ROOT) + "-"
            + resourceType.toLowerCase(Locale.ROOT) + "-" + fhirId;
        String safe = SAFE_ID.matcher(raw).replaceAll("-");
        if (safe.length() <= 64) {
            return safe;
        }
        return safe.substring(0, 47) + "-" + HexFormat.of().formatHex(sha256(safe)).substring(0, 16);
    }

    private byte[] sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return digest.digest(value.getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("当前 JDK 缺少 SHA-256", ex);
        }
    }

    private String currentTenant() {
        OrgScope scope = RequestContext.currentOrgScope();
        if (scope == null || !scope.hasTenant()) {
            throw new IllegalArgumentException("租户上下文缺失");
        }
        return scope.tenantId();
    }

    private String orgPath(OrgScope scope) {
        if (scope == null) {
            return "";
        }
        return String.join("/",
            nonNull(scope.tenantId()),
            nonNull(scope.groupId()),
            nonNull(scope.hospitalId()),
            nonNull(scope.campusId()),
            nonNull(scope.siteId()),
            nonNull(scope.departmentId()),
            nonNull(scope.specialtyId()));
    }

    private String canonicalResourceType(FhirFacadeCreateCommand command) {
        String type = firstNonBlank(command.resourceType(), text(command.resource().path("resourceType")));
        return switch (type) {
            case "Observation", "MedicationRequest", "ServiceRequest" -> type;
            default -> type;
        };
    }

    private boolean isHighRiskWrite(String resourceType) {
        return "MedicationRequest".equals(resourceType) || "ServiceRequest".equals(resourceType);
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return "";
    }

    private String nonNull(String value) {
        return value == null ? "" : value;
    }

    private String text(JsonNode node) {
        return node == null || node.isMissingNode() || node.isNull() ? "" : node.asText("");
    }

    private record FhirAdapterConfig(
        boolean enabled,
        String signatureWebhookId,
        List<String> allowedSourceIps,
        String defaultPackageVersion,
        boolean desensitizeResponse
    ) {}

    private record SecurityDecision(
        boolean allowed,
        HttpStatus status,
        String code,
        String message,
        IntegrationAdapter adapter
    ) {
        static SecurityDecision allowed(IntegrationAdapter adapter) {
            return new SecurityDecision(true, HttpStatus.OK, "", "", adapter);
        }

        static SecurityDecision denied(HttpStatus status, String code, String message) {
            return new SecurityDecision(false, status, code, message, null);
        }
    }

    @FunctionalInterface
    private interface VersionedMapper {
        CanonicalResourceMappingResult from(FhirCanonicalMappingRequest request);
    }
}
