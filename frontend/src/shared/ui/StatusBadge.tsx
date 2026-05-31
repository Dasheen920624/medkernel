import { Tag } from "antd";
import type { CSSProperties } from "react";
import { resolveStatusMeta } from "./StatusBadge.contract";
import type { AlertStatus, ChangeStatus, ConfigStatus, TodoStatus } from "./StatusBadge.contract";

/**
 * 4 套统一状态机（与 docs/CONSTITUTION.md §3 对齐）。
 *
 * 资产类、变更类、待办类、告警类全平台统一这 4 套，禁止自创。
 */
type StatusBadgeProps =
  | {
      machine: "config";
      status: ConfigStatus;
      style?: CSSProperties;
    }
  | {
      machine: "change";
      status: ChangeStatus;
      style?: CSSProperties;
    }
  | {
      machine: "todo";
      status: TodoStatus;
      style?: CSSProperties;
    }
  | {
      machine: "alert";
      status: AlertStatus;
      style?: CSSProperties;
    };

/**
 * 通用状态徽标。任何 PR 提交新页面，必须用 StatusBadge 显示状态。
 *
 * @example
 *   <StatusBadge machine="config" status="published" />
 *   <StatusBadge machine="change" status="canary" />
 *   <StatusBadge machine="todo" status="in_progress" />
 *   <StatusBadge machine="alert" status="new" />
 */
export function StatusBadge({ machine, status, style }: StatusBadgeProps) {
  const meta = resolveStatusMeta({ machine, status });
  return (
    <Tag color={meta.color} style={style}>
      {meta.label}
    </Tag>
  );
}
