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
    private static final String B0_GENERATION_MODE = "B0_TEMPLATE";
    private static final String PENDING_AUTHORING = "PENDING_AUTHORING";
    private static final String PENDING_SECTION_PATTERN = "^待编著（结构：[^（）\\r\\n]+）$";
    private static final String SHA256_HEX = "^[0-9a-f]{64}$";
    private static final Set<String> B0_ROOT_FIELDS = Set.of(
        "generationMode",
        "medicalContentStatus",
        "generatedByModel",
        "template",
        "sections",
        "sourceEvidence");
    private static final Set<String> B0_SOURCE_EVIDENCE_FIELDS = Set.of(
        "anchorPath",
        "excerpt",
        "contentHash");

    private final ClinicalRedlineService redlineService;
    private final ObjectMapper objectMapper;

    public ClinicalRedlineReadinessGate(ClinicalRedlineService redlineService, ObjectMapper objectMapper) {
        this.redlineService = redlineService;
        this.objectMapper = objectMapper;
    }

    @Override
    public String code() {
        return CODE;
    }

    @Override
    public GateItemResult evaluate(KnowledgeAssetEnvelope candidate, GateContext context) {
        JsonNode payload = parsePayload(candidate.payload());
        if (isStrictB0Template(payload)) {
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

    /**
     * 识别无医学逻辑的确定性 B0 结构候选。
     *
     * <p>这是基础知识启动期唯一允许不依赖 ACTIVE 红线目录的窄口：根字段必须与
     * {@code SourceCandidateGenerator} 完全一致，章节只能使用待编著结构标记，且必须携带来源证据。
     * 出现额外字段、模型标记、结构化红线检查或任何已编著逻辑时均返回 false，继续执行完整 OPT-04 门禁。
     */
    private boolean isStrictB0Template(JsonNode payload) {
        if (payload == null || !payload.isObject()) {
            return false;
        }
        List<String> fieldNames = new ArrayList<>();
        payload.fieldNames().forEachRemaining(fieldNames::add);
        if (!B0_ROOT_FIELDS.containsAll(fieldNames) || fieldNames.size() != B0_ROOT_FIELDS.size()) {
            return false;
        }
        if (!B0_GENERATION_MODE.equals(payload.path("generationMode").asText())
                || !PENDING_AUTHORING.equals(payload.path("medicalContentStatus").asText())
                || !payload.has("generatedByModel")
                || !payload.path("generatedByModel").isBoolean()
                || payload.path("generatedByModel").asBoolean()
                || payload.path("template").asText("").isBlank()) {
            return false;
        }
        JsonNode sections = payload.path("sections");
        if (!sections.isObject() || sections.isEmpty()) {
            return false;
        }
        List<JsonNode> sectionValues = new ArrayList<>();
        sections.elements().forEachRemaining(sectionValues::add);
        if (sectionValues.stream().anyMatch(value ->
                !value.isTextual() || !value.asText().matches(PENDING_SECTION_PATTERN))) {
            return false;
        }
        JsonNode evidence = payload.path("sourceEvidence");
        if (!evidence.isArray() || evidence.isEmpty()) {
            return false;
        }
        for (JsonNode item : evidence) {
            if (!item.isObject()) {
                return false;
            }
            List<String> evidenceFieldNames = new ArrayList<>();
            item.fieldNames().forEachRemaining(evidenceFieldNames::add);
            if (!B0_SOURCE_EVIDENCE_FIELDS.containsAll(evidenceFieldNames)
                    || evidenceFieldNames.size() != B0_SOURCE_EVIDENCE_FIELDS.size()
                    || item.path("anchorPath").asText("").isBlank()
                    || item.path("excerpt").asText("").isBlank()
                    || !item.path("contentHash").asText("").matches(SHA256_HEX)) {
                return false;
            }
        }
        return redlineCheckNodes(payload).isEmpty();
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
