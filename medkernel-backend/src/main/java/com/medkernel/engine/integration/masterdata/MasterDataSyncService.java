package com.medkernel.engine.integration.masterdata;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HexFormat;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.medkernel.engine.integration.domain.IntegrationWebhookConfig;
import com.medkernel.engine.integration.repository.IntegrationAdapterRepository;
import com.medkernel.engine.integration.repository.IntegrationWebhookConfigRepository;
import com.medkernel.engine.integration.service.WebhookSecretCodec;
import com.medkernel.engine.org.OrgFacilityType;
import com.medkernel.engine.org.OrgUnitService;
import com.medkernel.engine.org.OrgUnitStatus;
import com.medkernel.engine.org.OrgUnitSyncCommand;
import com.medkernel.engine.terminology.LocalTermSyncCommand;
import com.medkernel.engine.terminology.TerminologyService;
import com.medkernel.shared.api.error.ApiException;
import com.medkernel.shared.api.error.ErrorCode;
import com.medkernel.shared.audit.AuditAction;
import com.medkernel.shared.audit.AuditRecordCommand;
import com.medkernel.shared.audit.AuditRecorder;
import com.medkernel.shared.context.OrgLevel;
import com.medkernel.shared.context.OrgScope;
import com.medkernel.shared.context.RequestContext;

/**
 * 院内组织、人员用户和本地字典主数据同步服务。
 */
@Service
public class MasterDataSyncService {

    private static final long SIGNATURE_MAX_SKEW_SECONDS = 300L;
    private static final String ACTIVE = "ACTIVE";
    private static final String MASTER_DATA_EVENT = "MASTER_DATA";

    private final IntegrationWebhookConfigRepository webhooks;
    private final IntegrationAdapterRepository adapters;
    private final MasterDataSyncBatchRepository batches;
    private final MasterDataSyncRecordRepository records;
    private final OrgUnitService organizations;
    private final MasterDataPersonnelPort personnel;
    private final TerminologyService terminology;
    private final ObjectMapper objectMapper;
    private final WebhookSecretCodec secretCodec;
    private final MasterDataSyncFailureRecorder failures;
    private final AuditRecorder auditRecorder;

    public MasterDataSyncService(
            IntegrationWebhookConfigRepository webhooks,
            IntegrationAdapterRepository adapters,
            MasterDataSyncBatchRepository batches,
            MasterDataSyncRecordRepository records,
            OrgUnitService organizations,
            MasterDataPersonnelPort personnel,
            TerminologyService terminology,
            ObjectMapper objectMapper,
            WebhookSecretCodec secretCodec,
            MasterDataSyncFailureRecorder failures,
            AuditRecorder auditRecorder) {
        this.webhooks = webhooks;
        this.adapters = adapters;
        this.batches = batches;
        this.records = records;
        this.organizations = organizations;
        this.personnel = personnel;
        this.terminology = terminology;
        this.objectMapper = objectMapper;
        this.secretCodec = secretCodec;
        this.failures = failures;
        this.auditRecorder = auditRecorder;
    }

    /**
     * 验签后原子应用主数据批次。任一记录失败时领域写入与游标一起回滚。
     */
    @Transactional
    public MasterDataSyncResponse sync(
            String tenantId,
            String webhookId,
            String timestamp,
            String signature,
            MasterDataSyncRequest request) {
        String safeTenant = required(tenantId, "租户标识");
        String sourceSystem = normalize(request.sourceSystem());
        String traceId = Optional.ofNullable(RequestContext.currentTraceId())
            .filter(value -> !value.isBlank())
            .orElseGet(() -> RequestContext.snapshot().traceId());
        String hash = payloadHash(request);
        verifyChannelAndSignature(safeTenant, webhookId, timestamp, signature, request);

        try {
            return runInSourceContext(
                safeTenant,
                sourceSystem,
                traceId,
                () -> applyBatch(safeTenant, webhookId, sourceSystem, request, hash, traceId));
        } catch (ApiException exception) {
            failures.record(
                safeTenant, webhookId, request, hash, traceId, exception.errorCode().code());
            throw exception;
        } catch (RuntimeException exception) {
            failures.record(
                safeTenant, webhookId, request, hash, traceId, ErrorCode.INTERNAL_ERROR.code());
            throw exception;
        }
    }

    @Transactional(readOnly = true)
    public MasterDataReconciliationResponse reconciliation(String sourceSystem) {
        String tenantId = RequestContext.currentOrgScope().tenantId();
        String source = normalize(sourceSystem);
        MasterDataSyncBatch latest = batches.findLatestSuccessful(tenantId, source).orElse(null);
        List<MasterDataReconciliationResponse.ResourceCount> counts =
            java.util.Arrays.stream(MasterDataResourceType.values())
                .map(type -> new MasterDataReconciliationResponse.ResourceCount(
                    type,
                    records.countByStatus(
                        tenantId, source, type.name(), MasterDataRecordStatus.ACTIVE.name()),
                    records.countByStatus(
                        tenantId, source, type.name(), MasterDataRecordStatus.DISABLED.name())))
                .toList();
        return new MasterDataReconciliationResponse(
            source,
            latest == null ? null : latest.batchId(),
            latest == null ? null : latest.cursor(),
            latest == null ? null : latest.processedAt(),
            counts);
    }

    String payloadHash(MasterDataSyncRequest request) {
        try {
            return sha256(objectMapper.writeValueAsBytes(request));
        } catch (JsonProcessingException exception) {
            throw new ApiException(ErrorCode.BAD_REQUEST, "主数据同步载荷无法序列化", exception);
        }
    }

    private MasterDataSyncResponse applyBatch(
            String tenantId,
            String webhookId,
            String sourceSystem,
            MasterDataSyncRequest request,
            String hash,
            String traceId) {
        validateRequest(request);
        MasterDataSyncBatch existing = batches
            .findByTenantIdAndSourceSystemAndBatchId(tenantId, sourceSystem, request.batchId())
            .orElse(null);
        if (existing != null) {
            if (!existing.payloadHash().equals(hash)) {
                throw ApiException.conflict("同一批次标识不能提交不同载荷");
            }
            if (existing.status() == MasterDataSyncStatus.SUCCESS) {
                return response(existing, true, List.of());
            }
        }

        MasterDataSyncBatch latest = batches.findLatestSuccessful(tenantId, sourceSystem).orElse(null);
        String expectedPrevious = latest == null ? null : latest.cursor();
        if (!same(expectedPrevious, blankToNull(request.previousCursor()))) {
            throw ApiException.conflict("同步游标不连续，请从服务端最新游标继续");
        }
        adapters.findByAdapterIdAndTenantId(request.adapterId(), tenantId)
            .orElseThrow(() -> new ApiException(
                ErrorCode.ENG_INTEG_002, "主数据同步适配器不存在"));

        assertUniqueItems(request.items());
        for (MasterDataSyncItem item : request.items()) {
            MasterDataSyncRecord current = findRecord(tenantId, sourceSystem, item).orElse(null);
            if (item.operation() == MasterDataOperation.DISABLE && current == null) {
                throw ApiException.conflict(
                    "停用来源记录前必须存在已同步映射，recordId=" + item.recordId());
            }
            if (current != null && item.sourceVersion() < current.sourceVersion()) {
                throw ApiException.conflict(
                    "来源版本早于已应用版本，recordId=" + item.recordId());
            }
            if (current != null && item.sourceVersion() == current.sourceVersion()
                    && !current.payloadHash().equals(itemHash(item))) {
                throw ApiException.conflict(
                    "同一来源版本不能提交不同载荷，recordId=" + item.recordId());
            }
        }

        Authentication sourceAuthentication = sourceAuthentication(sourceSystem);
        List<MasterDataSyncResponse.ItemResult> results = new ArrayList<>();
        List<MasterDataSyncItem> ordered = request.items().stream()
            .sorted(Comparator.comparingInt(item -> resourceOrder(item.resourceType())))
            .toList();
        for (MasterDataSyncItem item : ordered) {
            MasterDataSyncRecord current = findRecord(tenantId, sourceSystem, item).orElse(null);
            String itemHash = itemHash(item);
            if (current != null && item.sourceVersion() == current.sourceVersion()
                    && current.payloadHash().equals(itemHash)) {
                results.add(result(item, current.internalId(), current.status()));
                continue;
            }
            String internalId;
            if (item.operation() == MasterDataOperation.DISABLE) {
                internalId = current.internalId();
                disableMissingRecord(item.resourceType(), internalId, sourceAuthentication);
            } else {
                internalId = applyItem(sourceSystem, item, sourceAuthentication);
            }
            MasterDataRecordStatus status = item.operation() == MasterDataOperation.DISABLE
                ? MasterDataRecordStatus.DISABLED
                : MasterDataRecordStatus.ACTIVE;
            MasterDataSyncRecord saved = records.save(new MasterDataSyncRecord(
                current == null ? null : current.id(),
                tenantId,
                sourceSystem,
                item.resourceType(),
                item.recordId(),
                internalId,
                item.sourceVersion(),
                itemHash,
                status,
                request.batchId(),
                item.sourceUpdatedAt(),
                Instant.now()));
            results.add(result(item, saved.internalId(), saved.status()));
        }

        if (request.mode() == MasterDataSyncMode.FULL_SNAPSHOT) {
            applyFullSnapshotTombstones(
                tenantId, sourceSystem, request, sourceAuthentication, results);
        }
        Instant now = Instant.now();
        MasterDataSyncBatch saved = batches.save(new MasterDataSyncBatch(
            existing == null ? null : existing.id(),
            request.batchId(),
            tenantId,
            webhookId,
            request.adapterId(),
            sourceSystem,
            request.mode(),
            blankToNull(request.previousCursor()),
            request.cursor(),
            hash,
            MasterDataSyncStatus.SUCCESS,
            results.size(),
            results.size(),
            0,
            null,
            existing == null ? now : existing.createdAt(),
            now,
            traceId));
        auditRecorder.record(new AuditRecordCommand(
            AuditAction.EXECUTE,
            "mk_integration_master_data_sync_batch",
            saved.batchId(),
            "院内主数据同步成功：来源=" + sourceSystem
                + "，模式=" + request.mode()
                + "，处理数=" + results.size(),
            null,
            new MasterDataAuditSnapshot(
                saved.sourceSystem(), saved.mode(), saved.cursor(), saved.totalCount()),
            null));
        return response(saved, false, results);
    }

    private record MasterDataAuditSnapshot(
        String sourceSystem,
        MasterDataSyncMode mode,
        String cursor,
        int totalCount
    ) {
    }

    private void applyFullSnapshotTombstones(
            String tenantId,
            String sourceSystem,
            MasterDataSyncRequest request,
            Authentication authentication,
            List<MasterDataSyncResponse.ItemResult> results) {
        if (request.authoritativeResourceTypes().isEmpty()) {
            throw new ApiException(
                ErrorCode.BAD_REQUEST, "全量快照必须声明权威资源类型");
        }
        Set<String> incoming = request.items().stream()
            .map(item -> item.resourceType().name() + ":" + item.recordId())
            .collect(java.util.stream.Collectors.toSet());
        List<MasterDataResourceType> tombstoneTypes = request.authoritativeResourceTypes().stream()
            .sorted(Comparator.comparingInt(this::tombstoneOrder))
            .toList();
        for (MasterDataResourceType type : tombstoneTypes) {
            for (MasterDataSyncRecord current :
                    records.findByTenantIdAndSourceSystemAndResourceTypeAndStatus(
                        tenantId, sourceSystem, type, MasterDataRecordStatus.ACTIVE)) {
                if (incoming.contains(type.name() + ":" + current.sourceRecordId())) {
                    continue;
                }
                disableMissingRecord(type, current.internalId(), authentication);
                MasterDataSyncRecord disabled = records.save(new MasterDataSyncRecord(
                    current.id(), current.tenantId(), current.sourceSystem(), current.resourceType(),
                    current.sourceRecordId(), current.internalId(), current.sourceVersion(),
                    current.payloadHash(), MasterDataRecordStatus.DISABLED, request.batchId(),
                    current.sourceUpdatedAt(), Instant.now()));
                results.add(new MasterDataSyncResponse.ItemResult(
                    current.sourceRecordId(), type, MasterDataOperation.DISABLE,
                    current.sourceVersion(), current.internalId(), disabled.status()));
            }
        }
    }

    private String applyItem(
            String sourceSystem,
            MasterDataSyncItem item,
            Authentication authentication) {
        try {
            return switch (item.resourceType()) {
                case ORG_UNIT -> {
                    OrgPayload payload = objectMapper.treeToValue(item.payload(), OrgPayload.class);
                    yield organizations.syncFromExternal(new OrgUnitSyncCommand(
                        payload.code(), payload.parentCode(), payload.level(), payload.name(),
                        payload.namePinyin(), payload.facilityType(), payload.specialtyId(),
                        payload.status(), false));
                }
                case PERSON -> {
                    PersonPayload payload = objectMapper.treeToValue(item.payload(), PersonPayload.class);
                    yield personnel.upsert(new MasterDataPersonCommand(
                        payload.employeeNo(), payload.displayName(), payload.organizationCode(),
                        payload.departmentCode(), payload.wardCode(), payload.appointmentType(),
                        payload.positionTitle(), payload.userId(), payload.roleCode(),
                        payload.identityProvider(), payload.identitySubject(), payload.status()),
                        authentication);
                }
                case LOCAL_TERM -> {
                    LocalTermPayload payload =
                        objectMapper.treeToValue(item.payload(), LocalTermPayload.class);
                    yield terminology.syncLocalTerm(new LocalTermSyncCommand(
                        sourceSystem, payload.localCode(), payload.category(), payload.localName(),
                        payload.normalizedName(), payload.departmentCode(), payload.status(), false));
                }
            };
        } catch (JsonProcessingException exception) {
            throw new ApiException(
                ErrorCode.BAD_REQUEST,
                "主数据记录结构不合法，recordId=" + item.recordId(),
                exception);
        }
    }

    private void disableMissingRecord(
            MasterDataResourceType type,
            String internalId,
            Authentication authentication) {
        switch (type) {
            case ORG_UNIT -> organizations.disableFromExternal(internalId);
            case PERSON -> personnel.disable(internalId, authentication);
            case LOCAL_TERM -> terminology.disableLocalTermFromExternal(internalId);
        }
    }

    private void verifyChannelAndSignature(
            String tenantId,
            String webhookId,
            String timestamp,
            String signature,
            MasterDataSyncRequest request) {
        IntegrationWebhookConfig webhook = webhooks
            .findByWebhookIdAndTenantId(webhookId, tenantId)
            .filter(item -> ACTIVE.equalsIgnoreCase(item.status()))
            .orElseThrow(() -> new ApiException(
                ErrorCode.ENG_INTEG_003, "主数据同步通道不存在或未启用"));
        boolean subscribed = webhook.eventsSubscribed() != null
            && java.util.Arrays.stream(webhook.eventsSubscribed().split("[,;\\s]+"))
                .map(value -> value.trim().toUpperCase(Locale.ROOT))
                .anyMatch(MASTER_DATA_EVENT::equals);
        if (!subscribed) {
            throw new ApiException(ErrorCode.ENG_INTEG_004, "Webhook 未订阅主数据同步事件");
        }
        long epoch;
        try {
            epoch = Long.parseLong(timestamp);
        } catch (RuntimeException exception) {
            throw new ApiException(ErrorCode.ENG_INTEG_004, "主数据同步签名时间戳非法");
        }
        if (Math.abs(Instant.now().getEpochSecond() - epoch) > SIGNATURE_MAX_SKEW_SECONDS) {
            throw new ApiException(ErrorCode.ENG_INTEG_004, "主数据同步签名已过期");
        }
        try {
            String canonical = objectMapper.writeValueAsString(request);
            String expected = hmac(timestamp + "." + canonical, secretCodec.decode(webhook.secretCipher()));
            String provided = signature == null ? "" : signature.trim();
            if (provided.startsWith("sha256=")) {
                provided = provided.substring("sha256=".length());
            }
            if (!MessageDigest.isEqual(
                    expected.getBytes(StandardCharsets.UTF_8),
                    provided.getBytes(StandardCharsets.UTF_8))) {
                throw new ApiException(ErrorCode.ENG_INTEG_004, "主数据同步签名校验失败");
            }
        } catch (ApiException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new ApiException(ErrorCode.INTERNAL_ERROR, "主数据同步签名校验异常", exception);
        }
    }

    private <T> T runInSourceContext(
            String tenantId,
            String sourceSystem,
            String traceId,
            java.util.concurrent.Callable<T> action) {
        Authentication previous = SecurityContextHolder.getContext().getAuthentication();
        try {
            SecurityContextHolder.getContext().setAuthentication(sourceAuthentication(sourceSystem));
            return RequestContext.callWith(
                new RequestContext.Snapshot(
                    traceId, OrgScope.tenant(tenantId), "integration:" + sourceSystem),
                action);
        } catch (ApiException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new ApiException(ErrorCode.INTERNAL_ERROR, "院内主数据同步失败", exception);
        } finally {
            SecurityContextHolder.getContext().setAuthentication(previous);
        }
    }

    private Authentication sourceAuthentication(String sourceSystem) {
        return new UsernamePasswordAuthenticationToken(
            "integration:" + sourceSystem,
            null,
            List.of(new SimpleGrantedAuthority("ROLE_ORGANIZATION_ADMIN")));
    }

    private void validateRequest(MasterDataSyncRequest request) {
        if (request.mode() == MasterDataSyncMode.INCREMENTAL && request.items().isEmpty()) {
            throw new ApiException(ErrorCode.BAD_REQUEST, "增量同步批次不能为空");
        }
        if (request.mode() == MasterDataSyncMode.FULL_SNAPSHOT
                && request.authoritativeResourceTypes().isEmpty()) {
            throw new ApiException(ErrorCode.BAD_REQUEST, "全量快照必须声明权威资源类型");
        }
    }

    private void assertUniqueItems(List<MasterDataSyncItem> items) {
        Set<String> keys = new LinkedHashSet<>();
        for (MasterDataSyncItem item : items) {
            String key = item.resourceType().name() + ":" + item.recordId();
            if (!keys.add(key)) {
                throw ApiException.conflict("同一批次存在重复主数据记录: " + key);
            }
        }
    }

    private Optional<MasterDataSyncRecord> findRecord(
            String tenantId,
            String sourceSystem,
            MasterDataSyncItem item) {
        return records.findByTenantIdAndSourceSystemAndResourceTypeAndSourceRecordId(
            tenantId, sourceSystem, item.resourceType(), item.recordId());
    }

    private String itemHash(MasterDataSyncItem item) {
        try {
            return sha256(objectMapper.writeValueAsBytes(item));
        } catch (JsonProcessingException exception) {
            throw new ApiException(ErrorCode.BAD_REQUEST, "主数据记录无法序列化", exception);
        }
    }

    private String sha256(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (Exception exception) {
            throw new ApiException(ErrorCode.INTERNAL_ERROR, "主数据摘要计算失败", exception);
        }
    }

    private String hmac(String value, String secret) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        return HexFormat.of().formatHex(mac.doFinal(value.getBytes(StandardCharsets.UTF_8)));
    }

    private int resourceOrder(MasterDataResourceType type) {
        return switch (type) {
            case ORG_UNIT -> 0;
            case PERSON -> 1;
            case LOCAL_TERM -> 2;
        };
    }

    private int tombstoneOrder(MasterDataResourceType type) {
        return switch (type) {
            case PERSON -> 0;
            case LOCAL_TERM -> 1;
            case ORG_UNIT -> 2;
        };
    }

    private MasterDataSyncResponse.ItemResult result(
            MasterDataSyncItem item,
            String internalId,
            MasterDataRecordStatus status) {
        return new MasterDataSyncResponse.ItemResult(
            item.recordId(), item.resourceType(), item.operation(),
            item.sourceVersion(), internalId, status);
    }

    private MasterDataSyncResponse response(
            MasterDataSyncBatch batch,
            boolean replay,
            List<MasterDataSyncResponse.ItemResult> items) {
        return new MasterDataSyncResponse(
            batch.batchId(), batch.sourceSystem(), batch.cursor(), batch.status(),
            batch.totalCount(), batch.appliedCount(), batch.failedCount(), replay,
            batch.processedAt(), batch.traceId(), items);
    }

    private String normalize(String value) {
        return required(value, "来源系统").toUpperCase(Locale.ROOT);
    }

    private String required(String value, String label) {
        if (value == null || value.isBlank()) {
            throw new ApiException(ErrorCode.BAD_REQUEST, label + "不能为空");
        }
        return value.trim();
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private boolean same(String left, String right) {
        return left == null ? right == null : left.equals(right);
    }

    private record OrgPayload(
        String code,
        String parentCode,
        OrgLevel level,
        String name,
        String namePinyin,
        OrgFacilityType facilityType,
        String specialtyId,
        OrgUnitStatus status
    ) {
    }

    private record PersonPayload(
        String employeeNo,
        String displayName,
        String organizationCode,
        String departmentCode,
        String wardCode,
        String appointmentType,
        String positionTitle,
        String userId,
        String roleCode,
        String identityProvider,
        String identitySubject,
        String status
    ) {
    }

    private record LocalTermPayload(
        String localCode,
        String category,
        String localName,
        String normalizedName,
        String departmentCode,
        String status
    ) {
    }
}
