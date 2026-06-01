# AUTH-02 · 登录页

> 读卡前置：[核心 CONSTITUTION](../../CONSTITUTION.md) + [D0 域简报](_brief.md) + [体验契约](../../EXPERIENCE_CONTRACT.md)（页面卡）。
> 迁移来源（覆盖矩阵锚点）：用户与双模鉴权设计 [2026-05-29-user-auth-dual-mode-design](../../superpowers/specs/2026-05-29-user-auth-dual-mode-design.md) §6 前端 / §4 cookie · 宪法 §8 颜色 token · 体验规范 §3 角色体验。
> 现状：`frontend/src/pages/Login.tsx` 已建（#146 真提交闭环 + #148 登录租户字段 + 显示修复）；本卡为其在卡体系的家 + 六态/无障碍/双模折叠补全。

## 身份
- 卡 ID：AUTH-02（backlog v8.2 D0 新增）
- 域：D0 登录域 / 平台脊柱
- 关联场景：S14 用户、权限与合规（系统入口）
- 依赖卡：[AUTH-01](AUTH-01.md)（登录闭环后端）· [BASE-10](BASE-10.md)（token/显示修复）· [BASE-06](BASE-06.md)（路由）· [INFRA-04](INFRA-04.md)（登出对称）
- 工作量：3d
- owner / reviewer：Codex / 待 reviewer（owner ≠ reviewer）

## 目标

交付**用户登录页面**：双模式登录 UI（用户名/密码 + 租户字段 + MFA/SSO 折叠）、httpOnly cookie 闭环、真实错误显示、六态、显示修复（弃系统色走 token），并以"不同角色登录看不同菜单"证明权限真实生效——这是登录域的字面入口、D0-验收"13 角色登入"的前提。

## 功能要求（原子可测条目）

- [x] **FR-1 双模式登录 UI**：用户名/密码 + **租户字段**（#148）+ MFA/SSO 折叠区（内网委托 IdP 入口）；真提交 `POST /auth/login`（[AUTH-01](AUTH-01.md)）→ 200 跳 `/dashboard`。
- [x] **FR-2 httpOnly 闭环**：`apiClient` `withCredentials:true` + 自动附 `X-XSRF-TOKEN`；401 → 跳 `/login`（与 [INFRA-04](INFRA-04.md)/[INFRA-08](INFRA-08.md) 同源）。
- [x] **FR-3 显示修复**：`Login.module.css` 弃用 `Canvas/CanvasText/currentColor` 系统色与硬编码颜色兜底，全部走 Antd token / CSS 变量；主题入口进入布局栅格，避免老年模式遮挡登录卡片。
- [x] **FR-4 六态 + 真实错误**：登录提交加载、接口错误、字段错误、统一身份状态加载 / 错误 / 禁用 / 未接入 / 已接入均据实展示；登录失败走共享 `ProblemDetail` 解析，含中文 reason + traceId。
- [x] **FR-5 角色驱动**：登录成功只进入 `/dashboard`；登入后的菜单 / 直接访问仍由 `/security/me` 的 `menuKeys` 和 [INFRA-05](INFRA-05.md) 二级菜单矩阵驱动，本卡补路由与登录页回归，不伪造 13 角色结果。
- [x] **FR-6 无障碍/多主题**：登录页接入 `ThemeSwitcher` 5 主题模式（默认 / 老年医生 / 暗黑 / 护眼 / 跟随系统），`main` 暴露中文 `aria-label` 与提交忙碌态；老年医生模式控件高度经浏览器验收为 52px。

## 接口契约 / 页面契约
### 接口契约
- 消费 [AUTH-01](AUTH-01.md) `/auth/login` `/auth/logout` + `/me`（当前用户画像，[INFRA-04](INFRA-04.md)）。

### 页面契约（页面卡）
- 路由元数据：`/login`（**未认证可达**，在 AppLayout 之外；非业务二级菜单，不占 27 菜单槽）。
- 结构：登录页 `PageShell` 变体（hero 区 + 表单区）+ 六态；MFA/SSO 折叠区默认收起。
- 主按钮 ≤1：单"登录"主按钮（核心 #6）；MFA/SSO 为次级折叠。
- 表单：用户名/密码/租户字段，`Form.Item` 字段级校验回显（[INFRA-03](INFRA-03.md)）。
- 样式：仅引用核心 §5 token + 体验契约组件；**禁系统色/硬编码 hex**（本卡正是修此历史违反）。

## 数据与迁移
N·A —— 前端页面；消费 [AUTH-01](AUTH-01.md) 凭证与会话，不落业务表。

## 视角清单（11 视角逐条）
1. **产品架构**：登录页是系统唯一入口；登入↔登出（[INFRA-04](INFRA-04.md)）对称闭环。
2. **产品体验**：★本卡主战场 —— 一页一目标（单登录按钮）+ 六态 + 双模折叠 + 显示修复 + 无障碍（核心 §16、体验契约）。
3. **系统与数据架构**：withCredentials + CSRF 头；401 跳转同源处理。
4. **临床医疗安全**：N·A —— 但医生快速安全登入/登出防顶替（呼应 [INFRA-08](INFRA-08.md)）。
5. **知识与数据治理**：N·A。
6. **安全合规与监管**：token 仅 httpOnly cookie（JS 读不到）；错误不泄露存在性（[AUTH-01](AUTH-01.md)，核心 §8）。
7. **集团化与多租户治理**：租户字段 + 角色驱动菜单（不同租户/角色看不同，[BASE-01](BASE-01.md)/[INFRA-05](INFRA-05.md)）。
8. **集成与互操作**：MFA/SSO 折叠区承载内网委托 IdP 登录入口（[AUTH-01](AUTH-01.md) DELEGATED）。
9. **运维 / SRE / 国产化**：5 主题适配国产化终端；离线内网形态登录页可用。
10. **质量与真实性审计**：★修历史违反——去假"未接入"提示、弃系统色走 token（核心 #18/§5，门禁 INFRA-01）。
11. **AI / 模型治理与可降级**：N·A —— 天然 B0。

## 适用不变量
- 命中核心约束：**#6 一页一目标** · **#8/§5 颜色走 token（修系统色违反）** · **#16 六态/体验契约** · **§14 中文/真实错误**。
- 本卡落点：双模式登录 UI + httpOnly 闭环 + 显示修复 + 六态 + 角色驱动菜单，把登录页从"#139 死胡同 + 显示失明"治成真实可用、按角色体验的系统入口。

## 验收 + 验证
- [x] **AC-1（FR-1/2）**：登录表单提交成功 → 跳 `/dashboard`；httpOnly cookie / CSRF 由 [AUTH-01](AUTH-01.md) 后端闭环种下；401 → 跳 `/login`。
- [x] **AC-2（FR-3）**：登录页左右文字在深/浅色环境均高对比；`Login.module.css` grep 无系统色/无 hex 字面量（门禁绿）。
- [x] **AC-3（FR-4）**：登录失败显真实中文错误 + traceId（无假"身份认证尚未接入"提示）；统一身份未接入只展示后端状态并禁用方式按钮。
- [x] **AC-4（FR-5）**：登录页只负责进入 `/dashboard`；登入后菜单继续由 `/security/me.menuKeys` 驱动，13 角色全量登入扫验归 D0-验收统一执行。
- [x] **AC-5（FR-6）**：登录页切 5 主题（含老年模式 ≥16pt）正常。
- 关联 A1–A9：A6 合规运维（登录入口）。
- T-GATE：前端门禁全绿（无系统色/无 hex/无假提示/无 localStorage token）。
- B0 验收：纯前端 + 确定性后端，天然 B0。

## 完工证据
- 代码 permalink：`frontend/src/pages/Login.tsx` / `Login.module.css` / `Login.test.tsx`，`frontend/src/shared/api/hooks.ts` / `hooks.test.ts`，`frontend/src/app/router.test.tsx`，`frontend/src/pages/pages.smoke.test.tsx`。
- 测试：登录提交→跳 dashboard · 首登改密 / MFA → bootstrap · 登录失败真实错误 · 统一身份状态折叠 · 登录页 token 化 / 布局守卫 · 401→login 路由守卫 · 页面 smoke。
- 审计员签字：待 PR reviewer（owner ≠ reviewer）。

## 本轮完成记录（2026-06-02，`codex/auth-02-login-experience`）

- 登录页交互：主题切换入口移入布局栅格，`main` 增加中文 `aria-label` 与 `aria-busy`；提交时禁用表单；统一身份折叠区实时读取 `/auth/delegated/status`，后端返回未开放 / 未接入时只展示状态并禁用 OIDC/CAS/SAML/国密 CA 按钮，不再出现旧的“CAS（待院方配置）”假入口。
- 设计收口：`Login.module.css` 无 `Canvas` / `CanvasText` / `currentColor` / hex 字面量；统一身份方式改为单列，避免窄屏和老年模式挤压。
- 真实接口核查：本地后端 H2 dev profile 启动后，`GET http://127.0.0.1:18080/medkernel/api/v1/auth/delegated/status` 返回 `mode=PLATFORM`、`enabled=false`、`status=DISABLED`、providers `OIDC,CAS,SAML,国密CA`，与页面禁用展示一致。真实院方 IdP / JWKS / 国密证书链仍登记为 `DEFER-005`，不得写成已接入。
- 本地验证：`npm test -- --run src/pages/Login.test.tsx` 11/11 通过；`npm run verify` 39 files / 181 tests 通过；`npm run build` 通过但保留 `vendor-antd` chunk 提示（`DEFER-003`）。
- 浏览器核查：Codex in-app browser 当前无可用 `iab` 实例，已登记 `DEFER-004`；本轮使用项目 Playwright fallback 验证 `http://127.0.0.1:5175/login` 桌面 / 移动端：无横向溢出、无控制台错误、老年医生控件高度 52px、主题入口不遮挡登录卡、OIDC 按钮禁用。截图位于 `/tmp/auth02-login-desktop.png` 与 `/tmp/auth02-login-mobile.png`。

## 大卡工序（3d，前端）
- PR1：Login.tsx 真提交闭环 + withCredentials/CSRF + 401 跳转 + 显示修复（token 化）→ AC-1/2/3。
- PR2：双模折叠 + 租户字段 + 角色驱动菜单验证 + 5 主题/无障碍 → AC-4/5。
