# ADAPTER-01 适配器中心页真实化审计记录

## 范围

- 页面：`frontend/src/pages/tenant/AdapterHub.tsx`
- 页面样式：`frontend/src/pages/tenant/AdapterHub.module.css`
- API hook：`frontend/src/shared/api/hooks.ts`
- 路由元数据：`frontend/src/shared/config/routes.ts`
- 卡片：`docs/cards/D2/ADAPTER-01.md`

## 真实化结论

- `/adapter/hub` 从旧 Webhook / Launch Token 控制台收口为“适配器中心”单目标页面，消费真实 `/api/v1/engine/integration/**` hook。
- 适配器目录展示 `HEALTHY`、`NOT_CONNECTED`、`MISCONFIGURED` 等后端状态；健康诊断只展示返回事实，断连保持 `NOT_CONNECTED`，不伪造在线。
- 字段映射缺口来自 `AdapterHubStatus.sources.gaps`；死信页只允许 `FAILED` 重试、只允许 `DEAD_LETTER` 重放。
- 数据质量看板调用 `/data-quality/reports`，展示必填率、映射率、时效率、断连和配置非法数量。
- 接入向导调用 `/onboardings` 与 `/onboardings/{id}/advance`，推进上线必须携带字段映射、健康状态和阻塞项证据文本。
- 路由收紧到 `menu.adapter-hub + integration.read/write/execute`，角色限定 `it-ops`、`implementation`。
- 页面补齐 `PageExperienceShell`、`PageState` 六态、`StepFlow` 配置类 7 步流、唯一主按钮“新增适配器”，并清理旧 Webhook 沙箱、Launch Token、Tailwind utility 和无用 CSS 类。

## 本地证据

- 红灯覆盖：旧页面未使用 `StepFlow`、路由仅靠菜单键、缺接入生命周期 hook、旧 Webhook / Launch Token 文案仍在源码、六态缺真实查询来源时测试失败。
- 聚焦测试：`npm test -- src/pages/tenant/AdapterHub.test.tsx src/shared/api/hooks.test.ts src/shared/config/routes.test.ts src/pages/tenant/RulePathwayCleanliness.test.ts`（4 files / 67 tests）。
- 页面 smoke：`npm test -- src/pages/pages.smoke.test.tsx`（22 tests）。
- 前端全量：`npm run verify`（51 files / 309 tests）。
- 生产依赖审计：`npm audit --omit=dev --json`（生产依赖漏洞 0）。
- 构建：`npm run build` 通过；`vendor-antd` chunk 大小提示归 `DEFER-003`，未冒领清零。
- 浏览器：in-app browser 打开 `http://127.0.0.1:5173/adapter/hub` 在无登录会话时正确重定向到 `/login`，console errors 0；未伪造登录会话或 token。AdapterHub 受保护页面完整交互由组件级红绿测试覆盖。
- T-GATE：`node --test scripts/authenticity-guard.test.mjs scripts/config-boundary-guard.test.mjs scripts/migration-convention-guard.test.mjs`（34/34）。
- 中文注释与差异：`scripts/check-comment-zh.sh`（0 fail / 0 warn）、`git diff --check` 与 `git diff --check origin/main...HEAD`。

## 未冒领

- `DEFER-003` React Router / rc-menu 测试警告与 `vendor-antd` 大 chunk 仍 open。
- `DEFER-004` 浏览器截图链路仍 open；本轮只声明路由鉴权重定向和 console errors 0。
- 真实院方 HIS/EMR/LIS/PACS/医保/病案/随访连接器依赖客户现场资源；当前页面只展示后端已登记适配器和诚实 `NOT_CONNECTED` 状态，不声称外部系统已真实连通。
- 本轮未新增后端 / 迁移；真实后端 API 由 `INTEG-01`、`SVC-PILOT-02`、`SVC-INTEGRATION-01` 提供。
- 远端 PR、CI、reviewer 签字须在后续步骤取得，不在本审计记录里提前冒领。
