# D4 质控改进 · 域简报

> 读卡前置：先读 [核心 CONSTITUTION](../../CONSTITUTION.md)，再读本简报，再读你领的那张卡。页面卡另读 [体验契约](../../EXPERIENCE_CONTRACT.md)。
> 本域所有卡共享的上下文放这里，**不在卡间复制**。冲突裁决：核心 > 本简报 > 卡。
> 准入：D3 域级验收通过后才进 D4（核心 §0 纵向推进）——D4 消费 D3 临床运行数据与 D2 已发布规则/指标。

## 域目标

把**质控评估改进闭环**做成 **B0 真实**：配指标 → 病例评估命中 → 生成问题 → 派整改（责任科室）→ 复核闭环 → 院级驾驶舱可下钻，**全程关模型也真实可跑、可验收**。覆盖**病历内涵质控、医保与病案质控（DRG/DIP）、智能评估与整改、电子病历评级支撑、价值/ROI 看板**。D4 是"看好不好 + 推动改"的域——消费 D3 的临床运行真实数据，不自己造临床数据。

D4 **天然 B0**（铁律 #4）：评估命中先确定性（指标规则命中），AI 知识审核台先做"**审/发**"（人工审核发布），**AI 生成留第二波**（wave2 AIK-*/KNOWGEN-*）；关模型评估/整改闭环仍可跑（`MODEL_DISABLED`）。

## 现状（搬迁时核查 2026-05-30；后端以 `medkernel-backend` 真实包为准复核）

D4 评估引擎**已实质成型**，本域评估/整改卡多是"真实化/契约化/补全"：

- **评估引擎**＝`engine/evaluation/`：`EvaluationEngineService/Controller` + `EvaluationIndicator{+CreateRequest/Filter/Repository/Status}` + `EvaluationResult{+Filter/Level/Repository/Request}` + `EvaluationEvaluateSnapshotRequest` + `EvaluationIdempotencyKey/Operation`（[API-08](API-08.md)/[EVAL-01](EVAL-01.md) 承接指标/评估/结果）。
- **整改闭环**＝`engine/evaluation/`：`RectificationTask{+Repository/Status}` + `RectificationReview{+Decision/Repository/Request/Response}` + `RectificationDispatchRequest` + `RectificationSubmitRequest` + `RectificationWaiveRequest` + `RectificationReportResponse` + `RectificationController`（[SVC-QUALITY-03](SVC-QUALITY-03.md) 已承接问题→责任科室→整改→复核→豁免→报告）。
- **前端 6 页面已存在待真实化**（`frontend/src/pages/quality/`，`app/router.tsx` 真实路由）：`QcDashboard`→`/qc/dashboard` · `QcAlerts`→`/qc/alerts` · `InsuranceAudit`→`/qc/insurance` · `QcEvalSets`→`/qc/eval/sets` · `QcEvalResults`→`/qc/eval/results` · `AiReview`→`/aik/review`；现状＝页面壳已存在，页面卡＝去占位/mock + 接真实评估/整改 API + 六态/五维 RBAC/可下钻齐全。
- **已成型补充**：价值/ROI 口径（[OPT-08](OPT-08.md)）已由真实关系库事实复算；质控驾驶舱服务包（[SVC-QUALITY-01](SVC-QUALITY-01.md)）已消费评估/整改/价值事实形成院级聚合、热力、下钻证据与预警 read-model；病案医保服务包（[SVC-QUALITY-02](SVC-QUALITY-02.md)）已提供病案内涵、DRG/DIP 入组核对与医保审核后端 B0 接口，读取真实病案快照与 `mk_clinical_claim` 结算事实，无事实时诚实 `INSUFFICIENT_DATA`；整改闭环服务包（[SVC-QUALITY-03](SVC-QUALITY-03.md)）已提供派发、提交、复核、豁免和报告后端 B0 接口；电子病历评级目标映射（[EMR-LEVEL-01](EMR-LEVEL-01.md)）已提供目标、差距、进度后端 B0 接口，缺证据不计满足并联动真实整改任务。
- **明确缺口**（建卡"现状"段照实写、勿夸大）：评级数据质量和证据包（[EMR-LEVEL-02](EMR-LEVEL-02.md)）仍待实现；`InsuranceAudit.tsx` 仍由页卡 [INSAUDIT-01](INSAUDIT-01.md) 接真实医保审核 API；6 个 D4 页面仍待真实化。

## 登入 / 使用角色（13 角色矩阵本域子集，全量见 [质量基线 §9](../../audit/质量基线.md)）

| 角色 | 在 D4 主要干什么 |
|---|---|
| 质控办 quality | 配指标、看评估结果、派整改、复核闭环（D4 主驾驶） |
| 医务处 medical-admin | 质控规则/指标审核、整改督办、院级质控总览 |
| 病案室 medical-records | 病历内涵质控、首页编码、DRG/DIP 入组核对 |
| 医保办 insurance | 医保智能审核、违规问题、费用合规 |
| 科主任 dept-head | 本科整改任务、科室质控指标 |
| 院长 / 院领导 president | 院级质控驾驶舱、价值/ROI、评级目标进度（只读下钻） |
| 信息科 it-ops | 电子病历评级数据质量与证据包支撑 |

> 角色 → 默认视图与可发布范围由各页五维 RBAC（[BASE-02](../D0/BASE-02.md)/[INFRA-05](../D0/INFRA-05.md)）+ 组织作用域（[BASE-01](../D0/BASE-01.md)）执行；**前端不写死角色逻辑、不前端造质控数/假指标**。

## 共享数据模型 / 实体（D4 卡共用，单一归属在此声明、卡内只引用）

- **评估指标（EvaluationIndicator）**：指标模型/配置/状态的单一归属在 [EVAL-01](EVAL-01.md)；[API-08](API-08.md) 暴露契约、页 [EVALSET-01](EVALSET-01.md) 消费。
- **评估结果（EvaluationResult）**：命中结果/级别的单一归属在 [EVAL-01](EVAL-01.md)；页 [EVALRES-01](EVALRES-01.md) 消费。
- **整改闭环（RectificationTask/Review）**：问题→整改→复核→豁免的单一归属在 [SVC-QUALITY-03](SVC-QUALITY-03.md)；[MED-C3](../D3/MED-C3.md) 安全复核任务亦汇入。
- **病案医保审核（CaseReview/DrgGrouping/InsuranceIssue）**：病案内涵、DRG/DIP 入组核对、医保问题事实的单一归属在 [SVC-QUALITY-02](SVC-QUALITY-02.md)；页 [INSAUDIT-01](INSAUDIT-01.md) 消费。
- **价值/ROI 指标**：采纳率/误报漏报/整改闭环率/医保违规减少的口径单一归属 [OPT-08](OPT-08.md)；驾驶舱 [SVC-QUALITY-01](SVC-QUALITY-01.md) 消费。
- **电子病历评级**：评级目标/能力差距/证据包的单一归属 [EMR-LEVEL-01](EMR-LEVEL-01.md)/[EMR-LEVEL-02](EMR-LEVEL-02.md)。
- **消费 D3 运行数据**：评估命中读 D3 临床运行（推荐采纳 [CDSS-01](../D3/CDSS-01.md)、路径完成 [SVC-CLINICAL-01](../D3/SVC-CLINICAL-01.md)、随访 [FOLLOW-01](../D3/FOLLOW-01.md)），不重造临床数据。
- **版本与权威**：质控规则/指标版本复用 D2 [SYS-04](../D2/SYS-04.md)/[SYS-08](../D2/SYS-08.md)；质控规则来自 [RULE-01](../D2/RULE-01.md)。
- **API 统一输入**：所有 D4 API 复用 [BASE-03](../D0/BASE-03.md) `ApiResult`/`ProblemDetail`/Record DTO + §1.4 12 字段；大列表复用 [API-13](../D0/API-13.md)。

> **权威源铁律**：唯一权威源是院内关系库（核心 §7 / 铁律 #5）；图/缓存/搜索只能是投影。

## 依赖

- **上游（D3 运行 + D2 料 + D0 脊柱）**：D3 临床运行数据（[CDSS-01](../D3/CDSS-01.md)/[SVC-CLINICAL-01](../D3/SVC-CLINICAL-01.md)/[FOLLOW-01](../D3/FOLLOW-01.md)）· D2 规则/指标（[RULE-01](../D2/RULE-01.md)/[KNOW-01](../D2/KNOW-01.md)）· [BASE-01](../D0/BASE-01.md) OrgContext · [BASE-02](../D0/BASE-02.md)/[INFRA-05](../D0/INFRA-05.md) RBAC · [BASE-03](../D0/BASE-03.md) API · [BASE-04](../D0/BASE-04.md) 审计 · [BASE-05](../D0/BASE-05.md) 方言 · [BASE-08](../D0/BASE-08.md)/[BASE-10](../D0/BASE-10.md) 前端体验/token · [API-13](../D0/API-13.md) 大列表。
- **下游（D5+）**：D5 合规消费质控证据/审计；GA 验收消费 ROI/评级。

## 本域最烫的不变量（点核心编号 + 为何在本域最关键）

- **核心 §13 真实性 + 铁律 #1/#2**：质控直接影响考核/医保——指标命中/问题/采纳率/违规必须真实来自引擎，**绝不前端造质控数、不写死指标、不假闭环率**。
- **核心 §11 B0 先于模型**：评估命中先确定性；AI 知识审核台只做审/发，AI 生成 wave2；关模型 `MODEL_DISABLED` 仍闭环。
- **核心 §5 状态机 + 闭环**：问题→整改→复核→豁免走统一待办/变更状态机，闭环可审计、不可静默关闭。
- **核心 §9 多租户作用域**：质控指标/驾驶舱按集团/院/科继承下钻；安全质控红线下级不可关。
- **病历内涵/医保合规**：DRG/DIP 入组、编码、费用问题可追溯到病历证据，不臆造违规。

## 域级验收（D4-验收）

D4 全部卡（8 ID + 6 页面）`done` 后过域级验收（[质量基线 §2.3](../../audit/质量基线.md)）：

1. 质控办/病案室/医保办/科主任逐角色登入 → 6 个二级菜单页按五维 RBAC 正确呈现、六态齐全、驾驶舱可下钻；
2. 跑通 D4 B0 主链路 E2E（**全程关模型**）：配指标 → 对 D3 真实病例评估命中 → 生成问题 → 派整改到责任科室 → 科室整改提交 → 质控复核闭环（或豁免）→ 院级驾驶舱下钻看到该问题闭环；每步状态机正确、证据可导出；
3. 医保/病案：DRG/DIP 入组 + 编码 + 费用问题可追溯病历证据，医保审核违规有据；
4. 评级支撑：电子病历评级目标映射 + 数据质量 + 证据包可导出；
5. 关闭模型/Dify/图投影 → D4 评估/整改闭环仍真实通过（`B0`/`MODEL_DISABLED`，无伪造质控数）；AI 知识审核台仅审/发可用；
6. T-GATE 前后端真实性门禁全绿（无 Math.random 造数/无写死指标/无假闭环率/无绕 no-page-mock）；owner ≠ reviewer 签字。

**域级验收过，才算 D4 落实，才走 D5。** AI 生成不在 D4（第二波）。
