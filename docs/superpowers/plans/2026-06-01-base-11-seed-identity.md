# BASE-11 平台首发种子身份实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**目标：** 让全新生产环境不依赖 dev 账号也能通过一次性 init token 安全接管，完成首发超管凭证、强制改密、MFA 绑定、CLI 应急与首次部署手册闭环。

**架构：** 后端新增 `sys_bootstrap_init_token` 持久化和 `bootstrap` 服务族；生产只接受部署期提供的一次性 token，DB 只存 SHA-256 hash，不落明文。登录成功后按账号状态强制进入改密 / MFA 步骤，未完成不得进入业务页；CLI 仅本机执行并写审计。

**Tech Stack：** Spring Boot 3、Spring Data JDBC、Flyway 五方言、BCrypt、JWT Cookie、React + Ant Design、Vitest、JUnit / MockMvc。

---

## 当前核查

- `PlatformCredentialDevSeeder` 仅 `@Profile("dev")`，生产没有预置账号，这是 BASE-11 要修的致命缺口。
- `AuthService` / `AuthController` / `JwtIssuer` 当前仅 `dev/test` profile，生产 profile 无平台账号登录主链路；BASE-11 需要放开平台账号登录，但生产 JWT 启动密钥必须显式配置，不能使用 dev 默认密钥。
- `platform_credential.mfa_secret` 已预留，但没有 MFA 绑定 / 强制逻辑；本卡补最小真实机制，后续 AUTH-01/03 可扩展 IdP / 国密 / TOTP 策略。
- 当前运行范围只保障 PostgreSQL + Oracle；仍按仓库迁移规约提交 h2/postgres/oracle/dm/kingbase 五方言脚本，达梦 / 人大金仓真实运行证据继续登记 `DEFER-001`。
- 基线已跑：`npm run verify` 145 tests 通过；`mvn -B -q test` 通过 PostgreSQL + Oracle Testcontainers。

## 任务 1：生产 init token 持久化与启动种子

**Files:**
- Create: `medkernel-backend/src/main/java/com/medkernel/engine/security/bootstrap/BootstrapInitToken.java`
- Create: `medkernel-backend/src/main/java/com/medkernel/engine/security/bootstrap/BootstrapInitTokenRepository.java`
- Create: `medkernel-backend/src/main/java/com/medkernel/engine/security/bootstrap/BootstrapInitTokenService.java`
- Create: `medkernel-backend/src/main/java/com/medkernel/engine/security/bootstrap/BootstrapInitTokenSeeder.java`
- Create: `medkernel-backend/src/test/java/com/medkernel/engine/security/bootstrap/BootstrapInitTokenServiceTest.java`
- Create: `medkernel-backend/src/main/resources/db/migration/{h2,postgres,oracle,dm,kingbase}/V36__bootstrap_init_token.sql`
- Modify: `medkernel-backend/src/test/java/com/medkernel/migration/*`

- [ ] 写失败测试：无明文 token 落库、hash 唯一、过期和已用 token 拒绝、有效 token 可一次性消费。
- [ ] 写 V36 五方言迁移：`sys_bootstrap_init_token` 含 token hash、状态、过期、使用人、审计字段和中文 COMMENT。
- [ ] 实现 token hash / 过期 / 单次消费服务；使用 SHA-256，不使用 UUID/时间戳伪 hash。
- [ ] 实现启动种子：仅当部署显式提供 `MEDKERNEL_BOOTSTRAP_INIT_TOKEN` 时写入 hash；不创建账号，不打印明文 token。
- [ ] 更新迁移基线测试到 V36，并单跑迁移规约。

## 任务 2：首发超管引导端点与强制改密

**Files:**
- Create: `medkernel-backend/src/main/java/com/medkernel/engine/security/bootstrap/BootstrapController.java`
- Create: `BootstrapStartRequest/Response`、`BootstrapPasswordRequest/Response` Record DTO
- Create: `BootstrapIdentityService.java`
- Create: `medkernel-backend/src/test/java/com/medkernel/engine/security/bootstrap/BootstrapControllerTest.java`
- Modify: `AuthService.java`、`AuthController.java`、`JwtIssuer.java`、`SecurityConfig.java`、`LoginResponse.java`

- [ ] 写失败测试：生产 profile 无 dev seeder 账号；init token 可创建首发账号；重复/过期 token 返回 `INIT_TOKEN_USED/EXPIRED`。
- [ ] 放开平台账号登录服务到生产，但生产 JWT secret 未显式配置时启动失败，避免 dev 默认密钥进生产。
- [ ] `POST /api/v1/bootstrap/init-token` 校验 token 后返回引导状态，不签发业务 JWT。
- [ ] `POST /api/v1/bootstrap/password` 消费 token、创建首发账号、授 `platform-admin` 基础接管角色，账号 `must_change_pwd=Y`。
- [ ] 登录响应包含 `mustChangePwd`，前端登录后若为 true 强制跳转改密页，不得进入业务页。

## 任务 3：MFA 绑定与高危拦截最小闭环

**Files:**
- Create: `BootstrapMfaRequest/Response` Record DTO
- Create: `MfaPolicyService.java`
- Create: `medkernel-backend/src/test/java/com/medkernel/engine/security/bootstrap/MfaPolicyServiceTest.java`
- Modify: `PlatformCredential.java`、`AuthService.java`、高危配置 / 租户开通入口的 guard

- [ ] 写失败测试：首发账号未绑定 MFA 时，高危动作被拒；绑定后放行。
- [ ] 实现最小 MFA 绑定：服务端生成一次性恢复码摘要 / 或 TOTP secret 保护存储，绑定动作写审计。
- [ ] 登录响应暴露 `mfaRequired/mfaBound`，业务页 guard 能识别 MFA 未完成状态。
- [ ] 给配置中心高危变更和租户开通入口加 MFA guard，避免超管未绑 MFA 做高危动作。

## 任务 4：CLI 应急与首次部署手册

**Files:**
- Create: `medkernel-backend/src/main/java/com/medkernel/engine/security/bootstrap/BootstrapEmergencyCommand.java`
- Create: `medkernel-backend/src/test/java/com/medkernel/engine/security/bootstrap/BootstrapEmergencyCommandTest.java`
- Modify: `docs/handbook/operations.md`
- Create or modify: `deploy/docker/scripts/*bootstrap*`

- [ ] 写失败测试：无本机确认 / 无二次确认时 CLI 拒绝；成功时只输出一次性恢复信息并写审计。
- [ ] 实现 CLI：本机命令触发锁定解除 / MFA 重置，必须带确认短语、原因和操作者。
- [ ] 运维手册补首次部署：生成 token、配置 env、首登、改密、绑 MFA、开通首个租户、销毁 token。
- [ ] 文档明确当前只保障 PostgreSQL + Oracle，国产化真实环境证据仍归 `DEFER-001`。

## 任务 5：前端首次部署引导页

**Files:**
- Create: `frontend/src/pages/Bootstrap.tsx`
- Create: `frontend/src/pages/Bootstrap.test.tsx`
- Modify: `frontend/src/app/router.tsx`
- Modify: `frontend/src/shared/api/hooks.ts`
- Modify: `frontend/src/pages/Login.tsx`

- [ ] 写失败测试：登录页可进入首次部署引导；每步只有一个主按钮；错误按字段回显。
- [ ] 新增 `/bootstrap` 页面：init token → 设置首发账号密码 → 绑定 MFA，复用 BASE-10 token，不写硬编码颜色/px。
- [ ] 登录成功后按 `mustChangePwd/mfaRequired` 跳转强制步骤，不允许进入 `/dashboard`。
- [ ] 浏览器验收 `/login` 与 `/bootstrap`，确保主题切换不破、页面不崩。

## 任务 6：验收、门禁、PR

- [ ] 更新 `docs/cards/D0/BASE-11.md` FR/AC、`docs/backlog.md`、`docs/_HANDOFF.md`。
- [ ] 跑后端目标测试、前端目标测试、`npm run verify`、`npm run build`、`mvn -B -q test`。
- [ ] 跑 T-GATE：`git diff --check`、真实性 changed、配置边界 changed、迁移 changed、中文注释。
- [ ] 推送 PR；远端 CI 通过并合并后，才能领取下一阶段。
