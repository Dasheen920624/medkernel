package com.medkernel.engine.rule;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Objects;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.medkernel.shared.context.OrgScope;
import org.springframework.stereotype.Service;

/**
 * 规则适用域唯一应用服务。
 *
 * <p>DSL 中的 {@code applicability} 是权威数据；本服务统一承担结构校验、运行期判定和
 * 关系库检索镜像写入，避免规则创建、配置包导入和推荐执行各自实现一套边界。
 */
@Service
public class RuleApplicabilityService {

    private final RuleApplicabilityRepository repository;
    private final RuleApplicabilityEvaluator evaluator;
    private final ObjectMapper json;

    public RuleApplicabilityService(
            RuleApplicabilityRepository repository,
            RuleApplicabilityEvaluator evaluator,
            ObjectMapper json) {
        this.repository = Objects.requireNonNull(repository, "规则适用域仓库不能为空");
        this.evaluator = Objects.requireNonNull(evaluator, "规则适用域判定器不能为空");
        this.json = Objects.requireNonNull(json, "JSON 处理器不能为空");
    }

    /**
     * 校验规则 DSL 中的完整适用域。
     */
    public void validateDsl(JsonNode dsl) {
        evaluator.validate(dsl == null ? null : dsl.get("applicability"));
    }

    /**
     * 使用当前标准上下文和组织上下文判定规则版本是否适用。
     */
    public RuleApplicabilityDecision evaluate(
            JsonNode dsl,
            JsonNode context,
            OrgScope orgScope,
            String versionId) {
        return evaluator.evaluate(
            dsl == null ? null : dsl.get("applicability"),
            context,
            orgScope,
            versionId);
    }

    /**
     * 从权威 DSL 刷新结构化检索镜像。
     */
    public RuleApplicability saveMirror(
            RuleVersion version,
            JsonNode dsl,
            Instant now,
            String actor,
            String traceId) {
        validateDsl(dsl);
        JsonNode applicability = dsl.path("applicability");
        JsonNode effective = applicability.path("effective");
        RuleApplicability existing = repository
            .findByTenantIdAndRuleVersionId(version.tenantId(), version.versionId())
            .orElse(null);
        RuleApplicability mirror = new RuleApplicability(
            existing == null ? null : existing.id(),
            version.versionId(),
            version.tenantId(),
            jsonValue(applicability.path("population")),
            jsonValue(applicability.path("orgScope")),
            jsonValue(applicability.path("settings")),
            optionalLocalDate(effective.path("from")),
            optionalLocalDate(effective.path("to")),
            effective.path("rolloutPercent").asInt(),
            existing == null ? now : existing.createdAt(),
            existing == null ? actor : existing.createdBy(),
            now,
            actor,
            traceId
        );
        return repository.save(mirror);
    }

    private String jsonValue(JsonNode value) {
        try {
            return json.writeValueAsString(value);
        } catch (com.fasterxml.jackson.core.JsonProcessingException exception) {
            throw new IllegalStateException("规则适用域无法序列化", exception);
        }
    }

    private static LocalDate optionalLocalDate(JsonNode value) {
        return value == null || !value.isTextual() || value.asText().isBlank()
            ? null
            : LocalDate.parse(value.asText());
    }
}
