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
import com.medkernel.engine.context.CanonicalResourceType;
import com.medkernel.engine.context.ClinicalEventRequest;
import com.medkernel.engine.context.ClinicalEventService;
import com.medkernel.engine.context.ClinicalEventTriggerPoint;
import com.medkernel.engine.context.ClinicalEventType;
import com.medkernel.engine.context.QualityStatus;
import com.medkernel.engine.context.canonical.ClinicalSetting;
import com.medkernel.engine.integration.domain.IntegrationAdapter;
import com.medkernel.engine.integration.dto.IntegrationOutboundRequestDto;
import com.medkernel.engine.integration.dto.IntegrationOutboundResultDto;
import com.medkernel.engine.integration.repository.IntegrationAdapterRepository;
import com.medkernel.engine.integration.repository.IntegrationWebhookConfigRepository;
import com.medkernel.engine.integration.service.IntegrationService;
import com.medkernel.engine.integration.service.WebhookSecretCodec;
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
    private final WebhookSecretCodec webhookSecretCodec;

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
                             ObjectMapper json,
                             WebhookSecretCodec webhookSecretCodec) {
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
        this.webhookSecretCodec = webhookSecretCodec;
    }

    public JsonNode metadata(FhirVersion version) {
        return capabilities.runtimeCapability(version);
    }

    @Transactional(readOnly = true)
    public FhirFacadeResponse read(FhirFacadeReadCommand command) {
        String tenantId = currentTenant();
        String resourceType = canonicalResourceType(command.resourceType(), null);
        if (command.id() == null || command.id().isBlank()) {
            return error(HttpStatus.BAD_REQUEST, "invalid", "FHIR " + resourceType + ".id 不能为空");
        }

        SecurityDecision security = verifyAdapterAccess(tenantId, command.adapterId(), command.sourceIp());
        if (!security.allowed()) {
            return error(security.status(), security.code(), security.message());
        }

        Optional<FhirResourceMapping> mapping = mappings.findByTenantIdAndFhirVersionAndFhirResourceTypeAndFhirId(
            tenantId, command.version(), resourceType, command.id());
        if (mapping.isEmpty()) {
            return error(HttpStatus.NOT_FOUND, "not-found", "FHIR " + resourceType + "/" + command.id() + " 未建立标准资源映射");
        }

        Optional<CanonicalResource> canonical = resources.findById(mapping.get().canonicalResourceId())
            .filter(resource -> tenantId.equals(resource.tenantId()));
        if (canonical.isEmpty()) {
            return error(HttpStatus.NOT_FOUND, "not-found", "FHIR 映射指向的标准资源不存在或不属于当前租户");
        }
        return new FhirFacadeResponse(HttpStatus.OK,
            toFhir(command.version(), canonical.get(), mapping.get().fhirResourceType()).resource());
    }

    @Transactional(readOnly = true)
    public FhirFacadeResponse search(FhirFacadeSearchCommand command) {
        String tenantId = currentTenant();
        String resourceType = canonicalResourceType(command.resourceType(), null);
        SecurityDecision security = verifyAdapterAccess(tenantId, command.adapterId(), command.sourceIp());
        if (!security.allowed()) {
            return error(security.status(), security.code(), security.message());
        }

        ArrayNode entries = json.createArrayNode();
        for (FhirResourceMapping mapping : mappings.findByTenantIdAndFhirVersionAndFhirResourceTypeOrderByCreatedAtDesc(
            tenantId, command.version(), resourceType)) {
            resources.findById(mapping.canonicalResourceId())
                .filter(resource -> tenantId.equals(resource.tenantId()))
                .ifPresent(resource -> {
                    ObjectNode entry = json.createObjectNode();
                    entry.set("resource", toFhir(command.version(), resource, mapping.fhirResourceType()).resource());
                    entries.add(entry);
                });
        }

        ObjectNode bundle = json.createObjectNode();
        bundle.put("resourceType", "Bundle");
        bundle.put("type", "searchset");
        bundle.put("total", entries.size());
        bundle.set("entry", entries);
        return new FhirFacadeResponse(HttpStatus.OK, bundle);
    }

    @Transactional
    public FhirFacadeResponse create(FhirFacadeCreateCommand command) {
        String tenantId = currentTenant();
        String resourceType = canonicalResourceType(command.resourceType(), command.resource());
        String fhirId = text(command.resource().path("id"));
        if (fhirId.isBlank()) {
            return error(HttpStatus.BAD_REQUEST, "invalid", "FHIR " + resourceType + ".id 不能为空");
        }

        SecurityDecision security = verifyCreateSecurity(tenantId, command);
        if (!security.allowed()) {
            return error(security.status(), security.code(), security.message());
        }

        if (isHighRiskWrite(resourceType)) {
            return createPhysicianConfirmationTask(command, security.adapter(), resourceType, fhirId);
        }

        return createStandardResource(tenantId, command, security.adapter(), resourceType, fhirId);
    }

    private FhirFacadeResponse createStandardResource(String tenantId,
                                                      FhirFacadeCreateCommand command,
                                                      IntegrationAdapter adapter,
                                                      String resourceType,
                                                      String fhirId) {
        String snapshotId = firstNonBlank(command.snapshotId(), stableId(command.version(), resourceType, fhirId));
        String traceId = RequestContext.currentTraceId();
        String patientId;
        String encounterId;
        try {
            patientId = patientId(resourceType, command.resource());
            encounterId = encounterId(command.resource());
        } catch (IllegalArgumentException ex) {
            return error(HttpStatus.BAD_REQUEST, "invalid", ex.getMessage());
        }
        String packageVersion = firstNonBlank(command.packageVersion(), fhirConfig(adapter).defaultPackageVersion());
        if (packageVersion.isBlank()) {
            return error(HttpStatus.BAD_REQUEST, "invalid",
                "FHIR " + resourceType + " create 必须提供 packageVersion 或适配器默认包版本");
        }
        Optional<FhirResourceMapping> existing = mappings.findByTenantIdAndFhirVersionAndFhirResourceTypeAndFhirId(
            tenantId, command.version(), resourceType, fhirId);
        if (existing.isPresent()) {
            ObjectNode idempotent = outcome(List.of(new FhirOperationOutcomeIssue(
                "information", "informational",
                "FHIR " + resourceType + " create 已按幂等键返回既有映射，不重复写入")));
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
        ClinicalSetting clinicalSetting;
        try {
            clinicalSetting = requireClinicalSetting(mapped.resource(), command);
        } catch (IllegalArgumentException exception) {
            return error(HttpStatus.BAD_REQUEST, "invalid", exception.getMessage());
        }

        CanonicalResource saved = resources.save(withStableResourceId(
            mapped.resource(), stableId(command.version(), resourceType, fhirId), snapshotId));
        FhirResourceMapping mapping = mappings.save(new FhirResourceMapping(
            null,
            tenantId,
            orgPath(RequestContext.currentOrgScope()),
            command.version(),
            resourceType,
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
            "evt-" + stableId(command.version(), resourceType, fhirId),
            eventTypeFor(saved.resourceType()),
            patientId,
            encounterId,
            clinicalSetting,
            "FHIR_" + command.version().name(),
            packageVersion,
            triggerPointFor(saved.resourceType()),
            null,
            null,
            eventPayload(saved, mapping, command),
            mapped.resource().eventTime() == null ? Instant.now() : mapped.resource().eventTime()));

        IntegrationOutboundResultDto outbound = integration.enqueueOutboundMessage(tenantId, new IntegrationOutboundRequestDto(
            stableId(command.version(), resourceType, fhirId),
            traceId,
            adapter.adapterId(),
            adapter.name(),
            PROTOCOL_FHIR,
            "FHIR " + resourceType + " create 已回流标准引擎并登记外部补偿",
            outboundPayload(saved, mapping, command),
            3
        ));

        ObjectNode body = outcome(merge(mapped.issues(), new FhirOperationOutcomeIssue(
            "information", "informational",
            "FHIR " + resourceType + " 已保存为标准资源并进入临床事件引擎；总线状态 " + outbound.status())));
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
            patientId(resourceType, command.resource());
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

    private SecurityDecision verifyCreateSecurity(String tenantId, FhirFacadeCreateCommand command) {
        SecurityDecision access = verifyAdapterAccess(tenantId, command.adapterId(), command.sourceIp());
        if (!access.allowed()) {
            return access;
        }
        FhirAdapterConfig config = fhirConfig(access.adapter());
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
        return access;
    }

    private SecurityDecision verifyAdapterAccess(String tenantId, String adapterId, String sourceIp) {
        if (adapterId == null || adapterId.isBlank()) {
            return SecurityDecision.denied(HttpStatus.SERVICE_UNAVAILABLE, "not-connected",
                "FHIR 适配器未指定，状态 " + STATUS_NOT_CONNECTED);
        }
        Optional<IntegrationAdapter> adapter = adapters.findByAdapterIdAndTenantId(adapterId, tenantId);
        if (adapter.isEmpty()) {
            return SecurityDecision.denied(HttpStatus.SERVICE_UNAVAILABLE, "not-connected",
                "FHIR 适配器不存在: " + adapterId + "，状态 " + STATUS_NOT_CONNECTED);
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
            && (sourceIp == null || !config.allowedSourceIps().contains(sourceIp))) {
            return SecurityDecision.denied(HttpStatus.FORBIDDEN, "forbidden",
                "FHIR 来源 IP 不在白名单内");
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

    private FhirResourceMappingResult toFhir(FhirVersion version,
                                             CanonicalResource canonical,
                                             String requestedResourceType) {
        return switch (version) {
            case R4 -> r4Mapper.toR4(canonical, requestedResourceType);
            case R5 -> r5Mapper.toR5(canonical, requestedResourceType);
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

    private ClinicalSetting requireClinicalSetting(
            CanonicalResource canonical,
            FhirFacadeCreateCommand command) {
        if (command.clinicalSetting() == null) {
            throw new IllegalArgumentException("FHIR 回流必须提供标准临床场景");
        }
        if (canonical.resourceType() == CanonicalResourceType.ENCOUNTER) {
            String mappedSetting = readJson(canonical.resourcePayloadJson())
                .path("encounterType")
                .asText();
            if (!command.clinicalSetting().name().equals(mappedSetting)) {
                throw new IllegalArgumentException(
                    "FHIR Encounter 场景与 X-MedKernel-Clinical-Setting 不一致");
            }
        }
        if (canonical.resourceType() == CanonicalResourceType.FOLLOW_UP
                && command.clinicalSetting() != ClinicalSetting.FOLLOWUP) {
            throw new IllegalArgumentException("FHIR 随访资源只允许 FOLLOWUP 场景");
        }
        return command.clinicalSetting();
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
        payload.put("patientId", patientId(resourceType, command.resource()));
        payload.put("policy", "PHYSICIAN_CONFIRMATION_REQUIRED");
        payload.put("safety", "不自动写医嘱/病历/申请单");
        payload.set("resource", command.resource().deepCopy());
        return payload;
    }

    private String signatureSecretKey(String tenantId, FhirAdapterConfig config) {
        return webhookSecrets.findByWebhookIdAndTenantId(config.signatureWebhookId(), tenantId)
            .filter(webhook -> ACTIVE.equals(webhook.status()))
            .map(webhook -> firstNonBlank(webhookSecretCodec.decode(webhook.secretCipher())))
            .orElse("");
    }

    private String patientId(String resourceType, JsonNode resource) {
        if ("Patient".equals(resourceType)) {
            String identifier = firstIdentifier(resource.path("identifier"));
            String id = text(resource.path("id"));
            String patient = firstNonBlank(identifier, id);
            if (!patient.isBlank()) {
                return patient;
            }
        }
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
        String patientReference = text(resource.path("patient").path("reference"));
        if (patientReference.startsWith("Patient/")) {
            String patient = patientReference.substring("Patient/".length());
            if (!patient.isBlank()) {
                return patient;
            }
        }
        String extensionPatient = patientContextFromExtension(resource.path("extension"));
        if (!extensionPatient.isBlank()) {
            return extensionPatient;
        }
        throw new IllegalArgumentException("FHIR " + resourceType
            + " create 必须携带患者上下文：subject.reference=Patient/<mpi>、subject.identifier.value 或 Patient.id");
    }

    private String encounterId(JsonNode resource) {
        String reference = text(resource.path("encounter").path("reference"));
        if (reference.startsWith("Encounter/")) {
            return reference.substring("Encounter/".length());
        }
        String context = text(resource.path("context").path("reference"));
        return context.startsWith("Encounter/") ? context.substring("Encounter/".length()) : null;
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
            nonNull(scope.wardId()),
            nonNull(scope.specialtyId()));
    }

    private ClinicalEventType eventTypeFor(CanonicalResourceType resourceType) {
        return switch (resourceType) {
            case PATIENT, ENCOUNTER -> ClinicalEventType.ADMISSION;
            case CONDITION -> ClinicalEventType.DIAGNOSIS;
            case ALLERGY_INTOLERANCE, OBSERVATION, DIAGNOSTIC_REPORT, DOCUMENT -> ClinicalEventType.REPORT;
            case MEDICATION, PROCEDURE, CARE_PLAN -> ClinicalEventType.ORDER;
            case FOLLOW_UP -> ClinicalEventType.FOLLOWUP;
            case NURSING_ASSESSMENT, CLAIM -> ClinicalEventType.REPORT;
        };
    }

    private ClinicalEventTriggerPoint triggerPointFor(CanonicalResourceType resourceType) {
        return switch (resourceType) {
            case PATIENT, ENCOUNTER, CONDITION, CLAIM -> ClinicalEventTriggerPoint.PATIENT_VIEW;
            case ALLERGY_INTOLERANCE, OBSERVATION, DIAGNOSTIC_REPORT, DOCUMENT, NURSING_ASSESSMENT ->
                ClinicalEventTriggerPoint.RESULT_REVIEW;
            case MEDICATION -> ClinicalEventTriggerPoint.MEDICATION_PRESCRIBE;
            case PROCEDURE, CARE_PLAN -> ClinicalEventTriggerPoint.ORDER_SIGN;
            case FOLLOW_UP -> ClinicalEventTriggerPoint.FOLLOWUP_ALERT;
        };
    }

    private String canonicalResourceType(String requestedResourceType, JsonNode resource) {
        String type = firstNonBlank(requestedResourceType, resource == null ? "" : text(resource.path("resourceType")));
        return switch (type) {
            case "Patient",
                 "Encounter",
                 "Condition",
                 "AllergyIntolerance",
                 "Observation",
                 "Medication",
                 "Procedure",
                 "CarePlan",
                 "ServiceRequest",
                 "MedicationRequest",
                 "DiagnosticReport",
                 "DocumentReference" -> type;
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

    private String firstIdentifier(JsonNode identifiers) {
        if (identifiers instanceof ArrayNode array && !array.isEmpty()) {
            return text(array.get(0).path("value"));
        }
        return "";
    }

    private String patientContextFromExtension(JsonNode extensions) {
        if (!(extensions instanceof ArrayNode array)) {
            return "";
        }
        for (JsonNode extension : array) {
            String url = text(extension.path("url"));
            if (!"urn:medkernel:patient".equals(url)) {
                continue;
            }
            String reference = text(extension.path("valueReference").path("reference"));
            if (reference.startsWith("Patient/")) {
                return reference.substring("Patient/".length());
            }
            String identifier = text(extension.path("valueIdentifier").path("value"));
            if (!identifier.isBlank()) {
                return identifier;
            }
            String value = text(extension.path("valueString"));
            if (!value.isBlank()) {
                return value;
            }
        }
        return "";
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
