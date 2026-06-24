package com.medkernel.engine.datasvc;

import java.time.Instant;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.medkernel.engine.embed.EmbedLaunchToken;
import com.medkernel.engine.embed.EmbedLaunchTokenRepository;
import com.medkernel.shared.api.error.ApiException;
import com.medkernel.shared.audit.AuditAction;
import com.medkernel.shared.audit.AuditRecorder;
import com.medkernel.shared.context.OrgScope;
import com.medkernel.shared.context.RequestContext;
import com.medkernel.shared.hash.Sha256ContentHash;

/**
 * 引擎数据服务层 · 临床上下文服务（DATASVC-01 PR2-d，受控工具 {@code getClinicalContextExplanation} 上游）。
 *
 * <p>对临床启动凭证授权的会话，只读校验 {@code embed_launch_token}（engine-embed 所属，仅读不消费、不写、
 * 不违域归属）并强制租户隔离、过期/状态校验，授权时返回**最小授权上下文**：触发点/角色/接入模式/会话有效期 +
 * <b>经不可逆 SHA-256 脱敏的患者/就诊引用</b>（不输出原始患者字段；后续 D4 明文字段落库须走
 * T6.4 字段级加密账本，且 MCP 默认不返回可拼提示词的患者上下文，核心视角 11 / FR-2）。凭证无效/过期/跨服务机构＝诚实拒绝（{@code authorized=false}、
 * 不返回临床数据，不泄漏凭证是否存在于其他服务机构）；仅上游不可用时 {@code degraded=true}，不以「未授权」伪装真实校验结果（铁律 #1）。
 */
@Service
public class ClinicalContextService {

    private final EmbedLaunchTokenRepository launchTokenRepository;
    private final AuditRecorder auditRecorder;

    public ClinicalContextService(EmbedLaunchTokenRepository launchTokenRepository,
            AuditRecorder auditRecorder) {
        this.launchTokenRepository = launchTokenRepository;
        this.auditRecorder = auditRecorder;
    }

    /**
     * 解释临床启动凭证授权的会话上下文（D4，患者引用脱敏）。凭证无效/过期/跨服务机构诚实拒绝；上游不可用诚实降级。
     */
    @Transactional(readOnly = true)
    public ClinicalContextExplanation explainContext(String launchToken, String purpose) {
        String tenantId = requireCurrentTenant();

        EmbedLaunchToken token;
        try {
            token = launchTokenRepository.findByToken(launchToken).orElse(null);
        } catch (RuntimeException upstreamDown) {
            auditRecorder.record(AuditAction.EXECUTE, "embed_launch_token", "explain-clinical-context",
                "临床上下文解释上游不可用，已诚实降级：" + upstreamDown.getMessage());
            return denied("上游临床启动凭证服务暂不可用，未能校验授权（未返回临床上下文）", true);
        }

        // 凭证不存在或不属于当前服务机构：诚实拒绝，不泄漏其是否存在于其他服务机构。
        if (token == null || !tenantId.equals(token.tenantId())) {
            auditRecorder.record(AuditAction.EXECUTE, "embed_launch_token", "explain-clinical-context",
                "临床启动凭证无效或跨服务机构，已诚实拒绝（未返回临床上下文） 用途=" + purpose);
            return denied("临床启动凭证无效，未返回临床上下文", false);
        }

        boolean expired = token.expiredAt() == null || !token.expiredAt().isAfter(Instant.now());
        boolean inactive = "EXPIRED".equals(token.status()) || "REVOKED".equals(token.status());
        if (expired || inactive) {
            auditRecorder.record(AuditAction.EXECUTE, "embed_launch_token", "explain-clinical-context",
                "临床启动凭证已过期或已撤销，已诚实拒绝（未返回临床上下文） 用途=" + purpose);
            return denied("临床启动凭证已过期或已撤销，未返回临床上下文", false);
        }

        auditRecorder.record(AuditAction.EXECUTE, "embed_launch_token", "explain-clinical-context",
            "解释授权临床会话上下文 D4（患者引用已脱敏） tenant=" + tenantId
            + " 触发点=" + token.triggerPoint() + " 用途=" + purpose);
        return new ClinicalContextExplanation(true, "临床启动凭证已校验，返回最小授权上下文（患者引用已脱敏）",
            token.triggerPoint(), token.roleCode(), token.integrationMode(),
            maskRef(token.patientId()), maskRef(token.encounterId()), token.expiredAt(),
            EngineDataLevel.D4, Instant.now(), false, null);
    }

    private ClinicalContextExplanation denied(String reason, boolean degraded) {
        return new ClinicalContextExplanation(false, reason, null, null, null, null, null, null,
            EngineDataLevel.D4, Instant.now(), degraded, degraded ? reason : null);
    }

    /** 患者/就诊引用脱敏：不可逆 SHA-256 截断伪名引用，不输出原始标识（FR-2 / 视角 11）。 */
    private static String maskRef(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        return "ref:" + Sha256ContentHash.sha256(raw, "脱敏引用不可为空").substring(0, 12);
    }

    private String requireCurrentTenant() {
        OrgScope scope = RequestContext.currentOrgScope();
        if (scope == null || !scope.hasTenant()) {
            throw ApiException.tenantMissing();
        }
        return scope.tenantId();
    }
}
