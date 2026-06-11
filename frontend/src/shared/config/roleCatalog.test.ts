import { describe, expect, it } from "vitest";
import { ROLE_OPTIONS, roleLabel } from "./roleCatalog";

const currentRoles = [
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
  it("exposes only the current responsibility role system", () => {
    expect(ROLE_OPTIONS.map((role) => role.code)).toEqual(currentRoles);
  });

  it("does not translate removed legacy roles", () => {
    removedLegacyRoles.forEach((role) => {
      expect(roleLabel(role)).toBe("未识别角色");
    });
  });
});
