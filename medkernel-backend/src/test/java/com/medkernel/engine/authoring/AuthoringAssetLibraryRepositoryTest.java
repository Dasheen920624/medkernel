package com.medkernel.engine.authoring;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;

import com.medkernel.engine.followup.FollowupTemplate;
import com.medkernel.engine.followup.FollowupTemplateRepository;
import com.medkernel.engine.pathway.PathwayEntryMode;
import com.medkernel.engine.pathway.PathwayEdge;
import com.medkernel.engine.pathway.PathwayEdgeRepository;
import com.medkernel.engine.pathway.PathwayEdgeType;
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
import com.medkernel.engine.rule.RuleVersion;
import com.medkernel.engine.rule.RuleVersionRepository;
import com.medkernel.engine.rule.RuleVersionStatus;
import com.medkernel.engine.versioning.VersionedAssetType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.autoconfigure.flyway.FlywayAutoConfiguration;
import org.springframework.boot.test.autoconfigure.data.jdbc.DataJdbcTest;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.test.context.TestPropertySource;

@DataJdbcTest
@ImportAutoConfiguration(FlywayAutoConfiguration.class)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@TestPropertySource(properties = {
    "spring.datasource.url=jdbc:h2:mem:authoring-asset-library-${random.uuid};MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;CASE_INSENSITIVE_IDENTIFIERS=TRUE;DB_CLOSE_DELAY=-1",
    "spring.datasource.driver-class-name=org.h2.Driver",
    "spring.datasource.username=sa",
    "spring.datasource.password=",
    "spring.flyway.enabled=true",
    "spring.flyway.locations=classpath:db/migration/h2"
})
class AuthoringAssetLibraryRepositoryTest {

    @Autowired RuleDefinitionRepository rules;
    @Autowired RuleVersionRepository ruleVersions;
    @Autowired PathwayTemplateRepository pathways;
    @Autowired PathwayEdgeRepository pathwayEdges;
    @Autowired ConditionFragmentRepository fragments;
    @Autowired FollowupTemplateRepository followups;
    @Autowired AuthoringAssetProfileRepository profiles;
    @Autowired AuthoringAssetFavoriteRepository favorites;

    @AfterEach
    void clean() {
        favorites.deleteAll();
        profiles.deleteAll();
        followups.deleteAll();
        fragments.deleteAll();
        pathwayEdges.deleteAll();
        pathways.deleteAll();
        ruleVersions.deleteAll();
        rules.deleteAll();
    }

    @Test
    void profileFilteredLibraryQueriesExecuteThroughRepositoryPagination() {
        rules.save(rule("rule-1", "RULE.CKD"));
        pathways.save(pathway("pathway-1", "PATH.CKD"));
        fragments.save(fragment("fragment-1", "FRAG_CKD"));
        followups.save(followup("followup-1", "FOLLOWUP.CKD"));
        for (VersionedAssetType type : new VersionedAssetType[] {
            VersionedAssetType.RULE,
            VersionedAssetType.PATHWAY,
            VersionedAssetType.CONDITION_FRAGMENT,
            VersionedAssetType.FOLLOWUP
        }) {
            String assetId = switch (type) {
                case RULE -> "rule-1";
                case PATHWAY -> "pathway-1";
                case CONDITION_FRAGMENT -> "fragment-1";
                case FOLLOWUP -> "followup-1";
                default -> throw new IllegalStateException("未接入测试资产类型: " + type);
            };
            profiles.save(profile(type, assetId));
            favorites.save(favorite(type, assetId));
        }

        assertThat(rules.countForAuthoringLibrary("tenant-A", "%ckd%", "%\"复用\"%", "author-1"))
            .isEqualTo(1);
        assertThat(rules.pageForAuthoringLibrary("tenant-A", "%ckd%", "%\"复用\"%", "author-1", 0, 20))
            .extracting(RuleDefinition::ruleId)
            .containsExactly("rule-1");
        assertThat(pathways.countForAuthoringLibrary("tenant-A", "%ckd%", "%\"复用\"%", "author-1"))
            .isEqualTo(1);
        assertThat(pathways.pageForAuthoringLibrary("tenant-A", "%ckd%", "%\"复用\"%", "author-1", 0, 20))
            .extracting(PathwayTemplate::templateId)
            .containsExactly("pathway-1");
        assertThat(fragments.countForAuthoringLibrary("tenant-A", "%ckd%", "%\"复用\"%", "author-1"))
            .isEqualTo(1);
        assertThat(fragments.pageForAuthoringLibrary("tenant-A", "%ckd%", "%\"复用\"%", "author-1", 0, 20))
            .extracting(ConditionFragment::fragmentId)
            .containsExactly("fragment-1");
        assertThat(followups.countForAuthoringLibrary("tenant-A", "%ckd%", "%\"复用\"%", "author-1"))
            .isEqualTo(1);
        assertThat(followups.pageForAuthoringLibrary("tenant-A", "%ckd%", "%\"复用\"%", "author-1", 0, 20))
            .extracting(FollowupTemplate::templateId)
            .containsExactly("followup-1");
    }

    @Test
    void fragmentImpactPrefilterQueriesExecuteThroughRepositoryPagination() {
        rules.save(rule("rule-impact", "RULE.IMPACT", "rv-impact"));
        ruleVersions.save(ruleVersion("rv-impact", "rule-impact", """
            {"when":{"fragmentRef":"FRAG_RENAL","version":1,"packageVersion":"pkg-2026.06"}}
            """));
        pathways.save(pathway("pathway-impact", "PATH.IMPACT"));
        pathwayEdges.save(edge("edge-impact", "pathway-impact", """
            {"all":[{"fragmentRef":"FRAG_RENAL","version":1,"packageVersion":"pkg-2026.06"}]}
            """));

        assertThat(rules.countActiveRuleImpactsByFragmentPattern("tenant-A", "%frag_renal%"))
            .isEqualTo(1);
        assertThat(rules.pageActiveRuleImpactsByFragmentPattern("tenant-A", "%frag_renal%", 0, 20))
            .extracting(RuleDefinition::ruleId)
            .containsExactly("rule-impact");
        assertThat(pathways.countTemplateImpactsByFragmentPattern("tenant-A", "%frag_renal%"))
            .isEqualTo(1);
        assertThat(pathways.pageTemplateImpactsByFragmentPattern("tenant-A", "%frag_renal%", 0, 20))
            .extracting(PathwayTemplate::templateId)
            .containsExactly("pathway-impact");
    }

    private RuleDefinition rule(String ruleId, String ruleCode) {
        return rule(ruleId, ruleCode, null);
    }

    private RuleDefinition rule(String ruleId, String ruleCode, String activeVersionId) {
        Instant now = Instant.now();
        return new RuleDefinition(
            null, ruleId, "tenant-A", ruleCode, "CKD 规则",
            RuleType.ORDER, RuleAuthoringMode.DSL, RuleRiskLevel.HIGH,
            100, null, 0, RuleDefinitionStatus.DRAFT, activeVersionId, "pkg-1",
            "dept-1", now, "tester", now, "tester", "trace-authoring");
    }

    private RuleVersion ruleVersion(String versionId, String ruleId, String dslJson) {
        Instant now = Instant.now();
        return new RuleVersion(
            null, versionId, "tenant-A", ruleId, 1, "source-1", "测试版本",
            dslJson, "{}", RuleVersionStatus.DRAFT, null, null, null,
            now, "tester", now, "tester", "trace-authoring");
    }

    private PathwayTemplate pathway(String templateId, String templateCode) {
        Instant now = Instant.now();
        return new PathwayTemplate(
            null, templateId, "tenant-A", "pkg-1", templateCode, "CKD 路径",
            "CKD", 1, PathwayTemplateLevel.SPECIALTY, PathwayTemplateStatus.DRAFT,
            PathwayEntryMode.MANUAL_CONFIRM, "start", "source-1", "说明", "[]", "[]",
            now, "tester", now, "tester", "trace-authoring");
    }

    private ConditionFragment fragment(String fragmentId, String fragmentCode) {
        Instant now = Instant.now();
        return new ConditionFragment(
            null, fragmentId, "tenant-A", fragmentCode, "CKD 条件片段",
            "慢病", "{\"all\":[]}", 1, ConditionFragmentStatus.DRAFT, "pkg-1",
            now, "tester", now, "tester", "trace-authoring");
    }

    private FollowupTemplate followup(String templateId, String templateCode) {
        Instant now = Instant.now();
        return new FollowupTemplate(
            null, templateId, "tenant-A", templateCode, 1, "CKD 随访",
            "说明", "tenant-A", "CKD", "{}", "{}", "{}", "source-1",
            "av-followup-1", now, "tester", now, "tester", "trace-authoring");
    }

    private PathwayEdge edge(String edgeId, String templateId, String conditionJson) {
        Instant now = Instant.now();
        return new PathwayEdge(
            null, edgeId, "tenant-A", templateId, "EDGE.IMPACT", "START", "NEXT",
            PathwayEdgeType.CONDITION, conditionJson, 10,
            now, "tester", now, "tester", "trace-authoring");
    }

    private AuthoringAssetProfile profile(VersionedAssetType type, String assetId) {
        Instant now = Instant.now();
        return new AuthoringAssetProfile(
            null, "tenant-A", type, assetId, "慢病", "[\"CKD\",\"复用\"]",
            now, "tester", now, "tester", "trace-authoring");
    }

    private AuthoringAssetFavorite favorite(VersionedAssetType type, String assetId) {
        return new AuthoringAssetFavorite(
            null, "tenant-A", "author-1", type, assetId, Instant.now(), "trace-authoring");
    }
}
