package com.medkernel.engine.interop;

import com.medkernel.engine.rule.RuleRiskLevel;
import com.medkernel.engine.rule.RuleType;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * CQL 受控导入规则草稿请求。
 *
 * <p>仅接收 MedKernel 确定性导出的可重放语句形态，不把通用 CQL 执行器引入规则主链路。
 */
public record CqlRuleImportRequest(
    @NotBlank String ruleCode,
    @NotBlank String name,
    @NotNull RuleType ruleType,
    @NotNull RuleRiskLevel riskLevel,
    String applicableOrgUnitId,
    @NotBlank String sourceRef,
    @NotBlank String library,
    @NotBlank String statement
) {
    public CqlRuleImportRequest {
        ruleCode = trim(ruleCode);
        name = trim(name);
        applicableOrgUnitId = trim(applicableOrgUnitId);
        sourceRef = trim(sourceRef);
        library = trim(library);
        statement = trim(statement);
    }

    private static String trim(String value) {
        return value == null ? null : value.trim();
    }
}
