import { readFileSync } from "node:fs";
import { resolve } from "node:path";

import { describe, expect, it } from "vitest";

const sourceFiles = [
  "src/pages/tenant/RuleDefinitions.tsx",
  "src/pages/tenant/PathwayTemplates.tsx",
  "src/pages/tenant/ImplementationGuide.tsx",
  "src/pages/tenant/AdapterHub.tsx",
  "src/pages/clinical/RuleValidate.tsx",
  "src/pages/clinical/PatientPathways.tsx",
  "src/pages/clinical/CdssFatigue.tsx",
  "src/pages/quality/QcAlerts.tsx",
];

const forbiddenBusinessExamples = [
  "DRUG-CODE",
  "DX-CODE",
  "P-1001",
  "PT-CAP-01",
  "PKG-COP-001",
  "J44",
  "张三",
  "强力阿司匹林",
  "低分子肝素",
  "吸氧",
  "老年患者",
  "社区获得性",
  "抗感染化疗",
  "TRACE-RULE",
];

const forbiddenBypassLanguage = [
  "规避门禁",
  "防止 ESLint",
  "AST 扫描",
  "模拟传入",
  "本地列表展现",
  "后端未返回患者路径实体，列表保持不变",
];

function readSource(file: string) {
  return readFileSync(resolve(process.cwd(), file), "utf8");
}

describe("BASE-09 rule and pathway page cleanliness", () => {
  it("does not keep hard-coded clinical examples or bypass-language in production pages", () => {
    const combinedSource = sourceFiles.map(readSource).join("\n");

    for (const forbidden of [...forbiddenBusinessExamples, ...forbiddenBypassLanguage]) {
      expect(combinedSource).not.toContain(forbidden);
    }
  });

  it("does not seed patient pathway table with local fake rows", () => {
    const patientPathwaysSource = readSource("src/pages/clinical/PatientPathways.tsx");

    expect(patientPathwaysSource).not.toMatch(/useState<PatientPathway\[\]>\(\s*\[\s*\{/);
    expect(patientPathwaysSource).not.toContain("Date.now()");
    expect(patientPathwaysSource).not.toMatch(/\|\|\s*\(\s*<Option/);
  });

  it("uses the current rule and pathway customer API roots", () => {
    const hooksSource = readSource("src/shared/api/hooks.ts");

    expect(hooksSource).not.toContain('"/engine/rules');
    expect(hooksSource).not.toContain("`/engine/rules");
    expect(hooksSource).not.toContain('"/engine/pathways');
    expect(hooksSource).not.toContain("`/engine/pathways");
    expect(hooksSource).toContain("/engine/rule/rules");
    expect(hooksSource).toContain("/engine/pathway/pathway-templates");
    expect(hooksSource).toContain("/engine/pathway/patient-pathways");
  });

  it("uses the engine tenant API roots for onboarding service package hooks", () => {
    const hooksSource = readSource("src/shared/api/hooks.ts");

    expect(hooksSource).not.toContain("/platform/branding");
    expect(hooksSource).not.toContain("/platform/success/lifecycle");
    expect(hooksSource).toContain("/engine/tenant/branding");
    expect(hooksSource).toContain("/engine/tenant/success-plan");
    expect(hooksSource).toContain("/engine/tenant/onboarding-readiness");
    expect(hooksSource).toContain("/engine/org/org-units");
    expect(hooksSource).not.toContain('"/tenant/org-units"');
  });

  it("uses the engine MPI API roots for SVC-PILOT-02 hooks", () => {
    const hooksSource = readSource("src/shared/api/hooks.ts");

    expect(hooksSource).not.toContain("/clinical/mpi");
    expect(hooksSource).toContain("/api/v1/engine/mpi/patients");
    expect(hooksSource).toContain("/api/v1/engine/mpi/stats");
  });

  it("does not keep pathway hard-coded topology defaults or paste-style snapshot simulation", () => {
    const pathwaySource = readSource("src/pages/tenant/PathwayTemplates.tsx");

    expect(pathwaySource).not.toContain("DEFAULT_NODES_JSON");
    expect(pathwaySource).not.toContain("DEFAULT_EDGES_JSON");
    expect(pathwaySource).not.toContain("Tabs.TabPane");
    expect(pathwaySource).not.toContain("真实脱敏路径上下文快照 JSON");
    expect(pathwaySource).not.toContain("粘贴由上下文快照接口返回");
  });

  it("does not keep fake tenant branding defaults in onboarding page", () => {
    const onboardingSource = readSource("src/pages/tenant/TenantOnboarding.tsx");

    expect(onboardingSource).not.toContain("MedKernel 智能示范医院");
    expect(onboardingSource).not.toContain("http://assets");
    expect(onboardingSource).not.toContain("Tabs.TabPane");
    expect(onboardingSource).not.toContain("sandbox");
    expect(onboardingSource).toContain("未配置医院名称");
  });

  it("keeps tenant onboarding aligned with the seven-layer organization model", () => {
    const onboardingSource = readSource("src/pages/tenant/TenantOnboarding.tsx");

    for (const level of [
      "TENANT",
      "GROUP",
      "HOSPITAL",
      "CAMPUS",
      "SITE",
      "DEPARTMENT",
      "SPECIALTY",
    ]) {
      expect(onboardingSource).toContain(`value="${level}"`);
    }
    expect(onboardingSource).toContain("parentLevelByChildLevel");
  });

  it("keeps config package center free of legacy decorative layout and wired to StepFlow", () => {
    const configPackagesSource = readSource("src/pages/tenant/ConfigPackages.tsx");

    expect(configPackagesSource).toContain("StepFlow");
    expect(configPackagesSource).not.toContain("bg-gradient-to-br");
    expect(configPackagesSource).not.toContain("rounded-2xl");
    expect(configPackagesSource).not.toContain("text-slate-");
    expect(configPackagesSource).not.toContain("style=");
    expect(configPackagesSource).not.toContain("font-normal font");
    expect(configPackagesSource).not.toContain("一键创建知识配置包草稿");
    expect(configPackagesSource).toContain("width: 260");
    expect(configPackagesSource).toContain("className={styles.nowrap}");
  });

  it("keeps terminology mapping wired to real API-04 safety flows instead of read-only samples", () => {
    const terminologySource = readSource("src/pages/tenant/TerminologyMapping.tsx");

    expect(terminologySource).toContain("StepFlow");
    expect(terminologySource).toContain("useStandardTerms");
    expect(terminologySource).toContain("useLocalTerms");
    expect(terminologySource).toContain("useTerminologyCandidates");
    expect(terminologySource).toContain("useTerminologyConflicts");
    expect(terminologySource).toContain("usePublishTerminologyPackage");
    expect(terminologySource).toContain("useRollbackTerminologyPackage");
    expect(terminologySource).not.toContain("read-only");
    expect(terminologySource).not.toContain("experience sample");
    expect(terminologySource).not.toContain("style=");
  });
});
