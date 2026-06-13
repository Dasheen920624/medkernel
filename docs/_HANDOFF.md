# 会话接力

## 2026-06-13 P5-ACT2-04 修复已部署复验，幕9 发布链揭出 P5-ACT2-05 并本地修复待 PR

- 当前执行线：P5 第一阶段端到端旅程 · 幕9 前置 + 幕2 遗留回收。PR #577（P5-ACT2-04 静默吞错修复 + 模拟接收端）已 squash 合并为 `13b930e4a18adab936d4b4ebbef7c2248983d35a` 并部署 134：manifest/jar 精确匹配（jar SHA `3784b770…a070f`），服务 `active|active|active`，readiness `200|200`，Flyway `118|118|118`，178 表，知识 `0|0|0|0|0|0`，文献根地址长度 0，xattr 噪声 0。发布前备份 `/zoesoft/medkernel/backups/p5-13b930e4-predeploy-20260613-081542` 隔离恢复全过（术语 `5|4|4|1`，临时库残留 0），post-deploy 证据同目录 `evidence/post-deploy-13b930e4.properties`。
- 幕9 前置已落地：134 上 `medkernel-mock-third-party.service` 运行中（脚本哈希与仓库一致，`/health` 200）；集成运维员真实前台建适配器 `p5-his-gateway` 并健康诊断 HEALTHY；演练脚本 `scripts/drill/p5-act9-integration-release-chain.mjs`。
- **重要事实更正**：#576 归档的「构建映射包草稿成功」是假阳性——部署日服务端核查 `knowledge_package=0`，草稿从未落库；真实情况是构建弹窗默认最窄范围命中 409「当前范围没有已确认映射」（铺底院内码无科室归属），被 P5-ACT2-04 静默吞错掩盖。修复部署后现场复验：窄范围构建可见 409 报错（含 traceId）→ 改选服务空间范围 → `TERM.P5.MAPPING 2026.06.1` DRAFT 真实落库。
- 新缺陷 `P5-ACT2-05`（阻断，已本地修复待 PR）：服务空间（TENANT）级映射包灰度发布被术语页前端预校验拦死（「知识包缺少有效组织作用域」），而后端本就支持 `scopeType=ALL` 灰度自动收敛目标机构 10%。修复=`parseReleaseScopeType` 加 `TENANT→ALL`、ALL 时 `scopeValue` 置空；红灯新用例先失败、修复后页面套件 23/23、verify/build 全过。发现态证据 `docs/release/evidence/p5-second-fresh-drill-20260612/幕9-系统接入与发布链/defect-p5-act2-05-discovery/`。
- 134 当前业务状态：适配器 `p5-his-gateway` ACTIVE/HEALTHY；`TERM.P5.MAPPING 2026.06.1` 仍为 DRAFT（灰度被 P5-ACT2-05 拦住）；待修复部署后续跑灰度→全量。
- 凭据：134 服务器受控文件不变；本机受控副本 `/tmp/p5-14-role-drill-credentials-20260612.json`（600），不入仓库。
- 权限说明：本会话已获 134 SSH/写入授权与 #577 合并授权；合并 main 仍逐 PR 授权。

## 当前下一步

1. 提交 P5-ACT2-05 修复 + 幕9 脚本 + 文档更正（幕2 README §5/§7/§8、checkpoint 5.4/5.5），创建 PR，CI 全绿后请求合并授权。
2. 合并后重建制品、备份留痕、部署 134、post-deploy 复验。
3. 重跑 `p5-act9-integration-release-chain.mjs` 完成「灰度（知识治理员）→ 全量（机构管理员 /config/packages）」发布链 + P5-ACT2-04 重复构建复验；核查模拟接收端 JSONL 收到 `MEDKERNEL_PACKAGE_RELEASE` 投递并归档证据；提交幕9 证据 PR。
4. 继续幕3（知识治理诚实边界验证）→ 幕4 规则 → 幕5 路径 → 幕6 临床运行 → 幕7 随访质控 → 幕8 配置包 → 幕10 审计导出审批。
5. 正式知识生产继续阻断；文献资料库根地址仍为空，不得进入 P6。

## 2026-06-13 P5 幕2 术语跨角色旅程闭环：三缺陷修复已部署并现场复验通过

- 当前执行线：P5 第一阶段端到端旅程；幕2（术语与字典）已完成「铺底 → 缺陷实锤 → TDD 修复 → 部署 → 修复后旅程复验」全闭环。
- PR #575 已 squash 合并为 `d8bf7f4fb1e949d853d62579856692ba9d3e48d4`，CI 8/8 绿；134 已部署该精确提交，jar SHA `7445480b73dcfcc98f257efc7e151da05d73bde0f87c341a18cd40bf61a3d54d` 匹配，服务 `active|active|active`，readiness `200|200`，Flyway `118`，178 表，知识 `0|0|0|0|0|0`，文献资料库根地址长度 0。
- 发布前有效备份：`/zoesoft/medkernel/backups/p5-d8bf7f4-predeploy-20260613-061959`，隔离恢复 `118|118|118`、178 表、知识 0、术语铺底 `5|4|5|1`、临时库残留 0；失败留痕 `…-061923`（pg_dump 目录权限，无破坏动作）；程序发布自动备份 `deploy-20260613-062101`。
- 幕2 三项阻断缺陷已关闭（真实前台复验）：`P5-ACT2-01` 高危错配候选可行级驳回（钾/钠互斥候选已驳回留痕）；`P5-ACT2-02` 候选/冲突面板不再被空态吞没；`P5-ACT2-03` 普通候选可见并批量确认（4 条映射「已确认」、待审清零）。机构知识治理员前台构建映射包 `TERM.P5.MAPPING` 草稿成功。
- 证据：`docs/release/evidence/p5-second-fresh-drill-20260612/幕2-术语与字典/`（首轮）与 `…/postdeploy-d8bf7f4f/`（修复后复跑）；脚本 `scripts/drill/p5-act2-terminology-cross-role.mjs` 可整链复跑。
- 凭据：134 服务器受控文件不变；本机受控副本 `/tmp/p5-14-role-drill-credentials-20260612.json`（600），不入仓库。
- 遗留待回收：映射包发布链依赖健康发布适配器（当前机构 0 个，幕9 系统接入为前置）；全量发布承载在 `/tenant/packages`（机构管理员）；一对多冲突无前台处置入口（观察项，候选驳回后冲突仍 OPEN）。
- 权限说明：本会话用户已授权 134 SSH/写入与 PR #575 合并；合并 main 仍需逐次明确授权；代理不可自行写 SSH 白名单到 settings。

## 当前下一步

1. 提交幕2 部署与修复后复验证据（postdeploy-d8bf7f4f），创建 PR，CI 全绿后请求合并授权。
2. 幕9 前置先行：集成运维员前台完成系统接入（需在 134 上准备可达的模拟第三方接收端），打通映射包「灰度（知识治理员）→ 全量（机构管理员）」跨角色发布链并回收幕2 遗留。
3. 继续幕3（知识治理诚实边界验证）→ 幕4 规则 → 幕5 路径 → 幕6 临床运行 → 幕7 随访质控 → 幕8 配置包 → 幕10 审计导出审批。
4. 正式知识生产继续阻断；文献资料库根地址仍为空，不得进入 P6。

## 2026-06-12 P5 第二轮全新演练进行中，协同待办移动端缺陷已部署复验

- 当前执行线：P5 134 第二轮全新演练与第一阶段正式验收；当前工作分支 `codex/p5-ab213-deploy`，基于精确 `origin/main=ab2132891a208e72a1573c82e6a79d665918310b`。
- PR #573 已全绿并 squash 合并；134 已部署精确提交 `ab2132891a208e72a1573c82e6a79d665918310b`。发布前有效备份：`/zoesoft/medkernel/backups/p5-ab213-predeploy-20260612-224748`；隔离恢复 `118|118|118`（成功迁移条数、最大 installed_rank、最大数值版本）、178 张 public 基表、知识 `0|0|0|0|0|0`、文献资料库根地址长度 0、P5 账号/角色统计 `2|17|15|15`、目标平台管理员/超管角色 `1|1`、临时恢复库清理 0。
- 134 当前服务 `medkernel|nginx|postgresql=active|active|active`，HTTP/HTTPS readiness 200，Flyway `118|118|118`，178 张表，知识 `0|0|0|0|0|0`，文献资料库根地址长度 0。部署后证据：`/zoesoft/medkernel/backups/p5-ab213-predeploy-20260612-224748/evidence/post-deploy-ab213.properties` 与仓库 `docs/release/evidence/p5-second-fresh-drill-20260612/14-role-journeys/postdeploy-ab213/00-postdeploy-ab213-summary.json`。
- 首次 ab213 程序发布成功但前端包仍含 macOS `LIBARCHIVE.xattr` 扩展头噪声；已按 `COPYFILE_DISABLE=1 tar --no-xattrs` 重打 clean 包并重发前端，最终 clean 包 SHA-256 `4300536db0b63a8bf5e637e266721950e47d707898532e945e2ce98256b54df6`、xattr 噪声计数 0。保留首次噪声计数 514 作为过程证据，不作为最终闭环。
- 14 个职责角色的受控凭据仅在 `/zoesoft/medkernel/conf/p5-14-role-drill-credentials-20260612.json`，权限 `600|medkernel|medkernel`；本地验证使用 `/tmp` 受控副本，无凭据入仓库。
- 2b8b324 部署后真实复现 `clinical-decision-user` 390px 进入 `/workflow/todos` 横向溢出 27px；PR #573 已通过 TDD 修复协同待办中心表格卡片可收缩边界、固定表格布局与内部横向滚动。
- ab213 部署后复验通过：14 角色菜单快照与四档视口主动作 `5/5` 通过；全部受保护授权页面聚合冒烟 `1/1` 通过；P5 核心只读探针 `21/21` 通过；目标缺陷单点复验 `390px documentWidth=390`，无权限态、HTTP 错误、浏览器错误和网络失败均为 0。证据在 `docs/release/evidence/p5-second-fresh-drill-20260612/14-role-journeys/postdeploy-ab213/` 与 `docs/release/evidence/p5-second-fresh-drill-20260612/core-readiness/p5-core-readiness-probe.json`。
- 阶段检查点：`docs/audit/p5-second-fresh-drill-checkpoint.md`。P5 仍在进行，尚未形成第一阶段正式验收；正式知识生产继续阻断，未配置文献资料库根地址，不得进入 P6。

## 当前下一步

1. 提交 ab213 发布与 post-deploy 证据，创建 PR，等待 CI 全绿并 squash 合并。
2. 继续跨角色审批、第一阶段端到端旅程、恢复、医疗安全、最小化、五方言与 GA 门禁。
3. 继续保持正式知识生产阻断；文献资料库根地址仍为空，不得配置正式资料库或生成正式知识，不得进入 P6。
4. P5 全部通过后形成第一阶段正式验收并冻结结构。

## 2026-06-12 P4 fd843 精确部署完成，14 角色菜单路由冒烟通过

- 当前执行线：P4 134 首轮 14 角色菜单路由缺陷已闭环；当前工作分支 `codex/p4-14-role-deploy-fd843`，本会话继续执行，不开新线程。
- PR #569 已 squash 合并为 `fd84369ded18f98568fcc5b4d9e7b216c25ebdda`，CI 8/8 通过；134 已部署该精确版本，manifest/commit 为 `fd84369ded18f98568fcc5b4d9e7b216c25ebdda`。
- fd843 发布前有效备份：`/zoesoft/medkernel/backups/p4-fd843-predeploy-20260612-184145/evidence/predeploy-backup.properties`，隔离恢复 `117|117`、178 张 public 基表、知识 `0|0|0|0|0|0`、文献资料库根地址长度 0，临时恢复库清理 0。
- fd843 发布前失败留痕：`p4-fd843-predeploy-20260612-183907` 为临时恢复库名含 `-` 导致 SQL 语法错误；`p4-fd843-predeploy-20260612-183958` 为应用数据库账号无 `CREATE DATABASE` 权限；`p4-fd843-predeploy-20260612-184040` 为 postgres 恢复用户不可读 root 目录下 dump。三次均未部署、未清库，`destructive_action_performed=false`。
- fd843 程序发布自动备份：`/zoesoft/medkernel/backups/deploy-20260612-184219`；post-deploy 证据：`/zoesoft/medkernel/backups/p4-fd843-predeploy-20260612-184145/evidence/post-deploy-fd843.properties`。
- fd843 post-deploy：jar SHA-256 `1f3d2e7af3a657a3aa741e2073b210ec1ab3a5ec344a2af89e57d492562c9036` 且 `jar_matches_expected=YES`；`medkernel|nginx|postgresql = active|active|active`；HTTP/HTTPS readiness 200；Flyway `117|117`；public 基表 178；知识 `0|0|0|0|0|0`；文献资料库根地址仍为空。
- fd843 完整 14 角色菜单路由冒烟通过：`docs/release/evidence/p4-first-drill-20260612/14-role-journeys/postdeploy-fd843/full-14-role-menu-smoke-fd843.json`，14/14 角色、134 条默认菜单路由通过。`diagnostic-service-user:/terminology/mapping` 存在页面内导出动作权限提示 1 条，但页面标题加载、无页面级无权限、无 4xx API、无 console error，不构成菜单路由失败。
- 正式知识生产仍未放行：文献资料库根地址仍未配置；正式根地址必须在系统配置页维护为 COS/S3/OSS/OBS/MinIO/HTTPS 网关等受管 URI，不得使用服务器 IP、存储厂商硬编码、`tmp`、本机目录或非加密 HTTP。

## 当前下一步

1. 提交 fd843 发布与 14 角色菜单路由通过证据，创建 PR，等 CI 通过并 squash 合并。
2. 证据合并后，P4 菜单路由缺陷闭环；进入 P5 第二轮全新演练前准备。
3. P5 前必须先重新备份、隔离恢复、确认回退路径，再按“全新处理”清库；仍不得在正式文献资料库根地址配置前生产正式知识。

## 2026-06-12 P4 b685 已部署，14 角色菜单守卫聚合补丁待 PR/部署

- 当前执行线：P4 134 首轮 14 角色菜单路由演练缺陷闭环；当前工作分支 `codex/p4-14-role-final`，本会话继续执行，不开新线程。
- PR #568 已 squash 合并为 `b68502e78e5697c68122355ae19ac1fd62260a6b`，CI 8/8 通过；134 已部署该版本，manifest/commit 为 `b68502e78e5697c68122355ae19ac1fd62260a6b`，服务健康。
- b685 发布前有效备份：`/zoesoft/medkernel/backups/p4-b685-predeploy-20260612-181139/evidence/predeploy-backup.properties`，隔离恢复 `117|117`、178 张 public 基表、知识 `0|0|0|0|0|0`、文献资料库根地址长度 0。
- b685 发布自动备份：`/zoesoft/medkernel/backups/deploy-20260612-181202`；post-deploy 证据：`/zoesoft/medkernel/backups/p4-b685-predeploy-20260612-181139/evidence/post-deploy-b685.properties`。
- b685 定向复验已通过：implementation-operator 可进入 `/admin/users`，证据 `docs/release/evidence/p4-first-drill-20260612/14-role-journeys/postdeploy-b685/implementation-operator-admin-users-b685.json` 与同目录截图。
- b685 完整 14 角色菜单冒烟仍失败：implementation-operator 访问 `/security/identity-binding` 显示“当前权限不足”，证据 `docs/release/evidence/p4-first-drill-20260612/14-role-journeys/postdeploy-b685/full-14-role-menu-smoke-b685.json` 与同目录失败截图。
- 聚合根因：后端 `DefaultPermissionPolicyTest` 14 角色菜单快照已授予 implementation-operator `identity-bindings`、`knowledge-governance`、`terminology-mapping`，但前端 `routes.ts` 对 `/security/identity-binding`、`/knowledge/governance`、`/terminology/mapping` 的 `requiredRoles` 未同步，导致菜单可见但路由层拦截。
- 本地补丁：三条路由显式加入 `implementation-operator`；`frontend/src/shared/config/routes.test.ts` 新增后端默认菜单快照解析与前端路由守卫一致性回归，避免后续同类错配逐个靠线上冒烟发现；`IDBIND-01`、`DICTMAP-01` 页面卡同步角色描述。
- 本地验证：红灯 `npm test -- src/shared/config/routes.test.ts` 先复现 implementation-operator 身份来源拦截，聚合测试又发现 `knowledge-governance`、`terminology-mapping` 两处同类错配；修复后 `npm test -- src/shared/config/routes.test.ts` 39/39 通过，`mvn -f medkernel-backend/pom.xml -Dtest='DefaultPermissionPolicyTest' test` 16/16 通过，`npm run build` 通过，`check-comment-zh`、`authenticity-guard --mode=all`、`config-boundary-guard --mode=inventory`、`git diff --check` 通过。
- 正式知识生产仍未放行：文献资料库根地址仍未配置；正式根地址必须在系统配置页维护为 COS/S3/OSS/OBS/MinIO/HTTPS 网关等受管 URI，不得使用服务器 IP、存储厂商硬编码、`tmp`、本机目录或非加密 HTTP。

## 当前下一步

1. 提交三处前端 route guard 聚合补丁、b685 失败/通过证据与验收报告追加，创建 PR，等 CI 通过并 squash 合并。
2. 从合并后的精确 `origin/main` 重建前端/后端制品；对 134 再做发布前备份、隔离恢复和留痕后部署。
3. 部署后复验 manifest、服务、Flyway、知识 0、文献资料库根地址仍为空，并重新运行完整 14 角色菜单路由冒烟。
4. 若 P4 菜单路由全绿，再回写 post-deploy 证据；P4 问题关闭后才重新备份清库进入 P5。

## 2026-06-12 P4 d432 后端修复已部署，前端路由守卫补丁待 PR/部署

- 当前执行线：P4 134 首轮 14 角色演练缺陷闭环；当前工作分支 `codex/p4-14-role-postdeploy`，本会话继续执行，不开新线程。
- PR #567 已 squash 合并为 `d432caa764d495861b4c945cfdb3073b781217af`，CI 8/8 通过；134 已部署该版本，manifest/commit 为 `d432caa764d495861b4c945cfdb3073b781217af`，服务健康。
- d432 发布前有效备份：`/zoesoft/medkernel/backups/p4-d432-predeploy-20260612-175338/evidence/predeploy-backup.properties`，隔离恢复 `117|117`、178 张 public 基表、知识 `0|0|0|0|0|0`、文献资料库根地址长度 0。失败留痕：`p4-d432-predeploy-20260612-175224` 为 dump 目录权限问题，`p4-d432-predeploy-20260612-175259` 为证据统计表名错误，二者均 `destructive_action_performed=false`。
- d432 发布自动备份：`/zoesoft/medkernel/backups/deploy-20260612-175403`；post-deploy 证据：`/zoesoft/medkernel/backups/p4-d432-predeploy-20260612-175338/evidence/post-deploy-d432.properties`。
- d432 前台复验仍失败：implementation-operator 访问 `/admin/users` 仍进入“当前权限不足”。失败证据：`docs/release/evidence/p4-first-drill-20260612/14-role-journeys/postdeploy-d432/implementation-operator-admin-users-d432.json` 与同目录失败截图。
- 新根因：后端 API 守卫和默认权限已放通，但前端 `frontend/src/shared/config/routes.ts` 的 `/admin/users` `requiredRoles` 缺少 `implementation-operator`，导致路由层在请求页面前拦截。
- 本地补丁：`/admin/users` 前端路由加入 `implementation-operator`；`frontend/src/shared/config/routes.test.ts` 新增 implementation-operator 可访问人员与账号断言。
- 本地验证：先红灯 `npm test -- src/shared/config/routes.test.ts` 复现 1 fail；修复后 `npm test -- src/shared/config/routes.test.ts` 38/38 通过；`mvn -f medkernel-backend/pom.xml -Dtest='DefaultPermissionPolicyTest,ComplianceUserControllerTest,PersonnelControllerTest' test` 32/32 通过；`check-comment-zh`、authenticity/config/migration guards、`git diff --check` 通过。
- 正式知识生产仍未放行：文献资料库根地址仍未配置；正式根地址必须在系统配置页维护为 COS/S3/OSS/OBS/MinIO/HTTPS 网关等受管 URI，不得使用服务器 IP、存储厂商硬编码、`tmp`、本机目录或非加密 HTTP。

## 当前下一步

1. 提交前端 route guard 补丁和 d432 失败证据，创建 PR，等 CI 通过并 squash 合并。
2. 从合并后的精确 `origin/main` 重建前端/后端制品；对 134 再做发布前备份、隔离恢复和留痕后部署。
3. 部署后复验 manifest、服务、Flyway、知识 0、文献资料库根地址仍为空，并重新运行 implementation-operator `/admin/users` 前台复验。
4. 若该缺陷关闭，再继续完整 14 角色菜单路由冒烟；P4 问题关闭后才重新备份清库进入 P5。

## 2026-06-12 P4 14 角色首轮演练发现缺陷，本地修复待 PR/部署闭环

- 当前执行线：P4 134 首轮 14 角色演练；当前工作分支 `codex/p4-14-role-drill`，本会话继续执行，不开新线程。
- 当前主线：`origin/main=a203289c82612ae65e06fb694bf3405ce5f67a61`，包含 P4 090e4155 精确重发证据文档追加；134 现场当前运行 manifest 仍为 `090e4155d90b74bc90259200483e8f4d7ecf6cbf`，本地修复尚未部署。
- 演练前有效备份：`/zoesoft/medkernel/backups/p4-pre-14-role-drill-20260612-164536`，隔离恢复 `117|117`、178 张 public 基表、知识 `0|0|0|0|0|0`、文献资料库根地址长度 0、首发身份/MFA 与角色分配正常。失败备份留痕：`/zoesoft/medkernel/backups/p4-pre-14-role-drill-20260612-164432/evidence/pre-drill-backup-failed.properties`，未执行破坏性动作。
- 已通过真实前台创建客户租户 `p4-hospital`、组织树、11 个客户角色账号；另以受控 API 前置创建 2 个平台治理角色账号。14 个角色登录工作台与唯一主动作冒烟已通过，证据在 `docs/release/evidence/p4-first-drill-20260612/14-role-journeys/00-role-journey-summary.json` 及同目录截图。
- 暴露缺陷 1：人员批量导入模板保留 UTF-8 BOM 时，后端将首列表头解析为带 BOM，导致预检 `HAS_ISSUES`。已本地修复 `PersonnelImportService` 剥离 BOM，并新增回归。
- 暴露缺陷 2：实施运维员菜单含“人员与账号”，但 `/admin/users` 显示“当前权限不足”。根因是后端 `PersonnelController`、`ComplianceUserController` 守卫缺少 `IMPLEMENTATION_OPERATOR`，已本地修复并新增人员/账号维护回归。失败证据：`docs/release/evidence/p4-first-drill-20260612/14-role-journeys/debug-menu-smoke/fail-implementation-operator-admin-users.json` 与同名截图。
- 本地验证已通过：`mvn -f medkernel-backend/pom.xml -Dtest='ComplianceUserControllerTest,PersonnelControllerTest' test`（16 tests）、`mvn -f medkernel-backend/pom.xml -Dtest='DefaultPermissionPolicyTest,ComplianceUserControllerTest,PersonnelControllerTest' test`（32 tests）、`bash scripts/check-comment-zh.sh`、`node scripts/authenticity-guard.mjs --mode=changed --base=origin/main`、`node scripts/config-boundary-guard.mjs --mode=changed --base=origin/main`、`node scripts/migration-convention-guard.mjs --mode=changed --base=origin/main`、`git diff --check`。
- 受控凭据文件：`/zoesoft/medkernel/conf/p4-14-role-drill-credentials-20260612.json`，权限 `600|medkernel|medkernel`；凭据、MFA secret、恢复码不得写入仓库或聊天记录。`organization-admin` 因首次 UI 自动化超时未捕获恢复码，但已完成改密/MFA，二次登录前台验证通过。
- 正式知识生产仍未放行：文献资料库根地址仍未配置；正式根地址必须在系统配置页维护为 COS/S3/OSS/OBS/MinIO/HTTPS 网关等受管 URI，不得使用服务器 IP、存储厂商硬编码、`tmp`、本机目录或非加密 HTTP。

## 当前下一步

1. 提交本地修复、演练证据与 `docs/audit/p4-first-fresh-deployment-acceptance.md` 追加；创建 PR，等待 CI 通过并 squash 合并。
2. 从合并后的精确 `origin/main` 重建制品；对 134 执行发布前备份、隔离恢复和留痕后部署。
3. 部署后验证 manifest、服务、Flyway、知识 0、文献资料库根地址仍为空，并重新运行 14 角色菜单路由冒烟，确认 implementation-operator `/admin/users` 不再越权。
4. Post-deploy 证据回写本文件和 P4 验收报告后，若 P4 问题关闭，再重新备份并清库进入 P5 第二轮完整重演；不得复用首轮业务结果冒充通过。

## 2026-06-12 P4 090e4155 精确重发完成，待进入 14 角色演练

- 当前执行线：P4 134 首轮演练。当前工作分支 `codex/p4-final-deploy-evidence` 仅用于提交本轮证据更新；合并后从最新 `origin/main` 继续，不开新线程。
- 当前主线：PR #563、#564、#565 已 squash 合并，`origin/main=090e4155d90b74bc90259200483e8f4d7ecf6cbf`。
- 当前 134 已从精确主线 `090e4155d90b74bc90259200483e8f4d7ecf6cbf` 重建、备份、隔离恢复并完成受控重发；发布日志已验证无 `LIBARCHIVE.xattr` 噪声。
- 首发管理员 `platform-admin` 已完成创建、首次改密、MFA 绑定并进入工作台；凭据、MFA secret、恢复码仅在服务器受控凭据文件，未写入仓库和聊天记录。
- 正式知识生产仍未放行：文献资料库根地址仍未配置；正式根地址必须在系统配置页维护为受管 URI（COS/S3/OSS/OBS/MinIO/HTTPS 网关等），不得使用服务器 IP、存储厂商硬编码、`tmp`、本机目录或非加密 HTTP。
- 长目标持续绑定当前 Codex 会话；上下文过长时只在本会话压缩整理，不创建、切换或引导进入新线程。

## 当前现场

- 主机：`root@193.112.107.134`，主机名 `VM-0-13-opencloudos`，部署根目录 `/zoesoft/medkernel`。
- 运行版本：manifest `source/commit=090e4155d90b74bc90259200483e8f4d7ecf6cbf`。
- 后端 jar SHA-256：`6ec6f7845051e66215cbc7a6979e1e473fc18cb3f21ad01d68d8f829f5067982`。
- 前端上传包 SHA-256：`0d3815060ca9d634ba97473bc60534e9bdf3cd6816f54989a7f191a0d6b5c7ce`。
- 服务：`medkernel|nginx|postgresql = active|active|active`，HTTP readiness `200`，HTTPS readiness `200`，`/medkernel/api/v1/bootstrap/status` 返回 `initialized=true`。
- 数据库：PostgreSQL 15.18，Flyway `117|117`，public 基表 178 张。
- 首发身份：`platform-admin` 已创建，`system-superadmin` 有效分配 1 条，凭据状态 `platform-admin:N:ACTIVE:MFA_SET`。
- 令牌状态：旧令牌 `REVOKED`，当前接管令牌 `USED:platform-admin`，ACTIVE 令牌 0；`bootstrap-init-token.txt` 与环境令牌一致，权限 `600|medkernel|medkernel`。
- 知识数据：`knowledge_identity|knowledge_asset_version|knowledge_package|mk_knowledge_customization|mk_pkg_package_entitlement|mk_pkg_tenant_package_reference = 0|0|0|0|0|0`。
- 文献资料库根地址：`medkernel.knowledge.literature.material-root-uri` 当前值长度 0，元数据 `SYSTEM|PLATFORM_SEED|Y|Y|1`。

## 备份、证据与回退

- P3 首轮备份：`/zoesoft/medkernel/backups/p3-prep-20260612-124124`，隔离恢复通过。
- P3 发布前备份：`/zoesoft/medkernel/backups/p3-pre-release-20260612-133831`，隔离恢复通过。
- P4 清库前最终备份：`/zoesoft/medkernel/backups/p4-pre-clear-20260612-135752`，隔离恢复通过。
- V117 发布前有效备份：`/zoesoft/medkernel/backups/p4-v117-predeploy-20260612-143821`，隔离恢复通过；程序发布自动备份 `/zoesoft/medkernel/backups/deploy-20260612-143920`。
- UI 首发接管证据：`/zoesoft/medkernel/backups/p4-v117-release-20260612-143920/evidence/ui-bootstrap.properties`，截图归档 `medkernel-p4-ui-evidence-20260612-1534.tar.gz`，敏感值已遮盖。
- #563/#564 重发前失败留痕：`/zoesoft/medkernel/backups/p4-e5f301e-predeploy-20260612-160749/evidence/predeploy-backup.properties`，`pg_dump` 因 root 私有备份目录权限被拒，`destructive_action_performed=false`。
- #563/#564 有效备份：`/zoesoft/medkernel/backups/p4-e5f301e-predeploy-20260612-160836/evidence/predeploy-backup.properties`，隔离恢复 `117|117`、178 张表、知识 `0|0|0|0|0|0`。
- 服务器发布脚本更新证据：`/zoesoft/medkernel/backups/p4-e5f301e-predeploy-20260612-160836/evidence/deploy-script-update.properties`，接管码同步日志无明文，交付文件与环境令牌一致。
- #563/#564 程序发布自动备份：`/zoesoft/medkernel/backups/deploy-20260612-160953`；该轮服务健康但 `tar_xattr_noise=YES`，保留为失败复现证据，不作为最终闭环。
- #565 有效备份：`/zoesoft/medkernel/backups/p4-090e4155-predeploy-20260612-162338/evidence/predeploy-backup.properties`，隔离恢复 `117|117`、178 张表、知识 `0|0|0|0|0|0`。
- #565 程序发布自动备份：`/zoesoft/medkernel/backups/deploy-20260612-162418`。
- #565 发布与验收证据：`/zoesoft/medkernel/backups/p4-090e4155-predeploy-20260612-162338/evidence/deploy-090e4155.properties`、`deploy-090e4155.log`、`post-deploy-090e4155.properties` 及对应 SHA-256。

## 已验证

- PR #562 已 squash 合并，主线包含 V117 五方言空值语义修复；CI 8/8 绿。
- PR #563 已 squash 合并：首发管理员成功页不再被 `initialized=true` 抢占，部署脚本同步 bootstrap 接管码交付文件；CI 8/8 绿。
- PR #564 已 squash 合并：`mk-publish.sh` 初步加入 `COPYFILE_DISABLE=1`，发布包契约进入 CI；CI 8/8 绿。真实重发暴露该修复不足，见 #565。
- PR #565 已 squash 合并：`mk-publish.sh` 加入 `--no-xattrs`，发布包契约收紧；CI 8/8 绿，真实 Linux GNU tar 探针 stderr 为空。
- 本地 V117 验证已通过：H2、PostgreSQL、Oracle 真实迁移/重复迁移/空配置回滚；后端聚焦测试、前端 SecurityBaseline、guard、生产构建均通过。
- UI 首发接管真实前台通过：部署接管码、创建首发管理员、登录、首次改密、MFA secret 生成、TOTP 校验、恢复码保存、进入工作台；浏览器 console errors 0、failed requests 0。
- 134 `090e4155` post-deploy 独立验收通过：manifest/commit 精确匹配，jar SHA 与本地一致，前端上传包 SHA 与本地一致，HTTP/HTTPS readiness 200，Flyway `117|117`，首发身份/MFA 正常，接管码日志无明文，知识数据 0，正式文献资料库根地址仍未配置。

## 风险与边界

- 当前 134 已完成首发身份初始化；正式生产前仍按全新标准处理。P4 问题关闭后，P5 需重新备份、清库并完整重演，不复用 P4 业务结果冒充通过。
- 旧库 V6/V25/V43 校验和差异为历史迁移原地修改造成；项目未上线且用户要求全新处理，因此不执行 Flyway repair、不为旧演练库新增兼容迁移。
- 当前未配置正式文献资料库根地址，未生成正式知识，未接入 wave2 模型网关。不得跳到 P6。
- 本轮证据文档随 `codex/p4-final-deploy-evidence` 同步；若读取到的是已合并 `main`，可直接进入 14 角色 P4 全流程。

## 下一步

1. 确认本轮证据文档更新已合并到最新 `origin/main`；若仍在分支，先提交、PR、CI、合并。
2. 从最新 `origin/main` 继续当前会话，执行真实前台 14 角色 P4 首轮全流程；API 只用于模拟外部系统或铺设无关前置。
3. 发现不合理功能时登记、复现、定根因、写失败测试并重构，不为旧演练数据或旧包保留兼容负担。
4. P4 完整问题清单关闭后，重新备份并清库，进入 P5 第二轮完整重演；不得复用首轮业务结果冒充通过。
5. P5 与第一阶段正式验收通过、结构冻结后，才可在系统配置页维护正式文献资料库受管 URI 并进入 P6。
