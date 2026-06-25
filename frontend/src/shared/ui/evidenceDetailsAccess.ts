import type { SecurityProfile } from "@/shared/api/hooks";

const EVIDENCE_DETAIL_PERMISSIONS = new Set(["advanced.read", "system.debug", "llm.eval.manage"]);
const EVIDENCE_DETAIL_MENU_KEYS = new Set([
  "provenance",
  "graph-explore",
  "knowledge-production",
  "ai-workflows",
  "domestic-check",
  "dev-console",
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
