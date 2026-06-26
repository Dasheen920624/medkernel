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

test("前端生产文件会阻断客户面退役演示说明", async () => {
  const retiredDemoData = "演示" + "数据";
  const retiredSafetySkeleton = "安全" + "骨架";
  const fallbackFake = "兜底" + "伪造";
  await withFixture(
    {
      "frontend/src/pages/GraphExplore.tsx": `
        export function GraphExplore() {
          return <Result subTitle="未使用本地${retiredDemoData}替代真实结果。" />;
        }
      `,
      "frontend/src/pages/PathwayTemplates.tsx": `
        export const description = "默认生成急诊评估到处置安排的两节点${retiredSafetySkeleton}。";
      `,
      "frontend/src/pages/CdssFatigue.tsx": `
        export const empty = "暂无来源解释证据，不做任何${fallbackFake}";
      `,
    },
    async (root) => {
      const report = await scanFiles(root, [
        "frontend/src/pages/GraphExplore.tsx",
        "frontend/src/pages/PathwayTemplates.tsx",
        "frontend/src/pages/CdssFatigue.tsx",
      ]);

      assert.equal(hasBlockingViolations(report), true);
      assert.deepEqual(ruleIds(report), [
        "frontend.retired-demo-copy",
        "frontend.retired-demo-copy",
        "frontend.retired-demo-copy",
      ]);
    },
  );
});

test("前端生产文件会阻断客户面工程内部语言", async () => {
  const developerConsole = "开发者" + "控制台";
  const technicalReview = "技术" + "验证";
  const technicalConfig = "技术" + "配置";
  const technicalGate = "技术" + "门";
  const technicalField = "技术" + "字段";
  const technicalValidation = "技术" + "校验";
  const controlledDebug = "受控" + "调试" + "信息";
  await withFixture(
    {
      "frontend/src/pages/DevConsole.tsx": `
        export function DevConsole() {
          return <PageShell title="${developerConsole}" description="请让 SRE 等待${technicalReview}" />;
        }
      `,
      "frontend/src/pages/RuleDefinitions.tsx": `
        export const label = "L3 ${technicalConfig}";
      `,
      "frontend/src/pages/KnowledgeGovernance.tsx": `
        export const message = "${technicalGate}尚未满足，${technicalField}和${technicalValidation}进入详情";
      `,
      "frontend/src/shared/config/routes.ts": `
        export const route = {
          title: "诊断工具",
          experience: readonlyExperience("平台管理员", "核查${controlledDebug}", "最近诊断"),
        };
      `,
    },
    async (root) => {
      const report = await scanFiles(root, [
        "frontend/src/pages/DevConsole.tsx",
        "frontend/src/pages/RuleDefinitions.tsx",
        "frontend/src/pages/KnowledgeGovernance.tsx",
        "frontend/src/shared/config/routes.ts",
      ]);

      assert.equal(hasBlockingViolations(report), true);
      assert.deepEqual(ruleIds(report), [
        "frontend.customer-facing-engineering-language",
        "frontend.customer-facing-engineering-language",
        "frontend.customer-facing-engineering-language",
        "frontend.customer-facing-engineering-language",
      ]);
    },
  );
});

test("当前权威文档会阻断旧技术安全门口径", async () => {
  const technicalSafetyGate = "技术" + "安全门";
  const technicalEvaluation = "技术" + "评测";
  const technicalField = "技术" + "字段";
  const technicalValidation = "技术" + "校验";
  await withFixture(
    {
      "docs/PRODUCT_SCOPE.md": `知识生产包含${technicalSafetyGate}。`,
      "docs/glossary.md": `模型生成、${technicalEvaluation}和发布。`,
      "docs/handbook/operations.md": `发布前核查${technicalValidation}和回滚。`,
      "docs/EXPERIENCE_CONTRACT.md": `默认视图折叠${technicalField}。`,
    },
    async (root) => {
      const report = await scanFiles(root, [
        "docs/PRODUCT_SCOPE.md",
        "docs/glossary.md",
        "docs/handbook/operations.md",
        "docs/EXPERIENCE_CONTRACT.md",
      ]);

      assert.equal(hasBlockingViolations(report), true);
      assert.deepEqual(ruleIds(report), [
        "docs.customer-facing-safety-language",
        "docs.customer-facing-safety-language",
        "docs.customer-facing-safety-language",
        "docs.customer-facing-safety-language",
      ]);
    },
  );
});

test("当前权威体验文档禁止回流演示重构旧说法", async () => {
  await withFixture(
    {
      "docs/EXPERIENCE_CONTRACT.md": `
        新增或改造客户可见页面还必须遵守五条演示重构原则：
      `,
    },
    async (root) => {
      const report = await scanFiles(root, ["docs/EXPERIENCE_CONTRACT.md"]);

      assert.equal(hasBlockingViolations(report), true);
      assert.deepEqual(ruleIds(report), ["docs.customer-facing-safety-language"]);
    },
  );
});

test("后端与数据库中文注释会阻断旧安全校验口径", async () => {
  const technicalEvaluation = "技术" + "评测";
  const technicalValidation = "技术" + "校验";
  await withFixture(
    {
      "medkernel-backend/src/main/java/com/medkernel/engine/llm/eval/ModelEvalRun.java": `
        /** 全部${technicalEvaluation}通过后才能上线。 */
        public record ModelEvalRun() {}
      `,
      "medkernel-backend/src/main/resources/db/migration/postgres/V1__baseline.sql": `
        COMMENT ON TABLE asset_validation_record IS '资产发布${technicalValidation}证据';
      `,
      "medkernel-backend/src/main/resources/db/schema/medkernel.schema.json": `
        {"comment":"${technicalValidation}结果摘要"}
      `,
    },
    async (root) => {
      const report = await scanFiles(root, [
        "medkernel-backend/src/main/java/com/medkernel/engine/llm/eval/ModelEvalRun.java",
        "medkernel-backend/src/main/resources/db/migration/postgres/V1__baseline.sql",
        "medkernel-backend/src/main/resources/db/schema/medkernel.schema.json",
      ]);

      assert.equal(hasBlockingViolations(report), true);
      assert.deepEqual(ruleIds(report), [
        "backend.customer-facing-safety-language",
        "db.customer-facing-safety-language",
        "db.customer-facing-safety-language",
      ]);
    },
  );
});

test("前后端契约会阻断实施内部口径残留", async () => {
  const technicalCheck = "技术" + "核验";
  const technicalReleaseChain = "技术" + "发布链";
  const sourceVersionTechInfo = "来源版本" + "技术" + "信息";
  const platformDeveloper = "平台" + "开发者";
  const debugBefore = "调试" + "前";
  const channelDebug = "通道" + "调试";
  const testPayload = "测试 " + "Payload";
  const offlineFixture = "offline-" + "fixture";
  await withFixture(
    {
      "medkernel-backend/src/main/java/com/medkernel/engine/knowledge/acquisition/AcquisitionController.java": `
        /** 启用已经完成来源真实性、许可和 robots ${technicalCheck}的资料来源。 */
        public class AcquisitionController {}
      `,
      "medkernel-backend/src/main/java/com/medkernel/engine/security/auth/LoginTenantDirectoryService.java": `
        /** 平台主租户退到第二层给${platformDeveloper}和运维人员使用。 */
        public class LoginTenantDirectoryService {}
      `,
      "medkernel-backend/src/main/java/com/medkernel/engine/pathway/PathwaySimulationResponse.java": `
        /** 用于在发布或${debugBefore}回放路径走向。 */
        public record PathwaySimulationResponse() {}
      `,
      "medkernel-backend/src/main/java/com/medkernel/engine/integration/dto/WebhookTestDto.java": `
        /** 用于在 Webhook 订阅${channelDebug}中传递${testPayload}报文。 */
        public record WebhookTestDto() {}
      `,
      "medkernel-backend/src/main/java/com/medkernel/engine/knowledge/production/initialization/KnowledgeInitializationService.java": `
        public class KnowledgeInitializationService {
          String message = "${sourceVersionTechInfo}不完整";
        }
      `,
      "medkernel-backend/src/main/java/com/medkernel/engine/security/PermissionCode.java": `
        public enum PermissionCode {
          INTEGRATION_EXECUTE("执行适配器健康检查、Webhook 测试、入站验签")
        }
      `,
      "medkernel-backend/src/main/java/com/medkernel/engine/llm/eval/ModelEvalService.java": `
        public class ModelEvalService {
          String providerCode = "${offlineFixture}";
        }
      `,
      "medkernel-backend/src/main/resources/db/migration/postgres/V1__baseline.sql": `
        COMMENT ON COLUMN rule_governance.author_id IS '规则版本负责人，可确认并推进完整${technicalReleaseChain}';
      `,
      "medkernel-backend/src/main/resources/db/schema/medkernel.schema.json": `
        {"comment":"规则版本负责人，可确认并推进完整${technicalReleaseChain}"}
      `,
    },
    async (root) => {
      const report = await scanFiles(root, [
        "medkernel-backend/src/main/java/com/medkernel/engine/knowledge/acquisition/AcquisitionController.java",
        "medkernel-backend/src/main/java/com/medkernel/engine/security/auth/LoginTenantDirectoryService.java",
        "medkernel-backend/src/main/java/com/medkernel/engine/pathway/PathwaySimulationResponse.java",
        "medkernel-backend/src/main/java/com/medkernel/engine/integration/dto/WebhookTestDto.java",
        "medkernel-backend/src/main/java/com/medkernel/engine/knowledge/production/initialization/KnowledgeInitializationService.java",
        "medkernel-backend/src/main/java/com/medkernel/engine/security/PermissionCode.java",
        "medkernel-backend/src/main/java/com/medkernel/engine/llm/eval/ModelEvalService.java",
        "medkernel-backend/src/main/resources/db/migration/postgres/V1__baseline.sql",
        "medkernel-backend/src/main/resources/db/schema/medkernel.schema.json",
      ]);

      assert.equal(hasBlockingViolations(report), true);
      assert.deepEqual(ruleIds(report), [
        "backend.customer-facing-internal-operation-language",
        "backend.customer-facing-internal-operation-language",
        "backend.customer-facing-internal-operation-language",
        "backend.customer-facing-internal-operation-language",
        "backend.customer-facing-internal-operation-language",
        "backend.customer-facing-internal-operation-language",
        "backend.customer-facing-internal-operation-language",
        "db.customer-facing-safety-language",
        "db.customer-facing-safety-language",
      ]);
    },
  );
});

test("领域门面生产契约禁止回流 fixture 验收样本口径", async () => {
  await withFixture(
    {
      "medkernel-backend/src/main/java/com/medkernel/engine/domainfacade/DomainFacadeController.java": `
        /** 列举 X-DOMAIN 17 张领域门面的 B0 fixture 证据。 */
        @GetMapping("/b0-fixtures")
        public class DomainFacadeController {}
      `,
    },
    async (root) => {
      const report = await scanFiles(root, [
        "medkernel-backend/src/main/java/com/medkernel/engine/domainfacade/DomainFacadeController.java",
      ]);

      assert.equal(hasBlockingViolations(report), true);
      assert.deepEqual(ruleIds(report), [
        "backend.customer-facing-internal-operation-language",
        "backend.domain-facade-fixture-language",
      ]);
    },
  );
});

test("后端生产规则校验禁止继续使用静态校验占位口径", async () => {
  await withFixture(
    {
      "medkernel-backend/src/main/java/com/medkernel/engine/rule/RuleEngineService.java": `
        public class RuleEngineService {
          void fill(ObjectNode node) {
            node.put("summary", "临床提示卡引用静态校验占位");
          }
        }
      `,
    },
    async (root) => {
      const report = await scanFiles(root, [
        "medkernel-backend/src/main/java/com/medkernel/engine/rule/RuleEngineService.java",
      ]);

      assert.equal(hasBlockingViolations(report), true);
      assert.deepEqual(ruleIds(report), ["backend.rule-static-placeholder-language"]);
    },
  );
});

test("上线演练脚本禁止继续使用影响模拟旧口径", async () => {
  await withFixture(
    {
      "scripts/knowledge/full-knowledge-rehearsal-lib.mjs": `
        export const review = {
          reason: "低风险上线演练知识：来源、结构、引用、安全门和影响模拟均已核对",
        };
      `,
    },
    async (root) => {
      const report = await scanFiles(root, [
        "scripts/knowledge/full-knowledge-rehearsal-lib.mjs",
      ]);

      assert.equal(hasBlockingViolations(report), true);
      assert.deepEqual(ruleIds(report), ["scripts.impact-simulation-language"]);
    },
  );
});

test("后端发布治理契约禁止回流发布模拟旧口径", async () => {
  await withFixture(
    {
      "medkernel-backend/src/main/java/com/medkernel/engine/contract/ServiceContractCatalog.java": `
        public class ServiceContractCatalog {
          String title = "发布模拟与灰度治理服务";
        }
      `,
      "medkernel-backend/src/main/java/com/medkernel/engine/versioning/ReleaseGovernanceController.java": `
        public class ReleaseGovernanceController {
          String conflict = "模拟摘要已变化，请重新模拟并确认";
        }
      `,
    },
    async (root) => {
      const report = await scanFiles(root, [
        "medkernel-backend/src/main/java/com/medkernel/engine/contract/ServiceContractCatalog.java",
        "medkernel-backend/src/main/java/com/medkernel/engine/versioning/ReleaseGovernanceController.java",
      ]);

      assert.equal(hasBlockingViolations(report), true);
      assert.deepEqual(ruleIds(report), [
        "backend.customer-facing-safety-language",
        "backend.customer-facing-safety-language",
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

test("共享 API 合同禁止继续使用 Webhook 测试旧口径", async () => {
  await withFixture(
    {
      "frontend/src/shared/api/hooks.ts": `
        // Webhook 签名生成与双向测试
        export function useTestWebhookSignature() {}
      `,
    },
    async (root) => {
      const report = await scanFiles(root, [
        "frontend/src/shared/api/hooks.ts",
      ]);

      assert.equal(hasBlockingViolations(report), true);
      assert.deepEqual(ruleIds(report), [
        "frontend.customer-facing-integration-test-language",
      ]);
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

test("后端生产注释会阻断早期任务口吻", async () => {
  const retiredSkeletonIntro = "本类只提供" + "骨架";
  const retiredTaskPhrase = "任务中" + "实施";
  await withFixture(
    {
      "medkernel-backend/src/main/java/com/medkernel/shared/context/RequestContext.java": `
        package com.medkernel.shared.context;

        /**
         * 请求上下文。
         *
         * <p>${retiredSkeletonIntro}；JWT 到组织上下文的真实填充在后续${retiredTaskPhrase}。
         */
        public final class RequestContext {}
      `,
    },
    async (root) => {
      const report = await scanFiles(root, [
        "medkernel-backend/src/main/java/com/medkernel/shared/context/RequestContext.java",
      ]);

      assert.equal(hasBlockingViolations(report), true);
      assert.deepEqual(ruleIds(report), ["backend.retired-task-language"]);
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
