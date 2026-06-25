import { Space, Switch, Typography } from "antd";

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
      <Text>高级信息</Text>
      <Switch
        aria-label="高级信息"
        checked={effectiveEvidenceDetails}
        onChange={handleEvidenceDetailsChange}
      />
    </Space>
  );
}
