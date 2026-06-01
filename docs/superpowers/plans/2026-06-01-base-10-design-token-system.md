# BASE-10 设计 Token 系统实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**目标：** 收口 BASE-10：`theme.ts` 成为唯一品牌色 token 源，5 主题模式真实可用，老年医生模式达到 ≥16pt，`.module.css` 只引用 token 变量，stylelint / 测试门禁阻断硬编码回流，并补用户级主题偏好持久化。

**架构：** 前端继续以 Ant Design `ConfigProvider` 为运行时 token 注入点，新增 `createThemeConfig()` 作为主题配置单一入口；登录页保留本地主题可用性，登录后 `ThemeSwitcher` 通过 `/api/v1/experience/theme-preference` 同步用户级偏好。后端复用 `experience` 包与 `RequestContext`，新增 `mk_experience_user_pref` 五方言表保存租户 + 用户偏好。CSS 层以 `--mk-unit` / `--mk-space-*` / `--ant-*` 变量替代 `.module.css` 原始 px/颜色字面量。

**技术栈：** React 18、TypeScript、Ant Design 5、Zustand、React Query、Spring Boot 3、Spring Data JDBC、Flyway、Vitest、stylelint。

---

## 当前核查结论

- 已有 `frontend/src/shared/config/theme.ts`，但只定义基础 token 与护眼背景；老年模式在 `App.tsx` 中写死 `fontSize: 16`，未达到 16pt。
- 已有 `ThemeSwitcher` 和 `themeStore`，支持 default / elder / dark / eye / system，当前只写 `medkernel.theme.mode` localStorage。
- 登录页已经展示主题切换器，BASE-10 必须保持登录前可切换，因此后端偏好只能作为登录后同步，不得让登录页依赖鉴权接口。
- 当前没有 stylelint 依赖和脚本；CI 只跑 ESLint / Prettier / 前端测试。
- `.module.css` 中没有旧 `ColumnManager` / `medkernel.view.*` 口径，但仍有大量 px 字面量，需要清零。

## 任务 1：主题配置与老年模式红绿测试

- [x] 在 `frontend/src/shared/config/theme.test.ts` 写红灯测试：`createThemeConfig("elder")` 的 `token.fontSize` 至少为 22，且 5 个模式均返回 `cssVar: true`。
- [x] 将 `frontend/src/shared/config/theme.ts` 扩展为主题配置单一入口：导出 `ThemeMode`、`THEME_MODE_OPTIONS`、`createThemeConfig(mode, systemPrefersDark)`。
- [x] 修改 `frontend/src/app/App.tsx` 只调用 `createThemeConfig()`，删除散落在组件里的主题算法判断。
- [x] 跑 `npm test -- theme.test.ts themeStore.test.ts ThemeSwitcher.test.tsx`，确认红绿通过。

## 任务 2：用户级主题偏好后端持久化

- [x] 写后端红灯测试：`UserPreferenceServiceTest` 验证同租户同用户同键 upsert、跨用户隔离、只允许主题模式；`ThemePreferenceControllerSecurityTest` 验证鉴权与租户上下文。
- [x] 新增 `UserPreference` / `ThemePreferenceRequest` / `ThemePreferenceResponse` / `UserPreferenceRepository` / `UserPreferenceService` / `ThemePreferenceController`。
- [x] 新增 V35 五方言迁移 `mk_experience_user_pref`，带中文 COMMENT、租户索引、唯一约束 `(tenant_id, user_id, pref_key)`。
- [x] 更新迁移契约测试到 35，并加入 `mk_experience_user_pref` 字段与注释断言。
- [x] 跑后端聚焦测试与 `node scripts/migration-convention-guard.mjs --mode=files ...V35__experience_user_preference.sql`。

## 任务 3：前端主题偏好同步

- [x] 在 `frontend/src/shared/api/hooks.test.ts` 写红灯测试：GET/PUT `/experience/theme-preference`，PUT 只提交合法 mode。
- [x] 在 `hooks.ts` 增加 `fetchThemePreference` / `saveThemePreference` / `useThemePreference` / `useSaveThemePreference`。
- [x] 更新 `ThemeSwitcher`：默认登录后远端同步，`syncRemote={false}` 时只用本地；切换时先更新本地，再尝试保存后端，失败保持本地且不打断登录页。
- [x] 登录页传 `syncRemote={false}`，主应用布局保持默认同步。
- [x] 扩展 `ThemeSwitcher.test.tsx` 覆盖远端加载、远端保存、本地 fallback。

## 任务 4：module.css token 化与 stylelint 门禁

- [x] 在 `visualDebtGuard.test.ts` 写红灯测试：扫描所有 `.module.css`，禁止 hex/rgb/hsl 和 `px` 字面量。
- [x] 新增 stylelint 依赖、`stylelint.config.mjs`、`npm run stylelint`，并让 `npm run verify` 与 CI `frontend-lint` 执行 stylelint。
- [x] 在 `frontend/src/app/index.css` 定义 `--mk-unit`、常用间距、尺寸、线宽、阴影和模糊 token。
- [x] 逐个清理 `.module.css` 中 px：固定尺寸改 `calc(var(--mk-unit) * n)` 或语义 `--mk-*` token，断点改 `em`，颜色继续走 `var(--ant-*)` / `var(--mk-*)`。
- [x] 跑 `npm run stylelint`、`npm test -- visualDebtGuard.test.ts`、`rg "px\\b|#[0-9a-fA-F]{3,8}|rgb\\(|hsl\\(" frontend/src/**/*.module.css`，确认无命中。

## 任务 5：文档、验收与 PR 收口

- [x] 新增 `docs/audit/design-token-inventory.md`，列出 5 主题、token 源、CSS 变量规则和后续 AI 禁止恢复硬编码。
- [x] 勾选 `docs/cards/D0/BASE-10.md` FR/AC，更新 `docs/backlog.md` 和 `docs/_HANDOFF.md`。
- [x] 跑前端全量：`npm run typecheck`、`npm run lint`、`npm run stylelint`、`npm run format:check`、`npm test`、`npm run build`。
- [x] 跑后端全量：`mvn -B -q test`。
- [x] 跑 T-GATE：真实性、配置边界、迁移 changed、中文注释、空白检查。
- [x] 用浏览器验收 `/login` 与登录后主应用：5 主题均可切换，登录页不依赖鉴权接口，老年模式字号明显放大且页面不崩。
- [ ] 提交中文 commit、推送、创建 PR，远端 CI 通过并合并后再领取下一阶段。
