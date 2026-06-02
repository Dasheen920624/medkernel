# D1 工作台域级验收报告

> 日期：2026-06-02  
> 范围：D1 `INFRA-09` / `WORKBENCH-01` / `WORKBENCH-02` / `D1-验收`  
> 当前环境范围：PostgreSQL + Oracle；达梦 / 人大金仓等国产化真实运行环境按 `DEFER-001` 后移最终适配，不阻塞 D1。

## 结论

D1 工作台域本轮按“登录后第一屏真实可用、演示校验真实、不新增独立工作台接口、生产无演示假页”完成域级验收收口。

- `INFRA-09` 已由 PR #242 合入，生产路由无 `StepFlowDemo`，并有真实性门禁防回流。
- `WORKBENCH-01` 已由 PR #243 / #244 分两批合入，工作台复用现有来源 API、按角色默认视图呈现、六态与诚实降级齐全。
- `WORKBENCH-02` 已由 PR #245 合入，`/workbench/demo-validation` 以同槽 Tab 提供真实演示就绪度自检，不新增 `/api/v1/workbench/*`。
- 本轮 D1 域级验收新增自动化锁定：`/dashboard` 显式绑定 13 个客户角色，`WorkbenchPanel` 对 13 角色逐一渲染正确默认首屏。

## 验收矩阵

| 域级要求 | 证据 | 结论 |
|---|---|---|
| 13 角色登入后默认落 `/dashboard`，各见正确默认视图 | `frontend/src/shared/config/routes.test.ts` 覆盖 `/dashboard` 显式 13 角色；`frontend/src/widgets/WorkbenchPanel.test.tsx` 覆盖 13 角色首屏标题与默认卡片 | 通过 |
| 六态可达：正常 / 加载 / 空 / 错误 / 无权限 / 部分成功 | `WorkbenchPanel.test.tsx` 覆盖加载、无权限、错误、部分成功、未建域诚实空态、正常；`DemoValidation.test.tsx` 覆盖无权限、错误 traceId、部分成功、正常 | 通过 |
| 摘要指标可下钻；未建域诚实说明，不 404、不假链接 | `WorkbenchPanel.test.tsx` 覆盖最近变化下钻、未建质控域详情抽屉、无 `/api/v1/workbench/*` | 通过 |
| 外部依赖断连 / 关模型诚实降级 | `WorkbenchPanel.test.tsx` 与 `DemoValidation.test.tsx` 使用运行底座真实状态枚举 `NOT_CONNECTED` / `MODEL_DISABLED`，渲染阻塞或未启用，不计入可演示 | 通过 |
| 演示与校验页真实自检、不假绿 | `DemoValidation.test.tsx` 覆盖真实计数、中文阻塞原因、去修复链接、临床角色无权限和只读自检 | 通过 |
| 生产路由无 `StepFlowDemo` / 无演示假页回流 | `scripts/authenticity-guard.test.mjs` 与真实性门禁 `frontend.production-demo-route`；`routes.test.ts` 覆盖 `/config/packages/demo` 不在生产元数据 | 通过 |
| T-GATE 前后端真实性门禁 | 本地前端聚焦、全量 verify / build、真实性门禁、中文注释与 diff 检查均已复跑；远端 CI 以本 PR 为准 | 通过，待 CI 复核 |

## 本轮新增验收锁

1. `/dashboard` 不再依赖空 `requiredRoles` 的隐式放行，改为从 `ROLE_OPTIONS` 派生 13 个客户角色。
2. 路由测试逐角色验证持有 `menuKeys=["workbench"]` 时可访问 `/dashboard`。
3. 工作台组件测试逐角色验证标题、默认卡片、权限态和来源请求启用状态，防止后续改动让某个角色首屏缺失或误入无权限态。

## 待处理问题

本轮没有新增阻塞 D1 主链路的问题。以下问题保持登记，不阻塞 D1 → D2：

- `DEFER-001`：国产化真实运行环境适配后移 D6/GA。
- `DEFER-002`：前端依赖审计 7 个告警。
- `DEFER-003`：前端测试 / 构建输出噪声。
- `DEFER-004`：Codex in-app browser 不稳定，当前以 Playwright fallback 留证。
- `DEFER-005`：真实院方 IdP / JWKS / 国密证书链缺失。
- `DEFER-006`：历史迁移中文 COMMENT 覆盖缺口。
- `DEFER-007`：非当前卡历史页面技术化文案残留。

关闭标准仍以 [待处理问题清单](deferred-issues.md) 为准，不得在未提交证据前写成已通过。

## 本轮验证证据

- `npm test -- --run src/widgets/WorkbenchPanel.test.tsx src/pages/workbench/DemoValidation.test.tsx src/shared/config/routes.test.ts src/shared/config/menu.test.ts src/app/router.test.tsx`：5 文件 / 46 测试通过。
- `npm run verify`：lint / stylelint / lint-rules / format / typecheck / 41 文件 / 205 测试通过；`DEFER-003` 噪声仍 open。
- `npm run build`：通过；`vendor-antd` chunk 大小提示仍归 `DEFER-003`。
- `node --test scripts/authenticity-guard.test.mjs scripts/migration-convention-guard.test.mjs scripts/config-boundary-guard.test.mjs`：34/34 通过。
- `node scripts/authenticity-guard.mjs --mode=all`：扫描 747 文件，通过。
- `scripts/check-comment-zh.sh --mode=full`：退出 0；本轮新增 / 触碰项 OK，历史 V1/V3/V7/V10 COMMENT GAP 继续归 `DEFER-006`。
- `git diff --check`：通过。
- Playwright fallback `http://127.0.0.1:5176/dashboard` + `/workbench/demo-validation`：工作台与演示页可见，控制台错误 `[]`，页面错误 `[]`，请求仅 `/security/me`、`/experience/theme-preference`、`/auth/session`、`/system/operations`、`/compliance/audit/events`，无 `/medkernel/api/v1/workbench/*`。
