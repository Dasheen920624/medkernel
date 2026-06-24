package com.medkernel.engine.sandbox;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Component;

/**
 * 全真体验沙盘场景目录。只登记场景意图，不固化机构生效版本或运行就绪状态。
 */
@Component
public class SandboxScenarioCatalog {

    private final Map<String, SandboxScenario> scenarios = new LinkedHashMap<>();

    public SandboxScenarioCatalog() {
        register(ruleScenario(
            "sbx-lab-critical-k",
            "clinical-collaboration",
            "result-review",
            "LAB",
            "血钾危急值红线",
            "SBX-LAB-K-001",
            "SBX-LAB-K-ENC-001",
            "SBX.LAB.CRITICAL.K",
            "STRONG_REMINDER",
            "CRITICAL"
        ));
        register(ruleScenario(
            "sbx-med-warfarin-asa", "clinical-collaboration", "medication-prescribe", "ORDER",
            "华法林与阿司匹林联用风险", "SBX-MED-WA-001", "SBX-MED-WA-ENC-001",
            "SBX.MED.WARFARIN.ASA", "STRONG_REMINDER", "HIGH"));
        register(ruleScenario(
            "sbx-order-contrast-ckd", "clinical-collaboration", "order-sign", "ORDER",
            "肾功能不全造影剂风险", "SBX-CT-CKD-001", "SBX-CT-CKD-ENC-001",
            "SBX.ORDER.CONTRAST.CKD", "BLOCK", "HIGH"));
        register(ruleScenario(
            "sbx-dx-acs", "clinical-collaboration", "patient-view", "DIAGNOSIS",
            "胸痛与肌钙蛋白异常提示", "SBX-ACS-001", "SBX-ACS-ENC-001",
            "SBX.DX.ACS", "REMIND", "MEDIUM"));
        register(ruleScenario(
            "sbx-report-critical", "clinical-collaboration", "result-review", "REPORT",
            "影像报告危急征象回报", "SBX-RPT-001", "SBX-RPT-ENC-001",
            "SBX.REPORT.CRITICAL", "STRONG_REMINDER", "HIGH"));
        register(ruleScenario(
            "sbx-discharge-check", "clinical-collaboration", "discharge-sign", "DISCHARGE",
            "出院带药与随访核查", "SBX-DC-001", "SBX-DC-ENC-001",
            "SBX.DISCHARGE.CHECK", "REMIND", "MEDIUM"));
        register(ruleScenario(
            "sbx-followup-inr", "clinical-collaboration", "followup-alert", "FOLLOWUP",
            "随访 INR 异常处理", "SBX-FU-001", "SBX-FU-ENC-001",
            "SBX.FOLLOWUP.INR", "STRONG_REMINDER", "HIGH"));
        register(ruleScenario(
            "sbx-insurance-drg", "quality-improvement", "order-sign", "INSURANCE",
            "医保 DRG/DIP 合理性提示", "SBX-INS-001", "SBX-INS-ENC-001",
            "SBX.INSURANCE.DRG", "INFO", "LOW"));
        register(ruleScenario(
            "sbx-quality-record", "quality-improvement", "patient-view", "QUALITY",
            "病历内涵质控提示", "SBX-QC-001", "SBX-QC-ENC-001",
            "SBX.QUALITY.RECORD", "REMIND", "MEDIUM"));
        register(ruleScenario(
            "sbx-record-completeness", "quality-improvement", "patient-view", "RECORD",
            "病历结构化完整性提示", "SBX-REC-001", "SBX-REC-ENC-001",
            "SBX.RECORD.COMPLETENESS", "INFO", "LOW"));

        register(outerScenario(
            "sbx-pathway-ed", "pathway", "PATHWAY", "急诊路径入径与推进",
            "SBX-LAB-K-001", "SBX-LAB-K-ENC-001", "PATHWAY", "MEDIUM",
            "PATH.ED.DISPOSITION"));
        register(outerScenario(
            "sbx-recommendation-composite", "recommendation", "RECOMMENDATION_COMPOSITE",
            "推荐综合卡", "SBX-ACS-001", "SBX-ACS-ENC-001", "SUGGEST_ORDER", "MEDIUM",
            null));
        register(outerScenario(
            "sbx-followup-closed-loop", "followup", "FOLLOWUP", "随访异常回院闭环",
            "SBX-FU-001", "SBX-FU-ENC-001", "FOLLOWUP", "HIGH", null));
        register(outerScenario(
            "sbx-evaluation-closed-loop", "evaluation", "EVALUATION", "质控整改复核闭环",
            "SBX-QC-001", "SBX-QC-ENC-001", "EVALUATION", "MEDIUM", null));
        register(outerScenario(
            "sbx-embed-modes", "embed", "EMBED", "嵌入三模式契约",
            "SBX-LAB-K-001", "SBX-LAB-K-ENC-001", "IFRAME_SDK_API", "LOW", null));
    }

    public SandboxScenario require(String scenarioId) {
        SandboxScenario scenario = scenarios.get(scenarioId);
        if (scenario == null) {
            throw new IllegalArgumentException("未知沙盘场景: " + scenarioId);
        }
        return scenario;
    }

    public SandboxScenario requireRunnable(String scenarioId) {
        return require(scenarioId);
    }

    public List<SandboxScenario> all() {
        return List.copyOf(scenarios.values());
    }

    private void register(SandboxScenario scenario) {
        scenarios.put(scenario.id(), scenario);
    }

    private static SandboxScenario ruleScenario(
            String id,
            String serviceLine,
            String triggerPoint,
            String ruleType,
            String title,
            String patientId,
            String encounterId,
            String expectedRuleCode,
            String expectedAction,
            String expectedSeverity) {
        return new SandboxScenario(
            id, serviceLine, "rule", triggerPoint, ruleType, title, patientId, encounterId,
            expectedRuleCode, expectedAction, expectedSeverity, "RULE_ONLY", null);
    }

    private static SandboxScenario outerScenario(
            String id,
            String engine,
            String playbook,
            String title,
            String patientId,
            String encounterId,
            String expectedAction,
            String expectedSeverity,
            String expectedAssetCode) {
        return new SandboxScenario(
            id, "engine-orchestration", engine, "patient-view", "N/A", title,
            patientId, encounterId, null, expectedAction, expectedSeverity, playbook, expectedAssetCode);
    }
}
