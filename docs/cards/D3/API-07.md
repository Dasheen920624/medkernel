# API-07 · 推荐 / CDSS API

> 读卡前置：[核心 CONSTITUTION](../../CONSTITUTION.md) + [D3 域简报](_brief.md)。
> 迁移来源（覆盖矩阵锚点）：详规 S8 临床嵌入运行 · 详规 §1.4 推荐与反馈 · 体验规范 §3 低打扰提醒。

## 身份
- 卡 ID：API-07（引擎/API 卡）
- 域：D3 临床运行
- 关联场景：S8 临床嵌入运行 · S16 辅助诊疗
- 依赖卡：[CDSS-01](CDSS-01.md) 推荐引擎（单一归属）· [API-02](API-02.md) 事件 · [BASE-03](../D0/BASE-03.md) API 契约 · [API-13](../D0/API-13.md) 大列表 · [OPT-02](OPT-02.md) 触发点
- 工作量：3d
- owner / reviewer：待派单（owner ≠ reviewer）

## 目标
把推荐/CDSS 能力**契约化**：触发 → 返回推荐卡（含解释追溯）→ 医师反馈（采纳/不采纳 + 原因）→ 疲劳治理，全部真实数据、可解释、低打扰。

## 现状（搬迁时核查 2026-05-30，以 `medkernel-backend` 为准）
已有实质基础：`engine/recommendation/` 下 `RecommendationEngineController` + `RecommendationCard{+DetailResponse/Filter/Repository/Request/Status/Type}`。本卡＝把控制器/DTO 契约化为统一 API（触发/列表/详情/反馈/疲劳），命中逻辑归 [CDSS-01](CDSS-01.md)。

## 功能要求（原子可测条目）
- [x] FR-1 触发与列表：按患者/就诊/触发点取推荐卡列表（[API-13](../D0/API-13.md) 分页/筛选）。
- [x] FR-2 详情解释：推荐卡详情含命中规则/路径/知识来源与版本（可追溯）。
- [x] FR-3 反馈：采纳/不采纳必须带**原因**（不采纳原因结构化），留痕。
- [x] FR-4 疲劳治理：重复/低价值卡按阈值抑制；抑制可解释、可审计。
- [x] FR-5 降级：关模型时只返回确定性命中卡 + `MODEL_DISABLED` 标记。

## 接口契约 / 页面契约
### 接口契约（引擎/API 卡）
- 端点：`POST /api/v1/engine/recommendations:evaluate`（触发）· `GET .../recommendations/cards`（列表）· `GET .../recommendations/cards/{id}`（详情）· `POST .../recommendations/cards/{id}/feedback`（反馈）
- DTO：`RecommendationCardRequest` / `RecommendationCardFilter` → `RecommendationCardDetailResponse`；反馈 DTO 含 `accepted` + `reason`
- 响应信封：`ApiResult` / `ProblemDetail`；状态机：告警类（待处理→已采纳/已拒绝/已抑制）
- 幂等 / 错误码 / traceId：反馈幂等键；trace（[OBS-01](../D0/OBS-01.md)）

## 数据与迁移
- 复用 `RecommendationCard` 表族（[CDSS-01](CDSS-01.md) 归属）+ 反馈/抑制记录表；本卡不另立模型，仅补反馈与疲劳字段。

## 视角清单（11 视角逐条）
1. 产品架构：CDSS 对外契约层（命中归引擎 [CDSS-01](CDSS-01.md)）。
2. 产品体验：N·A（页面在 [REMIND-01](REMIND-01.md)）；但契约保证"采纳/拒绝带原因 + 低打扰"。
3. 系统与数据架构：触发 P95 ≤1s；列表大数据量分页；疲劳抑制 O(1) 查询。
4. 临床医疗安全：★推荐只是建议、不自动执行；不采纳带原因；高危卡走 [OPT-03](OPT-03.md) 分级。
5. 知识与数据治理：每张卡可追溯到命中的规则/知识版本（[SYS-08](../D2/SYS-08.md)）。
6. 安全合规与监管：反馈/抑制全留审计（[BASE-04](../D0/BASE-04.md)）。
7. 集团化与多租户治理：推荐与疲劳阈值按 `OrgContext`/科室作用域。
8. 集成与互操作：触发点对齐 [OPT-02](OPT-02.md)；可经 [EMBED-01](EMBED-01.md) 嵌入第三方。
9. 运维 / SRE / 国产化：命中可观测；模型不可用诚实降级。
10. 质量与真实性审计：★无前端造卡、无假采纳率；抑制有据。
11. AI / 模型治理与可降级：模型增强（语义解释）为挂点，关闭回确定性卡 `MODEL_DISABLED`。

## 适用不变量
- 命中核心约束：**铁律 #1 真实性** · **核心 §11 B0 先于模型** · **§13 低打扰/可解释** · **§1.4 统一入参**。
- 本卡落点：推荐/反馈/疲劳的真实可追溯契约，命中归 [CDSS-01](CDSS-01.md)。

## 验收 + 验证
- [x] AC-1（FR-1/2）：列表/详情正确，详情可追溯到命中版本。
- [x] AC-2（FR-3/4）：反馈必带原因；疲劳抑制按阈值且可解释。
- [x] AC-3（FR-5）：关模型只回确定性卡 + `MODEL_DISABLED`。
- 关联 A1–A9 剧本：A5 推荐与反馈。
- T-GATE：前后端真实性门禁全绿（无假采纳率 / 无前端造卡）。
- B0 验收：关模型推荐契约仍可用（确定性卡）。

## 完工证据
- 代码：`RecommendationEvaluateSuffixController` 新增 `POST /api/v1/engine/recommendations:evaluate`；`RecommendationEngineController` 暴露 `cards` 列表/详情/反馈契约；`RecommendationEngineService` 落实确定性评估、模型关闭 `MODEL_DISABLED`、疲劳抑制和反馈幂等；`V68__recommendation_cdss_contract.sql` 五方言补反馈幂等键与 `SUPPRESSED` 约束。
- 测试：先跑红灯套件，确认缺 `RecommendationEvaluationResponse` / `evaluate` / `MODEL_DISABLED` / `SUPPRESSED` / `ENG_REC_007` / 幂等仓储等能力导致编译失败；实现后 `mvn -q -Dtest=RecommendationEngineServiceTest,RecommendationRepositoryTest,RecommendationEngineControllerSecurityTest,OpenApiContractConfigurationTest,MigrationBaselineContractTest,H2BaselineMigrationTest,ErrorCodeTest test` 通过；`mvn -q -Dtest=ClinicalEventEngineAdapterTest,EngineEndToEndIntegrationTest,ServiceContractGovernanceTest,FlywayMultiDialectSmokeTest test` 通过；补 ROLLBACK 说明后 `mvn -q -Dtest=MigrationBaselineContractTest,H2BaselineMigrationTest,FlywayMultiDialectSmokeTest test` 通过；最终 `mvn -q test` 通过，Surefire 190 reports / 1149 tests / 0 failures / 0 errors / 0 skipped，H2 / PostgreSQL / Oracle 均迁移到 V68 且二次 migrate no-op。
- T-GATE：提交前工作树扫描通过：真实性门禁候选 31 个 / 实扫 15 个、配置边界候选 31 个 / 实扫 15 个、迁移规约 files-mode 扫 5 个 V68 文件；`node --test scripts/authenticity-guard.test.mjs scripts/config-boundary-guard.test.mjs scripts/migration-convention-guard.test.mjs` 34/34 通过；`scripts/check-comment-zh.sh` 0 fail / 0 warn；`git diff --check` 通过。提交后 changed-mode：真实性门禁扫描 15 个、配置边界扫描 15 个、迁移规约扫描 5 个，均通过；中文注释 0 fail / 0 warn；`git diff --check origin/main..HEAD` 通过。
- 审计员签字：@<reviewer>（owner ≠ reviewer，PR 审核时补）。
