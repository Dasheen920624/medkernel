package com.medkernel.engine.integration.service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;

import com.medkernel.engine.context.ContextFieldDescriptor;
import com.medkernel.engine.context.ClinicalRuntimeRelease;
import com.medkernel.engine.context.CurrentClinicalRuntimeReleaseResolver;
import com.medkernel.engine.context.RuntimeReleaseFieldCatalogResolver;
import com.medkernel.engine.integration.dto.IntegrationDataContractField;
import com.medkernel.engine.integration.dto.IntegrationDataContractFieldSchema;
import com.medkernel.engine.integration.dto.IntegrationDataContractJsonSchema;
import com.medkernel.engine.integration.dto.IntegrationDataContractResource;
import com.medkernel.engine.integration.dto.IntegrationDataContractResponse;
import com.medkernel.shared.context.RequestContext;

/**
 * 第三方数据接入契约生成器。
 *
 * <p>字段只从医院当前运行修订中的字段目录资产恢复，避免当前工作区草稿污染已发布契约。
 */
@Service
public class IntegrationDataContractService {

    private static final String SCHEMA_VERSION = "medkernel.context-field-contract.v1";

    private final RuntimeReleaseFieldCatalogResolver fieldCatalog;
    private final CurrentClinicalRuntimeReleaseResolver runtimeReleases;

    public IntegrationDataContractService(
            RuntimeReleaseFieldCatalogResolver fieldCatalog,
            CurrentClinicalRuntimeReleaseResolver runtimeReleases) {
        this.fieldCatalog = fieldCatalog;
        this.runtimeReleases = runtimeReleases;
    }

    public IntegrationDataContractResponse generate() {
        ClinicalRuntimeRelease release =
            runtimeReleases.resolve(RequestContext.currentOrgScope());
        List<ContextFieldDescriptor> descriptors =
            fieldCatalog.resolve(release.tenantId(), release.releaseId());
        List<IntegrationDataContractField> fields = descriptors.stream()
            .map(this::toContractField)
            .toList();
        return new IntegrationDataContractResponse(
            "context-field-contract:" + release.releaseId(),
            release.releaseId(),
            SCHEMA_VERSION,
            accessGuide(release.releaseId()),
            resources(fields),
            fields);
    }

    private IntegrationDataContractField toContractField(ContextFieldDescriptor field) {
        boolean required = false;
        return new IntegrationDataContractField(
            field.resourceType(),
            field.fieldPath(),
            field.payloadKey(),
            field.propertyName(),
            field.displayName(),
            field.dataType(),
            field.jsonSchemaType(),
            field.unit(),
            field.codeSystem(),
            required,
            field.derived(),
            field.externalWritable(),
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
                    field.derived(),
                    field.externalWritable()));
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

    private static List<String> accessGuide(String runtimeReleaseId) {
        return List.of(
            "字段契约由医院当前运行修订 " + runtimeReleaseId
                + " 自动确定；第三方请求不得提交发布制品、领域或版本。",
            "外部数据先按 resources 下的 payloadKey 投影为 canonical 资源，再经术语对照归一。",
            "规则/路径只消费归一后的 canonical 字段；缺失、未对照或低质量字段会进入质量状态与人工复核证据。"
        );
    }
}
