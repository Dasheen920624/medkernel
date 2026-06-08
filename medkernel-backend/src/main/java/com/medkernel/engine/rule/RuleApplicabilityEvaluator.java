package com.medkernel.engine.rule;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.HashSet;
import java.util.Set;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.medkernel.shared.api.error.ApiException;
import com.medkernel.shared.api.error.ErrorCode;
import com.medkernel.shared.context.OrgScope;

/**
 * 规则适用域确定性判定器。
 *
 * <p>按人群、组织、临床场景、生效期和稳定灰度顺序判定。任一维度不适用即停止，
 * 不进入规则条件求值；灰度分桶仅使用规则版本与患者或就诊业务标识，不读取随机数。
 */
@Component
public class RuleApplicabilityEvaluator {

    private static final Set<String> CLINICAL_SETTINGS =
        Set.of("INPATIENT", "OUTPATIENT", "ED", "FOLLOWUP");

    private final ObjectMapper json;
    private final ConditionEvaluator conditionEvaluator;
    private final Clock clock;

    /**
     * 创建生产判定器，所有日期边界统一按 UTC 计算。
     */
    @Autowired
    public RuleApplicabilityEvaluator(ObjectMapper json, ConditionEvaluator conditionEvaluator) {
        this(json, conditionEvaluator, Clock.systemUTC());
    }

    public RuleApplicabilityEvaluator(ObjectMapper json) {
        this(json, Clock.systemUTC());
    }

    RuleApplicabilityEvaluator(ObjectMapper json, Clock clock) {
        this(json, new ConditionEvaluator(json), clock);
    }

    RuleApplicabilityEvaluator(ObjectMapper json, ConditionEvaluator conditionEvaluator, Clock clock) {
        this.json = json;
        this.conditionEvaluator = conditionEvaluator == null ? new ConditionEvaluator(json) : conditionEvaluator;
        this.clock = clock;
    }

    /**
     * 校验适用域 DSL 的完整结构与边界。
     */
    public void validate(JsonNode applicability) {
        if (applicability == null || !applicability.isObject()) {
            throw invalid("规则 DSL 缺少 applicability 适用域");
        }

        JsonNode population = requireObject(applicability, "population");
        validatePopulationCondition(population, "include");
        validatePopulationCondition(population, "exclude");

        JsonNode orgScope = requireObject(applicability, "orgScope");
        validateStringArray(orgScope, "groupIds");
        validateStringArray(orgScope, "hospitalIds");
        validateStringArray(orgScope, "deptIds");

        JsonNode settings = applicability.get("settings");
        if (settings == null || !settings.isArray() || settings.isEmpty()) {
            throw invalid("规则 applicability.settings 至少包含一个临床场景");
        }
        Set<String> uniqueSettings = new HashSet<>();
        for (JsonNode setting : settings) {
            String value = setting.isTextual() ? setting.asText() : null;
            if (value == null || !CLINICAL_SETTINGS.contains(value)) {
                throw invalid("规则 applicability.settings 仅允许 INPATIENT/OUTPATIENT/ED/FOLLOWUP");
            }
            if (!uniqueSettings.add(value)) {
                throw invalid("规则 applicability.settings 不允许重复值: " + value);
            }
        }

        JsonNode effective = requireObject(applicability, "effective");
        LocalDate from = optionalDate(effective, "from");
        LocalDate to = optionalDate(effective, "to");
        if (from != null && to != null && from.isAfter(to)) {
            throw invalid("规则 applicability.effective.from 不能晚于 to");
        }
        JsonNode rollout = effective.get("rolloutPercent");
        if (rollout == null || !rollout.canConvertToInt() || !rollout.isIntegralNumber()) {
            throw invalid("规则 applicability.effective.rolloutPercent 必须是 0 到 100 的整数");
        }
        int rolloutPercent = rollout.asInt();
        if (rolloutPercent < 0 || rolloutPercent > 100) {
            throw invalid("规则 applicability.effective.rolloutPercent 必须是 0 到 100 的整数");
        }
    }

    /**
     * 在当前标准上下文与组织上下文中判定规则版本是否适用。
     */
    public RuleApplicabilityDecision evaluate(
            JsonNode applicability,
            JsonNode context,
            OrgScope orgScope,
            String versionId) {
        validate(applicability);
        JsonNode safeContext = context == null ? json.createObjectNode() : context;
        ObjectNode details = json.createObjectNode();

        JsonNode population = applicability.path("population");
        JsonNode exclude = population.get("exclude");
        if (exclude != null && conditionEvaluator.evaluate(exclude, safeContext).matched()) {
            return decision(false, "POPULATION_EXCLUDED", "患者命中适用域排除条件", details);
        }
        JsonNode include = population.get("include");
        if (include != null && !conditionEvaluator.evaluate(include, safeContext).matched()) {
            return decision(false, "POPULATION_NOT_INCLUDED", "患者未命中适用域纳入条件", details);
        }

        JsonNode configuredOrgScope = applicability.path("orgScope");
        OrgScope safeOrgScope = orgScope == null ? OrgScope.empty() : orgScope;
        if (!matchesDimension(configuredOrgScope.path("groupIds"), safeOrgScope.groupId())
                || !matchesDimension(configuredOrgScope.path("hospitalIds"), safeOrgScope.hospitalId())
                || !matchesDimension(configuredOrgScope.path("deptIds"), safeOrgScope.departmentId())) {
            return decision(false, "ORG_SCOPE_MISMATCH", "当前组织不在规则适用范围内", details);
        }

        String setting = clinicalSetting(safeContext);
        details.put("clinicalSetting", setting);
        if (setting == null) {
            return decision(false, "SETTING_MISSING", "标准上下文缺少就诊场景", details);
        }
        if (!containsText(applicability.path("settings"), setting)) {
            return decision(false, "SETTING_MISMATCH", "当前就诊场景不在规则适用范围内", details);
        }

        LocalDate today = LocalDate.now(clock);
        details.put("evaluationDate", today.toString());
        JsonNode effective = applicability.path("effective");
        LocalDate from = optionalDate(effective, "from");
        LocalDate to = optionalDate(effective, "to");
        if (from != null && today.isBefore(from)) {
            return decision(false, "NOT_YET_EFFECTIVE", "规则尚未进入生效期", details);
        }
        if (to != null && today.isAfter(to)) {
            return decision(false, "EXPIRED", "规则已超过生效期", details);
        }

        int rolloutPercent = effective.path("rolloutPercent").asInt();
        details.put("rolloutPercent", rolloutPercent);
        if (rolloutPercent == 0) {
            return decision(false, "ROLLOUT_EXCLUDED", "当前规则灰度比例为 0", details);
        }
        if (rolloutPercent < 100) {
            String subjectId = stableSubjectId(safeContext);
            if (subjectId == null) {
                return decision(
                    false,
                    "ROLLOUT_IDENTITY_MISSING",
                    "灰度判定缺少患者或就诊稳定标识",
                    details);
            }
            int bucket = rolloutBucket(versionId, subjectId);
            details.put("rolloutBucket", bucket);
            if (bucket >= rolloutPercent) {
                return decision(false, "ROLLOUT_EXCLUDED", "当前患者不在规则灰度范围内", details);
            }
        }

        return decision(true, "APPLICABLE", "当前上下文满足规则适用域", details);
    }

    private void validatePopulationCondition(JsonNode population, String field) {
        JsonNode condition = population.get(field);
        if (condition == null) {
            return;
        }
        if (!condition.isObject()) {
            throw invalid("规则 applicability.population." + field + " 必须是条件对象");
        }
        conditionEvaluator.evaluate(condition, json.createObjectNode());
    }

    private void validateStringArray(JsonNode source, String field) {
        JsonNode values = source.get(field);
        if (values == null) {
            return;
        }
        if (!values.isArray()) {
            throw invalid("规则 applicability.orgScope." + field + " 必须是字符串数组");
        }
        Set<String> uniqueValues = new HashSet<>();
        for (JsonNode value : values) {
            String text = value.isTextual() ? value.asText().trim() : "";
            if (text.isBlank()) {
                throw invalid("规则 applicability.orgScope." + field + " 仅允许非空字符串");
            }
            if (!uniqueValues.add(text)) {
                throw invalid("规则 applicability.orgScope." + field + " 不允许重复值: " + text);
            }
        }
    }

    private JsonNode requireObject(JsonNode source, String field) {
        JsonNode value = source.get(field);
        if (value == null || !value.isObject()) {
            throw invalid("规则 applicability." + field + " 必须是对象");
        }
        return value;
    }

    private LocalDate optionalDate(JsonNode source, String field) {
        JsonNode value = source.get(field);
        if (value == null || value.isNull()) {
            return null;
        }
        if (!value.isTextual() || value.asText().isBlank()) {
            throw invalid("规则 applicability.effective." + field + " 必须是 ISO 日期");
        }
        try {
            return LocalDate.parse(value.asText());
        } catch (DateTimeParseException exception) {
            throw invalid("规则 applicability.effective." + field + " 必须是 ISO 日期");
        }
    }

    private static boolean matchesDimension(JsonNode configured, String actual) {
        if (!configured.isArray() || configured.isEmpty()) {
            return true;
        }
        return actual != null && !actual.isBlank() && containsText(configured, actual);
    }

    private static boolean containsText(JsonNode values, String expected) {
        if (!values.isArray() || expected == null) {
            return false;
        }
        for (JsonNode value : values) {
            if (value.isTextual() && expected.equals(value.asText())) {
                return true;
            }
        }
        return false;
    }

    private static String clinicalSetting(JsonNode context) {
        JsonNode encounters = context.path("encounters");
        if (!encounters.isArray() || encounters.isEmpty()) {
            return null;
        }
        String value = encounters.get(0).path("encounterType").asText(null);
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static String stableSubjectId(JsonNode context) {
        String patientId = context.path("patient").path("mpi").asText(null);
        if (patientId == null || patientId.isBlank()) {
            patientId = context.path("patient").path("patientId").asText(null);
        }
        if (patientId == null || patientId.isBlank()) {
            patientId = context.path("patientId").asText(null);
        }
        if (patientId != null && !patientId.isBlank()) {
            return "patient:" + patientId.trim();
        }
        JsonNode encounters = context.path("encounters");
        if (encounters.isArray() && !encounters.isEmpty()) {
            String encounterId = encounters.get(0).path("encounterId").asText(null);
            if (encounterId != null && !encounterId.isBlank()) {
                return "encounter:" + encounterId.trim();
            }
        }
        String encounterId = context.path("encounterId").asText(null);
        return encounterId == null || encounterId.isBlank() ? null : "encounter:" + encounterId.trim();
    }

    private static int rolloutBucket(String versionId, String subjectId) {
        if (versionId == null || versionId.isBlank()) {
            throw invalid("规则版本 ID 不能为空");
        }
        byte[] digest;
        try {
            digest = MessageDigest.getInstance("SHA-256")
                .digest((versionId + ":" + subjectId).getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("JDK 缺少 SHA-256", exception);
        }
        int value = ((digest[0] & 0xff) << 24)
            | ((digest[1] & 0xff) << 16)
            | ((digest[2] & 0xff) << 8)
            | (digest[3] & 0xff);
        return Math.floorMod(value, 100);
    }

    private RuleApplicabilityDecision decision(
            boolean applicable,
            String reasonCode,
            String reason,
            ObjectNode details) {
        return new RuleApplicabilityDecision(applicable, reasonCode, reason, details.deepCopy());
    }

    private static ApiException invalid(String message) {
        return new ApiException(ErrorCode.ENG_RULE_001, message);
    }
}
