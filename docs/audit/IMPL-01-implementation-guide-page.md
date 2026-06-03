# IMPL-01 客户实施向导页真实化审计记录

## 范围

- 页面：`frontend/src/pages/tenant/ImplementationGuide.tsx`
- API hook：`frontend/src/shared/api/hooks.ts`
- 路由元数据：`frontend/src/shared/config/routes.ts`
- 卡片：`docs/cards/D2/IMPL-01.md`

## 真实化结论

- 删除旧 9 步前端推导与成功计划推进按钮，不再用 `success-plan` 在前端推断实施进度。
- 新增 `useImplementationSteps`，唯一读取 `/engine/tenant/implementation-steps`，步骤标题、状态、阻塞原因、证据与跳转目标均来自租户引擎后端。
- 阻塞步骤保留 `BLOCKED` 展示，显示后端 blocker，不把缺项显示为已完成。
- 页面补齐 PageShell 加载 / 空 / 错误 / 正常路径，并保留部分就绪告警；配置流程展示复用 `StepFlow`。
- 路由收紧到 `menu.implementation-guide` + `tenant.read`，角色限定为实施工程师、平台管理员、医院管理员。

## 本地证据

- 红灯：`useImplementationSteps is not a function`，证明旧 hook 缺失。
- 红灯：页面未显示后端返回的“组织树 / 阻塞原因 / targetPath”，证明旧页面未消费真实步骤。
- 红灯：`/onboarding/guide` 仅有菜单权限，缺 `tenant.read` 与角色限制。
- 绿灯：`npm test -- src/pages/tenant/ImplementationGuide.test.tsx src/shared/api/hooks.test.ts src/shared/config/routes.test.ts src/pages/tenant/RulePathwayCleanliness.test.ts`，4 files / 47 tests 通过。
- 绿灯：`npm run typecheck` 通过。
- 绿灯：`npm run verify`，49 files / 278 tests 通过。
- 绿灯：`npm audit --omit=dev --json`，生产依赖漏洞 0。
- 绿灯：`npm run build` 通过。
- 绿灯：后端 `mvn -q test` 通过，186 reports / 1120 tests / 0 failures / 0 errors / 0 skipped；PostgreSQL 15.18 与 Oracle 21.3 迁移到 V66。
- 绿灯：`scripts/check-comment-zh.sh`、`git diff --check` 通过。
- 绿灯：提交后 `node scripts/authenticity-guard.mjs --mode=changed --base=origin/main` 扫描 3 个前端文件，0 阻断；`config-boundary-guard` / `migration-convention-guard` 本卡无适用文件且通过；`git diff --check origin/main..HEAD` 通过。

## 未冒领

- backlog 的“7 页面真实化”整行仍为 pending；本记录只覆盖 IMPL-01。
- 本卡不新增后端、不新增迁移；后端 SVC-PILOT-01 已提供真实 `implementation-steps`。
- changed-mode T-GATE 只证明本卡触碰范围通过，不用它冒领整行页面真实化完成。
