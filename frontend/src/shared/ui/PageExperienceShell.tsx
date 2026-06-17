import { Space, Switch, Typography } from "antd";
import type { ReactNode } from "react";

import type { SecurityProfile } from "@/shared/api/hooks";

import { PageShell } from "./PageShell";
import type { RouteExperience } from "./experienceTypes";

const { Text } = Typography;
const EXPERT_PERMISSIONS = new Set(["advanced.read", "system.debug"]);
const EXPERT_MENU_KEYS = new Set([
  "provenance",
  "graph-explore",
  "knowledge-production",
  "ai-workflows",
  "domestic-check",
  "dev-console",
]);

interface PageExperienceShellProps {
  meta: { title: string; experience: RouteExperience };
  securityProfile?: Pick<SecurityProfile, "permissions" | "menuKeys">;
  expertMode?: boolean;
  onExpertModeChange?: (enabled: boolean) => void;
  primary?: ReactNode;
  extras?: ReactNode;
  children: ReactNode;
}

export function PageExperienceShell({
  meta,
  securityProfile,
  expertMode = false,
  onExpertModeChange,
  primary,
  extras,
  children,
}: PageExperienceShellProps) {
  const mayUseExpertMode =
    meta.experience.expertContent.length > 0 &&
    !!securityProfile &&
    (securityProfile.menuKeys.some((menuKey) => EXPERT_MENU_KEYS.has(menuKey)) ||
      securityProfile.permissions.some((permission) => EXPERT_PERMISSIONS.has(permission.code)));

  const expertControl = mayUseExpertMode ? (
    <Space size="small">
      <Text>专家模式</Text>
      <Switch
        aria-label="专家模式"
        checked={expertMode}
        onChange={(checked) => onExpertModeChange?.(checked)}
      />
    </Space>
  ) : null;

  return (
    <PageShell
      title={meta.title}
      description={meta.experience.goal}
      primary={primary}
      extras={
        <Space wrap>
          {extras}
          {expertControl}
        </Space>
      }
    >
      {children}
    </PageShell>
  );
}
