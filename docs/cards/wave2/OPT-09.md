# OPT-09 · 数据最小化策略引擎

> 读卡前置：[核心 CONSTITUTION](../../CONSTITUTION.md) + [wave2 域简报](_brief.md)。
> 迁移来源（覆盖矩阵锚点）：详规 §数据最小化 · 核心 §安全合规 · 铁律 #1。

## 身份
- 卡 ID：OPT-09（= backlog `OPT-09`）
- 域：wave2（X-LLM）
- 关联场景：S15、S14（合规）
- 依赖卡：[LLM-03](LLM-03.md)（网关侧执行点，与本卡同源）· [SECBASE-01](../D5/SECBASE-01.md)（策略前台）· [SYS-06](../D5/SYS-06.md)（证据框架）
- 工作量：4d
- owner / reviewer：待派单（owner ≠ reviewer）

## 目标
**数据最小化策略引擎**：字段白名单 + 脱敏 + 审批——把数据最小化做成**可配策略中枢**，[LLM-03](LLM-03.md) 等出域点统一消费同一套策略。

## 现状（搬迁时核查 2026-05-31）
**已建（T5.5 代码态）**：[LLM-03](LLM-03.md) 的模型出域治理表扩展为 OPT-09 策略中枢：V144 五方言新增字段级脱敏规则、审批阈值和不可关闭护栏；`DataMinimizationPolicyController` 提供 `/api/v1/data-minimization/policies/model-egress/*` 正式入口；`ModelEgressGuard` 运行时统一消费同一策略。策略缺失仍默认最严：不放行外调，网关降级 B0。

## 功能要求（原子可测条目）
- [x] FR-1 策略中枢：字段白名单 + 脱敏规则 + 审批阈值集中配置（可热生效，复用 [CONFIG-01](../D0/CONFIG-01.md)）。
- [x] FR-2 脱敏规则库：可配脱敏算子（掩码/泛化/置空），按字段类型应用。
- [x] FR-3 审批阈值：按敏感级定审批要求；高敏强制人工批。
- [x] FR-4 统一消费：[LLM-03](LLM-03.md) 及其他出域点调同一策略，不各写各的。
- [x] FR-5 审计：策略变更 + 应用结果留痕（[BASE-04](../D0/BASE-04.md)/[SYS-06](../D5/SYS-06.md)）。

## 接口契约 / 页面契约
### 接口契约（引擎/API 卡）
- 端点：`/api/v1/data-minimization/policies/model-egress/{capabilityCode}`、`/api/v1/data-minimization/policies/model-egress/approvals`；前台在 [SECBASE-01](../D5/SECBASE-01.md)；高危护栏不可关。

## 数据与迁移
- `mk_llm_egress_whitelist` 作为模型出域数据最小化策略中枢：V144 五方言新增 `desensitization_rules`、`approval_threshold_level`、`guardrail_locked_flag`；策略变更复用 LLM-03 审计，应用结果落 `mk_llm_egress_evidence`；高危项护栏持久化不可关（呼应 [CONFIG-01](../D0/CONFIG-01.md)）。

## 视角清单（11 视角）
1. 产品架构：数据最小化的策略单一源。
2. 产品体验：策略前台在 [SECBASE-01](../D5/SECBASE-01.md)（专家/合规）。
3. 系统与数据架构：策略热生效、应用轻量。
4. 临床医疗安全：患者隐私字段强制最小化。
5. 知识与数据治理：N·A。
6. 安全合规与监管：★核心——白名单/脱敏/审批可配可审、满足隐私合规。
7. 集团化与多租户治理：策略按 OrgContext 继承、高危集团统管。
8. 集成与互操作：所有出域点（含 [LLM-03](LLM-03.md)）统一消费。
9. 运维 / SRE / 国产化：策略可离线导出审查。
10. 质量与真实性审计：★策略真实应用、不可被绕过。
11. AI / 模型治理与可降级：策略缺失时默认最严（不放行外调，退 B0）。

## 适用不变量
- 命中核心约束：**核心 §安全合规** · **铁律 #1 真实性** · **#11 配置外置（CONFIG-01）**。
- 本卡落点：白名单+脱敏+审批集中可配可审，出域点统一消费、缺省最严。

## 验收 + 验证
- [x] AC-1（FR-1~3）：策略配置 + 脱敏算子 + 审批阈值生效。
- [x] AC-2（FR-4/5）：[LLM-03](LLM-03.md) 消费同策略 + 变更审计。
- T-GATE：后端真实性门禁全绿。
- B0 验收：★策略缺省最严 → 不外调走 B0，主链路可用。

## 完工证据
- 代码 permalink：`ModelEgressGovernanceService` 策略保存与校验；`ModelEgressGuard` 统一消费；`DataMinimizationPolicyController` 正式策略入口；V144 五方言迁移。
- 测试：`ModelEgressGuardTest`（字段级算子 + 阈值审批）、`ModelEgressGovernanceServiceTest`（策略保存 + 非法算子拒绝）、`ModelEgressGovernanceRepositoryTest`（H2 真实持久化）、`ModelEgressControllerSecurityTest`（策略入口 RBAC）、`MigrationBaselineContractTest#dataMinimizationPolicyIsPersistedAcrossAllDialects`。
- 审计员签字：@<reviewer>（owner ≠ reviewer）。
