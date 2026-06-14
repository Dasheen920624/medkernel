package com.medkernel.engine.llm.egress;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.TextNode;
import com.medkernel.engine.llm.ModelDataDesensitizer;
import com.medkernel.shared.api.error.ApiException;
import com.medkernel.shared.api.error.ErrorCode;

/**
 * 模型外调出域数据最小化与安全闸（LLM-03）。
 *
 * <p>B2 外部调用出域前置拦截：① 仅保留能力码白名单声明的允许字段，其余剥离（FR-1）；
 * ② 保留字段强制脱敏后才出域（FR-2）；③ 高敏出域须经审批，否则诚实阻断（FR-3/4）；
 * ④ 出域留证（FR-5）。任一不满足均诚实报错，由网关降级 B0，绝不静默放行裸数据。
 */
@Component
public class ModelEgressGuard {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final ModelEgressWhitelistRepository whitelistRepo;
    private final ModelEgressApprovalRepository approvalRepo;
    private final ModelEgressEvidenceRepository evidenceRepo;

    public ModelEgressGuard(ModelEgressWhitelistRepository whitelistRepo,
                            ModelEgressApprovalRepository approvalRepo,
                            ModelEgressEvidenceRepository evidenceRepo) {
        this.whitelistRepo = whitelistRepo;
        this.approvalRepo = approvalRepo;
        this.evidenceRepo = evidenceRepo;
    }

    /**
     * 出域前置处理结果：最小化+脱敏后的载荷、实际出域字段清单、脱敏后内容 hash。
     */
    public record EgressPreparation(String payload, List<String> egressFields, String desensitizedHash) {}

    /**
     * 对外调载荷执行字段白名单最小化（脱敏/审批/留证在后续条目接入）。
     */
    public EgressPreparation prepareEgress(String tenantId,
                                           String capabilityCode,
                                           String payloadJson,
                                           String taskId,
                                           String providerCode) {
        ModelEgressWhitelist whitelist = whitelistRepo
            .findByTenantIdAndCapabilityCode(tenantId, capabilityCode)
            .orElse(null);
        Set<String> allowed = parseAllowedFields(whitelist);
        // FR-1/4：未配置出域白名单（无允许字段契约）一律阻断，不静默放行任何字段。
        if (allowed.isEmpty()) {
            throw new ApiException(ErrorCode.ENG_LLM_006,
                "能力 " + capabilityCode + " 未配置出域字段白名单，外调被阻断");
        }

        ObjectNode source = parsePayloadObject(payloadJson);
        ObjectNode minimized = OBJECT_MAPPER.createObjectNode();
        List<String> egressFields = new ArrayList<>();
        source.fieldNames().forEachRemaining(field -> {
            if (allowed.contains(field)) {
                minimized.set(field, desensitizeNode(source.get(field)));
                egressFields.add(field);
            }
        });

        String payload = minimized.toString();
        String desensitizedHash = sha256(payload);

        // FR-3：高敏出域须命中已批准审批记录，否则诚实阻断（不静默出域）。
        Long approvalId = null;
        if ("HIGH".equalsIgnoreCase(whitelist.sensitivityLevel())) {
            ModelEgressApproval approval = approvalRepo
                .findFirstByTenantIdAndCapabilityCodeAndPayloadHashAndStatusOrderByIdDesc(
                    tenantId, capabilityCode, desensitizedHash, "APPROVED")
                .orElseThrow(() -> new ApiException(ErrorCode.ENG_LLM_007,
                    "能力 " + capabilityCode + " 高敏数据外调出域未经审批，已阻断"));
            approvalId = approval.id();
        }

        // FR-5：出域留证（字段清单 + 脱敏后 hash + 审批引用 + 目标 provider）。
        Instant now = Instant.now();
        evidenceRepo.save(new ModelEgressEvidence(
            null, tenantId, capabilityCode, taskId,
            toJsonArray(egressFields), desensitizedHash, approvalId, providerCode,
            now, "system", now, "system"));

        return new EgressPreparation(payload, egressFields, desensitizedHash);
    }

    private String toJsonArray(List<String> fields) {
        var array = OBJECT_MAPPER.createArrayNode();
        fields.forEach(array::add);
        return array.toString();
    }

    private Set<String> parseAllowedFields(ModelEgressWhitelist whitelist) {
        Set<String> allowed = new LinkedHashSet<>();
        if (whitelist == null) {
            return allowed;
        }
        try {
            JsonNode node = OBJECT_MAPPER.readTree(whitelist.allowedFields());
            if (node.isArray()) {
                node.forEach(item -> {
                    if (item.isTextual() && !item.asText().isBlank()) {
                        allowed.add(item.asText().trim());
                    }
                });
            }
        } catch (Exception ignored) {
            // 白名单非法 JSON 视为零允许字段，由调用方按阻断处理。
        }
        return allowed;
    }

    /**
     * 出域字段强制脱敏：外部出域采用最严格 {@code MASK_ALL}，文本节点脱敏后回填，非文本节点原样保留。
     */
    private JsonNode desensitizeNode(JsonNode value) {
        if (value != null && value.isTextual()) {
            return new TextNode(ModelDataDesensitizer.desensitize(value.asText(), "MASK_ALL"));
        }
        return value;
    }

    private ObjectNode parsePayloadObject(String payloadJson) {
        try {
            JsonNode node = OBJECT_MAPPER.readTree(payloadJson);
            if (node instanceof ObjectNode objectNode) {
                return objectNode;
            }
        } catch (Exception ignored) {
            // 非对象载荷无字段可出域，返回空对象。
        }
        return OBJECT_MAPPER.createObjectNode();
    }

    private String sha256(String data) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(data.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder();
            for (byte b : hash) {
                String h = Integer.toHexString(0xff & b);
                if (h.length() == 1) {
                    hex.append('0');
                }
                hex.append(h);
            }
            return hex.toString();
        } catch (Exception e) {
            throw new IllegalStateException("SHA-256 摘要计算失败", e);
        }
    }
}
