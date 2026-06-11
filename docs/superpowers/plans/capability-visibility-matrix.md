# 能力可见性矩阵

> 范围：幕8.5 前台重新演练的活矩阵。计划原口径为 71 Controller × 44 页面；本分支按当前仓库 `rg '@RestController'` 实测为 75 个 Controller，后续全量批次以实际仓库扫描为准。
> 状态：第一批只覆盖幕0–2。结论分为「可见可操作」「局部可见」「API-only/缺口」「未到本批」。

## 第一批结论（幕0–2）

| 后端能力 / Controller | 客户页面 | 本批结论 | 前台实测动作 | 证据 | 后续动作 |
|---|---|---|---|---|---|
| `BootstrapController` | `/bootstrap` | 可见可操作（接管后只读状态） | 重读已完成首次部署页，不重置接管码 | [幕0 UI 重演](../../release/evidence/v1.0-drill-20260611/幕0-部署接管与首次登录/ui-replay/01-ui-bootstrap-completed.png) | 无 |
| `AuthController` | `/login` | 可见可操作 | 多角色重新登录；不截图密码、TOTP 秘钥或恢复码 | [幕0 登录入口](../../release/evidence/v1.0-drill-20260611/幕0-部署接管与首次登录/ui-replay/00-ui-login-entry.png) | MFA 绑定页继续按敏感凭据规则留证 |
| `SecurityMeController` | 顶栏用户态、`/dashboard`、权限边界页 | 可见可操作 | 信息科进入工作台；医生访问用户管理显示「当前权限不足」 | [信息科工作台](../../release/evidence/v1.0-drill-20260611/幕0-部署接管与首次登录/ui-replay/02-ui-it-ops-dashboard-after-login.png)、[医生越权](../../release/evidence/v1.0-drill-20260611/幕1-租户组织与用户/ui-replay/16-ui-admin-users-forbidden-doctor.png) | 无 |
| `MenuPermissionController` | 左侧菜单、页面级 forbidden | 可见可操作 | 医生侧边栏没有合规运维管理入口；直接访问被页面阻断 | [医生越权](../../release/evidence/v1.0-drill-20260611/幕1-租户组织与用户/ui-replay/16-ui-admin-users-forbidden-doctor.png) | 无 |
| `HealthController` / `RuntimeProbeController` | `/workbench/readiness-validation` | 可见可操作 | 信息科查看就绪、阻塞、未启用状态 | [验收自检](../../release/evidence/v1.0-drill-20260611/幕0-部署接管与首次登录/ui-replay/03-ui-readiness-validation.png) | 幕10 L2 补运行状态页 |
| `TenantProvisioningController` | `/tenant/onboarding`（平台主租户视图） | 已有 L1/L2 证据，本批未重开租户 | 现场租户已存在，不重复开通客户租户 | [幕1 README](../../release/evidence/v1.0-drill-20260611/幕1-租户组织与用户/README.md) | 后续若做重装环境，再补开通页复演 |
| `TenantEngineController` | `/onboarding/guide` | 可见可操作 | 医院管理员重走客户实施向导，6/6 就绪 | [客户实施向导](../../release/evidence/v1.0-drill-20260611/幕1-租户组织与用户/ui-replay/15-ui-onboarding-guide.png) | 无 |
| `OrgUnitController` | `/tenant/onboarding` | 可见可操作 | 前台新增/核对「演练·幕8.5门诊协同科」 | [组织节点复演](../../release/evidence/v1.0-drill-20260611/幕1-租户组织与用户/ui-replay/11-ui-tenant-onboarding-org-created.png) | 无 |
| `ComplianceUserController` | `/admin/users` | 可见可操作 | 前台新建/核对外部身份观察员并查看角色范围 | [用户详情](../../release/evidence/v1.0-drill-20260611/幕1-租户组织与用户/ui-replay/14-ui-admin-users-role-detail.png) | 评估角色选择器分组/搜索 |
| `TerminologyController` | `/terminology/mapping` | 局部可见 | 可看标准字典、院内待映射、候选、冲突；可打开高危确认弹窗 | [字典映射总览](../../release/evidence/v1.0-drill-20260611/幕2-字典与术语对照/ui-replay/20-ui-terminology-overview.png)、[高危确认](../../release/evidence/v1.0-drill-20260611/幕2-字典与术语对照/ui-replay/21-ui-high-risk-confirmation-modal.png) | OPT-TERM-UI-01：补手工新建映射、冲突裁决、替换/回滚状态机 |
| `PackageEngineController` | `/terminology/mapping` 的构建映射包入口 | 局部可见 | 构建术语映射包弹窗可打开，但本批不提交新版本 | [构建映射包入口](../../release/evidence/v1.0-drill-20260611/幕2-字典与术语对照/ui-replay/23-ui-build-package-entry.png) | 幕8.5 后续在配置包中心统一复演打包和发布 |
| `ReleaseGovernanceController` | `/terminology/mapping` 的发布/回滚入口 | 局部可见 | 发布/回滚按钮可见但禁用；禁用原因不够直接 | [发布与回滚入口](../../release/evidence/v1.0-drill-20260611/幕2-字典与术语对照/ui-replay/24-ui-package-publish-rollback-entry.png) | OPT-TERM-UI-01 同步补禁用原因；幕8 批次复演完整发布治理 |
| `LargeListController` | `/terminology/mapping` 导出按钮 | 可见未操作 | 导出入口可见，本批没有执行导出 | [字典映射总览](../../release/evidence/v1.0-drill-20260611/幕2-字典与术语对照/ui-replay/20-ui-terminology-overview.png) | 幕2 后续可补一次脱敏导出审批链 |
| `SavedViewController` | `/terminology/mapping` 保存视图 | 可见未操作 | 保存视图入口可见，本批没有写入新视图 | [字典映射总览](../../release/evidence/v1.0-drill-20260611/幕2-字典与术语对照/ui-replay/20-ui-terminology-overview.png) | 全量矩阵统一抽查 |

## 缺口登记

| 缺口 ID | 影响页面 | 问题 | 安全口径 | 归属 |
|---|---|---|---|---|
| OPT-TERM-UI-01 | `/terminology/mapping` | 无法在前台新建映射、制造冲突、替换/回滚单条映射；发布/回滚禁用原因不直观 | 不用 API 冒充客户面动作；高危钾/钠候选不提交确认 | 体验重构线 |

## 后续批次

| 批次 | 覆盖幕 | 重点 |
|---|---|---|
| 第二批 | 幕3–5 | 知识治理、规则配置、路径配置的前台新建/退役/编辑/发布状态机 |
| 第三批 | 幕6–9 | 推荐/待办/通知闭环、随访与质控、配置包发布治理、适配器健康状态 |
| 幕10 L2 | 幕10 | 审计日志、运行状态、国产化自检、安全基线与系统配置页面 |
