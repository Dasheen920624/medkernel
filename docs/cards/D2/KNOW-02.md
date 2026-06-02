# KNOW-02 · 知识版本引擎（新旧识别与审核去重工作流）

> 读卡前置：[核心 CONSTITUTION](../../CONSTITUTION.md) + [D2 域简报](_brief.md)。
> 迁移来源（覆盖矩阵锚点）：详规 §8.2.3 生成期新旧识别与审核分流（L1627）· §8.13 权威知识版本替换与旧版失效（L1856，约束/事务归 [SYS-08](SYS-08.md)）· 落地规划 §6.6 权威知识替换与审核去重（L375）· 核心 §7 / #14 新旧识别只审不执行。

## 身份
- 卡 ID：KNOW-02（= backlog 任务 ID）
- 域：D2 试点准备
- 关联场景：S3 AI 知识工厂（审核去重工作流；B0 人工审核侧）
- 依赖卡：[SYS-08](SYS-08.md)（权威版本唯一约束 + 原子替换 + 紧急失效——本卡**用其框架**，不重造）· [KNOW-01](KNOW-01.md)（来源/资产/content_hash）· [SYS-04](SYS-04.md)（版本框架）· [BASE-04](../D0/BASE-04.md)（审核审计）
- 工作量：5d
- owner / reviewer：待派单（owner ≠ reviewer）

## 目标
提供知识资产的**新旧识别 → 去重 → 冲突归并 → 待审分流 → 旧版隔离**的**审核工作流**：来源更新/AI 候选/院内补充进入时，自动判定"新建 / 同主题新版 / 重复 / 冲突"，分流到人工审核，审核通过经 [SYS-08](SYS-08.md) 原子替换。**本卡管工作流；唯一有效约束、替换事务、紧急失效、影响病例任务在 [SYS-08](SYS-08.md)**（单一归属划清，不重造约束与事务）。

## 现状（搬迁时核查 2026-05-30，以 `medkernel-backend/src` 为准）
版本/替换**底座已建**（见 [SYS-08](SYS-08.md) 现状），本卡＝**补"生成期新旧识别 + 去重 + 待审分流"工作流**：
- 已有：`KnowledgeVersionStatus`（`DRAFT→CANDIDATE→UNDER_REVIEW→ACTIVE→SUPERSEDED/WITHDRAWN/REJECTED`）、`KnowledgeVersionService.activate/withdraw`（悲观锁 + 原子替换）、`KnowledgeSupersession`(+`SupersessionType`)、`KnowledgeLineage`、`KnowledgeIdentityService`。
- 缺口（本卡补）：① **新旧识别**（来源/内容指纹 + 适用域 → 新建/新版/重复/冲突判定）；② **去重**（同 content_hash 或等价主题不产生重复待办）；③ **冲突归并**（多来源同主题差异 → 合并审核视图）；④ **待审分流**（候选 `PENDING_REPLACEMENT_REVIEW` 共存仅供比较、绝不执行）；⑤ 审核通过**调用 [SYS-08](SYS-08.md) 原子替换**。

## 功能要求（原子可测条目）
- [x] **FR-1 新旧识别**：新候选进入 → 按 `(content_hash, knowledge_identity, 适用域)` 判定 **新建 / 同身份新版 / 重复 / 冲突** 四类，记判定依据。
- [x] **FR-2 去重**：同 content_hash 或判定为重复者**不新增审核待办、不产生可发布候选**（去重约束基座在 [KNOW-01](KNOW-01.md)/[SYS-08](SYS-08.md)，本卡管工作流分流）。
- [x] **FR-3 冲突归并**：多来源同主题差异 → 生成**对照审核视图**（现行版/候选差异/来源/分级），不自动合并。
- [x] **FR-4 待审分流**：候选置 `PENDING_REPLACEMENT_REVIEW` 与现行 `ACTIVE` 共存**仅供比较**，**绝不参与临床执行**（核心 #14）；分派审核人（医务处/专家）。
- [x] **FR-5 审核 → 替换**：审核通过 → **调用 [SYS-08](SYS-08.md) 原子替换主链路**（激活新版 / 失效旧版 / 刷知识图与搜索投影）；拒绝 → `REJECTED` 留档；**本卡不自行写替换事务**，紧急失效、发布同步与影响病例任务仍归 [SYS-08](SYS-08.md)。
- [x] **FR-6 旧版隔离**：被替换旧版仅历史重放、标"历史版本"，不混入新审核候选池。

## 接口契约 / 页面契约
### 接口契约（引擎/API 卡）
- 端点：识别/去重/分流为**引擎工作流**；客户面经 [API-03](API-03.md)：`GET /identities/{id}/candidates`（待审候选 + 判定）· `POST /candidates/{id}/review`（通过→调 SYS-08 替换 / 拒绝）· `GET /candidates/{id}/diff`（对照视图）。
- DTO：复用 `KnowledgeSupersession`/`KnowledgeLineage`；新增 `CandidateClassification`（新建/新版/重复/冲突 + 依据）· `ReviewDecision`（通过/拒绝 + 理由）。
- 响应信封：`ApiResult` / `ProblemDetail`（[BASE-03](../D0/BASE-03.md)）。
- 状态机：知识版本状态机（已有）；候选 `PENDING_REPLACEMENT_REVIEW` 分支；替换走 [SYS-08](SYS-08.md)。
- 幂等 / 错误码 / traceId：审核通过按 `(identity, candidate_version)` 幂等转交 SYS-08；重复候选 → 静默去重不报错；全链路 traceId（[OBS-01](../D0/OBS-01.md)）。
### 页面契约（页面卡）
N·A —— 本卡无页面。"版本替换审核"左右对照页在 **D4 AI 知识审核页**呈现；本卡供工作流与判定。

## 数据与迁移
- 表族：复用 `knowledge_asset_version`/`knowledge_supersession`/`knowledge_lineage`（[KNOW-01](KNOW-01.md)/[SYS-08](SYS-08.md)）；新增 `mk_knowledge_candidate_classification`（候选 → 类别 + 判定依据 + 时点）· `mk_knowledge_review_assignment`（审核分派/结论）。
- 主键沿知识域既有迁移模式使用 `BIGINT` identity；索引：`knowledge_identity`、`classification`、`review_status`、候选版本。
- 组织字段：`tenant_id` + `org_path` 作用域快照 + 审计（[BASE-04](../D0/BASE-04.md)）。
- 5 方言迁移：h2/postgres/oracle/dm/kingbase 一致 + 中文注释。

## 视角清单（11 视角逐条）
1. **产品架构**：知识"进料→审核→发布"工作流；与 [SYS-08](SYS-08.md) 划清——本卡管识别/去重/冲突/审核/隔离**工作流**，SYS-08 管约束/事务/失效/影响。
2. **产品体验**：N·A —— 审核对照页在 D4 呈现。
3. **系统与数据架构**：候选池与 ACTIVE 严格隔离；去重避免重复待办；判定可解释。
4. **临床医疗安全**：★候选**绝不参与临床**（核心 #14）；高危冲突必经人工对照审核；旧版仅历史重放。
5. **知识与数据治理**：★主战场 —— 新旧识别/去重/冲突归并/待审分流；替代非覆盖。
6. **安全合规与监管**：审核分派/结论/去重判定全留审计（[BASE-04](../D0/BASE-04.md)）。
7. **集团化与多租户治理**：审核权按五维 RBAC（[INFRA-05](../D0/INFRA-05.md)）；集团/院内审核范围隔离。
8. **集成与互操作**：外部/适配器来的候选统一进本工作流，不绕审核直发。
9. **运维 / SRE / 国产化**：待审候选量可监控；离线站点候选补审。
10. **质量与真实性审计**：去重真实（按 content_hash），无伪造"已去重"；候选未审不得显示为生效（铁律 #1/#2）。
11. **AI / 模型治理与可降级**：★**AI 只产候选不产事实**（核心 #14）；AI 自动新旧分类后移第二波，**B0＝人工识别 + 人工审核**，关模型工作流仍可人工跑通。

## 适用不变量
- 命中核心约束：**§7 唯一权威知识** · **#14 新旧识别只审不执行** · **§6 旧版隔离** · **用 [SYS-08](SYS-08.md) 约束/事务（不重造）**。
- 本卡落点：把"候选进来怎么识别/去重/分流/审核/隔离"做成工作流，发布动作一律委托 [SYS-08](SYS-08.md) 原子替换。

## 验收 + 验证
- [x] **AC-1（FR-1/2）**：导入同一来源两次 → 第二次判定"重复"、不产生新待办；导入同主题更新 → 判定"同身份新版"、入待审。
- [x] **AC-2（FR-3/4）**：两来源同主题冲突 → 生成对照视图、置 `PENDING_REPLACEMENT_REVIEW`，临床请求**不命中候选**只命中现行 ACTIVE。
- [x] **AC-3（FR-5）**：审核通过 → 调用 [SYS-08](SYS-08.md) 既有原子替换主链路（旧版 SUPERSEDED、知识图 / 搜索投影刷新）；拒绝 → REJECTED 留档。完整影响病例任务不在本卡冒领，仍归 [SYS-08](SYS-08.md)。
- [x] **AC-4（FR-6）**：被替换旧版不再进候选池、仅历史重放标"历史版本"。
- 关联 A1–A9 剧本：A4 权威替换（识别→审核→替换闭环）。
- T-GATE：前后端真实性门禁全绿（去重/分类真实、候选不参与执行）。
- B0 验收：人工识别 + 审核，**天然 B0**；AI 自动分类关闭不影响工作流。

## 完工证据
- 代码 permalink：`CandidateClassification` + `ReviewDecision` + 审核 → [SYS-08](SYS-08.md) 替换调用 + `mk_knowledge_candidate_classification`/`mk_knowledge_review_assignment` 5 方言迁移。
- 测试：新旧识别四类测试 + 去重测试 + 冲突对照测试 + 候选不参与临床测试 + 审核→替换链路测试。
- 本地验证：`mvn -q -Dtest=KnowledgeVersionServiceTest,KnowledgeEngineTest,KnowledgeAssetApiContractTest,MigrationBaselineContractTest,H2BaselineMigrationTest test`。
- 审计员签字：@<reviewer>（owner ≠ reviewer）。
