package com.medkernel.engine.interop;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.medkernel.engine.context.ClinicalEventTriggerPoint;
import com.medkernel.engine.pathway.PathwayEdgeRequest;
import com.medkernel.engine.pathway.PathwayEdgeType;
import com.medkernel.engine.pathway.PathwayEntryMode;
import com.medkernel.engine.pathway.PathwayMilestoneRequest;
import com.medkernel.engine.pathway.PathwayNodeRequest;
import com.medkernel.engine.pathway.PathwayNodeType;
import com.medkernel.engine.pathway.PathwayTemplateCreateRequest;
import com.medkernel.engine.pathway.PathwayTemplateLevel;
import com.medkernel.engine.rule.RuleAuthoringMode;
import com.medkernel.engine.rule.RuleCreateRequest;
import com.medkernel.engine.rule.RuleRiskLevel;
import com.medkernel.engine.rule.RuleType;
import com.medkernel.shared.api.error.ApiException;
import org.junit.jupiter.api.Test;

class InteroperabilityMappingServiceTest {

    private final ObjectMapper json = new ObjectMapper();
    private final InteroperabilityMappingService service = new InteroperabilityMappingService(json);

    @Test
    void ruleDslExportsToCdsHooksAndOptionalCqlArdenWithoutLosingRoundTripSemantics() throws Exception {
        JsonNode dsl = json.readTree("""
            {
              "trigger": "order-sign",
              "when": {
                "all": [
                  {"fact": "order.drugClass", "operator": "equals", "value": "ACEI"}
                ]
              },
              "then": [
                {
                  "atSeverity": "HIGH",
                  "actionCode": "BLOCK",
                  "indicator": "critical",
                  "summary": "ACEI 医嘱需复核肾功能",
                  "detail": "患者 CKD 风险下开立 ACEI 前需复核 eGFR 与血钾。",
                  "source": {
                    "label": "CKD 诊疗规范",
                    "url": "https://example.invalid/ckd",
                    "evidenceLevel": "A"
                  },
                  "suggestions": [
                    {"label": "查看肾功能趋势", "actionType": "OPEN_PANEL", "payload": {"panel": "renal-trend"}}
                  ],
                  "overrideReasons": ["已复核 eGFR", "抢救场景"],
                  "requiresPhysicianConfirmation": false
                }
              ],
              "explain": {"summary": "CKD ACEI 安全用药"}
            }
            """);
        RuleCreateRequest request = new RuleCreateRequest(
            "RULE-CKD-ACEI",
            "CKD ACEI 开嘱复核",
            RuleType.ORDER,
            RuleAuthoringMode.VISUAL,
            RuleRiskLevel.HIGH,
            "pkg-ckd-2026.06",
            "HOSPITAL-1",
            "CKD-PACKAGE",
            "导出标准 CDS Hooks 映射",
            dsl,
            json.createObjectNode());

        RuleCdsHooksMapping mapping = service.exportRuleToCdsHooks(request);

        assertThat(mapping.hook()).isEqualTo(ClinicalEventTriggerPoint.ORDER_SIGN);
        assertThat(mapping.cards()).singleElement().satisfies(card -> {
            assertThat(card.uuid()).isEqualTo("RULE-CKD-ACEI-BLOCK-1");
            assertThat(card.indicator()).isEqualTo("critical");
            assertThat(card.summary()).isEqualTo("ACEI 医嘱需复核肾功能");
            assertThat(card.source().label()).isEqualTo("CKD 诊疗规范");
            assertThat(card.suggestions()).singleElement()
                .satisfies(suggestion -> assertThat(suggestion.payload().path("panel").asText())
                    .isEqualTo("renal-trend"));
            assertThat(card.overrideReasons()).containsExactly("已复核 eGFR", "抢救场景");
            assertThat(card.requiresPhysicianConfirmation()).isTrue();
        });
        assertThat(mapping.cdsService().path("hook").asText()).isEqualTo("order-sign");
        assertThat(mapping.cdsService().path("extension").path("medkernelRuleDsl")).isEqualTo(dsl);
        assertThat(mapping.cql().path("library").asText()).isEqualTo("RULE_CKD_ACEI");
        assertThat(mapping.cql().path("statement").asText()).contains("order-sign", "order.drugClass");
        assertThat(mapping.arden().path("mlm").asText()).contains("RULE-CKD-ACEI", "ACEI 医嘱需复核肾功能");

        RuleCreateRequest roundTrip = service.importRuleFromCdsHooks(mapping);

        assertThat(roundTrip.ruleCode()).isEqualTo(request.ruleCode());
        assertThat(roundTrip.name()).isEqualTo(request.name());
        assertThat(roundTrip.packageVersion()).isEqualTo(request.packageVersion());
        assertThat(roundTrip.dsl()).isEqualTo(dsl);
    }

    @Test
    void ruleExportRejectsUnsupportedTriggerAsRuleDomainError() throws Exception {
        JsonNode dsl = json.readTree("""
            {
              "trigger": "unsupported-trigger",
              "when": {"all": []},
              "then": []
            }
            """);
        RuleCreateRequest request = new RuleCreateRequest(
            "RULE-BAD-TRIGGER",
            "非法触发点",
            RuleType.ORDER,
            RuleAuthoringMode.DSL,
            RuleRiskLevel.LOW,
            "pkg-ckd-2026.06",
            null,
            "CKD-PACKAGE",
            "非法触发点应返回规则域错误",
            dsl,
            json.createObjectNode());

        assertThatThrownBy(() -> service.exportRuleToCdsHooks(request))
            .isInstanceOf(ApiException.class)
            .hasMessageContaining("规则触发点无效");
    }

    @Test
    void pathwayTemplateExportsToPlanDefinitionAndGlifWithStagesActionsDecisionsAndGuards() throws Exception {
        JsonNode guard = json.readTree("""
            {"all": [{"fact": "patient.egfr", "operator": "lt", "value": 30}]}
            """);
        PathwayTemplateCreateRequest request = new PathwayTemplateCreateRequest(
            "PKG-CKD",
            "PATH-CKD",
            "CKD 分层诊疗路径",
            "N18",
            3,
            PathwayTemplateLevel.STANDARD,
            PathwayEntryMode.AUTO_SUGGEST,
            "N1",
            "CKD-PACKAGE",
            "CKD 专病包标准路径",
            guard,
            json.readTree("""
                {"all": [{"fact": "patient.followupDone", "operator": "equals", "value": true}]}
                """),
            List.of(new PathwayMilestoneRequest(
                "PHASE-ASSESS",
                "评估期",
                "M1",
                "完成 CKD 分层评估",
                0,
                240,
                guard,
                1)),
            List.of(
                new PathwayNodeRequest("N1", "入径评估", PathwayNodeType.ASSESSMENT, "M1", 1,
                    "DOCTOR", null, 60, false, json.createObjectNode()),
                new PathwayNodeRequest("N2", "eGFR 决策", PathwayNodeType.DECISION, "M1", 2,
                    "DOCTOR", null, 30, false, json.createObjectNode()),
                new PathwayNodeRequest("N3", "肾内会诊", PathwayNodeType.MANUAL_GATE, "M1", 3,
                    "NEPHROLOGIST", null, 120, true, json.createObjectNode())),
            List.of(
                new PathwayEdgeRequest("E1", "N1", "N2", PathwayEdgeType.DEFAULT, null, 1),
                new PathwayEdgeRequest("E2", "N2", "N3", PathwayEdgeType.CONDITION, guard, 2)),
            List.of());

        PathwayStandardMapping mapping = service.exportPathwayToPlanDefinition(request);

        JsonNode plan = mapping.planDefinition();
        assertThat(plan.path("resourceType").asText()).isEqualTo("PlanDefinition");
        assertThat(plan.path("id").asText()).isEqualTo("PATH-CKD");
        assertThat(plan.path("action")).hasSize(3);
        assertThat(plan.path("action").get(1).path("type").path("coding").get(0).path("code").asText())
            .isEqualTo("DECISION");
        assertThat(plan.path("action").get(1).path("relatedAction").get(0).path("targetId").asText())
            .isEqualTo("N3");
        assertThat(plan.path("action").get(1).path("condition").get(0).path("expression").path("extension")
            .path("medkernelGuard")).isEqualTo(guard);
        assertThat(mapping.glif().path("steps")).hasSize(3);
        assertThat(mapping.glif().path("decisions")).hasSize(1);
        assertThat(plan.path("extension").path("medkernelPathwayDraft").path("templateCode").asText())
            .isEqualTo("PATH-CKD");

        PathwayTemplateCreateRequest roundTrip = service.importPathwayFromPlanDefinition(mapping);

        assertThat(roundTrip.templateCode()).isEqualTo(request.templateCode());
        assertThat(roundTrip.name()).isEqualTo(request.name());
        assertThat(roundTrip.milestones()).hasSize(1);
        assertThat(roundTrip.nodes()).extracting(PathwayNodeRequest::nodeCode)
            .containsExactly("N1", "N2", "N3");
        assertThat(roundTrip.edges()).extracting(PathwayEdgeRequest::edgeCode)
            .containsExactly("E1", "E2");
        assertThat(roundTrip.edges().get(1).condition()).isEqualTo(guard);
    }
}
