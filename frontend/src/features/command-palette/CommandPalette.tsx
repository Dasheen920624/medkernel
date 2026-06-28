import { useEffect, useState, useMemo } from "react";
import { Modal, Input, List, Typography, Tag } from "antd";
import { useNavigate } from "react-router-dom";
import { menuSections } from "@/shared/config/menu";
import type { MenuSection } from "@/shared/config/menu";

/**
 * 全局命令面板。
 *
 * 只接收当前权限画像允许的菜单项，避免在搜索入口暴露未授权页面。
 */

interface CommandPaletteProps {
  open: boolean;
  onClose: () => void;
  sections?: MenuSection[];
}

interface CommandItem {
  key: string;
  label: string;
  group: string;
  path: string;
}

export function CommandPalette({ open, onClose, sections = menuSections }: CommandPaletteProps) {
  const [q, setQ] = useState("");
  const navigate = useNavigate();

  const allCommands: CommandItem[] = useMemo(
    () =>
      sections.flatMap((s) =>
        s.items.map((it) => ({
          key: it.key,
          label: it.label,
          group: s.label,
          path: it.path,
        })),
      ),
    [sections],
  );

  const filtered = useMemo(() => {
    const lc = q.trim().toLowerCase();
    if (!lc) return allCommands.slice(0, 12);
    return allCommands.filter(
      (c) => c.label.toLowerCase().includes(lc) || c.group.toLowerCase().includes(lc),
    );
  }, [q, allCommands]);

  useEffect(() => {
    if (!open) setQ("");
  }, [open]);

  return (
    <Modal
      open={open}
      onCancel={onClose}
      footer={null}
      title="命令面板"
      width={680}
      destroyOnClose
    >
      <Input.Search
        placeholder="搜索菜单"
        value={q}
        onChange={(e) => setQ(e.target.value)}
        autoFocus
      />
      <List
        size="small"
        className="mk-command-results"
        dataSource={filtered}
        locale={{ emptyText: "无匹配" }}
        renderItem={(item) => (
          <List.Item
            className="mk-clickable"
            onClick={() => {
              navigate(item.path);
              onClose();
            }}
          >
            <Tag color="default">{item.group}</Tag>
            <Typography.Text>{item.label}</Typography.Text>
          </List.Item>
        )}
      />
    </Modal>
  );
}
