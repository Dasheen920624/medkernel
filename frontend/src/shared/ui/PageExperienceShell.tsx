import { Space } from "antd";
import type { ReactNode } from "react";

import type { SecurityProfile } from "@/shared/api/hooks";

import { EvidenceDetailsToggle } from "./EvidenceDetailsToggle";
import { PageShell } from "./PageShell";
import type { PageStateProps } from "./PageState";
import type { PageStateKind } from "./PageState.contract";
import type { RouteExperience } from "./experienceTypes";

interface PageExperienceShellProps {
  meta: { title: string; experience: RouteExperience };
  securityProfile?: Pick<SecurityProfile, "permissions" | "menuKeys">;
  evidenceDetailsEnabled?: boolean;
  onEvidenceDetailsChange?: (enabled: boolean) => void;
  primary?: ReactNode;
  extras?: ReactNode;
  state?: PageStateKind;
  stateProps?: Omit<PageStateProps, "state" | "children">;
  children: ReactNode;
}

export function PageExperienceShell({
  meta,
  securityProfile,
  evidenceDetailsEnabled,
  onEvidenceDetailsChange,
  primary,
  extras,
  state,
  stateProps,
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
      state={state}
      stateProps={stateProps}
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
