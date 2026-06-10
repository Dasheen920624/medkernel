# MedKernel · 运维手册

> 状态：持续有效 · 当前底座与统一知识运行期已纳入
> 适用：v1.0 GA · 院方信息科 / 乙方 SRE / 国产化驻场运维

---

## 1. 文档定位

本手册面向**日常运维与值守人员**，覆盖系统上线后的可用性、安全性、可追溯性维护。

运维范围：

- 服务健康度与告警值守（Prometheus / Actuator / Grafana）
- 备份恢复演练（联动 [runbooks/backup-restore.md](runbooks/backup-restore.md)）
- 升级与回滚（联动 [runbooks/upgrade-rollback.md](runbooks/upgrade-rollback.md)）
- 性能压测与基线维护（联动 [performance/README.md](performance/README.md)）
- Provider/模型健康度与降级值守
- 审计链验签与证据导出
- 国产化 profile（govcloud）巡检

---

## 2. 维护原则

- 只登记真实健康、迁移、同步、审计和降级证据；外部资源不可达时不得显示成功。
- 统一知识包、规则、路径、术语和字段目录共用同一版本、继承、发布与回滚主链路。
- 本手册只保留可执行运维口径；产品方案引用 [详细规范](../MEDKERNEL_BUSINESS_SCENARIO_DETAIL_SPEC.md)，不另起平行文档。

---

## 3. 当前已具备的运维入口

- 健康检查：`/actuator/health`
- 指标：`/actuator/prometheus`
- 备份脚本：`deploy/docker/scripts/backup.sh` + `restore.sh`（SHA-256 摘要校验）
- 国产化配置：`application-govcloud.yml`
- 首次部署接管：`MEDKERNEL_BOOTSTRAP_INIT_TOKEN` + `/api/v1/bootstrap/*`
- 应急命令：`deploy/docker/scripts/bootstrap-emergency.sh`

---

## 4. 首次部署接管流程

当前运行保障范围为 **PostgreSQL + Oracle**。达梦 / 人大金仓与国产 OS / JDK 的真实运行证据登记在 [待处理问题清单](../audit/deferred-issues.md) 的 `DEFER-001`，由 D6/GA 最终适配阶段关闭；当前阶段不得宣称国产化真实环境已通过。

上线前由值班运维在安全终端生成一次性 init token，长度建议不少于 32 位，并只通过部署密钥系统注入：

```bash
export MEDKERNEL_BOOTSTRAP_INIT_TOKEN='<一次性强随机 token>'
export MEDKERNEL_BOOTSTRAP_INIT_TOKEN_TTL_MINUTES=60
export MEDKERNEL_AUTH_JWT_SECRET='<生产 JWT 强随机密钥，至少 32 位>'
```

启动后按顺序完成：

1. 打开 `/bootstrap`，输入 init token，确认 token 未过期。
2. 设置首发平台管理员账号和密码；系统只消费一次 token，账号授予 `platform-admin`，并强制 `must_change_pwd=Y`。
3. 使用首发账号登录，立即完成首次改密。
4. 绑定 MFA；恢复码只展示一次，数据库仅保存 SHA-256 摘要。
5. 绑定 MFA 后开通首个租户；未绑定 MFA 时，高危配置变更与租户开通会返回 `ENG-AUTH-010`。
6. 销毁部署环境中的 `MEDKERNEL_BOOTSTRAP_INIT_TOKEN`，保留审计记录，不保留明文 token。

---

## 5. 首发身份应急命令

应急命令只允许在本机控制台执行，必须同时提供本机确认、二次确认、操作者和原因。缺任一项会拒绝执行，且不得把拒绝写成成功。

> **救命通道启动语义**：`bootstrap-emergency.sh` 以**非 Web 模式**（`--spring.main.web-application-type=none`，应用入口检测到 `--bootstrap-emergency` 时也会强制兜底）旁路启动，**不绑定业务端口**，因此生产实例仍在运行时也可执行，无需占用空闲端口变通。命令执行并提交后进程**一发即走、自动退出**，运维无需手动 kill。

MFA 重置：

```bash
deploy/docker/scripts/bootstrap-emergency.sh \
  --bootstrap-emergency=mfa-reset \
  --tenant-id=t-1 \
  --user-id=platform-owner \
  --actor='<值班人员工号>' \
  --reason='<应急原因>' \
  --local-confirm=MEDKERNEL_LOCAL_CONSOLE \
  --confirm=RESET_MFA:platform-owner
```

账号解锁：

```bash
deploy/docker/scripts/bootstrap-emergency.sh \
  --bootstrap-emergency=unlock \
  --tenant-id=t-1 \
  --user-id=platform-owner \
  --actor='<值班人员工号>' \
  --reason='<应急原因>' \
  --local-confirm=MEDKERNEL_LOCAL_CONSOLE \
  --confirm=UNLOCK:platform-owner
```

MFA 重置成功时只输出一次性恢复码；恢复码不得截图进工单，不得写入日志，不得提交到仓库。工单仅登记操作者、原因、时间、目标用户和审计 trace。

---

## 6. 数据库运维注意（Oracle 标识符大小写）

业务表由 Flyway 迁移脚本以**不加双引号**方式创建，Oracle 按默认规则折叠为**大写**（如 `PLATFORM_CREDENTIAL`、`ORG_UNIT`）；运行时 Spring Data JDBC 已通过 `JdbcIdentifierPolicyConfig`（`forceQuote=false`）对齐，查询正常。

但 Flyway 版本历史表 `flyway_schema_history` 由 **Flyway 自身**创建并**强制加双引号**，在 Oracle 中以**小写、区分大小写**存储，与业务表（大写）不同。这是 Flyway 跨库的固定行为，**无害**（Flyway 读写口径一致，不影响迁移）。

**DBA 手动排查注意**：Oracle 下查询该表必须加双引号 + 小写，否则报 `ORA-00942`：

```sql
-- ✅ 正确（加双引号 + 小写）
SELECT * FROM "flyway_schema_history" ORDER BY installed_rank;

-- ❌ 错误：未加引号会被当成大写 FLYWAY_SCHEMA_HISTORY，ORA-00942 表不存在
SELECT * FROM flyway_schema_history;
```

PostgreSQL / Kingbase / 达梦 / H2 不受影响（不区分大小写或同为小写），仅 Oracle 需注意此差异。

---

## 7. 统一知识运行巡检

知识包有效解析按一次组织闭包、一次租户覆盖批读、一次租户版本批读和一次平台版本批读完成，资产数量增加不得产生逐项查询。数据库迁移必须到 V113，五方言均应存在 `idx_av_batch_identity` 与 `idx_io_batch_scope`。

| 看板信号 | 真实来源 | 异常判定 | 处理动作 |
| --- | --- | --- | --- |
| 有效包解析 | `GET /api/v1/engine/integration/knowledge-runtime/effective-package` | `NOT_FOUND`、`warnings` 非空、应生效资产进入 `excludedItems` | 核对平台包状态、租户授权、目标组织闭包、覆盖生效窗和资产版本 |
| 继承漂移 | 有效包条目的 `sourceTier`、`sourceOrgPath`、`contentHash` 与覆盖记录 | REPLACE/DISABLE/ADD 突增，或同机构哈希无发布原因变化 | 查看覆盖理由、差异摘要、评审证据；必要时退役覆盖或回滚版本 |
| 字典就绪度 | `/api/v1/engine/terminology/mappings/coverage` 与上下文 `mappingStatus` | `UNMAPPED`、`NO_STANDARD_TERM` | 补齐院内词条、确认映射并发布统一知识包；禁止人工改写为成功 |
| 包分发对账 | `/knowledge-runtime/packages/{packageId}/reconciliation` | `NOT_DISTRIBUTED`、`NOT_SYNCED`、`FAILED` 长时间未恢复 | 核对 SyncTarget、真实连接器、同步日志和重试/死信证据 |
| 服务与数据库 | `/actuator/health`、`/actuator/prometheus`、`flyway_schema_history` | 服务非 UP、数据库不可达、迁移版本低于 V113 | 先停止发布与覆盖变更，再按升级回滚手册恢复 |

看板不得用静态百分比或样例数据填充。当前没有专用解析时延指标时，只展示可核验的请求、审计和数据库事实，不伪造 P50/P99；性能验收以批量解析回归和实际压测证据为准。

## 8. 关联文档

- [产品宪法](../CONSTITUTION.md)
- [基础底座与引擎服务能力总览](../MEDKERNEL_FOUNDATION_AND_SERVICES.md)
- [备份恢复 Runbook](runbooks/backup-restore.md)
- [升级回滚 Runbook](runbooks/upgrade-rollback.md)
- [性能压测目录](performance/README.md)
