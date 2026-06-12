# 会话接力

## 2026-06-12 P3 演练前发布准备

- 当前分支：`codex/p3-release-prep-134-verification`，从 `origin/main` / `main` 的 PR #560 squash merge `f5db9dba` 切出。P2 全系统产品门禁与“平台知识文献资料库根地址”配置中心补丁已在 main。
- P3 已完成 `193.112.107.134` 首轮现场核验、前置备份和隔离恢复验证；权威报告：[P3 演练前发布准备验收报告](audit/p3-release-prep-acceptance.md)。
- 本次只做核验与备份恢复验证；未执行清库、发布、生产库恢复、知识生成或系统配置变更。
- P2 权威报告仍为：[全系统产品信息架构门禁验收报告](audit/global-product-ia-acceptance.md)。P2 精简入口：[P2 精简上下文快照](audit/context-snapshots/2026-06-12-p2-global-product-ia.md)。

## 已完成

- 本地核验：当前工作树起点为 `f5db9dba (HEAD, origin/main, origin/HEAD, main)`；本地工具 `rg/jq/gh/ssh/docker` 可用，`fd/bat/psql/pg_dump/mysql/mysqldump` 本机不可用但远端 PostgreSQL 工具可用。
- 仓库部署合同核验：134 使用 onprem 单机约定，部署根目录 `/zoesoft/medkernel`，发布入口 `/usr/local/bin/medkernel-deploy`，应用端口 `127.0.0.1:18080`，PostgreSQL `127.0.0.1:5432`，HTTPS 443；发布脚本只替换 jar/dist，不直接清库。
- 134 主机核验：SSH 只读连接 `root@193.112.107.134` 成功；主机 `VM-0-13-opencloudos`，Linux `6.6.119-49.22.oc9.x86_64`，核验时间 `2026-06-12T12:40:11+08:00`。
- 134 部署目录核验：`/zoesoft/medkernel` 属主 `medkernel:medkernel`；`lib/medkernel.jar`、`frontend/dist/index.html`、`conf/medkernel.env`、`backups/`、`logs/`、`manifest.properties`、Nginx 与 systemd 配置均存在；`medkernel.env` 权限为 `600`。
- 134 当前运行核验：`medkernel`、`nginx`、`postgresql` 均 active；`medkernel` MainPID `2290736`，`NRestarts=0`，启动时间 `2026-06-11 19:32:36 CST`；HTTP 与 HTTPS readiness 均 `200 {"status":"UP"}`，`/medkernel/api/v1/auth/login-tenants` 返回平台主租户与演练客户租户。
- 134 当前版本核验：manifest `source=codex-demo-drill-audit-trace-jump-74353b56`，`deployedAt=2026-06-11T18:36:05+08:00`，jar SHA-256 `51fdd05aabaad6b51f4016eeec9a4bcb0f4ff7934f5cb641c4117f651c90679e`，前端 dist 499 个文件。
- 134 数据库核验：PostgreSQL 15.18，库名 `medkernel`，Flyway 成功迁移 `114` 条，最新版本 `114`，public 基表 `172` 张；当前线上尚未部署 P2 的 V116，因此 `medkernel.knowledge.literature.material-root-uri` 在 134 生产库中为 `ABSENT`。
- 回退路径核验：最近发布备份 `/zoesoft/medkernel/backups/deploy-20260611-193235` 可用于程序包回滚，命令 `medkernel-deploy --rollback /zoesoft/medkernel/backups/deploy-20260611-193235`；注意发布脚本回滚 jar/dist，已经前向成功的 Flyway 数据库迁移不会自动回滚，数据库恢复需走单独备份恢复命令。
- P3 前置备份已生成：`/zoesoft/medkernel/backups/p3-prep-20260612-124124/`，包含 PostgreSQL 完整 dump、服务器配置包、当前 jar、当前前端 dist、`SHA256SUMS` 和 evidence 文件。
- P3 备份隔离恢复已验证：`database/medkernel.dump` 大小 `1697195` 字节；恢复到临时库 `medkernel_p3_prep_20260612_124124_restore` 后读到 Flyway `114`、public 基表 `172` 张、`mk_config_item` 存在，随后临时库已删除；复查 `restore_dbs_after=NONE`，服务仍 active，HTTPS readiness 仍 `200`。
- 备份留痕文件：`/zoesoft/medkernel/backups/p3-prep-20260612-124124/evidence/p3-prep-evidence.properties`。首次隔离恢复曾因备份目录 `root:root 700` 导致 `postgres` 无法读取 dump 而失败，已在 evidence 中记录，并通过临时 postgres-owned dump 重新验证通过；半成品未冒充通过。
- 本地 P3 发布候选制品已构建但未上传、未发布：后端 jar `medkernel-backend/target/medkernel-backend-1.0.0-SNAPSHOT.jar`，大小 `75191342` 字节，SHA-256 `fc8834372796f4ddbfef10e69e87ff199cda73408087c3f61e22a5eac5572872`；前端 dist 255 个文件，候选包 `/tmp/medkernel-frontend-f5db9dba-dist.tar.gz`，大小 `3563738` 字节，SHA-256 `e49f38033d8f7b247095ecec16a8986483939558cfafdf5ee9abd02e64d4e46d`。

## 验证证据

- `git status --short --branch`：当前分支为 `codex/p3-release-prep-134-verification`。
- `git log --oneline --decorate --max-count=12 --all`：`f5db9dba (HEAD, origin/main, origin/HEAD, main) 完成全系统产品信息架构门禁`。
- `git show --stat --oneline --decorate f5db9dba`：确认 PR #560 squash merge 已包含 P2 产品 IA 与配置中心补丁。
- 远端只读核验命令：SSH 执行主机、目录、服务、manifest、jar SHA、回滚目录、health、PostgreSQL/Flyway 和配置中心键查询；输出已汇总到 [P3 报告](audit/p3-release-prep-acceptance.md)。
- 远端备份命令：SSH 在 `/zoesoft/medkernel/backups/p3-prep-20260612-124124/` 生成数据库、配置、制品和证据备份，并计算 SHA-256。
- 远端隔离恢复命令：从备份 dump 恢复到临时库 `medkernel_p3_prep_20260612_124124_restore`，验证 Flyway、表数量和 `mk_config_item` 后删除临时库。
- 远端复查：`restore_dbs_after=NONE`；`medkernel_active=active`、`nginx_active=active`、`postgresql_active=active`；`https_readiness_http=200`。
- 本地候选制品：`mvn -f medkernel-backend/pom.xml -DskipTests clean package` 成功，`BUILD SUCCESS`；`npm --prefix frontend run build` 成功，Vite `3408 modules transformed`；随后对后端 jar 与前端 dist tar 计算 SHA-256。

## 未冒领与阻断

- 134 当前仍运行旧版本：manifest 指向 `codex-demo-drill-audit-trace-jump-74353b56`，数据库 Flyway 最新版本为 `114`；当前 main `f5db9dba` 的 P2/V116 尚未发布到 134。
- 134 生产库尚无配置键 `medkernel.knowledge.literature.material-root-uri`；这是“未发布 V116”的结果，不得写成已通过线上配置验收。发布 P2 包后必须通过系统配置页维护正式文献资料库根地址，并复核其不是 `tmp`、服务器本地目录、硬编码 IP、存储厂商绑定路径或非加密 HTTP。
- 本次没有执行清库、发布、生产库恢复、知识生成、系统配置变更、模型网关接入或 wave2 任务。
- P3 前置备份已验证可读，但不能替代 P4 清库/首轮演练前的即时备份；任何清库、发布或恢复动作仍需先生成当次备份、摘要和恢复命令，并在报告中留痕。
- `medkernel-deploy --rollback` 只能恢复 jar 与前端 dist；数据库前向迁移的回退必须依赖本次或当次 PostgreSQL dump，并需显式确认后执行，不得把程序回滚等同数据库回滚。

## 下一步

1. 使用已构建的 `f5db9dba` 后端 jar 与前端 dist 候选包执行发布前最终复核；若重新构建，必须刷新制品摘要。
2. 发布动作必须继续使用 `/usr/local/bin/medkernel-deploy`，触发其内置部署备份与健康检查；上传/发布前不得清库。
3. 发布到 134 后立即验证：manifest 指向 `f5db9dba`，服务 health 为 UP，Flyway 升至 V116，系统配置页和数据库均能看到 `medkernel.knowledge.literature.material-root-uri`，且值为受管资料库根地址。
4. 发布验收未通过前不得清库、不得进入 P4 首轮演练、不得生成平台知识资产。
5. P4 清库前必须重新做当次数据库、配置、制品和关键证据备份，并在隔离目标验证可读；不得复用 P3 备份冒充清库前即时证据。
6. P6 前必须完成第一阶段正式验收并冻结 134 平台知识结构；平台知识资产只允许在 134 上生成，正式文献资料根地址必须来自系统配置页维护的受管 URI。
