package com.medkernel.engine.authoring;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.medkernel.engine.followup.FollowupTemplate;
import com.medkernel.engine.pathway.PathwayEntryMode;
import com.medkernel.engine.pathway.PathwayTemplate;
import com.medkernel.engine.pathway.PathwayTemplateLevel;
import com.medkernel.engine.pathway.PathwayTemplateRepository;
import com.medkernel.engine.pathway.PathwayTemplateStatus;
import com.medkernel.engine.followup.FollowupTemplateRepository;
import com.medkernel.engine.rule.RuleAuthoringMode;
import com.medkernel.engine.rule.RuleDefinition;
import com.medkernel.engine.rule.RuleDefinitionRepository;
import com.medkernel.engine.rule.RuleDefinitionStatus;
import com.medkernel.engine.rule.RuleRiskLevel;
import com.medkernel.engine.rule.RuleType;
import com.medkernel.engine.versioning.VersionedAssetType;
import com.medkernel.engine.versioning.AssetVersionRepository;
import com.medkernel.shared.api.PageRequest;
import com.medkernel.shared.api.PageResponse;
import com.medkernel.shared.context.OrgScope;
import com.medkernel.shared.context.RequestContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class AuthoringAssetLibraryServiceTest {

    private final RuleDefinitionRepository rules = mock(RuleDefinitionRepository.class);
    private final PathwayTemplateRepository pathways = mock(PathwayTemplateRepository.class);
    private final ConditionFragmentRepository fragments = mock(ConditionFragmentRepository.class);
    private final FollowupTemplateRepository followupTemplates = mock(FollowupTemplateRepository.class);
    private final AssetVersionRepository assetVersions = mock(AssetVersionRepository.class);
    private final AuthoringAssetProfileRepository profiles = mock(AuthoringAssetProfileRepository.class);
    private final AuthoringAssetFavoriteRepository favorites = mock(AuthoringAssetFavoriteRepository.class);
    private final AuthoringAssetLibraryService service = new AuthoringAssetLibraryService(
        new ObjectMapper(), rules, pathways, fragments, followupTemplates, assetVersions, profiles, favorites);

    @BeforeEach
    void setUp() {
        RequestContext.restore(new RequestContext.Snapshot(
            "trace-assets", OrgScope.tenant("tenant-A"), "author-1"));
    }

    @AfterEach
    void clearContext() {
        RequestContext.clear();
    }

    @Test
    void listsRulesPathwaysAndFragmentsWithTagsAndFavorites() {
        when(rules.countForAuthoringLibrary("tenant-A", "%ckd%", "%\"复用\"%", "author-1"))
            .thenReturn(0L);
        when(rules.pageForAuthoringLibrary("tenant-A", "%ckd%", "%\"复用\"%", "author-1", 0, 20))
            .thenReturn(List.of());
        when(pathways.countForAuthoringLibrary("tenant-A", "%ckd%", "%\"复用\"%", "author-1"))
            .thenReturn(0L);
        when(pathways.pageForAuthoringLibrary("tenant-A", "%ckd%", "%\"复用\"%", "author-1", 0, 20))
            .thenReturn(List.of());
        when(fragments.countForAuthoringLibrary("tenant-A", "%ckd%", "%\"复用\"%", "author-1"))
            .thenReturn(1L);
        when(fragments.pageForAuthoringLibrary("tenant-A", "%ckd%", "%\"复用\"%", "author-1", 0, 20))
            .thenReturn(List.of(fragment("frag-1", "FRAG_CKD", "CKD 条件片段")));
        when(followupTemplates.countForAuthoringLibrary("tenant-A", "%ckd%", "%\"复用\"%", "author-1"))
            .thenReturn(0L);
        when(followupTemplates.pageForAuthoringLibrary("tenant-A", "%ckd%", "%\"复用\"%", "author-1", 0, 20))
            .thenReturn(List.of());
        when(profiles.findByTenantIdAndAssetTypeAndAssetId(
            "tenant-A", VersionedAssetType.CONDITION_FRAGMENT, "frag-1"))
            .thenReturn(Optional.of(profile(VersionedAssetType.CONDITION_FRAGMENT, "frag-1", "[\"CKD\",\"复用\"]")));
        when(favorites.existsByTenantIdAndUserIdAndAssetTypeAndAssetId(
            "tenant-A", "author-1", VersionedAssetType.CONDITION_FRAGMENT, "frag-1"))
            .thenReturn(true);

        PageResponse<AuthoringAssetLibraryItem> response = service.list(
            new AuthoringAssetLibraryQuery(null, "CKD", "复用", true, new PageRequest(0, 20, null)));

        assertThat(response.items()).extracting(AuthoringAssetLibraryItem::assetType)
            .containsExactly(VersionedAssetType.CONDITION_FRAGMENT);
        AuthoringAssetLibraryItem item = response.items().getFirst();
        assertThat(item.assetCode()).isEqualTo("FRAG_CKD");
        assertThat(item.tags()).containsExactly("CKD", "复用");
        assertThat(item.favorite()).isTrue();
        assertThat(item.cloneable()).isTrue();
        verify(rules, never()).listByFilter("tenant-A", null, null, null, null);
        verify(pathways, never()).listByFilter("tenant-A", null, null, null, null, null);
    }

    @Test
    void exposesCloneEntryOnlyForImplementedDraftCloneAssets() {
        when(rules.countByFilter("tenant-A", null, null, null, null)).thenReturn(1L);
        when(rules.pageByFilter("tenant-A", null, null, null, null, 0, 20))
            .thenReturn(List.of(rule("rule-1", "RULE.CKD", "CKD 阻断规则")));
        when(pathways.countByFilter("tenant-A", null, null, null, null, null)).thenReturn(1L);
        when(pathways.pageByFilter("tenant-A", null, null, null, null, null, 0, 20))
            .thenReturn(List.of(pathway("pathway-1", "PATH.CKD", "CKD 临床路径")));
        when(fragments.countByFilter("tenant-A", null, null, null)).thenReturn(1L);
        when(fragments.pageByFilter("tenant-A", null, null, null, 0, 20))
            .thenReturn(List.of(fragment("frag-1", "FRAG_CKD", "CKD 条件片段")));

        PageResponse<AuthoringAssetLibraryItem> response = service.list(
            new AuthoringAssetLibraryQuery(null, null, null, false, new PageRequest(0, 20, null)));

        assertThat(response.items())
            .extracting(AuthoringAssetLibraryItem::assetType, AuthoringAssetLibraryItem::cloneable)
            .containsExactlyInAnyOrder(
                org.assertj.core.groups.Tuple.tuple(VersionedAssetType.RULE, false),
                org.assertj.core.groups.Tuple.tuple(VersionedAssetType.PATHWAY, false),
                org.assertj.core.groups.Tuple.tuple(VersionedAssetType.CONDITION_FRAGMENT, true));
    }

    @Test
    void allAssetsQueryOnlyReadsAllowedAssetTypes() {
        when(rules.countByFilter("tenant-A", null, null, null, null)).thenReturn(1L);
        when(rules.pageByFilter("tenant-A", null, null, null, null, 0, 20))
            .thenReturn(List.of(rule("rule-1", "RULE.CKD", "CKD 阻断规则")));
        when(fragments.countByFilter("tenant-A", null, null, null)).thenReturn(1L);
        when(fragments.pageByFilter("tenant-A", null, null, null, 0, 20))
            .thenReturn(List.of(fragment("frag-1", "FRAG_CKD", "CKD 条件片段")));

        PageResponse<AuthoringAssetLibraryItem> response = service.list(
            new AuthoringAssetLibraryQuery(
                null,
                null,
                null,
                false,
                new PageRequest(0, 20, null),
                Set.of(VersionedAssetType.RULE, VersionedAssetType.CONDITION_FRAGMENT)
            ));

        assertThat(response.items())
            .extracting(AuthoringAssetLibraryItem::assetType)
            .containsExactlyInAnyOrder(VersionedAssetType.RULE, VersionedAssetType.CONDITION_FRAGMENT);
    }

    @Test
    void listsTypedFollowupAssetsThroughRepositoryPagination() {
        when(followupTemplates.countByFilter("tenant-A", "%copd%", null)).thenReturn(1L);
        when(followupTemplates.pageByFilter("tenant-A", "%copd%", null, 20, 20))
            .thenReturn(List.of(followupTemplate("ftpl-1", "av-followup-1")));

        PageResponse<AuthoringAssetLibraryItem> response = service.list(
            new AuthoringAssetLibraryQuery(
                VersionedAssetType.FOLLOWUP,
                " COPD ",
                null,
                false,
                new PageRequest(2, 20, null)
            ));

        assertThat(response.page()).isEqualTo(2);
        assertThat(response.size()).isEqualTo(20);
        assertThat(response.total()).isEqualTo(1);
        assertThat(response.items()).extracting(AuthoringAssetLibraryItem::assetId)
            .containsExactly("ftpl-1");
        verify(followupTemplates, never()).findByTenantIdOrderByUpdatedAtDesc("tenant-A");
    }

    @Test
    void clonesConditionFragmentAsIndependentDraft() {
        ConditionFragment source = fragment("frag-1", "FRAG_CKD", "CKD 条件片段");
        when(fragments.findByFragmentIdAndTenantId("frag-1", "tenant-A"))
            .thenReturn(Optional.of(source));
        when(fragments.findByTenantIdAndFragmentCodeAndVersionNo("tenant-A", "FRAG_CKD_COPY", 1))
            .thenReturn(Optional.empty());
        when(fragments.save(any())).thenAnswer(inv -> {
            ConditionFragment draft = inv.getArgument(0);
            return new ConditionFragment(
                2L, draft.fragmentId(), draft.tenantId(), draft.fragmentCode(), draft.name(),
                draft.category(), draft.bodyJson(), draft.versionNo(), draft.status(), draft.packageVersion(),
                draft.createdAt(), draft.createdBy(), draft.updatedAt(), draft.updatedBy(), draft.traceId());
        });

        AuthoringAssetCloneResponse response = service.cloneAsset(
            VersionedAssetType.CONDITION_FRAGMENT,
            "frag-1",
            new AuthoringAssetCloneRequest("FRAG_CKD_COPY", "CKD 条件片段副本", 1, "pkg-2026.06"));

        assertThat(response.sourceAssetId()).isEqualTo("frag-1");
        assertThat(response.clonedAssetType()).isEqualTo(VersionedAssetType.CONDITION_FRAGMENT);
        assertThat(response.clonedAssetCode()).isEqualTo("FRAG_CKD_COPY");
        assertThat(response.status()).isEqualTo("DRAFT");
        ArgumentCaptor<ConditionFragment> captor = ArgumentCaptor.forClass(ConditionFragment.class);
        verify(fragments).save(captor.capture());
        ConditionFragment saved = captor.getValue();
        assertThat(saved.fragmentId()).isNotEqualTo(source.fragmentId());
        assertThat(saved.bodyJson()).isEqualTo(source.bodyJson());
        assertThat(saved.status()).isEqualTo(ConditionFragmentStatus.DRAFT);
    }

    @Test
    void updatesProfileWithNormalizedDistinctTags() {
        when(fragments.findByFragmentIdAndTenantId("frag-1", "tenant-A"))
            .thenReturn(Optional.of(fragment("frag-1", "FRAG_CKD", "CKD 条件片段")));
        when(profiles.findByTenantIdAndAssetTypeAndAssetId(
            "tenant-A", VersionedAssetType.CONDITION_FRAGMENT, "frag-1"))
            .thenReturn(Optional.empty());

        AuthoringAssetProfileResponse response = service.updateProfile(
            VersionedAssetType.CONDITION_FRAGMENT,
            "frag-1",
            new AuthoringAssetProfileRequest(" 慢病 ", List.of("复用", "CKD", "复用", " ")));

        assertThat(response.category()).isEqualTo("慢病");
        assertThat(response.tags()).containsExactly("复用", "CKD");
        ArgumentCaptor<AuthoringAssetProfile> captor = ArgumentCaptor.forClass(AuthoringAssetProfile.class);
        verify(profiles).save(captor.capture());
        assertThat(captor.getValue().category()).isEqualTo("慢病");
        assertThat(captor.getValue().tagsJson()).isEqualTo("[\"复用\",\"CKD\"]");
        assertThat(captor.getValue().createdBy()).isEqualTo("author-1");
    }

    @Test
    void favoritesConditionFragmentIdempotently() {
        when(fragments.findByFragmentIdAndTenantId("frag-1", "tenant-A"))
            .thenReturn(Optional.of(fragment("frag-1", "FRAG_CKD", "CKD 条件片段")));
        when(favorites.findByTenantIdAndUserIdAndAssetTypeAndAssetId(
            "tenant-A", "author-1", VersionedAssetType.CONDITION_FRAGMENT, "frag-1"))
            .thenReturn(Optional.empty());

        AuthoringAssetFavoriteResponse response = service.favorite(
            VersionedAssetType.CONDITION_FRAGMENT,
            "frag-1");

        assertThat(response.favorite()).isTrue();
        ArgumentCaptor<AuthoringAssetFavorite> captor = ArgumentCaptor.forClass(AuthoringAssetFavorite.class);
        verify(favorites).save(captor.capture());
        assertThat(captor.getValue().assetType()).isEqualTo(VersionedAssetType.CONDITION_FRAGMENT);
        assertThat(captor.getValue().assetId()).isEqualTo("frag-1");
        assertThat(captor.getValue().userId()).isEqualTo("author-1");
    }

    @Test
    void unfavoriteDeletesExistingFavorite() {
        AuthoringAssetFavorite existing = new AuthoringAssetFavorite(
            1L,
            "tenant-A",
            "author-1",
            VersionedAssetType.CONDITION_FRAGMENT,
            "frag-1",
            Instant.parse("2026-06-08T00:00:00Z"),
            "trace-assets");
        when(favorites.findByTenantIdAndUserIdAndAssetTypeAndAssetId(
            "tenant-A", "author-1", VersionedAssetType.CONDITION_FRAGMENT, "frag-1"))
            .thenReturn(Optional.of(existing));

        AuthoringAssetFavoriteResponse response = service.unfavorite(
            VersionedAssetType.CONDITION_FRAGMENT,
            "frag-1");

        assertThat(response.favorite()).isFalse();
        verify(favorites).delete(existing);
    }

    private RuleDefinition rule(String id, String code, String name) {
        Instant now = Instant.parse("2026-06-08T00:00:00Z");
        return new RuleDefinition(
            1L, id, "tenant-A", code, name, RuleType.ORDER,
            RuleAuthoringMode.DSL, RuleRiskLevel.HIGH, 100, null, 3600,
            RuleDefinitionStatus.PUBLISHED, "rv-1", "pkg-2026.06", "dept-1",
            now, "tester", now, "tester", "trace-rule");
    }

    private PathwayTemplate pathway(String id, String code, String name) {
        Instant now = Instant.parse("2026-06-08T00:00:00Z");
        return new PathwayTemplate(
            1L, id, "tenant-A", "pkg-1", code, name, "CKD", 1,
            PathwayTemplateLevel.STANDARD, PathwayTemplateStatus.PUBLISHED,
            PathwayEntryMode.AUTO_SUGGEST, "N1", "指南", "说明", "{}", "{}",
            now, "tester", now, "tester", "trace-pathway");
    }

    private ConditionFragment fragment(String id, String code, String name) {
        Instant now = Instant.parse("2026-06-08T00:00:00Z");
        return new ConditionFragment(
            1L, id, "tenant-A", code, name, "肾病",
            "{\"all\":[{\"fact\":\"patient.age\",\"operator\":\"gte\",\"value\":65}]}",
            1, ConditionFragmentStatus.ACTIVE, "pkg-2026.06",
            now, "tester", now, "tester", "trace-fragment");
    }

    private FollowupTemplate followupTemplate(String templateId, String versionId) {
        Instant now = Instant.parse("2026-06-08T00:00:00Z");
        return new FollowupTemplate(
            1L,
            templateId,
            "tenant-A",
            "FUP.COPD",
            1,
            "慢阻肺随访",
            "按院内规范生成随访任务",
            "tenant:tenant-A",
            "riskLevel in [MEDIUM,HIGH]",
            "[]",
            "{}",
            "{}",
            "hospital://followup/copd",
            versionId,
            now,
            "tester",
            now,
            "tester",
            "trace-followup");
    }

    private AuthoringAssetProfile profile(VersionedAssetType type, String assetId, String tagsJson) {
        Instant now = Instant.parse("2026-06-08T00:00:00Z");
        return new AuthoringAssetProfile(
            1L, "tenant-A", type, assetId, "临床复用", tagsJson,
            now, "tester", now, "tester", "trace-profile");
    }
}
