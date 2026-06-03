package com.medkernel.engine.recommendation;

import jakarta.validation.constraints.NotNull;

/**
 * 医师反馈入参：反馈类型必填；ACCEPT / REJECT 必须带结构化原因代码和说明。
 *
 * <p>操作者 id 由 RequestContext 取，角色由前端附带；idempotencyKey 用于同一卡同一反馈请求幂等。
 */
public record RecommendationFeedbackRequest(
    @NotNull RecommendationFeedbackType feedbackType,
    String reasonCode,
    String reasonText,
    String operatorRole,
    String idempotencyKey
) {
    public RecommendationFeedbackRequest(
            RecommendationFeedbackType feedbackType,
            String reasonCode,
            String reasonText,
            String operatorRole) {
        this(feedbackType, reasonCode, reasonText, operatorRole, null);
    }

    public RecommendationFeedbackRequest {
        reasonCode = blankToNull(reasonCode);
        reasonText = blankToNull(reasonText);
        operatorRole = blankToNull(operatorRole);
        idempotencyKey = blankToNull(idempotencyKey);
    }

    private static String blankToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }
}
