# P4 首次全新部署验收报告

> 日期：2026-06-12
> 阶段：P4 134 首轮演练，第 1 个检查点
> 结论：V116 清库发布仅作为历史检查点；V117 已完成真实 UI 首发接管。本轮演练暴露的首发成功页、接管码交付文件和前端发布包扩展属性噪声问题已通过 PR #563/#564/#565 合并并从精确主线 `090e4155d90b74bc90259200483e8f4d7ecf6cbf` 重建、备份、隔离恢复、干净重发。`090e4155` 是当前 P4 首轮演练有效运行基线。
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

## 5. 已知项处理状态

- 前端 tar 在 GNU tar 解包时提示 macOS `LIBARCHIVE.xattr.com.apple.provenance` 扩展头未知的问题已在 PR #565 修复：`mk-publish.sh` 使用 `COPYFILE_DISABLE=1 tar --no-xattrs -czf`，并由 `deploy/onprem/tests/validate-mk-publish-package.sh` 纳入 CI guard。`090e4155` 真实重发日志验证 `tar_xattr_noise=NO`。
- 当前运行提交已合并 `origin/main=090e4155d90b74bc90259200483e8f4d7ecf6cbf`；本报告记录本轮证据状态。

## 6. 发布前复核追加

- 根因：Oracle 将 JDBC 空字符串持久化为 `NULL`，V31 的 `mk_config_item.config_value` 和 `mk_config_history.after_value` 非空约束会分别阻断初始未配置状态和设置后的审计回滚。
- TDD 红灯：先将最新迁移版本提升到 117，并要求 H2、PostgreSQL、Oracle 接受当前值和历史变更后值为 `NULL`；V117 尚不存在时静态合同失败，H2 插入因非空约束失败，设置后回滚接口返回 400。
- 修复：五方言新增 `V117__nullable_unconfigured_system_config.sql`，放宽当前值和历史变更后值非空约束；仓储读取层把数据库 `NULL` 统一返回空字符串，服务层只允许该配置通过审计回滚恢复为“未配置”。
- 绿灯：`MigrationBaselineContractTest`、`H2BaselineMigrationTest`、`SystemConfigControllerTest` 共 122 项通过；`FlywayMultiDialectSmokeTest` 使用真实 PostgreSQL、Oracle、H2 从空库迁移到 V117、验证当前值与历史回滚值写入并重复迁移 0 条，退出码 0。
- 裁决：V117 是全新产品的跨方言语义修正，不用于兼容旧演练库。V116 检查点已停在未初始化状态，后续已按本报告第 8-9 节完成 V117 精确主线部署和真实 UI 首发接管。

## 7. 下一检查点

1. 对本轮演练暴露的 UI 成功页抢占和部署令牌交付文件不同步问题完成 PR、CI、合并。
2. 从精确 `origin/main` 重建无扩展属性噪声的制品；对 134 做即时备份和隔离恢复后重发。
3. 重发后独立验证 manifest、jar SHA、服务健康、bootstrap initialized、首发管理员 MFA、令牌交付文件同步能力和知识数据仍为 0。
4. 从真实前台按 14 角色执行首轮客户全流程，记录功能、体验、医疗安全、权限、审计和数据最小化问题。
5. 对不合理功能执行复现、根因、失败测试和重构，直到达到全新产品标准。
6. P4 问题关闭后重新备份并清库，进入 P5 第二轮完整重演，不复用本轮业务数据。

## 8. V117 精确主线部署追加

| 检查项 | 结果 |
|---|---|
| PR / 主线 | PR #562 squash merge，`origin/main=1876aec72f459b4980f8aa769f4caf7ce5afc7b1` |
| CI | 8/8 通过 |
| 后端制品 SHA-256 | `bc9ae052f0887eb659cd6990ed52ddbe1ba0e67b0bebd782b8abc5397c0d162f` |
| 前端制品 SHA-256 | `241b5511b59ee0fede5829ff36b2a563bec29bfb8916c7e3dfaa340f0af9220d` |
| V117 发布前有效备份 | `/zoesoft/medkernel/backups/p4-v117-predeploy-20260612-143821` |
| 程序发布自动备份 | `/zoesoft/medkernel/backups/deploy-20260612-143920` |
| 发布证据 | `/zoesoft/medkernel/backups/p4-v117-release-20260612-143920/evidence/post-deploy.properties` |
| Flyway | `117|117` |
| 服务健康 | 内部/Nginx HTTPS readiness 均为 200 |
| 文献资料库根地址 | 空值，仍为 `PLATFORM_SEED` 受保护配置，未正式配置 |
| 知识数据 | `knowledge_identity=0`、`knowledge_asset_version=0`、`knowledge_package=0`、`mk_knowledge_customization=0` |

失败留痕：`/zoesoft/medkernel/backups/p4-v117-predeploy-20260612-143741` 因备份路径权限导致隔离恢复失败，证据记录 `destructive_action_performed=false`，未执行服务、数据库或配置变更。

## 9. 令牌治理与真实 UI 首发接管

| 检查项 | 结果 |
|---|---|
| 令牌裁剪 | `/zoesoft/medkernel/backups/p4-v117-pre-token-prune-20260612-144112/evidence/token-prune.properties`，`active_tokens_after=1|1` |
| 令牌交付文件同步 | `/zoesoft/medkernel/backups/p4-v117-pre-token-file-sync-20260612-144644/evidence/token-file-sync.properties`，状态 `PASSED` |
| 首发 UI 证据 | `/zoesoft/medkernel/backups/p4-v117-release-20260612-143920/evidence/ui-bootstrap.properties` |
| 截图归档 | `/zoesoft/medkernel/backups/p4-v117-release-20260612-143920/evidence/medkernel-p4-ui-evidence-20260612-1534.tar.gz` |
| 接管状态 | `platform-admin` 已创建，`system-superadmin` 有效分配 1 条 |
| 凭据状态 | `platform-admin:N:ACTIVE:MFA_SET` |
| 接管令牌 | `USED:platform-admin`，无有效 ACTIVE 令牌 |
| 浏览器前台 | console errors 0，failed requests 0 |
| 受控凭据文件 | `/zoesoft/medkernel/conf/p4-first-admin-credentials-20260612.json`，`600|medkernel|medkernel` |
| 知识主链路 | `0|0|0|0|0|0` |

首发 UI 真实流程已走通：部署接管码、创建首发管理员、登录、首次改密、MFA secret 生成、TOTP 校验、恢复码保存、进入工作台。MFA secret 与恢复码只写入服务器受控凭据文件；截图中敏感值已遮盖。

## 10. 演练暴露缺陷与本地修复

| 缺陷 | 根因 | 修复状态 |
|---|---|---|
| 首发管理员创建成功后页面未出现“首发管理员已创建”提示 | `createBootstrapAdmin` 成功后刷新 bootstrap status，`initialized=true` 的全局分支抢占本地 `login-required` 阶段 | 已在 `frontend/src/pages/Bootstrap.tsx` 收窄初始化状态门禁至 `init-token` 阶段 |
| 服务器交付文件 `/zoesoft/medkernel/conf/bootstrap-init-token.txt` 与环境令牌不同步 | 发布时轮换了 `MEDKERNEL_BOOTSTRAP_INIT_TOKEN`，但部署脚本只更新环境文件，未同步现场可读取的接管码交付文件 | 已在 `deploy/onprem/medkernel-deploy.sh` 增加 `--sync-bootstrap-token`，并在发布备份后、重启前自动同步，不输出明文 |

本地红绿验证：

- `npm test -- Bootstrap.test.tsx`：10/10 通过。
- `npm test -- Bootstrap.test.tsx Login.test.tsx`：31/31 通过。
- `npm run lint`、`npm run typecheck`：通过。
- `bash -n deploy/onprem/medkernel-deploy.sh deploy/onprem/tests/validate-medkernel-deploy.sh`：通过。
- `bash deploy/onprem/tests/validate-medkernel-deploy.sh`：通过，日志未泄露接管码明文。
- `node --test scripts/authenticity-guard.test.mjs scripts/migration-convention-guard.test.mjs scripts/config-boundary-guard.test.mjs`：38/38 通过。
- `authenticity-guard --mode=changed`、`migration-convention-guard --mode=changed`、`config-boundary-guard --mode=changed`：通过。
- `git diff --check`：通过。
- `.github/workflows/ci.yml` 的 `guard-rules` 已纳入 `deploy/onprem/tests/validate-medkernel-deploy.sh`，后续 PR 会自动覆盖该发布脚本契约。

上述修复已通过 PR #563 合并并在后续 `090e4155` 重发中生效；14 角色 P4 全流程前不再以这两项为阻断。

## 11. PR #563/#564/#565 收敛与发布噪声根因

| 项 | 结果 |
|---|---|
| PR #563 | `a85da9533a67ad569585966500aa68b87eca4f67`，CI 8/8 通过，修复首发成功页抢占和接管码交付文件同步 |
| PR #564 | `e5f301e030606d5fb8bcb22935511030820899d4`，CI 8/8 通过，将发布包契约纳入 CI，但真实重发仍复现 xattr 噪声 |
| PR #565 | `090e4155d90b74bc90259200483e8f4d7ecf6cbf`，CI 8/8 通过，加入 `--no-xattrs` 并收紧发布包契约 |
| #563/#564 有效备份 | `/zoesoft/medkernel/backups/p4-e5f301e-predeploy-20260612-160836`，隔离恢复 `117|117`、178 张表、知识 `0|0|0|0|0|0` |
| #563/#564 发布 | `/zoesoft/medkernel/backups/deploy-20260612-160953`，服务健康但 `tar_xattr_noise=YES` |
| #565 根因探针 | `COPYFILE_DISABLE=1` 默认包在 Linux GNU tar 上复现 `LIBARCHIVE.xattr.com.apple.provenance`；`COPYFILE_DISABLE=1 tar --no-xattrs -czf` 包在 Linux GNU tar 上 stderr 为空，257 个条目 |
| 失败留痕 | `/zoesoft/medkernel/backups/p4-e5f301e-predeploy-20260612-160749/evidence/predeploy-backup.properties`，备份目录权限导致 `pg_dump` 写入失败，`destructive_action_performed=false` |

根因：macOS 构建产物带 `com.apple.provenance` xattr；BSD tar 在未显式 `--no-xattrs` 时会写入 `LIBARCHIVE.xattr.*` PAX 扩展头。`COPYFILE_DISABLE=1` 只避免 AppleDouble 文件，不足以阻止 xattr PAX 头进入发布包。

## 12. 090e4155 干净重发与独立验收

| 检查项 | 结果 |
|---|---|
| 精确主线 | `origin/main=090e4155d90b74bc90259200483e8f4d7ecf6cbf` |
| 后端制品 SHA-256 | `6ec6f7845051e66215cbc7a6979e1e473fc18cb3f21ad01d68d8f829f5067982` |
| 前端上传包 SHA-256 | `0d3815060ca9d634ba97473bc60534e9bdf3cd6816f54989a7f191a0d6b5c7ce` |
| 预发布备份 | `/zoesoft/medkernel/backups/p4-090e4155-predeploy-20260612-162338` |
| 隔离恢复 | `restore_flyway=117|117`、`restore_public_base_tables=178`、`restore_knowledge_counts=0|0|0|0|0|0` |
| 程序发布自动备份 | `/zoesoft/medkernel/backups/deploy-20260612-162418` |
| 发布证据 | `/zoesoft/medkernel/backups/p4-090e4155-predeploy-20260612-162338/evidence/deploy-090e4155.properties` 与 `deploy-090e4155.log` |
| Post-deploy 证据 | `/zoesoft/medkernel/backups/p4-090e4155-predeploy-20260612-162338/evidence/post-deploy-090e4155.properties` |
| manifest | `source/commit=090e4155d90b74bc90259200483e8f4d7ecf6cbf` |
| 制品匹配 | `jar_matches_expected=YES`、`frontend_upload_matches_expected=YES` |
| 服务健康 | `medkernel|nginx|postgresql = active|active|active`，HTTP readiness 200，HTTPS readiness 200 |
| Flyway / 表数 | `117|117`，public 基表 178 |
| 首发身份 | `platform_admin_credential=1|platform-admin:N:ACTIVE:MFA_SET`，`system_superadmin_assignment=1` |
| 接管令牌 | `active_bootstrap_tokens=0`，`token_file_matches_env=YES`，`token_file_mode_owner=600|medkernel|medkernel`，`deploy_log_has_secret=NO` |
| 发布噪声 | `tar_xattr_noise=NO` |
| 文献资料库根地址 | 长度 0，元数据 `SYSTEM|PLATFORM_SEED|Y|Y|1`，仍未正式配置 |
| 知识数据 | `0|0|0|0|0|0` |

裁决：`090e4155` 是当前 P4 首轮演练的有效运行基线。正式知识生产继续阻断；下一步是从真实前台执行 14 角色 P4 全流程。
