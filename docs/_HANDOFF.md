# 会话接力

## 2026-06-12 P4 V117 精确部署、首发接管与演练缺陷修复

- 当前分支：`codex/p4-first-drill`，从已合并主线 `origin/main=1876aec72f459b4980f8aa769f4caf7ce5afc7b1` 续接。
- 当前本地改动：P4 首发 UI 演练暴露两项产品缺陷，已按红绿测试修复但尚未提交/PR/重发：
  1. `frontend/src/pages/Bootstrap.tsx`：首发管理员创建成功后，初始化状态刷新不再抢占本地“返回登录完成改密”提示。
  2. `deploy/onprem/medkernel-deploy.sh`：发布流程在备份后、重启前自动把 `MEDKERNEL_BOOTSTRAP_INIT_TOKEN` 同步到 `/zoesoft/medkernel/conf/bootstrap-init-token.txt`；新增 `--sync-bootstrap-token` 运维动作，不输出明文。
- 当前 134 已完成 V117 精确主线部署与真实 UI 首发接管。首发管理员凭据、MFA secret、恢复码仅保存在服务器受控凭据文件，未写入仓库和聊天记录。
- 正式知识生产仍未放行：文献资料库根地址仍未配置；正式根地址必须在系统配置页维护为受管 URI（COS/S3/OSS/OBS/MinIO/HTTPS 网关等），不得使用服务器 IP、存储厂商硬编码、`tmp`、本机目录或非加密 HTTP。
- 长目标持续绑定当前 Codex 会话；上下文过长时只在本会话压缩整理，不创建、切换或引导进入新线程。

## 当前现场

- 主机：`root@193.112.107.134`，主机名 `VM-0-13-opencloudos`，部署根目录 `/zoesoft/medkernel`。
- 运行版本：manifest `source/commit=1876aec72f459b4980f8aa769f4caf7ce5afc7b1`，部署时间 `2026-06-12T14:39:21+08:00`。
- 后端 jar SHA-256：`bc9ae052f0887eb659cd6990ed52ddbe1ba0e67b0bebd782b8abc5397c0d162f`。
- 前端制品 SHA-256：`241b5511b59ee0fede5829ff36b2a563bec29bfb8916c7e3dfaa340f0af9220d`。
- 服务：`medkernel` active，HTTPS readiness `200`，`/medkernel/api/v1/bootstrap/status` 返回 `initialized=true`。
- 数据库：PostgreSQL 15.18，Flyway `117|117`，public 基表 178 张。
- 首发身份：`platform-admin` 已创建，`system-superadmin` 有效分配 1 条，`must_change_pwd=N`，`mfa_secret=MFA_SET`。
- 令牌状态：旧令牌 `REVOKED`，当前接管令牌已 `USED:platform-admin`，无有效 ACTIVE 令牌。
- 知识数据：`knowledge_identity|knowledge_asset_version|knowledge_package|mk_knowledge_customization|mk_pkg_package_entitlement|mk_pkg_tenant_package_reference = 0|0|0|0|0|0`。

## 备份、证据与回退

- P3 首轮备份：`/zoesoft/medkernel/backups/p3-prep-20260612-124124`，隔离恢复通过。
- P3 发布前备份：`/zoesoft/medkernel/backups/p3-pre-release-20260612-133831`，隔离恢复通过。
- P4 清库前最终备份：`/zoesoft/medkernel/backups/p4-pre-clear-20260612-135752`，隔离恢复通过。
- P4 V116 历史发布检查点：`/zoesoft/medkernel/backups/p4-fresh-deploy-20260612-140144`；随后因 Oracle 空字符串语义发现 V117 必要性，未执行初始化。
- V117 发布前有效备份：`/zoesoft/medkernel/backups/p4-v117-predeploy-20260612-143821`，隔离恢复通过。
- V117 程序发布自动备份：`/zoesoft/medkernel/backups/deploy-20260612-143920`。
- V117 发布证据：`/zoesoft/medkernel/backups/p4-v117-release-20260612-143920/evidence/post-deploy.properties` 及 SHA-256。
- 令牌裁剪备份与证据：`/zoesoft/medkernel/backups/p4-v117-pre-token-prune-20260612-144112/evidence/token-prune.properties`，状态 `PASSED`，旧 ACTIVE 令牌撤销后 `active_tokens_after=1|1`。
- 令牌交付文件同步证据：`/zoesoft/medkernel/backups/p4-v117-pre-token-file-sync-20260612-144644/evidence/token-file-sync.properties`，状态 `PASSED`，根因记录为环境令牌轮换后交付文件未同步。
- UI 首发接管证据：`/zoesoft/medkernel/backups/p4-v117-release-20260612-143920/evidence/ui-bootstrap.properties`，状态 `PASSED`；截图归档 `medkernel-p4-ui-evidence-20260612-1534.tar.gz`，敏感值已遮盖。
- 失败留痕仍保留：`p4-v117-predeploy-20260612-143741` 为备份路径权限导致隔离恢复失败，未执行服务/库/配置变更；`p4-v117-pre-token-file-sync-20260612-144644` 的前一次同步校验脚本因把原始文件 SHA 与应用 trim 后摘要混比而失败，交付文件已在后续证据中证明同步。

## 已验证

- PR #562 已 squash 合并，主线包含 V117 五方言空值语义修复；CI 8/8 绿。
- 本地 V117 验证已通过：H2、PostgreSQL、Oracle 真实迁移/重复迁移/空配置回滚；后端聚焦测试、前端 SecurityBaseline、guard、生产构建均通过。
- 134 V117 post-deploy 独立验收通过：Flyway `117|117`、可空列、文献根地址为空、知识数据 0、服务健康 200。
- UI 首发接管真实前台通过：部署接管码、创建首发管理员、登录、首次改密、MFA secret 生成、TOTP 校验、恢复码保存、进入工作台；浏览器 console errors 0、failed requests 0。
- 本轮本地缺陷修复验证：
  - `npm run lint`：通过。
  - `npm run typecheck`：通过。
  - `npm test -- Bootstrap.test.tsx Login.test.tsx`：31/31 通过。
  - `bash -n deploy/onprem/medkernel-deploy.sh deploy/onprem/tests/validate-medkernel-deploy.sh`：通过。
  - `bash deploy/onprem/tests/validate-medkernel-deploy.sh`：通过，且日志不包含接管码明文。
  - `node --test scripts/authenticity-guard.test.mjs scripts/migration-convention-guard.test.mjs scripts/config-boundary-guard.test.mjs`：38/38 通过。
  - `authenticity-guard --mode=changed`、`migration-convention-guard --mode=changed`、`config-boundary-guard --mode=changed`：通过。
  - `git diff --check`：通过。
  - `.github/workflows/ci.yml` 的 `guard-rules` 已纳入 `deploy/onprem/tests/validate-medkernel-deploy.sh`。

## 风险与边界

- 本地两项演练缺陷修复尚未提交、PR、CI、合并，也尚未重发到 134；进入 14 角色 P4 全流程前，应先完成这批小修复的 PR 与精确重发。
- 当前 134 已完成首发身份初始化；正式生产前仍按全新标准处理。P4 问题关闭后，P5 需重新备份、清库并完整重演，不复用 P4 业务结果冒充通过。
- 旧库 V6/V25/V43 校验和差异为历史迁移原地修改造成；项目未上线且用户要求全新处理，因此不执行 Flyway repair、不为旧演练库新增兼容迁移。
- 当前未配置正式文献资料库根地址，未生成正式知识，未接入 wave2 模型网关。不得跳到 P6。

## 下一步

1. 对当前本地缺陷修复补齐必要验证、提交、推送、PR、CI、合并。
2. 从合并后的精确 `origin/main` 重建后端/前端制品；对 134 再做即时备份和隔离恢复后受控重发。
3. 重发后复核：manifest、jar SHA、HTTPS health、bootstrap initialized、`platform-admin` MFA、知识数据 0、令牌交付文件与环境令牌同步能力。
4. 从真实前台按 14 角色执行 P4 首轮全流程；API 只用于模拟外部系统或铺设无关前置。
5. 发现不合理功能时登记、复现、定根因、写失败测试并重构，不为旧演练数据或旧包保留兼容负担。
6. P4 完整问题清单关闭后，重新备份并清库，进入 P5 第二轮完整重演；不得复用首轮业务结果冒充通过。
7. P5 与第一阶段正式验收通过、结构冻结后，才可在系统配置页维护正式文献资料库受管 URI 并进入 P6。
