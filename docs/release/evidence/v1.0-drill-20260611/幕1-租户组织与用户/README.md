# 幕1：租户、组织与用户

执行时间：2026-06-10T17:48:20.856Z

本目录保存 134 真实环境的脱敏结构化证据：租户开通、组织树、账号与角色覆盖、菜单可见性、越权 403 与审计失败事件。

凭据只存放在服务器 `/zoesoft/medkernel/conf/drill-act1-credentials-20260611.json`，仓库仅提交路径与权限说明。

关键结论：

- 演练租户：演练总医院（drill-hospital-20260611）
- 组织树：租户根 + 演练总医院 + 7 个科室。
- 主账号：9 个演练账号，覆盖信息科、医务处、医保办、医生、护士、医技、药师、审计员。
- 角色覆盖：16/16 个内置角色。
- 组织范围：用户详情实证含 `TENANT`、`HOSPITAL`、`DEPARTMENT` 三类范围；所有平台管理凭证账号已完成首登设置。
- 越权验证：医生读取用户管理 403；审计员创建用户 403；审计流存在 authorization FAILED 事件。

## 证据文件

| 文件 | 内容 |
|---|---|
| `00-health.json` | 134 readiness 健康状态 |
| `01-platform-tenants-before-after.json` | 平台客户租户台账开通前后对比 |
| `02-tenant-provisioning.json` | 「演练总医院」开通响应，密码字段为空或已脱敏 |
| `03-tenant-admin-security.json` | 平台管理员与医院管理员的 `/security/me` 权限画像 |
| `04-org-units.json` | 租户根、医院、7 个科室的组织树响应 |
| `05-main-accounts.json` | 9 个演练主账号、菜单 key 与可见菜单 |
| `06-role-visibility-matrix.json` | 16 个内置角色覆盖矩阵与角色抽查账号 |
| `07-forbidden-access.json` | 医生读用户管理 403、审计员写用户管理 403 断言 |
| `08-audit-denials.json` | `authorization` / `FAILED` 审计事件查询结果 |
| `09-implementation-guide-readiness.json` | 客户实施向导四问审计与幕1就绪状态 |
| `10-user-role-scopes.json` | 用户详情中的真实角色范围与首登设置断言 |
| `11-ui-tenant-onboarding.png` | 医院管理员访问租户实施配置页截图 |
| `12-ui-admin-users.png` | 医院管理员访问用户管理页截图 |
| `13-ui-implementation-guide.png` | 医院管理员访问客户实施向导页截图 |
| `14-ui-screenshots.json` | Playwright 截图路由、标题与账号说明 |
| `trace-ids.txt` | API 调用状态码与 traceId 摘要 |
| `credential-location.txt` | 服务器凭据文件位置和权限说明 |

## 线上修复与部署

- 幕1需要「越权拒绝并留审计」；本分支在 `GlobalExceptionHandler` 为 `AccessDeniedException` 增加 `authorization` 失败审计事件，已部署到 134 后由真实 403 验证。
- 134 在接入 #527 后暴露早期种子迁移校验差异：V6 / V25 已按当前 main 的医技技师、临床药师种子修正，修正前备份为 `/zoesoft/medkernel/backups/pre-act1-role-seed-repair-20260611-013753.dump`。
- 发布脚本回滚时同步恢复 `manifest.properties`，避免失败发布回滚 jar 后留下错误 manifest；修复已热更新到 134 并随本分支提交。
