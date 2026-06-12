# P3 演练前发布准备验收报告

> 日期：2026-06-12  
> 阶段：P3 演练前发布准备  
> 结论：已完成 `193.112.107.134` 主机、部署目录、数据库、当前版本、回退路径、前置备份、隔离恢复验证和本地发布候选制品构建；本报告只放行进入“发布前最终复核与受控上传”的下一步准备，不放行清库、首轮演练、平台知识生成或 wave2。  
> 关键差异：134 当前仍运行 `2026-06-11T18:36:05+08:00` 的旧包，Flyway 最新版本为 `114`；当前 main `f5db9dba` 的 P2/V116 尚未发布到 134，因此线上配置中心尚无 `medkernel.knowledge.literature.material-root-uri`。

## 1. 输入与边界

- 权威输入：`docs/_HANDOFF.md`、`docs/audit/context-snapshots/2026-06-12-p2-global-product-ia.md`、`docs/audit/global-product-ia-acceptance.md`。
- 本地起点：`f5db9dba (HEAD, origin/main, origin/HEAD, main) 完成全系统产品信息架构门禁`；P3 工作分支 `codex/p3-release-prep-134-verification`。
- 执行边界：只读核验与备份恢复验证；未发布、未清库、未恢复生产库、未改系统配置、未生成知识。
- 存储边界：正式文献资料库根地址必须通过系统配置页维护为受管 URI，兼容 COS/S3/OSS/OBS/MinIO/HTTPS 网关；不得写死服务器 IP 或存储厂商，不得使用 `tmp`、本机目录或非加密 HTTP。

## 2. 134 现场核验

| 项 | 结果 |
|---|---|
| SSH 主机 | `root@193.112.107.134` 可连接 |
| 主机名 | `VM-0-13-opencloudos` |
| 内核 | `Linux 6.6.119-49.22.oc9.x86_64` |
| 核验时间 | `2026-06-12T12:40:11+08:00` |
| 部署根目录 | `/zoesoft/medkernel`，属主 `medkernel:medkernel` |
| 后端 jar | `/zoesoft/medkernel/lib/medkernel.jar`，SHA-256 `51fdd05aabaad6b51f4016eeec9a4bcb0f4ff7934f5cb641c4117f651c90679e` |
| 前端 dist | `/zoesoft/medkernel/frontend/dist`，499 个文件 |
| 环境变量文件 | `/zoesoft/medkernel/conf/medkernel.env`，权限 `600` |
| 发布脚本 | `/usr/local/bin/medkernel-deploy` |
| systemd | `medkernel.service` active，`NRestarts=0`，MainPID `2290736` |
| Nginx / PostgreSQL | 均 active |
| 健康检查 | `http://127.0.0.1:18080/medkernel/actuator/health/readiness` 与 `https://127.0.0.1/medkernel/actuator/health/readiness` 均 `200 {"status":"UP"}` |
| 当前 manifest | `source=codex-demo-drill-audit-trace-jump-74353b56`，`deployedAt=2026-06-11T18:36:05+08:00` |
| 数据库 | PostgreSQL 15.18，库名 `medkernel`，Flyway 成功迁移 `114` 条，最新版本 `114`，public 基表 `172` 张 |
| 文献资料根地址 | `ABSENT`，因为 V116 尚未发布到 134 |

## 3. 回退路径

| 类别 | 证据 |
|---|---|
| 最近程序备份 | `/zoesoft/medkernel/backups/deploy-20260611-193235` |
| 程序回滚命令 | `medkernel-deploy --rollback /zoesoft/medkernel/backups/deploy-20260611-193235` |
| 程序回滚范围 | 恢复 jar、前端 dist、manifest，并重启服务 |
| 数据库回退边界 | `medkernel-deploy` 不会自动回退已经成功执行的 Flyway 前向迁移；数据库恢复必须使用 PostgreSQL dump，并在显式确认后执行 |
| P3 数据库恢复命令留痕 | `systemctl stop medkernel && sudo -u postgres pg_restore --clean --if-exists --no-owner -d medkernel /zoesoft/medkernel/backups/p3-prep-20260612-124124/database/medkernel.dump && systemctl start medkernel` |

## 4. 前置备份

| 备份项 | 路径 / 摘要 |
|---|---|
| 备份根目录 | `/zoesoft/medkernel/backups/p3-prep-20260612-124124/` |
| 数据库 dump | `database/medkernel.dump`，`1697195` 字节 |
| 配置包 | `config/server-config.tar.gz`，包含服务器侧配置、systemd、Nginx 和发布脚本；内容不打印到终端、不提交仓库 |
| 后端制品 | `artifacts/medkernel.jar` |
| 前端制品 | `artifacts/frontend-dist.tar.gz` |
| 摘要文件 | `SHA256SUMS`，8 条文件摘要 |
| 证据文件 | `evidence/p3-prep-evidence.properties` |

首次隔离恢复尝试因备份目录 `root:root 700` 导致 `postgres` 用户无法读取 dump 而失败；该失败已保留在 evidence 中。随后复制临时 postgres-owned dump 到 `/tmp`，完成隔离恢复验证并删除临时文件，未放宽备份目录权限。

## 5. 隔离恢复验证

| 检查项 | 结果 |
|---|---|
| 临时恢复库 | `medkernel_p3_prep_20260612_124124_restore` |
| 恢复后 Flyway 成功迁移数 | `114` |
| 恢复后最新版本 | `114` |
| 恢复后 public 基表数 | `172` |
| 恢复后配置中心表 | `mk_config_item` 存在 |
| 临时库清理 | 复查 `restore_dbs_after=NONE` |
| 服务复查 | `medkernel`、`nginx`、`postgresql` 均 active |
| HTTPS readiness 复查 | `200 {"status":"UP"}` |

## 6. 未冒领项

- 本报告不表示 P2/V116 已发布到 134；134 当前仍是 V114。
- 本报告不表示线上“平台知识文献资料库根地址”已通过验收；该键当前为 `ABSENT`，必须在发布 V116 后通过系统配置页复核和维护。
- 本报告不放行清库、P4 首轮演练、wave2、模型网关、平台知识生成或 GA 验收。
- P3 备份已验证可读，但不能替代清库或发布前的当次备份；后续任何高风险动作仍需重新备份、摘要、恢复命令和留痕。

## 7. 本地发布候选制品

| 制品 | 证据 |
|---|---|
| 后端构建命令 | `mvn -f medkernel-backend/pom.xml -DskipTests clean package`，`BUILD SUCCESS`；本次仅打包，测试沿用 P2 全量绿基线 |
| 后端 jar | `medkernel-backend/target/medkernel-backend-1.0.0-SNAPSHOT.jar` |
| 后端 jar 大小 | `75191342` 字节 |
| 后端 jar SHA-256 | `fc8834372796f4ddbfef10e69e87ff199cda73408087c3f61e22a5eac5572872` |
| 前端构建命令 | `npm --prefix frontend run build`，Vite `3408 modules transformed` |
| 前端 dist | `frontend/dist`，255 个文件 |
| 前端候选包 | `/tmp/medkernel-frontend-f5db9dba-dist.tar.gz` |
| 前端候选包大小 | `3563738` 字节 |
| 前端候选包 SHA-256 | `e49f38033d8f7b247095ecec16a8986483939558cfafdf5ee9abd02e64d4e46d` |

以上候选制品尚未上传、尚未发布。若发布前重新构建，必须刷新本节摘要，并以最新摘要为准。

## 8. 下一步

1. 对已构建候选制品做发布前最终复核；若重新构建，刷新 SHA-256。
2. 使用 `/usr/local/bin/medkernel-deploy` 受控发布到 134，让脚本生成发布备份并执行健康检查；上传/发布前不得清库。
3. 发布后验证 manifest、health、Flyway V116、系统配置页中的“平台知识文献资料库根地址”和数据库 `mk_config_item`。
4. 发布验收通过后，才允许编排 P4 清库/首轮演练；P4 前必须重新执行当次备份和隔离恢复验证。
