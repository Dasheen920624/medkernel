# TENANT-01 租户开通页真实化审计记录

## 范围

- 页面：`frontend/src/pages/tenant/TenantOnboarding.tsx`
- API hook：`frontend/src/shared/api/hooks.ts`
- 路由元数据：`frontend/src/shared/config/routes.ts`
- 卡片：`docs/cards/D2/TENANT-01.md`

## 真实化结论

- 组织树查询 / 新增从旧兼容 `/tenant/org-units` 切到 `/engine/org/org-units`，不再让新前端依赖旧入口。
- 新增 `useOnboardingReadiness` 与 `useActivateOnboardingReadiness`，页面读取 `/engine/tenant/onboarding-readiness` 并只通过 `/engine/tenant/onboarding-readiness/activate` 执行开通门禁。
- 页面顶部展示真实就绪进度、阻塞项和检查时间；后端返回 `ready=false` 时唯一主按钮“开通租户”保持禁用，不允许前端强开。
- 组织树保留七层直接父级候选过滤；组织创建后复查组织列表和开通就绪门。
- 路由收紧到 `menu.tenant-onboarding` + `tenant.read`，角色限定为实施工程师、平台管理员、医院管理员；开通写动作继续由后端 `tenant.write` 守门。
- 清理旧 “sandbox / 高保真 / 原子持久化” 等低质页面语义，品牌配置改成品牌信息工作区。

## 本地证据

- 红灯：`useOnboardingReadiness is not a function`，证明旧 hook 缺失。
- 红灯：`useActivateOnboardingReadiness is not a function`，证明旧页面无法调用真实开通门禁。
- 红灯：组织 hook 仍调用旧 `/tenant/org-units`。
- 红灯：`/tenant/onboarding` 仅有菜单权限，缺 `tenant.read` 与角色限制。
- 红灯：页面未显示后端 readiness blockers，且没有“开通租户”主按钮。
- 绿灯：`npm test -- src/pages/tenant/TenantOnboarding.test.tsx src/shared/api/hooks.test.ts src/shared/config/routes.test.ts src/pages/tenant/RulePathwayCleanliness.test.ts`，4 files / 51 tests 通过。
- 绿灯：`npm run typecheck` 通过。
- 绿灯：`npm run verify`，50 files / 285 tests 通过。
- 绿灯：`npm run build` 通过。
- 绿灯：`npm audit --omit=dev --json`，生产依赖漏洞 0。
- 绿灯：`mvn -q test`，186 reports / 1120 tests / failures 0 / errors 0 / skipped 0；PostgreSQL 15.18 与 Oracle 21.3 迁移到 V66。
- 绿灯：提交后 `origin/main..HEAD` changed-mode T-GATE 通过：真实性门禁扫描 3 个触碰文件，配置边界 / 迁移规约无本卡相关新增，中文注释与 diff 检查通过。

## 未冒领

- backlog 的“7 页面真实化”整行仍为 pending；本记录只覆盖 TENANT-01。
- 本卡不新增后端、不新增迁移；后端 SVC-PILOT-01 已提供真实 `org-units` / `onboarding-readiness` / `activate`。
- 仍需远端 PR CI 8/8 与 reviewer 验收；本地证据不冒领远端完成。
