package com.medkernel.compliance.evidence.service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPair;
import java.security.PublicKey;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.medkernel.compliance.evidence.domain.EvidenceSnapshot;
import com.medkernel.compliance.evidence.dto.EvidenceCreateDto;
import com.medkernel.compliance.evidence.dto.EvidenceExportResult;
import com.medkernel.compliance.evidence.dto.EvidenceResponse;
import com.medkernel.compliance.evidence.dto.EvidenceVerifyResult;
import com.medkernel.compliance.evidence.repository.EvidenceSnapshotRepository;
import com.medkernel.shared.api.error.ApiException;
import com.medkernel.shared.api.error.ErrorCode;
import com.medkernel.shared.audit.AuditAction;
import com.medkernel.shared.audit.AuditEvent;
import com.medkernel.shared.audit.IsolatedAuditPublisher;
import com.medkernel.shared.crypto.SmCryptoService;
import com.medkernel.shared.datascope.DataScope;

/**
 * 医疗合规可信存证证据中枢服务层（强租户隔离与防篡改）。
 */
@Service
@DataScope(requireTenant = true)
public class EvidenceService {

    private static final String SNAPSHOT_DOWNLOAD_PREFIX = "/api/v1/compliance/evidence/snapshots/";
    private static final String EXPORT_DOWNLOAD_PREFIX = "/api/v1/compliance/evidence/snapshots/export/";
    private static final String NDJSON_CONTENT_TYPE = "application/x-ndjson";

    private final EvidenceSnapshotRepository repository;
    private final IsolatedAuditPublisher isolatedAudit;
    private final SmCryptoService crypto;
    private final Path storageRoot;

    @Autowired
    public EvidenceService(EvidenceSnapshotRepository repository, IsolatedAuditPublisher isolatedAudit,
                           SmCryptoService crypto) {
        this(repository, isolatedAudit, crypto,
            Path.of(System.getProperty("java.io.tmpdir"), "medkernel-evidence"));
    }

    EvidenceService(EvidenceSnapshotRepository repository, IsolatedAuditPublisher isolatedAudit,
                    SmCryptoService crypto, Path storageRoot) {
        this.repository = repository;
        this.isolatedAudit = isolatedAudit;
        this.crypto = crypto;
        this.storageRoot = storageRoot;
    }

    /**
     * 创建并在子事务中安全存证一条证据快照。
     */
    @Transactional
    public EvidenceResponse createSnapshot(String tenantId, EvidenceCreateDto dto) {
        Optional<EvidenceSnapshot> existing = repository.findByEvidenceId(dto.evidenceId());
        if (existing.isPresent()) {
            throw new ApiException(ErrorCode.ENG_EVID_003, "证据快照已存在: " + dto.evidenceId());
        }

        Instant now = Instant.now();
        EvidenceSnapshot unsigned = new EvidenceSnapshot(
            null,
            dto.evidenceId(),
            tenantId,
            dto.traceId(),
            dto.evidenceType(),
            dto.action(),
            dto.subjectType(),
            dto.subjectId(),
            dto.evidenceSummary(),
            dto.payloadSnapshot(),
            "",
            snapshotDownloadUri(dto.evidenceId()),
            "",
            EvidenceSnapshot.SIGNATURE_ALGORITHM,
            "",
            "",
            now,
            "system",
            now,
            "system"
        );

        EvidenceSnapshot signable = new EvidenceSnapshot(
            null,
            unsigned.evidenceId(),
            unsigned.tenantId(),
            unsigned.traceId(),
            unsigned.evidenceType(),
            unsigned.action(),
            unsigned.subjectType(),
            unsigned.subjectId(),
            unsigned.evidenceSummary(),
            unsigned.payloadSnapshot(),
            unsigned.calculateHash(),
            unsigned.fileUri(),
            unsigned.calculateFileDigest(),
            EvidenceSnapshot.SIGNATURE_ALGORITHM,
            "",
            "",
            unsigned.createdAt(),
            unsigned.createdBy(),
            unsigned.updatedAt(),
            unsigned.updatedBy()
        );

        SignatureMaterial signature = sign(signable);
        EvidenceSnapshot entity = new EvidenceSnapshot(
            null,
            signable.evidenceId(),
            signable.tenantId(),
            signable.traceId(),
            signable.evidenceType(),
            signable.action(),
            signable.subjectType(),
            signable.subjectId(),
            signable.evidenceSummary(),
            signable.payloadSnapshot(),
            signable.payloadHash(),
            signable.fileUri(),
            signable.fileDigest(),
            signable.signatureAlgorithm(),
            signature.signatureValue(),
            signature.signerPublicKey(),
            signable.createdAt(),
            signable.createdBy(),
            signable.updatedAt(),
            signable.updatedBy()
        );

        writeSnapshotFile(entity);
        EvidenceSnapshot saved = repository.save(entity);
        return EvidenceResponse.fromEntity(saved);
    }

    /**
     * 强租户物理隔离的分页检索。
     */
    @Transactional(readOnly = true)
    public List<EvidenceResponse> getEvidences(String tenantId, String keyword, String evidenceType, int page, int size) {
        int limit = size <= 0 ? 20 : size;
        int offset = (page <= 0 ? 0 : page - 1) * limit;

        List<EvidenceSnapshot> list = repository.findEvidencesPage(tenantId, evidenceType, keyword, limit, offset);
        return list.stream()
            .map(EvidenceResponse::fromEntity)
            .collect(Collectors.toList());
    }

    /**
     * 过滤查询的总数。
     */
    @Transactional(readOnly = true)
    public long countEvidences(String tenantId, String keyword, String evidenceType) {
        return repository.countEvidences(tenantId, evidenceType, keyword);
    }

    /**
     * 根据全局唯一证据 ID 检索证据详情。
     */
    @Transactional(readOnly = true)
    public EvidenceResponse getEvidenceById(String tenantId, String evidenceId) {
        EvidenceSnapshot entity = repository.findByEvidenceId(evidenceId)
            .orElseThrow(() -> new ApiException(ErrorCode.ENG_EVID_001, "未找到指定的证据快照: " + evidenceId));

        if (!tenantId.equals(entity.tenantId())) {
            throw new ApiException(ErrorCode.TENANT_FORBIDDEN);
        }

        return EvidenceResponse.fromEntity(entity);
    }

    /**
     * 双向国密验签服务（若篡改则发布隔离级别的高危入侵审计日志）。
     */
    @Transactional
    public EvidenceVerifyResult verifyEvidence(String tenantId, String evidenceId) {
        EvidenceSnapshot entity = repository.findByEvidenceId(evidenceId)
            .orElseThrow(() -> new ApiException(ErrorCode.ENG_EVID_001, "未找到指定的证据快照: " + evidenceId));

        if (!tenantId.equals(entity.tenantId())) {
            throw new ApiException(ErrorCode.TENANT_FORBIDDEN);
        }

        String calculated = entity.calculateHash();
        boolean signatureValid = verifySignature(entity);
        boolean isValid = entity.isValid() && signatureValid;

        if (!isValid) {
            isolatedAudit.publishInNewTx(AuditEvent.failure(
                AuditAction.REVIEW,
                "evidence_snapshot",
                evidenceId,
                "ENG-EVID-002",
                "国密签名或数字指纹验签失败，快照原始数据可能已被修改"
            ));
        } else {
            isolatedAudit.publishInNewTx(AuditEvent.of(
                AuditAction.REVIEW,
                "evidence_snapshot",
                evidenceId,
                "国密签名与数字指纹验签成功，数据完好无损"
            ));
        }

        return new EvidenceVerifyResult(
            evidenceId,
            isValid,
            calculated,
            entity.payloadHash(),
            entity.signatureAlgorithm(),
            signatureValid,
            entity.fileUri(),
            entity.fileDigest()
        );
    }

    /**
     * 生成合规证据大导出的归档文件、国密摘要与真实下载 URI。
     */
    @Transactional
    public EvidenceExportResult exportEvidences(String tenantId, String evidenceType) {
        long total = repository.countEvidences(tenantId, evidenceType, null);
        if (total == 0) {
            throw new ApiException(ErrorCode.ENG_EVID_001, "当前范围无可导出的证据快照");
        }

        List<EvidenceSnapshot> snapshots = repository.findEvidencesPage(
            tenantId, evidenceType, null, (int) total, 0);

        String canonical = snapshots.stream()
            .sorted(Comparator.comparing(EvidenceSnapshot::evidenceId))
            .map(e -> String.join(":",
                e.evidenceId(),
                e.payloadHash(),
                e.fileDigest() == null ? "" : e.fileDigest(),
                e.signatureValue() == null ? "" : e.signatureValue()))
            .collect(Collectors.joining("|"));
        String archiveDigestHex = crypto.sm3Hex(canonical);
        String archiveHash = EvidenceSnapshot.DIGEST_PREFIX + archiveDigestHex;
        String archiveUri = exportDownloadUri(archiveDigestHex);

        writeExportFile(tenantId, archiveDigestHex, snapshots);

        isolatedAudit.publishInNewTx(AuditEvent.of(
            AuditAction.EXPORT,
            "evidence_snapshot",
            "bulk-export-" + (evidenceType == null ? "ALL" : evidenceType),
            "证据合规数据包导出完成，含 " + total + " 条快照，归档国密摘要=" + archiveHash + "，URI=" + archiveUri
        ));

        return new EvidenceExportResult(archiveHash, archiveUri, NDJSON_CONTENT_TYPE, total, "COMPLETED");
    }

    /**
     * 读取单条证据文件。
     */
    @Transactional(readOnly = true)
    public byte[] readSnapshotFile(String tenantId, String evidenceId) {
        EvidenceSnapshot entity = repository.findByEvidenceId(evidenceId)
            .orElseThrow(() -> new ApiException(ErrorCode.ENG_EVID_001, "未找到指定的证据快照: " + evidenceId));
        if (!tenantId.equals(entity.tenantId())) {
            throw new ApiException(ErrorCode.TENANT_FORBIDDEN);
        }
        return readFile(snapshotFilePath(tenantId, evidenceId), "证据文件不存在: " + evidenceId);
    }

    /**
     * 读取导出的证据包文件。
     */
    @Transactional(readOnly = true)
    public byte[] readExportFile(String tenantId, String archiveDigestHex) {
        return readFile(exportFilePath(tenantId, archiveDigestHex), "证据包文件不存在: " + archiveDigestHex);
    }

    private SignatureMaterial sign(EvidenceSnapshot entity) {
        try {
            KeyPair keyPair = crypto.generateSm2KeyPair();
            byte[] payload = entity.signaturePayload().getBytes(StandardCharsets.UTF_8);
            byte[] signatureBytes = crypto.sm2Sign(keyPair.getPrivate(), payload);
            return new SignatureMaterial(
                crypto.base64Encode(signatureBytes),
                crypto.base64Encode(keyPair.getPublic().getEncoded())
            );
        } catch (Exception exception) {
            throw new ApiException(ErrorCode.INTERNAL_ERROR, "证据国密签名失败: " + exception.getMessage());
        }
    }

    private boolean verifySignature(EvidenceSnapshot entity) {
        if (!EvidenceSnapshot.SIGNATURE_ALGORITHM.equals(entity.signatureAlgorithm())
            || entity.signatureValue() == null || entity.signatureValue().isBlank()
            || entity.signerPublicKey() == null || entity.signerPublicKey().isBlank()) {
            return false;
        }
        try {
            PublicKey publicKey = crypto.decodeSm2PublicKey(entity.signerPublicKey());
            return crypto.sm2Verify(publicKey,
                entity.signaturePayload().getBytes(StandardCharsets.UTF_8),
                crypto.base64Decode(entity.signatureValue()));
        } catch (Exception exception) {
            return false;
        }
    }

    private void writeSnapshotFile(EvidenceSnapshot entity) {
        writeString(snapshotFilePath(entity.tenantId(), entity.evidenceId()), entity.payloadSnapshot());
    }

    private void writeExportFile(String tenantId, String archiveDigestHex, List<EvidenceSnapshot> snapshots) {
        String ndjson = snapshots.stream()
            .sorted(Comparator.comparing(EvidenceSnapshot::evidenceId))
            .map(this::exportLine)
            .collect(Collectors.joining("\n", "", "\n"));
        writeString(exportFilePath(tenantId, archiveDigestHex), ndjson);
    }

    private byte[] readFile(Path path, String missingMessage) {
        try {
            if (!Files.exists(path)) {
                throw new ApiException(ErrorCode.ENG_EVID_001, missingMessage);
            }
            return Files.readAllBytes(path);
        } catch (IOException exception) {
            throw new ApiException(ErrorCode.INTERNAL_ERROR, "读取证据文件失败: " + exception.getMessage());
        }
    }

    private void writeString(Path path, String content) {
        try {
            Files.createDirectories(path.getParent());
            Files.writeString(path, content, StandardCharsets.UTF_8);
        } catch (IOException exception) {
            throw new ApiException(ErrorCode.INTERNAL_ERROR, "写入证据文件失败: " + exception.getMessage());
        }
    }

    private Path snapshotFilePath(String tenantId, String evidenceId) {
        return storageRoot.resolve("snapshots")
            .resolve(safePathToken(tenantId))
            .resolve(safePathToken(evidenceId) + ".json");
    }

    private Path exportFilePath(String tenantId, String archiveDigestHex) {
        return storageRoot.resolve("exports")
            .resolve(safePathToken(tenantId))
            .resolve(safePathToken(archiveDigestHex) + ".ndjson");
    }

    private String snapshotDownloadUri(String evidenceId) {
        return SNAPSHOT_DOWNLOAD_PREFIX + evidenceId + "/file";
    }

    private String exportDownloadUri(String archiveDigestHex) {
        return EXPORT_DOWNLOAD_PREFIX + archiveDigestHex + "/download";
    }

    private String exportLine(EvidenceSnapshot snapshot) {
        return "{"
            + "\"evidenceId\":\"" + escape(snapshot.evidenceId()) + "\","
            + "\"tenantId\":\"" + escape(snapshot.tenantId()) + "\","
            + "\"evidenceType\":\"" + escape(snapshot.evidenceType()) + "\","
            + "\"subjectType\":\"" + escape(snapshot.subjectType()) + "\","
            + "\"subjectId\":\"" + escape(snapshot.subjectId()) + "\","
            + "\"payloadHash\":\"" + escape(snapshot.payloadHash()) + "\","
            + "\"fileUri\":\"" + escape(snapshot.fileUri()) + "\","
            + "\"fileDigest\":\"" + escape(snapshot.fileDigest()) + "\","
            + "\"signatureAlgorithm\":\"" + escape(snapshot.signatureAlgorithm()) + "\","
            + "\"signatureValue\":\"" + escape(snapshot.signatureValue()) + "\""
            + "}";
    }

    private String escape(String value) {
        if (value == null) {
            return "";
        }
        return value
            .replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\n", "\\n")
            .replace("\r", "\\r");
    }

    private String safePathToken(String value) {
        return value == null ? "unknown" : value.replaceAll("[^A-Za-z0-9._-]", "_");
    }

    private record SignatureMaterial(String signatureValue, String signerPublicKey) {
    }
}
