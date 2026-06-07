package com.medkernel.engine.integration.service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;

import com.medkernel.engine.context.ContextFieldCatalogService;
import com.medkernel.engine.context.ContextFieldDescriptor;
import com.medkernel.engine.integration.dto.IntegrationDataContractField;
import com.medkernel.engine.integration.dto.IntegrationDataContractFieldSchema;
import com.medkernel.engine.integration.dto.IntegrationDataContractJsonSchema;
import com.medkernel.engine.integration.dto.IntegrationDataContractResource;
import com.medkernel.engine.integration.dto.IntegrationDataContractResponse;
import com.medkernel.shared.api.error.ApiException;
import com.medkernel.shared.api.error.ErrorCode;

/**
 * 第三方数据接入契约生成器。字段只从 {@link ContextFieldCatalogService} 派生，避免规则、
 * 路径和集成各维护一套字段定义。
 */
@Service
public class IntegrationDataContractService {

    private static final String SCHEMA_VERSION = "medkernel.context-field-contract.v1";

    private static final Map<String, String> PAYLOAD_KEYS = Map.ofEntries(
        Map.entry("Patient", "patient"),
        Map.entry("AllergyIntolerance", "allergyIntolerances"),
        Map.entry("Encounter", "encounters"),
        Map.entry("Condition", "conditions"),
        Map.entry("NursingAssessment", "nursingAssessments"),
        Map.entry("Observation", "observations"),
        Map.entry("DiagnosticReport", "diagnosticReports"),
        Map.entry("Medication", "medications"),
        Map.entry("Procedure", "procedures"),
        Map.entry("Document", "documents"),
        Map.entry("CarePlan", "carePlans"),
        Map.entry("FollowUp", "followUps"),
        Map.entry("Claim", "claims")
    );

    private final ContextFieldCatalogService fieldCatalogService;

    public IntegrationDataContractService(ContextFieldCatalogService fieldCatalogService) {
        this.fieldCatalogService = fieldCatalogService;
    }

    public IntegrationDataContractResponse generate(String packageVersion) {
        String version = normalizeVersion(packageVersion);
        List<ContextFieldDescriptor> descriptors = fieldCatalogService.query(null, null, version);
        List<IntegrationDataContractField> fields = descriptors.stream()
            .map(this::toContractField)
            .toList();
        return new IntegrationDataContractResponse(
            "context-field-contract:" + version,
            version,
            SCHEMA_VERSION,
            accessGuide(version),
            resources(fields),
            fields);
    }

    private static String normalizeVersion(String packageVersion) {
        if (packageVersion == null || packageVersion.isBlank()) {
            throw new ApiException(ErrorCode.ENG_CONTEXT_002, "字段契约必须指定 packageVersion");
        }
        return packageVersion.trim();
    }

    private IntegrationDataContractField toContractField(ContextFieldDescriptor field) {
        String payloadKey = PAYLOAD_KEYS.getOrDefault(field.resourceType(), field.resourceType());
        String propertyName = propertyName(field.fieldPath());
        boolean required = false;
        return new IntegrationDataContractField(
            field.resourceType(),
            field.fieldPath(),
            payloadKey,
            propertyName,
            field.displayName(),
            field.dataType(),
            jsonSchemaType(field.dataType()),
            field.unit(),
            field.codeSystem(),
            required,
            field.derived(),
            field.description());
    }

    private static Map<String, IntegrationDataContractResource> resources(
            List<IntegrationDataContractField> fields) {
        Map<String, List<IntegrationDataContractField>> byResource = new LinkedHashMap<>();
        for (IntegrationDataContractField field : fields) {
            byResource.computeIfAbsent(field.resourceType(), ignored -> new ArrayList<>()).add(field);
        }

        Map<String, IntegrationDataContractResource> result = new LinkedHashMap<>();
        for (Map.Entry<String, List<IntegrationDataContractField>> entry : byResource.entrySet()) {
            String resourceType = entry.getKey();
            List<IntegrationDataContractField> resourceFields = entry.getValue();
            Map<String, IntegrationDataContractFieldSchema> properties = new LinkedHashMap<>();
            List<String> required = new ArrayList<>();
            for (IntegrationDataContractField field : resourceFields) {
                properties.put(field.propertyName(), new IntegrationDataContractFieldSchema(
                    field.jsonSchemaType(),
                    field.displayName(),
                    field.description(),
                    field.unit(),
                    field.codeSystem(),
                    field.required(),
                    field.derived()));
                if (field.required()) {
                    required.add(field.propertyName());
                }
            }
            String payloadKey = resourceFields.get(0).payloadKey();
            result.put(resourceType, new IntegrationDataContractResource(
                resourceType,
                payloadKey,
                payloadKey.endsWith("s"),
                new IntegrationDataContractJsonSchema(
                    "object",
                    resourceType + " canonical 字段",
                    Map.copyOf(properties),
                    List.copyOf(required))));
        }
        return Map.copyOf(result);
    }

    private static String propertyName(String fieldPath) {
        String tail = fieldPath.substring(fieldPath.lastIndexOf('.') + 1);
        return tail.replace("[]", "");
    }

    private static String jsonSchemaType(String dataType) {
        return switch (dataType) {
            case "number" -> "number";
            case "boolean" -> "boolean";
            case "list" -> "array";
            default -> "string";
        };
    }

    private static List<String> accessGuide(String packageVersion) {
        return List.of(
            "本契约对应 packageVersion=" + packageVersion + "；第三方请求必须携带相同 packageVersion。",
            "外部数据先按 resources 下的 payloadKey 投影为 canonical 资源，再经术语对照归一。",
            "规则/路径只消费归一后的 canonical 字段；缺失、未对照或低质量字段会进入质量状态与人工复核证据。"
        );
    }
}
