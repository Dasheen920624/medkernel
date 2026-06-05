# SVC-QUALITY-02 · 病案医保服务包

> 读卡前置：[核心 CONSTITUTION](../../CONSTITUTION.md) + [D4 域简报](_brief.md)。
> 迁移来源（覆盖矩阵锚点）：详规 S10 医保与病案质控 · S9 病历内涵质控 · 详规 DRG/DIP。

## 身份
- 卡 ID：SVC-QUALITY-02（服务包卡；DRG/DIP/医保审核单一归属）
- 域：D4 质控改进
- 关联场景：S10 医保与病案质控
- 依赖卡：[EVAL-01](EVAL-01.md) 评估 · [RULE-01](../D2/RULE-01.md) 规则 · [SVC-QUALITY-03](SVC-QUALITY-03.md) 整改 · 页 [INSAUDIT-01](INSAUDIT-01.md)
- 工作量：5d
- owner / reviewer：Codex / 待审（owner ≠ reviewer）

## 目标
把**病历内涵 + DRG/DIP + 编码 + 费用 + 医保审核**编排为服务包：对病案做内涵质控、DRG/DIP 入组核对、编码/费用合规、医保违规识别，全部**可追溯病历证据、不臆造违规**。

## 现状（2026-06-06，以 `medkernel-backend` / `frontend/src` 为准）
已建后端 B0 服务包：`com.medkernel.engine.quality.insurance` 提供病案内涵质控、DRG/DIP 入组核对、医保审核三类执行接口，以及真实医保问题分页读取接口；病案内涵复用 [EVAL-01](EVAL-01.md) 评估运行，DRG/DIP 按请求中的版本化分组结果做可解释核对，医保审核读取真实 `mk_clinical_claim` 结算事实并按版本化规则生成问题与整改联动。无结算事实时返回 `INSUFFICIENT_DATA`，不臆造违规；前端 `quality/InsuranceAudit.tsx` 已由页卡 [INSAUDIT-01](INSAUDIT-01.md) 消费这些真实接口。

## 功能要求（原子可测条目）
- [x] FR-1 病历内涵：对病案跑内涵质控（复用 [EVAL-01](EVAL-01.md)），问题追溯病历。
- [x] FR-2 DRG/DIP 入组：按规则核对入组，入组结果可解释、版本化。
- [x] FR-3 编码/费用：编码合规 + 费用异常识别，证据可追溯。
- [x] FR-4 医保审核：医保违规识别，违规带病历证据与规则依据、不臆造。
- [x] FR-5 整改联动：违规/问题派整改（[SVC-QUALITY-03](SVC-QUALITY-03.md)）。

## 接口契约 / 页面契约
### 接口契约（引擎/API 卡）
- 端点：`GET /api/v1/engine/quality/insurance-issues`（医保问题分页读取）· `POST /api/v1/engine/quality/case-review`（病案内涵）· `POST .../quality/drg-grouping`（入组）· `POST .../quality/insurance-audit`（医保审核）
- DTO：病案/DRG/医保审核 Record；信封 `ApiResult`/`ProblemDetail`；状态机：待办类（问题→整改→复核）
- 读取权限：`evaluation.read`；执行权限：`evaluation.execute`；均按当前租户作用域过滤。
- 幂等 / traceId：审核幂等可复现；trace（[OBS-01](../D0/OBS-01.md)）

## 数据与迁移
- 表族：`mk_quality_case_review` / `mk_quality_drg_grouping` / `mk_quality_insurance_issue`（结果 + 证据引用 + 规则版本 + 组织字段 + 审计）；V84 五方言（[BASE-05](../D0/BASE-05.md)）

## 视角清单（11 视角逐条）
1. 产品架构：病案与医保合规的质控服务包。
2. 产品体验：审核结果可追溯病历（页 [INSAUDIT-01](INSAUDIT-01.md)）。
3. 系统与数据架构：批量审核 10万病案级；P95 ≤1s；可复现。
4. 临床医疗安全：编码/费用/医保问题不影响临床诊疗判断。
5. 知识与数据治理：DRG/编码规则版本化（[SYS-08](../D2/SYS-08.md)）可追溯。
6. 安全合规与监管：★医保违规作监管证据，须有病历依据、留审计（[BASE-04](../D0/BASE-04.md)）。
7. 集团化与多租户治理：按院/病案室作用域。
8. 集成与互操作：医保/病案数据经适配器（[INTEG-01](../D2/INTEG-01.md)）入。
9. 运维 / SRE / 国产化：审核可观测、可重跑。
10. 质量与真实性审计：★违规可追溯病历证据、不臆造、可复现。
11. AI / 模型治理与可降级：编码辅助为挂点，关模型确定性规则审核 `MODEL_DISABLED`。

## 适用不变量
- 命中核心约束：**铁律 #1 真实性（不臆造违规）** · **核心 §7 权威版本** · **§5 状态机** · **合规监管**。
- 本卡落点：病案/DRG/医保审核服务包，违规追溯病历证据。

## 验收 + 验证
- [x] AC-1（FR-1/2）：内涵质控/入组真实可解释。
- [x] AC-2（FR-3/4）：编码/费用/医保违规追溯病历证据。
- [x] AC-3（FR-5）：违规派整改闭环。
- 关联 A1–A9 剧本：A9 医保病案质控。
- T-GATE：后端真实性门禁全绿（违规有据/可复现）。
- B0 验收：关模型规则审核仍可用。

## 大卡工序（5d）
- PR1：病历内涵质控（复用评估）+ 门禁 → 验收
- PR2：DRG/DIP 入组 + 编码/费用 → 验收
- PR3：医保审核 + 证据追溯 + 整改联动 → 验收

## 完工证据
- 代码：`InsuranceQualityController` / `InsuranceQualityService` / V84 五方言 `V84__insurance_quality_service.sql`。
- 测试：`InsuranceQualityServiceTest` 覆盖病案内涵、DRG/DIP、医保真实结算事实、无事实诚实降级、责任科室落库、本次整改只更新本次问题、医保问题列表租户作用域筛选分页；`InsuranceQualityControllerSecurityTest` 覆盖未认证、质控角色、普通医生权限与医保问题读取权限。
- 验证：`mvn -q -Dtest=InsuranceQualityServiceTest,InsuranceQualityControllerSecurityTest,ServiceContractGovernanceTest,DomainOwnershipContractTest,MigrationBaselineContractTest,H2BaselineMigrationTest test`；`mvn -q test`；`npm run verify`；`npm run build`。
- 审计员签字：@待审（owner ≠ reviewer）。
