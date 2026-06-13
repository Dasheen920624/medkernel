# 缺陷 P5-ACT4-01：质量治理员被路由守卫挡在规则配置页外，红线规则双人独立会签无法经真实前台完成

## 级别
阻断（红线规则治理旅程在真实前台无法走完院级全量）。

## 现象
幕4 规则治理旅程推进到「委员会双人独立会签」环节时，质量治理员（`quality-governor`）以真实前台进入
`/rule/definitions` 被路由守卫拦下，页面显示「当前权限不足，该页面包含受控数据，请联系信息科主任调整角色或数据范围」，
拿不到「查看配置与试运行」入口，无法完成会签。运行时证据见 `00-discovery.json` 与 `01-quality-governor-blocked.png`。

## 根因
- 后端把质量治理员列为规则委员会法定会签角色：`RuleGovernanceService.COMMITTEE_ROLES` 含 `QUALITY_GOVERNOR`，
  且 `validateSignoff` 明确「作者不能审核自己的规则」。在客户租户里作者=机构知识治理员被排除，
  能承担红线规则两名独立委员会会签的客户角色只有 `clinical-governor` 与 `quality-governor`。
- 后端会签端点 `POST /engine/rule/rules/{id}/governance/signoffs` 的 `@PreAuthorize` 为
  `@perm.hasAny('rule.publish','rule.write','evaluation.publish')`，质量治理员持 `rule.write`+`evaluation.publish`，
  **后端完全接纳其会签**。
- 但 `DefaultPermissionPolicy.qualityGovernancePermissions()` 未授予 `MENU_RULE_DEFINITIONS`，
  而前端路由 `/rule/definitions` 的守卫 `requiredPermissions=["menu.rule-definitions","rule.read"]` 用 `.every()` 全满足，
  质量治理员缺该菜单即被挡死。对照组 `clinical-governor` 与作者 `institutionKnowledgePermissions` 都含该菜单——只有质量治理员漏了。

即「后端授权可会签、产品要求必须由它会签，前端却没有进入会签页的菜单/路由」的菜单-路由错配（与 P4 同类）。

## 运行时事实（缺陷态 134）
`quality-governor /security/me`：`rule.read=true`、`rule.write=true`、`evaluation.publish=true`、
`menuKeys 含 rule-definitions = false`；进入 `/rule/definitions` 后「查看配置与试运行」按钮不可见。

## 修复
`DefaultPermissionPolicy.qualityGovernancePermissions()` 增补 `MENU_RULE_DEFINITIONS`，使质量治理员能进入规则配置页完成其法定会签。

## 回归
`DefaultPermissionPolicyTest`：
- 新增 `ruleCommitteeCustomerRolesCanReachRuleGovernancePage` —— 断言临床/质量治理员都含 `MENU_RULE_DEFINITIONS`+`RULE_READ`（修复前对质量治理员红灯）。
- 同步 `customerRoleMenusMatchExactProductSnapshots` 中质量治理员菜单快照（按目录顺序在 `knowledge-governance` 后插入 `rule-definitions`）。
