package com.medkernel.engine.followup;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.medkernel.engine.context.ClinicalRuntimeAssetSelection;
import com.medkernel.engine.context.ClinicalRuntimeRelease;
import com.medkernel.engine.context.ClinicalRuntimeReleaseCommand;
import com.medkernel.engine.context.ClinicalRuntimeReleaseContent;
import com.medkernel.engine.context.ClinicalRuntimeReleaseContentResolver;
import com.medkernel.engine.context.ClinicalRuntimeReleaseItem;
import com.medkernel.engine.context.ClinicalRuntimeReleaseService;
import com.medkernel.engine.context.CurrentClinicalRuntimeReleaseResolver;
import com.medkernel.engine.release.ReleaseEntryState;
import com.medkernel.engine.release.ReleaseSourceLayer;
import com.medkernel.engine.versioning.AssetVersion;
import com.medkernel.engine.versioning.AssetVersionRegisterCommand;
import com.medkernel.engine.versioning.AssetVersionRepository;
import com.medkernel.engine.versioning.AssetVersionService;
import com.medkernel.engine.versioning.AssetVersionStatus;
import com.medkernel.engine.versioning.ReleasePort;
import com.medkernel.engine.versioning.RolloutPolicy;
import com.medkernel.engine.versioning.VersionReleaseCommand;
import com.medkernel.engine.versioning.VersionReleaseScopeType;
import com.medkernel.engine.versioning.VersionedAssetType;
import com.medkernel.shared.api.PageRequest;
import com.medkernel.shared.api.PageResponse;
import com.medkernel.shared.api.error.ApiException;
import com.medkernel.shared.api.error.ErrorCode;
import com.medkernel.shared.audit.AuditAction;
import com.medkernel.shared.audit.AuditRecorder;
import com.medkernel.shared.context.OrgScope;
import com.medkernel.shared.context.RequestContext;
import com.medkernel.shared.ids.Ulid;

/**
 * 随访方案资产服务。
 */
@Service
public class FollowupTemplateService {

    private static final TypeReference<List<FollowupTemplateTaskInput>> TASK_LIST = new TypeReference<>() {};

    private final FollowupTemplateRepository templates;
    private final AssetVersionService versionedAssets;
    private final AssetVersionRepository assetVersions;
    private final ReleasePort releasePort;
    private final AuditRecorder auditRecorder;
    private final CurrentClinicalRuntimeReleaseResolver currentRuntimeReleaseResolver;
    private final ClinicalRuntimeReleaseContentResolver runtimeContentResolver;
    private final ClinicalRuntimeReleaseService runtimeReleaseService;
    private final ObjectMapper json = new ObjectMapper();

    @Autowired
    public FollowupTemplateService(
            FollowupTemplateRepository templates,
            AssetVersionService versionedAssets,
            AssetVersionRepository assetVersions,
            ReleasePort releasePort,
            AuditRecorder auditRecorder,
            CurrentClinicalRuntimeReleaseResolver currentRuntimeReleaseResolver,
            ClinicalRuntimeReleaseContentResolver runtimeContentResolver,
            ClinicalRuntimeReleaseService runtimeReleaseService) {
        this.templates = templates;
        this.versionedAssets = versionedAssets;
        this.assetVersions = assetVersions;
        this.releasePort = releasePort;
        this.auditRecorder = auditRecorder;
        this.currentRuntimeReleaseResolver = currentRuntimeReleaseResolver;
        this.runtimeContentResolver = runtimeContentResolver;
        this.runtimeReleaseService = runtimeReleaseService;
    }

    /**
     * 创建不可变方案草稿并登记统一配置资产版本。
     */
    @Transactional
    public FollowupTemplateResponse create(FollowupTemplateCreateRequest request) {
        String tenantId = tenantId();
        String templateCode = required(request.templateCode(), "院内随访方案身份");
        int versionNo = allocateNextVersion(tenantId, templateCode);
        validateTasks(request.tasks());
        JsonNode questionnaire = requireObject(request.questionnaireDefinition(), "问卷定义");
        JsonNode abnormalAction = requireObject(request.abnormalActionDefinition(), "异常处置定义");

        String templateId = "ftpl-" + Ulid.newUlid();
        String tasksJson = writeJson(request.tasks());
        String questionnaireJson = writeJson(questionnaire);
        String abnormalActionJson = writeJson(abnormalAction);
        String content = templateContent(
            templateId,
            request,
            versionNo,
            tasksJson,
            questionnaireJson,
            abnormalActionJson
        );
        String actor = actor();
        String traceId = RequestContext.currentTraceId();
        AssetVersion assetVersion;
        FollowupTemplate saved;
        try {
            assetVersion = versionedAssets.registerDraft(new AssetVersionRegisterCommand(
                tenantId,
                VersionedAssetType.FOLLOWUP,
                templateCode,
                null,
                required(request.applicableScope(), "适用范围"),
                content,
                null,
                required(request.sourceRef(), "来源引用"),
                actor,
                traceId
            ));
            Instant now = Instant.now();
            saved = templates.save(new FollowupTemplate(
                null,
                templateId,
                tenantId,
                templateCode,
                versionNo,
                required(request.name(), "方案名称"),
                normalize(request.description()),
                request.organizationScope().trim(),
                request.applicableScope().trim(),
                tasksJson,
                questionnaireJson,
                abnormalActionJson,
                request.sourceRef().trim(),
                assetVersion.versionId(),
                now,
                actor,
                now,
                actor,
                traceId
            ));
        } catch (DuplicateKeyException exception) {
            throw new ApiException(
                ErrorCode.CONFLICT,
                "随访方案版本并发创建冲突，请刷新后重试: " + templateCode + "@" + versionNo,
                exception
            );
        }
        auditRecorder.record(
            AuditAction.CREATE,
            "mk_followup_template",
            templateId,
            "创建随访方案 " + templateCode + "@" + versionNo
        );
        return response(saved, assetVersion);
    }

    /**
     * 按发布状态与关键词分页读取当前租户的随访方案。
     */
    @Transactional(readOnly = true)
    public PageResponse<FollowupTemplateResponse> list(
            FollowupTemplateFilter filter,
            PageRequest pageRequest) {
        PageRequest page = pageRequest == null ? PageRequest.defaults() : pageRequest;
        FollowupTemplateFilter f = filter == null
            ? new FollowupTemplateFilter(null, null)
            : filter;
        String keyword = normalizeKeyword(f.keyword());
        String assetStatus = f.assetStatus() == null ? null : f.assetStatus().name();
        String tenantId = tenantId();
        long total = templates.countByFilter(tenantId, keyword, assetStatus);
        List<FollowupTemplate> pageRows = templates.pageByFilter(
            tenantId, keyword, assetStatus, page.offset(), page.safeSize());
        List<FollowupTemplateResponse> rows = pageRows
            .stream()
            .map(template -> response(template, requireAssetVersion(template)))
            .toList();
        return PageResponse.of(rows, page, total);
    }

    /**
     * 依次完成审核流并全量发布方案版本。
     */
    @Transactional
    public FollowupTemplateResponse publish(
            String templateId,
            FollowupTemplatePublishRequest request) {
        FollowupTemplate template = requireTemplate(templateId);
        AssetVersion version = requireAssetVersion(template);
        VersionReleaseCommand command = releaseCommand(template, version, request);
        if (version.status() == AssetVersionStatus.DRAFT) {
            releasePort.submitForReview(command);
            releasePort.approveReview(command);
            releasePort.publish(command);
        } else if (version.status() != AssetVersionStatus.PUBLISHED) {
            throw new ApiException(
                ErrorCode.CONFLICT,
                "当前随访方案版本不可发布: " + version.status()
            );
        }
        Instant now = Instant.now();
        String actor = actor();
        AssetVersion publishedVersion = version.status() == AssetVersionStatus.PUBLISHED
            ? version
            : version.withStatus(
                AssetVersionStatus.PUBLISHED,
                version.activeScopeKey(),
                now,
                actor
            );
        activateCurrentHospitalRuntimeRelease(template, publishedVersion, actor, RequestContext.currentTraceId());
        auditRecorder.record(
            AuditAction.PUBLISH,
            "mk_followup_template",
            template.templateId(),
            "发布随访方案 " + template.templateCode() + "@" + template.versionNo()
        );
        return response(template, publishedVersion);
    }

    @Transactional(readOnly = true)
    public FollowupTemplate requirePublished(String templateId) {
        FollowupTemplate template = requireTemplate(templateId);
        AssetVersion version = requireAssetVersion(template);
        if (version.status() != AssetVersionStatus.PUBLISHED) {
            throw new ApiException(ErrorCode.ENG_FOLLOW_004, "随访方案尚未发布: " + templateId);
        }
        return template;
    }

    Optional<FollowupTemplate> findById(String tenantId, String templateId) {
        if (normalize(tenantId) == null || normalize(templateId) == null) {
            return Optional.empty();
        }
        return templates.findByTemplateIdAndTenantId(templateId.trim(), tenantId.trim());
    }

    Optional<FollowupTemplate> findByCodeAndVersion(
            String tenantId,
            String templateCode,
            Integer versionNo) {
        if (normalize(tenantId) == null || normalize(templateCode) == null || versionNo == null) {
            return Optional.empty();
        }
        return templates.findByTenantIdAndTemplateCodeAndVersionNo(
            tenantId.trim(),
            templateCode.trim(),
            versionNo);
    }

    List<FollowupTemplateTaskInput> tasks(FollowupTemplate template) {
        try {
            return json.readValue(template.taskDefinitionJson(), TASK_LIST);
        } catch (JsonProcessingException exception) {
            throw new ApiException(ErrorCode.ENG_FOLLOW_004, "随访方案任务定义损坏", exception);
        }
    }

    private VersionReleaseCommand releaseCommand(
            FollowupTemplate template,
            AssetVersion version,
            FollowupTemplatePublishRequest request) {
        return new VersionReleaseCommand(
            template.tenantId(),
            VersionedAssetType.FOLLOWUP,
            template.templateCode(),
            version.versionId(),
            version.organizationScope(),
            version.applicableScope(),
            VersionReleaseScopeType.ALL,
            null,
            RolloutPolicy.all(),
            required(request.impactDigest(), "影响摘要"),
            required(request.reason(), "审核说明"),
            actor(),
            RequestContext.currentTraceId(),
            null
        );
    }

    private void activateCurrentHospitalRuntimeRelease(
            FollowupTemplate template,
            AssetVersion version,
            String actor,
            String traceId) {
        OrgScope scope = RequestContext.currentOrgScope();
        if (scope == null || normalize(scope.hospitalId()) == null) {
            return;
        }
        ClinicalRuntimeRelease current = currentRuntimeReleaseResolver.resolve(scope);
        ClinicalRuntimeReleaseContent content =
            runtimeContentResolver.resolve(template.tenantId(), current.releaseId());
        boolean alreadyActive = content.items().stream()
            .anyMatch(item -> item.entryState() == ReleaseEntryState.ACTIVE
                && item.assetType() == VersionedAssetType.FOLLOWUP
                && template.templateCode().equals(item.assetIdentity())
                && version.versionId().equals(item.versionId()));
        if (alreadyActive) {
            return;
        }

        List<ClinicalRuntimeAssetSelection> activeAssets = new ArrayList<>();
        for (ClinicalRuntimeReleaseItem item : content.items()) {
            if (item.entryState() != ReleaseEntryState.ACTIVE) {
                continue;
            }
            if (item.assetType() == VersionedAssetType.FOLLOWUP
                    && template.templateCode().equals(item.assetIdentity())) {
                continue;
            }
            activeAssets.add(selectionFromRuntimeItem(item));
        }
        activeAssets.add(ClinicalRuntimeAssetSelection.local(
            VersionedAssetType.FOLLOWUP,
            template.templateCode(),
            version.versionId()
        ));

        runtimeReleaseService.activate(new ClinicalRuntimeReleaseCommand(
            template.tenantId(),
            current.hospitalId(),
            current.platformBaselineReleaseId(),
            current.releaseId(),
            activeAssets,
            actor,
            traceId
        ));
    }

    private ClinicalRuntimeAssetSelection selectionFromRuntimeItem(ClinicalRuntimeReleaseItem item) {
        if (item.sourceLayer() == ReleaseSourceLayer.PLATFORM) {
            return ClinicalRuntimeAssetSelection.platform(
                item.assetType(),
                item.assetIdentity()
            );
        }
        return ClinicalRuntimeAssetSelection.local(
            item.assetType(),
            item.assetIdentity(),
            required(item.versionId(), "机构生效版本本地资产版本 ID")
        );
    }

    private FollowupTemplateResponse response(FollowupTemplate template, AssetVersion version) {
        return new FollowupTemplateResponse(
            template.templateId(),
            template.templateCode(),
            template.versionNo(),
            template.name(),
            template.description(),
            template.organizationScope(),
            template.applicableScope(),
            tasks(template),
            template.questionnaireDefinitionJson(),
            template.abnormalActionJson(),
            template.sourceRef(),
            template.assetVersionId(),
            version.status(),
            version.contentHash(),
            template.updatedAt(),
            template.traceId()
        );
    }

    private FollowupTemplate requireTemplate(String templateId) {
        return templates.findByTemplateIdAndTenantId(required(templateId, "方案 ID"), tenantId())
            .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "随访方案不存在: " + templateId));
    }

    private static String normalizeKeyword(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return "%" + value.trim().toLowerCase() + "%";
    }

    private AssetVersion requireAssetVersion(FollowupTemplate template) {
        return assetVersions.findByVersionIdAndTenantId(template.assetVersionId(), template.tenantId())
            .orElseThrow(() -> new ApiException(
                ErrorCode.ENG_FOLLOW_004,
                "随访方案缺少统一资产版本: " + template.templateId()
            ));
    }

    private void validateTasks(List<FollowupTemplateTaskInput> tasks) {
        if (tasks == null || tasks.isEmpty()) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, "随访方案至少包含一个任务");
        }
        List<FollowupTaskType> seen = new ArrayList<>();
        for (FollowupTemplateTaskInput task : tasks) {
            if (task == null || task.taskType() == null || task.delayDays() == null
                    || task.delayDays() < 0 || task.delayDays() > 3650) {
                throw new ApiException(ErrorCode.VALIDATION_FAILED, "随访方案任务定义不完整");
            }
            if (!seen.add(task.taskType())) {
                throw new ApiException(ErrorCode.CONFLICT, "随访方案任务类型重复: " + task.taskType());
            }
            if (task.taskType() == FollowupTaskType.QUESTIONNAIRE
                    && normalize(task.questionnaireTemplateId()) == null) {
                throw new ApiException(ErrorCode.VALIDATION_FAILED, "问卷任务必须绑定问卷内容");
            }
        }
    }

    private String templateContent(
            String templateId,
            FollowupTemplateCreateRequest request,
            int versionNo,
            String tasksJson,
            String questionnaireJson,
            String abnormalActionJson) {
        Map<String, Object> content = new LinkedHashMap<>();
        content.put("templateId", templateId);
        content.put("templateCode", request.templateCode().trim());
        content.put("versionNo", versionNo);
        content.put("name", request.name().trim());
        content.put("description", normalize(request.description()));
        content.put("organizationScope", request.organizationScope().trim());
        content.put("applicableScope", request.applicableScope().trim());
        content.put("tasks", readTree(tasksJson));
        content.put("questionnaireDefinition", readTree(questionnaireJson));
        content.put("abnormalActionDefinition", readTree(abnormalActionJson));
        content.put("sourceRef", request.sourceRef().trim());
        return writeJson(content);
    }

    private int allocateNextVersion(String tenantId, String templateCode) {
        return templates.findTopByTenantIdAndTemplateCodeOrderByVersionNoDesc(
                tenantId, templateCode
            )
            .map(FollowupTemplate::versionNo)
            .orElse(0) + 1;
    }

    private JsonNode requireObject(String value, String label) {
        JsonNode node = readTree(required(value, label));
        if (!node.isObject()) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, label + "必须是 JSON 对象");
        }
        return node;
    }

    private JsonNode readTree(String value) {
        try {
            return json.readTree(value);
        } catch (JsonProcessingException exception) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, "随访方案 JSON 定义不合法", exception);
        }
    }

    private String writeJson(Object value) {
        try {
            return json.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("随访方案内容序列化失败", exception);
        }
    }

    private String tenantId() {
        String tenantId = RequestContext.currentOrgScope().tenantId();
        if (tenantId == null || tenantId.isBlank()) {
            throw ApiException.tenantMissing();
        }
        return tenantId;
    }

    private String actor() {
        return RequestContext.currentUserId().orElse("system");
    }

    private String required(String value, String label) {
        String normalized = normalize(value);
        if (normalized == null) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, label + "不能为空");
        }
        return normalized;
    }

    private String normalize(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
