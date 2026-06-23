package com.medkernel.engine.authoring;

import static org.assertj.core.api.Assertions.assertThat;
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
import com.medkernel.engine.followup.FollowupTemplateRepository;
import com.medkernel.engine.pathway.PathwayEntryMode;
import com.medkernel.engine.pathway.PathwayTemplate;
import com.medkernel.engine.pathway.PathwayTemplateLevel;
import com.medkernel.engine.pathway.PathwayTemplateRepository;
import com.medkernel.engine.pathway.PathwayTemplateStatus;
import com.medkernel.engine.rule.RuleAuthoringMode;
import com.medkernel.engine.rule.RuleDefinition;
import com.medkernel.engine.rule.RuleDefinitionRepository;
import com.medkernel.engine.rule.RuleDefinitionStatus;
import com.medkernel.engine.rule.RuleRiskLevel;
import com.medkernel.engine.rule.RuleType;
import com.medkernel.engine.versioning.AssetVersionRepository;
import com.medkernel.engine.versioning.VersionedAssetType;
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
    private final FollowupTemplateRepository followupTemplates = mock(FollowupTemplateRepository.class);
    private final AssetVersionRepository assetVersions = mock(AssetVersionRepository.class);
    private final AuthoringAssetProfileRepository profiles = mock(AuthoringAssetProfileRepository.class);
    private final AuthoringAssetFavoriteRepository favorites = mock(AuthoringAssetFavoriteRepository.class);
    private final AuthoringAssetLibraryService service = new AuthoringAssetLibraryService(
        new ObjectMapper(), rules, pathways, followupTemplates, assetVersions, profiles, favorites);

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
    void listsRulesWithTagsAndFavorites() {
        when(rules.countForAuthoringLibrary("tenant-A", "%ckd%", "%\"复用\"%", "author-1"))
            .thenReturn(1L);
        when(rules.pageForAuthoringLibrary("tenant-A", "%ckd%", "%\"复用\"%", "author-1", 0, 20))
            .thenReturn(List.of(rule("rule-1", "RULE.CKD", "CKD 阻断规则")));
        when(pathways.countForAuthoringLibrary("tenant-A", "%ckd%", "%\"复用\"%", "author-1"))
            .thenReturn(0L);
        when(pathways.pageForAuthoringLibrary("tenant-A", "%ckd%", "%\"复用\"%", "author-1", 0, 20))
            .thenReturn(List.of());
        when(followupTemplates.countForAuthoringLibrary("tenant-A", "%ckd%", "%\"复用\"%", "author-1"))
            .thenReturn(0L);
        when(followupTemplates.pageForAuthoringLibrary("tenant-A", "%ckd%", "%\"复用\"%", "author-1", 0, 20))
            .thenReturn(List.of());
        when(profiles.findByTenantIdAndAssetTypeAndAssetId(
            "tenant-A", VersionedAssetType.RULE, "rule-1"))
            .thenReturn(Optional.of(profile(VersionedAssetType.RULE, "rule-1", "[\"CKD\",\"复用\"]")));
        when(favorites.existsByTenantIdAndUserIdAndAssetTypeAndAssetId(
            "tenant-A", "author-1", VersionedAssetType.RULE, "rule-1"))
            .thenReturn(true);

        PageResponse<AuthoringAssetLibraryItem> response = service.list(
            new AuthoringAssetLibraryQuery(null, "CKD", "复用", true, new PageRequest(0, 20, null)));

        assertThat(response.items()).extracting(AuthoringAssetLibraryItem::assetType)
            .containsExactly(VersionedAssetType.RULE);
        AuthoringAssetLibraryItem item = response.items().getFirst();
        assertThat(item.assetCode()).isEqualTo("RULE.CKD");
        assertThat(item.tags()).containsExactly("CKD", "复用");
        assertThat(item.favorite()).isTrue();
        verify(rules, never()).listByFilter("tenant-A", null, null, null, null);
        verify(pathways, never()).listByFilter("tenant-A", null, null, null, null);
    }

    @Test
    void allAssetsQueryOnlyReadsAllowedAssetTypes() {
        when(rules.countByFilter("tenant-A", null, null, null, null)).thenReturn(1L);
        when(rules.pageByFilter("tenant-A", null, null, null, null, 0, 20))
            .thenReturn(List.of(rule("rule-1", "RULE.CKD", "CKD 阻断规则")));

        PageResponse<AuthoringAssetLibraryItem> response = service.list(
            new AuthoringAssetLibraryQuery(
                null,
                null,
                null,
                false,
                new PageRequest(0, 20, null),
                Set.of(VersionedAssetType.RULE)
            ));

        assertThat(response.items())
            .extracting(AuthoringAssetLibraryItem::assetType)
            .containsExactly(VersionedAssetType.RULE);
        verify(pathways, never()).countByFilter("tenant-A", null, null, null, null);
        verify(followupTemplates, never()).countByFilter("tenant-A", null, null);
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
    void updatesRuleProfileWithNormalizedDistinctTags() {
        when(rules.findByRuleIdAndTenantId("rule-1", "tenant-A"))
            .thenReturn(Optional.of(rule("rule-1", "RULE.CKD", "CKD 阻断规则")));
        when(profiles.findByTenantIdAndAssetTypeAndAssetId(
            "tenant-A", VersionedAssetType.RULE, "rule-1"))
            .thenReturn(Optional.empty());

        AuthoringAssetProfileResponse response = service.updateProfile(
            VersionedAssetType.RULE,
            "rule-1",
            new AuthoringAssetProfileRequest(" 慢病 ", List.of("复用", "CKD", "复用", " ")));

        assertThat(response.category()).isEqualTo("慢病");
        assertThat(response.tags()).containsExactly("复用", "CKD");
        ArgumentCaptor<AuthoringAssetProfile> captor = ArgumentCaptor.forClass(AuthoringAssetProfile.class);
        verify(profiles).save(captor.capture());
        assertThat(captor.getValue().tagsJson()).isEqualTo("[\"复用\",\"CKD\"]");
        assertThat(captor.getValue().createdBy()).isEqualTo("author-1");
    }

    @Test
    void favoritesAndUnfavoritesRuleIdempotently() {
        when(rules.findByRuleIdAndTenantId("rule-1", "tenant-A"))
            .thenReturn(Optional.of(rule("rule-1", "RULE.CKD", "CKD 阻断规则")));
        when(favorites.findByTenantIdAndUserIdAndAssetTypeAndAssetId(
            "tenant-A", "author-1", VersionedAssetType.RULE, "rule-1"))
            .thenReturn(Optional.empty());

        AuthoringAssetFavoriteResponse favorite = service.favorite(VersionedAssetType.RULE, "rule-1");

        assertThat(favorite.favorite()).isTrue();
        ArgumentCaptor<AuthoringAssetFavorite> captor = ArgumentCaptor.forClass(AuthoringAssetFavorite.class);
        verify(favorites).save(captor.capture());
        assertThat(captor.getValue().assetType()).isEqualTo(VersionedAssetType.RULE);

        AuthoringAssetFavorite existing = captor.getValue();
        when(favorites.findByTenantIdAndUserIdAndAssetTypeAndAssetId(
            "tenant-A", "author-1", VersionedAssetType.RULE, "rule-1"))
            .thenReturn(Optional.of(existing));

        AuthoringAssetFavoriteResponse unfavorite = service.unfavorite(VersionedAssetType.RULE, "rule-1");

        assertThat(unfavorite.favorite()).isFalse();
        verify(favorites).delete(existing);
    }

    private RuleDefinition rule(String id, String code, String name) {
        Instant now = Instant.parse("2026-06-08T00:00:00Z");
        return new RuleDefinition(
            1L, id, "tenant-A", code, name, RuleType.ORDER,
            RuleAuthoringMode.DSL, RuleRiskLevel.HIGH, 100, null, 3600,
            RuleDefinitionStatus.PUBLISHED, "rv-1", "dept-1",
            now, "tester", now, "tester", "trace-rule");
    }

    private PathwayTemplate pathway(String id, String code, String name) {
        Instant now = Instant.parse("2026-06-08T00:00:00Z");
        return new PathwayTemplate(
            1L, id, "tenant-A", code, name, "CKD", 1,
            PathwayTemplateLevel.STANDARD, PathwayTemplateStatus.PUBLISHED,
            PathwayEntryMode.AUTO_SUGGEST, "N1", "指南", "说明", "{}", "{}",
            now, "tester", now, "tester", "trace-pathway");
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
