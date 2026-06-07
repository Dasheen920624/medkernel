# D0 登录域级验收记录

> 版本：2026-06-02 · 当前分支 `codex/d0-domain-acceptance`。本记录只登记本轮真实验证证据；外部环境类问题继续归 [待处理问题清单](deferred-issues.md)，不得写成已通过。

## 验收范围

- 13 个客户角色逐个通过平台账号真实登录：`platform-admin`、`group-admin`、`hospital-admin`、`it-ops`、`medical-affairs`、`qa-manager`、`insurance-manager`、`dept-head`、`specialist`、`doctor`、`nurse`、`audit-compliance`、`implementation-engineer`。
- 登录后从真实 `/api/v1/security/me` 读取角色、五维权限画像和 `menuKeys`。
- 后端目录锁定 27 个客户二级菜单 + 5 个高级工具，禁止旧一级 sectionKey 冒充二级菜单权限。
- 首登改密、MFA TOTP setup→verify、登入后 Header 用户菜单、退出登录均走真实端点。
- 旧低质 E2E 与 fixture 删除；真实性门禁阻断 E2E 用 mock、固定医学剧本或演示路径冒充验收。

## 本轮修复

- `CredentialBootstrapGuardInterceptor` 改用应用内路径匹配白名单，修复真实 `/medkernel` context-path 下首登改密被 `ENG-AUTH-015` 误挡的问题；普通业务 API 仍会被首登改密守卫阻断。
- `Bootstrap` MFA UI 从“生成恢复码即完成”调整为“生成 TOTP 密钥 → 输入动态验证码验证并绑定”，对齐 AUTH-03 后端两步绑定。
- `AppLayout` 将当前用户菜单提升到权限芯片之前，保证改密与退出入口不会被页头工具挤出可视区。
- 删除旧 `frontend/e2e/scenarios/*` 固定剧本和 `frontend/e2e/fixtures/medkernel-fixtures.ts`，新增 `frontend/e2e/d0-login-domain.spec.ts`。

## 已跑证据

- 红灯复现：`mvn -B -q -Dtest=CredentialBootstrapGuardInterceptorTest test` 先失败，证明 `/medkernel/api/v1/auth/change-password` 被首登守卫误挡。
- 后端聚焦：`mvn -B -q -Dtest=CredentialBootstrapGuardInterceptorTest,AuthControllerTest,ComplianceUserCredentialFlowTest,D0DomainAcceptanceTest test` 退出 0。
- 后端全量：`mvn -B -q test` 退出 0。
- 前端聚焦：`npm test -- --run src/widgets/AppLayout.test.tsx src/pages/Bootstrap.test.tsx src/shared/api/hooks.test.ts`，35/35。
- 前端配置聚焦：`npm test -- --run src/pages/Bootstrap.test.tsx src/shared/api/hooks.test.ts src/shared/config/menu.test.ts src/shared/config/routes.test.ts`，34/34。
- 前端全量：`npm run verify` 退出 0，39 个测试文件 / 181 个测试通过；`DEFER-003` 噪声仍登记，未宣称清零。
- 前端构建：`npm run build` 退出 0；`vendor-antd` chunk 大小提示仍登记为 `DEFER-003`。
- 真实性规则：`node --test scripts/authenticity-guard.test.mjs`，22/22。
- D0 浏览器 E2E：后端 dev 服务健康端点 `{"status":"UP"}`；`npm run e2e -- d0-login-domain.spec.ts`，14/14。
- T-GATE：`node --test scripts/migration-convention-guard.test.mjs` 8/8；`node --test scripts/config-boundary-guard.test.mjs` 2/2；`scripts/check-comment-zh.sh --mode=full` 退出 0；`git diff --check` 退出 0。
- 工作区显式扫描：真实性门禁扫描 9 个相关文件退出 0；迁移规约扫描 0 个本轮迁移文件退出 0；配置边界扫描 1 个后端生产文件退出 0。

## 待远端验收

- PR 远端 CI 通过并合并后，`D0-验收` 才能作为已落地基线进入 `origin/main`，随后领取 D1 `INFRA-09`。

## 不作为阻塞的 open 项

- `DEFER-001`：达梦 / 人大金仓 + 国产 OS/JDK 真实运行环境适配，当前数据库运行范围只保障 PostgreSQL + Oracle。
- `DEFER-002`：前端依赖审计告警。
- `DEFER-003`：React Router / Antd / 构建输出噪声。
- `DEFER-004`：本机 in-app browser 截图链路不稳定，本轮使用项目 Playwright 证据。
- `DEFER-005`：真实院方 IdP/JWKS/国密证书链缺失。
- `DEFER-006`：历史迁移中文 COMMENT 覆盖缺口，不直接改旧迁移 checksum。
