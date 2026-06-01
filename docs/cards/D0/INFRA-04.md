# INFRA-04 · 退出登录 UI

> 读卡前置：[核心 CONSTITUTION](../../CONSTITUTION.md) + [D0 域简报](_brief.md) + [体验契约](../../EXPERIENCE_CONTRACT.md)（页面卡）。
> 迁移来源（覆盖矩阵锚点）：详规 §1.1 页面统一结构 · 体验规范 §3 角色体验标准 · 登录闭环（既有 #146 平台账号登录）。

## 身份
- 卡 ID：INFRA-04
- 域：D0 登录域 / 平台脊柱
- 关联场景：S14 用户、权限与合规（登录闭环收尾）
- 依赖卡：[BASE-06](BASE-06.md)（AppLayout/Header）· [INFRA-08](INFRA-08.md)（401/会话同源）
- 工作量：3d
- owner / reviewer：待派单（owner ≠ reviewer）

## 目标

交付**退出登录闭环 UI**：AppLayout Header 的用户 Avatar Dropdown（当前用户 / 修改密码 / 退出登录）+ `useLogout` + 401 自动跳登录，补齐登录闭环最后一环——登入后能安全登出、会话失效能正确回登录页。

## 功能要求（原子可测条目）

- [x] **FR-1 Header Avatar Dropdown**：AppLayout Header 右上用户 Avatar + 下拉（当前用户信息 / 修改密码 / 退出登录）。
- [x] **FR-2 useLogout**：登出清 token（httpOnly cookie 由后端清）+ 清前端态 + 跳登录页。
- [x] **FR-3 401 自动跳登录**：HTTP 401 拦截器统一跳登录（与 [INFRA-08](INFRA-08.md) 会话过期同源处理）。
- [x] **FR-4 修改密码入口**：下拉"修改密码"打开改密（复用自助改密，既有 #147）。
- [x] **FR-5 当前用户展示**：展示当前用户名 / 角色 / 所属组织（来自 [BASE-01](BASE-01.md) 上下文）。
- [x] **FR-6 退出确认 + 审计**：退出二次确认 + 审计登出（[BASE-04](BASE-04.md)）。

## 接口契约 / 页面契约
### 接口契约
- 端点：登出端点（`POST /api/v1/auth/logout`，清 httpOnly cookie + 审计）；当前用户信息端点（`GET /api/v1/security/me`）。
- DTO：当前用户 Record（`userId` / `username` / 角色 / 数据范围组织）。
- 响应信封：`ApiResult`；401 走统一拦截。
- 状态机：N·A。
- 幂等 / 错误码 / traceId：登出幂等；带 traceId。

### 页面契约（页面卡）
- 路由元数据：Header 组件挂全局 AppLayout（非独立路由）。
- 结构：Avatar Dropdown + 六态（加载/错误）；改密走既有页。
- 主按钮 ≤1：下拉项清晰，退出为危险操作样式。
- 样式：仅引用 token + 体验契约组件。

## 数据与迁移
N·A —— 复用既有用户/会话；登出审计入 [BASE-04](BASE-04.md)。

## 视角清单（11 视角逐条）
1. **产品架构**：登录闭环收尾；登入↔登出对称。
2. **产品体验**：★Header 用户菜单是全局一致入口；退出/改密路径清晰（体验契约）。
3. **系统与数据架构**：401 拦截统一处理；`/me` 当前用户。
4. **临床医疗安全**：N·A —— 但医生工作站离开需可快速安全登出防顶替。
5. **知识与数据治理**：N·A。
6. **安全合规与监管**：★登出清 httpOnly cookie + 审计；防会话残留（核心 §8）。
7. **集团化与多租户治理**：当前用户展示所属组织/租户（[BASE-01](BASE-01.md)）。
8. **集成与互操作**：N·A。
9. **运维 / SRE / 国产化**：N·A。
10. **质量与真实性审计**：真实登出（真清会话，非前端假清）；登出留审计。
11. **AI / 模型治理与可降级**：N·A。

## 适用不变量
- 命中核心约束：**§8 会话安全/审计** · **#16 体验一致** · **#9 中文** · **§2 AppLayout 全局**。
- 本卡落点：Header 用户菜单 + useLogout + 401 拦截，补齐登录闭环，使登入后可安全登出、会话失效正确回登录。

## 验收 + 验证
- [x] **AC-1（FR-1/5）**：登入后 Header 显示用户 Avatar + 当前用户/角色/组织。
- [x] **AC-2（FR-2/6）**：点退出 → 二次确认 → 清会话 + 审计 + 跳登录页；重登需重新认证。
- [x] **AC-3（FR-3）**：任一请求返回 401 → 自动跳登录页（不停留破碎页）。
- [x] **AC-4（FR-4）**：下拉"修改密码"打开改密流程并可完成。
- [x] **AC-5**：登出后后端 httpOnly cookie 已清（断言无残留会话）。
- 关联 A1–A9：A6 合规运维（会话/审计）。
- T-GATE：前端门禁全绿（真实登出，无假清）。
- B0 验收：纯确定性，天然 B0。

## 完工证据
- 代码 permalink：Header Avatar Dropdown / useLogout / 401 拦截器 / `/me` 端点 / 登出审计。
- 测试：登出闭环 E2E + 401 跳转测试 + 当前用户展示测试 + 会话清除断言。
- 审计员签字：@<reviewer>（owner ≠ reviewer）。

### 2026-06-02 实施证据（INFRA-04）

- 前端：`frontend/src/widgets/AppLayout.tsx` 新增 Header 用户 Avatar Dropdown、当前用户/角色/组织展示、修改密码 Modal、退出确认 Modal、`useLogout` 清 React Query 会话态并回 `/login`、`medkernel:auth-required` 统一 401 回登录。
- 后端：`GET /api/v1/security/me` 的 `EffectivePermissionProfile` 补 `username`，优先从 `PlatformCredential.username` 回填，缺凭证时安全降级为 `userId`；`POST /api/v1/auth/logout` 复用既有 httpOnly cookie 清理与 `AuthService.logout` 审计。
- 测试：`AppLayout.test.tsx` 覆盖用户菜单、改密提交、退出确认清缓存并跳登录、401 自动跳登录；`SecurityMeControllerTest` 覆盖 `username` 返回；`router.test.tsx` 补齐生产 App Provider 与新增 hook mock。
- 本地验证：前端 `npm run verify` 39 files / 169 tests 通过；前端 `npm run build` 通过（`vendor-antd` 大 chunk 提示登记为 `DEFER-003`）；后端 `mvn -B -q test` 通过并实跑 PostgreSQL 15 + Oracle 21 Testcontainers 迁移至 V42；T-GATE 真实性 / 迁移规约 / 配置边界 / 中文注释 / 空白检查通过。
- 浏览器验收：本机 in-app browser 无可用 `iab` 实例，已登记 `DEFER-004`；改用项目 Playwright 对 `http://127.0.0.1:5174/login` 与 mock API 下 `/dashboard` 验收，登录页主题 / 首次部署 / SSO 入口、Header 用户菜单、改密弹窗、退出确认回登录均通过，控制台错误 0。

## 大卡工序（3d，前端 + 少量后端）
- PR1：`/me` + 登出端点 + 401 拦截 + Header Avatar Dropdown → AC-1/2/3/5。
- PR2：修改密码入口接入 + 退出确认 + 审计 → AC-4。
