package com.medkernel.engine.sandbox.replay;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.stereotype.Component;

import com.medkernel.shared.api.error.ApiException;
import com.medkernel.shared.api.error.ErrorCode;

/** 以规范 JSON 和稳定字段顺序计算历史重放内容及清单 SHA-256。 */
@Component
public class SandboxReplayHashing {

    private final ObjectMapper json;

    public SandboxReplayHashing(ObjectMapper json) {
        this.json = json;
    }

    public String contentHash(JsonNode content) {
        if (content == null || content.isNull()) {
            throw new ApiException(ErrorCode.BAD_REQUEST, "历史重放内容不能为空");
        }
        return sha256(writeCanonical(content));
    }

    public String manifestHash(SandboxReplayImportRequest request) {
        if (request == null) {
            throw new ApiException(ErrorCode.BAD_REQUEST, "历史重放清单不能为空");
        }
        Map<String, Object> manifest = new TreeMap<>();
        manifest.put("replayCaseId", request.replayCaseId());
        manifest.put("sourceTenantRef", request.sourceTenantRef());
        manifest.put("sourceEventRef", request.sourceEventRef());
        manifest.put("sourceTraceRef", request.sourceTraceRef());
        manifest.put("sourceContextRef", request.sourceContextRef());
        manifest.put("contextSnapshotHash", request.contextSnapshotHash());
        manifest.put("packageCode", request.packageCode());
        manifest.put("packageVersion", request.packageVersion());
        manifest.put("occurredAt", request.occurredAt());
        manifest.put("deidentificationProfile", request.deidentificationProfile());
        List<Map<String, Object>> assetEntries = new ArrayList<>();
        request.assets().stream()
            .sorted(Comparator.comparing(asset -> String.join("|",
                enumName(asset.assetType()), value(asset.assetIdentity()), value(asset.versionId()))))
            .forEach(asset -> {
                Map<String, Object> entry = new TreeMap<>();
                entry.put("assetType", asset.assetType());
                entry.put("assetIdentity", asset.assetIdentity());
                entry.put("versionId", asset.versionId());
                entry.put("assetVersion", asset.assetVersion());
                entry.put("sourceTier", asset.sourceTier());
                entry.put("sourceOrgRef", asset.sourceOrgRef());
                entry.put("contentHash", asset.contentHash());
                entry.put("historicalStatus", asset.historicalStatus());
                assetEntries.add(entry);
            });
        manifest.put("assets", assetEntries);
        return contentHash(json.valueToTree(manifest));
    }

    public String canonicalJson(JsonNode content) {
        return writeCanonical(content);
    }

    private String writeCanonical(JsonNode content) {
        try {
            return json.writeValueAsString(canonicalize(content));
        } catch (JsonProcessingException exception) {
            throw new ApiException(ErrorCode.BAD_REQUEST, "历史重放 JSON 无法规范化", exception);
        }
    }

    private JsonNode canonicalize(JsonNode node) {
        if (node.isObject()) {
            ObjectNode result = json.createObjectNode();
            List<String> names = new ArrayList<>();
            node.fieldNames().forEachRemaining(names::add);
            names.stream().sorted().forEach(name -> result.set(name, canonicalize(node.get(name))));
            return result;
        }
        if (node.isArray()) {
            ArrayNode result = json.createArrayNode();
            node.forEach(item -> result.add(canonicalize(item)));
            return result;
        }
        return node.deepCopy();
    }

    private static String sha256(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                .digest(value.getBytes(StandardCharsets.UTF_8));
            return java.util.HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("JVM 缺少 SHA-256", exception);
        }
    }

    private static String enumName(Enum<?> value) {
        return value == null ? "" : value.name();
    }

    private static String value(String value) {
        return value == null ? "" : value;
    }
}
