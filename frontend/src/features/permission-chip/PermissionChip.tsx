import { Tag, Popover, Typography, Space } from "antd";
import { SafetyOutlined } from "@ant-design/icons";
import { useSecurityProfile, type SecurityProfile } from "@/shared/api/hooks";

const { Text } = Typography;

/**
 * 权限指纹 chip。
 *
 * 任何页面右上角显示当前用户"能改什么 / 能看什么"，减少 90% 客户工单。
 *
 */
export function PermissionChip() {
  const { data: profile, isError } = useSecurityProfile();
  let role = "权限读取中";
  if (profile) {
    role = profile.roles.map((item) => item.displayName).join(" / ") || "未分配角色";
  } else if (isError) {
    role = "权限读取失败";
  }

  const content = (
    <Space direction="vertical" size="middle" className="mk-popover-compact">
      <Text>
        当前角色：<Tag color="blue">{role}</Tag>
      </Text>
      {profile ? (
        <PermissionDetails profile={profile} />
      ) : (
        <Text type="secondary" className="mk-text-xs">
          {isError ? "暂时无法读取授权信息。" : "正在读取授权信息..."}
        </Text>
      )}
    </Space>
  );

  return (
    <Popover content={content} trigger="click" placement="bottomRight" title="我的权限指纹">
      <Tag icon={<SafetyOutlined />} color="blue" className="mk-clickable">
        {role}
      </Tag>
    </Popover>
  );
}

function PermissionDetails({ profile }: { profile: SecurityProfile }) {
  const actionPermissions = profile.permissions.filter(
    (permission) => permission.dimension === "ACTION",
  );
  const scopePermissions = profile.permissions.filter(
    (permission) => permission.dimension !== "ACTION",
  );
  const readable = actionPermissions.filter((permission) => permission.risk === "LOW");
  const changeable = actionPermissions.filter((permission) => permission.risk === "MEDIUM");
  const highRisk = actionPermissions.filter((permission) => permission.risk === "HIGH");
  const declaredEnvironmentKeys = profile.environmentKeys ?? [];
  const environmentKeys =
    declaredEnvironmentKeys.length > 0
      ? declaredEnvironmentKeys
      : profile.permissions
          .filter((permission) => permission.dimension === "ENVIRONMENT")
          .map((permission) => permission.target);

  return (
    <>
      {readable.length > 0 && (
        <PermSection
          title="可查看"
          items={readable.map((item) => item.displayName)}
          color="default"
        />
      )}
      {changeable.length > 0 && (
        <PermSection
          title="可变更"
          items={changeable.map((item) => item.displayName)}
          color="processing"
        />
      )}
      {highRisk.length > 0 && (
        <PermSection
          title="高风险操作"
          items={highRisk.map((item) => item.displayName)}
          color="warning"
        />
      )}
      {scopePermissions.length > 0 && (
        <PermSection
          title="菜单与范围"
          items={scopePermissions.map((item) => item.displayName)}
          color="geekblue"
        />
      )}
      <Text type="secondary" className="mk-text-xs">
        可用环境：{formatEnvironmentKeys(environmentKeys)}
      </Text>
      <Text type="secondary" className="mk-text-xs">
        数据范围：{formatDataScope(profile.dataScope)}；权限由合规运维统一配置。
      </Text>
    </>
  );
}

function formatEnvironmentKeys(environmentKeys: string[]) {
  if (environmentKeys.length === 0) {
    return "未授权环境";
  }
  return environmentKeys.map(formatEnvironmentKey).join(" / ");
}

function formatEnvironmentKey(environmentKey: string) {
  const labels: Record<string, string> = {
    test: "测试环境",
    trial: "试运行环境",
    production: "正式环境",
    emergency: "应急环境",
  };
  return labels[environmentKey] ?? environmentKey;
}

function formatDataScope(dataScope: SecurityProfile["dataScope"]) {
  if (dataScope.departmentId) {
    return `科室 ${dataScope.departmentId}`;
  }
  if (dataScope.hospitalId) {
    return `医院 ${dataScope.hospitalId}`;
  }
  if (dataScope.tenantId) {
    return `租户 ${dataScope.tenantId}`;
  }
  return "当前组织";
}

function PermSection({ title, items, color }: { title: string; items: string[]; color: string }) {
  return (
    <div>
      <Text strong className="mk-text-xs">
        {title}
      </Text>
      <div className="mk-tag-wrap">
        {items.map((item) => (
          <Tag key={item} color={color}>
            {item}
          </Tag>
        ))}
      </div>
    </div>
  );
}
