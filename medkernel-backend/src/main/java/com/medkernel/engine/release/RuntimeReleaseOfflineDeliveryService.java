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

import com.medkernel.compliance.evidence.dto.EvidenceCreateDto;
import com.medkernel.compliance.evidence.dto.EvidenceResponse;
import com.medkernel.compliance.evidence.dto.EvidenceVerifyResult;
import com.medkernel.compliance.evidence.service.EvidenceService;
import com.medkernel.engine.context.ClinicalRuntimeRelease;
import com.medkernel.engine.context.ClinicalRuntimeReleaseRepository;
import com.medkernel.shared.api.error.ApiException;
import com.medkernel.shared.api.error.ErrorCode;
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
    private final EvidenceService evidence;
    private final ClinicalRuntimeReleaseRepository releases;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    @Autowired
    public RuntimeReleaseOfflineDeliveryService(
            RuntimeReleaseQueryService queries,
            EvidenceService evidence,
            ClinicalRuntimeReleaseRepository releases,
            ObjectMapper objectMapper) {
        this(queries, evidence, releases, objectMapper, Clock.systemUTC());
    }

    RuntimeReleaseOfflineDeliveryService(
            RuntimeReleaseQueryService queries,
            EvidenceService evidence,
            ClinicalRuntimeReleaseRepository releases) {
        this(queries, evidence, releases, new ObjectMapper().findAndRegisterModules(), Clock.systemUTC());
    }

    RuntimeReleaseOfflineDeliveryService(
            RuntimeReleaseQueryService queries,
            EvidenceService evidence,
            ClinicalRuntimeReleaseRepository releases,
            ObjectMapper objectMapper,
            Clock clock) {
        this.queries = queries;
        this.evidence = evidence;
        this.releases = releases;
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
        EvidenceResponse evidenceResponse = evidence.createSnapshot(tenantId, new EvidenceCreateDto(
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
        EvidenceVerifyResult verify = evidence.verifyEvidence(normalizedTenant, evidenceId);
        if (!verify.isValid() || !verify.signatureValid()) {
            throw new ApiException(ErrorCode.CONFLICT, "离线交付文件验签失败");
        }
        EvidenceResponse stored = evidence.getEvidenceById(normalizedTenant, evidenceId);
        RuntimeReleaseOfflineDeliverySnapshot snapshot = readSnapshot(stored.payloadSnapshot());
        if (!DELIVERY_KIND.equals(snapshot.deliveryKind()) || snapshot.runtimeMutation()) {
            throw new ApiException(ErrorCode.CONFLICT, "离线交付文件类型不合法");
        }
        ClinicalRuntimeReleaseOfflineSnapshot release = snapshot.release();
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

    private static String required(String value, String label) {
        if (value == null || value.isBlank()) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, label + "不能为空");
        }
        return value.trim();
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
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
