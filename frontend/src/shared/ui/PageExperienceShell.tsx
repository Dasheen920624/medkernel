import { Space } from "antd";
import type { ReactNode } from "react";

import type { SecurityProfile } from "@/shared/api/hooks";

import { ExpertModeToggle } from "./ExpertModeToggle";
import { PageShell } from "./PageShell";
import type { RouteExperience } from "./experienceTypes";

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
  expertMode,
  onExpertModeChange,
  primary,
  extras,
  children,
}: PageExperienceShellProps) {
  const expertControl =
    meta.experience.expertContent.length > 0 ? (
      <ExpertModeToggle
        securityProfile={securityProfile}
        expertMode={expertMode}
        onExpertModeChange={onExpertModeChange}
      />
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
