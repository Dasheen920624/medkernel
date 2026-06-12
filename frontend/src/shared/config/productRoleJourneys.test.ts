import { readFileSync } from "node:fs";
import { resolve } from "node:path";
import { describe, expect, it } from "vitest";
import { ROLE_OPTIONS } from "./roleCatalog";
import { PRODUCT_ROLE_JOURNEYS } from "./productRoleJourneys";
import { findRouteByPath } from "./routes";

describe("product role journeys", () => {
  it("defines one explicit default workbench journey for all 14 customer roles", () => {
    expect(PRODUCT_ROLE_JOURNEYS.map((journey) => journey.roleCode)).toEqual(
      ROLE_OPTIONS.map((role) => role.code),
    );

    PRODUCT_ROLE_JOURNEYS.forEach((journey) => {
      expect(journey.title).toBe(`${journey.roleName}工作台`);
      expect(journey.summary).toBeTruthy();
      expect(journey.primaryAction.label).toBeTruthy();
      expect(journey.highFrequencyActions.length).toBeLessThanOrEqual(3);
    });
  });

  it("keeps every role task on a customer entry instead of hidden or expert routes", () => {
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

    [
      "试点准备",
      "临床运行",
      "质控改进",
      "合规运维",
      "高级工具",
      "配置包中心",
      "字典映射",
      "通知中心",
      "院级质控驾驶舱",
      "质控预警",
      "评估结果",
      "审计日志",
      "运行状态",
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
});
