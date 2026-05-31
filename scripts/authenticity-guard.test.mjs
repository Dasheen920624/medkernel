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
      const report = await scanFiles(root, ["frontend/src/pages/BypassPage.tsx"]);

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
      const report = await scanFiles(root, ["frontend/src/shared/api/hooks.ts"]);

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
      const report = await scanFiles(root, ["frontend/src/pages/Login.module.css"]);

      assert.equal(hasBlockingViolations(report), true);
      assert.deepEqual(ruleIds(report), [
        "frontend.css-hardcoded-color",
        "frontend.css-hardcoded-px-token",
      ]);
    },
  );
});

test("登录页 CSS 必须全部使用设计 token 变量", async () => {
  const report = await scanFiles(process.cwd(), ["frontend/src/pages/Login.module.css"]);

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
