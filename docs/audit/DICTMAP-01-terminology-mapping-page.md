# DICTMAP-01 字典映射页真实化审计记录

## 范围

- 页面：`frontend/src/pages/tenant/TerminologyMapping.tsx`
- 页面样式：`frontend/src/pages/tenant/TerminologyMapping.module.css`
- API hook：`frontend/src/shared/api/hooks.ts`
- 路由元数据：`frontend/src/shared/config/routes.ts`
- 通用体验修正：`frontend/src/shared/ui/PageShell.tsx`、`frontend/src/shared/ui/PageExperienceShell.tsx`
- 卡片：`docs/cards/D2/DICTMAP-01.md`

## 真实化结论

- `/terminology/mapping` 消费 `/engine/terminology/**` 的标准字典、院内字典、候选、冲突与映射能力；知识包构建、发布、回滚统一走 `/engine/pkg/packages/**`。
- 高危候选显示红标；批量确认按钮在高危存在时禁用；高危逐条二次确认弹窗必须勾选并填写理由。
- 普通候选无高危时支持批量确认；确认 / 发布请求通过 `withStandardApiContext` 携带 `request_id`、`trace_id`、`tenant_id`、`user_id`、`role_codes`、`package_version`。
- 映射包发布通过 `StepFlow` 展示 7 步流，并支持构建、10% 灰度 / 全量发布、回滚弹窗。
- 冲突待裁、映射包证据、标准 / 院内字典统计均来自真实 hook；主表继续服务端分页、保存视图、异步导出、详情抽屉。
- 路由收紧到 `menu.terminology-mapping + term.read/write/publish`，角色限定 `it-ops`、`specialist`、`medical-affairs`。
- 清理旧只读 / 示例口径；新增源码守卫阻断 `read-only`、`experience sample`、内联 `style=` 回流；移动端标题和表格不再逐字竖排。

## 本地证据

- 红灯覆盖：缺失真实 terminology hooks、路由只靠菜单键、页面源码旧口径、高危候选未二次确认、映射包发布流缺灰度请求上下文等测试在实现前失败。
- 聚焦测试：`npm test -- src/pages/tenant/TerminologyMapping.test.tsx src/shared/api/hooks.test.ts src/shared/config/routes.test.ts src/pages/tenant/RulePathwayCleanliness.test.ts`（4 files / 65 tests）。
- 移动端与通用壳回归：`npm test -- src/pages/tenant/TerminologyMapping.test.tsx src/shared/ui/PageShell.test.tsx src/shared/ui/PageExperienceShell.test.tsx src/pages/pages.smoke.test.tsx`（4 files / 33 tests）。
- 前端全量：`npm run verify`（84 files / 600 tests）。
- 生产依赖审计：`npm audit --omit=dev --json`（生产依赖漏洞 0）。
- 构建：`npm run build` 通过；`vendor-antd` chunk 大小提示归 `DEFER-003`，未冒领清零。
- 浏览器：in-app Browser 真实走通登录、首次改密、MFA、术语映射及桌面 / 窄屏主流程；页面运行无阻断错误。
- T-GATE：守卫自测 40 项通过；真实性全量扫描 1540 文件、配置边界全量扫描 1452 文件、迁移扫描 15 文件均无阻断项。
- 中文注释与差异：`scripts/check-comment-zh.sh`（0 fail / 0 warn）、`git diff --check`。

## 未冒领

- `DEFER-004` 仍仅保留项目 Playwright 截图工具链收口，不影响本轮 in-app Browser 真实主流程证据。
- `DEFER-010` 10 万级标准 / 院内字典压测证据仍 open，未伪造大规模数据结论。
