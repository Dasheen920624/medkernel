package com.medkernel.engine.versioning;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.medkernel.shared.api.error.ApiException;

/**
 * 独立配置资产正文契约测试：值集、公式、医嘱套餐和动作卡必须保存可运行结构，
 * 子路径由真实路径模板维护，不允许再登记第二份通用 JSON 正文。
 */
class DeclarativeAssetContentValidatorTest {

    private final ObjectMapper json = new ObjectMapper();
    private final DeclarativeAssetContentValidator validator =
        new DeclarativeAssetContentValidator(json);

    @Test
    void acceptsExecutableValueSetAndCanonicalizesContent() throws Exception {
        String content = validator.validateAndCanonicalize(
            VersionedAssetType.VALUE_SET,
            json.readTree("""
                {
                  "schemaVersion": "1.0",
                  "name": "肾毒性药物 ATC 值集",
                  "codeSystem": "ATC",
                  "members": [
                    {"code": "J01GB03", "display": "庆大霉素"},
                    {"code": "J01XA01", "display": "万古霉素"}
                  ]
                }
                """));

        assertThat(json.readTree(content).path("members")).hasSize(2);
        assertThat(content).doesNotContain("\n");
    }

    @Test
    void rejectsDuplicateValueSetMembers() throws Exception {
        assertThatThrownBy(() -> validator.validateAndCanonicalize(
            VersionedAssetType.VALUE_SET,
            json.readTree("""
                {
                  "schemaVersion": "1.0",
                  "name": "重复值集",
                  "codeSystem": "ATC",
                  "members": [
                    {"code": "J01GB03", "display": "庆大霉素"},
                    {"code": "J01GB03", "display": "重复项"}
                  ]
                }
                """)))
            .isInstanceOf(ApiException.class)
            .hasMessageContaining("成员编码重复");
    }

    @Test
    void acceptsOnlyWhitelistedRuntimeFormula() throws Exception {
        String content = validator.validateAndCanonicalize(
            VersionedAssetType.FORMULA,
            json.readTree("""
                {
                  "schemaVersion": "1.0",
                  "name": "体质指数",
                  "runtimeFunction": "BMI",
                  "inputs": [
                    {"name": "height", "fieldPath": "observations[].valueNumeric", "unit": "cm"},
                    {"name": "weight", "fieldPath": "observations[].valueNumeric", "unit": "kg"}
                  ],
                  "output": {"dataType": "number", "unit": "kg/m2"}
                }
                """));

        assertThat(json.readTree(content).path("runtimeFunction").asText()).isEqualTo("BMI");

        assertThatThrownBy(() -> validator.validateAndCanonicalize(
            VersionedAssetType.FORMULA,
            json.readTree("""
                {
                  "schemaVersion": "1.0",
                  "name": "任意表达式",
                  "runtimeFunction": "EVAL_JAVASCRIPT",
                  "inputs": [{"name": "x", "fieldPath": "patient.age"}],
                  "output": {"dataType": "number", "unit": "1"}
                }
                """)))
            .hasMessageContaining("受控白名单");
    }

    @Test
    void orderSetRequiresPhysicianConfirmationAndStructuredItems() throws Exception {
        String content = validator.validateAndCanonicalize(
            VersionedAssetType.ORDER_SET,
            json.readTree("""
                {
                  "schemaVersion": "1.0",
                  "name": "急性肾损伤检验套餐",
                  "requiresPhysicianConfirmation": true,
                  "items": [{
                    "itemType": "LAB",
                    "codeSystem": "LOINC",
                    "code": "2160-0",
                    "display": "血肌酐",
                    "required": true
                  }]
                }
                """));
        assertThat(json.readTree(content).path("items")).hasSize(1);

        assertThatThrownBy(() -> validator.validateAndCanonicalize(
            VersionedAssetType.ORDER_SET,
            json.readTree("""
                {
                  "schemaVersion": "1.0",
                  "name": "错误自动医嘱",
                  "requiresPhysicianConfirmation": false,
                  "items": [{
                    "itemType": "LAB",
                    "codeSystem": "LOINC",
                    "code": "2160-0",
                    "display": "血肌酐",
                    "required": true
                  }]
                }
                """)))
            .hasMessageContaining("必须由医师确认");
    }

    @Test
    void actionCardValidatesExecutableActions() throws Exception {
        String content = validator.validateAndCanonicalize(
            VersionedAssetType.ACTION_CARD,
            json.readTree("""
                {
                  "schemaVersion": "1.0",
                  "title": "肾功能异常处置",
                  "actionCode": "SUGGEST_ORDER",
                  "atSeverity": "HIGH",
                  "indicator": "critical",
                  "summary": "复核肾功能并调整方案",
                  "detail": "命中后建议医师打开肾功能复核套餐，不自动开立医嘱。",
                  "source": {"label": "CKD 用药安全指南", "evidenceLevel": "GUIDELINE"},
                  "suggestions": [{
                    "label": "打开肾功能复核套餐",
                    "actionType": "SUGGEST_ORDER",
                    "payload": {"orderSetRef": "ORDER.CKD.REVIEW"}
                  }],
                  "overrideReasons": ["已人工复核", "临床获益大于风险"],
                  "requiresPhysicianConfirmation": true
                }
                """));
        assertThat(json.readTree(content).path("actionCode").asText()).isEqualTo("SUGGEST_ORDER");
        assertThat(json.readTree(content).path("suggestions")).hasSize(1);

        assertThatThrownBy(() -> validator.validateAndCanonicalize(
            VersionedAssetType.ACTION_CARD,
            json.readTree("""
                {
                  "schemaVersion": "1.0",
                  "title": "错误自动动作卡",
                  "actionCode": "SUGGEST_ORDER",
                  "atSeverity": "HIGH",
                  "indicator": "critical",
                  "summary": "错误自动医嘱建议",
                  "detail": "建议医嘱不能跳过医师确认。",
                  "source": {"label": "测试来源"},
                  "suggestions": [{
                    "label": "自动建议",
                    "actionType": "SUGGEST_ORDER",
                    "payload": {"orderSetRef": "ORDER.TEST"}
                  }],
                  "overrideReasons": ["人工复核"],
                  "requiresPhysicianConfirmation": false
                }
                """)))
            .hasMessageContaining("动作卡建议医嘱必须由医师确认");
    }

    @Test
    void subPathwayMustUsePathwayWorkbenchInsteadOfGenericContent() throws Exception {
        assertThatThrownBy(() -> validator.validateAndCanonicalize(
            VersionedAssetType.PATHWAY,
            json.readTree("""
                {"schemaVersion": "1.0", "name": "重复子路径正文"}
                """)))
            .hasMessageContaining("路径工作台");
    }
}
