import type { SecurityProfile } from "@/shared/api/hooks";

const EVIDENCE_DETAIL_PERMISSIONS = new Set([
  "advanced.read",
  "system.debug",
  "system.read",
  "system.manage",
  "audit.read",
  "llm.eval.manage",
  "llm.provider.manage",
  "llm.egress.manage",
]);
const EVIDENCE_DETAIL_MENU_KEYS = new Set([
  "provenance",
  "graph-explore",
  "knowledge-production",
  "ai-workflows",
  "domestic-check",
  "runtime-diagnostics",
  "security-baseline",
  "clinical-followup",
  "qc-dashboard",
  "qc-alerts",
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
