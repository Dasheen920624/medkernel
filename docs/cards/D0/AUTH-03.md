# AUTH-03 · 凭证自助与口令安全

> 读卡前置：[核心 CONSTITUTION](../../CONSTITUTION.md) + [D0 域简报](_brief.md)。
> 迁移来源（覆盖矩阵锚点）：用户与双模鉴权设计 [2026-05-29-user-auth-dual-mode-design](../../superpowers/specs/2026-05-29-user-auth-dual-mode-design.md) §7 安全 / §8 Phase 2 · 宪法 §1 #1 国密 / §8 / #20 MFA。
> 现状：自助改密 #147 已建；本卡为其在卡体系的家 + 收编分期项（MFA 强制/国密口令/失败锁定限流/强密码）。

## 身份
- 卡 ID：AUTH-03（backlog v8.2 D0 新增）
- 域：D0 登录域 / 平台脊柱
- 关联场景：S14 用户、权限与合规
- 依赖卡：[AUTH-01](AUTH-01.md)（凭证 + MFA 机制）· [BASE-04](BASE-04.md)（审计）· [CONFIG-01](CONFIG-01.md)（口令策略配置外置）
- 工作量：4d
- owner / reviewer：待派单（owner ≠ reviewer）

## 目标

交付**凭证自助与口令安全**：首登强制改密、自助改密、MFA 启用、失败锁定+限流、国密口令、忘记密码受控重置——把登录域的口令安全从"BCrypt 基线"补强到等保/国密合规，并为 [BASE-11](BASE-11.md)/[SUPERADMIN-01](SUPERADMIN-01.md) 的"强制 MFA"提供机制。

## 功能要求（原子可测条目）

- [x] **FR-1 首登强制改密**：`must_change_pwd=Y` → 强制改密才进系统（呼应 [BASE-11](BASE-11.md) 首发引导）。
- [x] **FR-2 自助改密**：登入用户自助改本人密码（既有 #147）；强密码策略（长度/复杂度，经 [CONFIG-01](CONFIG-01.md) 配置外置）。
- [ ] **FR-3 MFA 启用**：基于 [AUTH-01](AUTH-01.md) `mfa_secret` 机制，TOTP 绑定/校验；超管/种子身份**强制**（[SUPERADMIN-01](SUPERADMIN-01.md)/[BASE-11](BASE-11.md) 消费本机制，核心 #20）。
- [x] **FR-4 失败锁定 + 限流**：连续失败锁定凭证（status=LOCKED）+ 登录限流（防爆破），阈值配置外置（[CONFIG-01](CONFIG-01.md)）。
- [ ] **FR-5 国密口令**：口令哈希支持国密 SM3（国产化合规，核心 #1）；BCrypt 为基线、国密为国产化形态可切。
- [ ] **FR-6 忘记密码/重置**：受控重置（管理员重置或邮箱/短信验证，全程审计 [BASE-04](BASE-04.md)），重置后强制改密。

## 接口契约 / 页面契约
### 接口契约
- 端点：`POST /auth/change-password`（自助改密）· `POST /auth/mfa/bind` `POST /auth/mfa/verify`（MFA）· `POST /auth/password-reset`（受控重置）。
- DTO：改密/MFA/重置 Record + Bean Validation（强密码规则）。
- 响应信封：`ApiResult` / `ProblemDetail`（`ENG-AUTH-LOCKED`/`PWD_POLICY_VIOLATION`）。
- 状态机：N·A —— 凭证 status（ACTIVE/DISABLED/LOCKED）非四资产类。
- 幂等 / 错误码 / traceId：改密幂等；锁定/限流错误码；全程审计 traceId。

### 页面契约（页面卡）
- 结构：改密 / MFA 绑定 弹窗或页（入口在 [INFRA-04](INFRA-04.md) Header 下拉"修改密码"+ [BASE-11](BASE-11.md) 首发引导）；六态 + 强密码校验回显。
- 主按钮 ≤1：每步单主按钮。

## 数据与迁移
- 表族：复用 [AUTH-01](AUTH-01.md) `platform_credential`（must_change_pwd/mfa_secret/status）；`sys_login_attempt`（失败计数/锁定/限流）。
- 主键：ULID；索引：`(tenant_id, username)`、`locked_until`。
- 安全：MFA secret 加密；国密 SM3 口令哈希（国产化）；重置 token 一次性 hash 存。
- 5 方言迁移：h2/postgres/oracle/dm/kingbase + 中文注释。

## 视角清单（11 视角逐条）
1. **产品架构**：口令安全是登录域的纵深；自助改密/MFA/重置统一在此，不散落。
2. **产品体验**：改密/MFA 流程清晰单主按钮 + 强密码字段级回显（[INFRA-03](INFRA-03.md)）。
3. **系统与数据架构**：失败计数 + 锁定 + 限流；MFA TOTP 校验。
4. **临床医疗安全**：N·A —— 但高危角色（超管）强制 MFA 防顶替（核心 #20）。
5. **知识与数据治理**：N·A。
6. **安全合规与监管**：★本卡主战场 —— 强密码 + MFA + 失败锁定限流 + 国密 SM3 + 重置审计（等保/国密/个保法，核心 #1/§8）。
7. **集团化与多租户治理**：口令策略可租户级覆盖（[CONFIG-01](CONFIG-01.md)/核心 §9）。
8. **集成与互操作**：N·A —— 内网 IdP 委托时口令由 IdP 管（[AUTH-01](AUTH-01.md) DELEGATED）。
9. **运维 / SRE / 国产化**：★国密 SM3 口令哈希（国产化合规，核心 §12）；锁定阈值配置外置。
10. **质量与真实性审计**：真实锁定/限流（非伪造）；改密/MFA/重置全审计（核心 #18/§8）。
11. **AI / 模型治理与可降级**：N·A —— 天然 B0。

## 适用不变量
- 命中核心约束：**#1 国密口令** · **#20 MFA 机制（供超管强制）** · **#19 口令策略配置外置** · **§8 锁定/限流/重置审计**。
- 本卡落点：首登改密 + 自助改密 + MFA + 失败锁定限流 + 国密口令 + 受控重置，把登录域口令安全补强到合规，并为超管/种子的强制 MFA 提供机制。

## 验收 + 验证
- [x] **AC-1（FR-1/2）**：`must_change_pwd=Y` 用户登入强制改密才放行；自助改密生效，弱密码被强密码策略拒。
- [ ] **AC-2（FR-3）**：MFA 绑定后校验生效；超管/种子未绑 MFA 不得执行高危动作（[SUPERADMIN-01](SUPERADMIN-01.md)/[BASE-11](BASE-11.md)）。
- [x] **AC-3（FR-4）**：连续失败达阈值 → 凭证 LOCKED + 限流；阈值从 [CONFIG-01](CONFIG-01.md) 改即生效。
- [ ] **AC-4（FR-5）**：国产化形态下国密 SM3 口令哈希生效（可切 BCrypt/SM3）。
- [ ] **AC-5（FR-6）**：受控重置走审计 + 重置后强制改密；重置 token 一次性。
- 关联 A1–A9：A6 合规运维（口令安全 + 审计）。
- T-GATE：后端门禁全绿（真实锁定/限流，无伪造）。
- B0 验收：纯确定性，天然 B0。

## 完工证据
- 代码 permalink：自助改密/MFA/重置端点 · 失败锁定限流 · 国密 SM3 口令 · `sys_login_attempt` 迁移 · 审计。
- 测试：首登改密拦截 · 强密码策略 · MFA 绑定校验 · 失败锁定限流 · 国密口令切换 · 重置受控审计。
- PR1 证据：`AuthControllerTest` 覆盖首登强制改密、登录失败锁定与未知账号限流；`CredentialAdminControllerTest` 覆盖弱密码拒绝、手动锁定不被过期自动锁误解锁、状态空值后端校验；`SystemConfigControllerTest` 覆盖密码最小长度不可降到 12 以下；`FlywayMultiDialectSmokeTest` / 后端全量测试覆盖 H2 / PostgreSQL / Oracle 迁移到 V45。
- 审计员签字：@<reviewer>（owner ≠ reviewer）。

## 大卡工序（4d，后端 + 少量前端弹窗）
- PR1：首登/自助改密 + 强密码策略 + 失败锁定限流 → AC-1/3（已完成）。
- PR2：MFA 绑定/校验机制（供超管强制）+ 国密 SM3 口令 + 受控重置 → AC-2/4/5。
