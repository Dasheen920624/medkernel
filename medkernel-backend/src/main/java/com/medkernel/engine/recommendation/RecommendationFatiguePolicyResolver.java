package com.medkernel.engine.recommendation;

import java.util.Optional;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.medkernel.shared.config.SystemConfigItem;
import com.medkernel.shared.config.SystemConfigRepository;
import com.medkernel.shared.context.OrgScope;
import com.medkernel.shared.context.RequestContext;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Component;

/**
 * CDSS 疲劳治理策略解析器。
 *
 * <p>配置中心键 {@code medkernel.cdss.fatigue.policy} 支持按科室、场景或科室+场景覆盖；
 * 配置缺失或非法时不抑制，避免错误配置静默临床安全提醒。
 */
@Component
public class RecommendationFatiguePolicyResolver {

    static final String CONFIG_KEY = "medkernel.cdss.fatigue.policy";
    private static final String SYSTEM_TENANT = "SYSTEM";
    private static final String CONFIG_SOURCE = "CONFIG_CENTER";

    private final SystemConfigRepository configs;
    private final ObjectMapper json;

    public RecommendationFatiguePolicyResolver(SystemConfigRepository configs, ObjectMapper json) {
        this.configs = configs;
        this.json = json;
    }

    public Optional<RecommendationFatiguePolicy> resolve(RecommendationTriggerRequest request) {
        try {
            Optional<SystemConfigItem> item = configs.findActive(SYSTEM_TENANT, CONFIG_KEY);
            if (item.isPresent() && hasText(item.get().value())) {
                return parseConfiguredPolicy(item.get().value(), request);
            }
        } catch (DataAccessException ignored) {
            return Optional.empty();
        }
        return Optional.empty();
    }

    private Optional<RecommendationFatiguePolicy> parseConfiguredPolicy(
            String value,
            RecommendationTriggerRequest request) {
        try {
            JsonNode root = json.readTree(value);
            Optional<RecommendationFatiguePolicy> scoped = firstPresent(
                policyAt(root.path("departmentScenarios").path(departmentScenarioKey(request)), CONFIG_SOURCE),
                policyAt(root.path("departments").path(departmentId()), CONFIG_SOURCE),
                policyAt(root.path("scenarios").path(request.scenarioCode()), CONFIG_SOURCE),
                policyAt(firstExisting(root, "default", "defaults"), CONFIG_SOURCE)
            );
            return scoped;
        } catch (Exception ignored) {
            return Optional.empty();
        }
    }

    @SafeVarargs
    private static <T> Optional<T> firstPresent(Optional<T>... values) {
        for (Optional<T> value : values) {
            if (value.isPresent()) {
                return value;
            }
        }
        return Optional.empty();
    }

    private JsonNode firstExisting(JsonNode root, String first, String second) {
        JsonNode firstNode = root.path(first);
        return firstNode.isMissingNode() ? root.path(second) : firstNode;
    }

    private Optional<RecommendationFatiguePolicy> policyAt(JsonNode node, String source) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return Optional.empty();
        }
        if (!node.has("threshold") && !node.has("windowHours")) {
            return Optional.empty();
        }
        JsonNode threshold = node.path("threshold");
        JsonNode window = node.path("windowHours");
        if (!threshold.canConvertToInt() || !window.canConvertToInt()) {
            throw new IllegalArgumentException("疲劳治理配置字段非法");
        }
        int thresholdValue = threshold.asInt();
        int windowValue = window.asInt();
        if (thresholdValue <= 0 || windowValue <= 0) {
            throw new IllegalArgumentException("疲劳治理配置必须为正整数");
        }
        return Optional.of(new RecommendationFatiguePolicy(thresholdValue, windowValue, source));
    }

    private String departmentScenarioKey(RecommendationTriggerRequest request) {
        return departmentId() + ":" + request.scenarioCode();
    }

    private String departmentId() {
        OrgScope scope = RequestContext.currentOrgScope();
        return scope == null || scope.departmentId() == null ? "" : scope.departmentId();
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
