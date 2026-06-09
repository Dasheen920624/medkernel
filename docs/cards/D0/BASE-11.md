# BASE-11 · 平台首发种子身份

> 读卡前置：[核心 CONSTITUTION](../../CONSTITUTION.md) + [D0 域简报](_brief.md)。
> 迁移来源（覆盖矩阵锚点）：核心 #20 内置超级管理员 · 全系统核查总报告（2026-05-29，BASE-11 新增项）· 落地规划部署/运维手册首次部署。

## 身份
- 卡 ID：BASE-11
- 域：D0 登录域 / 平台脊柱
- 关联场景：S1 集团与租户开通（首次部署引导）
- 依赖卡：[SUPERADMIN-01](SUPERADMIN-01.md)（内置超管身份）· [BASE-02](BASE-02.md)（五维授权）· [BASE-04](BASE-04.md)（审计）
- 工作量：3d
- owner / reviewer：待派单（owner ≠ reviewer）

## 目标

解决**首次部署到试点医院无法登录**的致命缺口：核查发现 `PlatformCredentialDevSeeder` 仅 `@Profile("dev")`，生产环境无任何账号。交付生产 init token 引导 + 强制首次改密 + MFA + CLI 应急 + 运维手册首次部署步骤，使全新生产环境可安全引导出第一个超管。

## 功能要求（原子可测条目）

- [x] **FR-1 生产 init token**：首次部署用**一次性 init token**（部署时生成、有效期短、单次使用）引导内置超管首登；**禁**生产写死账号/密码（核心 #18/#20）。
- [x] **FR-2 强制首次改密**：init token 登入后**强制**改密（不改不得进入系统）。
- [x] **FR-3 强制 MFA**：超管强制绑定 MFA（核心 #20）；未绑不得执行高危动作。
- [x] **FR-4 CLI 应急工具**：超管锁定/MFA 丢失时的应急重置 CLI（受控：需服务器本地访问 + 审计 + 二次确认）。
- [x] **FR-5 诚实种子**：无 `@Profile("dev")` 账号泄入生产；生产种子只走 init token 路径（核心 #18）。
- [x] **FR-6 运维手册首次部署步骤**：生成 init token → 首登 → 改密 → 绑 MFA → 种子完成 → 开通首个租户 全流程文档化。

## 接口契约 / 页面契约
### 接口契约
- 端点：`GET /api/v1/bootstrap/status` 只返回是否已完成首次接管；`POST /api/v1/bootstrap/init-token` 校验一次性 token；`POST /api/v1/bootstrap/password` 消费 token 并在唯一平台主租户创建首发 `system-superadmin`；`POST /api/v1/auth/change-password` 完成首次改密；`POST /api/v1/bootstrap/mfa` 绑定 MFA 恢复码；`GET /api/v1/security/me` 返回业务页强制拦截所需安全完成状态。
- DTO：init token / 首发账号密码 / 改密 / MFA 绑定均为 Record DTO + Bean Validation（强密码策略、确认字段、恢复码保护）。
- 响应信封：`ApiResult` / `ProblemDetail`（init token 失效/过期诚实报错）。
- 状态机：种子身份引导状态（未接管→待改密→待 MFA→就绪），完成接管后登录页隐藏入口，直接访问引导页只允许返回登录。
- 幂等 / 错误码 / traceId：init token 单次使用（用后失效）；内置超管角色行作为五方言数据库互斥点，并发首次接管只允许一个成功；`ENG-AUTH-008` 过期、`ENG-AUTH-009` 已用/撤销、`ENG-AUTH-010` MFA 未完成、`ENG-AUTH-017` 已完成首次部署；全程审计 traceId。

### 页面契约（页面卡）
- 结构：登录页保留登录前主题切换，仅在系统未初始化时提供“首次部署接管”入口；首次部署引导页（init token 输入 → 首发管理员与密码 → 首次改密 → MFA 绑定）支持明暗 / 老年医生等主题。
- 六态：字段级错误、接口错误、空 token / 空密码、处理中、成功恢复码、完成跳转均有中文回显。
- 主按钮 ≤1：每步单主按钮（核心 #6）；业务路由若 `mustChangePwd=true` 或 `mfaRequired=true && mfaBound=false`，必须显示“需要完成首次安全设置”并只能继续到 `/bootstrap`。

## 数据与迁移
- 表族：`mk_security_bootstrap_init_token`（一次性 token：SHA-256 hash / 过期 / 已用标记 / 审计字段）；复用 `platform_credential` 首发平台凭证与 `mfa_secret` TOTP 加密绑定记录。
- 主键：数据库自增 ID + `token_id` 业务 ID；唯一约束：`token_hash` 唯一；索引：状态、过期时间、使用人。
- 安全：init token 只存 SHA-256 hash（非明文）；密码 BCrypt；MFA 恢复码只存 SHA-256 摘要；生产 JWT secret 必须显式配置。
- 5 方言迁移：h2/postgres/oracle/dm/kingbase + 中文注释；当前真实运行范围只保障 PostgreSQL + Oracle，达梦 / 人大金仓真实环境证据登记 [DEFER-001](../../audit/deferred-issues.md) 到最终适配阶段关闭。

## 视角清单（11 视角逐条）
1. **产品架构**：首发身份是"系统可被接管"的起点；无种子=死系统。
2. **产品体验**：首次部署引导页清晰 3 步（init→改密→MFA），中文、单主按钮。
3. **系统与数据架构**：init token 单次使用 + 短期失效；种子幂等（重复部署不重复建超管）。
4. **临床医疗安全**：N·A —— 脊柱身份层。
5. **知识与数据治理**：N·A。
6. **安全合规与监管**：★本卡主战场 —— 强制改密 + MFA + init token hash 存储 + 应急受控（等保/个保法，核心 §8/#20）。
7. **集团化与多租户治理**：超管引导后开通首个租户（衔接 S1）。
8. **集成与互操作**：N·A。
9. **运维 / SRE / 国产化**：★首次部署步骤文档化（运维手册）+ CLI 应急工具，内外网双形态均可引导。
10. **质量与真实性审计**：★禁生产写死账号（核心 #18/#20）；init token + 首发全程审计高亮（[BASE-04](BASE-04.md)）。
11. **AI / 模型治理与可降级**：N·A —— 天然 B0。

## 适用不变量
- 命中核心约束：**#20 内置超管（强制 MFA/不旁路）** · **#18 禁生产写死账号** · **§8 安全合规** · **§12 首次部署运维**。
- 本卡落点：init token 一次性引导 + 强制改密 + MFA + CLI 应急 + 手册，让全新生产环境**可安全产生**第一个超管，无写死后门。

## 验收 + 验证
- [x] **AC-1（FR-1/5）**：生产 profile 全新部署，无任何预置账号；用 init token 引导出超管；`@Profile("dev")` 账号确认不入生产。
- [x] **AC-2（FR-2）**：init token 登入后未改密无法进入任何业务页（强制改密生效）。
- [x] **AC-3（FR-3）**：超管未绑 MFA 时执行高危动作被拒；绑定后可执行。
- [x] **AC-4（FR-1）**：init token 用后失效；过期/重用返回诚实错误（`ENG-AUTH-008/009`）。
- [x] **AC-5（FR-4/6）**：CLI 应急重置可用且受控审计；运维手册首次部署步骤可照做走通。
- [x] **AC-6（FR-1/5）**：状态端点只返回初始化布尔值；初始化后入口关闭，重复或并发创建只保留一个平台超管。
- 关联 A1–A9：A6 合规运维（身份 + 审计）。
- T-GATE：后端门禁全绿（无生产写死账号/密码）。
- B0 验收：纯确定性身份引导，天然 B0。

## 完工证据
- 代码：`BootstrapInitTokenService` / `BootstrapInitTokenSeeder` / `BootstrapController` / `BootstrapIdentityService` / `MfaPolicyService` / `BootstrapEmergencyCommand` / `SecurityMeController` / `/bootstrap` 页面 / `mk_security_bootstrap_init_token` V36 五方言迁移 / 运维手册首次部署章节。
- 测试：`BootstrapInitTokenServiceTest`、`BootstrapInitTokenSeederTest`、`BootstrapControllerTest`（含并发单例）、`AuthControllerTest`、`SecurityMeControllerTest`、`MfaPolicyServiceTest`、`SystemConfigControllerTest`、`TenantProvisioningControllerTest`、`BootstrapEmergencyCommandTest`、`Bootstrap.test.tsx`、`Login.test.tsx`、`AppLayout.test.tsx`、`router.test.tsx`、`hooks.test.ts`、`d0-bootstrap-closure.spec.ts`。
- 验收：真实 Playwright 已核验桌面 `/login` 隐藏首次部署入口、移动端 `/bootstrap` 只显示完成状态与返回登录，控制台无 error 且无根级横向溢出。
- 待处理：当前只保障 PostgreSQL + Oracle；达梦 / 人大金仓真实运行证据登记 `DEFER-001`，不阻塞本卡但不得写成已通过。
- 审计员签字：PR review（owner ≠ reviewer，高风险建议双签）。

## 大卡工序（3d，后端 + 少量前端引导页）
- PR1：init token 机制 + 强制改密 + 种子幂等 + 迁移 → AC-1/2/4。
- PR2：MFA 强制 + CLI 应急工具 + 首次部署引导页 + 运维手册 → AC-3/5。
