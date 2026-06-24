package com.medkernel.engine.authoring;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

class AuthoringPreviewServiceTest {

    private final ObjectMapper json = new ObjectMapper();
    private final AuthoringPreviewService service = new AuthoringPreviewService(json);

    @Test
    void rendersNestedRulePreviewWithoutLosingClinicalSemantics() throws Exception {
        AuthoringPreviewResponse response = service.preview(new AuthoringPreviewRequest(
            apiContext(),
            AuthoringPreviewSubject.RULE_CONDITION,
            json.readTree("""
                {
                  "when": {
                    "all": [
                      {
                        "fact": "patient.age",
                        "operator": "gte",
                        "value": 65,
                        "ui": {"label": "年龄"}
                      },
                      {
                        "any": [
                          {
                            "expr": {
                              "field": "observations[].valueNumeric",
                              "select": "latest",
                              "where": {
                                "fact": "observations[].code",
                                "operator": "equals",
                                "value": {"const": "CREATININE"}
                              },
                              "over": "P7D"
                            },
                            "operator": "derived",
                            "value": {
                              "formula": "CKD_EPI_2021_EGFR",
                              "parameters": {
                                "creatinine": "observations[].valueNumeric",
                                "age": "patient.age",
                                "sex": "patient.gender"
                              },
                              "source": "CKD-EPI 2021"
                            },
                            "ui": {"label": "eGFR"}
                          },
                          {
                            "fact": "medications[].code",
                            "operator": "in",
                            "value": {
                              "valueSet": "VS.ANTICOAGULANT",
                              "members": ["ATC:B01AA03", "ATC:M01A"]
                            },
                            "ui": {"label": "用药编码"}
                          }
                        ]
                      },
                      {
                        "not": {
                          "fact": "allergyIntolerances[].code",
                          "operator": "contains",
                          "value": "PENICILLIN",
                          "ui": {"label": "过敏编码"}
                        }
                      }
                    ]
                  },
                  "then": [
                    {
                      "actionCode": "BLOCK",
                      "summary": "阻断高危用药",
                      "source": {"label": "慢性肾病诊疗指南", "evidenceLevel": "A"}
                    }
                  ],
                  "explain": {"source": {"label": "慢性肾病诊疗指南", "evidenceLevel": "A"}}
                }
                """)
        ));

        assertThat(response.previewText())
            .contains("年龄 大于等于 65")
            .contains("且")
            .contains("或")
            .contains("不满足")
            .contains("最近一次 observations[].valueNumeric")
            .contains("P7D")
            .contains("eGFR CKD-EPI 2021")
            .contains("VS.ANTICOAGULANT")
            .contains("ATC:B01AA03")
            .contains("阻断高危用药")
            .contains("慢性肾病诊疗指南")
            .contains("证据等级 A");
        assertThat(response.warnings()).isEmpty();
    }

    @Test
    void warnsAndIgnoresManualRuntimeSelectorFieldsInValueSetPreview() throws Exception {
        AuthoringPreviewResponse response = service.preview(new AuthoringPreviewRequest(
            apiContext(),
            AuthoringPreviewSubject.RULE_CONDITION,
            json.readTree("""
                {
                  "when": {
                    "all": [
                      {
                        "fact": "medications[].code",
                        "operator": "in",
                        "value": {
                          "valueSet": "VS.ANTICOAGULANT",
                          "members": ["ATC:B01AA03"],
                          "packageVersion": "pkg-2026.1"
                        },
                        "ui": {"label": "用药编码"}
                      }
                    ]
                  }
                }
                """)
        ));

        assertThat(response.previewText())
            .contains("值集 VS.ANTICOAGULANT")
            .contains("ATC:B01AA03")
            .doesNotContain("包版本", "pkg-2026.1");
        assertThat(response.warnings())
            .contains("值集引用中的手工运行定位字段已忽略；请通过资产依赖和当前机构生效版本定位正式版本。");
    }

    @Test
    void rendersPathwayGuardWithSameConditionGrammar() throws Exception {
        AuthoringPreviewResponse response = service.preview(new AuthoringPreviewRequest(
            apiContext(),
            AuthoringPreviewSubject.PATHWAY_GUARD,
            json.readTree("""
                {
                  "guard": {
                    "any": [
                      {"fact": "risk.level", "operator": "equals", "value": "HIGH", "ui": {"label": "风险等级"}},
                      {"fact": "pathway.timer.WAIT24H.ready", "operator": "equals", "value": true, "ui": {"label": "24小时等待计时"}}
                    ]
                  },
                  "edgeCode": "E3",
                  "fromNodeCode": "N2",
                  "toNodeCode": "N5"
                }
                """)
        ));

        assertThat(response.previewText())
            .contains("路径守卫 E3")
            .contains("从 N2 到 N5")
            .contains("风险等级 等于 HIGH")
            .contains("或")
            .contains("24小时等待计时 等于 true");
    }

    private static AuthoringApiContext apiContext() {
        return new AuthoringApiContext(
            "req-preview",
            "trace-preview",
            "t-1",
            null,
            null,
            null,
            null,
            null,
            null,
            "author-1",
            List.of("engine-operator")
        );
    }
}
