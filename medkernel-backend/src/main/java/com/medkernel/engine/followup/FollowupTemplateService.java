package com.medkernel.engine.followup;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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
import com.medkernel.shared.context.RequestContext;
import com.medkernel.shared.ids.Ulid;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 随访模板资产服务。
 */
@Service
public class FollowupTemplateService {

    private static final TypeReference<List<FollowupTemplateTaskInput>> TASK_LIST = new TypeReference<>() {};

    private final FollowupTemplateRepository templates;
    private final AssetVersionService versionedAssets;
    private final AssetVersionRepository assetVersions;
    private final ReleasePort releasePort;
    private final AuditRecorder auditRecorder;
    private final ObjectMapper json = new ObjectMapper();

    public FollowupTemplateService(
            FollowupTemplateRepository templates,
            AssetVersionService versionedAssets,
            AssetVersionRepository assetVersions,
            ReleasePort releasePort,
            AuditRecorder auditRecorder) {
        this.templates = templates;
        this.versionedAssets = versionedAssets;
        this.assetVersions = assetVersions;
        this.releasePort = releasePort;
        this.auditRecorder = auditRecorder;
    }

    /**
     * 创建不可变模板草稿并登记统一配置资产版本。
     */
    @Transactional
    public FollowupTemplateResponse create(FollowupTemplateCreateRequest request) {
        String tenantId = tenantId();
        String templateCode = required(request.templateCode(), "模板编码");
        Integer versionNo = request.versionNo();
        templates.findByTenantIdAndTemplateCodeAndVersionNo(tenantId, templateCode, versionNo)
            .ifPresent(existing -> {
                throw new ApiException(
                    ErrorCode.CONFLICT,
                    "随访模板编码和版本已存在: " + templateCode + "@" + versionNo
                );
            });
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
            tasksJson,
            questionnaireJson,
            abnormalActionJson
        );
        String actor = actor();
        String traceId = RequestContext.currentTraceId();
        AssetVersion assetVersion = versionedAssets.registerDraft(new AssetVersionRegisterCommand(
            tenantId,
            VersionedAssetType.FOLLOWUP,
            templateId,
            String.valueOf(versionNo),
            required(request.organizationScope(), "组织生效域"),
            required(request.applicableScope(), "适用范围"),
            content,
            null,
            required(request.sourceRef(), "来源引用"),
            actor,
            traceId
        ));
        Instant now = Instant.now();
        FollowupTemplate saved = templates.save(new FollowupTemplate(
            null,
            templateId,
            tenantId,
            templateCode,
            versionNo,
            required(request.name(), "模板名称"),
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
        auditRecorder.record(
            AuditAction.CREATE,
            "mk_followup_template",
            templateId,
            "创建随访模板 " + templateCode + "@" + versionNo
        );
        return response(saved, assetVersion);
    }

    /**
     * 按发布状态与关键词分页读取当前租户的随访模板。
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
     * 依次完成审核流并全量发布模板版本。
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
        } else if (version.status() == AssetVersionStatus.IN_REVIEW) {
            releasePort.approveReview(command);
            releasePort.publish(command);
        } else if (version.status() == AssetVersionStatus.APPROVED) {
            releasePort.publish(command);
        } else if (version.status() != AssetVersionStatus.PUBLISHED) {
            throw new ApiException(
                ErrorCode.CONFLICT,
                "当前随访模板版本不可发布: " + version.status()
            );
        }
        auditRecorder.record(
            AuditAction.PUBLISH,
            "mk_followup_template",
            template.templateId(),
            "发布随访模板 " + template.templateCode() + "@" + template.versionNo()
        );
        return response(template, version.withStatus(
            AssetVersionStatus.PUBLISHED,
            version.activeScopeKey(),
            Instant.now(),
            actor()
        ));
    }

    @Transactional(readOnly = true)
    public FollowupTemplate requirePublished(String templateId) {
        FollowupTemplate template = requireTemplate(templateId);
        AssetVersion version = requireAssetVersion(template);
        if (version.status() != AssetVersionStatus.PUBLISHED) {
            throw new ApiException(ErrorCode.ENG_FOLLOW_004, "随访模板尚未发布: " + templateId);
        }
        return template;
    }

    List<FollowupTemplateTaskInput> tasks(FollowupTemplate template) {
        try {
            return json.readValue(template.taskDefinitionJson(), TASK_LIST);
        } catch (JsonProcessingException exception) {
            throw new ApiException(ErrorCode.ENG_FOLLOW_004, "随访模板任务定义损坏", exception);
        }
    }

    private VersionReleaseCommand releaseCommand(
            FollowupTemplate template,
            AssetVersion version,
            FollowupTemplatePublishRequest request) {
        return new VersionReleaseCommand(
            template.tenantId(),
            VersionedAssetType.FOLLOWUP,
            template.templateId(),
            version.versionId(),
            template.organizationScope(),
            template.applicableScope(),
            VersionReleaseScopeType.ALL,
            null,
            RolloutPolicy.all(),
            required(request.impactDigest(), "影响摘要"),
            required(request.reason(), "审核说明"),
            actor(),
            RequestContext.currentTraceId(),
            null,
            null
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
        return templates.findByTemplateIdAndTenantId(required(templateId, "模板 ID"), tenantId())
            .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "随访模板不存在: " + templateId));
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
                "随访模板缺少统一资产版本: " + template.templateId()
            ));
    }

    private void validateTasks(List<FollowupTemplateTaskInput> tasks) {
        if (tasks == null || tasks.isEmpty()) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, "随访模板至少包含一个任务");
        }
        List<FollowupTaskType> seen = new ArrayList<>();
        for (FollowupTemplateTaskInput task : tasks) {
            if (task == null || task.taskType() == null || task.delayDays() == null
                    || task.delayDays() < 0 || task.delayDays() > 3650) {
                throw new ApiException(ErrorCode.VALIDATION_FAILED, "随访模板任务定义不完整");
            }
            if (!seen.add(task.taskType())) {
                throw new ApiException(ErrorCode.CONFLICT, "随访模板任务类型重复: " + task.taskType());
            }
            if (task.taskType() == FollowupTaskType.QUESTIONNAIRE
                    && normalize(task.questionnaireTemplateId()) == null) {
                throw new ApiException(ErrorCode.VALIDATION_FAILED, "问卷任务必须绑定问卷模板 ID");
            }
        }
    }

    private String templateContent(
            String templateId,
            FollowupTemplateCreateRequest request,
            String tasksJson,
            String questionnaireJson,
            String abnormalActionJson) {
        Map<String, Object> content = new LinkedHashMap<>();
        content.put("templateId", templateId);
        content.put("templateCode", request.templateCode().trim());
        content.put("versionNo", request.versionNo());
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
            throw new ApiException(ErrorCode.VALIDATION_FAILED, "随访模板 JSON 定义不合法", exception);
        }
    }

    private String writeJson(Object value) {
        try {
            return json.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("随访模板内容序列化失败", exception);
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
