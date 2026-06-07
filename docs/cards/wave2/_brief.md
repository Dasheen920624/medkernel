# 第二波 wave2 · AI 加深 · 域简报

> 读卡前置：先读 [核心 CONSTITUTION](../../CONSTITUTION.md)，再读本简报，再读你领的那张卡。页面卡另读 [体验契约](../../EXPERIENCE_CONTRACT.md)。
> 本域所有卡共享的上下文放这里，**不在卡间复制**。冲突裁决：核心 > 本简报 > 卡。
> 准入：D0–D6 全部域级验收通过后才进 wave2（核心 §0 纵向推进 + 铁律 #4 B0 先于模型）。wave2 **分 4 子块、多 PR 增量交付**（卡同放 `cards/wave2/`）。

## 域目标

在 D0–D6 的 **B0 真实基线**上**叠加模型/AI 增强**：跨域共享深引擎 + 领域门面，回灌相关域。复用同一**资产治理 / 状态机 / 发布 / 证据**链路，不另起业务实现。四子块：

- **X-LLM 模型网关与真实接入**（[API-12](API-12.md) · LLM-01~08 · [OPT-06](OPT-06.md)/[OPT-09](OPT-09.md)）：provider 无关网关 + 真实接入 + 降级矩阵 + 数据最小化 + 评测。
- **X-AIK AI 工厂**（AIK-STD-01~12）：来源→解析→候选生成→安全校验→静默评测→包同步→替换处置的知识自动生产线（批 2）。
- **X-KNOWGEN 首发知识资产**（KNOWGEN-01~15）：14 类首发资产 →「试点医院首发知识包 v1.0」（批 3）。
- **X-DOMAIN 15 领域门面**（NURSING/REPORT/…/SVC-DOMAIN-01/02）：领域专精组合，复用同一引擎链路（批 4）。

## 现状（搬迁时核查 2026-05-31；后端以 `medkernel-backend` 真实包为准复核）

- **模型网关已有 MVP**＝`engine/llm/`：关系库能力目录 `model_capability_definition` + 租户策略 `model_capability_policy` + 任务 `model_capability_task`，五方言由 `V18__model_gateway_api.sql` 统一创建并种入 8 项初始能力。端点 `/api/v1/model-capabilities/{status,catalog,catalog/{capabilityCode},tasks,tasks/{id},tasks/{id}/retry,policies/validate,policies/{capabilityCode}}`；**B0 诚实回退**（无 provider 一律降级 B0，不伪造 B2 模型名/置信度/引文）+ 正则脱敏 + JSON Schema 校验 + 审计；权限分离为 `llm.read`/`llm.execute`/`llm.manage`，全局目录维护另需 `system.manage`。
- **明确缺口（建卡「现状」段照实写、勿夸大）**：真实 provider 接入（B1/B2/Dify，[LLM-08]）、故障切换矩阵（[LLM-02]）、数据最小化外调安全（[LLM-03]/[OPT-09]）、版本治理（[LLM-04]）、来源探索编排（[LLM-06]）、医学回归评测（[LLM-07]/[OPT-06]）、增强接入矩阵（[LLM-05]）；AI 工厂全线（AIK-STD-*，知识自动生成仅约 10-15%）、首发资产（KNOWGEN-*，未生产）、领域门面（X-DOMAIN，未建）。AI 生成依赖的知识/审核台**壳已在 D2/D4 建**（[KNOW-01](../D2/KNOW-01.md)/[KNOW-02](../D2/KNOW-02.md)/[AIREVIEW-01](../D4/AIREVIEW-01.md)）。

## 共享数据模型 / 实体（wave2 卡共用，单一归属在此声明、卡内只引用）

- **能力策略 / 任务**：`model_capability_policy` / `model_capability_task` 单一归属 [LLM-01](LLM-01.md)/[API-12](API-12.md)；其余卡复用、不重造。
- **知识资产 / 版本**：单一归属 D2 [KNOW-01](../D2/KNOW-01.md)/[KNOW-02](../D2/KNOW-02.md)/[SYS-08](../D2/SYS-08.md)；AIK/KNOWGEN 只**生产候选** + 走既有审核/原子替换链，不另起资产表。
- **审核台**：`AiReview` 单一归属 D4 [AIREVIEW-01](../D4/AIREVIEW-01.md)（人工审/发）；AIK-STD-12 补「AI 生成」接入同一台。
- **来源分级**：单一归属 D2 [OPT-07](../D2/OPT-07.md)；AIK/KNOWGEN 引用。
- **API 统一输入**：复用 [BASE-03](../D0/BASE-03.md) `ApiResult`/`ProblemDetail`；大列表复用 [API-13](../D0/API-13.md)。

## 登入 / 使用角色（13 角色矩阵 wave2 子集，全量见 [质量基线 §9](../../audit/质量基线.md)）

知识工程师 / 医学专家审核生成候选；信息科 / 运维配模型策略与 provider；架构师 / 开发者看网关与 AI 工作流。角色 → 默认视图与可操作范围由 [BASE-02](../D0/BASE-02.md)/[INFRA-05](../D0/INFRA-05.md) + [BASE-01](../D0/BASE-01.md) OrgContext 执行。

## 依赖

- **上游**：D0 脊柱（[BASE-01](../D0/BASE-01.md)/[BASE-02](../D0/BASE-02.md)/[BASE-03](../D0/BASE-03.md)/[BASE-04](../D0/BASE-04.md) · [API-13](../D0/API-13.md) · [SYS-03](../D0/SYS-03.md)）· D2 知识/字典/规则/路径/包（[KNOW-01](../D2/KNOW-01.md)/[KNOW-02](../D2/KNOW-02.md)/[TERM-01](../D2/TERM-01.md)/[RULE-01](../D2/RULE-01.md)/[PATH-01](../D2/PATH-01.md)/[PKG-01](../D2/PKG-01.md)/[SYS-08](../D2/SYS-08.md)/[OPT-07](../D2/OPT-07.md)）· D3 CDSS/随访（[CDSS-01](../D3/CDSS-01.md)/[FOLLOW-01](../D3/FOLLOW-01.md)/[OPT-04](../D3/OPT-04.md)）· D4 评估/审核台（[EVAL-01](../D4/EVAL-01.md)/[AIREVIEW-01](../D4/AIREVIEW-01.md)）· D6 AI 工作流壳（[AIFLOW-01](../D6/AIFLOW-01.md)）。
- **下游**：GA 门禁 3（AI 工厂）/ 8（领域门面）/ 10（KNOWGEN-15 首发包）——**GA 实际 pass 待 wave2 建成**（[GA 域简报](../ga/_brief.md)）。

## 本域最烫的不变量（点核心编号 + 为何在本域最关键）

- **铁律 #4 B0 先于模型**：★每个 AI 增强必须先有「无模型确定性 + 人工」路径再叠加模型；关模型主链路仍可跑（wave2 整体后置于 D0–D6 B0 即此故）。
- **铁律 #1 真实性**：无 provider 诚实降级 B0，**禁伪造模型名/置信度/来源引文**；评测集/候选不写死。
- **铁律 #5 关系库权威 + #6 唯一权威知识**：AI 只产**候选**，入库走既有版本/审核/原子替换链（[SYS-08](../D2/SYS-08.md)）；图/Dify/模型是投影或执行器、非权威。
- **医疗安全（核心 §安全）**：AI 内容明显标识、医师确认才进病历、高危近似禁批量自动确认（详规 §8.9 11 项门禁，[OPT-04](../D3/OPT-04.md) 红线生效）。

## 域级验收（wave2-验收）

四子块全部卡 `done` 后过 wave2 域级验收 = 点亮 **GA 门禁 3/8/10**：AI 工厂无模型可运行组（AIK-STD-01~12）通过、审核台真实可审可发；15 领域门面按核心 §1.#15 完成；KNOWGEN-15 形成「试点医院首发知识包 v1.0」过 A1-A9 + 同步试点。详见 [GA 域简报](../ga/_brief.md) + [backlog GA 门禁](../../backlog.md)。

**域级验收过，wave2 落实 → 连同 D0–D6 一起进 GA 总验收。**
