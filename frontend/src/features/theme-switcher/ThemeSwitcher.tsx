import { Dropdown, Button, Tooltip } from "antd";
import { BgColorsOutlined } from "@ant-design/icons";
import type { MenuProps } from "antd";
import { useEffect, useRef } from "react";
import { useSaveThemePreference, useThemePreference } from "@/shared/api/hooks";
import { isThemeMode, THEME_MODE_OPTIONS } from "@/shared/config/theme";
import { useThemeStore } from "@/shared/lib/themeStore";

/**
 * 主题切换：默认 / 老年医生模式 / 暗黑 / 护眼 / 跟随系统。
 * 与产品宪法的设计 token 和产品体验固定规范保持一致。
 */
export function ThemeSwitcher({
  compact = false,
  syncRemote = true,
}: {
  compact?: boolean;
  syncRemote?: boolean;
}) {
  const { mode, setMode } = useThemeStore();
  const hydratedRemote = useRef(false);
  const themePreference = useThemePreference(syncRemote);
  const saveThemePreference = useSaveThemePreference();

  useEffect(() => {
    if (!syncRemote || hydratedRemote.current || !themePreference.data) return;
    hydratedRemote.current = true;
    setMode(themePreference.data.mode);
  }, [setMode, syncRemote, themePreference.data]);

  const items: MenuProps["items"] = THEME_MODE_OPTIONS.map((option) => ({
    key: option.mode,
    label: option.label,
  }));

  const labelMap = Object.fromEntries(
    THEME_MODE_OPTIONS.map((option) => [option.mode, option.label]),
  ) as Record<typeof mode, string>;

  const currentLabel = labelMap[mode];

  function handleSelect(key: string) {
    if (!isThemeMode(key)) return;
    setMode(key);
    if (syncRemote) {
      void saveThemePreference.mutateAsync(key).catch(() => undefined);
    }
  }

  return (
    <Dropdown
      menu={{
        items,
        selectable: true,
        selectedKeys: [mode],
        onClick: (info) => handleSelect(info.key),
      }}
      placement="bottomRight"
      trigger={["click"]}
    >
      <Button
        type="text"
        aria-label={`主题模式：${currentLabel}`}
        icon={
          <Tooltip title="主题模式">
            <BgColorsOutlined />
          </Tooltip>
        }
      >
        {compact ? null : currentLabel}
      </Button>
    </Dropdown>
  );
}
