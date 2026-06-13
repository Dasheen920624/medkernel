# 全真体验沙盘 · 阶段A2（前端沙盘页）实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans. Steps use checkbox (`- [ ]`).

**Goal:** 在 MedKernel 前端新增 `/sandbox` 受控页：左侧业务系统宿主面板（场景目录 + 真实数据录入）、中间触发调阶段A 后端编排、右侧 `iframe` 嵌真 `/embed/launch`、底部完整路径检查器。

**Architecture:** `SandboxHost.tsx` 组合四个 feature 组件；`useRunSandboxScenario` mutation hook 调 `POST /engine/sandbox/scenarios/{id}/run`（阶段A 端点）；据返回 `embedUrl` 加载 iframe，据 `steps` 渲染路径检查器；`postMessage` 监听回传。前端不复制编排逻辑。

**Tech Stack:** React + TypeScript + Ant Design + `@tanstack/react-query` + react-router；测试 vitest + @testing-library/react。

**前置依赖：** 阶段A 后端（`/engine/sandbox/...run`、`sandbox.run` 权限、`menu.sandbox`）已合并。**先读 spec §4/§6/§9/§17、A1 计划 Task 6 的菜单 key。**

---

## 文件结构
| 文件 | 职责 |
|---|---|
| Create `frontend/src/features/sandbox/sandboxScenarios.ts` | 前端场景注册表（`SandboxScenario` 接口 + 高钾 #1） |
| Create `frontend/src/features/sandbox/SandboxDataEntry.tsx` | 按触发点动态录入表单（阶段A2 先只读预置 + 可改阈值） |
| Create `frontend/src/features/sandbox/SandboxPathInspector.tsx` | 路径检查器（渲染 `steps`，每步可展开 req/resp/serverFacts） |
| Create `frontend/src/features/sandbox/SandboxEmbedFrame.tsx` | 包裹 `/embed/launch` iframe + `postMessage` 监听 |
| Create `frontend/src/pages/sandbox/SandboxHost.tsx` + `.module.css` | 页面骨架，组合上述组件 |
| Modify `frontend/src/shared/api/hooks.ts` | 加 `useRunSandboxScenario` + 类型 `SandboxRunResponse`/`SandboxStepTrace` |
| Modify `frontend/src/app/router.tsx` | 加 `<Route path="/sandbox" element={<SandboxHost/>} />`（lazy） |
| Modify `frontend/src/shared/config/routes.ts` | 加 `/sandbox` 条目（placement 受控、`requiredRoles` 含沙盘角色） |
| Test | 各组件 `.test.tsx` + `routes.test.ts`（前后端菜单一致性，已有机制） |

---

## Task 1: 运行 hook 与类型

**Files:** Modify `frontend/src/shared/api/hooks.ts`；Test `frontend/src/shared/api/hooks.test.ts`

- [ ] **Step 1: 加类型 + hook**（仿 `useSubmitEmbedFeedback` mutation 模式）

```ts
export interface SandboxStepTrace {
  stage: string; endpoint: string;
  request: unknown; response: unknown;
  serverFacts: Record<string, unknown>;
  status: "OK" | "FAIL"; error?: string;
}
export interface SandboxRunResponse {
  scenarioId: string; traceId: string; steps: SandboxStepTrace[];
  snapshotId?: string; triggerId?: string; cardCount: number;
  embedToken?: string; embedUrl?: string; result: "PASS" | "FAIL";
}
export interface SandboxRunRequest { entryMode?: "SNAPSHOT" | "EVENT" | "ADAPTER"; contextOverride?: unknown; occurredAt?: string; }

export function useRunSandboxScenario() {
  return useMutation({
    mutationFn: async (vars: { scenarioId: string; body?: SandboxRunRequest }) => {
      const { data } = await apiClient.post<{ data: SandboxRunResponse }>(
        `/engine/sandbox/scenarios/${vars.scenarioId}/run`,
        vars.body ?? {},
      );
      return data.data;
    },
  });
}
```

- [ ] **Step 2: 写测试**（`hooks.test.ts` 加用例：mock `apiClient.post`，调 hook，断言 POST 到 `/engine/sandbox/scenarios/sbx-lab-critical-k/run` 且返回 `result`）。Run: `cd frontend && npm test -- src/shared/api/hooks.test.ts`　Expected: PASS。

- [ ] **Step 3: 提交**　`git add frontend/src/shared/api/hooks.ts frontend/src/shared/api/hooks.test.ts && git commit -m "feat(sandbox-fe): useRunSandboxScenario hook"`

---

## Task 2: 前端场景注册表

**Files:** Create `frontend/src/features/sandbox/sandboxScenarios.ts`；Test 同目录 `.test.ts`

- [ ] **Step 1: 写注册表**（结构见 spec §17.1，阶段A2 仅 #1）

```ts
export interface SandboxScenario {
  id: string; servicePackage: string; engine: string;
  triggerPoint: "patient-view"|"order-sign"|"medication-prescribe"|"result-review"|"discharge-sign"|"followup-alert";
  ruleType?: string; title: string; narrative: string;
  expectedRuleCode?: string; expectedAction?: string; expectedSeverity?: string;
  status: "ready" | "pending-seed";
  hostSummary: string; // 业务系统面板展示文案
}
export const SANDBOX_SCENARIOS: SandboxScenario[] = [
  { id: "sbx-lab-critical-k", servicePackage: "clinical-run", engine: "rule",
    triggerPoint: "result-review", ruleType: "LAB", title: "血钾危急值红线",
    narrative: "急诊检验复核，血钾 6.8 mmol/L 命中危急值红线，需医师确认。",
    expectedRuleCode: "P5.ACT4.CRITICAL.K", expectedAction: "STRONG_REMINDER", expectedSeverity: "CRITICAL",
    status: "ready",
    hostSummary: "患者 张某 男60 · 急诊 · 血清钾 6.8 mmol/L（参考 3.5-5.5）" },
];
export function scenariosByServicePackage(): Record<string, SandboxScenario[]> {
  return SANDBOX_SCENARIOS.reduce((acc, s) => { (acc[s.servicePackage] ??= []).push(s); return acc; }, {} as Record<string, SandboxScenario[]>);
}
```

- [ ] **Step 2: 测试**（断言 `scenariosByServicePackage()['clinical-run']` 含高钾、`status==='ready'`）。Run: `npm test -- src/features/sandbox/sandboxScenarios.test.ts`。
- [ ] **Step 3: 提交**

---

## Task 3: 路径检查器组件

**Files:** Create `SandboxPathInspector.tsx` + `.test.tsx`

- [ ] **Step 1: 写组件**：props `{ steps: SandboxStepTrace[] }`；渲染每步 stage 徽标（OK 绿/FAIL 红）+ 可折叠面板展示 `request`/`response`/`serverFacts`（JSON `<pre>`）。空 steps 显示"尚未运行"。用 AntD `Steps`/`Collapse`/`Tag`。

- [ ] **Step 2: 测试**：传 3 个 OK step → 断言渲染 3 个 stage 名 + "通过"态；传含 1 个 FAIL → 断言显示 error 文本。Run: `npm test -- src/features/sandbox/SandboxPathInspector.test.tsx`。
- [ ] **Step 3: 提交**

---

## Task 4: 嵌入帧组件

**Files:** Create `SandboxEmbedFrame.tsx` + `.test.tsx`

- [ ] **Step 1: 写组件**：props `{ embedUrl?: string; onDecision: (e: {action:string; reason?:string}) => void }`；`embedUrl` 为空显示占位"运行场景后加载嵌入终端"；非空渲染 `<iframe src={embedUrl} title="临床嵌入式终端">`；`useEffect` 注册 `window.addEventListener("message", handler)`，校验 `event.data.source === "MEDKERNEL_CDSS_EMBED"` 后调 `onDecision`，卸载时移除监听。

- [ ] **Step 2: 测试**：传 `embedUrl` → 断言 iframe `src` 正确；派发 `message` 事件（source=MEDKERNEL_CDSS_EMBED, action=ADOPT）→ 断言 `onDecision` 被调。Run: `npm test -- src/features/sandbox/SandboxEmbedFrame.test.tsx`。
- [ ] **Step 3: 提交**

---

## Task 5: 数据录入组件（阶段A2 最小：展示预置 + 触发）

**Files:** Create `SandboxDataEntry.tsx` + `.test.tsx`

- [ ] **Step 1: 写组件**：props `{ scenario: SandboxScenario; onRun: () => void; running: boolean }`；展示 `scenario.hostSummary` + 触发点标签 + 「医生复核 → 触发 MedKernel 引擎」按钮（`disabled={running}`，点击调 `onRun`）。阶段A2 先不做自由字段编辑（留阶段B/后续按 `requiredContextFields` 扩展，spec §6）。

- [ ] **Step 2: 测试**：渲染 → 断言显示 hostSummary；点按钮 → `onRun` 被调；`running=true` → 按钮禁用。
- [ ] **Step 3: 提交**

---

## Task 6: 页面骨架 SandboxHost

**Files:** Create `SandboxHost.tsx` + `.module.css` + `.test.tsx`

- [ ] **Step 1: 写页面**：左列场景树（`scenariosByServicePackage()`，点选 set `selected`）；中列 `<SandboxDataEntry scenario={selected} onRun={run} running={isPending}/>`；右列 `<SandboxEmbedFrame embedUrl={result?.embedUrl} onDecision={...}/>`；底部 `<SandboxPathInspector steps={result?.steps ?? []}/>`。`run` = `mutate({scenarioId: selected.id})`，成功 set `result`，失败 message.error。`pending-seed` 场景禁用触发并标"未就绪"。

```tsx
const { mutate, isPending } = useRunSandboxScenario();
const [result, setResult] = useState<SandboxRunResponse | null>(null);
const run = () => mutate({ scenarioId: selected.id }, { onSuccess: setResult, onError: () => message.error("场景运行失败") });
```

- [ ] **Step 2: 测试**：`vi.mock("@/shared/api/hooks")` 同 EmbedLaunch.test 模式；mock `useRunSandboxScenario` 返回 `mutate`；渲染 → 选高钾场景 → 点触发 → 断言 `mutate` 调用参数 `{scenarioId:"sbx-lab-critical-k"}`；注入 result → 断言路径检查器与 iframe 出现。Run: `npm test -- src/pages/sandbox/SandboxHost.test.tsx`。
- [ ] **Step 3: 提交**

---

## Task 7: 路由接线 + 守卫一致性

**Files:** Modify `router.tsx`、`routes.ts`；Test `routes.test.ts`

- [ ] **Step 1: router.tsx**：`const SandboxHost = lazy(() => import("@/pages/sandbox/SandboxHost"));` + `<Route path="/sandbox" element={<SandboxHost />} />`。

- [ ] **Step 2: routes.ts**：加条目（菜单 key `sandbox` 与后端 A1 Task6 一致）：

```ts
{ path: "/sandbox", title: "全真体验沙盘", breadcrumb: ["临床协同", "全真体验沙盘"],
  requireAuth: true, sectionKey: "clinical-collaboration", placement: "expert",
  requiredRoles: ["clinical-decision-user"], pageType: "advanced" },
```

- [ ] **Step 3: routes.test.ts**：现有机制读 `DefaultPermissionPolicyTest.java` 菜单快照与前端 `requiredRoles` 比对——确保 `sandbox` 菜单在后端快照（A1 已加）与前端 `requiredRoles` 角色一致。补一条断言：沙盘角色 `canAccessRoute('/sandbox')` 为真。Run: `npm test -- src/shared/config/routes.test.ts`　Expected: PASS（一致）。

- [ ] **Step 4: 提交**

---

## Task 8: 构建 + 守卫 + 端到端联调
- [ ] **Step 1:** `cd frontend && npm run build`　Expected: 成功。
- [ ] **Step 2:** `npm test`（前端全量）+ `npm run lint`　Expected: 全绿。
- [ ] **Step 3:** 守卫 `check-comment-zh`、`authenticity-guard --mode=all`、`config-boundary-guard --mode=inventory`、`git diff --check`。
- [ ] **Step 4（对真后端）:** 部署/本地起前后端，登录沙盘角色进 `/sandbox`，选高钾场景触发 → 右侧 iframe 显真危急值卡、底部 7-step 全 OK、采纳/拒绝 postMessage 回填左侧；截图入证据 `docs/release/evidence/.../sandbox/`。
- [ ] **Step 5: 提交**截图证据。

## 自审记录
- spec 覆盖：§4 前端组件、§6 数据录入（阶段A2 最小、后续扩展已注明）、§9 路径检查器、§10 嵌入体验、§11 前端守卫一致性、§22 阶段A 验收前端侧。
- 占位扫描：Task 5 注明"自由字段编辑留后续"为范围决策非占位；其余步骤含具体组件 props/测试断言。
- 类型一致性：`SandboxRunResponse`/`SandboxStepTrace`/`SandboxScenario` 跨 Task 1/2/3/6 一致；菜单 key `sandbox` 与 A1 Task6 后端一致。
