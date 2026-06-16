package com.medkernel.engine.authoring;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.medkernel.engine.pathway.PathwayEdge;
import com.medkernel.engine.pathway.PathwayEdgeRepository;
import com.medkernel.engine.pathway.PathwayEdgeType;
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
import com.medkernel.engine.rule.RuleVersion;
import com.medkernel.engine.rule.RuleVersionRepository;
import com.medkernel.engine.rule.RuleVersionStatus;
import com.medkernel.shared.api.error.ApiException;
import com.medkernel.shared.api.error.ErrorCode;
import com.medkernel.shared.audit.AuditRecorder;
import com.medkernel.shared.context.OrgScope;
import com.medkernel.shared.context.RequestContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ConditionFragmentServiceTest {

    private final ObjectMapper json = new ObjectMapper().findAndRegisterModules();
    private final ConditionFragmentRepository fragments = mock(ConditionFragmentRepository.class);
    private final RuleDefinitionRepository ruleDefinitions = mock(RuleDefinitionRepository.class);
    private final RuleVersionRepository ruleVersions = mock(RuleVersionRepository.class);
    private final PathwayTemplateRepository pathwayTemplates = mock(PathwayTemplateRepository.class);
    private final PathwayEdgeRepository pathwayEdges = mock(PathwayEdgeRepository.class);
    private final ConditionFragmentAssetVersionProjector versionProjector =
        mock(ConditionFragmentAssetVersionProjector.class);
    private final AuditRecorder auditRecorder = mock(AuditRecorder.class);
    private final ConditionFragmentService service = new ConditionFragmentService(
        json, fragments, ruleDefinitions, ruleVersions, pathwayTemplates, pathwayEdges,
        versionProjector, auditRecorder);

    @BeforeEach
    void setUp() {
        RequestContext.restore(new RequestContext.Snapshot(
            "trace-fragment", OrgScope.tenant("tenant-A"), "author-1"));
    }

    @AfterEach
    void clearContext() {
        RequestContext.clear();
    }

    @Test
    void createsFragmentWithTenantAuditFields() throws Exception {
        when(fragments.findByTenantIdAndFragmentCodeAndVersionNo("tenant-A", "FRAG_RENAL", 1))
            .thenReturn(Optional.empty());
        when(fragments.save(any())).thenAnswer(invocation -> {
            ConditionFragment fragment = invocation.getArgument(0);
            return new ConditionFragment(
                10L, fragment.fragmentId(), fragment.tenantId(), fragment.fragmentCode(), fragment.name(),
                fragment.category(), fragment.bodyJson(), fragment.versionNo(), fragment.status(),
                fragment.packageVersion(), fragment.createdAt(), fragment.createdBy(),
                fragment.updatedAt(), fragment.updatedBy(), fragment.traceId());
        });

        ConditionFragmentResponse response = service.create(new ConditionFragmentUpsertRequest(
            "FRAG_RENAL",
            "肾功能受限",
            "肾病",
            json.readTree("""
                {"all":[{"fact":"patient.age","operator":"gte","value":65}]}
                """),
            1,
            "pkg-2026.06",
            ConditionFragmentStatus.ACTIVE
        ));

        assertThat(response.fragmentCode()).isEqualTo("FRAG_RENAL");
        assertThat(response.tenantId()).isEqualTo("tenant-A");
        assertThat(response.versionNo()).isEqualTo(1);
        assertThat(response.packageVersion()).isEqualTo("pkg-2026.06");
        assertThat(response.bodyJson().path("all")).hasSize(1);
    }

    @Test
    void impactFindsRulesAndPathwaysReferencingSameFragment() throws Exception {
        ConditionFragment renal = fragment("frag-renal", "FRAG_RENAL", 1, body("""
            {"all":[{"fact":"patient.age","operator":"gte","value":65}]}
            """));
        when(fragments.findByFragmentIdAndTenantId("frag-renal", "tenant-A"))
            .thenReturn(Optional.of(renal));
        when(ruleDefinitions.listByFilter("tenant-A", null, null, null, null))
            .thenReturn(List.of(rule("rule-1", "RULE.RENAL", "rv-1")));
        when(ruleVersions.findByVersionIdAndTenantId("rv-1", "tenant-A"))
            .thenReturn(Optional.of(ruleVersion("rv-1", "rule-1", """
                {
                  "when": {
                    "fragmentRef": "FRAG_RENAL",
                    "version": 1,
                    "packageVersion": "pkg-2026.06"
                  }
                }
                """)));
        when(pathwayTemplates.listByFilter("tenant-A", null, null, null, null, null))
            .thenReturn(List.of(pathway("pathway-1", "PATH.RENAL")));
        when(pathwayEdges.findByTemplateIdAndTenantIdOrderByPriorityAsc("pathway-1", "tenant-A"))
            .thenReturn(List.of(edge("edge-1", "pathway-1", """
                {"fragmentRef":"FRAG_RENAL","version":1,"packageVersion":"pkg-2026.06"}
                """)));

        ConditionFragmentImpactResponse impact = service.impact("frag-renal");

        assertThat(impact.fragmentCode()).isEqualTo("FRAG_RENAL");
        assertThat(impact.affectedAssets())
            .extracting(ConditionFragmentAffectedAsset::assetType)
            .containsExactly("RULE", "PATHWAY");
        assertThat(impact.impactDigest()).isNotBlank();
    }

    @Test
    void rejectsIndirectFragmentCycleOnSave() throws Exception {
        when(fragments.findByFragmentIdAndTenantId("frag-a", "tenant-A"))
            .thenReturn(Optional.of(fragment(
                "frag-a", "FRAG_A", 1,
                body("""
                    {"all":[{"fact":"patient.age","operator":"gte","value":65}]}
                    """),
                ConditionFragmentStatus.DRAFT)));
        when(fragments.findLatestByTenantIdAndFragmentCode("tenant-A", "FRAG_B"))
            .thenReturn(Optional.of(fragment("frag-b", "FRAG_B", 1, body("""
                {"fragmentRef":"FRAG_A","version":1,"packageVersion":"pkg-2026.06"}
                """))));

        assertThatThrownBy(() -> service.update("frag-a", new ConditionFragmentUpsertRequest(
            "FRAG_A",
            "片段 A",
            null,
            body("""
                {"fragmentRef":"FRAG_B","version":1,"packageVersion":"pkg-2026.06"}
                """),
            1,
            "pkg-2026.06",
            ConditionFragmentStatus.ACTIVE
        )))
            .isInstanceOf(ApiException.class)
            .satisfies(error -> assertThat(((ApiException) error).errorCode()).isEqualTo(ErrorCode.ENG_RULE_001))
            .hasMessageContaining("条件片段循环引用");
    }

    @Test
    void rejectsInPlaceMutationOfActiveFragment() throws Exception {
        when(fragments.findByFragmentIdAndTenantId("frag-a", "tenant-A"))
            .thenReturn(Optional.of(fragment("frag-a", "FRAG_A", 1, body("""
                {"all":[{"fact":"patient.age","operator":"gte","value":65}]}
                """))));

        assertThatThrownBy(() -> service.update("frag-a", new ConditionFragmentUpsertRequest(
            "FRAG_A",
            "片段 A",
            null,
            body("""
                {"all":[{"fact":"patient.age","operator":"gte","value":18}]}
                """),
            1,
            "pkg-2026.06",
            ConditionFragmentStatus.ACTIVE
        )))
            .isInstanceOf(ApiException.class)
            .hasMessageContaining("不可原地修改")
            .hasMessageContaining("新建更高版本");
    }

    private ConditionFragment fragment(String fragmentId, String code, int versionNo, com.fasterxml.jackson.databind.JsonNode body) {
        return fragment(fragmentId, code, versionNo, body, ConditionFragmentStatus.ACTIVE);
    }

    private ConditionFragment fragment(
            String fragmentId,
            String code,
            int versionNo,
            com.fasterxml.jackson.databind.JsonNode body,
            ConditionFragmentStatus status) {
        Instant now = Instant.parse("2026-06-08T00:00:00Z");
        return new ConditionFragment(
            1L, fragmentId, "tenant-A", code, code + " 名称", "通用",
            body.toString(), versionNo, status, "pkg-2026.06",
            now, "author-1", now, "author-1", "trace-fragment");
    }

    private com.fasterxml.jackson.databind.JsonNode body(String jsonText) throws Exception {
        return json.readTree(jsonText);
    }

    private RuleDefinition rule(String ruleId, String ruleCode, String versionId) {
        Instant now = Instant.parse("2026-06-08T00:00:00Z");
        return new RuleDefinition(
            1L, ruleId, "tenant-A", ruleCode, "肾病规则", RuleType.LAB,
            RuleAuthoringMode.VISUAL, RuleRiskLevel.HIGH, 10, null, 0,
            RuleDefinitionStatus.DRAFT, versionId, "pkg-2026.06", null,
            now, "author-1", now, "author-1", "trace-fragment");
    }

    private RuleVersion ruleVersion(String versionId, String ruleId, String dsl) {
        Instant now = Instant.parse("2026-06-08T00:00:00Z");
        return new RuleVersion(
            1L, versionId, "tenant-A", ruleId, 1, "院内规范", "引用片段",
            dsl, "{}", RuleVersionStatus.DRAFT, null, null, null,
            now, "author-1", now, "author-1", "trace-fragment");
    }

    private PathwayTemplate pathway(String templateId, String code) {
        Instant now = Instant.parse("2026-06-08T00:00:00Z");
        return new PathwayTemplate(
            1L, templateId, "tenant-A", "pkg-renal", code, "肾病路径", "CKD", 1,
            PathwayTemplateLevel.HOSPITAL, PathwayTemplateStatus.DRAFT,
            PathwayEntryMode.MANUAL_CONFIRM, "N1", "院内路径", "引用片段路径",
            "{}", "{}", now, "author-1", now, "author-1", "trace-fragment");
    }

    private PathwayEdge edge(String edgeId, String templateId, String conditionJson) {
        Instant now = Instant.parse("2026-06-08T00:00:00Z");
        return new PathwayEdge(
            1L, edgeId, "tenant-A", templateId, "E1", "N1", "N2",
            PathwayEdgeType.CONDITION, conditionJson, 10,
            now, "author-1", now, "author-1", "trace-fragment");
    }
}
