# INFRA-08 · 会话超时与多 tab 同步

> 读卡前置：[核心 CONSTITUTION](../../CONSTITUTION.md) + [D0 域简报](_brief.md) + [体验契约](../../EXPERIENCE_CONTRACT.md)（页面卡）。
> 迁移来源（覆盖矩阵锚点）：体验规范 §3 角色体验标准 · 核心 §8 安全合规（会话）· 详规 §7.9 安全与合规实现。

## 身份
- 卡 ID：INFRA-08
- 域：D0 登录域 / 平台脊柱
- 关联场景：S14 用户、权限与合规（会话安全）
- 依赖卡：[INFRA-04](INFRA-04.md)（401/登出同源）· [CONFIG-01](CONFIG-01.md)（超时阈值配置外置）
- 工作量：3d
- owner / reviewer：待派单（owner ≠ reviewer）

## 目标

交付**会话安全闭环**：token 过期自动跳登录 + 多 tab storage event 同步（一处登出全部登出）+ 长时间无操作自动登出——防会话残留与多 tab 状态不一致，满足等保会话安全要求。

## 功能要求（原子可测条目）

- [x] **FR-1 token 过期自动跳登录**：token/会话过期 → 自动跳登录（与 [INFRA-04](INFRA-04.md) 401 拦截同源）。
- [x] **FR-2 多 tab 同步**：storage event 监听，一个 tab 登出 → 所有 tab 同步登出。
- [x] **FR-3 无操作自动登出**：可配置无操作超时（核心 #19 配置外置，经 [CONFIG-01](CONFIG-01.md)）到期自动登出。
- [x] **FR-4 会话续期**：活动时滑动续期（在最大会话时长内）。
- [x] **FR-5 退出全 tab 同步**：主动退出在所有 tab 生效。
- [x] **FR-6 超时前提醒**：无操作即将超时前提示（可续期/即将登出）。

## 接口契约 / 页面契约
### 接口契约
- 端点：`GET /api/v1/auth/session` 查询当前会话状态；`POST /api/v1/auth/session/renew` 在最大会话时长内续期。
- DTO：`SessionStatusResponse` Record，返回 `remainingSeconds`、`idleTimeoutSeconds`、`warningSeconds`、`maxSessionSeconds`、`maxSessionRemainingSeconds`、`serverTime`。
- 响应信封：`ApiResult`；过期走 401。
- 状态机：N·A。
- 幂等 / 错误码 / traceId：续期幂等；会话过期统一返回 `ENG-AUTH-012`（会话已过期，请重新登录）；带 traceId。

### 页面契约（页面卡）
- 结构：AppLayout 全局无操作计时 + 超时提醒 Modal + 续期/登出动作；会话状态失败沿用 INFRA-04 的 401 自动回登录。
- 主按钮 ≤1：提醒框单主操作（续期）。
- 样式：仅引用 token + 体验契约组件。

## 数据与迁移
N·A —— 不新增迁移。会话状态以 httpOnly cookie + JWT `session_started_at` 为准；无操作超时、提醒提前量、最大会话时长由 [CONFIG-01](CONFIG-01.md) 配置中心热生效，启动种子只作为缺省值。

## 视角清单（11 视角逐条）
1. **产品架构**：会话安全闭环；与 INFRA-04 登出对称。
2. **产品体验**：超时前提醒 + 多 tab 一致，无"一个 tab 登出另一个还在"的割裂（体验契约）。
3. **系统与数据架构**：storage event 多 tab 同步；服务端会话过期为准。
4. **临床医疗安全**：医生离开工作站无操作自动登出，防顶替误操作（核心 §6）。
5. **知识与数据治理**：N·A。
6. **安全合规与监管**：★本卡主战场 —— 会话超时 + 无操作登出 + 防残留（等保会话安全，核心 §8）。
7. **集团化与多租户治理**：N·A。
8. **集成与互操作**：N·A。
9. **运维 / SRE / 国产化**：超时阈值配置外置（[CONFIG-01](CONFIG-01.md)，核心 #19）。
10. **质量与真实性审计**：真实会话过期（服务端为准，非前端假登出）。
11. **AI / 模型治理与可降级**：N·A。

## 适用不变量
- 命中核心约束：**§8 会话安全** · **#19 超时阈值配置外置** · **#16 体验一致** · **#6 单主操作提醒**。
- 本卡落点：token 过期跳登录 + 多 tab storage 同步 + 无操作登出 + 续期提醒，把会话安全做成防残留、防顶替、多 tab 一致的闭环。

## 验收 + 验证
- [x] **AC-1（FR-1）**：会话过期后任一操作自动跳登录。
- [x] **AC-2（FR-2/5）**：两个 tab 登入，一个登出 → 另一个同步登出。
- [x] **AC-3（FR-3）**：无操作达配置超时 → 自动登出；阈值从 [CONFIG-01](CONFIG-01.md) 改即生效。
- [x] **AC-4（FR-4/6）**：活动滑动续期；即将超时弹提醒可续期。
- [x] **AC-5**：服务端会话为准（前端篡改 token 不延长真实会话）。
- 关联 A1–A9：A6 合规运维（会话安全）。
- T-GATE：前后端门禁全绿（真实会话，非假登出）。
- B0 验收：纯确定性，天然 B0。

## 完工证据
- 代码 permalink：`AuthSessionService` / `AuthController` / `SessionStatusResponse` / `AuthSessionProperties` / `AuthSessionClaims` / `SecurityConfig` / `SystemConfigService` / `SystemConfigSeeder` / `JwtIssuer` / `LoginResponse` / `AppLayout` / `sessionEvents` / `browserStorage` / `api hooks`。
- 测试：`AuthControllerTest#sessionStatusAndRenewUseConfigCenterPolicyWithoutRestart` 覆盖配置中心阈值热生效、状态查询、续期和 cookie Max-Age；`AuthControllerTest#protectedSessionRejectsJwtWhenRuntimeSessionPolicyIsShortened` 覆盖服务端 JWT 解码阶段按配置中心 idle/max 策略即时拒绝旧会话；`AppLayout.test.tsx` 覆盖 storage event 多 tab 同步、超时前提醒、续期、主动退出和无操作自动登出；`router.test.tsx` 覆盖登录页默认入口与会话 hook 兼容。
- 全量验证：后端 `mvn -B test` 通过 771 tests / 0 failures / 0 errors / 0 skipped；前端 `npm run verify` 通过 39 files / 175 tests；前端 `npm run build` 通过；提交后 changed T-GATE 通过（真实性扫 14 文件、配置边界扫 12 文件、迁移规约无新增迁移、中文注释 0 fail / 0 warn、空白检查通过）。
- 待处理项：`DEFER-002` 依赖审计、`DEFER-003` 前端测试 / 构建噪声、`DEFER-004` in-app browser 连接 / 截图能力仍 open；本卡不得把这些外部或历史噪声写成已清零。
- 审计员签字：@<reviewer>（owner ≠ reviewer）。

## 大卡工序（3d，前端 + 少量后端）
- PR1：token 过期拦截 + 多 tab storage 同步 + 退出全 tab → AC-1/2/5。
- PR2：无操作自动登出（配置外置）+ 滑动续期 + 超时提醒 → AC-3/4。
