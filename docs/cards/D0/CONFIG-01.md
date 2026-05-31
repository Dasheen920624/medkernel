# CONFIG-01 · 配置中心引擎（配置外置）

> 读卡前置：[核心 CONSTITUTION](../../CONSTITUTION.md) + [D0 域简报](_brief.md)。
> 迁移来源（覆盖矩阵锚点）：核心 #19 配置外置 · 超管/配置前台化设计 §B2 · 既有 yml feature-flags 结构。

## 身份
- 卡 ID：CONFIG-01（backlog v8.1 D0 新增）
- 域：D0 登录域 / 平台脊柱
- 关联场景：S14 用户、权限与合规（系统配置治理）
- 依赖卡：[BASE-07](BASE-07.md)（Feature Flag 消费配置存储）· [BASE-04](BASE-04.md)（变更审计）· [BASE-02](BASE-02.md)（超管/合规可达）
- 工作量：5d
- owner / reviewer：待派单（owner ≠ reviewer）

## 目标

交付**配置中心引擎**：DB 配置存储（key/value + 元数据）+ 热生效 + 变更审计 + 可回滚 + 高危二次确认；启动只从 yml/env 读 DB 连接/端口/profile/迁移/启动密钥，其余 `medkernel.*` 从配置存储读——落实核心 #19 配置外置，把业务/运营配置从写死 yml 搬到可治理、可审计、有护栏的配置中心。

## 功能要求（原子可测条目）

- [x] **FR-1 DB 配置存储**：key/value + 元数据（display-name / risk / owner / description，沿用 yml 现有 feature-flags 结构）。
- [ ] **FR-2 热生效**：配置变更即时生效，无需重启（功能开关 / JWT TTL / Cookie 策略 / 备份 RPO-RTO / 日志级别 等）。已覆盖功能开关与备份 RPO/RTO；JWT TTL / Cookie 策略 / 日志级别仍待后续 PR。
- [x] **FR-3 变更审计 + 回滚**：每次配置变更留审计（[BASE-04](BASE-04.md)，who/before/after）+ 回滚点可恢复。
- [ ] **FR-4 启动引导边界**：启动只从 yml/env 读 **DB 连接 / 端口 / profile / 迁移位置 / 启动密钥**；其余 `medkernel.*` 从配置存储读（核心 #19）。
- [x] **FR-5 高危护栏**：审计持久化（audit-persistence）等关键项**不可从配置中心关闭/删除**（沿用 yml"生产必须开启"口径）；引擎已拦截，D5 页面置灰 + 说明由系统配置中心页面卡承载。
- [x] **FR-6 二次确认 + 兜底**：高危项变更走影响提示 + 二次确认；配置存储读失败回退**安全默认** + 诚实告警（不静默用错值）。

## 接口契约 / 页面契约
### 接口契约
- 端点：配置读取/变更/回滚端点 + 高危项护栏校验。
- DTO：配置项 Record（key/value/risk/metadata）+ 变更请求 Record + Bean Validation。
- 响应信封：`ApiResult` / `ProblemDetail`（高危拦截 → `CONFIG_PROTECTED`）。
- 状态机：N·A —— **配置非临床资产，不套 7 步流灰度**（设计 §6 YAGNI）；变更走审计 + 回滚点即可。
- 幂等 / 错误码 / traceId：配置变更按 `(key, version)` 幂等；`CONFIG_PROTECTED`/`CONFIG_HIGH_RISK_CONFIRM`；带审计 traceId。

### 页面契约
N·A —— 本卡是配置**引擎**；前台「系统配置中心」界面是 D5 页面卡（`D5-PAGE-系统配置中心`，挂"安全基线与系统配置"槽，核心 §2.2 不净增二级菜单）。

## 数据与迁移
- 表族：`mk_config_item`（key/value/risk/metadata/version）；`mk_config_history`（变更历史/回滚点）。旧计划中的 `sys_config` 命名已按 BASE-05 新增迁移规约收敛为 `mk_<域>_<实体>`，后续实现不得回退旧表名。
- 主键：字符串 ID；唯一约束：`uk_config_item_tenant_key (tenant_id, config_key)`；索引：`idx_config_item_tenant_key`、`idx_config_history_tenant_key`。
- 组织字段：平台级 + 租户级配置（继承覆盖，核心 §9）；审计字段齐全。
- 5 方言迁移：h2/postgres/oracle/dm/kingbase + 中文注释 + 从 yml 迁入的初始配置种子。

## 视角清单（11 视角逐条）
1. **产品架构**：★配置单一源（配置中心），yml 仅启动引导/兜底；消除写死 yml 漂移。
2. **产品体验**：N·A（引擎）—— D5 系统配置中心页呈现，高危项护栏 UI 置灰 + 说明。
3. **系统与数据架构**：热生效 + 回滚点 + 版本化；配置读失败安全默认兜底。
4. **临床医疗安全**：N·A —— 但临床相关阈值若配置化，高危项受护栏（不可误关）。
5. **知识与数据治理**：配置变更可审计可追溯（核心 §8）。
6. **安全合规与监管**：★本卡主战场 —— 配置外置 + 高危护栏（审计持久化/国密不可从 UI 关）+ 二次确认 + 回滚（核心 #19/§8）。
7. **集团化与多租户治理**：平台/租户配置继承覆盖（核心 §9）。
8. **集成与互操作**：Provider 等配置经配置中心（写入归此，状态展示在 D5 Provider 状态只读）。
9. **运维 / SRE / 国产化**：★功能开关/备份/国产化/日志级别前台可调（核心 §12）；配置存储不可用回退安全默认 + 告警。
10. **质量与真实性审计**：★禁配置写死 yml（铁律 #11/核心 #19，门禁可校验）；变更真实审计。
11. **AI / 模型治理与可降级**：模型/Provider 配置经配置中心（第二波 AI 配置仍属 wave2，本卡先立引擎）。

## 适用不变量
- 命中核心约束：**#19 配置外置（+铁律 #11）** · **§8 高危护栏/审计** · **§9 配置继承覆盖** · **§12 运维配置前台化** · **§11 读失败诚实降级**。
- 本卡落点：DB 配置存储 + 热生效 + 审计回滚 + 高危护栏 + 启动引导边界，把"除启动必需外配置不写死 yml"做成可治理、可审计、有护栏的事实。

## 验收 + 验证
- [ ] **AC-1（FR-1/2）**：从配置中心改功能开关/JWT TTL → 热生效（无需重启）。功能开关与备份 RPO/RTO 已验收，JWT TTL 待补齐后再勾选。
- [x] **AC-2（FR-3）**：配置变更留审计（who/before/after）+ 回滚到前值成功。
- [ ] **AC-3（FR-4）**：启动只读 yml 的 DB/端口/profile/迁移/密钥；写死业务配置 yml 被门禁/校验提示应迁配置中心。
- [x] **AC-4（FR-5）**：尝试从配置中心关审计持久化 → `ENG-AUDIT-001` / `CONFIG_PROTECTED` 类护栏拦截；D5 UI 置灰不可关由页面卡呈现。
- [x] **AC-5（FR-6）**：高危项变更需二次确认；配置存储读失败 → 回退安全默认 + 诚实告警（非静默错值）。
- 关联 A1–A9：A6 合规运维（配置治理 + 审计）。
- T-GATE：后端门禁全绿（配置不写死 yml / 读失败不静默伪造）。
- B0 验收：纯确定性配置引擎，天然 B0。

## 完工证据
- PR1 本地证据：`mk_config_item` / `mk_config_history` 五方言迁移；`SystemConfigControllerTest` 覆盖配置中心元数据、运行 Feature Flag 热生效、API 更新来源标记与历史记录；运行底座 `RuntimeOperationsService` 已从配置中心读取 Feature Flag，YML 仅作为启动种子与缺失兜底；审计持久化、国密增强高危运行开关禁止从配置中心关闭，拒绝动作写入审计。
- PR2 本地证据：新增配置回滚端点 `POST /api/v1/system/configs/{key}/rollback`；配置更新支持 `expectedVersion` 防覆盖与 `confirmedHighRisk` 二次确认；高危更新/回滚缺确认返回 `ENG-CONFIG-002`；普通配置回滚写 `mk_config_history.change_type = ROLLBACK` 并记录 `AuditAction.ROLLBACK`；审计持久化/国密禁关仍优先返回 `ENG-AUDIT-001` / `ENG-CONFIG-001`；运行 Feature Flag 与备份配置读取失败或布尔非法时回退安全默认并通过 `source/warning` 暴露，Provider 状态页展示告警。
- 代码 permalink：配置存储 / 热生效机制 / 变更审计 + 回滚 / 高危护栏 / 启动引导边界 / yml→DB 迁入种子。
- 测试：热生效测试 + 变更审计回滚测试 + 高危护栏拦截测试 + 启动边界测试 + 读失败安全默认测试。
- 审计员签字：@<reviewer>（owner ≠ reviewer）。

## 大卡工序（5d，后端）
- PR1：`mk_config_item` / `mk_config_history` 存储 + 元数据 + 运行 Feature Flag 热生效 + 启动引导边界 + 审计/国密高危关闭护栏 → 支撑 AC-1/3/5 的第一段。
- PR2：变更审计 + 回滚点 + 高危护栏 + 二次确认 + 读失败兜底 → AC-2/4/5。
- PR3（待）：JWT TTL / Cookie 策略 / 日志级别热读取 + 启动引导边界门禁 → 收口 AC-1 剩余项与 AC-3。
