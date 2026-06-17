import type { SecurityProfile } from "@/shared/api/hooks";

const EXPERT_PERMISSIONS = new Set(["advanced.read", "system.debug"]);
const EXPERT_MENU_KEYS = new Set([
  "provenance",
  "graph-explore",
  "knowledge-production",
  "ai-workflows",
  "domestic-check",
  "dev-console",
]);

export type ExpertModeProfile = Partial<Pick<SecurityProfile, "permissions" | "menuKeys">>;

export function canUseExpertMode(profile?: ExpertModeProfile) {
  return (
    (profile?.menuKeys ?? []).some((menuKey) => EXPERT_MENU_KEYS.has(menuKey)) ||
    (profile?.permissions ?? []).some((permission) => EXPERT_PERMISSIONS.has(permission.code))
  );
}
