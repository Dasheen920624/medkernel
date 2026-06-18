package com.medkernel.engine.llm.eval;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

/**
 * 医学回归基准集不可变证据：把所有影响裁决的用例字段绑定到评测运行。
 *
 * <p>用例内容、期望、术语、禁断言、红线、引用要求、来源或版本任一变化，都会生成不同指纹，
 * 防止仅凭题数相同复用旧 {@code PASSED} 结果。
 */
public final class RegressionBaselineEvidence {

    private static final int SCHEMA_VERSION = 1;
    private static final String ALGORITHM = "SHA-256";
    private static final String FINGERPRINT_PREFIX = "sha256:";
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final Comparator<MedicalRegressionCase> CASE_ORDER = Comparator
        .comparing(MedicalRegressionCase::id, Comparator.nullsLast(Comparator.naturalOrder()))
        .thenComparing(RegressionBaselineEvidence::stableFallbackKey);

    private RegressionBaselineEvidence() {
    }

    /** 生成可持久化到评测运行摘要的基准集证据。 */
    public static String toJson(List<MedicalRegressionCase> cases) {
        List<MedicalRegressionCase> normalized = normalize(cases);
        ObjectNode evidence = OBJECT_MAPPER.createObjectNode();
        evidence.put("schemaVersion", SCHEMA_VERSION);
        evidence.put("algorithm", ALGORITHM);
        evidence.put("caseCount", normalized.size());
        evidence.put("baselineFingerprint", fingerprint(normalized));
        return evidence.toString();
    }

    /** 当前启用基准集是否与评测运行绑定的证据完全一致。 */
    public static boolean matches(String evidenceJson, List<MedicalRegressionCase> cases) {
        if (evidenceJson == null || evidenceJson.isBlank()) {
            return false;
        }
        try {
            JsonNode evidence = OBJECT_MAPPER.readTree(evidenceJson);
            List<MedicalRegressionCase> normalized = normalize(cases);
            return evidence.path("schemaVersion").asInt(-1) == SCHEMA_VERSION
                && ALGORITHM.equals(evidence.path("algorithm").asText())
                && evidence.path("caseCount").asInt(-1) == normalized.size()
                && fingerprint(normalized).equals(evidence.path("baselineFingerprint").asText());
        } catch (Exception invalidEvidence) {
            return false;
        }
    }

    private static String fingerprint(List<MedicalRegressionCase> cases) {
        MessageDigest digest = sha256();
        update(digest, String.valueOf(SCHEMA_VERSION));
        update(digest, String.valueOf(cases.size()));
        for (MedicalRegressionCase item : cases) {
            update(digest, item.id());
            update(digest, item.tenantId());
            update(digest, item.capabilityCode());
            update(digest, item.caseDomain());
            update(digest, item.caseInput());
            update(digest, item.expectedPhrase());
            update(digest, item.expectedTermsJson());
            update(digest, item.forbiddenAssertionsJson());
            update(digest, item.minScore());
            update(digest, item.redLineType());
            update(digest, item.sourceReference());
            update(digest, item.citationRequired());
            update(digest, item.caseVersion());
            update(digest, item.enabledFlag());
        }
        return FINGERPRINT_PREFIX + HexFormat.of().formatHex(digest.digest());
    }

    private static List<MedicalRegressionCase> normalize(List<MedicalRegressionCase> cases) {
        if (cases == null || cases.isEmpty()) {
            return List.of();
        }
        return cases.stream().sorted(CASE_ORDER).toList();
    }

    private static void update(MessageDigest digest, Object value) {
        if (value == null) {
            digest.update((byte) 0);
            return;
        }
        digest.update((byte) 1);
        byte[] bytes = String.valueOf(value).getBytes(StandardCharsets.UTF_8);
        digest.update(ByteBuffer.allocate(Integer.BYTES).putInt(bytes.length).array());
        digest.update(bytes);
    }

    private static String stableFallbackKey(MedicalRegressionCase item) {
        return String.join("\u001f",
            value(item.capabilityCode()),
            value(item.caseDomain()),
            value(item.caseInput()),
            value(item.expectedPhrase()),
            value(item.caseVersion()),
            value(item.sourceReference()));
    }

    private static String value(Object value) {
        return value == null ? "<null>" : String.valueOf(value);
    }

    private static MessageDigest sha256() {
        try {
            return MessageDigest.getInstance(ALGORITHM);
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("运行环境缺少 SHA-256", impossible);
        }
    }
}
