package com.medkernel.engine.datasvc;

import java.time.Instant;

/**
 * 临床上下文解释（DATASVC-01 PR2-d，受控工具 {@code getClinicalContextExplanation} 的 D4 输出）。
 *
 * <p>对一个临床 launch 令牌授权的会话，返回**最小授权上下文**：触发点/角色/接入模式/会话有效期 +
 * <b>经不可逆 hash 脱敏的患者/就诊引用</b>（不输出原始患者字段；后续 D4 明文字段落库须走
 * T6.4 字段级加密账本，且 MCP 默认不返回可拼提示词的患者上下文，核心视角 11 / FR-2）。{@code authorized} 为令牌校验结果，
 * {@code reason} 解释授权/拒绝原因；令牌无效/过期/越租户＝诚实拒绝（{@code authorized=false}、不返回临床数据），
 * 仅上游不可用时 {@code degraded=true}（未能校验，不以「未授权」伪装真实校验结果，铁律 #1）。
 */
public record ClinicalContextExplanation(
    boolean authorized,
    String reason,
    String triggerPoint,
    String roleCode,
    String integrationMode,
    String patientRef,
    String encounterRef,
    Instant sessionValidUntil,
    EngineDataLevel dataLevel,
    Instant generatedAt,
    boolean degraded,
    String degradeReason
) {}
