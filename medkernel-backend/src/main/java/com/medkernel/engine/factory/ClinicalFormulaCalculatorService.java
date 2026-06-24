package com.medkernel.engine.factory;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.stereotype.Service;

import com.medkernel.shared.api.error.ApiException;
import com.medkernel.shared.api.error.ErrorCode;

/**
 * 评分量表/计算器确定性算子。
 *
 * <p>算法完全来自传入的计算公式定义；本服务不内置任何医学评分量表常量。
 */
@Service
public class ClinicalFormulaCalculatorService {

    /** 按公式定义和输入事实计算得分；缺必填输入时诚实返回不可执行。 */
    public ClinicalFormulaResult calculate(ClinicalFormulaDefinition definition, Map<String, BigDecimal> inputs) {
        validateDefinition(definition);
        Map<String, BigDecimal> safeInputs = inputs == null ? Map.of() : inputs;
        List<String> missing = missingInputs(definition, safeInputs);
        if (!missing.isEmpty()) {
            return new ClinicalFormulaResult(definition.code(), null, missing, false);
        }
        BigDecimal score = definition.intercept();
        for (ClinicalFormulaTerm term : definition.terms()) {
            BigDecimal value = safeInputs.get(term.inputKey());
            if (value != null) {
                score = score.add(value.multiply(term.coefficient()));
            }
        }
        return new ClinicalFormulaResult(definition.code(), score, List.of(), true);
    }

    private void validateDefinition(ClinicalFormulaDefinition definition) {
        if (definition == null || definition.code() == null || definition.code().isBlank()) {
            throw new ApiException(ErrorCode.BAD_REQUEST, "公式定义不能为空");
        }
        Set<String> inputKeys = new LinkedHashSet<>();
        for (ClinicalFormulaInput input : definition.inputs()) {
            if (input == null || input.key() == null || input.key().isBlank()) {
                throw new ApiException(ErrorCode.BAD_REQUEST, "公式输入项不能为空");
            }
            inputKeys.add(input.key());
        }
        if (inputKeys.isEmpty()) {
            throw new ApiException(ErrorCode.BAD_REQUEST, "公式至少需要一个输入项");
        }
        for (ClinicalFormulaTerm term : definition.terms()) {
            if (term == null || term.inputKey() == null || !inputKeys.contains(term.inputKey())) {
                String key = term == null ? "" : term.inputKey();
                throw new ApiException(ErrorCode.BAD_REQUEST, "公式项引用未知输入项: " + key);
            }
            if (term.coefficient() == null) {
                throw new ApiException(ErrorCode.BAD_REQUEST, "公式项系数不能为空");
            }
        }
    }

    private List<String> missingInputs(ClinicalFormulaDefinition definition, Map<String, BigDecimal> inputs) {
        List<String> missing = new ArrayList<>();
        for (ClinicalFormulaInput input : definition.inputs()) {
            if (input.required() && !inputs.containsKey(input.key())) {
                missing.add(input.key());
            }
        }
        return missing;
    }
}
