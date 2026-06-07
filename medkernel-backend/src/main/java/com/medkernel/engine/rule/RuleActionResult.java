package com.medkernel.engine.rule;

import java.util.List;

import com.medkernel.engine.cdshook.CdsHookCard;
import com.medkernel.engine.cdshook.CdsHookSource;
import com.medkernel.engine.cdshook.CdsHookSuggestion;

/**
 * 规则 DSL {@code then} 段动作产出值对象（GA-ENG-API-05）。
 *
 * <p>动作结构与 CDS Hooks 卡片字段保持一致；高严重度、阻断、强提醒和建议医嘱动作一律强制医师确认。
 */
public record RuleActionResult(
    RuleActionCode actionCode,
    RuleRiskLevel severity,
    String indicator,
    String summary,
    String detail,
    CdsHookSource source,
    List<CdsHookSuggestion> suggestions,
    List<String> overrideReasons,
    boolean requiresPhysicianConfirmation
) {
    public RuleActionResult {
        suggestions = suggestions == null ? List.of() : List.copyOf(suggestions);
        overrideReasons = overrideReasons == null ? List.of() : List.copyOf(overrideReasons);
    }

    /**
     * 转换为客户面 CDS Hooks 卡片，不生成可自动执行的 system-action。
     */
    public CdsHookCard toCdsHookCard(String uuid) {
        return new CdsHookCard(
            uuid,
            summary,
            detail,
            indicator,
            source,
            suggestions,
            overrideReasons,
            requiresPhysicianConfirmation
        );
    }
}
