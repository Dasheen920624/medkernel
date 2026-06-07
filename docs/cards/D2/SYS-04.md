# SYS-04 · 版本继承与发布框架

> 读卡前置：[核心 CONSTITUTION](../../CONSTITUTION.md) + [D2 域简报](_brief.md)。
> 迁移来源（覆盖矩阵锚点）：详规 §7.7 版本、继承与发布模型（L1427）· §7.6 核心表族·发布同步（L1411）· 落地规划 §12 知识包、配置包与院内同步（L792）· 核心 §3 变更类状态机 / §9 集团继承 / §4 7 步流。

## 身份
- 卡 ID：SYS-04（= backlog 任务 ID）
- 域：D2 试点准备
- 关联场景：横切（所有配置类资产的版本与发布底座；S13 包发布与院内同步的框架层）
- 依赖卡：[SYS-03](../D0/SYS-03.md)（关系库权威源与投影同步）· [BASE-01](../D0/BASE-01.md)（七层组织继承 / 包版本快照）· [BASE-04](../D0/BASE-04.md)（发布审计链）· [BASE-05](../D0/BASE-05.md)（5 方言迁移）
- 工作量：5d
- owner / reviewer：待派单（owner ≠ reviewer）

## 目标

提供所有配置类资产（知识 / 字典 / 规则 / 路径 / 包）共享的**版本继承与发布框架**：**版本不可变** + **七层组织继承覆盖** + **草稿 → 审核 → 静默观察 → 灰度 → 全量 → 下线/回滚** 发布流 + **历史重放**，使任何资产用**同一套**版本与发布机制，不各造一套。本卡是**通用框架**；权威知识专属的唯一约束与原子替换在 [SYS-08](SYS-08.md)（建于本框架之上）。

## 现状（搬迁时核查 2026-05-30，以 `medkernel-backend/src` 为准）

本卡＝**框架抽取/统一**，当前散落无统一框架：

- 版本字段散落多引擎：`engine/context`（`PackageVersionPort`）、`engine/knowledge`（`KnowledgeAssetVersion` + 状态机 + `activate/withdraw`）、`engine/pathway`（`SpecialtyPackage`）、`engine/pkg`（`ReleasePlan/PackageSyncPort/KnowledgePackage`）、`engine/evaluation`（版本字段）。
- `engine/pkg` 的 `ReleasePlan` / `PackageSyncPort` / `PackageEngineService` 属包级投放；外部连接统一复用 `integration_adapter` 与 `IntegrationConnector`，不维护包域同步目标。
- **缺口**：无统一的「不可变版本 + 七层继承解析 + 变更类发布流 + 灰度/回滚/历史重放」框架；各引擎各写版本与发布。本卡抽取为复用框架（Port/SPI），各引擎接入，**不推翻已有 `engine/knowledge` 的 `activate` 主干**（[SYS-08](SYS-08.md) 在其上专项化）。

## 功能要求（原子可测条目）

- [x] **FR-1 版本不可变**：已发布的知识/规则/路径/包**不可原地修改**，只能生成新版本（版本号单调 + 内容 `content_hash`）；已发布版本只读，旧内容不得删除。
- [x] **FR-2 唯一权威生效域**：以 `asset_identity + org_path + applicable_population/context + effective_time` 判定生效域，每域**最多一个** `ACTIVE` 版本（框架提供约束与解析；权威知识的并发激活拒绝/原子替换专项在 [SYS-08](SYS-08.md)）。
- [x] **FR-3 七层组织继承覆盖**：平台 → 集团 → 医院 → 院区 → 社区站点 → 科室 → 专病；提供继承解析（给定组织节点 + 资产身份 → 生效版本）；**局部覆盖必须说明差异/来源/原因/影响范围**（覆盖可解释）；**安全红线（高风险禁忌、法定规则）不可被下级静默关闭**。
- [x] **FR-4 发布流（变更类状态机 + 7 步流）**：草稿 → 审核 → 静默观察 → 灰度（默认 10% 床位）→ 全量 → 下线/回滚，复用核心 §3 变更类状态机 + §4 七步流（含"看影响"）；**仅院级管理员可"直接全量"**。
- [x] **FR-5 回滚**：回滚**不丢审计、不破坏历史推荐解释**；被撤回的高风险版本**不得一键回滚**，回滚目标须过当前安全校验与授权确认。
- [x] **FR-6 历史重放 + 运行解析隔离**：运行结果绑定**当时的患者快照 + 资产包版本**，支持事后复现；新事件**仅解析生效中权威版本**，历史旧版仅用于既往结果解释/审计/合规重放，**不混入新推荐**。
- [x] **FR-7 发布证据**：每次审核/灰度/全量/回滚生成证据（范围/影响分析/审核结论/激活事务/同步结果），可导出（[BASE-04](../D0/BASE-04.md) 审计链）。

## 接口契约 / 页面契约

### 接口契约（引擎/框架卡）
- 端点：本卡交付**框架契约（Port/SPI）**供各引擎复用，不新增客户面端点：`VersionedAssetPort`（不可变版本读写）· `InheritanceResolver`（七层继承解析）· `ReleasePort`（草稿→灰度→全量→回滚）· `ReplayPort`（历史重放）。REST 发布端点在 [API-10](API-10.md) 包发布 API 承接。
- DTO：`AssetVersion`（不可变 Record：`assetIdentity`/`versionNo`/`contentHash`/`status`/`effectiveScope`/`effectiveTime`）· `VersionReleasePlan`（范围/灰度水位/回滚目标/状态，通用表 `mk_version_release_plan`，不复用包域 `release_plan` 旧模型）· `InheritanceOverride`（覆盖差异说明）。
- 响应信封：`ApiResult` / `ProblemDetail`（[BASE-03](../D0/BASE-03.md)）。
- 状态机：★复用核心 §3 两套——**配置类**（草稿→待审核→已发布→生效中→已下线→已归档）+ **变更类**（待发布→灰度→全量→回滚）；**禁自创**。
- 幂等 / 错误码 / traceId：激活/回滚事务按 `(asset_identity, version_no, scope)` 幂等；下级关安全红线 → `INHERITANCE_SAFETY_DENIED`；高危回滚未过校验 → `ROLLBACK_SAFETY_DENIED`；全链路 traceId（[OBS-01](../D0/OBS-01.md)）。

### 页面契约（页面卡）
N·A —— 本卡无页面。发布流走 [BASE-06](../D0/BASE-06.md) 的 7 步流组件，呈现在各配置页与 **D2 配置包中心页**；本卡只立框架。

## 数据与迁移
- 表族（对齐详规 §7.6 发布同步族）：`mk_version_asset_version`、`mk_version_release_plan`、`mk_version_activation_transaction`、`mk_version_replay_binding`、`mk_version_inheritance_override`；包投递水位使用 `sync_log`，连接目录只使用集成域 `integration_adapter`。
- 业务版本 ID：`version_id` 使用 `av-<ULID>`；物理 `id` 仅作 JDBC 持久化主键。唯一约束：**`(asset_identity, org_path, applicable, effective)` 上 `ACTIVE` 唯一**（框架级保证 FR-2）；索引：`org_path`、`status`、`effective_time`、`asset_identity`。
- 组织字段：`tenant_id` + `org_path` + 审计字段；继承解析依赖 [BASE-01](../D0/BASE-01.md) 的 `org_closure`。
- 5 方言迁移：h2/postgres/oracle/dm/kingbase 一致 + 中文注释；`ACTIVE` 唯一约束按方言实现（部分唯一索引或发布事务校验）。

## 视角清单（11 视角逐条）
1. **产品架构**：★版本与发布的**单一框架**，知识/字典/规则/路径/包全部复用；消除"每个引擎各写一套版本与发布"的漂移。
2. **产品体验**：N·A —— 框架无页面；7 步流统一组件（[BASE-06](../D0/BASE-06.md)）+ 配置包中心页（D2）呈现，本卡只供机制。
3. **系统与数据架构**：★主战场 —— 不可变版本 + 继承解析 + 激活事务 + 历史重放；关系库权威（[SYS-03](../D0/SYS-03.md)），缓存/图/Dify 投影随发布失效或重建。
4. **临床医疗安全**：安全红线（高风险禁忌/法定规则）**不可被下级静默关闭**；旧版隔离——历史重放不混入新推荐；高危撤回版本**不可一键回滚**（核心 §6）。
5. **知识与数据治理**：唯一权威生效域（核心 §7）；**版本不可变 + content_hash**；**替代而非覆盖**（旧内容保留来源/审核/历史运行证据，禁删）。
6. **安全合规与监管**：发布审计链全留证（审核/激活/失效/回滚，核心 §8）；每次发布证据可导出供监管。
7. **集团化与多租户治理**：★七层继承覆盖 + 局部覆盖可解释 + 冲突仲裁 + 灰度默认 10% 床位（核心 §9）；**仅院级管理员可直接全量**。
8. **集成与互操作**：发布同步到院内/离线节点（[PKG-01](PKG-01.md)/[API-10](API-10.md)）；**同步失败不得破坏权威版本**（核心 §10）；无通道返回 `NOT_SYNCED`。
9. **运维 / SRE / 国产化**：5 方言；灰度/回滚机制；离线包版本水位与失败站点告警；内外网双形态（内网离线包导入、外网在线同步）。
10. **质量与真实性审计**：高危发布**必带影响分析 + 审核 + 回滚证据**，缺一即拒（核心 §13 通用拒绝项）；无伪造同步哈希/无假灰度（铁律 #1）。
11. **AI / 模型治理与可降级**：框架**确定性 B0**；AI 生成的候选资产复用**同一版本框架**——候选 `PENDING` 不自动生效，必经审核灰度（核心 §11 / 铁律 #4、#14）。

## 适用不变量
- 命中核心约束：**§7.7 版本不可变 / 唯一权威生效 / 替代非覆盖 / 历史重放 / 运行解析隔离** · **§9 七层继承覆盖 + 灰度** · **#5 变更类状态机** · **#4 7 步流** · **#19 配置外置 / 铁律 #11** · **§7 关系库权威**。
- 本卡落点：把"版本不可变 + 七层继承解析 + 变更类发布流 + 灰度/回滚/历史重放"抽成**一套被所有配置引擎复用的框架**，资产再多也只有一套版本与发布语义；[SYS-08](SYS-08.md) 在其上加"权威知识"专项约束。

## 验收 + 验证
- [x] **AC-1（FR-1/2）**：发布资产 v1（ACTIVE）后再改 → 拒绝原地改、强制生成 v2；同一生效域并发激活第二个 ACTIVE → 框架拒绝或事务内替换（仅留一个 ACTIVE）。
- [x] **AC-2（FR-3）**：集团发布 v1，医院本地覆盖 v1'（带差异说明）→ 该院解析到 v1'、其他院解析到 v1；下级试图关集团"高风险禁忌红线" → `INHERITANCE_SAFETY_DENIED`。
- [x] **AC-3（FR-4）**：走变更类状态机：草稿→审核→静默观察→灰度（10% 床位）→全量；非院级角色点"直接全量" → 拒绝，仅可走灰度。
- [x] **AC-4（FR-5）**：全量后回滚到 v1 → 审计与历史解释完整保留；对已 `WITHDRAWN` 的高危版本一键回滚 → `ROLLBACK_SAFETY_DENIED`。
- [x] **AC-5（FR-6）**：旧事件按当时绑定的 v1 + 患者快照重放结果一致；新事件只解析当前 ACTIVE v2，旧版不混入新推荐。
- 关联 A1–A9 剧本：A5 集团复用（继承下发/局部覆盖）、A4 发布回滚（灰度→全量→回滚证据）。
- T-GATE：前后端真实性门禁全绿（无伪造灰度/同步哈希；迁移 5 方言一致 + ACTIVE 唯一约束生效）。
- B0 验收：框架纯确定性、无模型依赖，**天然 B0**（关闭全部模型后版本与发布行为不变）。

## 完工证据
- 代码 permalink：`VersionedAssetPort`/`InheritanceResolver`/`ReleasePort`/`ReplayPort` + `AssetVersion`/`VersionReleasePlan`/`InheritanceOverride` + `mk_version_activation_transaction` + `mk_version_replay_binding` + `mk_version_asset_version` 迁移（×5 方言，ACTIVE 唯一约束）。
- 测试：版本不可变测试 + 七层继承解析测试 + 安全红线不可下级关测试 + 灰度/全量/回滚状态机测试 + 历史重放一致性测试 + ACTIVE 唯一约束并发测试。
- 审计员签字：@<reviewer>（owner ≠ reviewer）。
- PR 拆分证据口径：PR2 只交付 `InheritanceResolver`、替换式 `InheritanceOverride` 解释与 `INHERITANCE_SAFETY_DENIED`；普通 `DISABLE` 关闭继承必须等 PR3 发布流具备审核 / 证据链 / 解析消费后再开放，不允许先落一个运行时不会执行的关闭记录。

## 大卡工序（5d，后端框架；按 PR 拆分）
- PR1：`AssetVersion` 不可变版本 + `content_hash` + `mk_version_asset_version` 5 方言迁移 + ACTIVE 唯一约束 → AC-1。
- PR2：`InheritanceResolver` 七层继承解析 + 局部替换覆盖可解释 + 安全红线不可下级关；普通关闭继承在 PR2 诚实拒绝并留待 PR3 发布证据链 → AC-2。
- PR3：`ReleasePort` 变更类发布流（审核 / 静默观察 / 灰度默认 10% / 院级管理员全量 / 高危撤回回滚拒绝 / 激活与回滚幂等重试）+ `ReplayPort` 历史重放绑定 + 运行解析隔离 + 发布证据，新增 `mk_version_release_plan` / `mk_version_activation_transaction` / `mk_version_replay_binding` V56 五方言迁移，激活事务以 `(tenant_id, asset_type, asset_identity, to_version_id, action, active_scope_key)` 唯一约束兜底 → AC-3/4/5。本地证据：`VersionReleaseServiceTest`、`VersionReplayServiceTest`、`MigrationBaselineContractTest`、`H2BaselineMigrationTest`、`FlywayMultiDialectSmokeTest`、后端全量 `mvn -q test` 均通过；PostgreSQL 15.18 与 Oracle 21.3 迁移至 V56。
