# LLM-06 · 可信来源探索编排

> 读卡前置：[核心 CONSTITUTION](../../CONSTITUTION.md) + [wave2 域简报](_brief.md)。
> 迁移来源（覆盖矩阵锚点）：详规 §来源探索 · 核心 §6/§7 权威与投影 · 铁律 #1 真实性。

## 身份
- 卡 ID：LLM-06（= backlog `LLM-06`）
- 域：wave2（X-LLM）
- 关联场景：S15、S37（床旁知识）
- 依赖卡：[LLM-01](LLM-01.md)（能力 `knowledge.discovery`）· [OPT-07](../D2/OPT-07.md)（来源分级）· [KNOW-01](../D2/KNOW-01.md)（来源登记）
- 工作量：4d
- owner / reviewer：待派单（owner ≠ reviewer）

## 目标
**可信来源探索编排**：受控检索 + 检索时点记录 + 来源核验——AI 探索新知识时只从受控来源检索、记录检索时点、产出必带可核验来源（非凭空生成）。

## 现状（搬迁时核查 2026-05-31 → 实现核查 2026-06-16）
**已实现**。落卡前核查发现**受控源清单不需新建**：KNOW-01 `source_document`（受控源注册表：编码/类型/A–E 权威级/publisher/license）+ `source_version`（content_hash 真实核验）+ `source_fragment`（引用锚点 + 正文）已是成熟受控源注册表；`knowledge.discovery` 能力码（V18 网关 + V127 增强矩阵 ACTIVE，B0=确定性知识检索）+ `SourceAuthorityLevel` A–E + AIK-STD-01 `KnowledgeAssetEnvelope` 候选契约均已建。故**复用既有受控源，不建 `knowledge_discovery_source`**，仅新增编排服务 + `mk_knowledge_discovery_run` 检索时点存证表。2026-06-16 已接 [AIK-STD-08](AIK-STD-08.md)：请求可带 `targetIdentityId`，响应 `diffs[]` 返回与现行权威的差异/过期检测结果；产出候选仍交 AIK 流水线（AIK-STD-13），不写权威库。

## 功能要求（原子可测条目）
- [x] FR-1 受控源：仅从配置的受控来源（法规/指南/说明书库）检索，不开放全网。〔`ControlledSourceSearchRepository` 仅 JOIN 已登记 source_*，强租户隔离〕
- [x] FR-2 检索时点：每次探索记检索时点 + 源版本（可复查「当时看到什么」）。〔`mk_knowledge_discovery_run`：executed_at + source_snapshot 源版本快照 + result_hash〕
- [x] FR-3 来源核验：产出每条带来源锚点 + 可信分级（[OPT-07](../D2/OPT-07.md)）；无源不出。〔每候选带 `AssetSourceRef`（锚点+A–E），经 AIK-STD-01 校验闸无源拒收〕
- [x] FR-4 候选交付：探索结果作**候选**交审核链，不直接入权威库。〔产 DRAFT 候选信封返回交 AIK-STD-13，不写权威〕
- [x] FR-5 不臆造：无可信来源时诚实空态，禁模型臆造来源/引文。〔无匹配 EMPTY（degraded=false）/ 上游不可用 DEGRADED；来源恒为真实注册片段，纯确定性 B0 无模型臆造〕

## 接口契约 / 页面契约
### 接口契约（引擎/API 卡）
- 复用 [API-12](API-12.md) `knowledge.discovery`；新增受控源配置 + 探索任务端点。
- `POST /api/v1/engine/knowledge/discovery:explore`：`query` / `limit` / 可选 `targetIdentityId`；响应候选 `candidates[]` 与可选差异检测 `diffs[]`。

## 数据与迁移
- 复用 KNOW-01 `source_document/source_version/source_fragment` 作为受控源，不新建 `knowledge_discovery_source`。
- `mk_knowledge_discovery_run`（检索时点/源版本/结果 hash），五方言；AIK-STD-08 另有 `mk_knowledge_diff` + `mk_knowledge_expiry_task`。

## 视角清单（11 视角）
1. 产品架构：知识探索的受控入口。
2. 产品体验：N·A（结果在审核台呈现）。
3. 系统与数据架构：检索异步、留时点。
4. 临床医疗安全：探索产物经审核才可临床用。
5. 知识与数据治理：★产出候选走版本/审核/替换链；来源分级。
6. 安全合规与监管：检索范围受控、可审计。
7. 集团化与多租户治理：受控源按 OrgContext。
8. 集成与互操作：外部源经 [LLM-08](LLM-08.md) 出域 + [LLM-03](LLM-03.md) 最小化。
9. 运维 / SRE / 国产化：无外网仅检索内置受控源。
10. 质量与真实性审计：★无源不出、不臆造引文。
11. AI / 模型治理与可降级：无模型/无源 → 诚实空态。

## 适用不变量
- 命中核心约束：**铁律 #1 真实性**（无源不出）· **核心 §6/§7 权威与投影** · **#4 B0**。
- 本卡落点：受控检索 + 时点存证 + 来源核验，候选入审核链不臆造。

## 验收 + 验证
- [x] AC-1（FR-1~3）：受控源检索 + 时点 + 来源核验。
- [x] AC-2（FR-4/5）：候选（DRAFT 信封）校验就绪交审核链；无源诚实空态。
- [x] T-GATE：四门禁（真实性/配置/迁移/中文注释）changed 全绿。
- [x] B0 验收：★无模型/无外网时不臆造、诚实空（纯确定性检索，无受控匹配诚实 EMPTY）。

## 完工证据
- 代码：`com.medkernel.engine.knowledge.discovery`（`DiscoveryOrchestrationService` 编排 + `ControlledSourceSearchRepository` 受控检索 + `mk_knowledge_discovery_run` 时点存证 + `DiscoveryController`）+ V129 五方言迁移；复用 KNOW-01 受控源 + AIK-STD-01 校验闸；AIK-STD-08 差异检测接入 `KnowledgeDiffDetectionService`。
- 测试：`DiscoveryOrchestrationServiceTest`（9：产候选/归一/存证/EMPTY/DEGRADED/result_hash 确定性/空白拒收/台账/差异接入）+ `ControlledSourceSearchRepositoryIntegrationTest`（2：权威序+租户隔离+limit）+ `DiscoveryRunRepositoryIntegrationTest`（2）+ `DiscoveryControllerSecurityTest`（5：权限矩阵）。
- 审计员签字：@<reviewer>（owner ≠ reviewer，待派单）。
