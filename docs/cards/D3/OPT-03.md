# OPT-03 · 医疗器械与 CDSS 风险分级矩阵

> 读卡前置：[核心 CONSTITUTION](../../CONSTITUTION.md) + [D3 域简报](_brief.md)。
> 迁移来源（覆盖矩阵锚点）：详规 S15 AI 验证与验收 · 核心 §临床安全 · 合规监管（NMPA 路径预留）。

## 身份
- 卡 ID：OPT-03（治理/契约卡；CDSS 风险分级矩阵单一归属）
- 域：D3 临床运行
- 关联场景：S8 临床嵌入运行 · S15 AI 验证与验收
- 依赖卡：[CDSS-01](CDSS-01.md) 命中 · [OPT-04](OPT-04.md) 红线 · [OPT-02](OPT-02.md) 触发点 · [SYS-04](../D2/SYS-04.md) 版本框架
- 工作量：4d
- owner / reviewer：Codex / 待 PR reviewer（owner ≠ reviewer）

## 目标
建立 **CDSS/器械风险分级矩阵**：按触发点 × 危害严重度 × 自动化程度给每类 CDSS 输出定风险级别，决定其**审核强度/上线门槛/可否自动**，并**预留 NMPA 医疗器械软件（SaMD）合规路径**。

## 现状（搬迁时核查 2026-05-30，以 `medkernel-backend` 为准）
基本待建：现有无统一风险分级矩阵。本卡＝新建分级模型 + 把分级挂到 [CDSS-01](CDSS-01.md) 输出与 [OPT-04](OPT-04.md) 红线上线门槛；NMPA 路径为**预留挂点**（不在本期落证，但模型留字段）。

## 功能要求（原子可测条目）
- [x] FR-1 分级维度：风险级别 = f(触发点危害, 严重度, 自动化程度)，受控矩阵可配。
- [x] FR-2 门槛绑定：每级对应审核强度/静默试运行时长/上线门槛（喂 [OPT-04](OPT-04.md)）。
- [x] FR-3 输出标级：[CDSS-01](CDSS-01.md) 每类推荐卡带风险级别标记，高级别强人工确认。
- [x] FR-4 NMPA 预留：分级模型预留 SaMD 分类/证据字段，不阻断本期但可追溯。
- [x] FR-5 可审计：分级与门槛变更留痕、可解释。

## 接口契约 / 页面契约
### 接口契约（引擎/API 卡）
- 端点：`GET /api/v1/engine/cdss/risk-matrix` · `PUT .../cdss/risk-matrix`（受控变更）
- DTO：风险分级 Record（触发点/严重度/自动化/级别/门槛）；信封 `ApiResult`/`ProblemDetail`
- 状态机：配置类（草稿→已发布→生效中）；变更走 7 步流（[SYS-04](../D2/SYS-04.md)）
- 幂等 / traceId：矩阵版本化；trace（[OBS-01](../D0/OBS-01.md)）

## 数据与迁移
- 表族：`mk_engine_cdss_risk_matrix`（分级规则 + 门槛 + NMPA 预留字段 + 版本 + 审计）；五方言（[BASE-05](../D0/BASE-05.md)）

## 视角清单（11 视角逐条）
1. 产品架构：CDSS 安全治理的"分级标尺"。
2. 产品体验：高风险卡 UI 强确认（[REMIND-01](REMIND-01.md)）。
3. 系统与数据架构：分级查询 O(1)；矩阵版本化可灰度。
4. 临床医疗安全：★高危/高自动化输出强门槛，杜绝"自动执行高危建议"。
5. 知识与数据治理：分级绑定版本，变更可追溯（[SYS-04](../D2/SYS-04.md)）。
6. 安全合规与监管：★NMPA SaMD 路径预留；分级变更审计（[BASE-04](../D0/BASE-04.md)）。
7. 集团化与多租户治理：分级矩阵平台级统一，下级不可下调安全级别（红线）。
8. 集成与互操作：分级随 [OPT-02](OPT-02.md) 触发点维度。
9. 运维 / SRE / 国产化：矩阵可观测、可回滚。
10. 质量与真实性审计：分级真实绑定输出、不可绕过门槛。
11. AI / 模型治理与可降级：★模型类输出默认更高风险级、更严门槛。

## 适用不变量
- 命中核心约束：**核心 §临床安全** · **§5 状态机/7 步流** · **合规监管** · **铁律 #1**。
- 本卡落点：风险分级矩阵决定 CDSS 输出门槛，安全级别下级不可下调。

## 验收 + 验证
- [x] AC-1（FR-1/2）：分级矩阵可配、门槛绑定正确。
- [x] AC-2（FR-3）：CDSS 输出带级别、高级别强确认。
- [x] AC-3（FR-4/5）：NMPA 字段预留、变更可审计。
- 关联 A1–A9 剧本：A9 验证验收。
- T-GATE：后端真实性门禁全绿（门槛不可绕）。
- B0 验收：分级纯确定性、与模型无关。

## 完工证据
- 代码 permalink：PR 创建后补；本地落点为 `engine/cdss/risk/**`、`RecommendationEngineService` / `RecommendationCard`、V72 五方言迁移、服务契约与领域归属登记。
- 测试：`CdssRiskMatrixServiceTest`、`CdssRiskMatrixControllerSecurityTest`、`RecommendationEngineServiceTest`、`RecommendationRepositoryTest`、`ServiceContractGovernanceTest`、`DomainOwnershipContractTest`、`MigrationBaselineContractTest`、`H2BaselineMigrationTest`、`FlywayMultiDialectSmokeTest` 覆盖分级、门槛、高危强确认、审计、五方言迁移。
- 本地验证：`mvn -q test` 通过；`npm run verify` 51 files / 311 tests 通过；`npm run build` 通过；`npm audit --omit=dev --audit-level=moderate` 0 漏洞；T-GATE 自测 34/34、迁移 `--mode=files` 5 个 V72 文件通过。
- 审计员签字：待 PR reviewer（owner ≠ reviewer）。
