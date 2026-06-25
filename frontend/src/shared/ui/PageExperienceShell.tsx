import { Space } from "antd";
import type { ReactNode } from "react";

import type { SecurityProfile } from "@/shared/api/hooks";

import { EvidenceDetailsToggle } from "./EvidenceDetailsToggle";
import { PageShell } from "./PageShell";
import type { RouteExperience } from "./experienceTypes";

interface PageExperienceShellProps {
  meta: { title: string; experience: RouteExperience };
  securityProfile?: Pick<SecurityProfile, "permissions" | "menuKeys">;
  evidenceDetailsEnabled?: boolean;
  onEvidenceDetailsChange?: (enabled: boolean) => void;
  primary?: ReactNode;
  extras?: ReactNode;
  children: ReactNode;
}

export function PageExperienceShell({
  meta,
  securityProfile,
  evidenceDetailsEnabled,
  onEvidenceDetailsChange,
  primary,
  extras,
  children,
}: PageExperienceShellProps) {
  const evidenceDetailsControl =
    meta.experience.evidenceDetailContent.length > 0 ? (
      <EvidenceDetailsToggle
        securityProfile={securityProfile}
        evidenceDetailsEnabled={evidenceDetailsEnabled}
        onEvidenceDetailsChange={onEvidenceDetailsChange}
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
          {evidenceDetailsControl}
        </Space>
      }
    >
      {children}
    </PageShell>
  );
}
