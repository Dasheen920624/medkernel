# P3 演练前发布准备验收报告

> 日期：2026-06-12  
> 阶段：P3 演练前发布准备  
> 结论：通过。已完成 134 主机、部署目录、数据库、当前版本、回退路径、三次有效备份与隔离恢复、候选制品构建、旧库迁移校验和根因定位和空库预检；在 P4 清库前具备可恢复、可复核的发布条件。
> 后续状态：用户明确要求“正式知识生产前全部按全新处理、不保留历史包袱”，因此 P4 已在最终备份恢复通过后清理旧演练库并从 V1 全新迁移。现场结果见 [P4 首次全新部署验收报告](p4-first-fresh-deployment-acceptance.md)。

## 1. 输入与边界

- 权威输入：`docs/_HANDOFF.md`、P2 精简快照和全系统产品 IA 验收报告。
- 本地基线：`origin/main=28d900cd6cce0d729b5260a0762198142a74bcb7`；候选分支 `codex/p3-release-134`。
- 候选提交：`c19aee87` 明确全新演练与配置边界；`ec9901bf` 移除文献资料库厂商默认地址。
- 存储边界：配置键 `medkernel.knowledge.literature.material-root-uri` 初始必须为空，只能通过系统配置页维护正式受管 URI；兼容 COS/S3/OSS/OBS/MinIO/HTTPS 网关，不绑定当前服务器 IP 或厂商，不接受 `tmp`、本机路径、`file://` 或非加密 HTTP。
- P3 未生成知识、未配置正式资料库地址、未接入 wave2 模型网关。

## 2. 主机与旧现场核验

| 项 | 结果 |
|---|---|
| SSH / 主机 | `root@193.112.107.134` / `VM-0-13-opencloudos` |
| 部署根目录 | `/zoesoft/medkernel`，发布入口 `/usr/local/bin/medkernel-deploy` |
| 服务 | `medkernel`、`nginx`、`postgresql` 均 active，HTTPS readiness 200 |
| 旧 manifest | `source=codex-demo-drill-audit-trace-jump-74353b56`，`deployedAt=2026-06-11T18:36:05+08:00` |
| 旧 jar SHA-256 | `51fdd05aabaad6b51f4016eeec9a4bcb0f4ff7934f5cb641c4117f651c90679e` |
| 旧数据库 | PostgreSQL 15.18，Flyway V114，172 张 public 基表 |
| 旧程序回退 | `/zoesoft/medkernel/backups/deploy-20260611-193235` |

## 3. 备份与隔离恢复

| 备份 | 用途 | 恢复结果 |
|---|---|---|
| `p3-prep-20260612-124124` | 首轮现场核验前置备份 | V114、172 张表、配置表存在，临时库已删除 |
| `p3-pre-release-20260612-133831` | 候选发布前备份 | dump `1697192` 字节，隔离恢复通过 |
| `p4-pre-clear-20260612-135752` | 清库前最终即时备份 | dump `1697196` 字节；恢复库 `medkernel_p4_pre_clear_20260612135752_restore` 为 V114、172 张表，状态 `PASSED`，清理计数 0 |

- 最终备份包含数据库、服务器配置、现网 jar、前端 dist、摘要和恢复命令。
- 最终证据：`/zoesoft/medkernel/backups/p4-pre-clear-20260612-135752/evidence/pre-clear.properties` 与 `SHA256SUMS`。
- `p4-pre-clear-20260612-135641` 的首次尝试因目录权限使 `postgres` 无法遍历读取 dump；证据记录 `destructive_action_performed=false`，随后修正目录可读链路并重新完成有效备份，没有把失败目录冒充通过。

## 4. 迁移校验和根因

旧 V114 数据库对当前候选执行 Flyway validate 时发现：

| 迁移 | 旧库 checksum | 当前候选 checksum |
|---|---:|---:|
| V6 | `1297702750` | `-1229984444` |
| V25 | `1891431755` | `427098790` |
| V43 | `-1501011817` | `1370426850` |

对旧部署 jar、数据库和 Git 历史交叉核验后，确认 V6/V25/V43 曾被后续提交原地修改。由于项目尚未上线且用户要求正式知识生产前全部作为全新环境处理，本阶段裁决为：

- 不执行 `flyway repair` 掩盖历史差异。
- 不新增 V117 兼容旧演练数据。
- 不回填旧角色、菜单、租户或知识数据。
- 以最终备份保留可审计回退能力，P4 从空 PostgreSQL 库执行当前 V1-V116。

## 5. 候选制品与空库预检

| 制品 | 大小 | SHA-256 |
|---|---:|---|
| `medkernel-backend-ec9901bf.jar` | `75191240` | `8363ffa4f01efdd5465d8ce847a7eb57c6bde2577d3ca4277028a3b381835d0c` |
| `medkernel-frontend-ec9901bf-dist.tar.gz` | `3567397` | `00eeea5e623bb26f4303e787e20ffc89112f812ed284a69f43b9bc262104066b` |

- 后端 `mvn -f medkernel-backend/pom.xml -DskipTests clean package` 通过。
- 前端 `npm --prefix frontend run build` 通过，Vite 处理 3408 个模块。
- 远端预检证据目录：`/zoesoft/medkernel/backups/p3-fresh-preflight-ec9901bf-20260612-135854`。
- 临时 PostgreSQL 空库运行真实候选进程，迁移 116 条、最新 V116、178 张表、15 个有效 SYSTEM 角色、2 条平台职责分配、30 个菜单权限，health 200。
- 文献资料库配置为 `SYSTEM|medkernel.knowledge.literature.material-root-uri|空值|PLATFORM_SEED|受保护|版本1`。
- 临时数据库和 18089 端口进程已清理。
- 一次预检脚本曾按字符串求 `max(version)` 得到 99 并超时；数据库实际已完成 116 条迁移且服务健康。后续改为数字版本判断并通过，失败证据保留。

## 6. P3 放行结论

P3 已满足“先核验、先备份、先证明可恢复、再允许 P4 清库”的串行门禁。P3 不代表 P4 全角色真实前台演练完成，也不放行正式知识生产。后续所有不合理功能必须在 P4/P5 中按全新产品标准修正，不为旧演练环境保留兼容负担。
