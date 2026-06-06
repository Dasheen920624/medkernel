package com.medkernel.compliance.evidence;

import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.medkernel.compliance.evidence.domain.EvidenceSnapshot;
import com.medkernel.compliance.evidence.dto.EvidenceCreateDto;
import com.medkernel.compliance.evidence.dto.EvidenceExportResult;
import com.medkernel.compliance.evidence.dto.EvidenceResponse;
import com.medkernel.compliance.evidence.dto.EvidenceVerifyResult;
import com.medkernel.compliance.evidence.repository.EvidenceSnapshotRepository;
import com.medkernel.compliance.evidence.service.EvidenceService;
import com.medkernel.shared.api.error.ApiException;
import com.medkernel.shared.audit.AuditEvent;
import com.medkernel.shared.audit.IsolatedAuditPublisher;
import com.medkernel.shared.crypto.SmCryptoService;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 证据链服务层纯单元测试（无 Spring 上下文，Mockito 驱动）。
 *
 * <p>覆盖核心业务场景：
 * <ul>
 *   <li>创建证据快照（含自动 SM3 指纹与 SM2 签名）</li>
 *   <li>重复创建冲突检测（ENG-EVID-003）</li>
 *   <li>按租户隔离检索</li>
 *   <li>防伪哈希碰撞验签（正常与篡改场景）</li>
 *   <li>跨租户越权访问拒绝</li>
 *   <li>异步导出审计留痕</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
class EvidenceServiceTest {

    @Mock
    EvidenceSnapshotRepository repository;

    @Mock
    IsolatedAuditPublisher isolatedAudit;

    EvidenceService service;

    private static final String TENANT_ID = "tenant-hospital-01";
    private static final String EVIDENCE_ID = "evd-compliance-001";
    private static final String PAYLOAD = "{\"approvalId\":\"exp-001\",\"result\":\"APPROVED\"}";

    private SmCryptoService crypto;
    private EvidenceSnapshot validSnapshot;

    @BeforeEach
    void setUp() throws Exception {
        crypto = new SmCryptoService();
        service = new EvidenceService(repository, isolatedAudit, crypto);
        validSnapshot = signedSnapshot(
            1L,
            EVIDENCE_ID,
            "trace-001",
            "COMPLIANCE_EXPORT",
            "CREATE",
            "export_approval",
            "exp-001",
            "合规导出审批证据",
            PAYLOAD
        );
    }

    // ── 创建存证 ──────────────────────────────────────────────

    @Test
    @DisplayName("创建证据快照：自动计算 SM3 指纹、真实文件 URI 与 SM2 签名并入库")
    void createSnapshot_success() {
        EvidenceCreateDto dto = new EvidenceCreateDto(
            EVIDENCE_ID, "trace-001", "COMPLIANCE_EXPORT", "CREATE",
            "export_approval", "exp-001",
            "合规导出审批证据", PAYLOAD
        );

        when(repository.findByEvidenceId(EVIDENCE_ID)).thenReturn(Optional.empty());
        when(repository.save(any(EvidenceSnapshot.class))).thenAnswer(inv -> {
            EvidenceSnapshot arg = inv.getArgument(0);
            return new EvidenceSnapshot(
                1L, arg.evidenceId(), arg.tenantId(), arg.traceId(),
                arg.evidenceType(), arg.action(), arg.subjectType(), arg.subjectId(),
                arg.evidenceSummary(), arg.payloadSnapshot(), arg.payloadHash(),
                arg.fileUri(), arg.fileDigest(), arg.signatureAlgorithm(),
                arg.signatureValue(), arg.signerPublicKey(),
                arg.createdAt(), arg.createdBy(), arg.updatedAt(), arg.updatedBy()
            );
        });

        EvidenceResponse resp = service.createSnapshot(TENANT_ID, dto);

        assertThat(resp).isNotNull();
        assertThat(resp.evidenceId()).isEqualTo(EVIDENCE_ID);
        assertThat(resp.payloadHash()).matches("sm3:[0-9a-f]{64}");
        assertThat(resp.fileUri()).isEqualTo("/api/v1/compliance/evidence/snapshots/" + EVIDENCE_ID + "/file");
        assertThat(resp.fileDigest()).matches("sm3:[0-9a-f]{64}");
        assertThat(resp.signatureAlgorithm()).isEqualTo("SM3_WITH_SM2");
        assertThat(resp.signatureValue()).isNotBlank();
        assertThat(resp.signerPublicKey()).isNotBlank();
        assertThat(resp.isValid()).isTrue();

        // 确认保存时证据文件、国密摘要和签名材料均非空
        ArgumentCaptor<EvidenceSnapshot> captor = ArgumentCaptor.forClass(EvidenceSnapshot.class);
        verify(repository).save(captor.capture());
        assertThat(captor.getValue().payloadHash()).matches("sm3:[0-9a-f]{64}");
        assertThat(captor.getValue().fileUri()).isEqualTo(resp.fileUri());
        assertThat(captor.getValue().fileDigest()).isEqualTo(resp.fileDigest());
        assertThat(captor.getValue().signatureAlgorithm()).isEqualTo("SM3_WITH_SM2");
        assertThat(captor.getValue().signatureValue()).isEqualTo(resp.signatureValue());
        assertThat(captor.getValue().signerPublicKey()).isEqualTo(resp.signerPublicKey());
    }

    @Test
    @DisplayName("创建证据快照：重复 evidenceId 抛出 ENG-EVID-003 冲突异常")
    void createSnapshot_duplicateId_throwsConflict() {
        EvidenceCreateDto dto = new EvidenceCreateDto(
            EVIDENCE_ID, "trace-001", "COMPLIANCE_EXPORT", "CREATE",
            "export_approval", "exp-001",
            "合规导出审批证据", PAYLOAD
        );
        when(repository.findByEvidenceId(EVIDENCE_ID)).thenReturn(Optional.of(validSnapshot));

        assertThatThrownBy(() -> service.createSnapshot(TENANT_ID, dto))
            .isInstanceOf(ApiException.class)
            .hasMessageContaining("已存在");
    }

    // ── 分页检索 ──────────────────────────────────────────────

    @Test
    @DisplayName("分页检索：返回当前租户的证据列表")
    void getEvidences_returnsList() {
        when(repository.findEvidencesPage(any(), any(), any(), anyInt(), anyInt()))
            .thenReturn(List.of(validSnapshot));

        List<EvidenceResponse> result = service.getEvidences(TENANT_ID, null, "COMPLIANCE_EXPORT", 1, 20);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).evidenceId()).isEqualTo(EVIDENCE_ID);
    }

    @Test
    @DisplayName("计数查询：返回过滤后的总数")
    void countEvidences_returnsTotal() {
        when(repository.countEvidences(TENANT_ID, "COMPLIANCE_EXPORT", null)).thenReturn(42L);

        long total = service.countEvidences(TENANT_ID, null, "COMPLIANCE_EXPORT");
        assertThat(total).isEqualTo(42L);
    }

    // ── 详情查询 ──────────────────────────────────────────────

    @Test
    @DisplayName("详情查询：按 evidenceId 返回完整响应")
    void getEvidenceById_success() {
        when(repository.findByEvidenceId(EVIDENCE_ID)).thenReturn(Optional.of(validSnapshot));

        EvidenceResponse resp = service.getEvidenceById(TENANT_ID, EVIDENCE_ID);
        assertThat(resp.evidenceId()).isEqualTo(EVIDENCE_ID);
        assertThat(resp.payloadSnapshot()).isEqualTo(PAYLOAD);
    }

    @Test
    @DisplayName("详情查询：不存在的 evidenceId 抛出 ENG-EVID-001")
    void getEvidenceById_notFound_throws() {
        when(repository.findByEvidenceId("evd-not-exist")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getEvidenceById(TENANT_ID, "evd-not-exist"))
            .isInstanceOf(ApiException.class)
            .hasMessageContaining("未找到");
    }

    @Test
    @DisplayName("详情查询：跨租户越权访问抛出 TENANT_FORBIDDEN")
    void getEvidenceById_wrongTenant_throws() {
        when(repository.findByEvidenceId(EVIDENCE_ID)).thenReturn(Optional.of(validSnapshot));

        assertThatThrownBy(() -> service.getEvidenceById("other-tenant", EVIDENCE_ID))
            .isInstanceOf(ApiException.class);
    }

    // ── 防伪验签 ──────────────────────────────────────────────

    @Test
    @DisplayName("验签成功：合法快照通过双向国密验证，记录成功审计")
    void verifyEvidence_validHash_succeeds() {
        when(repository.findByEvidenceId(EVIDENCE_ID)).thenReturn(Optional.of(validSnapshot));

        EvidenceVerifyResult result = service.verifyEvidence(TENANT_ID, EVIDENCE_ID);

        assertThat(result.isValid()).isTrue();
        assertThat(result.calculatedHash()).isEqualTo(result.storedHash());
        assertThat(result.signatureAlgorithm()).isEqualTo("SM3_WITH_SM2");
        assertThat(result.signatureValid()).isTrue();

        // 验证发布了成功审计事件
        ArgumentCaptor<AuditEvent> captor = ArgumentCaptor.forClass(AuditEvent.class);
        verify(isolatedAudit).publishInNewTx(captor.capture());
        assertThat(captor.getValue().outcome()).isEqualTo(AuditEvent.OUTCOME_SUCCESS);
    }

    @Test
    @DisplayName("验签失败：篡改后的快照触发高危入侵审计（outcome=FAILED）")
    void verifyEvidence_tamperedData_triggersFailureAudit() {
        // 构建一个被篡改的快照：payload 被修改但 hash 保持原值
        EvidenceSnapshot tampered = new EvidenceSnapshot(
            1L, EVIDENCE_ID, TENANT_ID, "trace-001",
            "COMPLIANCE_EXPORT", "CREATE", "export_approval", "exp-001",
            "合规导出审批证据",
            "{\"approvalId\":\"exp-001\",\"result\":\"TAMPERED\"}",
            validSnapshot.payloadHash(),
            validSnapshot.fileUri(), validSnapshot.fileDigest(), validSnapshot.signatureAlgorithm(),
            validSnapshot.signatureValue(), validSnapshot.signerPublicKey(),
            validSnapshot.createdAt(), "system", validSnapshot.updatedAt(), "system"
        );

        when(repository.findByEvidenceId(EVIDENCE_ID)).thenReturn(Optional.of(tampered));

        EvidenceVerifyResult result = service.verifyEvidence(TENANT_ID, EVIDENCE_ID);

        assertThat(result.isValid()).isFalse();
        assertThat(result.calculatedHash()).isNotEqualTo(result.storedHash());
        assertThat(result.signatureAlgorithm()).isEqualTo("SM3_WITH_SM2");
        assertThat(result.signatureValid()).isFalse();

        // 验证发布了失败审计事件
        ArgumentCaptor<AuditEvent> captor = ArgumentCaptor.forClass(AuditEvent.class);
        verify(isolatedAudit).publishInNewTx(captor.capture());
        assertThat(captor.getValue().outcome()).isEqualTo(AuditEvent.OUTCOME_FAILED);
        assertThat(captor.getValue().errorCode()).isEqualTo("ENG-EVID-002");
    }

    // ── 异步导出 ──────────────────────────────────────────────

    @Test
    @DisplayName("导出证据：对真实快照生成国密归档摘要和真实下载 URI，并发布成功审计")
    void exportEvidences_returnsDigestUriAndPublishesAudit() {
        when(repository.countEvidences(TENANT_ID, "COMPLIANCE_EXPORT", null)).thenReturn(1L);
        when(repository.findEvidencesPage(eq(TENANT_ID), eq("COMPLIANCE_EXPORT"), isNull(), anyInt(), anyInt()))
            .thenReturn(List.of(validSnapshot));

        EvidenceExportResult result = service.exportEvidences(TENANT_ID, "COMPLIANCE_EXPORT");

        assertThat(result.archiveHash()).matches("sm3:[0-9a-f]{64}");
        assertThat(result.archiveUri()).matches("/api/v1/compliance/evidence/snapshots/export/[0-9a-f]{64}/download");
        assertThat(result.contentType()).isEqualTo("application/x-ndjson");
        assertThat(result.itemCount()).isEqualTo(1);
        assertThat(result.status()).isEqualTo("COMPLETED");

        // 验证审计记录已发布
        verify(isolatedAudit).publishInNewTx(any(AuditEvent.class));
    }

    @Test
    @DisplayName("导出全量证据（无类型过滤）：审计标记 ALL 并生成真实归档指纹")
    void exportEvidences_noTypeFilter_usesAllMarker() {
        when(repository.countEvidences(TENANT_ID, null, null)).thenReturn(1L);
        when(repository.findEvidencesPage(eq(TENANT_ID), isNull(), isNull(), anyInt(), anyInt()))
            .thenReturn(List.of(validSnapshot));

        EvidenceExportResult result = service.exportEvidences(TENANT_ID, null);

        assertThat(result.archiveHash()).matches("sm3:[0-9a-f]{64}");
        assertThat(result.archiveUri()).contains("/snapshots/export/");

        ArgumentCaptor<AuditEvent> captor = ArgumentCaptor.forClass(AuditEvent.class);
        verify(isolatedAudit).publishInNewTx(captor.capture());
        assertThat(captor.getValue().resourceId()).contains("ALL");
    }

    @Test
    @DisplayName("导出证据：范围内无快照时拒绝导出（不生成伪造指纹、不留导出审计）")
    void exportEvidences_emptySet_throws() {
        when(repository.countEvidences(TENANT_ID, "RULE_DEFINITION", null)).thenReturn(0L);

        assertThatThrownBy(() -> service.exportEvidences(TENANT_ID, "RULE_DEFINITION"))
            .isInstanceOf(ApiException.class)
            .hasMessageContaining("无可导出");

        verify(isolatedAudit, never()).publishInNewTx(any(AuditEvent.class));
    }

    @Test
    @DisplayName("导出证据：对真实快照集合计算确定性真 SM3（非随机假串）")
    void exportEvidences_computesDeterministicRealHash() throws Exception {
        EvidenceSnapshot tempB = new EvidenceSnapshot(
            null, "evd-compliance-002", TENANT_ID, "trace-002",
            "COMPLIANCE_EXPORT", "CREATE", "export_approval", "exp-002",
            "合规导出复核证据", "{\"approvalId\":\"exp-002\",\"result\":\"APPROVED\"}",
            "",
            "/api/v1/compliance/evidence/snapshots/evd-compliance-002/file",
            "",
            EvidenceSnapshot.SIGNATURE_ALGORITHM,
            "",
            "",
            Instant.now(), "system", Instant.now(), "system"
        );
        EvidenceSnapshot second = signedSnapshot(2L, tempB.evidenceId(), tempB.traceId(), tempB.evidenceType(),
            tempB.action(), tempB.subjectType(), tempB.subjectId(), tempB.evidenceSummary(), tempB.payloadSnapshot());

        when(repository.countEvidences(TENANT_ID, "COMPLIANCE_EXPORT", null)).thenReturn(2L);
        when(repository.findEvidencesPage(eq(TENANT_ID), eq("COMPLIANCE_EXPORT"), isNull(), anyInt(), anyInt()))
            .thenReturn(List.of(validSnapshot, second));

        EvidenceExportResult result1 = service.exportEvidences(TENANT_ID, "COMPLIANCE_EXPORT");
        EvidenceExportResult result2 = service.exportEvidences(TENANT_ID, "COMPLIANCE_EXPORT");

        assertThat(result1.archiveHash()).matches("sm3:[0-9a-f]{64}");
        assertThat(result1.archiveHash()).isEqualTo(result2.archiveHash());
        assertThat(result1.archiveUri()).isEqualTo(result2.archiveUri());
        assertThat(result1.archiveHash()).doesNotContain("proof");
        verify(isolatedAudit, atLeastOnce()).publishInNewTx(any(AuditEvent.class));
    }

    private EvidenceSnapshot signedSnapshot(Long id, String evidenceId, String traceId, String evidenceType,
                                            String action, String subjectType, String subjectId,
                                            String evidenceSummary, String payload) throws Exception {
        Instant now = Instant.now();
        EvidenceSnapshot unsigned = new EvidenceSnapshot(
            id,
            evidenceId,
            TENANT_ID,
            traceId,
            evidenceType,
            action,
            subjectType,
            subjectId,
            evidenceSummary,
            payload,
            "",
            "/api/v1/compliance/evidence/snapshots/" + evidenceId + "/file",
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
            id,
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
        KeyPair keyPair = crypto.generateSm2KeyPair();
        String signature = crypto.base64Encode(crypto.sm2Sign(
            keyPair.getPrivate(),
            signable.signaturePayload().getBytes(StandardCharsets.UTF_8)
        ));
        String publicKey = crypto.base64Encode(keyPair.getPublic().getEncoded());
        return new EvidenceSnapshot(
            signable.id(),
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
            signature,
            publicKey,
            signable.createdAt(),
            signable.createdBy(),
            signable.updatedAt(),
            signable.updatedBy()
        );
    }
}
