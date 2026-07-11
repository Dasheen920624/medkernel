import { Space, Tag, Typography } from "antd";
import type { ReactNode } from "react";

import type { SecurityProfile } from "@/shared/api/hooks";

import { EvidenceDetailsToggle } from "./EvidenceDetailsToggle";
import { PageShell } from "./PageShell";
import type { PageStateProps } from "./PageState";
import type { PageStateKind } from "./PageState.contract";
import type { RouteExperience } from "./experienceTypes";

const { Text } = Typography;

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
  const stakeholderViews = meta.experience.stakeholderViews ?? [];
  const stakeholderSummary =
    stakeholderViews.length > 0 ? (
      <section aria-label="角色视角">
        <Space direction="vertical" size="small" className="mk-full-width">
          <Text strong>角色视角</Text>
          <Space wrap>
            {stakeholderViews.map((view) => (
              <Space key={`${view.role}-${view.responsibility}`} wrap size="small">
                <Tag color="blue">{view.role}</Tag>
                <Text>{view.responsibility}</Text>
                <Text type="secondary">{view.boundary}</Text>
              </Space>
            ))}
          </Space>
        </Space>
      </section>
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
      {stakeholderSummary ? (
        <Space direction="vertical" size="middle" className="mk-full-width">
          {stakeholderSummary}
          {children}
        </Space>
      ) : (
        children
      )}
    </PageShell>
  );
}
