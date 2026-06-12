# P4 首次全新部署验收报告

> 日期：2026-06-12
> 阶段：P4 134 首轮演练，第 1 个检查点
> 结论：通过“清库、从零迁移、候选发布与初始化前基线”检查点；真实客户前台全流程尚未开始，不得把本报告视为 P4 完成。
> 产品口径：正式生产知识前环境一律按全新处理，不保留旧包、旧库、旧角色、旧菜单、旧演练数据或迁移兼容负担；每次清库仍须先备份、验证恢复并留痕。

## 1. 清库前门禁

| 检查项 | 结果 |
|---|---|
| 最终备份 | `/zoesoft/medkernel/backups/p4-pre-clear-20260612-135752` |
| dump | `database/medkernel.dump`，`1697196` 字节 |
| 隔离恢复 | V114、172 张表、`mk_config_item` 存在，`restore_status=PASSED` |
| 临时恢复库清理 | `restore_cleanup_count=0` |
| 候选 jar SHA-256 | `8363ffa4f01efdd5465d8ce847a7eb57c6bde2577d3ca4277028a3b381835d0c` |
| 候选前端 SHA-256 | `00eeea5e623bb26f4303e787e20ffc89112f812ed284a69f43b9bc262104066b` |
| 清库前现场 | `medkernel/nginx/postgresql` active，HTTPS readiness 200 |

## 2. 受控清库与发布

- 操作时间：`2026-06-12T14:01:44+08:00` 至 `14:02:33+08:00`。
- 清库动作：停止 `medkernel`，终止 `medkernel` 数据库连接，删除旧库，以 owner `medkernel` 创建同名空库。
- 空库确认：数据库存在、owner `medkernel`、public 表数 0。
- 发布命令显式指定 `ec9901bf` 后端 jar 和前端 tar，不使用 incoming 自动发现，避免选中旧候选。
- 发布自动备份：`/zoesoft/medkernel/backups/deploy-20260612-140206`。
- 清库/部署证据：`/zoesoft/medkernel/backups/p4-fresh-deploy-20260612-140144/evidence/clear-and-deploy.log`。
- 首次脚本在空库创建后读取 owner 时因 shell 引号错误退出，候选包尚未部署；现场确认数据库存在、owner 正确、表数 0 后直接续接发布。该失败未触发重复清库，也未被记为成功。
- 发布脚本最终返回健康检查通过和“发布成功”。

## 3. 发布后独立验收

| 检查项 | 结果 |
|---|---|
| manifest | `source/commit=ec9901bf20976e0c4846713237510679ca698c35` |
| 部署时间 | `2026-06-12T14:02:07+08:00` |
| 运行 jar SHA-256 | `8363ffa4f01efdd5465d8ce847a7eb57c6bde2577d3ca4277028a3b381835d0c` |
| systemd | active/running，`NRestarts=0` |
| 内部 readiness | 200，`{"status":"UP"}` |
| Nginx HTTPS readiness | 200，`{"status":"UP"}` |
| 数据库 | owner `medkernel`，Flyway 116 条、最新 V116，178 张 public 基表 |
| SYSTEM 角色 | 有效 15 个 |
| 平台职责种子 | `t-1` 有效分配 2 条 |
| 菜单权限 | 有效 MENU 权限 30 个 |
| 首次部署状态 | `/bootstrap` 200；`GET /api/v1/bootstrap/status` 返回 `initialized=false` |

独立验收文件：`/zoesoft/medkernel/backups/p4-fresh-deploy-20260612-140144/evidence/post-deploy.properties` 及其 SHA-256。

## 4. 知识与存储门禁

| 检查项 | 结果 |
|---|---|
| 配置键 | `medkernel.knowledge.literature.material-root-uri` |
| tenant / source | `SYSTEM` / `PLATFORM_SEED` |
| 当前值 | 空，长度 0，即“未配置” |
| 保护 / 生效 / 版本 | `Y` / `Y` / `1` |
| `knowledge_identity` | 0 |
| `knowledge_asset_version` | 0 |
| `knowledge_package` | 0 |
| `mk_knowledge_customization` | 0 |

因此当前没有正式知识资产，也没有绑定任何服务器 IP、存储厂商、本地目录或非加密 HTTP。正式知识生产仍被门禁阻断；P6 前必须在系统配置页维护经批准的 COS/S3/OSS/OBS/MinIO/HTTPS 网关受管 URI。

## 5. 已知非阻断项

- 前端 tar 在 GNU tar 解包时提示 macOS `LIBARCHIVE.xattr.com.apple.provenance` 扩展头未知；511 个文件完成解包，发布与健康检查通过。后续应在制品打包脚本中排除该扩展属性，减少部署噪声。
- 当前运行提交尚未合并 `origin/main`；本分支需完成门禁、PR、CI 和合并。若合并前主线出现运行时实质变更，必须重新构建并评估重发。

## 6. 下一检查点

1. 完成首次管理员初始化，凭证和恢复码只写入受控凭证位置。
2. 从真实前台按 14 角色执行首轮客户全流程，记录功能、体验、医疗安全、权限、审计和数据最小化问题。
3. 对不合理功能执行复现、根因、失败测试和重构，直到达到全新产品标准。
4. P4 问题关闭后重新备份并清库，进入 P5 第二轮完整重演，不复用本轮业务数据。
