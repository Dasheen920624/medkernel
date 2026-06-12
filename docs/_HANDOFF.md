# 会话接力

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
