# API-05 · 规则引擎 API

> 读卡前置：[核心 CONSTITUTION](../../CONSTITUTION.md) + [D2 域简报](_brief.md)。
> 迁移来源（覆盖矩阵锚点）：详规 §1.5.2 当前/后续 API 清单·规则行（L185）· §4.7 规则发布门禁（L1115）· 落地规划 §8.2 规则引擎（L458）· 核心 §1.4 统一入参。

## 身份
- 卡 ID：API-05（= backlog 任务 ID）
- 域：D2 试点准备
- 关联场景：S5 规则引擎配置（客户面 API）
- 依赖卡：[RULE-01](RULE-01.md)（规则引擎）· [SYS-04](SYS-04.md)（发布）· [API-01](API-01.md)（执行输入）· [BASE-03](../D0/BASE-03.md)（契约）· [API-13](../D0/API-13.md)（大列表）
- 工作量：3d
- owner / reviewer：待派单（owner ≠ reviewer）

## 目标
提供规则引擎**统一 REST 客户面**：规则定义 CRUD · 测试病例 · 影响分析 · 发布（7 步流）· 执行 · 解释。本卡只立 **API 契约**，能力在 [RULE-01](RULE-01.md)、发布在 [SYS-04](SYS-04.md)。

## 现状（搬迁时核查 2026-05-30，以 `medkernel-backend/src` 为准；API-05 收口 2026-06-02）
`engine/rule` **控制器已建**，本卡＝**契约化 + 影响分析/解释/统一入参补全**：
- 已有：`RuleEngineController`(+ 安全测试)、`RuleEngineService`、`RuleEvaluateRequest`/`Response`、`RuleSimulateRequest`、`RuleTestCase`、`RulePublishResponse`、`RuleCreateRequest`/`Response`/`RuleDetailResponse`/`RuleFilter`。
- 已补：① 客户面入口统一为 `/api/v1/engine/rule/**`；② 规则定义创建/详情/列表/更新、测试病例、全量测试、仿真、影响分析、发布、执行、执行解释端点；③ 写接口、仿真、测试、发布、执行补 12 字段统一入参与租户一致性校验；④ 高危规则发布必须携带当前影响分析摘要，且测试用例需全绿；⑤ DSL 错误返回 `ENG-RULE-001`，高危门禁返回 `ENG-RULE-004`；⑥ 规则影响分析已通过关系库只读索引定位路径模板、在径患者和发布同步目标，关闭 `DEFER-012`，无真实对象时返回空列表，不前端补造。
- 未在本卡伪造：真实 10 万级规则列表压测归 [API-13](../D0/API-13.md) / [SYS-07](../ga/SYS-07.md) / GA 总验收关闭。

## 功能要求（原子可测条目）
- [x] **FR-1 定义 CRUD**：`GET/POST/PUT /rules`、`GET /rules/{id}`（含三层产物）；列表分页（[API-13](../D0/API-13.md)）。
- [x] **FR-2 测试 + 仿真**：`POST /rules/{id}/test`（用例全绿判定）、`POST /rules/{id}/simulate`（真实快照）。
- [x] **FR-3 影响分析**：`GET /rules/{id}/impact`（受影响规则/路径/在径患者/同步目标）。
- [x] **FR-4 发布**：`POST /rules/{id}/publish`（7 步流，委托 [SYS-04](SYS-04.md)）；高危无用例/无影响分析 → 拒。
- [x] **FR-5 执行 + 解释**：`POST /rules/evaluate`（对标准上下文求值）、返回 `RuleActionResult` + 命中解释。
- [x] **FR-6 统一入参/信封**：12 字段入参 + `ApiResult`/`ProblemDetail`（[BASE-03](../D0/BASE-03.md)）。

## 接口契约 / 页面契约
### 接口契约（引擎/API 卡）
- 端点：`/api/v1/engine/rule/**`（rules、test、simulate、impact、publish、evaluate）。
- DTO：复用 `RuleCreateRequest`/`RuleDetailResponse`/`RuleEvaluateRequest`/`Response`/`RulePublishResponse`/`RuleFilter`。
- 响应信封：`ApiResult` / `ProblemDetail`；大列表 `PageResult`（[API-13](../D0/API-13.md)）。
- 状态机：规则版本核心 §3 配置类 + 变更类（[SYS-04](SYS-04.md)）。
- 幂等 / 错误码 / traceId：发布幂等键；高危门禁 → `ENG-RULE-004`；DSL 错 → `ENG-RULE-001`；traceId（[OBS-01](../D0/OBS-01.md)）。
### 页面契约（页面卡）
N·A —— 本卡无页面。被 [RULE-01](RULE-01.md) 规则库页消费。

## 数据与迁移
- 无独立表族——复用 [RULE-01](RULE-01.md) 表族。不落新库（API 契约卡）。

## 视角清单（11 视角逐条）
1. **产品架构**：规则能力统一对外契约口。
2. **产品体验**：N·A —— 规则库页（[RULE-01](RULE-01.md)）消费。
3. **系统与数据架构**：统一入参/信封/分页；求值/影响分析可观测；P95 ≤1s。
4. **临床医疗安全**：发布门禁 API 层兜底（高危必带用例/影响）。
5. **知识与数据治理**：规则版本/影响/发布可溯。
6. **安全合规与监管**：发布/回滚留审计（[BASE-04](../D0/BASE-04.md)）。
7. **集团化与多租户治理**：按 `OrgContext` 作用域；发布权五维 RBAC。
8. **集成与互操作**：执行入参为标准上下文（[API-01](API-01.md)）。
9. **运维 / SRE / 国产化**：灰度/回滚；大列表分页稳定。
10. **质量与真实性审计**：无伪造影响分析/解释；门禁 API 校验（铁律 #1）。
11. **AI / 模型治理与可降级**：执行/解释确定性；AI 规则候选经本 API 入审核，关模型不影响执行。

## 适用不变量
- 命中核心约束：**§1.4 统一入参** · **§4.7 发布门禁** · **§4 7 步流** · **依赖 [RULE-01](RULE-01.md)/[SYS-04](SYS-04.md)/[API-13](../D0/API-13.md)**。
- 本卡落点：规则能力以统一契约对外，门禁在 API 层再兜一层。

## 验收 + 验证
- [x] **AC-1（FR-1/2）**：规则 CRUD + 用例测试，统一信封；用例不全绿不可发布。
- [x] **AC-2（FR-3/4）**：影响分析返回受影响对象；高危无影响分析发布 → `ENG-RULE-004`。跨域真实影响对象由关系库只读索引返回路径模板、在径患者和同步目标，`DEFER-012` 已关闭；无真实引用时返回空列表，不伪造对象。
- [x] **AC-3（FR-5）**：对标准上下文求值返回结果 + 解释。
- [x] **AC-4（FR-6）**：缺统一入参 → `ProblemDetail`；越权 → 0 + 审计。
- 关联 A1–A9 剧本：A3 规则配置、A4 发布回滚。
- T-GATE：本分支脚本门禁与提交后 changed-mode 扫描作为 PR 合入门禁，证据见 [D2 域级验收报告](../../audit/D2-domain-acceptance.md)。
- B0 验收：确定性求值，**天然 B0**。

## 完工证据
- 代码 permalink：`/api/v1/engine/rule/**` 端点 + 影响分析 + 解释 + 发布接 [SYS-04](SYS-04.md)。
- 本地验证：`RuleEngineApiContractTest`、`RuleEngineServiceTest`、`RuleEngineControllerSecurityTest`、`RuleDslEvaluatorTest`、`RuleRepositoryTest`、`RelationalRuleImpactIndexTest`、`RelationalRuleImpactIndexRepositoryTest`、`ServiceContractGovernanceTest`、`OpenApiContractConfigurationTest`、`GlobalExceptionHandlerTest`、`ErrorCodeTest` 聚焦通过；后端全量 `mvn -q test` 通过；`FlywayMultiDialectSmokeTest` 覆盖 H2 / PostgreSQL / Oracle 迁移至当前版本；T-GATE 脚本测试、中文注释 changed 扫描、diff 检查与前端全量证据见 [D2 域级验收报告](../../audit/D2-domain-acceptance.md)。历史迁移中文 COMMENT full gap 仍归 `DEFER-006`，不得写成已清零。
- 审计员签字：@<reviewer>（owner ≠ reviewer）。
