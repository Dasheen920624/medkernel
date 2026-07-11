import { Space, Switch, Tooltip, Typography } from "antd";

import { useEvidenceDetailsStore } from "@/shared/lib/evidenceDetailsStore";

import { canUseEvidenceDetails, type EvidenceDetailsProfile } from "./evidenceDetailsAccess";

const { Text } = Typography;

interface EvidenceDetailsToggleProps {
  securityProfile?: EvidenceDetailsProfile;
  evidenceDetailsEnabled?: boolean;
  onEvidenceDetailsChange?: (enabled: boolean) => void;
}

export function EvidenceDetailsToggle({
  securityProfile,
  evidenceDetailsEnabled,
  onEvidenceDetailsChange,
}: EvidenceDetailsToggleProps) {
  const globalEvidenceDetails = useEvidenceDetailsStore((state) => state.enabled);
  const setGlobalEvidenceDetails = useEvidenceDetailsStore((state) => state.setEnabled);
  if (!canUseEvidenceDetails(securityProfile)) {
    return null;
  }
  const effectiveEvidenceDetails = evidenceDetailsEnabled ?? globalEvidenceDetails;
  const handleEvidenceDetailsChange = onEvidenceDetailsChange ?? setGlobalEvidenceDetails;

  return (
    <Space size="small">
      <Tooltip title="展开审计追溯、原始标识和受控诊断字段">
        <Text>追溯证据</Text>
      </Tooltip>
      <Switch
        aria-label="证据详情"
        checked={effectiveEvidenceDetails}
        onChange={handleEvidenceDetailsChange}
      />
    </Space>
  );
}
