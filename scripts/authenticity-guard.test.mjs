import assert from "node:assert/strict";
import { execFileSync } from "node:child_process";
import { mkdir, mkdtemp, rm, writeFile } from "node:fs/promises";
import { tmpdir } from "node:os";
import { dirname, join } from "node:path";
import test from "node:test";

import { hasBlockingViolations, scanFiles } from "./authenticity-guard.mjs";

async function withFixture(files, run) {
  const root = await mkdtemp(join(tmpdir(), "medkernel-auth-guard-"));
  try {
    for (const [file, content] of Object.entries(files)) {
      const fullPath = join(root, file);
      await mkdir(dirname(fullPath), { recursive: true });
      await writeFile(fullPath, content, "utf8");
    }
    return await run(root);
  } finally {
    await rm(root, { recursive: true, force: true });
  }
}

function ruleIds(report) {
  return report.violations.map((violation) => violation.ruleId).sort();
}

test("前端页面触碰文件会阻断绕门禁、伪数据、医学硬编码和技术对象裸露", async () => {
  await withFixture(
    {
      "frontend/src/pages/BadPage.tsx": `
        /* eslint-disable medkernel/no-page-mock */
        import MockAdapter from "axios-mock-adapter";
        export function BadPage() {
          try {
            throw new Error("fail");
          } catch {
            message.success("仿真模式成功");
          }
          const traceId = "TRACE-" + Math.floor(Math.random() * 1000);
          const hash = "SHA-256-MOCK-HASH";
          return <pre className="font-mono">{JSON.stringify({ disease: "高血压", traceId, hash })}</pre>;
        }
      `,
    },
    async (root) => {
      const report = await scanFiles(root, ["frontend/src/pages/BadPage.tsx"]);

      assert.equal(hasBlockingViolations(report), true);
      assert.deepEqual(ruleIds(report), [
        "frontend.catch-success",
        "frontend.fake-hash",
        "frontend.hardcoded-medical-constant",
        "frontend.mock-import",
        "frontend.no-medkernel-disable",
        "frontend.random-business-value",
        "frontend.technical-object-visible",
      ]);
    },
  );
});

test("前端测试与 Storybook 文件走白名单，不因测试 mock 被误杀", async () => {
  await withFixture(
    {
      "frontend/src/pages/BadPage.test.tsx": `
        vi.mock("@/shared/api/hooks", () => ({}));
        const hash = "SHA-256-MOCK-HASH";
        const value = Math.random();
      `,
      "frontend/src/features/Sample.stories.tsx": `
        import MockAdapter from "axios-mock-adapter";
        export const Basic = {};
      `,
    },
    async (root) => {
      const report = await scanFiles(root, [
        "frontend/src/pages/BadPage.test.tsx",
        "frontend/src/features/Sample.stories.tsx",
      ]);

      assert.equal(hasBlockingViolations(report), false);
      assert.deepEqual(report.violations, []);
    },
  );
});

test("前端 E2E 验收脚本禁止使用 mock 或固定医学剧本冒充真实验收", async () => {
  await withFixture(
    {
      "frontend/e2e/scenarios/fake-acceptance.spec.ts": `
        import { test } from "@playwright/test";

        test("固定 AMI 演示路径", async ({ page }) => {
          // 必含 3 条 mock 提醒
          await page.goto("/cdss/fatigue");
          await page.locator("text=胸痛 AMI 急诊路径").click();
        });
      `,
    },
    async (root) => {
      const report = await scanFiles(root, [
        "frontend/e2e/scenarios/fake-acceptance.spec.ts",
      ]);

      assert.equal(hasBlockingViolations(report), true);
      assert.deepEqual(ruleIds(report), ["frontend.e2e-fake-acceptance"]);
    },
  );
});

test("生产路由禁止注册 Demo 演示页", async () => {
  await withFixture(
    {
      "frontend/src/app/router.tsx": `
        import { lazy } from "react";
        import { Route } from "react-router-dom";

        const StepFlowDemo = lazy(() => import("@/pages/StepFlowDemo"));

        export function AppRouter() {
          return <Route path="/demo/step-flow" element={<StepFlowDemo />} />;
        }
      `,
    },
    async (root) => {
      const report = await scanFiles(root, ["frontend/src/app/router.tsx"]);

      assert.equal(hasBlockingViolations(report), true);
      assert.deepEqual(ruleIds(report), ["frontend.production-demo-route"]);
    },
  );
});

test("生产路由允许 WORKBENCH-02 演示与校验验收页", async () => {
  await withFixture(
    {
      "frontend/src/app/router.tsx": `
        import { lazy } from "react";
        import { Route } from "react-router-dom";

        const DemoValidation = lazy(() => import("@/pages/workbench/DemoValidation"));

        export function AppRouter() {
          return <Route path="/workbench/demo-validation" element={<DemoValidation />} />;
        }
      `,
    },
    async (root) => {
      const report = await scanFiles(root, ["frontend/src/app/router.tsx"]);

      assert.equal(hasBlockingViolations(report), false);
    },
  );
});


test("前端页面触碰文件会阻断旧规则路径示例占位符回流", async () => {
  await withFixture(
    {
      "frontend/src/pages/RulePathPage.tsx": `
        export function RulePathPage() {
          return <textarea defaultValue='{"drug_code":"DRUG-CODE","templateId":"PT-CAP-01"}' />;
        }
      `,
    },
    async (root) => {
      const report = await scanFiles(root, [
        "frontend/src/pages/RulePathPage.tsx",
      ]);

      assert.equal(hasBlockingViolations(report), true);
      assert.deepEqual(ruleIds(report), [
        "frontend.hardcoded-medical-constant",
      ]);
    },
  );
});

test("前端生产文件会阻断工作台本地假闭环和业务示例残留", async () => {
  await withFixture(
    {
      "frontend/src/pages/Dashboard.tsx": `
        export function Dashboard() {
          const todoMock = [{ title: "神经内科卒中路径还差 2 节点" }];
          return (
            <section>
              <h2>演示与校验</h2>
              <p>6 大客户验收剧本</p>
              <span>{todoMock[0].title}</span>
              <span>{source.evidenceLevel || "Class I"}</span>
              <input defaultValue="临床危急值回调报警" />
            </section>
          );
        }
      `,
    },
    async (root) => {
      const report = await scanFiles(root, ["frontend/src/pages/Dashboard.tsx"]);

      assert.equal(hasBlockingViolations(report), true);
      assert.deepEqual(ruleIds(report), [
        "frontend.hardcoded-medical-constant",
        "frontend.local-demo-workflow",
      ]);
    },
  );
});

test("前端生产文件会阻断默认临床病例文本回流", async () => {
  await withFixture(
    {
      "frontend/src/pages/AiWorkflows.tsx": `
        const defaultCaseInput = \`患者李建国，男，68岁，因“突发左侧肢体无力伴言语不清3小时”急诊入院。
        拟诊：急性脑梗死。已通知急性神经事件中心会诊拟开具阿替普酶静脉溶栓。\`;
        export function AiWorkflows() {
          return <textarea defaultValue={defaultCaseInput} />;
        }
      `,
    },
    async (root) => {
      const report = await scanFiles(root, ["frontend/src/pages/AiWorkflows.tsx"]);

      assert.equal(hasBlockingViolations(report), true);
      assert.deepEqual(ruleIds(report), [
        "frontend.hardcoded-medical-constant",
      ]);
    },
  );
});

test("前端 catch 成功门禁只检查 catch 代码块内部", async () => {
  await withFixture(
    {
      "frontend/src/pages/GoodPage.tsx": `
        export async function GoodPage() {
          try {
            JSON.parse("{}");
          } catch {
            message.error("JSON 格式不合法");
            return;
          }

          message.success("后端真实返回后再提示成功");
        }
      `,
    },
    async (root) => {
      const report = await scanFiles(root, ["frontend/src/pages/GoodPage.tsx"]);

      assert.equal(hasBlockingViolations(report), false);
      assert.deepEqual(report.violations, []);
    },
  );
});

test("前端生产文件禁止用规避门禁话术隐藏本地假数据闭环", async () => {
  await withFixture(
    {
      "frontend/src/pages/BypassPage.tsx": `
        // 规避 no-page-mock：通过函数动态提供初始通知列表
        function getInitialNotifications() {
          return [{ id: 1, title: "本地假通知" }];
        }
        export function BypassPage() {
          return getInitialNotifications().map((item) => <div key={item.id}>{item.title}</div>);
        }
      `,
    },
    async (root) => {
      const report = await scanFiles(root, [
        "frontend/src/pages/BypassPage.tsx",
      ]);

      assert.equal(hasBlockingViolations(report), true);
      assert.deepEqual(ruleIds(report), ["frontend.mock-bypass-language"]);
    },
  );
});

test("共享 API 层禁止导出演示快照供生产页面调用", async () => {
  await withFixture(
    {
      "frontend/src/shared/api/hooks.ts": `
        export const DEMO_SNAPSHOTS = [
          { id: "ctx-vte-demo-1", name: "演示快照" },
        ];
      `,
    },
    async (root) => {
      const report = await scanFiles(root, [
        "frontend/src/shared/api/hooks.ts",
      ]);

      assert.equal(hasBlockingViolations(report), true);
      assert.deepEqual(ruleIds(report), ["frontend.demo-snapshot-export"]);
    },
  );
});

test("CSS 触碰文件会阻断 hex/rgb/hsl 与字号圆角 px 硬编码", async () => {
  await withFixture(
    {
      "frontend/src/pages/Login.module.css": `
        .page {
          color: #1565c0;
          border: 1px solid rgba(21, 101, 192, 0.16);
          border-radius: 8px;
          font-size: 14px;
        }
      `,
    },
    async (root) => {
      const report = await scanFiles(root, [
        "frontend/src/pages/Login.module.css",
      ]);

      assert.equal(hasBlockingViolations(report), true);
      assert.deepEqual(ruleIds(report), [
        "frontend.css-hardcoded-color",
        "frontend.css-hardcoded-px-token",
      ]);
    },
  );
});

test("登录页 CSS 必须全部使用设计 token 变量", async () => {
  const report = await scanFiles(process.cwd(), [
    "frontend/src/pages/Login.module.css",
  ]);

  assert.equal(hasBlockingViolations(report), false);
  assert.deepEqual(report.violations, []);
});

test("全仓真实性 inventory 必须清零", async () => {
  const files = execFileSync("git", ["ls-files"], { encoding: "utf8" })
    .trim()
    .split(/\r?\n/)
    .filter(Boolean);
  const report = await scanFiles(process.cwd(), files);

  assert.equal(hasBlockingViolations(report), false);
  assert.deepEqual(report.violations, []);
});

test("后端生产代码触碰文件会阻断随机造数、吞错成功、UUID 伪 hash 和占位 Javadoc", async () => {
  await withFixture(
    {
      "medkernel-backend/src/main/java/com/medkernel/engine/BadService.java": `
        package com.medkernel.engine;

        import java.util.UUID;

        /** 演示用服务，占位实现。 */
        public class BadService {
          String buildHash() {
            String dataIntegrityHash = UUID.randomUUID().toString();
            return dataIntegrityHash + Math.random();
          }

          ApiResult run() {
            try {
              throw new IllegalStateException("fail");
            } catch (Exception ex) {
              return ApiResult.success("高血压处理成功");
            }
          }
        }
      `,
    },
    async (root) => {
      const report = await scanFiles(root, [
        "medkernel-backend/src/main/java/com/medkernel/engine/BadService.java",
      ]);

      assert.equal(hasBlockingViolations(report), true);
      assert.deepEqual(ruleIds(report), [
        "backend.catch-success",
        "backend.hardcoded-medical-constant",
        "backend.placeholder-javadoc",
        "backend.random-business-value",
        "backend.uuid-as-hash",
      ]);
    },
  );
});

test("后端生产代码会阻断时间戳或 hashCode 伪造证据摘要", async () => {
  await withFixture(
    {
      "medkernel-backend/src/main/java/com/medkernel/engine/HashEvidenceService.java": `
        package com.medkernel.engine;

        import java.time.Instant;

        public class HashEvidenceService {
          String sourceVersionHash(SourceVersionRegisterRequest request) {
            return sha256(request.versionNo() + "_" + Instant.now().toEpochMilli());
          }

          String idempotencyDigest(ContextSnapshotRequest request) {
            return Integer.toHexString(request.hashCode());
          }
        }
      `,
    },
    async (root) => {
      const report = await scanFiles(root, [
        "medkernel-backend/src/main/java/com/medkernel/engine/HashEvidenceService.java",
      ]);

      assert.equal(hasBlockingViolations(report), true);
      assert.deepEqual(ruleIds(report), [
        "backend.hashcode-digest",
        "backend.timestamp-as-hash",
      ]);
    },
  );
});

test("后端生产代码会阻断导出成功但仅写 memory 占位 URI", async () => {
  await withFixture(
    {
      "medkernel-backend/src/main/java/com/medkernel/engine/KnowledgeExportService.java": `
        package com.medkernel.engine;

        public class KnowledgeExportService {
          String finish(String jobCode) {
            return "memory://knowledge-export/" + jobCode + ".jsonl";
          }
        }
      `,
    },
    async (root) => {
      const report = await scanFiles(root, [
        "medkernel-backend/src/main/java/com/medkernel/engine/KnowledgeExportService.java",
      ]);

      assert.equal(hasBlockingViolations(report), true);
      assert.deepEqual(ruleIds(report), ["backend.placeholder-export-uri"]);
    },
  );
});

test("后端生产代码会阻断模拟同步和时间戳摘要伪造同步证据", async () => {
  await withFixture(
    {
      "medkernel-backend/src/main/java/com/medkernel/engine/release/BadSyncAdapter.java": `
        package com.medkernel.engine.release;

        import java.time.Instant;

        public class BadSyncAdapter {
          String sync() {
            // 模拟离线同步逻辑，计算带有发布计划、通道和时间戳的唯一摘要作为证据存证
            return "LNT-" + sha256("plan" + Instant.now().toString()).substring(0, 32);
          }
        }
      `,
    },
    async (root) => {
      const report = await scanFiles(root, [
        "medkernel-backend/src/main/java/com/medkernel/engine/release/BadSyncAdapter.java",
      ]);

      assert.equal(hasBlockingViolations(report), true);
      assert.deepEqual(ruleIds(report), ["backend.fake-sync-evidence"]);
    },
  );
});

test("后端生产代码会阻断资产影响分析伪造默认科室", async () => {
  await withFixture(
    {
      "medkernel-backend/src/main/java/com/medkernel/engine/release/BadImpactService.java": `
        package com.medkernel.engine.release;

        public class BadImpactService {
          String getAssetDepartment() {
            return "dept-default";
          }
        }
      `,
    },
    async (root) => {
      const report = await scanFiles(root, [
        "medkernel-backend/src/main/java/com/medkernel/engine/release/BadImpactService.java",
      ]);

      assert.equal(hasBlockingViolations(report), true);
      assert.deepEqual(ruleIds(report), ["backend.fake-impact-department"]);
    },
  );
});

test("后端同步证据字段与审计时间更新不会被误判为伪同步证据", async () => {
  await withFixture(
    {
      "medkernel-backend/src/main/java/com/medkernel/engine/release/GoodSyncLog.java": `
        package com.medkernel.engine.release;

        import java.time.Instant;

        public record GoodSyncLog(String syncEvidence, Instant updatedAt) {
          GoodSyncLog withStatus() {
            return new GoodSyncLog(syncEvidence, Instant.now());
          }
        }
      `,
    },
    async (root) => {
      const report = await scanFiles(root, [
        "medkernel-backend/src/main/java/com/medkernel/engine/release/GoodSyncLog.java",
      ]);

      assert.equal(hasBlockingViolations(report), false);
      assert.deepEqual(report.violations, []);
    },
  );
});

test("后端控制器触碰文件会阻断 RequestBody Map 裸入参", async () => {
  await withFixture(
    {
      "medkernel-backend/src/main/java/com/medkernel/engine/BadController.java": `
        package com.medkernel.engine;

        import java.util.Map;
        import org.springframework.web.bind.annotation.RequestBody;

        public class BadController {
          ApiResult<?> submit(@RequestBody Map<String, Object> body) {
            return ApiResult.ok(body);
          }
        }
      `,
    },
    async (root) => {
      const report = await scanFiles(root, [
        "medkernel-backend/src/main/java/com/medkernel/engine/BadController.java",
      ]);

      assert.equal(hasBlockingViolations(report), true);
      assert.deepEqual(ruleIds(report), [
        "backend.raw-request-body-map",
      ]);
    },
  );
});

test("前端生产文件会阻断全量加载式大分页", async () => {
  await withFixture(
    {
      "frontend/src/pages/BadListPage.tsx": `
        export function BadListPage() {
          return <ServerDataTable request={{ pageSize: 1000, filters: {} }} />;
        }
      `,
    },
    async (root) => {
      const report = await scanFiles(root, ["frontend/src/pages/BadListPage.tsx"]);

      assert.equal(hasBlockingViolations(report), true);
      assert.deepEqual(ruleIds(report), [
        "frontend.full-list-load",
      ]);
    },
  );
});

test("后端占位 Javadoc 门禁只检查 Javadoc 块内部", async () => {
  await withFixture(
    {
      "medkernel-backend/src/main/java/com/medkernel/engine/GoodService.java": `
        package com.medkernel.engine;

        /**
         * 真实服务说明。
         */
        public class GoodService {
          // 普通实现备注里的占位词不应被 Javadoc 门禁跨块误报。

          /**
           * 查询当前状态。
           */
          public String status() {
            return "OK";
          }
        }
      `,
    },
    async (root) => {
      const report = await scanFiles(root, [
        "medkernel-backend/src/main/java/com/medkernel/engine/GoodService.java",
      ]);

      assert.equal(hasBlockingViolations(report), false);
      assert.deepEqual(report.violations, []);
    },
  );
});

test("后端真实性门禁放行测试目录、迁移 SQL 与 dev profile bean", async () => {
  await withFixture(
    {
      "medkernel-backend/src/test/java/com/medkernel/engine/BadServiceTest.java": `
        package com.medkernel.engine;

        /** 演示测试服务，占位实现。 */
        class BadServiceTest {
          double randomScore() {
            return Math.random();
          }
        }
      `,
      "medkernel-backend/src/main/resources/db/migration/postgres/V99__demo.sql": `
        COMMENT ON TABLE demo_table IS '演示迁移占位';
      `,
      "medkernel-backend/src/main/java/com/medkernel/engine/dev/DevOnlyConfig.java": `
        package com.medkernel.engine.dev;

        import org.springframework.context.annotation.Bean;
        import org.springframework.context.annotation.Configuration;
        import org.springframework.context.annotation.Profile;

        /** dev profile 演示配置，仅本地开发使用。 */
        @Configuration
        @Profile("dev")
        public class DevOnlyConfig {
          @Bean
          String localHealthValue() {
            return "dev-" + Math.random();
          }
        }
      `,
    },
    async (root) => {
      const report = await scanFiles(root, [
        "medkernel-backend/src/test/java/com/medkernel/engine/BadServiceTest.java",
        "medkernel-backend/src/main/resources/db/migration/postgres/V99__demo.sql",
        "medkernel-backend/src/main/java/com/medkernel/engine/dev/DevOnlyConfig.java",
      ]);

      assert.equal(hasBlockingViolations(report), false);
      assert.deepEqual(report.violations, []);
    },
  );
});
