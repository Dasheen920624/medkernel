package com.medkernel.engine.authoring;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.medkernel.engine.pathway.PathwayTemplate;
import com.medkernel.engine.pathway.PathwayTemplateRepository;
import com.medkernel.engine.followup.FollowupTemplate;
import com.medkernel.engine.followup.FollowupTemplateRepository;
import com.medkernel.engine.rule.RuleDefinition;
import com.medkernel.engine.rule.RuleDefinitionRepository;
import com.medkernel.engine.versioning.AssetVersionRepository;
import com.medkernel.engine.versioning.VersionedAssetType;
import com.medkernel.shared.api.PageRequest;
import com.medkernel.shared.api.PageResponse;
import com.medkernel.shared.api.error.ApiException;
import com.medkernel.shared.api.error.ErrorCode;
import com.medkernel.shared.context.RequestContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 统一创作资产库应用服务。
 */
@Service
public class AuthoringAssetLibraryService {

    private static final int SOURCE_SCAN_LIMIT = 200;
    private static final TypeReference<List<String>> TAG_LIST = new TypeReference<>() {};

    private final ObjectMapper json;
    private final RuleDefinitionRepository rules;
    private final PathwayTemplateRepository pathways;
    private final ConditionFragmentRepository fragments;
    private final FollowupTemplateRepository followupTemplates;
    private final AssetVersionRepository assetVersions;
    private final AuthoringAssetProfileRepository profiles;
    private final AuthoringAssetFavoriteRepository favorites;

    public AuthoringAssetLibraryService(
            ObjectMapper json,
            RuleDefinitionRepository rules,
            PathwayTemplateRepository pathways,
            ConditionFragmentRepository fragments,
            FollowupTemplateRepository followupTemplates,
            AssetVersionRepository assetVersions,
            AuthoringAssetProfileRepository profiles,
            AuthoringAssetFavoriteRepository favorites) {
        this.json = json;
        this.rules = rules;
        this.pathways = pathways;
        this.fragments = fragments;
        this.followupTemplates = followupTemplates;
        this.assetVersions = assetVersions;
        this.profiles = profiles;
        this.favorites = favorites;
    }

    /**
     * 合并规则、路径、条件片段到统一资产库。
     */
    @Transactional(readOnly = true)
    public PageResponse<AuthoringAssetLibraryItem> list(AuthoringAssetLibraryQuery query) {
        String tenantId = currentTenant();
        String userId = RequestContext.currentUserId().orElse("system");
        PageRequest page = query == null || query.page() == null ? PageRequest.defaults() : query.page();
        VersionedAssetType requestedType = query == null ? null : query.assetType();
        Set<VersionedAssetType> allowedTypes = query == null ? null : query.allowedAssetTypes();
        String keyword = normalize(query == null ? null : query.keyword());
        String tag = normalize(query == null ? null : query.tag());
        boolean favoriteOnly = query != null && query.favoriteOnly();

        if (tag == null && !favoriteOnly) {
            return listRepositoryPage(tenantId, userId, requestedType, allowedTypes, keyword, page);
        }
        return listWithProfileFilters(tenantId, userId, requestedType, allowedTypes, keyword, tag, favoriteOnly, page);
    }

    private PageResponse<AuthoringAssetLibraryItem> listRepositoryPage(
            String tenantId,
            String userId,
            VersionedAssetType requestedType,
            Set<VersionedAssetType> allowedTypes,
            String keyword,
            PageRequest page) {
        if (requestedType != null) {
            RepositoryAssetPage source =
                loadRepositoryPage(tenantId, userId, requestedType, keyword, page.offset(), page.safeSize());
            return PageResponse.of(source.items(), page, source.total());
        }

        int sourceLimit = page.offset() + page.safeSize();
        List<AuthoringAssetLibraryItem> rows = new ArrayList<>();
        long total = 0L;
        for (VersionedAssetType type : repositoryBackedTypes()) {
            if (!shouldInclude(null, allowedTypes, type)) {
                continue;
            }
            RepositoryAssetPage source = loadRepositoryPage(tenantId, userId, type, keyword, 0, sourceLimit);
            rows.addAll(source.items());
            total += source.total();
        }
        List<AuthoringAssetLibraryItem> items = sorted(rows).stream()
            .skip(page.offset())
            .limit(page.safeSize())
            .toList();
        return PageResponse.of(items, page, total);
    }

    private PageResponse<AuthoringAssetLibraryItem> listWithProfileFilters(
            String tenantId,
            String userId,
            VersionedAssetType requestedType,
            Set<VersionedAssetType> allowedTypes,
            String keyword,
            String tag,
            boolean favoriteOnly,
            PageRequest page) {
        List<AuthoringAssetLibraryItem> rows = new ArrayList<>();
        if (shouldInclude(requestedType, allowedTypes, VersionedAssetType.RULE)) {
            rules.listByFilter(tenantId, null, null, null, null).forEach(rule ->
                rows.add(ruleItem(tenantId, userId, rule)));
        }
        if (shouldInclude(requestedType, allowedTypes, VersionedAssetType.PATHWAY)) {
            pathways.listByFilter(tenantId, null, null, null, null, null).forEach(pathway ->
                rows.add(pathwayItem(tenantId, userId, pathway)));
        }
        if (shouldInclude(requestedType, allowedTypes, VersionedAssetType.CONDITION_FRAGMENT)) {
            fragments.pageByFilter(tenantId, null, null, null, 0, SOURCE_SCAN_LIMIT).forEach(fragment ->
                rows.add(fragmentItem(tenantId, userId, fragment)));
        }
        if (shouldInclude(requestedType, allowedTypes, VersionedAssetType.FOLLOWUP)) {
            followupTemplates.pageByFilter(tenantId, null, null, 0, SOURCE_SCAN_LIMIT).forEach(template ->
                rows.add(followupItem(tenantId, userId, template)));
        }

        List<AuthoringAssetLibraryItem> filtered = rows.stream()
            .filter(item -> matchesKeyword(item, keyword))
            .filter(item -> tag == null || item.tags().stream().anyMatch(value -> value.equalsIgnoreCase(tag)))
            .filter(item -> !favoriteOnly || item.favorite())
            .sorted(assetOrdering())
            .toList();

        List<AuthoringAssetLibraryItem> items = filtered.stream()
            .skip(page.offset())
            .limit(page.safeSize())
            .toList();
        return PageResponse.of(items, page, filtered.size());
    }

    private List<VersionedAssetType> repositoryBackedTypes() {
        return List.of(
            VersionedAssetType.RULE,
            VersionedAssetType.PATHWAY,
            VersionedAssetType.CONDITION_FRAGMENT,
            VersionedAssetType.FOLLOWUP
        );
    }

    private RepositoryAssetPage loadRepositoryPage(
            String tenantId,
            String userId,
            VersionedAssetType type,
            String keyword,
            int offset,
            int limit) {
        String likeKeyword = likeKeyword(keyword);
        return switch (type) {
            case RULE -> new RepositoryAssetPage(
                rules.pageByFilter(tenantId, null, null, null, likeKeyword, offset, limit).stream()
                    .map(rule -> ruleItem(tenantId, userId, rule))
                    .toList(),
                rules.countByFilter(tenantId, null, null, null, likeKeyword)
            );
            case PATHWAY -> new RepositoryAssetPage(
                pathways.pageByFilter(tenantId, null, null, null, null, likeKeyword, offset, limit).stream()
                    .map(pathway -> pathwayItem(tenantId, userId, pathway))
                    .toList(),
                pathways.countByFilter(tenantId, null, null, null, null, likeKeyword)
            );
            case CONDITION_FRAGMENT -> new RepositoryAssetPage(
                fragments.pageByFilter(tenantId, null, null, keyword, offset, limit).stream()
                    .map(fragment -> fragmentItem(tenantId, userId, fragment))
                    .toList(),
                fragments.countByFilter(tenantId, null, null, keyword)
            );
            case FOLLOWUP -> new RepositoryAssetPage(
                followupTemplates.pageByFilter(tenantId, likeKeyword, null, offset, limit).stream()
                    .map(template -> followupItem(tenantId, userId, template))
                    .toList(),
                followupTemplates.countByFilter(tenantId, likeKeyword, null)
            );
            default -> new RepositoryAssetPage(List.of(), 0L);
        };
    }

    private List<AuthoringAssetLibraryItem> sorted(List<AuthoringAssetLibraryItem> rows) {
        return rows.stream()
            .sorted(assetOrdering())
            .toList();
    }

    private Comparator<AuthoringAssetLibraryItem> assetOrdering() {
        return Comparator
            .comparing(AuthoringAssetLibraryItem::updatedAt, Comparator.nullsLast(Comparator.reverseOrder()))
            .thenComparing(item -> item.assetType().name())
            .thenComparing(AuthoringAssetLibraryItem::assetCode);
    }

    private String likeKeyword(String keyword) {
        return keyword == null ? null : "%" + keyword.toLowerCase(Locale.ROOT) + "%";
    }

    private record RepositoryAssetPage(List<AuthoringAssetLibraryItem> items, long total) {
    }

    private boolean shouldInclude(
            VersionedAssetType requestedType,
            Set<VersionedAssetType> allowedTypes,
            VersionedAssetType candidateType) {
        boolean requested = requestedType == null || requestedType == candidateType;
        boolean allowed = allowedTypes == null || allowedTypes.contains(candidateType);
        return requested && allowed;
    }

    /**
     * 将资产另存为可独立编辑的新草稿。
     */
    @Transactional
    public AuthoringAssetCloneResponse cloneAsset(
            VersionedAssetType assetType,
            String assetId,
            AuthoringAssetCloneRequest request) {
        if (assetType != VersionedAssetType.CONDITION_FRAGMENT) {
            throw new ApiException(ErrorCode.ENG_PACKAGE_002, "当前资产类型尚未接入可克隆草稿: " + assetType);
        }
        String tenantId = currentTenant();
        ConditionFragment source = fragments.findByFragmentIdAndTenantId(required(assetId, "源资产 ID"), tenantId)
            .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "条件片段不存在: " + assetId));
        String newCode = required(request.newCode(), "新资产编码");
        String newName = required(request.newName(), "新资产名称");
        Integer newVersion = request.newVersion();
        if (newVersion == null || newVersion <= 0) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, "新资产版本号必须大于 0");
        }
        String packageVersion = required(request.packageVersion(), "新资产包版本");
        fragments.findByTenantIdAndFragmentCodeAndVersionNo(tenantId, newCode, newVersion)
            .ifPresent(existing -> {
                throw new ApiException(ErrorCode.CONFLICT, "条件片段编码和版本已存在: " + newCode + " v" + newVersion);
            });
        Instant now = Instant.now();
        String actor = RequestContext.currentUserId().orElse("system");
        ConditionFragment saved = fragments.save(new ConditionFragment(
            null,
            "cf-" + UUID.randomUUID(),
            tenantId,
            newCode,
            newName,
            source.category(),
            source.bodyJson(),
            newVersion,
            ConditionFragmentStatus.DRAFT,
            packageVersion,
            now,
            actor,
            now,
            actor,
            RequestContext.currentTraceId()));
        return new AuthoringAssetCloneResponse(
            assetType,
            source.fragmentId(),
            assetType,
            saved.fragmentId(),
            saved.fragmentCode(),
            saved.status().name());
    }

    /**
     * 更新资产库分类与标签。
     */
    @Transactional
    public AuthoringAssetProfileResponse updateProfile(
            VersionedAssetType assetType,
            String assetId,
            AuthoringAssetProfileRequest request) {
        String tenantId = currentTenant();
        VersionedAssetType type = requiredType(assetType);
        String normalizedAssetId = required(assetId, "资产 ID");
        ensureAssetExists(tenantId, type, normalizedAssetId);
        List<String> tags = normalizeTags(request == null ? null : request.tags());
        String tagsJson = writeTags(tags);
        Instant now = Instant.now();
        String actor = RequestContext.currentUserId().orElse("system");
        AuthoringAssetProfile existing = profile(tenantId, type, normalizedAssetId).orElse(null);
        profiles.save(new AuthoringAssetProfile(
            existing == null ? null : existing.id(),
            tenantId,
            type,
            normalizedAssetId,
            normalize(request == null ? null : request.category()),
            tagsJson,
            existing == null ? now : existing.createdAt(),
            existing == null ? actor : existing.createdBy(),
            now,
            actor,
            RequestContext.currentTraceId()
        ));
        return new AuthoringAssetProfileResponse(
            type,
            normalizedAssetId,
            normalize(request == null ? null : request.category()),
            tags,
            RequestContext.currentTraceId()
        );
    }

    /**
     * 收藏统一资产。
     */
    @Transactional
    public AuthoringAssetFavoriteResponse favorite(VersionedAssetType assetType, String assetId) {
        String tenantId = currentTenant();
        String userId = RequestContext.currentUserId().orElse("system");
        VersionedAssetType type = requiredType(assetType);
        String normalizedAssetId = required(assetId, "资产 ID");
        ensureAssetExists(tenantId, type, normalizedAssetId);
        favorites.findByTenantIdAndUserIdAndAssetTypeAndAssetId(tenantId, userId, type, normalizedAssetId)
            .orElseGet(() -> favorites.save(new AuthoringAssetFavorite(
                null,
                tenantId,
                userId,
                type,
                normalizedAssetId,
                Instant.now(),
                RequestContext.currentTraceId()
            )));
        return new AuthoringAssetFavoriteResponse(type, normalizedAssetId, true, RequestContext.currentTraceId());
    }

    /**
     * 取消收藏统一资产。
     */
    @Transactional
    public AuthoringAssetFavoriteResponse unfavorite(VersionedAssetType assetType, String assetId) {
        String tenantId = currentTenant();
        String userId = RequestContext.currentUserId().orElse("system");
        VersionedAssetType type = requiredType(assetType);
        String normalizedAssetId = required(assetId, "资产 ID");
        favorites.findByTenantIdAndUserIdAndAssetTypeAndAssetId(tenantId, userId, type, normalizedAssetId)
            .ifPresent(favorites::delete);
        return new AuthoringAssetFavoriteResponse(type, normalizedAssetId, false, RequestContext.currentTraceId());
    }

    private AuthoringAssetLibraryItem ruleItem(String tenantId, String userId, RuleDefinition rule) {
        AuthoringAssetProfile profile = profile(tenantId, VersionedAssetType.RULE, rule.ruleId()).orElse(null);
        return new AuthoringAssetLibraryItem(
            VersionedAssetType.RULE,
            rule.ruleId(),
            rule.ruleCode(),
            rule.name(),
            category(profile, rule.ruleType().name()),
            tags(profile),
            rule.activeVersionId(),
            rule.status().name(),
            rule.packageVersion(),
            favorite(tenantId, userId, VersionedAssetType.RULE, rule.ruleId()),
            false,
            rule.updatedAt());
    }

    private AuthoringAssetLibraryItem pathwayItem(String tenantId, String userId, PathwayTemplate pathway) {
        AuthoringAssetProfile profile = profile(tenantId, VersionedAssetType.PATHWAY, pathway.templateId()).orElse(null);
        return new AuthoringAssetLibraryItem(
            VersionedAssetType.PATHWAY,
            pathway.templateId(),
            pathway.templateCode(),
            pathway.name(),
            category(profile, pathway.diseaseCode()),
            tags(profile),
            String.valueOf(pathway.templateVersion()),
            pathway.status().name(),
            pathway.packageId(),
            favorite(tenantId, userId, VersionedAssetType.PATHWAY, pathway.templateId()),
            false,
            pathway.updatedAt());
    }

    private AuthoringAssetLibraryItem fragmentItem(String tenantId, String userId, ConditionFragment fragment) {
        AuthoringAssetProfile profile = profile(
            tenantId, VersionedAssetType.CONDITION_FRAGMENT, fragment.fragmentId()).orElse(null);
        return new AuthoringAssetLibraryItem(
            VersionedAssetType.CONDITION_FRAGMENT,
            fragment.fragmentId(),
            fragment.fragmentCode(),
            fragment.name(),
            category(profile, fragment.category()),
            tags(profile),
            String.valueOf(fragment.versionNo()),
            fragment.status().name(),
            fragment.packageVersion(),
            favorite(tenantId, userId, VersionedAssetType.CONDITION_FRAGMENT, fragment.fragmentId()),
            true,
            fragment.updatedAt());
    }

    private AuthoringAssetLibraryItem followupItem(
            String tenantId,
            String userId,
            FollowupTemplate template) {
        AuthoringAssetProfile profile = profile(
            tenantId, VersionedAssetType.FOLLOWUP, template.templateId()).orElse(null);
        String status = assetVersions.findByVersionIdAndTenantId(template.assetVersionId(), tenantId)
            .map(version -> version.status().name())
            .orElse("MISSING_VERSION");
        return new AuthoringAssetLibraryItem(
            VersionedAssetType.FOLLOWUP,
            template.templateId(),
            template.templateCode(),
            template.name(),
            category(profile, "随访模板"),
            tags(profile),
            String.valueOf(template.versionNo()),
            status,
            null,
            favorite(tenantId, userId, VersionedAssetType.FOLLOWUP, template.templateId()),
            false,
            template.updatedAt()
        );
    }

    private Optional<AuthoringAssetProfile> profile(
            String tenantId,
            VersionedAssetType type,
            String assetId) {
        return profiles.findByTenantIdAndAssetTypeAndAssetId(tenantId, type, assetId);
    }

    private boolean favorite(String tenantId, String userId, VersionedAssetType type, String assetId) {
        return favorites.existsByTenantIdAndUserIdAndAssetTypeAndAssetId(tenantId, userId, type, assetId);
    }

    private String category(AuthoringAssetProfile profile, String fallback) {
        String category = profile == null ? null : normalize(profile.category());
        return category == null ? fallback : category;
    }

    private List<String> tags(AuthoringAssetProfile profile) {
        if (profile == null || normalize(profile.tagsJson()) == null) {
            return List.of();
        }
        try {
            return json.readValue(profile.tagsJson(), TAG_LIST).stream()
                .map(this::normalize)
                .filter(value -> value != null)
                .distinct()
                .toList();
        } catch (JsonProcessingException exception) {
            throw new ApiException(ErrorCode.ENG_PACKAGE_002, "资产标签 JSON 解析失败", exception);
        }
    }

    private boolean matchesKeyword(AuthoringAssetLibraryItem item, String keyword) {
        if (keyword == null) {
            return true;
        }
        String needle = keyword.toLowerCase(Locale.ROOT);
        return contains(item.assetCode(), needle)
            || contains(item.name(), needle)
            || contains(item.category(), needle)
            || item.tags().stream().anyMatch(tag -> contains(tag, needle));
    }

    private boolean contains(String value, String needle) {
        return value != null && value.toLowerCase(Locale.ROOT).contains(needle);
    }

    private String currentTenant() {
        String tenantId = RequestContext.currentOrgScope().tenantId();
        if (tenantId == null || tenantId.isBlank()) {
            throw new ApiException(ErrorCode.TENANT_CONTEXT_MISSING, "统一资产库缺少租户上下文");
        }
        return tenantId;
    }

    private String normalize(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private String required(String value, String label) {
        String normalized = normalize(value);
        if (normalized == null) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, label + "不能为空");
        }
        return normalized;
    }

    private VersionedAssetType requiredType(VersionedAssetType assetType) {
        if (assetType == null) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, "资产类型不能为空");
        }
        return assetType;
    }

    private void ensureAssetExists(String tenantId, VersionedAssetType assetType, String assetId) {
        switch (assetType) {
            case RULE -> rules.findByRuleIdAndTenantId(assetId, tenantId)
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "规则资产不存在: " + assetId));
            case PATHWAY -> pathways.findByTemplateIdAndTenantId(assetId, tenantId)
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "路径资产不存在: " + assetId));
            case CONDITION_FRAGMENT -> fragments.findByFragmentIdAndTenantId(assetId, tenantId)
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "条件片段资产不存在: " + assetId));
            case FOLLOWUP -> followupTemplates.findByTemplateIdAndTenantId(assetId, tenantId)
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "随访模板资产不存在: " + assetId));
            default -> throw new ApiException(ErrorCode.ENG_PACKAGE_002, "当前资产类型尚未接入资产库: " + assetType);
        }
    }

    private List<String> normalizeTags(List<String> tags) {
        if (tags == null || tags.isEmpty()) {
            return List.of();
        }
        Set<String> normalized = new LinkedHashSet<>();
        tags.stream()
            .map(this::normalize)
            .filter(value -> value != null)
            .forEach(normalized::add);
        return List.copyOf(normalized);
    }

    private String writeTags(List<String> tags) {
        if (tags == null || tags.isEmpty()) {
            return null;
        }
        try {
            return json.writeValueAsString(tags);
        } catch (JsonProcessingException exception) {
            throw new ApiException(ErrorCode.ENG_PACKAGE_002, "资产标签 JSON 写入失败", exception);
        }
    }
}
