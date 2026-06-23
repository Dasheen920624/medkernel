package com.medkernel.engine.versioning;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.medkernel.engine.context.ClinicalEventTriggerPoint;
import com.medkernel.shared.api.error.ApiException;
import com.medkernel.shared.api.error.ErrorCode;
import com.medkernel.shared.ids.Ulid;

/**
 * 统一维护规则与路径精确资产版本的多触发绑定。
 *
 * <p>服务先完整校验新绑定，再替换草稿版本现有记录，避免错误入参清空可用配置。
 * 已发布或撤回版本不可修改；复制下一版本时会生成新的绑定业务标识。
 */
@Service
public class AssetTriggerBindingService {

    private final AssetTriggerBindingRepository repository;
    private final ObjectMapper json;
    private final Clock clock;

    @Autowired
    public AssetTriggerBindingService(
            AssetTriggerBindingRepository repository,
            ObjectMapper json) {
        this(repository, json, Clock.systemUTC());
    }

    AssetTriggerBindingService(
            AssetTriggerBindingRepository repository,
            ObjectMapper json,
            Clock clock) {
        this.repository = repository;
        this.json = json;
        this.clock = clock;
    }

    /**
     * 原子替换一个草稿资产版本的全部触发绑定。
     */
    @Transactional
    public List<AssetTriggerBinding> replaceBindings(
            AssetVersion version,
            List<AssetTriggerBindingInput> inputs,
            String actor,
            String traceId) {
        AssetVersion draft = requireDraft(version);
        String normalizedActor = requireText(actor, "操作人");
        List<NormalizedBinding> normalized = normalize(draft, inputs);
        repository.deleteByTenantIdAndVersionId(draft.tenantId(), draft.versionId());
        Instant now = Instant.now(clock);
        List<AssetTriggerBinding> saved = new ArrayList<>(normalized.size());
        for (NormalizedBinding input : normalized) {
            saved.add(repository.save(new AssetTriggerBinding(
                null,
                "atb-" + Ulid.newUlid(),
                draft.tenantId(),
                draft.assetType(),
                draft.assetIdentity(),
                draft.versionId(),
                input.triggerPoint(),
                input.purpose(),
                writeFields(input.requiredFields()),
                now,
                normalizedActor,
                now,
                normalizedActor,
                blankToNull(traceId)
            )));
        }
        return List.copyOf(saved);
    }

    /**
     * 将来源版本触发绑定复制到同一稳定资产身份的下一草稿版本。
     */
    @Transactional
    public List<AssetTriggerBinding> copyBindings(
            AssetVersion source,
            AssetVersion target,
            String actor,
            String traceId) {
        AssetVersion normalizedSource = requireVersion(source, "来源版本");
        AssetVersion normalizedTarget = requireDraft(target);
        if (!normalizedSource.tenantId().equals(normalizedTarget.tenantId())
                || normalizedSource.assetType() != normalizedTarget.assetType()
                || !normalizedSource.assetIdentity().equals(normalizedTarget.assetIdentity())) {
            throw invalid("触发绑定只能复制到同租户、同类型、同稳定身份的下一版本");
        }
        List<AssetTriggerBindingInput> inputs = repository
            .findByTenantIdAndVersionIdOrderByPurposeAscTriggerPointAsc(
                normalizedSource.tenantId(), normalizedSource.versionId())
            .stream()
            .map(binding -> new AssetTriggerBindingInput(
                binding.triggerPoint(),
                binding.purpose(),
                readFields(binding.requiredFieldsJson())
            ))
            .toList();
        return replaceBindings(normalizedTarget, inputs, actor, traceId);
    }

    /**
     * 查询一个精确资产版本的全部触发绑定。
     */
    @Transactional(readOnly = true)
    public List<AssetTriggerBinding> listBindings(AssetVersion version) {
        AssetVersion required = requireVersion(version, "资产版本");
        return List.copyOf(repository.findByTenantIdAndVersionIdOrderByPurposeAscTriggerPointAsc(
            required.tenantId(), required.versionId()));
    }

    /**
     * 判断精确资产版本是否声明指定用途与触发点。
     */
    @Transactional(readOnly = true)
    public boolean matches(
            AssetVersion version,
            AssetTriggerPurpose purpose,
            String triggerPoint) {
        AssetVersion required = requireVersion(version, "资产版本");
        validatePurpose(required.assetType(), purpose);
        String canonicalTrigger = requireCanonicalTrigger(triggerPoint);
        return !repository
            .findByTenantIdAndVersionIdAndPurposeAndTriggerPointOrderByTriggerBindingIdAsc(
                required.tenantId(),
                required.versionId(),
                purpose,
                canonicalTrigger
            )
            .isEmpty();
    }

    /**
     * 判断来源版本是否完整覆盖目标版本指定用途的全部触发点。
     *
     * <p>目标版本没有该用途绑定时返回 {@code false}，避免空配置被误判为可覆盖。
     */
    @Transactional(readOnly = true)
    public boolean covers(
            AssetVersion source,
            AssetVersion target,
            AssetTriggerPurpose purpose) {
        AssetVersion requiredSource = requireVersion(source, "来源版本");
        AssetVersion requiredTarget = requireVersion(target, "目标版本");
        validatePurpose(requiredSource.assetType(), purpose);
        validatePurpose(requiredTarget.assetType(), purpose);
        Set<String> sourceTriggers = triggersFor(requiredSource, purpose);
        Set<String> targetTriggers = triggersFor(requiredTarget, purpose);
        return !targetTriggers.isEmpty() && sourceTriggers.containsAll(targetTriggers);
    }

    /**
     * 判断两个精确资产版本在指定用途上是否至少共享一个触发点。
     */
    @Transactional(readOnly = true)
    public boolean overlaps(
            AssetVersion left,
            AssetVersion right,
            AssetTriggerPurpose purpose) {
        AssetVersion requiredLeft = requireVersion(left, "左侧版本");
        AssetVersion requiredRight = requireVersion(right, "右侧版本");
        validatePurpose(requiredLeft.assetType(), purpose);
        validatePurpose(requiredRight.assetType(), purpose);
        Set<String> leftTriggers = triggersFor(requiredLeft, purpose);
        return triggersFor(requiredRight, purpose).stream().anyMatch(leftTriggers::contains);
    }

    private Set<String> triggersFor(
            AssetVersion version,
            AssetTriggerPurpose purpose) {
        return repository.findByTenantIdAndVersionIdOrderByPurposeAscTriggerPointAsc(
                version.tenantId(), version.versionId())
            .stream()
            .filter(binding -> binding.purpose() == purpose)
            .map(AssetTriggerBinding::triggerPoint)
            .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
    }

    private List<NormalizedBinding> normalize(
            AssetVersion version,
            List<AssetTriggerBindingInput> inputs) {
        if (inputs == null || inputs.isEmpty()) {
            throw invalid("资产版本至少需要一个触发绑定");
        }
        Set<String> uniqueBindings = new LinkedHashSet<>();
        List<NormalizedBinding> normalized = new ArrayList<>(inputs.size());
        for (AssetTriggerBindingInput input : inputs) {
            if (input == null || input.purpose() == null) {
                throw invalid("触发绑定及用途不能为空");
            }
            validatePurpose(version.assetType(), input.purpose());
            String triggerPoint = requireCanonicalTrigger(input.triggerPoint());
            String uniqueKey = input.purpose().name() + ":" + triggerPoint;
            if (!uniqueBindings.add(uniqueKey)) {
                throw invalid("同一资产版本的触发点与用途不能重复: " + triggerPoint);
            }
            LinkedHashSet<String> fields = new LinkedHashSet<>();
            for (String field : input.requiredFields()) {
                String normalizedField = requireText(field, "必需字段编码");
                if (!fields.add(normalizedField)) {
                    throw invalid("必需字段编码不能重复: " + normalizedField);
                }
            }
            normalized.add(new NormalizedBinding(
                triggerPoint, input.purpose(), List.copyOf(fields)));
        }
        return List.copyOf(normalized);
    }

    private static void validatePurpose(
            VersionedAssetType assetType,
            AssetTriggerPurpose purpose) {
        boolean valid = assetType == VersionedAssetType.RULE
            ? purpose == AssetTriggerPurpose.RULE_EXECUTION
            : assetType == VersionedAssetType.PATHWAY
                && EnumSet.of(
                    AssetTriggerPurpose.PATHWAY_ENTRY_CANDIDATE,
                    AssetTriggerPurpose.PATHWAY_PROGRESS
                ).contains(purpose);
        if (!valid) {
            throw invalid("资产类型 " + assetType + " 不支持触发用途 " + purpose);
        }
    }

    private static String requireCanonicalTrigger(String value) {
        String triggerPoint = requireText(value, "触发点");
        boolean canonical = EnumSet.allOf(ClinicalEventTriggerPoint.class).stream()
            .anyMatch(candidate -> candidate.wireValue().equals(triggerPoint));
        if (!canonical) {
            throw invalid("触发点必须使用标准客户面编码: " + triggerPoint);
        }
        return triggerPoint;
    }

    private AssetVersion requireDraft(AssetVersion version) {
        AssetVersion required = requireVersion(version, "资产版本");
        if (required.status() != AssetVersionStatus.DRAFT) {
            throw new ApiException(ErrorCode.CONFLICT, "只有草稿资产版本可以修改触发绑定");
        }
        return required;
    }

    private static AssetVersion requireVersion(AssetVersion version, String label) {
        if (version == null
                || version.versionId() == null || version.versionId().isBlank()
                || version.tenantId() == null || version.tenantId().isBlank()
                || version.assetType() == null
                || version.assetIdentity() == null || version.assetIdentity().isBlank()) {
            throw invalid(label + "信息不完整");
        }
        return version;
    }

    private String writeFields(List<String> fields) {
        try {
            return json.writeValueAsString(fields);
        } catch (JsonProcessingException exception) {
            throw new ApiException(
                ErrorCode.INTERNAL_ERROR, "触发绑定必需字段序列化失败", exception);
        }
    }

    private List<String> readFields(String value) {
        try {
            if (value == null || value.isBlank()) {
                return List.of();
            }
            return List.copyOf(json.readValue(
                value,
                json.getTypeFactory().constructCollectionType(List.class, String.class)
            ));
        } catch (JsonProcessingException exception) {
            throw new ApiException(
                ErrorCode.INTERNAL_ERROR, "来源触发绑定必需字段不是合法 JSON 数组", exception);
        }
    }

    private static String requireText(String value, String label) {
        if (value == null || value.isBlank()) {
            throw invalid(label + "不能为空");
        }
        return value.trim();
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static ApiException invalid(String message) {
        return new ApiException(ErrorCode.VALIDATION_FAILED, message);
    }

    private record NormalizedBinding(
        String triggerPoint,
        AssetTriggerPurpose purpose,
        List<String> requiredFields
    ) {
        private NormalizedBinding {
            Objects.requireNonNull(triggerPoint);
            Objects.requireNonNull(purpose);
            requiredFields = List.copyOf(requiredFields);
        }
    }
}
