import { ConfigProvider } from "antd";
import { fireEvent, render, screen, within } from "@testing-library/react";
import { beforeEach, describe, expect, it, vi } from "vitest";
import SystemProviders from "./SystemProviders";
import { useRuntimeOperations, useSecurityProfile } from "@/shared/api/hooks";
import { useEvidenceDetailsStore } from "@/shared/lib/evidenceDetailsStore";

vi.mock("@/shared/api/hooks", () => ({
  useRuntimeOperations: vi.fn(),
  useSecurityProfile: vi.fn(),
}));

const snapshot = {
  serviceName: "medkernel",
  environment: "container",
  deploymentMode: "docker-core",
  databaseDialect: "postgres",
  migrationLocation: "classpath:db/migration/postgres",
  activeProfiles: ["dev", "container"],
  healthStatus: "UP",
  jvm: {
    javaVersion: "21.0.8",
    javaVendor: "Eclipse Adoptium",
    vmName: "OpenJDK 64-Bit Server VM",
    virtualThreadsEnabled: false,
    availableProcessors: 8,
  },
  os: {
    name: "Linux",
    version: "6.8",
    arch: "amd64",
  },
  featureFlags: [
    {
      key: "graph-projection",
      displayName: "知识图谱投影",
      enabled: false,
      risk: "MEDIUM",
      owner: "信息科 / 架构组",
      description: "控制 Neo4j 图谱投影和图谱查询能力是否参与运行。",
      source: "SAFE_DEFAULT",
      warning: "配置中心读取失败，已使用启动安全默认。",
    },
    {
      key: "dify-workflow",
      displayName: "Dify 工作流",
      enabled: true,
      risk: "MEDIUM",
      owner: "AI 平台组",
      description: "控制 Dify 工作流接入。",
      source: "YML_SEED",
      warning: null,
    },
  ],
  dependencies: [
    {
      key: "database",
      displayName: "关系数据库",
      status: "UP",
      detail: "postgres · classpath:db/migration/postgres",
    },
    {
      key: "backup-restore",
      displayName: "备份恢复",
      status: "UP",
      detail: "隔离恢复演练通过，迁移历史校验正常",
    },
    {
      key: "graph-projection",
      displayName: "知识图谱投影",
      status: "NOT_CONNECTED",
      detail: "能力开关关闭，未连接图谱投影",
    },
    {
      key: "dify-workflow",
      displayName: "Dify 工作流",
      status: "MODEL_DISABLED",
      detail: "能力开关关闭，模型工作流未启用",
    },
  ],
  backup: {
    enabled: true,
    rpo: "24 小时",
    rto: "4 小时",
    backupScript: "./deploy/docker/scripts/backup.sh",
    restoreScript: "./deploy/docker/scripts/restore.sh",
    checksumPolicy: "SHA-256 摘要随备份文件生成，恢复前自动校验",
    drillEvidence: {
      status: "SUCCESS",
      completedAt: "2026-06-06T16:30:00Z",
      migrationCount: 96,
      evidenceReference: "latest-restore-drill.properties",
      detail: "隔离恢复演练通过，迁移历史校验正常",
    },
    source: "SAFE_DEFAULT",
    warning: "备份策略读取失败，已使用启动安全默认。",
  },
  domesticProfile: {
    targetOs: "麒麟 / 统信 / openEuler",
    targetJdk: "KAE-JDK 21 / BiSheng JDK 21",
    databaseVendors: ["达梦", "人大金仓"],
    cryptoAlgorithms: ["SM2", "SM3", "SM4"],
    evidence: "国产化自检、五方言迁移合同、国密算法 smoke",
  },
  domesticCompatibility: {
    overallStatus: "WARN",
    summary: "1 项通过，3 项警告，0 项失败，3 项待现场确认",
    checkedAt: "2026-05-26T04:00:00Z",
    items: [],
  },
  generatedAt: "2026-05-26T04:00:00Z",
};

describe("SystemProviders", () => {
  const runtimeRefetch = vi.fn();

  beforeEach(() => {
    window.localStorage.clear();
    useEvidenceDetailsStore.setState({ enabled: false });
    vi.clearAllMocks();
    vi.mocked(useSecurityProfile).mockReturnValue({
      data: {
        userId: "u-ops",
        username: "platform-admin",
        roles: [{ code: "platform-admin" }],
        permissions: [
          {
            code: "system.read",
            dimension: "ACTION",
            target: "system",
            displayName: "查看系统状态",
            risk: "LOW",
          },
          {
            code: "system.debug",
            dimension: "ACTION",
            target: "system",
            displayName: "查看系统诊断",
            risk: "HIGH",
          },
        ],
        menuKeys: ["system-providers"],
        environmentKeys: ["prod"],
        dataScope: {
          tenantId: "t-1",
          groupId: null,
          hospitalId: null,
          campusId: null,
          siteId: null,
          departmentId: null,
          specialtyId: null,
        },
        mustChangePwd: false,
        mfaRequired: true,
        mfaBound: true,
      },
      isLoading: false,
      isError: false,
      refetch: vi.fn(),
    } as never);
    vi.mocked(useRuntimeOperations).mockReturnValue({
      data: snapshot,
      isLoading: false,
      isError: false,
      isFetching: false,
      refetch: runtimeRefetch,
    } as never);
  });

  it("keeps implementation details out of the default operations view", () => {
    render(
      <ConfigProvider>
        <SystemProviders />
      </ConfigProvider>,
    );

    expect(screen.getByRole("heading", { name: "运行保障" })).toBeInTheDocument();
    expect(screen.getByText("核心服务")).toBeInTheDocument();
    expect(screen.queryByText("整体健康")).not.toBeInTheDocument();
    expect(screen.getByText("2 项依赖需关注")).toBeInTheDocument();
    expect(screen.getAllByText("知识图谱投影").length).toBeGreaterThan(0);
    expect(screen.getAllByText("Dify 工作流").length).toBeGreaterThan(0);
    expect(screen.getAllByText("备份恢复").length).toBeGreaterThan(0);
    expect(screen.getByText("备份策略读取失败，已使用启动安全默认。")).toBeInTheDocument();
    expect(
      screen.getAllByText("SHA-256 摘要随备份文件生成，恢复前自动校验").length,
    ).toBeGreaterThan(0);
    expect(screen.getByText("演练通过")).toBeInTheDocument();
    expect(screen.getByText("迁移校验：96 条")).toBeInTheDocument();
    expect(screen.getByText(/2026/)).toBeInTheDocument();
    expect(screen.getAllByText(/麒麟 \/ 统信 \/ openEuler/).length).toBeGreaterThan(0);
    expect(
      within(screen.getByTestId("runtime-dependencies")).getByText("关系数据库"),
    ).toBeInTheDocument();
    expect(
      within(screen.getByTestId("runtime-dependencies")).getAllByText("正常"),
    ).not.toHaveLength(0);
    expect(
      within(screen.getByTestId("runtime-dependencies")).getByText("未连接"),
    ).toBeInTheDocument();
    expect(
      within(screen.getByTestId("runtime-dependencies")).getByText("模型未启用"),
    ).toBeInTheDocument();
    const dependencyRows = within(screen.getByTestId("runtime-dependencies")).getAllByRole("row");
    expect(within(dependencyRows[1]).getByText("知识图谱投影")).toBeInTheDocument();

    expect(screen.queryByText("Oracle 23ai · 主库")).not.toBeInTheDocument();
    expect(screen.queryByText(/总院 PACS/)).not.toBeInTheDocument();
    expect(screen.queryByText("DISABLED")).not.toBeInTheDocument();
    expect(screen.queryByText("docker-core")).not.toBeInTheDocument();
    expect(screen.queryByText("postgres")).not.toBeInTheDocument();
    expect(screen.queryByText("classpath:db/migration/postgres")).not.toBeInTheDocument();
    expect(screen.queryByText("./deploy/docker/scripts/backup.sh")).not.toBeInTheDocument();
    expect(screen.queryByText("./deploy/docker/scripts/restore.sh")).not.toBeInTheDocument();
    expect(screen.queryByText("SAFE_DEFAULT")).not.toBeInTheDocument();
    expect(screen.queryByText("MEDIUM")).not.toBeInTheDocument();
    expect(screen.queryByText("配置中心读取失败，已使用启动安全默认。")).not.toBeInTheDocument();
    expect(screen.queryByText("latest-restore-drill.properties")).not.toBeInTheDocument();
  });

  it("re-runs the real provider probe from the single page action", () => {
    render(
      <ConfigProvider>
        <SystemProviders />
      </ConfigProvider>,
    );

    fireEvent.click(screen.getByRole("button", { name: "重新探测" }));

    expect(runtimeRefetch).toHaveBeenCalledTimes(1);
  });

  it("does not query or expose operations data without menu and system read permissions", () => {
    vi.mocked(useSecurityProfile).mockReturnValue({
      data: {
        userId: "u-doctor",
        username: "clinical-user",
        roles: [{ code: "clinical-user" }],
        permissions: [],
        menuKeys: [],
      },
      isLoading: false,
      isError: false,
      refetch: vi.fn(),
    } as never);

    render(
      <ConfigProvider>
        <SystemProviders />
      </ConfigProvider>,
    );

    expect(useRuntimeOperations).toHaveBeenLastCalledWith(false);
    expect(screen.getByText("当前权限不足")).toBeInTheDocument();
    expect(screen.queryByText("关系数据库")).not.toBeInTheDocument();
  });

  it("reveals deployment diagnostics only after an authorized operator enables evidence details", () => {
    render(
      <ConfigProvider>
        <SystemProviders />
      </ConfigProvider>,
    );

    fireEvent.click(screen.getByRole("switch", { name: "证据详情" }));

    expect(screen.getByText("docker-core")).toBeInTheDocument();
    expect(screen.getByText("postgres")).toBeInTheDocument();
    expect(screen.getByText("classpath:db/migration/postgres")).toBeInTheDocument();
    expect(screen.getByText("./deploy/docker/scripts/backup.sh")).toBeInTheDocument();
    expect(screen.getByText("./deploy/docker/scripts/restore.sh")).toBeInTheDocument();
    expect(screen.getAllByText("SAFE_DEFAULT").length).toBeGreaterThan(0);
    expect(screen.getAllByText("中风险").length).toBeGreaterThan(0);
    expect(screen.getByText("配置中心读取失败，已使用启动安全默认。")).toBeInTheDocument();
    expect(screen.getByText("latest-restore-drill.properties")).toBeInTheDocument();
  });
});
