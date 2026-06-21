import { describe, expect, it } from "vitest";
import { KNOWN_ROLE_CODES, ROLE_OPTIONS, roleLabel } from "./roleCatalog";

const launchRoles = [
  "platform-governance-admin",
  "platform-knowledge-governor",
  "integration-operator",
  "compliance-auditor",
];

const removedLegacyRoles = [
  "platform-admin",
  "group-admin",
  "hospital-admin",
  "it-ops",
  "medical-affairs",
  "qa-manager",
  "insurance-manager",
  "dept-head",
  "specialist",
  "doctor",
  "nurse",
  "med-technician",
  "pharmacist",
  "audit-compliance",
  "implementation-engineer",
];

describe("roleCatalog", () => {
  it("exposes only the four launch responsibilities", () => {
    expect(ROLE_OPTIONS).toEqual([
      { code: "platform-governance-admin", name: "平台管理员" },
      { code: "platform-knowledge-governor", name: "知识运营员" },
      { code: "integration-operator", name: "接入运维员" },
      { code: "compliance-auditor", name: "审计查看员" },
    ]);
    expect(ROLE_OPTIONS.map((role) => role.code)).toEqual(launchRoles);
  });

  it("keeps compatibility role codes readable without exposing them as options", () => {
    expect(KNOWN_ROLE_CODES).toContain("clinical-decision-user");
    expect(ROLE_OPTIONS.map((role) => role.code)).not.toContain("clinical-decision-user");
    expect(roleLabel("clinical-decision-user")).toBe("临床决策使用者");
  });

  it("does not translate removed legacy roles", () => {
    removedLegacyRoles.forEach((role) => {
      expect(roleLabel(role)).toBe("未识别角色");
    });
  });
});
