package com.medkernel.engine.release;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Instant;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.medkernel.engine.context.ClinicalRuntimeRelease;
import com.medkernel.engine.context.ClinicalRuntimeReleaseRepository;
import com.medkernel.engine.context.ClinicalRuntimeReleaseOfflineRestoreCommand;
import com.medkernel.engine.context.ClinicalRuntimeReleaseService;
import com.medkernel.shared.api.error.ApiException;
import com.medkernel.shared.api.error.ErrorCode;
import com.medkernel.shared.evidence.EvidenceSnapshotCreateCommand;
import com.medkernel.shared.evidence.EvidenceSnapshotPort;
import com.medkernel.shared.evidence.EvidenceSnapshotView;
import com.medkernel.shared.evidence.EvidenceVerificationView;
import com.medkernel.shared.ids.Ulid;

/**
 * 机构生效版本离线交付服务。
 *
 * <p>离线文件只传输当前机构生效版本完整快照并执行签名、验签和导入预检；
 * 不创建医疗资产，不作为临床运行指针，也不修改当前机构生效版本。
 */
@Service
public class RuntimeReleaseOfflineDeliveryService {

    public static final String DELIVERY_KIND = "CLINICAL_RUNTIME_RELEASE";
    public static final String EVIDENCE_TYPE = "RUNTIME_RELEASE_OFFLINE_DELIVERY";
    public static final String WARNING =
        "离线交付文件仅用于完整性校验和导入预检，不作为临床运行指针";

    private final RuntimeReleaseQueryService queries;
    private final EvidenceSnapshotPort evidence;
    private final ClinicalRuntimeReleaseRepository releases;
    private final ClinicalRuntimeReleaseService runtimes;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    @Autowired
    public RuntimeReleaseOfflineDeliveryService(
            RuntimeReleaseQueryService queries,
            EvidenceSnapshotPort evidence,
            ClinicalRuntimeReleaseRepository releases,
            ClinicalRuntimeReleaseService runtimes,
            ObjectMapper objectMapper) {
        this(queries, evidence, releases, runtimes, objectMapper, Clock.systemUTC());
    }

    RuntimeReleaseOfflineDeliveryService(
            RuntimeReleaseQueryService queries,
            EvidenceSnapshotPort evidence,
            ClinicalRuntimeReleaseRepository releases,
            ClinicalRuntimeReleaseService runtimes) {
        this(
            queries,
            evidence,
            releases,
            runtimes,
            new ObjectMapper().findAndRegisterModules(),
            Clock.systemUTC()
        );
    }

    RuntimeReleaseOfflineDeliveryService(
            RuntimeReleaseQueryService queries,
            EvidenceSnapshotPort evidence,
            ClinicalRuntimeReleaseRepository releases,
            ClinicalRuntimeReleaseService runtimes,
            ObjectMapper objectMapper,
            Clock clock) {
        this.queries = queries;
        this.evidence = evidence;
        this.releases = releases;
        this.runtimes = runtimes;
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    /**
     * 导出当前机构生效版本完整快照，并交由合规证据链生成真实文件、SM3 摘要和 SM2 签名。
     */
    @Transactional
    public RuntimeReleaseOfflineDeliveryResponse exportCurrentRuntimeRelease(
            String tenantId,
            String hospitalId,
            String actor,
            String traceId) {
        ClinicalRuntimeReleaseDetailResponse detail = queries.currentHospitalRuntime(
                required(tenantId, "租户"),
                required(hospitalId, "医院"))
            .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "当前机构生效版本不存在"));
        RuntimeReleaseOfflineDeliverySnapshot snapshot = snapshot(detail, actor, traceId);
        String payload = writeSnapshot(snapshot);
        String evidenceId = "runtime-offline-" + releaseDigestToken(detail.release().releaseId())
            + "-" + Ulid.newUlid();
        EvidenceSnapshotView evidenceResponse = evidence.createSnapshot(tenantId, new EvidenceSnapshotCreateCommand(
            evidenceId,
            traceId,
            EVIDENCE_TYPE,
            "EXPORT",
            "clinical_runtime_release",
            detail.release().releaseId(),
            "机构生效版本离线交付文件",
            payload
        ));
        return new RuntimeReleaseOfflineDeliveryResponse(
            DELIVERY_KIND,
            evidenceResponse.evidenceId(),
            evidenceResponse.fileUri(),
            evidenceResponse.fileDigest(),
            evidenceResponse.signatureAlgorithm(),
            false,
            snapshot.release(),
            snapshot.items()
        );
    }

    /**
     * 对离线交付文件执行导入预检：验签、租户隔离、目标医院和清单摘要对账；不修改运行版本。
     */
    @Transactional
    public RuntimeReleaseOfflineImportPreviewResponse validateImportPreview(
            String tenantId,
            RuntimeReleaseOfflineImportPreviewRequest request) {
        String normalizedTenant = required(tenantId, "租户");
        String evidenceId = required(request.evidenceId(), "证据 ID");
        EvidenceVerificationView verify = evidence.verifyEvidence(normalizedTenant, evidenceId);
        if (!verify.valid() || !verify.signatureValid()) {
            throw new ApiException(ErrorCode.CONFLICT, "离线交付文件验签失败");
        }
        EvidenceSnapshotView stored = evidence.getEvidenceById(normalizedTenant, evidenceId);
        RuntimeReleaseOfflineDeliverySnapshot snapshot = readSnapshot(stored.payloadSnapshot());
        if (!DELIVERY_KIND.equals(snapshot.deliveryKind()) || snapshot.runtimeMutation()) {
            throw new ApiException(ErrorCode.CONFLICT, "离线交付文件类型不合法");
        }
        ClinicalRuntimeReleaseOfflineSnapshot release = snapshot.release();
        if (release == null) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, "离线交付文件缺少机构生效版本");
        }
        assertOfflineDeliveryEvidenceMetadata(normalizedTenant, evidenceId, stored, release.releaseId());
        if (!required(request.expectedReleaseId(), "预期机构生效版本 ID").equals(release.releaseId())) {
            throw new ApiException(ErrorCode.CONFLICT, "离线交付文件中的机构生效版本与预期不一致");
        }
        if (!required(request.expectedHospitalId(), "预期医院 ID").equals(release.hospitalId())) {
            throw new ApiException(ErrorCode.CONFLICT, "离线交付文件中的医院与预期不一致");
        }
        ClinicalRuntimeRelease current = releases
            .findByTenantIdAndReleaseId(normalizedTenant, release.releaseId())
            .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "机构生效版本不存在"));
        boolean manifestMatched = current.hospitalId().equals(release.hospitalId())
            && current.manifestSha256().equals(release.manifestSha256());
        if (!manifestMatched) {
            throw new ApiException(ErrorCode.CONFLICT, "离线交付文件清单摘要与当前记录不一致");
        }
        return new RuntimeReleaseOfflineImportPreviewResponse(
            "VALIDATED",
            false,
            true,
            true,
            release.releaseId(),
            release.hospitalId(),
            release.manifestSha256(),
            verify.fileDigest(),
            snapshot.items().size(),
            WARNING
        );
    }

    /**
     * 将已验签的离线交付文件恢复为新的机构生效版本。
     */
    @Transactional
    public RuntimeReleaseOfflineRestoreResponse restoreImport(
            String tenantId,
            RuntimeReleaseOfflineRestoreRequest request,
            String actor,
            String traceId) {
        String normalizedTenant = required(tenantId, "租户");
        String evidenceId = required(request.evidenceId(), "证据 ID");
        EvidenceVerificationView verify = evidence.verifyEvidence(normalizedTenant, evidenceId);
        if (!verify.valid() || !verify.signatureValid()) {
            throw new ApiException(ErrorCode.CONFLICT, "离线交付文件验签失败");
        }
        String confirmedFileDigest = required(request.confirmedFileDigest(), "确认文件摘要");
        if (!confirmedFileDigest.equals(required(verify.fileDigest(), "验签文件摘要"))) {
            throw new ApiException(ErrorCode.CONFLICT, "离线交付文件摘要已变化，请重新下载后确认");
        }
        EvidenceSnapshotView stored = evidence.getEvidenceById(normalizedTenant, evidenceId);
        RuntimeReleaseOfflineDeliverySnapshot snapshot = readSnapshot(stored.payloadSnapshot());
        if (!DELIVERY_KIND.equals(snapshot.deliveryKind()) || snapshot.runtimeMutation()) {
            throw new ApiException(ErrorCode.CONFLICT, "离线交付文件类型不合法");
        }
        ClinicalRuntimeReleaseOfflineSnapshot release = snapshot.release();
        if (release == null) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, "离线交付文件缺少机构生效版本");
        }
        assertOfflineDeliveryEvidenceMetadata(normalizedTenant, evidenceId, stored, release.releaseId());
        if (!normalizedTenant.equals(required(release.tenantId(), "离线文件租户"))) {
            throw new ApiException(ErrorCode.CONFLICT, "离线交付文件租户与认证租户不一致");
        }
        if (!required(request.expectedSourceReleaseId(), "预期来源机构生效版本 ID")
                .equals(release.releaseId())) {
            throw new ApiException(ErrorCode.CONFLICT, "离线交付文件来源机构生效版本与预期不一致");
        }
        if (!required(request.expectedHospitalId(), "预期医院 ID").equals(release.hospitalId())) {
            throw new ApiException(ErrorCode.CONFLICT, "离线交付文件中的医院与预期不一致");
        }
        String calculatedManifest = calculateSnapshotManifest(snapshot.items());
        if (!calculatedManifest.equals(required(release.manifestSha256(), "离线清单摘要"))) {
            throw new ApiException(ErrorCode.CONFLICT, "离线交付文件清单摘要与文件内容不一致");
        }
        ClinicalRuntimeRelease source = releases
            .findByTenantIdAndReleaseId(normalizedTenant, release.releaseId())
            .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "来源机构生效版本不存在"));
        assertSourceRuntimeReleaseMatchesSnapshot(source, release);
        ClinicalRuntimeRelease restored = runtimes.restoreOfflineSnapshot(
            new ClinicalRuntimeReleaseOfflineRestoreCommand(
                normalizedTenant,
                release.hospitalId(),
                required(request.expectedCurrentReleaseId(), "预期当前机构生效版本 ID"),
                release.releaseId(),
                release.platformBaselineReleaseId(),
                release.manifestSha256(),
                snapshot.items(),
                actor,
                traceId
            ));
        return new RuntimeReleaseOfflineRestoreResponse(
            "RESTORED",
            true,
            evidenceId,
            release.releaseId(),
            release.hospitalId(),
            verify.fileDigest(),
            release.manifestSha256(),
            snapshot.items().size(),
            restored
        );
    }

    private RuntimeReleaseOfflineDeliverySnapshot snapshot(
            ClinicalRuntimeReleaseDetailResponse detail,
            String actor,
            String traceId) {
        List<ClinicalRuntimeReleaseItemOfflineSnapshot> items = detail.items().stream()
            .map(ClinicalRuntimeReleaseItemOfflineSnapshot::from)
            .sorted(Comparator
                .comparing((ClinicalRuntimeReleaseItemOfflineSnapshot item) -> item.assetType().name())
                .thenComparing(ClinicalRuntimeReleaseItemOfflineSnapshot::assetIdentity))
            .toList();
        return new RuntimeReleaseOfflineDeliverySnapshot(
            "1.0.0",
            DELIVERY_KIND,
            false,
            Instant.now(clock),
            required(actor, "操作人"),
            blankToNull(traceId),
            ClinicalRuntimeReleaseOfflineSnapshot.from(detail.release()),
            items,
            WARNING
        );
    }

    private static void assertOfflineDeliveryEvidenceMetadata(
            String tenantId,
            String evidenceId,
            EvidenceSnapshotView stored,
            String releaseId) {
        if (stored == null) {
            throw new ApiException(ErrorCode.NOT_FOUND, "离线交付证据不存在");
        }
        boolean matched = required(evidenceId, "证据 ID").equals(required(stored.evidenceId(), "存证 ID"))
            && required(tenantId, "租户").equals(required(stored.tenantId(), "存证租户"))
            && EVIDENCE_TYPE.equals(required(stored.evidenceType(), "存证类型"))
            && "EXPORT".equals(required(stored.action(), "存证动作"))
            && "clinical_runtime_release".equals(required(stored.subjectType(), "存证主体类型"))
            && required(releaseId, "来源机构生效版本 ID")
                .equals(required(stored.subjectId(), "存证主体 ID"));
        if (!matched) {
            throw new ApiException(ErrorCode.CONFLICT, "离线交付证据元数据不匹配");
        }
    }

    private static void assertSourceRuntimeReleaseMatchesSnapshot(
            ClinicalRuntimeRelease source,
            ClinicalRuntimeReleaseOfflineSnapshot snapshot) {
        boolean matched = source.releaseId().equals(required(snapshot.releaseId(), "来源机构生效版本 ID"))
            && source.tenantId().equals(required(snapshot.tenantId(), "来源机构生效版本租户"))
            && source.hospitalId().equals(required(snapshot.hospitalId(), "来源机构生效版本医院"))
            && source.platformBaselineReleaseId()
                .equals(required(snapshot.platformBaselineReleaseId(), "来源平台标准版本 ID"))
            && source.manifestSha256().equals(required(snapshot.manifestSha256(), "来源清单摘要"));
        if (snapshot.revisionNo() != null) {
            matched = matched && source.revisionNo() == snapshot.revisionNo();
        }
        if (!matched) {
            throw new ApiException(ErrorCode.CONFLICT, "来源机构生效版本不一致，不能恢复离线交付文件");
        }
    }

    private String writeSnapshot(RuntimeReleaseOfflineDeliverySnapshot snapshot) {
        try {
            return objectMapper.writeValueAsString(snapshot);
        } catch (JsonProcessingException exception) {
            throw new ApiException(ErrorCode.INTERNAL_ERROR, "离线交付文件序列化失败");
        }
    }

    private RuntimeReleaseOfflineDeliverySnapshot readSnapshot(String payload) {
        try {
            return objectMapper.readValue(required(payload, "离线交付文件"), RuntimeReleaseOfflineDeliverySnapshot.class);
        } catch (JsonProcessingException exception) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, "离线交付文件格式不合法");
        }
    }

    private static String calculateSnapshotManifest(
            List<ClinicalRuntimeReleaseItemOfflineSnapshot> items) {
        if (items == null || items.isEmpty()) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, "离线物化资产清单不能为空");
        }
        return ReleaseManifestHash.sha256(items.stream()
            .map(RuntimeReleaseOfflineDeliveryService::canonicalLine)
            .toList());
    }

    private static String canonicalLine(ClinicalRuntimeReleaseItemOfflineSnapshot item) {
        if (item == null) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, "离线物化资产条目不能为空");
        }
        return String.join(
            "\u001f",
            required(item.sourceTenantId(), "来源租户"),
            requiredValue(item.sourceLayer(), "来源层级").name(),
            requiredValue(item.assetType(), "资产类型").name(),
            required(item.assetIdentity(), "资产身份"),
            requiredValue(item.entryState(), "条目状态").name(),
            nullToEmpty(item.versionId()),
            nullToEmpty(item.versionNo()),
            nullToEmpty(item.contentHash())
        );
    }

    private static <T> T requiredValue(T value, String label) {
        if (value == null) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, label + "不能为空");
        }
        return value;
    }

    private static String required(String value, String label) {
        if (value == null || value.isBlank()) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, label + "不能为空");
        }
        return value.trim();
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    private static String releaseDigestToken(String releaseId) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                .digest(required(releaseId, "机构生效版本 ID").getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest).substring(0, 12);
        } catch (NoSuchAlgorithmException exception) {
            throw new ApiException(ErrorCode.INTERNAL_ERROR, "离线交付证据 ID 生成失败");
        }
    }
}
