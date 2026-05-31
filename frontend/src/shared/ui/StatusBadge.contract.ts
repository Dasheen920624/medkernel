export type ConfigStatus =
  | "draft"
  | "pending_review"
  | "published"
  | "active"
  | "deprecated"
  | "archived";
export type ChangeStatus = "pending" | "canary" | "rolled_out" | "rolled_back";
export type TodoStatus = "unread" | "in_progress" | "done" | "escalated";
export type AlertStatus = "new" | "assigned" | "remediating" | "closed" | "waived";

export type AnyStatus = ConfigStatus | ChangeStatus | TodoStatus | AlertStatus;

export interface StatusMeta {
  label: string;
  color: string;
}

const CONFIG_MAP: Record<ConfigStatus, StatusMeta> = {
  draft: { label: "草稿", color: "default" },
  pending_review: { label: "待审核", color: "warning" },
  published: { label: "已发布", color: "processing" },
  active: { label: "生效中", color: "success" },
  deprecated: { label: "已下线", color: "default" },
  archived: { label: "已归档", color: "default" },
};

const CHANGE_MAP: Record<ChangeStatus, StatusMeta> = {
  pending: { label: "待发布", color: "default" },
  canary: { label: "灰度", color: "warning" },
  rolled_out: { label: "全量", color: "success" },
  rolled_back: { label: "回滚", color: "error" },
};

const TODO_MAP: Record<TodoStatus, StatusMeta> = {
  unread: { label: "未读", color: "blue" },
  in_progress: { label: "处理中", color: "processing" },
  done: { label: "已完成", color: "success" },
  escalated: { label: "已升级", color: "error" },
};

const ALERT_MAP: Record<AlertStatus, StatusMeta> = {
  new: { label: "新建", color: "error" },
  assigned: { label: "已派单", color: "warning" },
  remediating: { label: "整改中", color: "processing" },
  closed: { label: "已闭环", color: "success" },
  waived: { label: "已豁免", color: "default" },
};

export const STATUS_MAPS = {
  config: CONFIG_MAP,
  change: CHANGE_MAP,
  todo: TODO_MAP,
  alert: ALERT_MAP,
} as const;

export type StatusMachine = keyof typeof STATUS_MAPS;

export interface StatusBadgeLookup {
  machine: StatusMachine;
  status: AnyStatus;
}

export function resolveStatusMeta({ machine, status }: StatusBadgeLookup): StatusMeta {
  const map = STATUS_MAPS[machine] as Record<string, StatusMeta>;
  const meta = map[status];
  if (!meta) {
    throw new Error(`未注册状态机状态：${machine}.${String(status)}`);
  }
  return meta;
}

export const STATUS_MACHINES = {
  config: Object.keys(CONFIG_MAP) as ConfigStatus[],
  change: Object.keys(CHANGE_MAP) as ChangeStatus[],
  todo: Object.keys(TODO_MAP) as TodoStatus[],
  alert: Object.keys(ALERT_MAP) as AlertStatus[],
} as const;
