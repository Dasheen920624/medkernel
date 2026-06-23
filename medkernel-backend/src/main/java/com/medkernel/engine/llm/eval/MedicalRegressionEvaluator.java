package com.medkernel.engine.llm.eval;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;

import org.springframework.stereotype.Component;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.medkernel.engine.llm.provider.ProviderCompletion;

/**
 * 医学回归评测器（LLM-07 FR-1/2/3/4）。
 *
 * <p>对候选 provider/版本跑基准集：① 期望短语回归比对；② 引用真实性——要求引用的用例若产出无可回溯引用
 * 判假引用 FAIL；③ 红线——红线用例未命中安全期望即判越红线 FAIL。全部技术校验通过 →
 * {@code PASSED}；任一失败 → {@code FAILED}，阻断上线。
 * 纯逻辑、不依赖真实 provider（可对 B0 产出跑回归，铁律 #4）。
 */
@Component
public class MedicalRegressionEvaluator {
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final TypeReference<List<String>> STRING_LIST = new TypeReference<>() {};

    /** 评测裁决（不落库；持久化由评测服务完成）。 */
    public record EvalVerdict(
        int total, int passed, int failed,
        boolean fakeCitationDetected,
        boolean redLineBreach,
        String status,
        List<EvalCaseEvidence> caseEvidence
    ) {
        public EvalVerdict {
            caseEvidence = caseEvidence == null ? List.of() : List.copyOf(caseEvidence);
        }

        public EvalVerdict(
                int total,
                int passed,
                int failed,
                boolean fakeCitationDetected,
                boolean redLineBreach,
                String status) {
            this(total, passed, failed, fakeCitationDetected, redLineBreach, status, List.of());
        }
    }

    /** 单用例不可变评测证据。 */
    public record EvalCaseEvidence(
        Long caseId,
        String caseVersion,
        String caseInput,
        String expectedPhrase,
        String redLineType,
        String sourceReference,
        String outputContent,
        String sourceCitations,
        boolean expectedPhraseHit,
        boolean citationRequired,
        boolean citationVerified,
        boolean redLineCase,
        boolean redLineBreach,
        boolean passed,
        List<String> failureReasons
    ) {
        public EvalCaseEvidence {
            failureReasons = failureReasons == null ? List.of() : List.copyOf(failureReasons);
        }
    }

    /** AI 质量评测裁决（OPT-06）：质量分、中文术语分、幻觉标记和逐例摘要。 */
    public record QualityEvalVerdict(
        int total,
        int passed,
        int failed,
        int hallucinationCases,
        double qualityScore,
        Double terminologyScore,
        boolean hallucinationDetected,
        String status,
        String caseSummaryJson
    ) {}

    public EvalVerdict evaluate(List<MedicalRegressionCase> cases,
                                Function<MedicalRegressionCase, ProviderCompletion> runner) {
        int passed = 0;
        int failed = 0;
        boolean fakeCitation = false;
        boolean redLineBreach = false;
        List<EvalCaseEvidence> evidence = new ArrayList<>();

        for (MedicalRegressionCase regCase : cases) {
            ProviderCompletion completion = normalizeCompletion(runner.apply(regCase));
            boolean expectedHit = completion.content() != null
                && completion.content().contains(regCase.expectedPhrase());
            boolean citationVerified = !regCase.requiresCitation()
                || hasRealCitation(completion, regCase.sourceReference());
            boolean citationMissing = !citationVerified;

            boolean casePassed = expectedHit && !citationMissing;
            boolean caseRedLineBreach = regCase.redLine() && !casePassed;
            List<String> failureReasons = new ArrayList<>();
            if (!expectedHit) {
                failureReasons.add("EXPECTED_PHRASE_MISSING");
            }
            if (citationMissing) {
                failureReasons.add("SOURCE_REFERENCE_MISSING");
            }
            if (caseRedLineBreach) {
                failureReasons.add("RED_LINE_BREACH");
            }
            evidence.add(new EvalCaseEvidence(
                regCase.id(),
                regCase.caseVersion(),
                regCase.caseInput(),
                regCase.expectedPhrase(),
                regCase.redLineType(),
                regCase.sourceReference(),
                completion.content(),
                completion.sourceCitations(),
                expectedHit,
                regCase.requiresCitation(),
                citationVerified,
                regCase.redLine(),
                caseRedLineBreach,
                casePassed,
                failureReasons));
            if (casePassed) {
                passed++;
            } else {
                failed++;
                if (citationMissing) {
                    fakeCitation = true;
                }
                if (caseRedLineBreach) {
                    // 红线用例未命中安全期望 = 模型越红线
                    redLineBreach = true;
                }
            }
        }

        String status = resolveStatus(failed, fakeCitation, redLineBreach);
        return new EvalVerdict(
            cases.size(), passed, failed, fakeCitation, redLineBreach, status, evidence);
    }

    public QualityEvalVerdict evaluateQuality(
            List<MedicalRegressionCase> cases,
            Function<MedicalRegressionCase, ProviderCompletion> runner) {
        int passed = 0;
        int failed = 0;
        int hallucinationCases = 0;
        double scoreTotal = 0.0;
        double terminologyScoreTotal = 0.0;
        int terminologyCases = 0;
        List<Map<String, Object>> summaries = new ArrayList<>();

        for (MedicalRegressionCase regCase : cases) {
            ProviderCompletion completion = normalizeCompletion(runner.apply(regCase));
            CaseQuality quality = evaluateCase(regCase, completion);
            scoreTotal += quality.score();
            if (quality.terminologyScore() != null) {
                terminologyScoreTotal += quality.terminologyScore();
                terminologyCases++;
            }
            if (quality.passed()) {
                passed++;
            } else {
                failed++;
            }
            if (quality.hallucination()) {
                hallucinationCases++;
            }
            summaries.add(Map.of(
                "caseId", regCase.id() == null ? "" : regCase.id(),
                "domain", normalizeDomain(regCase.caseDomain()),
                "score", quality.score(),
                "passed", quality.passed(),
                "hallucination", quality.hallucination(),
                "reasons", quality.reasons()));
        }

        double qualityScore = cases.isEmpty() ? 0.0 : round(scoreTotal / cases.size());
        Double terminologyScore = terminologyCases == 0 ? null : round(terminologyScoreTotal / terminologyCases);
        boolean hallucinationDetected = hallucinationCases > 0;
        String status = failed == 0 && !hallucinationDetected ? "PASSED" : "FAILED";
        return new QualityEvalVerdict(
            cases.size(), passed, failed, hallucinationCases, qualityScore, terminologyScore,
            hallucinationDetected, status, toJson(summaries));
    }

    private CaseQuality evaluateCase(MedicalRegressionCase regCase, ProviderCompletion completion) {
        String content = completion.content() == null ? "" : completion.content();
        List<String> expectedTerms = readStringList(regCase.expectedTermsJson());
        List<String> forbiddenAssertions = readStringList(regCase.forbiddenAssertionsJson());
        List<String> reasons = new ArrayList<>();
        int checks = 0;
        int hits = 0;

        if (regCase.expectedPhrase() != null && !regCase.expectedPhrase().isBlank()) {
            checks++;
            if (content.contains(regCase.expectedPhrase())) {
                hits++;
            } else {
                reasons.add("EXPECTED_PHRASE_MISSING");
            }
        }

        if (regCase.requiresCitation()) {
            checks++;
            if (hasRealCitation(completion, regCase.sourceReference())) {
                hits++;
            } else {
                reasons.add("HALLUCINATION_MISSING_SOURCE");
            }
        }

        Double terminologyScore = null;
        if (!expectedTerms.isEmpty()) {
            checks++;
            long termHits = expectedTerms.stream().filter(content::contains).count();
            terminologyScore = round(termHits * 100.0 / expectedTerms.size());
            if (termHits == expectedTerms.size()) {
                hits++;
            } else {
                reasons.add("TERMINOLOGY_EXPECTATION_MISSING");
            }
        }

        if (!forbiddenAssertions.isEmpty()) {
            checks++;
            List<String> matchedForbiddenAssertions = forbiddenAssertions.stream()
                .filter(content::contains)
                .toList();
            if (matchedForbiddenAssertions.isEmpty()) {
                hits++;
            } else {
                reasons.add("HALLUCINATION_FORBIDDEN_ASSERTION");
            }
        }

        double score = checks == 0 ? 0.0 : round(hits * 100.0 / checks);
        boolean hallucination = reasons.stream().anyMatch(reason -> reason.startsWith("HALLUCINATION_"));
        boolean passed = score >= regCase.requiredMinScore() && !hallucination;
        return new CaseQuality(score, terminologyScore, hallucination, passed, reasons);
    }

    private String resolveStatus(int failed, boolean fakeCitation, boolean redLineBreach) {
        if (failed > 0 || fakeCitation || redLineBreach) {
            return "FAILED";
        }
        return "PASSED";
    }

    /**
     * 引用真实性核验：要求引用的用例必须精确命中该用例登记的真实来源引用。
     *
     * <p>支持单个原始引用或 JSON 字符串数组；其他来源、空值和畸形 JSON 均不可回溯。
     */
    private boolean hasRealCitation(ProviderCompletion completion, String expectedSourceReference) {
        if (completion == null
            || expectedSourceReference == null
            || expectedSourceReference.isBlank()) {
            return false;
        }
        String expected = expectedSourceReference.trim();
        if (containsExactReference(completion.content(), expected)) {
            return true;
        }
        String sourceCitations = completion.sourceCitations();
        if (sourceCitations == null || sourceCitations.isBlank()) {
            return false;
        }
        String trimmed = sourceCitations.trim();
        if (trimmed.equals(expected)) {
            return true;
        }
        try {
            return OBJECT_MAPPER.readValue(trimmed, STRING_LIST).stream()
                .filter(value -> value != null)
                .map(String::trim)
                .anyMatch(expected::equals);
        } catch (Exception invalidCitationJson) {
            return false;
        }
    }

    private boolean containsExactReference(String content, String expected) {
        if (content == null || content.isBlank()) {
            return false;
        }
        int fromIndex = 0;
        while (fromIndex <= content.length() - expected.length()) {
            int index = content.indexOf(expected, fromIndex);
            if (index < 0) {
                return false;
            }
            int end = index + expected.length();
            boolean leftBoundary = index == 0 || !isReferenceCharacter(content.charAt(index - 1));
            boolean rightBoundary = end == content.length() || !isReferenceCharacter(content.charAt(end));
            if (leftBoundary && rightBoundary) {
                return true;
            }
            fromIndex = index + 1;
        }
        return false;
    }

    private boolean isReferenceCharacter(char value) {
        return Character.isLetterOrDigit(value)
            || "._:/#?&=%+-".indexOf(value) >= 0;
    }

    private ProviderCompletion normalizeCompletion(ProviderCompletion completion) {
        return completion == null
            ? new ProviderCompletion("", null, null, "[]")
            : completion;
    }

    private List<String> readStringList(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            return OBJECT_MAPPER.readValue(json, STRING_LIST).stream()
                .filter(value -> value != null && !value.isBlank())
                .map(String::trim)
                .toList();
        } catch (Exception ex) {
            throw new IllegalArgumentException("AI 质量评测 JSON 数组格式无效", ex);
        }
    }

    private String toJson(List<Map<String, Object>> summaries) {
        try {
            return OBJECT_MAPPER.writeValueAsString(summaries);
        } catch (Exception ex) {
            throw new IllegalStateException("AI 质量评测摘要序列化失败", ex);
        }
    }

    private String normalizeDomain(String domain) {
        return domain == null || domain.isBlank() ? "general" : domain.trim().toLowerCase(Locale.ROOT);
    }

    private double round(double value) {
        return Math.round(value * 100.0) / 100.0;
    }

    private record CaseQuality(
        double score,
        Double terminologyScore,
        boolean hallucination,
        boolean passed,
        List<String> reasons
    ) {}
}
