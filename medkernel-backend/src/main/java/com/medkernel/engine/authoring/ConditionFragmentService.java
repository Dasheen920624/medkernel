package com.medkernel.engine.authoring;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.medkernel.engine.pathway.PathwayEdge;
import com.medkernel.engine.pathway.PathwayEdgeRepository;
import com.medkernel.engine.pathway.PathwayTemplate;
import com.medkernel.engine.pathway.PathwayTemplateRepository;
import com.medkernel.engine.rule.ConditionFragmentReference;
import com.medkernel.engine.rule.ConditionFragmentResolver;
import com.medkernel.engine.rule.RuleDefinition;
import com.medkernel.engine.rule.RuleDefinitionRepository;
import com.medkernel.engine.rule.RuleVersion;
import com.medkernel.engine.rule.RuleVersionRepository;
import com.medkernel.shared.api.PageRequest;
import com.medkernel.shared.api.PageResponse;
import com.medkernel.shared.api.error.ApiException;
import com.medkernel.shared.api.error.ErrorCode;
import com.medkernel.shared.audit.AuditAction;
import com.medkernel.shared.audit.AuditRecorder;
import com.medkernel.shared.context.RequestContext;
import com.medkernel.shared.hash.Sha256ContentHash;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 条件片段库应用服务。
 */
@Service
public class ConditionFragmentService implements ConditionFragmentResolver {

    private static final String ENTITY = "mk_engine_condition_fragment";

    private final ObjectMapper json;
    private final ConditionFragmentRepository fragments;
    private final RuleDefinitionRepository ruleDefinitions;
    private final RuleVersionRepository ruleVersions;
    private final PathwayTemplateRepository pathwayTemplates;
    private final PathwayEdgeRepository pathwayEdges;
    private final ConditionFragmentAssetVersionProjector versionProjector;
    private final AuditRecorder auditRecorder;

    public ConditionFragmentService(
            ObjectMapper json,
            ConditionFragmentRepository fragments,
            RuleDefinitionRepository ruleDefinitions,
            RuleVersionRepository ruleVersions,
            PathwayTemplateRepository pathwayTemplates,
            PathwayEdgeRepository pathwayEdges,
            ConditionFragmentAssetVersionProjector versionProjector,
            AuditRecorder auditRecorder) {
        this.json = json;
        this.fragments = fragments;
        this.ruleDefinitions = ruleDefinitions;
        this.ruleVersions = ruleVersions;
        this.pathwayTemplates = pathwayTemplates;
        this.pathwayEdges = pathwayEdges;
        this.versionProjector = versionProjector;
        this.auditRecorder = auditRecorder;
    }

    /**
     * 创建条件片段。
     */
    @Transactional
    public ConditionFragmentResponse create(ConditionFragmentUpsertRequest request) {
        String tenantId = requireCurrentTenant();
        FragmentInput input = input(request);
        optional(fragments.findByTenantIdAndFragmentCodeAndVersionNo(
            tenantId, input.fragmentCode(), input.versionNo()))
            .ifPresent(existing -> {
                throw invalid("条件片段编码和版本已存在: " + input.fragmentCode() + " v" + input.versionNo());
            });
        validateBodyGraph(tenantId, input.rootKey(), input.bodyJson(), new LinkedHashSet<>());

        Instant now = Instant.now();
        String actor = RequestContext.currentUserId().orElse("system");
        String traceId = RequestContext.currentTraceId();
        ConditionFragment saved = fragments.save(new ConditionFragment(
            null,
            "cf-" + UUID.randomUUID(),
            tenantId,
            input.fragmentCode(),
            input.name(),
            input.category(),
            writeJson(input.bodyJson(), "条件片段正文无法序列化"),
            input.versionNo(),
            input.status(),
            input.packageVersion(),
            now,
            actor,
            now,
            actor,
            traceId));
        versionProjector.project(saved);
        auditRecorder.record(AuditAction.CREATE, ENTITY, saved.fragmentId(), "创建条件片段 " + saved.fragmentCode());
        return response(saved);
    }

    /**
     * 更新条件片段。
     */
    @Transactional
    public ConditionFragmentResponse update(String fragmentId, ConditionFragmentUpsertRequest request) {
        String tenantId = requireCurrentTenant();
        ConditionFragment existing = findFragment(fragmentId, tenantId);
        FragmentInput input = input(request);
        validateImmutableVersion(existing, input);
        optional(fragments.findByTenantIdAndFragmentCodeAndVersionNo(
            tenantId, input.fragmentCode(), input.versionNo()))
            .filter(candidate -> !Objects.equals(candidate.fragmentId(), existing.fragmentId()))
            .ifPresent(candidate -> {
                throw invalid("条件片段编码和版本已存在: " + input.fragmentCode() + " v" + input.versionNo());
            });
        validateBodyGraph(tenantId, input.rootKey(), input.bodyJson(), new LinkedHashSet<>());

        Instant now = Instant.now();
        String actor = RequestContext.currentUserId().orElse("system");
        ConditionFragment saved = fragments.save(new ConditionFragment(
            existing.id(),
            existing.fragmentId(),
            existing.tenantId(),
            input.fragmentCode(),
            input.name(),
            input.category(),
            writeJson(input.bodyJson(), "条件片段正文无法序列化"),
            input.versionNo(),
            input.status(),
            input.packageVersion(),
            existing.createdAt(),
            existing.createdBy(),
            now,
            actor,
            RequestContext.currentTraceId()));
        versionProjector.project(saved);
        auditRecorder.record(AuditAction.UPDATE, ENTITY, saved.fragmentId(), "更新条件片段 " + saved.fragmentCode());
        return response(saved);
    }

    /**
     * 分页查询条件片段。
     */
    @Transactional(readOnly = true)
    public PageResponse<ConditionFragmentResponse> list(
            ConditionFragmentStatus status,
            String packageVersion,
            String keyword,
            PageRequest page) {
        String tenantId = requireCurrentTenant();
        PageRequest safePage = page == null ? PageRequest.defaults() : page;
        String statusName = status == null ? null : status.name();
        String normalizedKeyword = normalize(keyword);
        String normalizedPackageVersion = normalize(packageVersion);
        List<ConditionFragmentResponse> rows = fragments.pageByFilter(
                tenantId,
                statusName,
                normalizedPackageVersion,
                normalizedKeyword,
                safePage.offset(),
                safePage.safeSize())
            .stream()
            .map(this::response)
            .toList();
        long total = fragments.countByFilter(tenantId, statusName, normalizedPackageVersion, normalizedKeyword);
        return PageResponse.of(rows, safePage, total);
    }

    /**
     * 返回片段变更影响分析。
     */
    @Transactional(readOnly = true)
    public ConditionFragmentImpactResponse impact(String fragmentId) {
        String tenantId = requireCurrentTenant();
        ConditionFragment fragment = findFragment(fragmentId, tenantId);
        FragmentKey key = new FragmentKey(fragment.fragmentCode(), fragment.versionNo(), fragment.packageVersion());
        List<ConditionFragmentAffectedAsset> affected = new ArrayList<>();
        appendRuleImpacts(tenantId, key, affected);
        appendPathwayImpacts(tenantId, key, affected);
        affected.sort(Comparator
            .comparingInt((ConditionFragmentAffectedAsset asset) -> assetTypeOrder(asset.assetType()))
            .thenComparing(ConditionFragmentAffectedAsset::assetCode));
        return new ConditionFragmentImpactResponse(
            fragment.fragmentId(),
            fragment.fragmentCode(),
            fragment.versionNo(),
            fragment.packageVersion(),
            affected,
            "sha256:" + Sha256ContentHash.sha256(writeJson(affected, "条件片段影响分析摘要为空"), "条件片段影响分析摘要为空"),
            RequestContext.currentTraceId());
    }

    /**
     * 运行时解析条件片段。
     */
    @Override
    @Transactional(readOnly = true)
    public JsonNode resolve(ConditionFragmentReference reference) {
        if (reference == null) {
            throw invalid("条件片段引用不能为空");
        }
        String tenantId = requireCurrentTenant();
        ConditionFragment fragment = optional(fragments.findByTenantIdAndFragmentCodeAndVersionNo(
                tenantId, reference.fragmentCode(), reference.version()))
            .orElseThrow(() -> invalid("条件片段引用不存在: " + reference.fragmentCode() + " v" + reference.version()));
        if (fragment.status() != ConditionFragmentStatus.ACTIVE) {
            throw invalid("条件片段未激活，不能用于运行时求值: " + reference.fragmentCode());
        }
        if (!Objects.equals(fragment.packageVersion(), reference.packageVersion())) {
            throw invalid("条件片段包版本不一致: " + reference.fragmentCode());
        }
        return readJson(fragment.bodyJson(), "条件片段正文 JSON 解析失败");
    }

    private void appendRuleImpacts(
            String tenantId,
            FragmentKey key,
            List<ConditionFragmentAffectedAsset> affected) {
        for (RuleDefinition rule : ruleDefinitions.listByFilter(tenantId, null, null, null)) {
            if (!hasText(rule.activeVersionId())) {
                continue;
            }
            Optional<RuleVersion> version = optional(ruleVersions.findByVersionIdAndTenantId(
                rule.activeVersionId(), tenantId));
            if (version.isEmpty()) {
                continue;
            }
            JsonNode dsl = readJson(version.get().dslJson(), "规则 DSL JSON 解析失败");
            if (containsReference(dsl, key)) {
                affected.add(new ConditionFragmentAffectedAsset(
                    "RULE",
                    rule.ruleId(),
                    rule.ruleCode(),
                    rule.name(),
                    "规则当前版本 when 引用条件片段"));
            }
        }
    }

    private void appendPathwayImpacts(
            String tenantId,
            FragmentKey key,
            List<ConditionFragmentAffectedAsset> affected) {
        for (PathwayTemplate template : pathwayTemplates.listByFilter(tenantId, null, null, null, null)) {
            boolean matched = false;
            for (PathwayEdge edge : pathwayEdges.findByTemplateIdAndTenantIdOrderByPriorityAsc(
                    template.templateId(), tenantId)) {
                if (!hasText(edge.conditionJson())) {
                    continue;
                }
                JsonNode condition = readJson(edge.conditionJson(), "路径边条件 JSON 解析失败");
                if (containsReference(condition, key)) {
                    matched = true;
                    break;
                }
            }
            if (matched) {
                affected.add(new ConditionFragmentAffectedAsset(
                    "PATHWAY",
                    template.templateId(),
                    template.templateCode(),
                    template.name(),
                    "路径守卫引用条件片段"));
            }
        }
    }

    private void validateBodyGraph(
            String tenantId,
            FragmentKey root,
            JsonNode body,
            LinkedHashSet<FragmentKey> stack) {
        if (body == null || !body.isObject()) {
            throw invalid("条件片段正文必须是 JSON 对象");
        }
        for (FragmentKey reference : references(body)) {
            if (!Objects.equals(reference.packageVersion(), root.packageVersion())) {
                throw invalid("条件片段包版本不一致: " + reference.fragmentCode());
            }
            if (reference.equals(root)) {
                throw invalid("条件片段循环引用: " + root);
            }
            if (!stack.add(reference)) {
                throw invalid("条件片段循环引用: " + stack + " -> " + reference);
            }
            ConditionFragment child = findReferencedFragment(tenantId, reference);
            validateBodyGraph(tenantId, root, readJson(child.bodyJson(), "条件片段正文 JSON 解析失败"), stack);
            stack.remove(reference);
        }
    }

    private ConditionFragment findReferencedFragment(String tenantId, FragmentKey reference) {
        Optional<ConditionFragment> exact = optional(fragments.findByTenantIdAndFragmentCodeAndVersionNo(
            tenantId, reference.fragmentCode(), reference.versionNo()));
        if (exact.isPresent()) {
            return exact.get();
        }
        Optional<ConditionFragment> latest = optional(
            fragments.findLatestByTenantIdAndFragmentCode(tenantId, reference.fragmentCode()));
        if (latest.isPresent() && Objects.equals(latest.get().versionNo(), reference.versionNo())) {
            return latest.get();
        }
        throw invalid("条件片段引用不存在: " + reference.fragmentCode() + " v" + reference.versionNo());
    }

    private boolean containsReference(JsonNode node, FragmentKey expected) {
        if (node == null || node.isNull() || node.isMissingNode()) {
            return false;
        }
        if (node.isObject()) {
            FragmentKey current = reference(node);
            if (expected.equals(current)) {
                return true;
            }
            var fields = node.fields();
            while (fields.hasNext()) {
                if (containsReference(fields.next().getValue(), expected)) {
                    return true;
                }
            }
            return false;
        }
        if (node.isArray()) {
            for (JsonNode item : node) {
                if (containsReference(item, expected)) {
                    return true;
                }
            }
        }
        return false;
    }

    private List<FragmentKey> references(JsonNode node) {
        List<FragmentKey> result = new ArrayList<>();
        collectReferences(node, result);
        return result;
    }

    private void collectReferences(JsonNode node, List<FragmentKey> result) {
        if (node == null || node.isNull() || node.isMissingNode()) {
            return;
        }
        if (node.isObject()) {
            FragmentKey reference = reference(node);
            if (reference != null) {
                result.add(reference);
            }
            var fields = node.fields();
            while (fields.hasNext()) {
                collectReferences(fields.next().getValue(), result);
            }
            return;
        }
        if (node.isArray()) {
            for (JsonNode item : node) {
                collectReferences(item, result);
            }
        }
    }

    private FragmentKey reference(JsonNode node) {
        String fragmentCode = text(node, "fragmentRef");
        if (!hasText(fragmentCode)) {
            return null;
        }
        JsonNode version = node.path("version");
        if (!version.isIntegralNumber() || version.asInt() <= 0) {
            throw invalid("条件片段引用 version 必须是正整数");
        }
        String packageVersion = text(node, "packageVersion");
        if (!hasText(packageVersion)) {
            throw invalid("条件片段引用 packageVersion 不能为空");
        }
        return new FragmentKey(fragmentCode, version.asInt(), packageVersion);
    }

    private ConditionFragment findFragment(String fragmentId, String tenantId) {
        if (!hasText(fragmentId)) {
            throw invalid("条件片段 ID 不能为空");
        }
        return optional(fragments.findByFragmentIdAndTenantId(fragmentId, tenantId))
            .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "条件片段不存在: " + fragmentId));
    }

    private FragmentInput input(ConditionFragmentUpsertRequest request) {
        if (request == null) {
            throw invalid("条件片段请求不能为空");
        }
        String fragmentCode = requireText(request.fragmentCode(), "条件片段编码不能为空");
        String name = requireText(request.name(), "条件片段名称不能为空");
        String packageVersion = requireText(request.packageVersion(), "条件片段包版本不能为空");
        Integer versionNo = request.versionNo();
        if (versionNo == null || versionNo <= 0) {
            throw invalid("条件片段版本号必须大于 0");
        }
        JsonNode body = request.bodyJson();
        if (body == null || !body.isObject()) {
            throw invalid("条件片段正文必须是 JSON 对象");
        }
        ConditionFragmentStatus status = request.status() == null ? ConditionFragmentStatus.DRAFT : request.status();
        return new FragmentInput(
            fragmentCode,
            name,
            normalize(request.category()),
            body,
            versionNo,
            packageVersion,
            status);
    }

    private void validateImmutableVersion(ConditionFragment existing, FragmentInput input) {
        if (!Objects.equals(existing.fragmentCode(), input.fragmentCode())
                || !Objects.equals(existing.versionNo(), input.versionNo())) {
            throw invalid("条件片段编码和版本号不可原地修改，请新建版本");
        }
        if (existing.status() == ConditionFragmentStatus.RETIRED) {
            throw invalid("已退役条件片段版本不可修改");
        }
        if (existing.status() != ConditionFragmentStatus.ACTIVE) {
            return;
        }
        boolean unchanged = Objects.equals(existing.name(), input.name())
            && Objects.equals(existing.category(), input.category())
            && Objects.equals(existing.packageVersion(), input.packageVersion())
            && readJson(existing.bodyJson(), "条件片段正文 JSON 解析失败").equals(input.bodyJson());
        if (input.status() != ConditionFragmentStatus.RETIRED || !unchanged) {
            throw invalid("已激活条件片段版本不可原地修改，请新建更高版本");
        }
    }

    private ConditionFragmentResponse response(ConditionFragment fragment) {
        return new ConditionFragmentResponse(
            fragment.fragmentId(),
            fragment.tenantId(),
            fragment.fragmentCode(),
            fragment.name(),
            fragment.category(),
            readJson(fragment.bodyJson(), "条件片段正文 JSON 解析失败"),
            fragment.versionNo(),
            fragment.status(),
            fragment.packageVersion(),
            fragment.createdAt(),
            fragment.createdBy(),
            fragment.updatedAt(),
            fragment.updatedBy(),
            fragment.traceId());
    }

    private JsonNode readJson(String raw, String message) {
        if (!hasText(raw)) {
            throw invalid(message);
        }
        try {
            return json.readTree(raw);
        } catch (JsonProcessingException exception) {
            throw new ApiException(ErrorCode.ENG_RULE_001, message, exception);
        }
    }

    private String writeJson(Object value, String message) {
        try {
            return json.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new ApiException(ErrorCode.ENG_RULE_001, message, exception);
        }
    }

    private String requireCurrentTenant() {
        String tenantId = RequestContext.currentOrgScope().tenantId();
        if (!hasText(tenantId)) {
            throw new ApiException(ErrorCode.TENANT_CONTEXT_MISSING, "条件片段操作缺少租户上下文");
        }
        return tenantId;
    }

    private String requireText(String value, String message) {
        if (!hasText(value)) {
            throw invalid(message);
        }
        return value.trim();
    }

    private String text(JsonNode node, String field) {
        if (node == null || node.isMissingNode() || !node.has(field) || node.path(field).isNull()) {
            return null;
        }
        String value = node.path(field).asText(null);
        return hasText(value) ? value.trim() : null;
    }

    private String normalize(String value) {
        return hasText(value) ? value.trim() : null;
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private ApiException invalid(String message) {
        return new ApiException(ErrorCode.ENG_RULE_001, message);
    }

    private int assetTypeOrder(String assetType) {
        return switch (assetType) {
            case "RULE" -> 10;
            case "PATHWAY" -> 20;
            default -> 100;
        };
    }

    private <T> Optional<T> optional(Optional<T> optional) {
        return optional == null ? Optional.empty() : optional;
    }

    private record FragmentInput(
        String fragmentCode,
        String name,
        String category,
        JsonNode bodyJson,
        Integer versionNo,
        String packageVersion,
        ConditionFragmentStatus status
    ) {
        FragmentKey rootKey() {
            return new FragmentKey(fragmentCode, versionNo, packageVersion);
        }
    }

    private record FragmentKey(
        String fragmentCode,
        Integer versionNo,
        String packageVersion
    ) {}
}
