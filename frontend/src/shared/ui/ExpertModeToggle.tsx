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
      <Text>高级信息</Text>
      <Switch
        aria-label="高级信息"
        checked={effectiveExpertMode}
        onChange={handleExpertModeChange}
      />
    </Space>
  );
}
