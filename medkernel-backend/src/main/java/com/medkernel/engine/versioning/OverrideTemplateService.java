package com.medkernel.engine.versioning;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.medkernel.engine.org.OrgHierarchyRepository;
import com.medkernel.engine.org.OrgUnit;
import com.medkernel.engine.org.OrgUnitRepository;
import com.medkernel.shared.api.PageRequest;
import com.medkernel.shared.api.PageResponse;
import com.medkernel.shared.api.error.ApiException;
import com.medkernel.shared.api.error.ErrorCode;
import com.medkernel.shared.ids.Ulid;

/**
 * 覆盖模板、批量应用/撤销和跨组织克隆编排。
 */
@Service
public class OverrideTemplateService {

    private final OverrideTemplateRepository templates;
    private final OverrideTemplateItemRepository items;
    private final OverrideOperationRepository operations;
    private final InheritanceOverrideRepository overrides;
    private final InheritanceOverrideService overrideService;
    private final OrgUnitRepository orgUnits;
    private final OrgHierarchyRepository orgHierarchy;
    private final ObjectMapper json;
    private final Clock clock;

    @Autowired
    public OverrideTemplateService(
            OverrideTemplateRepository templates,
            OverrideTemplateItemRepository items,
            OverrideOperationRepository operations,
            InheritanceOverrideRepository overrides,
            InheritanceOverrideService overrideService,
            OrgUnitRepository orgUnits,
            OrgHierarchyRepository orgHierarchy,
            ObjectMapper json) {
        this(
            templates,
            items,
            operations,
            overrides,
            overrideService,
            orgUnits,
            orgHierarchy,
            json,
            Clock.systemUTC()
        );
    }

    OverrideTemplateService(
            OverrideTemplateRepository templates,
            OverrideTemplateItemRepository items,
            OverrideOperationRepository operations,
            InheritanceOverrideRepository overrides,
            InheritanceOverrideService overrideService,
            OrgUnitRepository orgUnits,
            OrgHierarchyRepository orgHierarchy,
            ObjectMapper json,
            Clock clock) {
        this.templates = templates;
        this.items = items;
        this.operations = operations;
        this.overrides = overrides;
        this.overrideService = overrideService;
        this.orgUnits = orgUnits;
        this.orgHierarchy = orgHierarchy;
        this.json = json;
        this.clock = clock;
    }

    @Transactional
    public OverrideTemplateDetail createTemplate(OverrideTemplateCreateCommand command) {
        validateCreate(command);
        if (templates.findByTenantIdAndTemplateName(
                command.tenantId().trim(),
                command.templateName().trim()
            ).isPresent()) {
            throw new ApiException(ErrorCode.CONFLICT, "覆盖模板名称已存在");
        }
        Instant now = clock.instant();
        OverrideTemplate saved = templates.save(new OverrideTemplate(
            null,
            "ovt-" + Ulid.newUlid(),
            command.tenantId().trim(),
            command.templateName().trim(),
            blankToNull(command.description()),
            command.applicableScope().trim(),
            OverrideTemplateStatus.ACTIVE,
            now,
            command.actor().trim(),
            now,
            command.actor().trim(),
            blankToNull(command.traceId())
        ));
        List<OverrideTemplateItem> savedItems = new ArrayList<>();
        for (OverrideTemplateItemInput input : command.items()) {
            validateItem(input, command.applicableScope());
            savedItems.add(items.save(new OverrideTemplateItem(
                null,
                "ovi-" + Ulid.newUlid(),
                saved.templateId(),
                input.assetType(),
                input.assetIdentity().trim(),
                blankToNull(input.inheritedVersionId()),
                blankToNull(input.sourceOverrideVersionId()),
                input.overrideMode(),
                input.propagation() == null ? InheritancePropagation.INHERITABLE : input.propagation(),
                input.applicableScope().trim(),
                input.diffSummary().trim(),
                input.overrideReason().trim()
            )));
        }
        return new OverrideTemplateDetail(saved, savedItems);
    }

    @Transactional(readOnly = true)
    public PageResponse<OverrideTemplate> listTemplates(String tenantId, PageRequest request) {
        String requiredTenant = required(tenantId, "租户");
        PageRequest safe = request == null ? PageRequest.defaults() : request;
        long total = templates.countByTenantIdAndStatus(requiredTenant, OverrideTemplateStatus.ACTIVE);
        return PageResponse.of(
            templates.pageByTenantIdAndStatus(
                requiredTenant,
                OverrideTemplateStatus.ACTIVE,
                safe.offset(),
                safe.safeSize()
            ),
            safe,
            total
        );
    }

    @Transactional(readOnly = true)
    public OverrideBatchPreviewResult preview(OverrideBatchPreviewCommand command) {
        validatePreview(command);
        SourceItems source = sourceItems(command);
        List<OverrideBatchPreviewResult.Row> rows = new ArrayList<>();
        for (String targetOrgUnitId : command.targetOrgUnitIds().stream().distinct().toList()) {
            OrgUnit target = orgUnits.findByTenantIdAndId(command.tenantId(), targetOrgUnitId)
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "目标组织不存在: " + targetOrgUnitId));
            if (!target.isActive()) {
                throw new ApiException(ErrorCode.CONFLICT, "目标组织未启用: " + targetOrgUnitId);
            }
            for (SourceItem item : source.items()) {
                rows.add(previewRow(command, target, item));
            }
        }
        String operationType = source.cloned() ? OverrideOperationType.CLONE.name() : OverrideOperationType.APPLY.name();
        boolean releasable = rows.stream().allMatch(row -> "READY".equals(row.status()));
        return new OverrideBatchPreviewResult(
            previewDigest(command.tenantId(), operationType, rows),
            operationType,
            rows,
            releasable
        );
    }

    @Transactional
    public OverrideBatchOperationResult apply(OverrideBatchApplyCommand command) {
        if (command == null || command.preview() == null) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, "批量生效命令不能为空");
        }
        OverrideBatchPreviewResult preview = preview(command.preview());
        if (!Objects.equals(preview.previewDigest(), required(
                command.confirmedPreviewDigest(),
                "确认的预演摘要"
            ))) {
            throw new ApiException(ErrorCode.CONFLICT, "预演摘要已变化，请重新预演后再生效");
        }
        if (!preview.releasable()) {
            throw new ApiException(ErrorCode.CONFLICT, "预演存在冲突或缺少目标版本，禁止批量生效");
        }
        List<String> overrideIds = new ArrayList<>();
        for (OverrideBatchPreviewResult.Row row : preview.rows()) {
            InheritanceOverride saved = overrideService.registerOverride(
                new InheritanceOverrideRegisterCommand(
                    command.preview().tenantId(),
                    row.assetType(),
                    row.assetIdentity(),
                    row.inheritedVersionId(),
                    row.targetVersionId(),
                    row.targetOrgUnitId(),
                    row.applicableScope(),
                    row.overrideMode(),
                    row.diffSummary(),
                    row.overrideReason(),
                    "批量覆盖目标组织 " + row.targetOrgUnitId(),
                    command.preview().actor(),
                    command.preview().traceId(),
                    row.propagation()
                )
            );
            overrideIds.add(saved.overrideId());
        }
        Instant now = clock.instant();
        OverrideOperation savedOperation = operations.save(new OverrideOperation(
            null,
            "ovo-" + Ulid.newUlid(),
            command.preview().tenantId(),
            OverrideOperationType.valueOf(preview.operationType()),
            blankToNull(command.preview().templateId()),
            blankToNull(command.preview().sourceOrgUnitId()),
            writeJson(command.preview().targetOrgUnitIds()),
            OverrideOperationStatus.APPLIED,
            preview.previewDigest(),
            writeJson(Map.of("overrideIds", overrideIds)),
            now,
            command.preview().actor().trim(),
            blankToNull(command.preview().traceId())
        ));
        return new OverrideBatchOperationResult(
            savedOperation.operationId(),
            savedOperation.status(),
            overrideIds,
            preview.previewDigest()
        );
    }

    @Transactional
    public OverrideBatchOperationResult revoke(OverrideBatchRevokeCommand command) {
        if (command == null
                || isBlank(command.tenantId())
                || isBlank(command.operationId())
                || isBlank(command.actor())) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, "撤销命令租户、操作 ID 与操作人不能为空");
        }
        OverrideOperation operation = operations.findByOperationIdAndTenantId(
            command.operationId(),
            command.tenantId()
        ).orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "批量覆盖操作不存在"));
        List<String> overrideIds = readOverrideIds(operation.resultSummaryJson());
        if (operation.status() == OverrideOperationStatus.REVOKED) {
            return new OverrideBatchOperationResult(
                operation.operationId(),
                operation.status(),
                overrideIds,
                operation.previewDigest()
            );
        }
        if (operation.status() != OverrideOperationStatus.APPLIED) {
            throw new ApiException(ErrorCode.CONFLICT, "只有已生效批量覆盖可以撤销");
        }
        for (String overrideId : overrideIds) {
            overrideService.retireOverride(
                command.tenantId(),
                overrideId,
                command.actor(),
                command.traceId()
            );
        }
        OverrideOperation revoked = operations.save(operation.withStatus(
            OverrideOperationStatus.REVOKED,
            writeJson(Map.of("overrideIds", overrideIds, "revokedBy", command.actor()))
        ));
        return new OverrideBatchOperationResult(
            revoked.operationId(),
            revoked.status(),
            overrideIds,
            revoked.previewDigest()
        );
    }

    private SourceItems sourceItems(OverrideBatchPreviewCommand command) {
        boolean hasTemplate = !isBlank(command.templateId());
        boolean hasSourceOrg = !isBlank(command.sourceOrgUnitId());
        if (hasTemplate == hasSourceOrg) {
            throw new ApiException(
                ErrorCode.VALIDATION_FAILED,
                "模板应用与跨组织克隆必须且只能选择一种来源"
            );
        }
        if (hasTemplate) {
            OverrideTemplate template = templates.findByTemplateIdAndTenantId(
                command.templateId(),
                command.tenantId()
            ).filter(value -> value.status() == OverrideTemplateStatus.ACTIVE)
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "覆盖模板不存在或已归档"));
            List<SourceItem> sourceItems = items
                .findByTemplateIdOrderByAssetTypeAscAssetIdentityAsc(template.templateId())
                .stream()
                .map(item -> new SourceItem(
                    item.itemId(),
                    item.assetType(),
                    item.assetIdentity(),
                    item.inheritedVersionId(),
                    item.sourceOverrideVersionId(),
                    item.overrideMode(),
                    item.propagation(),
                    item.applicableScope(),
                    item.diffSummary(),
                    item.overrideReason()
                ))
                .toList();
            if (sourceItems.isEmpty()) {
                throw new ApiException(ErrorCode.CONFLICT, "覆盖模板没有可应用项");
            }
            return new SourceItems(false, sourceItems);
        }
        OrgUnit sourceOrg = orgUnits.findByTenantIdAndId(command.tenantId(), command.sourceOrgUnitId())
            .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "克隆来源组织不存在"));
        List<SourceItem> sourceItems = overrides.findByTenantIdAndOrgPathAndLifecycleStatus(
                command.tenantId(),
                sourceOrg.orgPath(),
                InheritanceOverrideStatus.ACTIVE
            ).stream()
            .map(override -> new SourceItem(
                override.overrideId(),
                override.assetType(),
                override.assetIdentity(),
                override.inheritedVersionId(),
                override.overrideVersionId(),
                override.overrideMode(),
                override.propagation(),
                override.applicableScope(),
                override.diffSummary(),
                override.overrideReason()
            ))
            .toList();
        if (sourceItems.isEmpty()) {
            throw new ApiException(ErrorCode.CONFLICT, "克隆来源组织没有已发布覆盖");
        }
        return new SourceItems(true, sourceItems);
    }

    private OverrideBatchPreviewResult.Row previewRow(
            OverrideBatchPreviewCommand command,
            OrgUnit target,
            SourceItem item) {
        String targetVersionKey = targetVersionKey(
            target.id(),
            item.assetType(),
            item.assetIdentity()
        );
        String targetVersionId = item.overrideMode() == InheritanceOverrideMode.DISABLE
            ? null
            : blankToNull(command.targetVersionIds().get(targetVersionKey));
        String status = "READY";
        String issue = null;
        if (item.overrideMode() != InheritanceOverrideMode.DISABLE && targetVersionId == null) {
            status = "TARGET_VERSION_REQUIRED";
            issue = "目标组织必须提供本组织已发布版本，映射键：" + targetVersionKey;
        }
        List<InheritanceOverride> existing = overrides
            .findByTenantIdAndAssetTypeAndAssetIdentityAndOrgPathAndApplicableScopeAndLifecycleStatus(
                command.tenantId(),
                item.assetType(),
                item.assetIdentity(),
                target.orgPath(),
                item.applicableScope(),
                InheritanceOverrideStatus.ACTIVE
            );
        if (!existing.isEmpty()) {
            status = "EXISTING_OVERRIDE_CONFLICT";
            issue = "目标组织已有已发布覆盖：" + existing.get(0).overrideId();
        }
        return new OverrideBatchPreviewResult.Row(
            item.sourceId(),
            target.id(),
            item.assetType(),
            item.assetIdentity(),
            item.overrideMode(),
            item.propagation(),
            item.applicableScope(),
            item.inheritedVersionId(),
            targetVersionId,
            item.diffSummary(),
            item.overrideReason(),
            status,
            issue
        );
    }

    private String previewDigest(
            String tenantId,
            String operationType,
            List<OverrideBatchPreviewResult.Row> rows) {
        Map<String, Object> evidence = new LinkedHashMap<>();
        evidence.put("tenantId", tenantId);
        evidence.put("operationType", operationType);
        evidence.put("rows", rows);
        try {
            byte[] bytes = json.writeValueAsString(evidence).getBytes(StandardCharsets.UTF_8);
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("JDK 缺少 SHA-256 摘要算法", exception);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("覆盖预演证据序列化失败", exception);
        }
    }

    private void validateCreate(OverrideTemplateCreateCommand command) {
        if (command == null
                || isBlank(command.tenantId())
                || isBlank(command.templateName())
                || isBlank(command.applicableScope())
                || isBlank(command.actor())) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, "覆盖模板名称、适用范围与操作人不能为空");
        }
        if (command.items().isEmpty()) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, "覆盖模板至少包含一个资产项");
        }
    }

    private void validateItem(OverrideTemplateItemInput item, String templateScope) {
        if (item == null
                || item.assetType() == null
                || isBlank(item.assetIdentity())
                || item.overrideMode() == null
                || isBlank(item.applicableScope())
                || isBlank(item.diffSummary())
                || isBlank(item.overrideReason())) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, "覆盖模板资产项字段不完整");
        }
        if (!Objects.equals(templateScope.trim(), item.applicableScope().trim())) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, "模板项适用范围必须与模板一致");
        }
        if (item.overrideMode() != InheritanceOverrideMode.ADD && isBlank(item.inheritedVersionId())) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, "REPLACE/DISABLE 模板项必须绑定被继承版本");
        }
    }

    private void validatePreview(OverrideBatchPreviewCommand command) {
        if (command == null
                || isBlank(command.tenantId())
                || command.targetOrgUnitIds().isEmpty()
                || isBlank(command.actor())) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, "批量预演租户、目标组织与操作人不能为空");
        }
    }

    private String targetVersionKey(
            String targetOrgUnitId,
            VersionedAssetType assetType,
            String assetIdentity) {
        return targetOrgUnitId + "|" + assetType.name() + "|" + assetIdentity;
    }

    private String writeJson(Object value) {
        try {
            return json.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("覆盖批量操作证据序列化失败", exception);
        }
    }

    private List<String> readOverrideIds(String resultSummaryJson) {
        try {
            return java.util.stream.StreamSupport
                .stream(json.readTree(resultSummaryJson).path("overrideIds").spliterator(), false)
                .filter(node -> node.isTextual() && !node.asText().isBlank())
                .map(node -> node.asText())
                .distinct()
                .toList();
        } catch (JsonProcessingException exception) {
            throw new ApiException(
                ErrorCode.CONFLICT,
                "批量覆盖操作证据无法解析，禁止模糊撤销",
                exception
            );
        }
    }

    private String required(String value, String label) {
        if (isBlank(value)) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, label + "不能为空");
        }
        return value.trim();
    }

    private String blankToNull(String value) {
        return isBlank(value) ? null : value.trim();
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private record SourceItems(boolean cloned, List<SourceItem> items) {
    }

    private record SourceItem(
        String sourceId,
        VersionedAssetType assetType,
        String assetIdentity,
        String inheritedVersionId,
        String sourceOverrideVersionId,
        InheritanceOverrideMode overrideMode,
        InheritancePropagation propagation,
        String applicableScope,
        String diffSummary,
        String overrideReason
    ) {
    }
}
