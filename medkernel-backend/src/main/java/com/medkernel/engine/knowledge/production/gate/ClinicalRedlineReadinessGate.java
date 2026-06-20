package com.medkernel.engine.knowledge.production.gate;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.medkernel.engine.factory.KnowledgeAssetEnvelope;
import com.medkernel.engine.knowledge.production.generation.StrictB0TemplatePolicy;
import com.medkernel.engine.safety.ClinicalRedlineCatalogResponse;
import com.medkernel.engine.safety.ClinicalRedlineCategory;
import com.medkernel.engine.safety.ClinicalRedlineContentStatus;
import com.medkernel.engine.safety.ClinicalRedlineResponse;
import com.medkernel.engine.safety.ClinicalRedlineService;

/**
 * 门禁：临床安全红线体系与候选结构化红线检查（AIK-STD-05，FR-2 红线/剂量/高危）。
 *
 * <p>包含医学逻辑的候选提审前必须确认 OPT-04 五类红线目录均已配置；严格匹配
 * {@code SourceCandidateGenerator} 契约的无医学逻辑 B0 结构候选可先进入人工编著审核，解除基础知识与红线目录的
 * 启动环依赖。如果候选 payload 带结构化
 * {@code clinicalSafety.redlineChecks} / {@code clinicalRedlineChecks}，每条检查必须引用 ACTIVE 红线并带证据。
 * 任一项声明命中、越界或阻断即诚实拦截，不把模型/模板结论伪装成已通过。
 */
@Component
public class ClinicalRedlineReadinessGate implements CandidateGate {

    public static final String CODE = "CLINICAL_REDLINE";

    private final ClinicalRedlineService redlineService;
    private final ObjectMapper objectMapper;
    private final StrictB0TemplatePolicy strictB0TemplatePolicy;

    public ClinicalRedlineReadinessGate(ClinicalRedlineService redlineService,
                                        ObjectMapper objectMapper,
                                        StrictB0TemplatePolicy strictB0TemplatePolicy) {
        this.redlineService = redlineService;
        this.objectMapper = objectMapper;
        this.strictB0TemplatePolicy = strictB0TemplatePolicy;
    }

    @Override
    public String code() {
        return CODE;
    }

    @Override
    public GateItemResult evaluate(KnowledgeAssetEnvelope candidate, GateContext context) {
        JsonNode payload = parsePayload(candidate.payload());
        if (strictB0TemplatePolicy.matches(payload)) {
            return GateItemResult.pass(CODE);
        }
        ClinicalRedlineCatalogResponse catalog = redlineService.activeCatalog(null);
        if (catalog == null || catalog.contentStatus() == ClinicalRedlineContentStatus.NOT_CONFIGURED) {
            return GateItemResult.fail(CODE, "临床安全红线目录未配置，无法完成红线/剂量/高危门禁");
        }
        Set<ClinicalRedlineCategory> configured = catalog.redlines() == null
            ? EnumSet.noneOf(ClinicalRedlineCategory.class)
            : catalog.redlines().stream()
                .filter(row -> row != null && row.category() != null)
                .map(row -> row.category())
                .collect(Collectors.toCollection(() -> EnumSet.noneOf(ClinicalRedlineCategory.class)));
        for (ClinicalRedlineCategory required : ClinicalRedlineCategory.requiredSafetyCategories()) {
            if (!configured.contains(required)) {
                return GateItemResult.fail(CODE, "临床安全红线类目未配置：" + required.name());
            }
        }
        GateItemResult structured = evaluateStructuredRedlineChecks(payload, catalog.redlines());
        if (!structured.passed()) {
            return structured;
        }
        return GateItemResult.pass(CODE);
    }

    private GateItemResult evaluateStructuredRedlineChecks(
            JsonNode payload,
            List<ClinicalRedlineResponse> activeRedlines) {
        if (payload == null) {
            return GateItemResult.fail(CODE, "候选 payload 不是合法 JSON，无法完成临床红线逐条校验");
        }
        List<JsonNode> checks = redlineCheckNodes(payload);
        if (checks.isEmpty()) {
            return GateItemResult.pass(CODE);
        }
        for (int i = 0; i < checks.size(); i++) {
            JsonNode check = checks.get(i);
            Optional<ClinicalRedlineCategory> category = parseCategory(text(check, "category", "redlineCategory"));
            if (category.isEmpty()) {
                return GateItemResult.fail(CODE, "结构化红线检查缺少有效 category，序号=" + (i + 1));
            }
            String redlineKey = text(check, "redlineKey", "redlineId", "key");
            if (redlineKey.isBlank()) {
                return GateItemResult.fail(CODE, "结构化红线检查缺少 redlineKey，category=" + category.get().name());
            }
            Optional<ClinicalRedlineResponse> active = activeRedlines == null
                ? Optional.empty()
                : activeRedlines.stream()
                    .filter(row -> matches(row, category.get(), redlineKey))
                    .findFirst();
            if (active.isEmpty()) {
                return GateItemResult.fail(CODE,
                    "结构化红线检查未匹配 ACTIVE 红线：" + category.get().name() + "/" + redlineKey);
            }
            String evidence = text(check, "evidenceReference", "sourceReference", "basis", "citation");
            if (evidence.isBlank()) {
                return GateItemResult.fail(CODE,
                    "结构化红线检查缺少证据引用：" + category.get().name() + "/" + redlineKey);
            }
            String outcome = text(check, "outcome", "status", "result");
            if (outcome.isBlank()) {
                return GateItemResult.fail(CODE,
                    "结构化红线检查缺少 outcome：" + category.get().name() + "/" + redlineKey);
            }
            if (isViolation(check, outcome)) {
                return GateItemResult.fail(CODE,
                    "命中临床安全红线：" + category.get().name() + "/" + redlineKey
                        + "，证据=" + evidence);
            }
        }
        return GateItemResult.pass(CODE);
    }

    private JsonNode parsePayload(String payload) {
        if (payload == null || payload.isBlank()) {
            return null;
        }
        try {
            return objectMapper.readTree(payload);
        } catch (JsonProcessingException invalidJson) {
            return null;
        }
    }

    private List<JsonNode> redlineCheckNodes(JsonNode payload) {
        List<JsonNode> result = new ArrayList<>();
        addArray(result, payload.path("clinicalRedlineChecks"));
        addArray(result, payload.path("clinicalSafety").path("redlineChecks"));
        addArray(result, payload.path("modelOutput").path("clinicalRedlineChecks"));
        addArray(result, payload.path("modelOutput").path("clinicalSafety").path("redlineChecks"));
        return result;
    }

    private void addArray(List<JsonNode> result, JsonNode node) {
        if (node != null && node.isArray()) {
            node.forEach(result::add);
        }
    }

    private boolean matches(ClinicalRedlineResponse row, ClinicalRedlineCategory category, String redlineKey) {
        if (row == null || row.category() != category) {
            return false;
        }
        return equalsIgnoreCase(row.redlineKey(), redlineKey) || equalsIgnoreCase(row.redlineId(), redlineKey);
    }

    private Optional<ClinicalRedlineCategory> parseCategory(String raw) {
        if (raw == null || raw.isBlank()) {
            return Optional.empty();
        }
        try {
            return Optional.of(ClinicalRedlineCategory.valueOf(raw.trim().toUpperCase(Locale.ROOT)));
        } catch (IllegalArgumentException unknown) {
            return Optional.empty();
        }
    }

    private static String text(JsonNode node, String... fields) {
        if (node == null) {
            return "";
        }
        for (String field : fields) {
            JsonNode value = node.get(field);
            if (value != null && !value.isNull()) {
                String text = value.asText("");
                if (!text.isBlank()) {
                    return text.trim();
                }
            }
        }
        return "";
    }

    private static boolean isViolation(JsonNode check, String outcome) {
        String normalized = outcome.trim().toUpperCase(Locale.ROOT);
        return Set.of("VIOLATION", "BREACH", "BLOCK", "BLOCKED", "FAIL", "FAILED", "HIT", "EXCEEDED")
            .contains(normalized)
            || booleanValue(check, "violated")
            || booleanValue(check, "breached")
            || booleanValue(check, "redlineHit");
    }

    private static boolean booleanValue(JsonNode node, String field) {
        JsonNode value = node == null ? null : node.get(field);
        return value != null && value.asBoolean(false);
    }

    private static boolean equalsIgnoreCase(String left, String right) {
        return left != null && right != null && left.trim().equalsIgnoreCase(right.trim());
    }
}
