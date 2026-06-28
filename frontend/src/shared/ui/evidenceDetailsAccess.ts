import type { SecurityProfile } from "@/shared/api/hooks";

const EVIDENCE_DETAIL_PERMISSIONS = new Set([
  "advanced.read",
  "system.debug",
  "system.read",
  "system.manage",
  "audit.read",
  "asset.read",
  "asset.write",
  "llm.eval.manage",
  "llm.provider.manage",
  "llm.egress.manage",
  "workbench:readiness:view",
]);
const EVIDENCE_DETAIL_MENU_KEYS = new Set([
  "provenance",
  "graph-explore",
  "knowledge-production",
  "knowledge-governance",
  "institution-knowledge",
  "diagnosis-knowledge",
  "terminology-mapping",
  "ai-workflows",
  "implementation-guide",
  "tenant-onboarding",
  "admin-users",
  "identity-bindings",
  "authoring-assets",
  "domestic-check",
  "runtime-diagnostics",
  "security-baseline",
  "clinical-followup",
  "qc-dashboard",
  "qc-alerts",
  "insurance-audit",
  "qc-eval-sets",
  "runtime-releases",
]);

export type EvidenceDetailsProfile = Partial<Pick<SecurityProfile, "permissions" | "menuKeys">>;

export function canUseEvidenceDetails(profile?: EvidenceDetailsProfile) {
  return (
    (profile?.menuKeys ?? []).some((menuKey) => EVIDENCE_DETAIL_MENU_KEYS.has(menuKey)) ||
    (profile?.permissions ?? []).some((permission) =>
      EVIDENCE_DETAIL_PERMISSIONS.has(permission.code),
    )
  );
}
