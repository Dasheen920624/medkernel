package com.medkernel.engine.llm.egress;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.NullNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.TextNode;
import com.medkernel.engine.llm.ModelDataDesensitizer;
import com.medkernel.shared.api.error.ApiException;
import com.medkernel.shared.api.error.ErrorCode;

/**
 * 模型外调数据最小化与安全闸（LLM-03）。
 *
 * <p>B2 外部调用前置拦截：① 仅保留能力码允许范围声明的字段，其余剥离（FR-1）；
 * ② 保留字段强制脱敏后才允许外调；③ 达到阈值的外调须由当前获授权操作者确认用途，否则诚实阻断；
 * ④ 外调留证（FR-5）。任一不满足均诚实报错，由网关降级 B0，绝不静默放行裸数据。
 */
@Component
public class ModelEgressGuard {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final Set<String> DIRECT_IDENTIFIER_FIELDS = Set.of(
        "name", "fullname", "patientname", "username",
        "patientid", "patientref", "mpiid", "idcard", "idnumber", "idlast4", "nationalid",
        "phone", "mobile", "telephone", "email", "address",
        "姓名", "患者姓名", "患者编号", "身份证", "身份证号", "身份证后四位", "电话", "手机号", "邮箱", "住址");

    private final ModelEgressWhitelistRepository whitelistRepo;
    private final ModelEgressConfirmationRepository confirmationRepo;
    private final ModelEgressEvidenceRepository evidenceRepo;

    public ModelEgressGuard(ModelEgressWhitelistRepository whitelistRepo,
                            ModelEgressConfirmationRepository confirmationRepo,
                            ModelEgressEvidenceRepository evidenceRepo) {
        this.whitelistRepo = whitelistRepo;
        this.confirmationRepo = confirmationRepo;
        this.evidenceRepo = evidenceRepo;
    }

    /**
     * 外调前置处理结果：最小化+脱敏后的内容、实际外调字段清单、脱敏后内容 hash。
     */
    public record EgressPreparation(String payload, List<String> egressFields, String desensitizedHash) {}

    /**
     * 对外调内容执行允许字段最小化（脱敏/审批/留证在后续条目接入）。
     */
    public EgressPreparation prepareEgress(String tenantId,
                                           String capabilityCode,
                                           String payloadJson,
                                           String taskId,
                                           String providerCode) {
        ModelEgressWhitelist whitelist = whitelistRepo
            .findByTenantIdAndCapabilityCode(tenantId, capabilityCode)
            .orElse(null);
        ModelEgressPolicyValidator.Validation policy = ModelEgressPolicyValidator.validate(whitelist);
        if (!policy.valid()) {
            throw new ApiException(ErrorCode.ENG_LLM_006,
                "能力 " + capabilityCode + " 外调策略不可执行：" + policy.reason());
        }

        ObjectNode source = parsePayloadObject(payloadJson);
        ObjectNode minimized = OBJECT_MAPPER.createObjectNode();
        List<String> egressFields = new ArrayList<>();
        source.fieldNames().forEachRemaining(field -> {
            if (policy.allowedFields().contains(field)) {
                minimized.set(field, desensitizeNode(
                    source.get(field), policy.desensitizationRules().getOrDefault(field, "MASK_ALL"), field));
                egressFields.add(field);
            }
        });
        if (egressFields.isEmpty()) {
            throw new ApiException(ErrorCode.ENG_LLM_006,
                "能力 " + capabilityCode + " 的外调内容未命中任何允许字段，外调被阻断");
        }

        String payload = minimized.toString();
        String desensitizedHash = sha256(payload);

        // 达到阈值的外调须命中当前操作者留下的责任确认，否则诚实阻断。
        Long confirmationId = null;
        if (requiresConfirmation(whitelist.sensitivityLevel(), whitelist.confirmationThresholdLevel())) {
            ModelEgressConfirmation confirmation = confirmationRepo
                .findFirstByTenantIdAndCapabilityCodeAndPayloadHashOrderByIdDesc(
                    tenantId, capabilityCode, desensitizedHash)
                .orElseThrow(() -> new ApiException(ErrorCode.ENG_LLM_007,
                    "能力 " + capabilityCode + " 高敏数据外调未经责任确认，已阻断"));
            confirmationId = confirmation.id();
        }

        // 外调留证（字段清单 + 脱敏后 hash + 责任确认引用 + 目标模型服务）。
        Instant now = Instant.now();
        evidenceRepo.save(new ModelEgressEvidence(
            null, tenantId, capabilityCode, taskId,
            toJsonArray(egressFields), desensitizedHash, confirmationId, providerCode,
            now, "system", now, "system"));

        return new EgressPreparation(payload, egressFields, desensitizedHash);
    }

    private String toJsonArray(List<String> fields) {
        var array = OBJECT_MAPPER.createArrayNode();
        fields.forEach(array::add);
        return array.toString();
    }

    /**
     * 外调字段强制脱敏：默认采用最严格 {@code MASK_ALL}；OPT-09 允许按字段配置掩码、泛化、置空或保留。
     * 即使配置为 {@code NONE}，核心患者标识仍强制遮蔽，仅非核心业务值可保留。
     */
    private JsonNode desensitizeNode(JsonNode value, String operator, String fieldName) {
        String normalized = operator == null ? "MASK_ALL" : operator.trim().toUpperCase(Locale.ROOT);
        return switch (normalized) {
            case "NONE" -> keepBusinessValueAndMaskCoreIdentifiers(value, fieldName);
            case "NULLIFY" -> NullNode.getInstance();
            case "GENERALIZE" -> new TextNode("[已泛化]");
            case "MASK", "MASK_ALL" -> maskAll(value, null);
            default -> maskAll(value, null);
        };
    }

    private JsonNode keepBusinessValueAndMaskCoreIdentifiers(JsonNode value, String fieldName) {
        if (value == null || value.isNull()) {
            return NullNode.getInstance();
        }
        if (directIdentifierField(fieldName)) {
            return NullNode.getInstance();
        }
        if (value.isTextual()) {
            return new TextNode(ModelDataDesensitizer.desensitize(value.asText(), "MASK_ALL"));
        }
        if (value.isObject()) {
            ObjectNode masked = OBJECT_MAPPER.createObjectNode();
            value.fields().forEachRemaining(entry ->
                masked.set(
                    entry.getKey(),
                    keepBusinessValueAndMaskCoreIdentifiers(entry.getValue(), entry.getKey())));
            return masked;
        }
        if (value.isArray()) {
            ArrayNode masked = OBJECT_MAPPER.createArrayNode();
            value.forEach(item -> masked.add(keepBusinessValueAndMaskCoreIdentifiers(item, fieldName)));
            return masked;
        }
        return value;
    }

    private JsonNode maskAll(JsonNode value, String fieldName) {
        if (value == null || value.isNull()) {
            return NullNode.getInstance();
        }
        if (directIdentifierField(fieldName)) {
            return NullNode.getInstance();
        }
        if (value.isTextual()) {
            return new TextNode(ModelDataDesensitizer.desensitize(value.asText(), "MASK_ALL"));
        }
        if (value.isObject()) {
            ObjectNode masked = OBJECT_MAPPER.createObjectNode();
            value.fields().forEachRemaining(entry ->
                masked.set(entry.getKey(), maskAll(entry.getValue(), entry.getKey())));
            return masked;
        }
        if (value.isArray()) {
            ArrayNode masked = OBJECT_MAPPER.createArrayNode();
            value.forEach(item -> masked.add(maskAll(item, fieldName)));
            return masked;
        }
        return NullNode.getInstance();
    }

    private boolean directIdentifierField(String fieldName) {
        if (fieldName == null || fieldName.isBlank()) {
            return false;
        }
        String normalized = fieldName.trim().toLowerCase(Locale.ROOT).replaceAll("[_\\-.]", "");
        return DIRECT_IDENTIFIER_FIELDS.contains(normalized);
    }

    private boolean requiresConfirmation(String sensitivityLevel, String confirmationThresholdLevel) {
        int sensitivityRank = sensitivityRank(sensitivityLevel, 3);
        int thresholdRank = sensitivityRank(confirmationThresholdLevel, 1);
        return sensitivityRank >= thresholdRank;
    }

    private int sensitivityRank(String value, int fallback) {
        if (value == null) {
            return fallback;
        }
        return switch (value.trim().toUpperCase(Locale.ROOT)) {
            case "LOW" -> 1;
            case "MEDIUM" -> 2;
            case "HIGH" -> 3;
            default -> fallback;
        };
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
