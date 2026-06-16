# LLM-04 · 提示词、工具与模型版本治理

> 读卡前置：[核心 CONSTITUTION](../../CONSTITUTION.md) + [wave2 域简报](_brief.md)。
> 迁移来源（覆盖矩阵锚点）：详规 §版本治理 · 核心 §6 唯一权威知识 · 铁律 #10 文档同步。

## 身份
- 卡 ID：LLM-04（= backlog `LLM-04`）
- 域：wave2（X-LLM）
- 关联场景：S15
- 依赖卡：[LLM-01](LLM-01.md)（任务记 prompt_version/model_version）· [SYS-04](../D2/SYS-04.md)（版本发布框架）· [BASE-04](../D0/BASE-04.md)（审计）
- 工作量：3d
- owner / reviewer：待派单（owner ≠ reviewer）

## 目标
**提示词 / 工具 / 模型版本治理**：可重放、可回滚、可导出——每次 AI 产出可定位到当时的 prompt+tool+model 版本三元组并复现。

## 现状（搬迁时核查 2026-05-31）
**部分**：`model_capability_task` 已存 `prompt_version`/`model_version`（当前恒 `baseline`/`B0-Deterministic-Baseline`）。本卡＝建**版本仓**（prompt/tool/model 版本可发布/回滚/导出）+ 任务绑定真实版本三元组 + 重放。复用 [SYS-04](../D2/SYS-04.md) 版本框架而非另起。

## 最新进度（2026-06-16 readiness 前置闸）
- 知识生产 readiness 已要求模型生产任务传入 `modelStrategy`，并至少声明 `prompt`、`tool`、`model` 三元组；若指定 provider，三元组中的模型版本必须与 provider 当前 `modelVersion` 一致。
- 已新增 `mk_llm_model_version_bundle` V139 五方言版本包表与 `tool_version` 任务列；`ModelVersionGovernanceService/Controller` 支持版本包发布、active 查询、回滚、导出，导出只含版本号与 hash，不泄露提示词正文/工具契约明文；`ModelGatewayService` 在 provider 成功任务上记录 ACTIVE 版本包的 prompt/tool 版本与 provider 返回的真实 modelVersion。
- 仍未完成：按 `task_id` 取当时输入和三元组执行可复现重放；版本仓尚未接 SYS-04 发布计划 UI。

## 功能要求（原子可测条目）
- [x] FR-1 版本仓：prompt/tool/model 各有版本记录（内容 hash + 生效区间）。
- [x] FR-2 任务绑定：每任务记真实 prompt+tool+model 版本三元组。
- [ ] FR-3 重放：按 task_id 取当时三元组 + 输入可复现产出（B0 下确定性）。
- [x] FR-4 回滚：可将能力码切回历史版本三元组。
- [x] FR-5 导出：版本与审计可导出（[EVID-01](../D5/EVID-01.md)）。

## 接口契约 / 页面契约
### 接口契约（引擎/API 卡）
- 端点：`/api/v1/model-versions/*`（增/查/回滚/导出）；信封 [BASE-03](../D0/BASE-03.md)。
- 状态机：配置（版本 草稿→生效→停用）。

## 数据与迁移
- `model_prompt_version` / `model_tool_version` / `model_version_registry`（内容 hash + 区间 + 审计），五方言。

## 视角清单（11 视角）
1. 产品架构：AI 可治理性基础设施。
2. 产品体验：N·A（专家/开发者视图）。
3. 系统与数据架构：版本查询走 [API-13](../D0/API-13.md)。
4. 临床医疗安全：可定位某条 AI 建议用的版本，便于事故追溯。
5. 知识与数据治理：★唯一生效版本（核心 §6）类比用于 prompt/model。
6. 安全合规与监管：版本变更审计 + 可导出证据。
7. 集团化与多租户治理：版本按 OrgContext 继承/覆盖。
8. 集成与互操作：N·A。
9. 运维 / SRE / 国产化：版本可离线导出/导入。
10. 质量与真实性审计：★版本三元组真实绑定、不伪造。
11. AI / 模型治理与可降级：版本缺失 → B0；治理本身不阻断 B0。

## 适用不变量
- 命中核心约束：**核心 §6 唯一权威**（生效版本）· **铁律 #10 文档同步** · **#1 真实性**。
- 本卡落点：prompt/tool/model 版本可重放可回滚可导出，任务绑定真实三元组。

## 验收 + 验证
- [ ] AC-1（FR-1~3）：版本发布 + 任务绑定 + 重放复现。
- [ ] AC-2（FR-4/5）：回滚 + 导出可举证。
- T-GATE：后端真实性门禁全绿。
- B0 验收：★无模型时版本治理不阻断 B0 主链路。

## 完工证据
- 代码 permalink：版本仓 + 重放 + 回滚/导出。
- 测试：版本/绑定/重放/回滚/导出。
- 审计员签字：@<reviewer>（owner ≠ reviewer）。
