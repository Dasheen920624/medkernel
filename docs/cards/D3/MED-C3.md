# MED-C3 · 安全撤回与旧版下游隔离（端到端）

> 读卡前置：[核心 CONSTITUTION](../../CONSTITUTION.md) + [D3 域简报](_brief.md)。
> 迁移来源（覆盖矩阵锚点）：核心 §6 旧版隔离 + §7 唯一权威 + #14 · 详规 S13 包发布与院内同步 · 详规 S8 临床运行安全。

## 身份
- 卡 ID：MED-C3（引擎卡；临床域"安全撤回端到端"编排，复用 [SYS-08](../D2/SYS-08.md) 框架）
- 域：D3 临床运行
- 关联场景：S8 临床嵌入运行 · S13 包发布与院内同步
- 依赖卡：[SYS-08](../D2/SYS-08.md) 权威替换/紧急失效（框架归属）· [SYS-04](../D2/SYS-04.md) 版本框架 · [CDSS-01](CDSS-01.md) 命中 · [OPT-04](OPT-04.md) 红线 · [SVC-CLINICAL-03](SVC-CLINICAL-03.md) 复核任务派发
- 工作量：4d
- owner / reviewer：待派单（owner ≠ reviewer）

## 目标
把"知识/规则被召回或禁忌升级"做成**端到端安全撤回**：紧急停用旧版 → **新临床请求只命中新版** → 旧版仅历史重放 → **自动生成受影响患者/路径/同步目标的复核任务**，全链可审计、不漏一例。

## 现状（搬迁时核查 2026-05-30，以 `medkernel-backend` 为准）
框架基础在 D2：`engine/knowledge/` 已有 `KnowledgeVersionService.withdraw`、`KnowledgeSupersession`、悲观锁原子替换（[SYS-08](../D2/SYS-08.md) 归属）。本卡＝**临床域端到端编排**：把"紧急失效 → 下游 CDSS/路径/嵌入隔离旧版 → 影响病例任务派发"串成可验收闭环（D2 给框架、D3 给临床落点），非重造约束。

2026-06-04 收口：新增 `engine/safety` 安全撤回编排层，`POST /api/v1/engine/safety/withdrawals` 复用 SYS-08 撤回框架，`GET /impact` 和 `GET /impact/export` 从真实 `mk_knowledge_affected_case_task` 重算 / 导出影响集合；`ClinicalSafetyGuard` 挂到推荐评估 / 触发与路径入径入口，阻止 `knowledge-version:<id>` 指向的非 ACTIVE 知识版本进入新临床命中。嵌入 token 当前不持有知识版本字段，隔离随推荐 / 路径新请求入口生效，不伪造 token 级旧版字段。复核任务统一进入待办 / 通知与完成闭环仍由 [SVC-CLINICAL-03](SVC-CLINICAL-03.md) 承接。

## 功能要求（原子可测条目）
- [x] FR-1 紧急停用：对召回/禁忌升级的版本调 [SYS-08](../D2/SYS-08.md) 紧急失效，立即不可命中。
- [x] FR-2 下游隔离：CDSS（[CDSS-01](CDSS-01.md)）/路径/嵌入（[EMBED-01](EMBED-01.md)）即刻不再命中旧版，仅历史重放可见旧版并标"历史版本"。
- [x] FR-3 影响识别：扫描受影响**患者/路径实例/同步目标**，生成复核任务（[SVC-CLINICAL-03](SVC-CLINICAL-03.md)）。
- [x] FR-4 不漏派：影响集合可重算、可核对，任务派发幂等不漏不重。
- [x] FR-5 审计闭环：召回原因/范围/任务/复核结果全留痕，可导出证据。

## 接口契约 / 页面契约
### 接口契约（引擎/API 卡）
- 端点：`POST /api/v1/engine/safety/withdrawals`（发起安全撤回）· `GET .../safety/withdrawals/{id}/impact`（影响集合）· `GET .../impact/export`（NDJSON 证据导出）
- DTO：撤回请求（版本/原因/范围 Record）→ 撤回结果 + 影响摘要；信封 `ApiResult`/`ProblemDetail`
- 状态机：变更类（发起→隔离中→已隔离 + 复核任务进行中→闭环）
- 幂等 / traceId：撤回与任务派发幂等；全链 trace（[OBS-01](../D0/OBS-01.md)）

## 数据与迁移
- 复用 [SYS-08](../D2/SYS-08.md) 版本/替代链表 + 撤回记录表 + 影响病例任务表（[SVC-CLINICAL-03](SVC-CLINICAL-03.md)）；五方言（[BASE-05](../D0/BASE-05.md)）

## 视角清单（11 视角逐条）
1. 产品架构：临床安全的"急停 + 隔离 + 复核"端到端编排。
2. 产品体验：复核任务进待办（[TODO-01](TODO-01.md)），有责任人/截止。
3. 系统与数据架构：影响扫描可重算；隔离即时生效（命中读 `ACTIVE`）；P95 影响查询可控。
4. 临床医疗安全：★召回旧版绝不再参与临床命中；不漏派受影响病例。
5. 知识与数据治理：★旧版仅历史重放、标"历史版本"；替代链完整（[SYS-08](../D2/SYS-08.md)）。
6. 安全合规与监管：召回/范围/任务/结果全审计可导出（[BASE-04](../D0/BASE-04.md)）。
7. 集团化与多租户治理：撤回范围按 `OrgContext`/继承生效；下级不可静默恢复旧版。
8. 集成与互操作：同步目标（[INTEG-01](../D2/INTEG-01.md)/[PKG-01](../D2/PKG-01.md)）一并标记待复核。
9. 运维 / SRE / 国产化：急停可观测、可回放核对影响集合。
10. 质量与真实性审计：★影响集合真实可重算、任务不漏不重。
11. AI / 模型治理与可降级：N·A（确定性安全编排；与模型无关）。

## 适用不变量
- 命中核心约束：**核心 §6 旧版隔离** · **§7 唯一权威** · **#14 影响病例任务** · **铁律 #1**。
- 本卡落点：召回→下游隔离→影响复核的临床端到端闭环，框架复用 [SYS-08](../D2/SYS-08.md)。

## 验收 + 验证
- [x] AC-1（FR-1/2）：撤回后旧版即刻不可命中（CDSS/路径/嵌入），仅历史重放。
- [x] AC-2（FR-3/4）：受影响患者/路径/同步目标复核任务自动派发、不漏不重。
- [x] AC-3（FR-5）：召回全链审计可导出。
- 关联 A1–A9 剧本：A8 安全撤回。
- T-GATE：后端真实性门禁全绿（旧版不可命中 / 影响不漏派）。
- B0 验收：撤回与隔离纯确定性，关模型仍闭环。

## 完工证据
- 代码 permalink：安全撤回编排 + 影响扫描 + 复核任务派发。
- 测试：`ClinicalSafetyGuardTest`、`SafetyWithdrawalServiceTest`、`SafetyWithdrawalControllerSecurityTest`、`RecommendationEngineServiceTest`、`PathwayEngineServiceTest`、`ServiceContractGovernanceTest`、`OpenApiContractConfigurationTest`。
- 本地验证（2026-06-04）：rebase 到 `origin/main` `71005f67` 后，MED-C3 聚焦套件、`mvn -q test`、`npm run verify`（51 files / 311 tests）、`npm run build`、T-GATE 脚本自测 34/34、真实性全量扫描 964 文件、配置边界 inventory 902 文件、迁移 changed 0 文件、中文注释 0 fail / 0 warn、`git diff --check origin/main..HEAD` 均已通过；远端 CI / 合并以 PR 门禁为准。
- 审计员签字：@<reviewer>（owner ≠ reviewer）。
