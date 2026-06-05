# OPT-05 · 互联互通测评映射

> 读卡前置：[核心 CONSTITUTION](../../CONSTITUTION.md) + [D5 域简报](_brief.md)。
> 迁移来源（覆盖矩阵锚点）：详规 S14 用户权限与合规 · 详规 互联互通测评 · 合规监管（测评标准）。

## 身份
- 卡 ID：OPT-05（治理/契约卡；互联互通测评映射单一归属）
- 域：D5 合规运维
- 关联场景：S14 用户、权限与合规
- 依赖卡：[EVID-01](EVID-01.md) 证据链 · [SYS-06](SYS-06.md) 证据框架 · [EMR-LEVEL-02](../D4/EMR-LEVEL-02.md) 评级证据
- 工作量：4d
- owner / reviewer：待派单（owner ≠ reviewer）

## 目标
建立**互联互通测评映射**：把测评四维（数据资源标准化/互联互通基础设施/应用效果/…）的指标项映射到**产品真实证据**，差距真实、证据可追溯，支撑测评评审。

## 现状（搬迁时核查 2026-05-30，以 `medkernel-backend` 为准）
后端 B0 已建（Codex PR1）：`com.medkernel.compliance.interopassessment` 提供互联互通测评指标项、证据映射、总览与差距查询；V93 五方言新增 `mk_compliance_interop_assessment_item` / `mk_compliance_interop_evidence_map`。测评项只把已落库且可追溯的 [EVID-01](EVID-01.md) 证据快照或 [EMR-LEVEL-02](../D4/EMR-LEVEL-02.md) 评级证据包计为达标证据；仅有映射记录但源证据不存在时仍标 `MISSING_EVIDENCE`，不假达标、不重复采集评级证据。

## 功能要求（原子可测条目）
- [x] FR-1 测评指标项：测评四维指标项受控、版本化。
- [x] FR-2 证据映射：每指标项映射到真实产品证据（[EVID-01](EVID-01.md)），缺证据标缺失。
- [x] FR-3 差距评估：达标/差距真实评估、可下钻到证据。
- [x] FR-4 证据复用：与评级（[EMR-LEVEL-02](../D4/EMR-LEVEL-02.md)）共享证据，不重复采集。
- [x] FR-5 不假达标：未满足项标差距、不假"已通过"。

## 接口契约 / 页面契约
### 接口契约（引擎/API 卡）
- 端点：`GET /api/v1/compliance/interop-assessment?standardVersion=` · `GET .../interop-assessment/gaps?standardVersion=`（`audit.read` + 租户数据范围）
- DTO：`InteropAssessmentResponse` / `InteropAssessmentItemResponse` / `InteropEvidenceResponse` Record；信封 `ApiResult`/`ProblemDetail`
- 状态机：N·A（只读映射 + 评估）；trace（[OBS-01](../D0/OBS-01.md)）
- 版本：测评标准版本化

## 数据与迁移
- 表族：`mk_compliance_interop_assessment_item` / `mk_compliance_interop_evidence_map`（指标 + 证据引用 + 差距 + 版本）；V93 五方言（[BASE-05](../D0/BASE-05.md)）

## 视角清单（11 视角逐条）
1. 产品架构：互联互通测评的"指标↔证据映射图"。
2. 产品体验：差距/证据可下钻（页 [SECBASE-01](SECBASE-01.md) 或合规视图）。
3. 系统与数据架构：映射评估可复算；P95 ≤1s。
4. 临床医疗安全：N·A（合规测评）。
5. 知识与数据治理：测评标准版本化、证据可追溯。
6. 安全合规与监管：★测评映射作评审材料，差距/证据真实。
7. 集团化与多租户治理：按院作用域测评。
8. 集成与互操作：★直接服务"互联互通"测评，证据标准化。
9. 运维 / SRE / 国产化：映射可观测、可重算。
10. 质量与真实性审计：★证据真实映射、不假达标、缺证标缺失。
11. AI / 模型治理与可降级：N·A（确定性映射）。

## 适用不变量
- 命中核心约束：**铁律 #1 真实性（不假达标）** · **合规监管** · **核心 §6 证据**。
- 本卡落点：互联互通测评指标↔产品证据映射，差距真实、证据复用评级。

## 验收 + 验证
- [x] AC-1（FR-1/2）：测评指标项与证据映射真实。
- [x] AC-2（FR-3/4）：差距真实可下钻；与评级共享证据。
- [x] AC-3（FR-5）：未满足标差距、不假达标。
- 关联 A1–A9 剧本：A9 测评映射。
- T-GATE：后端真实性门禁全绿（证据真实/不假达标）。
- B0 验收：测评映射确定性、与模型无关。

## 完工证据
- 代码 permalink：测评指标项 + 证据映射 + 差距评估。
- 测试：映射复算 / 证据追溯 / 不假达标。
- 审计员签字：@<reviewer>（owner ≠ reviewer）。

### Codex PR1 本地证据
- 代码：`medkernel-backend/src/main/java/com/medkernel/compliance/interopassessment/**`；V93 五方言迁移；`ServiceContractCatalog` / `DomainOwnershipCatalog` 登记。
- 测试：`InteropAssessmentServiceTest` 覆盖 EVID-01 真实证据、EMR-LEVEL-02 证据复用、缺证据 `MISSING_EVIDENCE` 和 `/gaps`；`InteropAssessmentControllerSecurityTest` 覆盖 `audit.read` + 租户范围；迁移基线测试覆盖 V93。
- 本地验证：`cd medkernel-backend && mvn -Dtest=InteropAssessmentServiceTest,InteropAssessmentControllerSecurityTest,MigrationBaselineContractTest,H2BaselineMigrationTest,ServiceContractGovernanceTest,DomainOwnershipContractTest test` 退出码 0（93 测试，V93）。
