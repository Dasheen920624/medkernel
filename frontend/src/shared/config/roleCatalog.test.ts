import { describe, expect, it } from "vitest";
import { KNOWN_ROLE_CODES, ROLE_OPTIONS, roleLabel } from "./roleCatalog";

const launchRoles = ["platform-admin", "engine-operator", "clinical-user", "auditor"];

const retiredRoleCodes = [
  "platform-governance-admin",
  "platform-knowledge-governor",
  "organization-admin",
  "identity-access-admin",
  "knowledge-governor",
  "clinical-governor",
  "clinical-decision-user",
  "nursing-collaborator",
  "medication-safety-user",
  "diagnostic-service-user",
  "quality-governor",
  "compliance-auditor",
  "integration-operator",
  "implementation-operator",
];

describe("roleCatalog", () => {
  it("exposes only the four launch responsibilities", () => {
    expect(ROLE_OPTIONS).toEqual([
      { code: "platform-admin", name: "平台管理员" },
      { code: "engine-operator", name: "医疗引擎运营员" },
      { code: "clinical-user", name: "临床使用者" },
      { code: "auditor", name: "审计员" },
    ]);
    expect(ROLE_OPTIONS.map((role) => role.code)).toEqual(launchRoles);
    expect(KNOWN_ROLE_CODES).toEqual(launchRoles);
  });

  it("normalizes active Spring authorities without maintaining aliases", () => {
    expect(roleLabel("ROLE_ENGINE_OPERATOR")).toBe("医疗引擎运营员");
  });

  it("does not translate retired role codes", () => {
    retiredRoleCodes.forEach((role) => {
      expect(roleLabel(role)).toBe("角色待确认");
    });
  });
});
