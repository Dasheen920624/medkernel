package com.medkernel.engine.sandbox;

/**
 * 全真体验沙盘前端目录项。后端负责场景可运行状态与验收目标，前端只补录入控件。
 */
public record SandboxScenarioCatalogItem(
    String id,
    String serviceLine,
    String engine,
    String playbook,
    String triggerPoint,
    String title,
    String narrative,
    String hostSummary,
    String patientId,
    String encounterId,
    String expectedRuleCode,
    String expectedAction,
    String expectedSeverity,
    String expectedAssetCode,
    String status,
    String statusReason,
    SandboxScenarioInput input
) {
    public static SandboxScenarioCatalogItem from(SandboxScenario scenario) {
        boolean potassium = "sbx-lab-critical-k".equals(scenario.id());
        SandboxScenarioInput input = potassium
            ? SandboxScenarioInput.potassium()
            : SandboxScenarioInput.orchestration();
        return new SandboxScenarioCatalogItem(
            scenario.id(),
            scenario.serviceLine(),
            scenario.engine(),
            scenario.playbook(),
            scenario.triggerPoint(),
            scenario.title(),
            narrativeFor(scenario),
            hostSummaryFor(scenario),
            scenario.patientId(),
            scenario.encounterId(),
            scenario.expectedRuleCode(),
            scenario.expectedAction(),
            scenario.expectedSeverity(),
            scenario.expectedAssetCode(),
            "runtime-check",
            "运行时按当前机构生效版本解析并校验资产；目录不固化机构生效版本",
            input
        );
    }

    private static String narrativeFor(SandboxScenario scenario) {
        if ("sbx-lab-critical-k".equals(scenario.id())) {
            return "急诊检验复核，血清钾达到危急值，需医师确认处置。";
        }
        return scenario.title() + "复用既有引擎主链，并保留每一步真实请求、响应与业务事实。";
    }

    private static String hostSummaryFor(SandboxScenario scenario) {
        if ("sbx-lab-critical-k".equals(scenario.id())) {
            return "沙盘患者 · 男 · 急诊 · 检验结果复核";
        }
        return "院内业务系统编排场景";
    }

    public record SandboxScenarioInput(
        String kind,
        String code,
        String label,
        Double defaultValue,
        Double minValue,
        Double maxValue,
        Double step,
        String unit,
        String referenceRange,
        Double upperReferenceValue,
        String encounterType
    ) {
        static SandboxScenarioInput potassium() {
            return new SandboxScenarioInput(
                "numeric",
                "2823-3",
                "血清钾",
                6.8,
                1.0,
                12.0,
                0.1,
                "mmol/L",
                "3.5-5.5",
                5.5,
                "ED"
            );
        }

        static SandboxScenarioInput orchestration() {
            return new SandboxScenarioInput(
                "orchestration", null, null, null, null, null, null, null, null, null, null);
        }
    }
}
