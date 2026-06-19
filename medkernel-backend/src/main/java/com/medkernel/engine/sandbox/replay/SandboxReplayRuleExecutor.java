package com.medkernel.engine.sandbox.replay;

import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import com.medkernel.engine.rule.RuleDslEvaluation;
import com.medkernel.engine.rule.RuleDslEvaluator;
import com.medkernel.engine.versioning.VersionedAssetType;
import com.medkernel.shared.api.error.ApiException;
import com.medkernel.shared.api.error.ErrorCode;

/** 对历史清单中的显式规则版本执行正式确定性 DSL 内核，不查询当前规则。 */
@Component
public class SandboxReplayRuleExecutor {

    private final ObjectMapper json;
    private final RuleDslEvaluator evaluator;

    public SandboxReplayRuleExecutor(ObjectMapper json, RuleDslEvaluator evaluator) {
        this.json = json;
        this.evaluator = evaluator;
    }

    public List<SandboxReplayRuleResult> execute(SandboxReplayResolvedCase replay) {
        if (replay == null) {
            throw new ApiException(ErrorCode.BAD_REQUEST, "历史重放清单不能为空");
        }
        List<SandboxReplayRuleResult> results = new ArrayList<>();
        for (SandboxReplayAssetBinding asset : replay.assets()) {
            if (asset.assetType() != VersionedAssetType.RULE) {
                continue;
            }
            JsonNode content = read(asset.contentJson(), asset.assetIdentity());
            JsonNode dsl = content.path("dsl");
            if (!dsl.isObject()) {
                throw new ApiException(
                    ErrorCode.ENG_RULE_001,
                    "历史规则 " + asset.assetIdentity() + " 缺少可执行 DSL");
            }
            JsonNode canonicalContext = replay.contextSnapshot().path("resources").isObject()
                ? replay.contextSnapshot().path("resources")
                : replay.contextSnapshot();
            RuleDslEvaluation evaluation = evaluator.evaluate(dsl, canonicalContext);
            results.add(new SandboxReplayRuleResult(
                text(content, "ruleCode", asset.assetIdentity()),
                text(content, "name", asset.assetIdentity()),
                asset.versionId(), asset.assetVersion(), asset.historicalStatus(), asset.contentHash(),
                evaluation.hit(), evaluation.severity() == null ? null : evaluation.severity().name(),
                evaluation.actions(), evaluation.explanation()));
        }
        return List.copyOf(results);
    }

    private JsonNode read(String content, String identity) {
        try {
            JsonNode node = json.readTree(content);
            if (node == null || !node.isObject()) {
                throw new ApiException(ErrorCode.ENG_RULE_001, "历史规则内容必须为 JSON 对象：" + identity);
            }
            return node;
        } catch (JsonProcessingException exception) {
            throw new ApiException(ErrorCode.ENG_RULE_001, "历史规则内容已损坏：" + identity, exception);
        }
    }

    private static String text(JsonNode node, String field, String fallback) {
        String value = node.path(field).asText(null);
        return value == null || value.isBlank() ? fallback : value;
    }
}
