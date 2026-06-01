# BASE-09 硬编码业务示例与工作台假闭环清理 PR13 记录

> 日期：2026-06-01  
> 范围：BASE-09 第十三批存量净化，不宣称 BASE-09 全部完成。

## 本批清理

- `frontend/src/widgets/WorkbenchPanel.tsx` 删除本地 `todoMock`、固定四指标、客户验收剧本入口和相关无用导入；只展示真实租户生命周期面板，未接入的工作台聚合数据用空态诚实说明。
- `frontend/src/pages/clinical/CdssFatigue.tsx` 删除 `authorityScore || 90` 与 `evidenceLevel || "Class I"` 默认兜底；后端未提供时展示“未提供”。
- `frontend/src/pages/tenant/AdapterHub.tsx` 删除新建适配器 / Webhook 表单的假系统 ID、假系统名、假 URL、假危急值通道预填；改为真实输入提示。
- `frontend/src/pages/tenant/ConfigPackages.tsx` 删除灰度发布文案中的具体科室示例。
- `frontend/src/pages/tenant/TenantOnboarding.tsx` 清理“模拟页面主体”注释。
- 删除未引用的 `frontend/src/features/demo-mode/DemoModeToggle.tsx`，避免保留 fixture 注入和客户验收剧本空壳。
- `scripts/authenticity-guard.mjs` 将生产扫描范围扩到 `widgets`，新增 `frontend.local-demo-workflow`，并补充 `神经内科 / 卒中 / 危急值 / Class I` 等前端医学硬编码阻断项。

## 红绿证据

- 红灯：`node --test scripts/authenticity-guard.test.mjs` 新增用例先失败，证明旧门禁无法拦截工作台假闭环和硬编码医学残留。
- 红灯：`npm test -- src/pages/pages.smoke.test.tsx` 新增工作台空态断言先失败，证明页面仍显示旧“本周建议动作 / 演示与校验”。
- 绿灯：`node --test scripts/authenticity-guard.test.mjs` 18/18 通过。
- 绿灯：`npm test -- src/pages/pages.smoke.test.tsx` 21/21 通过。

## 本地验证

- `npm run lint` 通过；仍有 11 个既有 warning，均不在本批触碰文件。
- `npm run format:check` 通过。
- `npm run typecheck` 通过。
- `npm run build` 通过；仍有既有 `vendor-antd` 大包 warning。
- `npm test` 32 个测试文件 / 122 个用例通过。
- `node scripts/authenticity-guard.mjs --mode=inventory` 通过，扫描 575 个文件，0 阻断项。
- `node --test scripts/config-boundary-guard.test.mjs scripts/migration-convention-guard.test.mjs scripts/authenticity-guard.test.mjs` 24/24 通过。
- `mvn -B -q test` 通过；本地 Docker Testcontainers 跑到 PostgreSQL / Oracle 迁移烟测，33 版迁移通过。
- 生产路径残留 grep 通过：`todoMock / 演示与校验 / 客户验收剧本 / 神经内科 / 卒中 / 危急值 / Class I / DemoModeToggle / demo-mode` 在 `frontend/src` 与后端生产 Java 路径无命中。
- `git diff --check` 通过。
- 浏览器真实 dev 登录流：使用 dev profile 种子账号登录到 `/dashboard`，工作台出现“真实工作台聚合数据待接入 / 暂无真实工作台聚合数据”；旧“本周建议动作”“演示与校验”“试点准备 · 在径科室”计数均为 0，控制台错误 0。

## 残留与边界

- 本批只清理已发现的前端硬编码业务示例、工作台假闭环和未引用演示模式空壳。
- 不宣称配置包离线导入 / 导出、包完整性校验、BASE-09 全量 FR/AC 或 GA 域级验收完成。
- 后续不得恢复 `demo-mode` fixture 注入、工作台本地待办、客户验收剧本或医学默认兜底；如确需演示能力，必须走独立授权的真实演示环境与明确水印，不得进入生产路径。
