import { Space, Switch, Typography } from "antd";

import { useExpertModeStore } from "@/shared/lib/expertModeStore";

import { canUseExpertMode, type ExpertModeProfile } from "./expertModeAccess";

const { Text } = Typography;

interface ExpertModeToggleProps {
  securityProfile?: ExpertModeProfile;
  expertMode?: boolean;
  onExpertModeChange?: (enabled: boolean) => void;
}

export function ExpertModeToggle({
  securityProfile,
  expertMode,
  onExpertModeChange,
}: ExpertModeToggleProps) {
  const globalExpertMode = useExpertModeStore((state) => state.enabled);
  const setGlobalExpertMode = useExpertModeStore((state) => state.setEnabled);
  if (!canUseExpertMode(securityProfile)) {
    return null;
  }
  const effectiveExpertMode = expertMode ?? globalExpertMode;
  const handleExpertModeChange = onExpertModeChange ?? setGlobalExpertMode;

  return (
    <Space size="small">
      <Text>专家模式</Text>
      <Switch
        aria-label="专家模式"
        checked={effectiveExpertMode}
        onChange={handleExpertModeChange}
      />
    </Space>
  );
}
