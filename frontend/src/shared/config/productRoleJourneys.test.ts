import { readFileSync } from "node:fs";
import { resolve } from "node:path";
import { describe, expect, it } from "vitest";
import { ROLE_OPTIONS } from "./roleCatalog";
import { PRODUCT_ROLE_JOURNEYS } from "./productRoleJourneys";
import { findRouteByPath } from "./routes";

function backendDefaultMenuSnapshots(): Map<string, string[]> {
  const source = readFileSync(
    resolve(
      process.cwd(),
      "../medkernel-backend/src/test/java/com/medkernel/engine/security/DefaultPermissionPolicyTest.java",
    ),
    "utf8",
  );
  const snapshots = new Map<string, string[]>();
  const entryPattern = /"([a-z-]+)", List\.of\(([\s\S]*?)\)(?=,\n\s*"[a-z-]+"|\n\s*\);)/g;
  for (const match of source.matchAll(entryPattern)) {
    const roleCode = match[1];
    const menuKeys = Array.from(match[2].matchAll(/"([^"]+)"/g), (quoted) => quoted[1]);
    snapshots.set(roleCode, menuKeys);
  }
  return snapshots;
}

function reportMenuSnapshots(report: string): Map<string, string[]> {
  const roleNameToCode: Map<string, string> = new Map(
    ROLE_OPTIONS.map((role) => [role.name, role.code]),
  );
  const snapshots = new Map<string, string[]>();
  for (const match of report.matchAll(/^\| ([^|]+) \| `([^`]+)` \|$/gm)) {
    const roleCode = roleNameToCode.get(match[1].trim());
    if (!roleCode) continue;
    snapshots.set(
      roleCode,
      match[2].split(",").map((menuKey) => menuKey.trim()),
    );
  }
  return snapshots;
}

describe("product role journeys", () => {
  it("defines one explicit default workbench journey for all four launch roles", () => {
    expect(PRODUCT_ROLE_JOURNEYS.map((journey) => journey.roleCode)).toEqual(
      ROLE_OPTIONS.map((role) => role.code),
    );

    PRODUCT_ROLE_JOURNEYS.forEach((journey) => {
      expect(journey.title).toBe(`${journey.roleName}工作台`);
      expect(journey.summary).toBeTruthy();
      expect(journey.primaryAction.label).toBeTruthy();
      expect(journey.highFrequencyActions.length).toBeLessThanOrEqual(3);
    });

    expect(
      PRODUCT_ROLE_JOURNEYS.map(({ roleCode, primaryAction }) => [roleCode, primaryAction.path]),
    ).toEqual([
      ["platform-admin", "/admin/users"],
      ["engine-operator", "/knowledge/production"],
      ["clinical-user", "/workflow/todos"],
      ["auditor", "/admin/audit"],
    ]);
    expect(
      PRODUCT_ROLE_JOURNEYS.find((journey) => journey.roleCode === "engine-operator"),
    ).toMatchObject({
      primaryAction: { label: "进入知识生产", path: "/knowledge/production" },
      highFrequencyActions: [
        { label: "知识审核发布中心", path: "/knowledge/governance" },
        { label: "质量问题与整改", path: "/qc/alerts" },
        { label: "来源与血缘", path: "/advanced/provenance" },
      ],
    });
  });

  it("keeps every role task on a customer entry instead of hidden diagnostic routes", () => {
    PRODUCT_ROLE_JOURNEYS.forEach((journey) => {
      const actions = [journey.primaryAction, ...journey.highFrequencyActions];
      expect(new Set(actions.map((action) => action.path)).size).toBe(actions.length);

      actions.forEach((action) => {
        const route = findRouteByPath(action.path);
        expect(route, `${journey.roleCode} 缺少路由 ${action.path}`).toBeDefined();
        expect(route?.placement, `${journey.roleCode} 不得把 ${action.path} 作为默认任务`).toMatch(
          /^(primary|header|profile)$/,
        );
      });
    });
  });

  it("contains no removed domain or customer-facing legacy names", () => {
    const serialized = JSON.stringify(PRODUCT_ROLE_JOURNEYS);
    const retiredStandaloneToolLabel = "高级" + "工具";

    [
      "试点准备",
      "临床运行",
      "质控改进",
      "合规运维",
      retiredStandaloneToolLabel,
      "配置" + "包中心",
      "字典映射",
      "通知中心",
      "院级质控驾驶舱",
      "质控预警",
      "评估结果",
      "审计日志",
      "运行状态",
      "生成与发布知识",
      "知识审核与发布",
      "诊断知识" + "维护",
      "发布治理",
      "规则配置",
      "路径配置",
      "模型服务",
      "运行核查",
    ].forEach((legacyLabel) => expect(serialized).not.toContain(legacyLabel));
  });

  it("keeps the reviewable role journey report synchronized", () => {
    const report = readFileSync(
      resolve(process.cwd(), "../docs/audit/product-role-journeys.md"),
      "utf8",
    );

    PRODUCT_ROLE_JOURNEYS.forEach((journey) => {
      expect(report).toContain(`<!-- role:${journey.roleCode} -->`);
      expect(report).toContain(journey.title);
      expect(report).toContain(
        `\`${journey.primaryAction.label}\` → \`${journey.primaryAction.path}\``,
      );
    });
  });

  it("keeps the complete menu snapshots synchronized with the backend permission policy", () => {
    const report = readFileSync(
      resolve(process.cwd(), "../docs/audit/product-role-journeys.md"),
      "utf8",
    );
    const reportSnapshots = reportMenuSnapshots(report);
    const backendSnapshots = backendDefaultMenuSnapshots();

    expect(reportSnapshots).toEqual(backendSnapshots);
  });

  it("keeps the role journey E2E gate strict about browser and server errors", () => {
    const e2eSource = readFileSync(
      resolve(process.cwd(), "e2e/product-role-journeys.spec.ts"),
      "utf8",
    );

    expect(e2eSource).toContain("collectBrowserErrors(page)");
    expect(e2eSource).toContain("collectServerErrors(page)");
    expect(e2eSource).toContain("collectNetworkFailures(page)");
    expect(e2eSource).toContain("不应产生浏览器错误");
    expect(e2eSource).toContain("不应产生 HTTP 错误");
    expect(e2eSource).toContain("不应产生网络失败");
    expect(e2eSource).toContain('failure?.errorText === "net::ERR_ABORTED"');
  });

  it("refreshes the frontend document between E2E role switches", () => {
    const authSource = readFileSync(resolve(process.cwd(), "e2e/support/auth.ts"), "utf8");

    expect(authSource).toContain("resetRoleSession(page)");
    expect(authSource).toContain("/auth/logout");
    expect(authSource).toContain("reloadFrontendSession(page, role)");
    expect(authSource).toContain("e2e-session-refresh");
  });

  it("E2E reads canonical platform and institution account contracts only", () => {
    const authSource = readFileSync(resolve(process.cwd(), "e2e/support/auth.ts"), "utf8");

    expect(authSource).toContain("source.platform.accounts");
    expect(authSource).toContain("source.rehearsal.accounts");
    expect(authSource).toContain('const defaultCredentialScope: RoleCredentialScope = "rehearsal"');
    expect(authSource).toContain('source.schemaVersion !== "1.0.0"');
    expect(authSource).toContain("if (!change.ok() && !credentialsConfigured)");
    expect(authSource).not.toContain("source.roleAccounts");
    expect(authSource).not.toContain("source.platformRoleAccounts");
    expect(authSource).not.toContain("source.customerTenant");
  });

  it("上线 E2E 使用外部部署并把全部证据写到仓库外运行目录", () => {
    const configSource = readFileSync(resolve(process.cwd(), "playwright.config.ts"), "utf8");
    const embedHostSource = readFileSync(
      resolve(process.cwd(), "e2e/support/embed-business-host-server.mjs"),
      "utf8",
    );

    expect(configSource).toContain("E2E_EXTERNAL_DEPLOYMENT");
    expect(configSource).toContain("E2E_EVIDENCE_DIR");
    expect(configSource).toContain("E2E_IGNORE_HTTPS_ERRORS");
    expect(configSource).toContain("MEDKERNEL_PLAYWRIGHT_CHROMIUM_EXECUTABLE");
    expect(configSource).toContain("MEDKERNEL_PLAYWRIGHT_NO_SANDBOX");
    expect(configSource).toContain("outputDir:");
    expect(configSource).toContain("assertOutsideRepository");
    expect(embedHostSource).toContain("process.env.E2E_BASE_URL");
    expect(embedHostSource).not.toContain('const embedOrigin = "http://localhost:5173"');
  });

  it("本地 E2E 默认代理与后端默认端口保持一致", () => {
    const configSource = readFileSync(resolve(process.cwd(), "playwright.config.ts"), "utf8");

    expect(configSource).toContain("'http://localhost:18080'");
    expect(configSource).not.toContain("127.0.0.1:18081");
  });
});
