# 会话接力

## 2026-06-12 P4 首轮全新部署与真实前台演练

- 当前分支：`codex/p3-release-134`，基于 `origin/main=28d900cd6cce0d729b5260a0762198142a74bcb7`。
- P3 演练前发布准备已完成，权威报告：[P3 演练前发布准备验收报告](audit/p3-release-prep-acceptance.md)。
- P4 已完成 134 清库前最终备份、隔离恢复、空库重建、V1-V116 从零迁移和 `ec9901bf` 候选部署；该现场仅作为历史检查点，权威报告：[P4 首次全新部署验收报告](audit/p4-first-fresh-deployment-acceptance.md)。
- 发布前复核发现 Oracle 将空字符串持久化为 `NULL`，而 V31 的配置当前值和历史变更后值原为 `NOT NULL`。当前分支已新增 V117，在五方言放宽“未配置”值为空，并在读取层统一归一为空字符串；真实 PostgreSQL、Oracle、H2 从空库迁移、设置后回滚到未配置和重复迁移均通过。
- 当前 134 仍运行 V116，虽保持未初始化且 `GET /api/v1/bootstrap/status` 返回 `initialized=false`，但首次初始化门禁已暂停。必须先完成 V117 提交、PR、CI、合并，从精确 `origin/main` 重建并备份后重发，才可从 `/bootstrap` 开始真实前台演练。
- 正式知识生产仍未放行：`medkernel.knowledge.literature.material-root-uri` 当前为空，来源 `PLATFORM_SEED`、受保护、版本 1；必须在系统配置页维护正式受管 URI 后，且 P4/P5、第一阶段正式验收和结构冻结全部通过，才能进入 P6。
- 长目标持续绑定当前 Codex 会话；上下文过长时只在本会话内压缩整理，不创建、切换或引导用户进入新线程。

## 当前现场

- 主机：`root@193.112.107.134`，主机名 `VM-0-13-opencloudos`，部署根目录 `/zoesoft/medkernel`。
- 运行版本：manifest `source/commit=ec9901bf20976e0c4846713237510679ca698c35`，部署时间 `2026-06-12T14:02:07+08:00`。
- 后端 jar：`/zoesoft/medkernel/lib/medkernel.jar`，SHA-256 `8363ffa4f01efdd5465d8ce847a7eb57c6bde2577d3ca4277028a3b381835d0c`。
- 服务：`medkernel` active/running，`NRestarts=0`；内部和 Nginx HTTPS readiness 均为 `200 {"status":"UP"}`。
- 数据库：PostgreSQL 15.18，owner `medkernel`，Flyway 成功迁移 116 条、最新 V116，public 基表 178 张。
- 基线治理数据：SYSTEM 有效角色 15 个，`t-1` 平台职责分配 2 条，有效菜单权限 30 个。
- 知识数据：`knowledge_identity`、`knowledge_asset_version`、`knowledge_package`、`mk_knowledge_customization` 均为 0。

## 备份与回退

- P3 首轮备份：`/zoesoft/medkernel/backups/p3-prep-20260612-124124`，隔离恢复通过。
- P3 发布前备份：`/zoesoft/medkernel/backups/p3-pre-release-20260612-133831`，隔离恢复通过。
- P4 清库前最终备份：`/zoesoft/medkernel/backups/p4-pre-clear-20260612-135752`。
- 最终 dump：`database/medkernel.dump`，`1697196` 字节；恢复到 `medkernel_p4_pre_clear_20260612135752_restore` 后验证 Flyway V114、172 张表、`mk_config_item` 存在，状态 `PASSED`，临时库已删除。
- 最终备份留痕：`evidence/pre-clear.properties`、`SHA256SUMS`；数据库恢复命令已写入备份证据。
- 程序发布自动备份：`/zoesoft/medkernel/backups/deploy-20260612-140206`。
- 清库与部署留痕：`/zoesoft/medkernel/backups/p4-fresh-deploy-20260612-140144/evidence/clear-and-deploy.log`。
- 发布后独立验收：`/zoesoft/medkernel/backups/p4-fresh-deploy-20260612-140144/evidence/post-deploy.properties` 及其 SHA-256。
- 失败留痕：`p4-pre-clear-20260612-135641` 因目录遍历权限导致 `pg_restore` 无法读取，未执行破坏动作；首次清库脚本在空库创建后因 owner 查询引号错误退出，未部署候选包，随后从已确认的 0 表空库续接发布。两次均未冒充成功。

## 已验证

- 本地聚焦后端：`SystemConfigControllerTest#knowledgeLiteratureMaterialRootUriRequiresManagedStorageConfiguration` 通过。
- 本地聚焦前端：`SecurityBaseline.test.tsx` 7/7 通过。
- 本地扩展后端：`SystemConfigControllerTest,MigrationBaselineContractTest,H2BaselineMigrationTest` 通过。
- 本地五方言迁移：V117 静态合同、H2 基线、Testcontainers 真实 PostgreSQL/Oracle/H2 从空库迁移与重复迁移通过；三种真实数据库均验证当前值和历史回滚值可持久化未配置状态。
- 本地生产构建：后端 `mvn -f medkernel-backend/pom.xml -DskipTests clean package` 和前端 `npm --prefix frontend run build` 通过。
- 远端空库预检：临时 PostgreSQL 数据库从 V1 迁移至 V116，116 条迁移、178 张表、15 个角色、2 条平台职责、30 个菜单权限，候选进程 health 200，文献根地址为空；临时库和 18089 进程已清理。
- 正式清库后独立复核：manifest、jar SHA、systemd、内外 health、Flyway V116、178 张表、基线治理数据、空文献根地址和 0 知识资产均通过。

## 风险与边界

- 旧库 V6/V25/V43 与当前迁移文件校验和不一致；这是历史迁移被原地修改的结果。项目尚未上线且用户明确要求全新处理，因此未执行 Flyway repair、未新增历史兼容迁移，也不保留旧演练数据。
- `ec9901bf` 当前仍是工作分支候选，尚未合并 `origin/main`；完成文档门禁后必须推送、PR、CI、合并。若主线发生实质漂移，应重新构建并评估是否需要重发。
- 134 当前 V116 不再具备首次初始化放行资格；V117 精确主线制品部署并验证前，不得创建首位管理员。
- 前端 tar 解包出现 macOS `LIBARCHIVE.xattr.com.apple.provenance` 扩展头警告，不影响 511 个文件解包和健康检查；后续制品脚本应考虑排除该扩展属性。
- 134 尚未完成首次管理员初始化、14 角色真实前台全流程、首轮问题登记、第二轮重演、第一阶段正式验收、结构冻结、正式存储配置或知识生成。

## 下一步

1. 完成 V117 与文档门禁、提交、推送、PR、CI 和合并。
2. 从合并后的精确 `origin/main` 重建制品；对 134 再做即时备份和隔离恢复后受控重发，验证 Flyway V117、`config_value` 可空、配置读取为空字符串以及内外健康。
3. 在 134 `/bootstrap` 走首次部署引导，凭证只保存在既有受控凭证位置，不写仓库、不打印到日志。
4. 从真实前台按角色完成 P4 首轮全流程；API 只用于模拟外部系统或铺设无关前置。
5. 发现不合理功能时登记、复现、定根因、写失败测试并重构，不为旧演练数据或旧包保留兼容负担。
6. P4 完整问题清单关闭后，重新清库并执行 P5 第二轮完整重演；不得复用首轮业务结果冒充通过。
7. P5 与第一阶段正式验收通过、结构冻结后，才可在系统配置页维护正式文献资料库受管 URI并进入 P6。
