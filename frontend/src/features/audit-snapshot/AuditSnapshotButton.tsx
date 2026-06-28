import { Button, Tooltip, message } from "antd";
import { ExportOutlined } from "@ant-design/icons";
import { useLocation } from "react-router-dom";
import { useAuditSnapshot, useSecurityProfile } from "@/shared/api/hooks";

/**
 * 全局审计快照按钮（与 docs/CONSTITUTION.md §5 第 7 角色对齐）。
 *
 * <p>只允许拥有 {@code audit.export} 的用户调用审计服务生成可追溯证据；
 * 页面端不自行制造身份、签名或审计上下文。
 */
export function AuditSnapshotButton({ compact = false }: { compact?: boolean }) {
  const location = useLocation();
  const profile = useSecurityProfile();
  const snapshot = useAuditSnapshot();
  const canExport = Boolean(
    profile.data?.permissions.some((permission) => permission.code === "audit.export"),
  );

  function handleClick() {
    if (!canExport) {
      return;
    }
    snapshot.mutate(`page:${location.pathname}`, {
      onSuccess: () => {
        message.success("审计快照已生成，可在审计证据中查看");
      },
      onError: () => {
        message.error("审计快照生成失败，请稍后重试");
      },
    });
  }

  const tooltipTitle = canExport ? "生成当前页审计快照" : "当前角色无审计快照导出权限";

  return (
    <Tooltip title={tooltipTitle}>
      <Button
        type="text"
        icon={<ExportOutlined />}
        aria-label="审计快照"
        disabled={!canExport}
        loading={snapshot.isPending}
        onClick={handleClick}
      >
        {compact ? null : "审计快照"}
      </Button>
    </Tooltip>
  );
}
