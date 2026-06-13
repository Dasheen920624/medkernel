# 缺陷 P5-ACT4-02：机构管理员缺规则页菜单，红线规则唯一职责分离合规发布人无法推进发布

## 级别
阻断（红线规则的影子/灰度/院级全量发布在真实前台无人可执行）。

## 现象
P5-ACT4-01 修复部署后续跑治理链，双人独立会签达成（committee 2/2），但推进「进入影子运行」被后端拒绝：
「规则治理推进被拒绝：规则作者、会签人和发布人必须相互分离」。改用机构管理员（唯一职责分离合规发布人）时，
机构管理员以真实前台进入 `/rule/definitions` 同样被路由守卫挡下，显示「当前权限不足」。
运行时证据见 `00-discovery.json` 与 `01-organization-admin-rule-page.png`。

## 根因
- 后端 `RuleGovernanceService.validateTransition` 对 SHADOW/CANARY/FULL 推进强制「规则作者、会签人和发布人必须相互分离」。
- 客户租户里红线规则两名独立委员会会签只能由临床治理员 + 质量治理员承担（作者=机构知识治理员被排除，
  也是唯二的客户委员会角色），二者都成了会签人；作者是知识治理员。
  于是唯一既非作者亦非会签人、且持 `rule.publish`（`canCoordinateRelease`/`canActivateFull` 依赖）的发布人**只能是机构管理员**。
- 但 `DefaultPermissionPolicy.organizationAdministrationPermissions()` 用 `withOnlyMenus(...)` 限定菜单集，
  其中**不含 `MENU_RULE_DEFINITIONS`**。机构管理员持 `rule.publish/rule.write/rule.read`（来自 `allNonEmergencyPermissions()`）
  却没有规则页菜单，前端 `/rule/definitions` 路由守卫 `.every(["menu.rule-definitions","rule.read"])` 把它挡死。

即「后端授权且产品要求必须由它发布、前端却没有进入发布页的菜单/路由」，与 P5-ACT4-01 同类（菜单-路由错配）。

## 运行时事实（缺陷态 134，含 P5-ACT4-01 修复后）
`organization-admin /security/me`：`rule.read=true`、`rule.publish=true`、`menuKeys 含 rule-definitions = false`；
进入 `/rule/definitions` 显示「当前权限不足」、「查看配置与试运行」不可见。

## 修复
`DefaultPermissionPolicy.organizationAdministrationPermissions()` 增补 `MENU_RULE_DEFINITIONS`。

## 回归
`DefaultPermissionPolicyTest`：
- 新增 `redLineRuleReleaseLifecycleCustomerRolesCanReachRuleGovernancePage` —— 断言红线规则全生命周期四个法定客户角色
  （作者知识治理员、委员临床/质量治理员、发布人机构管理员）都含 `MENU_RULE_DEFINITIONS`，且机构管理员持 `RULE_PUBLISH`（修复前对机构管理员红灯）。
- 同步 `customerRoleMenusMatchExactProductSnapshots` 中机构管理员菜单快照（按目录顺序在 `config-packages` 后插入 `rule-definitions`）。
