import {
  Alert,
  Button,
  Col,
  Descriptions,
  Row,
  Space,
  Statistic,
  Table,
  Tabs,
  Tag,
  Typography,
} from "antd";
import { ReloadOutlined } from "@ant-design/icons";

import { useRuntimeOperations, useSecurityProfile } from "@/shared/api/hooks";
import type { RuntimeDependencyStatus, SecurityProfile } from "@/shared/api/hooks";
import { PageShell } from "@/shared/ui/PageShell";
import { PageState } from "@/shared/ui/PageState";
import {
  customerEnumLabel,
  permissionDimensionLabel,
  riskLabel,
} from "@/shared/config/customerLabels";
import {
  DataPermissionPanel,
  InteropAssessmentPanel,
  MaskingRulePanel,
  SystemConfigPanel,
} from "./SecurityBaselinePanels";

const STATUS_LABEL: Record<string, string> = {
  UP: "正常",
  DEGRADED: "降级",
  NOT_CONNECTED: "未连接",
  MODEL_DISABLED: "模型未启用",
  DOWN: "异常",
  OUT_OF_SERVICE: "停服",
  UNKNOWN: "未知",
};

type BaselineRow = {
  key: string;
  item: string;
  status: "PASS" | "WARN";
  evidence: string;
};

function dependencyByKey(
  dependencies: RuntimeDependencyStatus[] | undefined,
  key: string,
): RuntimeDependencyStatus | undefined {
  return dependencies?.find((dependency) => dependency.key === key);
}

function dataScopeText(profile: SecurityProfile): string {
  const scope = profile.dataScope;
  return [
    scope.tenantId,
    scope.groupId,
    scope.hospitalId,
    scope.campusId,
    scope.siteId,
    scope.departmentId,
    scope.wardId,
    scope.specialtyId,
  ]
    .filter(Boolean)
    .join(" / ");
}

function mfaEvidence(profile: SecurityProfile): string {
  if (!profile.mfaRequired) return "当前角色未要求 MFA";
  return profile.mfaBound ? "MFA 已绑定" : "MFA 必需但未绑定";
}

function hasPermission(profile: SecurityProfile, code: string) {
  return profile.permissions.some((permission) => permission.code === code);
}

function BaselineOverview({
  profile,
  snapshot,
}: {
  profile: SecurityProfile;
  snapshot: NonNullable<ReturnType<typeof useRuntimeOperations>["data"]>;
}) {
  const highRiskPermissions = profile.permissions.filter(
    (permission) => permission.risk === "HIGH",
  );
  const database = dependencyByKey(snapshot.dependencies, "database");
  const backup = dependencyByKey(snapshot.dependencies, "backup-restore");
  const baselineRows: BaselineRow[] = [
    {
      key: "mfa",
      item: "MFA",
      status: profile.mfaRequired && profile.mfaBound ? "PASS" : "WARN",
      evidence: mfaEvidence(profile),
    },
    {
      key: "password",
      item: "初始口令",
      status: profile.mustChangePwd ? "WARN" : "PASS",
      evidence: profile.mustChangePwd ? "需要完成首次安全设置" : "无需强制改密",
    },
    {
      key: "high-risk",
      item: "高风险权限",
      status: highRiskPermissions.length > 0 ? "WARN" : "PASS",
      evidence:
        highRiskPermissions.length > 0
          ? `${highRiskPermissions.length} 项高风险权限需保持审计`
          : "未持有高风险权限",
    },
    {
      key: "database",
      item: "关系数据库",
      status: database?.status === "UP" ? "PASS" : "WARN",
      evidence: database?.detail ?? "未返回关系数据库依赖状态",
    },
    {
      key: "backup",
      item: "备份恢复",
      status: backup?.status === "UP" ? "PASS" : "WARN",
      evidence: backup?.detail ?? snapshot.backup.checksumPolicy,
    },
  ];

  return (
    <Space direction="vertical" size="large" className="mk-full-width">
      <Row gutter={[16, 16]}>
        <Col xs={24} sm={12} lg={6}>
          <Statistic title="当前账号" value={profile.username} />
        </Col>
        <Col xs={24} sm={12} lg={6}>
          <Statistic title="MFA" value={profile.mfaBound ? "MFA 已绑定" : "未绑定"} />
        </Col>
        <Col xs={24} sm={12} lg={6}>
          <Statistic title="高风险权限" value={highRiskPermissions.length} />
        </Col>
        <Col xs={24} sm={12} lg={6}>
          <Statistic
            title="运行状态"
            value={STATUS_LABEL[snapshot.healthStatus] ?? customerEnumLabel(snapshot.healthStatus)}
          />
        </Col>
      </Row>

      <Alert
        type={profile.mfaRequired && !profile.mfaBound ? "warning" : "success"}
        showIcon
        message={profile.mfaRequired ? "当前角色已纳入 MFA 基线" : "当前角色未强制 MFA"}
        description={`数据范围：${dataScopeText(profile) || "未返回范围"}；运行环境：${snapshot.environment} / ${snapshot.deploymentMode}`}
      />

      <Descriptions bordered size="small" column={{ xs: 1, md: 2 }}>
        <Descriptions.Item label="用户 ID">{profile.userId}</Descriptions.Item>
        <Descriptions.Item label="角色">
          <Space wrap>
            {profile.roles.map((role) => (
              <Tag key={role.code}>{role.displayName}</Tag>
            ))}
          </Space>
        </Descriptions.Item>
        <Descriptions.Item label="菜单权限">{profile.menuKeys.length} 项</Descriptions.Item>
        <Descriptions.Item label="环境权限">
          {profile.environmentKeys.join(" / ") || "未返回"}
        </Descriptions.Item>
      </Descriptions>

      <Table<BaselineRow>
        rowKey="key"
        dataSource={baselineRows}
        pagination={false}
        columns={[
          { title: "项目", dataIndex: "item" },
          {
            title: "状态",
            dataIndex: "status",
            render: (status) => (
              <Tag color={status === "PASS" ? "success" : "warning"}>
                {status === "PASS" ? "通过" : "需复核"}
              </Tag>
            ),
          },
          { title: "证据", dataIndex: "evidence" },
        ]}
      />

      {highRiskPermissions.length > 0 && (
        <Space direction="vertical" size="small" className="mk-full-width">
          <Typography.Title level={5}>高风险权限明细</Typography.Title>
          <Table<(typeof highRiskPermissions)[number]>
            rowKey="code"
            dataSource={highRiskPermissions}
            pagination={false}
            scroll={{ x: "max-content" }}
            columns={[
              { title: "权限", dataIndex: "displayName" },
              {
                title: "维度",
                dataIndex: "dimension",
                render: permissionDimensionLabel,
              },
              { title: "对象", dataIndex: "target" },
              { title: "编码", dataIndex: "code" },
              {
                title: "风险",
                dataIndex: "risk",
                render: (risk) => <Tag color="error">{riskLabel(risk)}</Tag>,
              },
            ]}
          />
        </Space>
      )}
    </Space>
  );
}

export default function SecurityBaseline() {
  const security = useSecurityProfile();
  const runtime = useRuntimeOperations();

  if (security.isLoading || runtime.isLoading) {
    return (
      <PageShell title="安全基线与系统配置" description="正在读取安全画像与运行底座">
        <PageState state="loading" />
      </PageShell>
    );
  }

  if (security.isError || runtime.isError) {
    return (
      <PageShell title="安全基线与系统配置" description="安全基线状态读取失败">
        <PageState
          state="error"
          title="暂时无法读取安全基线"
          description="请稍后重试，或让信息科检查安全画像与运行底座。"
          action={
            <Space wrap>
              <Button icon={<ReloadOutlined />} onClick={() => security.refetch()}>
                重读安全画像
              </Button>
              <Button onClick={() => runtime.refetch()}>重读运行底座</Button>
            </Space>
          }
        />
      </PageShell>
    );
  }

  const profile = security.data;
  const snapshot = runtime.data;
  if (!profile || !snapshot) {
    return (
      <PageShell title="安全基线与系统配置" description="安全基线合同暂无数据">
        <PageState state="empty" title="暂无安全基线状态" />
      </PageShell>
    );
  }

  const canManage = hasPermission(profile, "system.manage");
  return (
    <PageShell
      title="安全基线与系统配置"
      description="统一管理运行配置、数据访问、后端脱敏与互操作测评证据"
    >
      {!canManage && (
        <Alert
          type="info"
          showIcon
          message="当前为只读视图"
          description="只有平台管理员或安全管理员可以修改配置；读取仍按当前服务空间和组织范围隔离。"
        />
      )}
      <Tabs
        defaultActiveKey="overview"
        items={[
          {
            key: "overview",
            label: "基线概览",
            children: <BaselineOverview profile={profile} snapshot={snapshot} />,
          },
          {
            key: "config",
            label: "系统配置",
            children: <SystemConfigPanel canManage={canManage} />,
          },
          {
            key: "data-permission",
            label: "数据权限",
            children: <DataPermissionPanel canManage={canManage} />,
          },
          {
            key: "masking",
            label: "脱敏规则",
            children: <MaskingRulePanel canManage={canManage} />,
          },
          {
            key: "interop",
            label: "互操作测评",
            children: <InteropAssessmentPanel />,
          },
        ]}
      />
    </PageShell>
  );
}
