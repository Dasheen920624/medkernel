# P5 幕5 · 路径治理（治理侧完整旅程）

> 所属：P5 第二轮全新演练 · 第一阶段端到端旅程 · 幕5 路径治理（治理侧完整旅程到院级全量；患者入径与节点推进留幕6）。
> 演练脚本：[`scripts/drill/p5-act5-pathway-governance.mjs`](../../../../../scripts/drill/p5-act5-pathway-governance.mjs)
> 环境：`https://193.112.107.134`；路径基线零数据（`pathway_template=0`、`patient_pathway=0`）。

## 剧本与法定角色

| 步骤 | 动作 | 角色 | 权限/门禁 |
|---|---|---|---|
| ① 铺底 | API 模拟外部系统铺底 1 份急诊 ACTIVE 上下文快照（供试运行选用） | 集成运维员 | `context.write` |
| ② 建包 | 真实前台 `/pathway/templates`「管理路径知识包」建专用路径知识包 `PATH.P5.ED` | 机构知识治理员 | `pathway.write`/`knowledge.write` |
| ③ 建模板 | 用内置「急诊处置路径」原型建模板草稿 `PATH.ED.DISPOSITION`（ASSESS→DISPOSITION，含里程碑/默认边） | 机构知识治理员 | `pathway.write`，前置须租户内存在路径知识包（否则 `ENG-PATHWAY-007`） |
| ④ 试运行 | 选 ACTIVE 快照试运行，轨迹命中 ASSESS→DISPOSITION | 机构知识治理员 | `pathway.write` |
| ⑤ 灰度 | 读发布影响摘要 → 灰度发布门禁通过（DRAFT→PUBLISHED，10% 灰度，保留回滚证据） | 机构知识治理员 | `pathway.publish`；门禁须带实时 impactDigest + 审核说明 |
| ⑥ 院级全量 | 读影响摘要 → 院级确认全量激活（PUBLISHED→全量生效） | 机构管理员（或临床治理负责人） | `pathway.publish` + `requireReleaseCoordinator`（客户租户放行 CLINICAL_GOVERNOR / ORGANIZATION_ADMIN） |

成功判定一律以服务端回查为准（`/engine/pathway/pathway-templates[/{id}]`、`/engine/pkg/packages`）。

## 本轮发现并 TDD 闭环的缺陷

### P5-ACT5-01（菜单缺口，预判命中）

机构管理员持 `pathway.read/write/publish` 且是 `requireReleaseCoordinator` 在客户租户放行的**法定院级全量协调角色**，但 `DefaultPermissionPolicy.organizationAdministrationPermissions()` 菜单白名单含 `MENU_RULE_DEFINITIONS`（P5-ACT4-02 修复加入）却**缺 `MENU_PATHWAY_TEMPLATES`**；`/pathway/templates` 路由守卫 `every(["menu.pathway-templates","pathway.read"])` 因菜单缺失把它挡在页外，走不完院级全量。与 P5-ACT4-02 同型菜单-路由错配。

- 发现证据：[`defect-p5-act5-01-discovery/`](defect-p5-act5-01-discovery/)（org-admin `/security/me`：`pathway.publish=true`、`pathway-templates 菜单=false`；前台 `/pathway/templates`「当前权限不足」、左侧无「路径配置」）。
- 修复：`organizationAdministrationPermissions()` 加 `MENU_PATHWAY_TEMPLATES`；红灯回归 `DefaultPermissionPolicyTest#pathwayFullRolloutCoordinatorCustomerRolesCanReachPathwayTemplatesPage` + 14 角色菜单快照；前端 `routes.test.ts` 一致性 39/39。

### P5-ACT5-02（DRAFT 详情 404，阻断）

院级数据范围（DATA_HOSPITAL）治理员查看自己刚建的 DRAFT 路径模板详情返回 **404「未找到可继承的 PUBLISHED 资产版本」**（ENG-API-005），详情抽屉空白、无法进入试运行/发布，**阻断整个路径编排前台流**。根因：`templateDetail→findEffectiveTemplate` 在 `targetOrgUnitId` 非空时调继承解析器，DRAFT 无 PUBLISHED 版本致解析器抛 NOT_FOUND，短路了本应有的「回退本地草稿」分支；单测仅用租户级 scope（targetOrgUnitId=null）从未走到该路径故漏网。`impact` 接口走本地直查故 200，行为不一致坐实缺陷。

- 发现证据：[`defect-p5-act5-02-discovery/`](defect-p5-act5-02-discovery/)（404 服务端事实 + 抽屉空白现场）。
- 修复：`resolveEffectiveTemplateForCurrentOrg` 捕获解析器 NOT_FOUND 返回 empty，激活本地草稿回退；红灯回归 `PathwayEngineServiceTest#templateDetailReturnsLocalDraftWhenOrgUnitHasNoPublishedVersionYet`。

## 旅程证据（发现态，部署版本 f75f7edb）

| 截图 | 说明 |
|---|---|
| `01-ui-pathway-list-before.png` | 路径模板治理页（建包前，零数据） |
| `02-ui-pathway-package-form.png` | 新建路径知识包草稿表单 |
| `03-ui-pathway-package-created.png` | 路径知识包草稿创建成功 |
| `04-ui-pathway-create-form.png` | 急诊处置路径原型创建表单（节点/边/里程碑已播种） |
| `05-ui-pathway-list-after-create.png` | 创建草稿后的路径模板列表 |

机构知识治理员真实前台建路径知识包 + 用内置原型建 DRAFT 模板均成功（服务端回查 `pathway_template.status=DRAFT`）；随后打开详情即触发 P5-ACT5-02 阻断。

## 续接（post-deploy）

两缺陷修复合并并从 merged main 重建部署 134 后，复用已建 DRAFT 模板续跑：知识治理员试运行→灰度发布（DRAFT→PUBLISHED），**机构管理员**院级确认全量激活（实证 P5-ACT5-01 修复价值：先前被菜单挡死的合法协调角色现可走完院级全量），服务端回查全量状态。post-deploy 证据在本目录补充。
