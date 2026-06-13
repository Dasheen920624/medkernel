# 幕5 路径治理 · 修复部署后旅程复验（postdeploy-a73650d7）

PR [#588](https://github.com/Dasheen920624/medkernel/pull/588) 已 squash 合并入 `main=a73650d7`，并部署到 134（`commit=a73650d7`、`jarSha256=3a3b33d7`）。本目录为部署后对 134 续跑幕5 治理侧完整旅程（试运行→灰度→探针→院级全量）的复验证据。两个缺陷 P5-ACT5-01 / P5-ACT5-02 已在真实前台 + 服务端实证关闭。

> 缺陷**发现态**证据保留在上级目录 `defect-p5-act5-01-discovery/`、`defect-p5-act5-02-discovery/`（证明缺陷曾存在，未被修复后截图覆盖）；本目录为**修复后**确认。

## 部署事实

- manifest `commit=a73650d729a9bbc1ace490360dd155f9cdd11af6`，jar SHA `3a3b33d73c8b8b6a151a4d057ac37472d7c2ee87e587b5436fd080e652aaa37a` = 本地构建（`jar_matches_local_build=YES`）。
- 服务 `active|active|active`，readiness http|https `200|200`，Flyway 三路 `118|118|118`，public 基表 178。
- 前端 xattr 噪声 0，路径包 `PathwayTemplates-B7tJUR-y.js` 上线。
- 文献资料库根地址长度 0，**P6 阻断保持**。
- 发布前备份 `/zoesoft/medkernel/backups/p5-act5-a73650d7-predeploy-20260613-170752`：dump 1429787B / toc 3174，隔离恢复 flyway `118|118`、表 178、知识包 2、路径模板 1（`PATH.ED.DISPOSITION:DRAFT`）、患者路径 0；`destructive_action_performed=false`、`db_preserved=true`。**演练数据随部署完整保留，未清库。**

## 旅程四阶段（全 `failures=[]`）

| 阶段 | 角色 | 结果 | 证据 |
|---|---|---|---|
| simulate | knowledge-governor | ACTIVE 快照试运行轨迹命中 ASSESS→DISPOSITION；**DRAFT 详情抽屉不再 404，tab 正常渲染（P5-ACT5-02）** | `06-ui-pathway-simulate-trajectory.png` |
| canary | knowledge-governor | 灰度发布门禁通过 DRAFT→PUBLISHED（10% bed percent，携影响摘要+审核说明） | `07-…release-impact.png`、`08-…canary-published.png` |
| probe | organization-admin | **org-admin 现持 pathway-templates 菜单，进入 /pathway/templates，新建按钮可见（P5-ACT5-01）** | `probe-org-admin-pathway-accessible.png`、`probe-org-admin-postfix.json` |
| full | organization-admin | **院级全量激活 GRAY→PUBLISHED（scope=ALL），deploymentStatus=PUBLISHED** | `09-…fullrollout-impact.png`、`10-…fullrollout-done.png` |

## 服务端发布链实锤（`mk_version_release_plan` · PATHWAY/PATH.ED.DISPOSITION · p5-hospital）

impact_digest `sha256:04a4f266ecd5d479aa807cd59fd18ec430f941185c4aaf4ff2795b1512b70526`

1. `IN_REVIEW` ALL — knowledge-governor
2. `APPROVED` ALL — knowledge-governor
3. `GRAY` FACILITY（CANARY_BED_PERCENT 10%）— knowledge-governor
4. `PUBLISHED` **ALL — organization-admin** ← P5-ACT5-01 价值实证：机构管理员作为合法院级全量协调角色，独立完成全量激活

最终 `pathway_template.status = PATH.ED.DISPOSITION:PUBLISHED`。

## 缺陷关闭结论

- **P5-ACT5-01**（org-admin 缺 `MENU_PATHWAY_TEMPLATES`）：实证关闭。org-admin 经修复后菜单进入路径治理页（probe），并独立完成院级全量激活（full，发布计划 `PUBLISHED` scope=ALL created_by=organization-admin）。
- **P5-ACT5-02**（院级 DRAFT 详情 404 阻断）：实证关闭。DRAFT 模板详情抽屉不再 404，simulate 与 full 阶段 `templateDetail` 均正常渲染。
- CI 漏网测试 `PermissionDimensionModelTest`（org-admin 菜单全集断言遗漏 `MENU_PATHWAY_TEMPLATES`）已在合并前补齐，全量 `mvn test` 2228 全绿。
