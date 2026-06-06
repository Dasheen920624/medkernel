package com.medkernel.compliance.masking;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * SYS-06 后端脱敏执行结果。
 */
public record MaskingResult(
    String resourceType,
    String scenarioCode,
    Map<String, Object> values,
    List<String> maskedFields,
    boolean rawAllowed
) {

    public MaskingResult {
        values = Collections.unmodifiableMap(new LinkedHashMap<>(values));
        maskedFields = List.copyOf(maskedFields);
    }
}
