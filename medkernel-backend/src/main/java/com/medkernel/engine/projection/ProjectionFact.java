package com.medkernel.engine.projection;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;

/**
 * 可由关系库重放生成的图投影事实。
 *
 * <p>投影事实只保存结构化索引与内容摘要，不作为业务权威源。
 */
public record ProjectionFact(
    ProjectionTargetType targetType,
    ProjectionFactKind factKind,
    String objectType,
    String objectId,
    String subjectKey,
    String predicate,
    String objectKey,
    String canonicalPayload,
    String contentHash,
    Instant sourceUpdatedAt
) {
    public ProjectionFact {
        if (targetType == null) {
            targetType = ProjectionTargetType.CLINICAL_GRAPH;
        }
        if (factKind == null) {
            throw new IllegalArgumentException("投影事实类型不能为空");
        }
        objectType = required(objectType, "objectType");
        objectId = required(objectId, "objectId");
        subjectKey = blankToNull(subjectKey);
        predicate = blankToNull(predicate);
        objectKey = blankToNull(objectKey);
        canonicalPayload = required(canonicalPayload, "canonicalPayload");
        contentHash = blankToNull(contentHash);
        if (contentHash == null) {
            contentHash = sha256(canonicalPayload);
        }
    }

    public static ProjectionFact node(String objectType, String objectId, String payload, Instant updatedAt) {
        return new ProjectionFact(
            ProjectionTargetType.CLINICAL_GRAPH,
            ProjectionFactKind.NODE,
            objectType,
            objectId,
            null,
            null,
            null,
            payload,
            null,
            updatedAt);
    }

    public static ProjectionFact edge(String subjectKey, String predicate, String objectKey, String payload,
            Instant updatedAt) {
        return new ProjectionFact(
            ProjectionTargetType.CLINICAL_GRAPH,
            ProjectionFactKind.EDGE,
            "RELATION",
            subjectKey + ":" + predicate + ":" + objectKey,
            subjectKey,
            predicate,
            objectKey,
            payload,
            null,
            updatedAt);
    }

    public String factKey() {
        if (factKind == ProjectionFactKind.NODE) {
            return "NODE:" + objectType + ":" + objectId;
        }
        return "EDGE:" + subjectKey + ":" + predicate + ":" + objectKey;
    }

    private static String required(String value, String field) {
        String normalized = blankToNull(value);
        if (normalized == null) {
            throw new IllegalArgumentException("投影事实字段不能为空: " + field);
        }
        return normalized;
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("当前 JDK 缺少 SHA-256 摘要算法", exception);
        }
    }
}
