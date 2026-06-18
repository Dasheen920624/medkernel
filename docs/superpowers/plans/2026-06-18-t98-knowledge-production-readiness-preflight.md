# T9.8 知识生产上线只读预检 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 提供一个不执行生产业务写入的 T9.8 预检器，精确核验 9 项知识生产 readiness、服务健康和受控公域来源，并输出脱敏、可重放的 JSON 证据。

**Architecture:** 核心库通过注入 `fetch` 完成登录、画像、health、readiness 和来源只读查询，并返回结构化裁决；CLI 只负责环境变量、凭据文件和原子证据写入。测试使用本地响应桩验证裁决与 HTTP 方法，不把测试数据冒充生产证据。

**Tech Stack:** Node.js 24、ESM、`node:test`、原生 `fetch`、原生文件系统 API。

---

### Task 1: 核心裁决红测

**Files:**
- Create: `scripts/drill/p9-t98-readiness-preflight.test.mjs`
- Create: `scripts/drill/p9-t98-readiness-preflight-lib.mjs`

- [ ] **Step 1: 写 9 闸全绿与来源有效测试**

测试导入以下公共接口：

```js
import {
  EXPECTED_READINESS_CODES,
  assessKnowledgeReadiness,
  assessSourceReadiness,
  redactEvidence,
} from "./p9-t98-readiness-preflight-lib.mjs";
```

构造 `items` 精确包含：

```js
[
  "LITERATURE_ROOT",
  "DEPLOYMENT_FORM",
  "MODEL_PROVIDER",
  "REGRESSION_BASELINE",
  "MODEL_EVALUATION",
  "EGRESS_GOVERNANCE",
  "MODEL_POLICY",
  "VERSION_TRIPLE",
  "P6_ACCEPTANCE",
]
```

每项 `ready=true/required=true`，聚合响应为 `ready=true`、`modelInvocationAllowed=true`、`producer="API_MODEL"`、`providerCode="external-mimo-v25"`、`capabilityCode="rule.draft"`。断言裁决 `ready=true`、无 failures。

来源夹具必须为：

```js
{
  sourceCode: "WHO-CHB-GUIDELINE-2024",
  enabledFlag: "Y",
  approvedBy: "independent-governor",
  approvedAt: "2026-06-18T00:00:00Z",
  licensePolicy: "PERMITTED",
  robotsPolicy: "ALLOW_FETCH",
}
```

断言来源裁决通过。

- [ ] **Step 2: 写阻断与脱敏测试**

分别断言：

- 缺 `P6_ACCEPTANCE`；
- 重复 `MODEL_PROVIDER`；
- 任一项 `ready=false`；
- 聚合 `ready/modelInvocationAllowed` 与单项不一致；
- producer/provider/capability 漂移；
- 来源停用、未审批、许可非 `PERMITTED`、robots 为 `DISALLOW_FETCH`；
- `redactEvidence` 对任意层级的 `password/cookie/token/secret/credential/recovery/mfa/otp/totp/signature` 值输出 `[REDACTED]`。

- [ ] **Step 3: 运行红测**

Run:

```bash
node --test scripts/drill/p9-t98-readiness-preflight.test.mjs
```

Expected: FAIL，原因是核心库尚未导出上述接口。

### Task 2: 实现核心裁决

**Files:**
- Create: `scripts/drill/p9-t98-readiness-preflight-lib.mjs`
- Modify: `scripts/drill/p9-t98-readiness-preflight.test.mjs`

- [ ] **Step 1: 实现固定闸门集合**

```js
export const EXPECTED_READINESS_CODES = Object.freeze([
  "LITERATURE_ROOT",
  "DEPLOYMENT_FORM",
  "MODEL_PROVIDER",
  "REGRESSION_BASELINE",
  "MODEL_EVALUATION",
  "EGRESS_GOVERNANCE",
  "MODEL_POLICY",
  "VERSION_TRIPLE",
  "P6_ACCEPTANCE",
]);
```

- [ ] **Step 2: 实现 readiness 与来源裁决**

```js
export function assessKnowledgeReadiness(data, expected) {
  const failures = [];
  const items = Array.isArray(data?.items) ? data.items : [];
  const codes = items.map((item) => item?.code);
  // 精确集合、唯一性、required/ready、聚合标志和上下文一致性逐项追加 failures。
  return { ready: failures.length === 0, failures, items };
}

export function assessSourceReadiness(pageData, sourceCode) {
  const items = Array.isArray(pageData?.items) ? pageData.items : [];
  const source = items.find((item) => item?.sourceCode === sourceCode) ?? null;
  // 校验启用、审批、许可与 robots。
  return { ready: failures.length === 0, failures, source };
}
```

`MANUAL_APPROVED` 与 `ALLOW_FETCH` 均视为 robots 允许；任何未知值阻断。

- [ ] **Step 3: 实现递归脱敏**

```js
const SENSITIVE_KEY = /password|cookie|token|secret|credential|recovery|mfa|otp|totp|signature/i;

export function redactEvidence(value) {
  if (Array.isArray(value)) return value.map(redactEvidence);
  if (!value || typeof value !== "object") return value;
  return Object.fromEntries(
    Object.entries(value).map(([key, item]) => [
      key,
      SENSITIVE_KEY.test(key) ? "[REDACTED]" : redactEvidence(item),
    ]),
  );
}
```

- [ ] **Step 4: 运行单测转绿**

Run:

```bash
node --test scripts/drill/p9-t98-readiness-preflight.test.mjs
```

Expected: PASS。

### Task 3: 只读网络编排与方法约束

**Files:**
- Modify: `scripts/drill/p9-t98-readiness-preflight-lib.mjs`
- Modify: `scripts/drill/p9-t98-readiness-preflight.test.mjs`

- [ ] **Step 1: 写网络编排红测**

用可注入的 `fetchImpl` 记录请求，依次返回 login、`/security/me`、health、readiness 和 sources 响应。调用：

```js
const result = await runReadinessPreflight({
  fetchImpl,
  apiBaseUrl: "http://127.0.0.1:18080/medkernel/api/v1",
  healthUrl: "http://127.0.0.1:18080/medkernel/actuator/health/readiness",
  credentials: { username: "owner", password: "controlled", tenantId: "t-1" },
  producer: "API_MODEL",
  providerCode: "external-mimo-v25",
  capabilityCode: "rule.draft",
  sourceCode: "WHO-CHB-GUIDELINE-2024",
  now: () => new Date("2026-06-18T12:00:00Z"),
});
```

断言：

- 只有 `/auth/login` 使用 `POST`；
- 其余请求全部 `GET`；
- Cookie 只出现在请求头，不出现在结果；
- 全绿返回 `status="PASSED"`；
- 任一 HTTP 非 2xx 或 JSON 解析失败返回 `status="BLOCKED"` 和中文 failures。

- [ ] **Step 2: 运行红测**

Run:

```bash
node --test scripts/drill/p9-t98-readiness-preflight.test.mjs
```

Expected: FAIL，原因是 `runReadinessPreflight` 尚未实现。

- [ ] **Step 3: 实现只读编排**

实现：

```js
export async function runReadinessPreflight(options) {
  // 1. POST /auth/login，提取 Set-Cookie；
  // 2. GET /security/me；
  // 3. GET healthUrl；
  // 4. GET /engine/knowledge/production/readiness?producer=...；
  // 5. GET /engine/knowledge/acquisition/sources?page=1&size=100；
  // 6. 汇总 assessKnowledgeReadiness / assessSourceReadiness；
  // 7. 返回 redactEvidence(evidence)。
}
```

请求账本只保存 `{ method, path, status }`。响应解析必须检查 `content-type` 和 JSON，错误信息不得包含响应认证头或请求体。

- [ ] **Step 4: 运行单测转绿**

Run:

```bash
node --test scripts/drill/p9-t98-readiness-preflight.test.mjs
```

Expected: PASS。

### Task 4: CLI、原子证据与当前 134 诚实阻断

**Files:**
- Create: `scripts/drill/p9-t98-readiness-preflight.mjs`
- Modify: `scripts/drill/README.md`
- Modify: `docs/superpowers/plans/2026-06-16-autonomous-knowledge-production-golive-master-plan.md`
- Modify: `docs/_HANDOFF.md`

- [ ] **Step 1: 实现环境边界**

CLI 启动时读取并校验：

```js
const required = [
  "P9_T98_API_BASE_URL",
  "P9_T98_HEALTH_URL",
  "P9_T98_CREDENTIALS_FILE",
  "P9_T98_PROVIDER_CODE",
  "P9_T98_SOURCE_CODE",
  "P9_T98_OUTPUT_PATH",
];
```

缺失时在调用 `fetch` 前抛中文错误。producer 默认 `API_MODEL`，capability 默认 `rule.draft`。

- [ ] **Step 2: 实现原子写入**

```js
mkdirSync(dirname(outputPath), { recursive: true });
const tempPath = `${outputPath}.${process.pid}.tmp`;
writeFileSync(tempPath, `${JSON.stringify(result, null, 2)}\n`, { mode: 0o600 });
renameSync(tempPath, outputPath);
```

打印只包含 `status/failureCount/outputPath` 的摘要；`BLOCKED` 设置 `process.exitCode=1`。

- [ ] **Step 3: 运行当前 134 预检**

Run:

```bash
P9_T98_API_BASE_URL=http://127.0.0.1:18080/medkernel/api/v1 \
P9_T98_HEALTH_URL=http://127.0.0.1:18080/medkernel/actuator/health/readiness \
P9_T98_CREDENTIALS_FILE=/tmp/p9-production-admin.json \
P9_T98_PROVIDER_CODE=external-mimo-v25 \
P9_T98_SOURCE_CODE=WHO-CHB-GUIDELINE-2024 \
P9_T98_OUTPUT_PATH=/tmp/p9-t98-readiness-preflight.json \
node scripts/drill/p9-t98-readiness-preflight.mjs
```

Expected: 退出 1，证据 `status=BLOCKED`，明确显示 provider/医学评测/版本三元组/P6 未通过；health 与来源通过。不得发生业务写请求。

- [ ] **Step 4: 同步文档**

`scripts/drill/README.md` 登记预检器；主计划 T9.8 和 `_HANDOFF.md` 记录“预检器完成但当前 134 仍阻断”，不得勾选 T9.3/T9.6/T9.8。

### Task 5: 全量验证与本地提交

**Files:**
- Modify: `docs/superpowers/plans/2026-06-18-t98-knowledge-production-readiness-preflight.md`

- [ ] **Step 1: 运行验证**

```bash
node --test scripts/drill/p9-t98-readiness-preflight.test.mjs
node --test scripts/authenticity-guard.test.mjs
node scripts/authenticity-guard.mjs --mode=changed
node scripts/b0-perfect-check.mjs
bash scripts/check-comment-zh.sh
node scripts/audit/export-product-capabilities.mjs --check
git diff --check
```

Expected: 全部通过；当前 134 预检仍诚实 `BLOCKED`。

- [ ] **Step 2: 自审**

确认：

- 新脚本没有关闭 TLS 校验；
- 没有凭据、Cookie、Token 或 MFA 内容落入仓库/证据；
- 登录外所有请求均为 GET；
- 没有任何专家签署、P6 放行或候选激活自动化。

- [ ] **Step 3: 本地提交**

```bash
git add scripts/drill docs
git commit -m "feat: 增加T9.8知识生产只读预检"
```

Expected: 本地提交成功，不推送远程。
