# CFGPKG-01 配置包中心页真实化审计记录

## 范围

- 页面：`frontend/src/pages/tenant/ConfigPackages.tsx`
- 页面样式：`frontend/src/pages/tenant/ConfigPackages.module.css`
- API hook：`frontend/src/shared/api/hooks.ts`
- 路由元数据：`frontend/src/shared/config/routes.ts`
- 卡片：`docs/cards/D2/CFGPKG-01.md`

## 真实化结论

- 配置包列表改为通过 `usePackages({ page, size, keyword, status })` 走 `/engine/pkg/packages` 服务端分页与筛选，不在前端用本地数组假筛。
- 页面改为 PageShell 六态：加载、错误、空、正常均有明确状态；无包时不显示旧草稿捷径。
- 发布流程复用 `StepFlow` 展示 7 步流：选模板/导入、自动校验、看影响、提交审核、10% 灰度、全量、证据/回滚。
- 首发模板、资产就绪门、组包资产、差异影响、离线包导入/导出、同步发布、失败/未接入站点、同步证据导出和回滚均消费既有 `/engine/pkg/**` 真实接口。
- 路由收紧到 `menu.config-packages` + `pkg.read` + `pkg.release`，角色限定实施工程师、医务处和医院管理员；直接全量发布仍按院级角色二次限制。
- 清理旧装饰性 utility class、旧“一键创建知识配置包草稿”文案和多主按钮式入口；页面只保留一个 PageShell 主动作“发布配置包”。

## 本地证据

- 红灯：`usePackages({ page, size, keyword, status })` 仍把对象当作页码，证明旧 hook 不支持服务端筛选。
- 红灯：`/config/packages` 缺 `pkg.read/pkg.release` 与角色限制。
- 红灯：页面源码缺 `StepFlow` 且含 `bg-gradient-to-br`、`rounded-2xl`、`text-slate-`、旧草稿文案等历史布局债务。
- 红灯：页面缺 PageShell 加载 / 错误 / 空状态。
- 绿灯：`npm test -- src/pages/tenant/ConfigPackages.test.tsx src/shared/api/hooks.test.ts src/shared/config/routes.test.ts src/pages/tenant/RulePathwayCleanliness.test.ts`，4 files / 63 tests 通过。
- 绿灯：`npm run typecheck` 通过。
- 绿灯：`npm run verify`，50 files / 290 tests 通过。
- 绿灯：`npm test -- src/pages/pages.smoke.test.tsx`，22 tests 通过。
- 绿灯：项目 Playwright 浏览器验收 `http://127.0.0.1:5175/config/packages`：标题 1、StepFlow 1、页面主按钮“发布配置包”1、失败同步证据 1、超时弹窗 0、console errors 0、失败响应 0；截图 `/tmp/medkernel-cfgpkg-01-config-packages.png`。
- 绿灯：`npm run build` 通过；保留既有 `vendor-antd` chunk 提示，归 `DEFER-003`，不冒领清零。
- 绿灯：`npm audit --omit=dev --json`，生产依赖漏洞 0。
- 绿灯：`mvn -q test`，186 reports / 1120 tests / failures 0 / errors 0 / skipped 0；PostgreSQL 15.18 与 Oracle 21.3 迁移到 V66。
- 绿灯：`node --test scripts/authenticity-guard.test.mjs scripts/config-boundary-guard.test.mjs scripts/migration-convention-guard.test.mjs`，34/34 通过。
- 绿灯：`scripts/check-comment-zh.sh`，0 fail / 0 warn。
- 绿灯：`git diff --check` 通过。
- 绿灯：提交后 `node scripts/authenticity-guard.mjs --mode=changed --base=origin/main` 扫描 3 个触碰文件，通过；`config-boundary-guard` / `migration-convention-guard` changed-mode 均无新增扫描文件并通过；`git diff --check origin/main..HEAD` 通过。

## 未冒领

- backlog 的“7 页面真实化”整行仍为 pending；本记录只覆盖 CFGPKG-01。
- 本卡不新增后端、不新增迁移；后端 PKG-01/API-10/SVC-PILOT-03 已提供真实发布、同步、资产准备接口。
- in-app browser 插件链路仍归 `DEFER-004`，本次使用项目 Playwright 对本地页面渲染做浏览器级替代验收，不冒领插件问题已关闭。
- 远端 PR CI 8/8 与 reviewer 验收未在本地证据中冒领。
