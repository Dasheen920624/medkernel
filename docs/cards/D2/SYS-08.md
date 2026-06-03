# SYS-08 · 权威知识版本解析与原子替换框架

> 读卡前置：[核心 CONSTITUTION](../../CONSTITUTION.md) + [D2 域简报](_brief.md)。
> 迁移来源（覆盖矩阵锚点）：详规 §8.13 权威知识版本替换与旧版失效规范（L1856，含 8.13.1 唯一性/8.13.2 生命周期/8.13.3 旧版隔离/8.13.5 替换验收）· 核心 §7 唯一权威知识 / §6 旧版隔离 / #14。

## 身份
- 卡 ID：SYS-08（= backlog 任务 ID）
- 域：D2 试点准备
- 关联场景：横切（权威知识版本治理底座；S3 AI 知识工厂 / S13 包发布的替换机制层）
- 依赖卡：[SYS-04](SYS-04.md)（通用版本继承与发布框架，本卡建于其上）· [SYS-03](../D0/SYS-03.md)（关系库权威/投影失效）· [BASE-04](../D0/BASE-04.md)（替换审计链）· [SYS-01](../D0/SYS-01.md)（在径患者/路径识别依赖标准对象）
- 工作量：5d
- owner / reviewer：待派单（owner ≠ reviewer）

## 目标

提供**权威知识专属**的版本解析与原子替换框架：同一适用域**唯一有效约束** + **替代链** + **原子替换**（激活新版/失效旧版/刷投影/发同步同一事务）+ **紧急失效** + **影响病例任务**，保证"同一适用域同时只有一个权威版本参与临床、未审新版绝不执行、旧版仅历史重放"。本卡是**框架/机制**；知识域的新旧识别/去重/冲突/待审/旧版隔离**工作流**归 [KNOW-02](KNOW-02.md)（用本框架，不重造约束与事务）。

## 现状（搬迁时核查 2026-05-30，以 `medkernel-backend/src` 为准）

主干**已建**，本卡＝**框架化补全**（精准切分"已有 vs 待补"）：

- **已有**（`engine/knowledge/`）：`KnowledgeIdentity`、`KnowledgeAssetVersion`、版本状态机 `KnowledgeVersionStatus`（`DRAFT→CANDIDATE→UNDER_REVIEW→ACTIVE→SUPERSEDED/WITHDRAWN/REJECTED`，`isAuthoritative()=ACTIVE`）、`KnowledgeVersionService.activate(identity,version)`（**已是悲观锁 identity + 替代旧 ACTIVE→新 ACTIVE 的原子事务**）+ `withdraw()`、`KnowledgeSupersession(+Type: ACTIVATE/REPLACE/WITHDRAW/RESTORE/ROLLBACK)` 替代链、`KnowledgeLineage`、端点 `POST /identities/{id}/versions/{vid}/activate|withdraw`、`V3__knowledge_asset_baseline` 五方言。
- **唯一约束现状**：仅"同一 `identity_id` 同时刻 `ACTIVE` ≤1"且**仅 Service 层事务保证**（非 DB 约束、非完整适用域）。
- **2026-06-03 收口（#287/#288/#289）**：① 唯一约束已扩到完整适用域（`organization_scope + applicable_scope + active_scope_key`）并以 V57 五方言约束兜底；② 激活/撤回路径已刷新图谱与搜索投影，运行解析只取当前适用域 ACTIVE；③ 紧急失效证据已落 `mk_knowledge_invalidation`，影响处置任务已落 `mk_knowledge_affected_case_task`；④ 被撤回高危版本一键回滚返回 `ROLLBACK_SAFETY_DENIED`；⑤ lineage / FULL_TENANT 导出已包含失效记录和影响任务。当前 B0 不伪造患者 / 路径清单，真实病例 / 路径索引接入后才允许生成 `PATIENT_CASE` / `PATIENT_PATHWAY` 任务。

## 功能要求（原子可测条目）

- [x] **FR-1 唯一有效约束（完整适用域）**：同一适用域（`knowledge_identity + organization_scope + applicable_population/context + effective_time`）**最多一个** `ACTIVE` 权威版本，由 **DB 约束或发布事务校验**保证；允许 ≥0 个 `PENDING_REPLACEMENT_REVIEW` 新版候选共存**仅供审核比较**，绝不参与执行（核心 §7、详规 §8.13.1）。
- [x] **FR-2 替代链 lineage**：`knowledge_supersession` 记录 `old→new` + 转换类型 + 时点；`GET /identities/{id}/lineage` 展示来源 → 候选 → 审核 → 发布 → 替代 → 撤回完整链（扩展现有 `KnowledgeLineage`）。
- [x] **FR-3 原子替换**：审核激活在**同一事务**内：激活新版 → 失效旧版（`SUPERSEDED`）→ 写替代链 → **刷新运行缓存/投影** → **生成同步任务**（在现有 `activate` 主干上补"刷投影 + 同步任务"，详规 §8.13.2 激活替换阶段）。
- [x] **FR-4 紧急失效**：已核验旧版重大安全风险（新增禁忌/召回/强制撤回）→ **不等普通审核周期**，按授权立即限制/撤回旧版 + 加急审核 + 启动影响处置；**未审新版仍不自动执行**；`knowledge_invalidation` 记录（详规 §8.13.3）。
- [x] **FR-5 影响病例任务**：替换/失效自动识别**在径/已触发患者、知识包依赖、近期推荐/用药风险、离线未同步站点** → 生成医师复核 / 补同步 / 风险告知任务（`affected_case_task`）；安全风险变化**不得等下次自然触发**。
- [x] **FR-6 运行解析隔离**：新诊疗请求**只命中**当前适用域 `ACTIVE`；缓存/搜索/图谱/Dify 投影随替换**失效或重建**，不向新决策返回旧版；历史请求按旧版重放并标"**历史版本，禁止新决策使用**"；未审新版只出现在审核比较与替换提醒。
- [x] **FR-7 回滚安全**：被撤回的高风险版本**不得一键回滚**，回滚目标须过当前安全校验与授权（与 [SYS-04](SYS-04.md) FR-5 一致、知识专项强化）。

## 接口契约 / 页面契约

### 接口契约（引擎/框架卡）
- 端点（当前知识身份 API，已有/扩展）：`POST /api/v1/engine/knowledge/identities/{id}/versions/{vid}/activate`（扩展为原子替换 + 刷投影 + 同步任务）· `.../withdraw`（扩展为紧急失效 + 影响派发）· `GET /api/v1/engine/knowledge/identities/{id}/lineage`（替代链）。**替换分析 API**（`.../replacement-analysis`）与 **AI 候选分类**（`/ai-candidates/classify`）属后续 E6 / 第二波（AI），本卡 B0 不含。
- DTO：复用 `KnowledgeAssetVersion` + `KnowledgeSupersession`；新增 `KnowledgeInvalidation`（紧急失效）· `AffectedCaseTask`（影响病例任务）· `ActivationTransaction`（激活事务记录，与 [SYS-04](SYS-04.md) 共用语义）。
- 响应信封：`ApiResult` / `ProblemDetail`（[BASE-03](../D0/BASE-03.md)）。
- 状态机：知识版本状态机（`DRAFT→CANDIDATE→UNDER_REVIEW→ACTIVE→SUPERSEDED/WITHDRAWN/REJECTED`，已有）映射核心 §3 配置类；紧急失效走核心 §3 变更类的"紧急激活"分支。
- 幂等 / 错误码 / traceId：激活按 `(identity, version)` 幂等；并发激活第二个 ACTIVE → `KNOWLEDGE_ACTIVE_CONFLICT`；高危回滚 → `ROLLBACK_SAFETY_DENIED`；全链路 traceId（[OBS-01](../D0/OBS-01.md)）。

### 页面契约（页面卡）
N·A —— 本卡无页面。"版本替换审核"左右对照（现行版/新版/来源差异/影响患者路径规则/紧急处置状态）在 **D4 AI 知识审核页**与知识资产页（[KNOW-01](KNOW-01.md)/[KNOW-02](KNOW-02.md)）呈现；本卡供机制与约束。

## 数据与迁移
- 表族（对齐详规 §7.6 / §8.13.4）：复用 `knowledge_identity`/`knowledge_asset_version`/`knowledge_supersession`（已有 V3）· 新增 `knowledge_invalidation`（紧急失效：原因/授权/时点/范围）· `affected_case_task`（受影响对象/任务类型/处置状态）· `activation_transaction`（激活/失效事务，与 [SYS-04](SYS-04.md) 共用）。
- 适用域字段补全：`knowledge_asset_version` 增 `organization_scope` + `applicable_population/context` + `effective_from/to`（支撑 FR-1 完整适用域）。
- 唯一约束：**`(knowledge_identity, organization_scope, applicable, effective)` 上 `ACTIVE` 唯一**（升级现有仅 identity-level 的服务层约束为完整适用域 + DB 护栏）。
- 5 方言迁移：h2/postgres/oracle/dm/kingbase 一致 + 中文注释；ACTIVE 唯一约束按方言实现（部分唯一索引或发布事务校验）。

## 视角清单（11 视角逐条）
1. **产品架构**：权威知识"同一适用域唯一有效"的**单一框架**；与 [KNOW-02](KNOW-02.md) 工作流划清——本卡管**约束/事务/解析/失效/影响**，KNOW-02 管**识别/去重/冲突/审核/隔离工作流**。
2. **产品体验**：N·A —— 本卡无页面；替换审核左右对照页在 D4/知识资产页呈现，本卡供机制。
3. **系统与数据架构**：★原子替换事务（激活/失效/替代链/刷投影/发同步同事务）+ 投影失效一致性 + 解析隔离；关系库权威（[SYS-03](../D0/SYS-03.md)）。
4. **临床医疗安全**：★主战场 —— 未审新版**不参与临床**；旧版仅历史重放且明显标识；高危**紧急失效 + 影响病例复核**不等自然触发；高危版本**不可一键回滚**；缓存/图/Dify 随替换失效，绝不向新决策返回旧版（核心 §6/#14）。
5. **知识与数据治理**：★唯一权威知识（核心 §7）；**替代而非覆盖**（旧版留存）；替代链/lineage 可追溯；为 [KNOW-02](KNOW-02.md) 的生成期去重提供**约束基座**（去重工作流在 KNOW-02）。
6. **安全合规与监管**：替换审计链全留证——来源核验/版本差异/审核/激活事务/失效旧版/影响处置/同步结果可导出（核心 §8 / 详规 §8.13.5）。
7. **集团化与多租户治理**：适用域含 `organization_scope`；本地合法覆盖 = **更精确适用域**且可解释；上下级冲突仲裁——**法定/高危安全规则不可被下级静默覆盖**（核心 §9）。
8. **集成与互操作**：替换后同步到院内/离线节点；**高风险替换必须告警 + 补同步 + 限制旧包继续服务**（核心 §10 / 详规 §8.13.3、§12.3）；无通道 `NOT_SYNCED`。
9. **运维 / SRE / 国产化**：图谱/搜索/Dify 投影随替换失效或重建；离线站点旧包**可检测/补同步/降级**；5 方言；内外网双形态。
10. **质量与真实性审计**：覆盖详规 §8.13.5 五项验收（唯一有效约束/生成期去重/待审共存边界/临床解析隔离/高风险紧急更新/投影离线一致/审计证据）；无伪造替换/无假失效（铁律 #1/#2）。
11. **AI / 模型治理与可降级**：**AI 只产候选不产事实**（核心 §7/#14）；候选 `PENDING_REPLACEMENT_REVIEW` 不执行；**B0 = 人工审核 + 确定性替换**；AI 自动新旧分类（`ai-candidates/classify`）整体后移第二波，关模型不影响替换框架。

## 适用不变量
- 命中核心约束：**§7 唯一权威知识** · **#14 新旧识别只审不执行 / 新版生效后旧版退出新临床** · **§6 旧版隔离（历史重放）** · **依赖 §7.7 / [SYS-04](SYS-04.md) 版本框架** · **§10 同步不破坏权威版本**。
- 本卡落点：在 [SYS-04](SYS-04.md) 通用版本框架上，为"权威知识"加**完整适用域唯一约束 + 原子替换（含刷投影/发同步）+ 紧急失效 + 影响病例任务 + 运行解析隔离**，使一条危险旧知识替换后**全链路（实时引擎/缓存/图/Dify/离线包）即时退出新临床决策**。

## 验收 + 验证
- [x] **AC-1（FR-1）**：构造同一适用域两版本并发激活 → DB/服务**拒绝第二个有效版本**或事务内完成原子替换，最终仅一个 `ACTIVE`（`KNOWLEDGE_ACTIVE_CONFLICT`）。
- [x] **AC-2（FR-3/6）**：审核激活新版 → 同事务失效旧版 + 写替代链 + 刷投影 + 发同步；替换后新诊疗请求**只命中新版**，历史请求按旧版重放且标"历史版本"。
- [x] **AC-3（FR-4/5）**：模拟说明书禁忌/安全警示变化 → 系统**撤回旧版 + 触发受影响患者/路径复核 + 同步告警**，未审新版仍不自动执行。
- [x] **AC-4（FR-6 投影一致）**：图谱/搜索/Dify/离线站点出现旧版本 → 可检测、补同步或降级，**不向新决策返回旧版**。
- [x] **AC-5（FR-7 + 去重基座）**：被撤回高危版本一键回滚 → `ROLLBACK_SAFETY_DENIED`；重复导入同一来源版本 → 不新增普通审核待办、不产生可发布候选（去重工作流在 [KNOW-02](KNOW-02.md)，约束在本卡）。
- 关联 A1–A9 剧本：A4 权威替换（替换/紧急失效/影响处置）、A6 合规运维（替换审计证据导出）。
- T-GATE：前后端真实性门禁全绿（无伪造替换/失效；迁移 5 方言一致 + ACTIVE 唯一约束生效）。
- B0 验收：人工审核 + 确定性替换，**天然 B0**；AI 自动识别/分类后移第二波，关模型替换框架行为不变。

## 完工证据
- 代码 permalink：`KnowledgeVersionService.activate`（扩展：刷投影 + 同步任务）+ `KnowledgeInvalidation` + `AffectedCaseTask` + `knowledge_supersession`/`KnowledgeLineage` + `knowledge_invalidation`/`affected_case_task` 迁移（×5 方言）+ 适用域字段 + ACTIVE 唯一约束。
- 测试：唯一有效约束并发测试 + 原子替换（激活/失效/投影/同步）测试 + 紧急失效 + 影响病例派发测试 + 运行解析隔离测试 + 投影/离线一致测试 + 替换审计导出测试（覆盖 §8.13.5 全项）。
- PR1 证据（#287，merge `deb31977`）：V57 五方言补 `organization_scope`、`applicable_scope`、`active_scope_key`，激活事务按完整适用域替换 ACTIVE，不影响其他适用域；本地聚焦 / 后端全量、PostgreSQL 15.18 / Oracle 21.3 迁移至 V57、T-GATE 与远端 CI 8/8 通过。
- PR2 证据（#288，merge `b15ef4fb`）：`KnowledgeProjectionRefreshPort` 汇总图谱 / 搜索投影刷新证据，激活路径同步刷新投影，运行解析只取当前适用域 ACTIVE；旧投影残留可检测为 `extra`，本地聚焦 / 后端全量、T-GATE 与远端 CI 8/8 通过。
- PR3 证据（#289，merge `2d72aaa`）：V58 五方言新增 `mk_knowledge_invalidation` / `mk_knowledge_affected_case_task`，撤回写失效记录、派发医师复核 / 包补同步 / 同步告警任务，LINEAGE / FULL_TENANT 导出包含失效与影响任务，高危撤回版本回滚返回 `ROLLBACK_SAFETY_DENIED`。本地 `mvn -q test` 1008 tests / 0 failures / 0 errors / 0 skipped，H2 / PostgreSQL 15.18 / Oracle 21.3 均迁移至 v58 并二次 migrate 无新迁移；脚本门禁、中文注释、diff 检查与远端 CI 8/8 通过。
- 审计员签字：@<reviewer>（owner ≠ reviewer）。

## 大卡工序（5d，后端框架；按 PR 拆分）
- PR1：适用域字段补全 + 完整适用域 ACTIVE 唯一约束（DB + 发布事务）+ 5 方言迁移 → AC-1。
- PR2：原子替换扩展（刷投影 + 发同步）+ 替代链 lineage + 运行解析隔离（投影失效/旧版重放标识）→ AC-2/4。
- PR3：紧急失效 `knowledge_invalidation` + 影响病例任务 `affected_case_task` + 高危回滚护栏 + 审计导出 → AC-3/5。
