# REMIND-01 · 临床提醒治理页

> 读卡前置：[核心 CONSTITUTION](../../CONSTITUTION.md) + [D3 域简报](_brief.md) + [体验契约](../../EXPERIENCE_CONTRACT.md)。
> 迁移来源（覆盖矩阵锚点）：详规 S8 临床嵌入运行 · 体验规范 §3 低打扰提醒 · 详规 §提醒治理。
> 实化映射：占位 `D3-PAGE-临床提醒治理` → 本卡 **REMIND-01**。

## 身份
- 卡 ID：REMIND-01（页面卡；= backlog `D3-PAGE-临床提醒治理` 实化）
- 域：D3 临床运行
- 关联场景：S8 临床嵌入运行
- 依赖卡：[SVC-CLINICAL-02](SVC-CLINICAL-02.md)（提醒/反馈/疲劳后端）· [CDSS-01](CDSS-01.md)（命中）· [OPT-04](OPT-04.md)（红线）· [BASE-08](../D0/BASE-08.md)/[BASE-10](../D0/BASE-10.md)（体验/token）· [INFRA-09](../D1/INFRA-09.md)
- 工作量：3d
- owner / reviewer：待派单（owner ≠ reviewer）

## 目标
把临床提醒治理页**真实化**：呈现确定性提醒卡、解释追溯、采纳/拒绝带原因，并治理**疲劳阈值**（低打扰），全部接 [SVC-CLINICAL-02](SVC-CLINICAL-02.md)，**不前端造提醒、不假采纳率**。

## 现状（搬迁时核查 2026-05-30，以 `frontend/src` 为准）
页面**已存在待真实化**：`pages/clinical/CdssFatigue.tsx`（路由 `/cdss/fatigue` 已注册 `app/router.tsx`）。本卡＝去占位/mock + 接提醒/反馈/疲劳阈值 API + 六态/五维 RBAC/低打扰齐全。

## 页面收口增量（2026-06-05，本地目标红绿）
- 已实现：提醒治理页按真实 `RecommendationCard` 和疲劳信号展示科室级阈值、配置中心键 `medkernel.cdss.fatigue.policy`、治理来源、阈值作用域和当前触发负担；红线 / 高风险提醒标注“不可抑制”，不允许被低打扰策略静默。
- 已覆盖：组织数据范围或权限拒绝时展示无权限态，普通读取失败展示错误态；反馈仍只提交原因和动作，不由浏览器传操作者身份。
- RBAC 边界：前端路由具备 `menu.cdss-fatigue` 登录菜单保护，提醒读取、反馈、统计和治理数据范围以后端推荐 / CDSS 控制器权限与 `OrgContext` 为准；本 PR 未新增浏览器端阈值写接口。

## 功能要求（原子可测条目）
- [x] FR-1 提醒列表：按科室/患者列确定性提醒卡（[CDSS-01](CDSS-01.md)），含解释追溯。PR1 改用 `useClinicalRecommendationCards`，列表字段来自后端聚合触发上下文。
- [x] FR-2 署名反馈：采纳/拒绝带原因 + 真实署名（[SVC-CLINICAL-02](SVC-CLINICAL-02.md) FR-3）。PR1 展示反馈历史真实 `operatorId/operatorRole`，提交反馈不携带 `operatorId`。
- [x] FR-3 疲劳治理：科室级疲劳阈值可配可视；红线（[OPT-04](OPT-04.md)）标"不可抑制"。
- [x] FR-4 采纳率：真实采纳/拒绝统计可视（供质控只读）。PR1 接 `/recommendations/stats`，不再由前端假算。
- [x] FR-5 六态 + 五维 RBAC：齐全；仅医生/科主任可治理阈值；数据按 `OrgContext`。

## 接口契约 / 页面契约
### 接口契约（引擎/API 卡）
N·A —— 消费 [SVC-CLINICAL-02](SVC-CLINICAL-02.md) / [API-07](API-07.md) 提醒与疲劳 API；PR1 使用 `/engine/recommendations/clinical-cards`、`/engine/recommendations/stats`、`/engine/recommendations/cards/{cardId}`、`/engine/recommendations/fatigue-signals`。
### 页面契约（页面卡）
- 路由元数据：sectionKey `clinical-run` / menuKey `cdss-fatigue` / menuLabel `临床提醒治理` / path `/cdss/fatigue` / requiredPermissions 提醒治理 / requiredRoles 临床医生·科主任。
- 结构：PageShell（[BASE-08](../D0/BASE-08.md)）+ 提醒卡列表（解释可展开）+ 疲劳阈值面板 + 采纳率图 + 六态。
- 主按钮 ≤1（保存阈值）/ 默认筛选 ≤3 / 默认角色视图（临床医生）。
- 五维 RBAC：菜单 / 动作（治理阈值）/ 数据（org）/ 资产 / 环境。
- 样式：仅引用 [BASE-10](../D0/BASE-10.md) token + [体验契约](../../EXPERIENCE_CONTRACT.md)；禁硬编码 hex/px。

## 数据与迁移
N·A —— 页面卡不落库；消费 [SVC-CLINICAL-02](SVC-CLINICAL-02.md) 后端。

## 视角清单（11 视角逐条）
1. 产品架构：CDSS 提醒的"治理与反馈"页。
2. 产品体验：★低打扰核心页——解释清晰、采纳一键带原因；国产浏览器可读。
3. 系统与数据架构：提醒列表分页 P95 ≤1s；采纳率聚合查询。
4. 临床医疗安全：★红线提醒标"不可抑制"；提醒非自动执行。
5. 知识与数据治理：提醒可追溯规则/知识版本（[SYS-08](../D2/SYS-08.md)）。
6. 安全合规与监管：反馈/阈值变更留审计（[BASE-04](../D0/BASE-04.md)）。
7. 集团化与多租户治理：疲劳阈值按科室作用域；红线不可下级关。
8. 集成与互操作：N·A（页面）。
9. 运维 / SRE / 国产化：内网慢场景骨架。
10. 质量与真实性审计：★无前端造提醒/假采纳率；无演示路由（[INFRA-09](../D1/INFRA-09.md) no-page-mock）。
11. AI / 模型治理与可降级：关模型只显确定性提醒 + `MODEL_DISABLED` 标记。

## 适用不变量
- 命中核心约束：**铁律 #1 真实性** · **核心 §13 低打扰** · **§临床安全（红线不可抑制）** · **§9 多租户作用域**。
- 本卡落点：把提醒治理页变为接真实提醒/反馈/疲劳、红线强可见的治理页。

## 验收 + 验证
- [x] AC-1（FR-1/2）：提醒真实可解释；反馈带原因+署名。
- [x] AC-2（FR-3/4）：疲劳阈值可治理、红线不可抑制；采纳率真实。
- [x] AC-3（FR-5）：六态齐全；非授权不可治理阈值。
- 关联 A1–A9 剧本：A5 提醒反馈。
- T-GATE：前端真实性门禁全绿（no-page-mock、无造提醒、无假采纳率）。
- B0 验收：关模型确定性提醒页仍可用。

## 完工证据
- 代码 permalink：`frontend/src/pages/clinical/CdssFatigue.tsx` 接临床提醒聚合接口、真实统计、反馈历史签名、疲劳信号和红线不可抑制提示；阈值写入仍以后端配置中心治理接口为准。
- 测试：`npm test -- src/pages/clinical/CdssFatigue.test.tsx` 覆盖聚合字段、真实统计、反馈不提交操作者 ID、红线不可抑制 + 配置阈值展示、组织数据范围无权限态；同轮 D3 页面组 8 文件 / 65 用例通过。
- 后端证据：`mvn -q -Dtest=RecommendationEngineServiceTest,RecommendationEngineControllerSecurityTest,RecommendationFatiguePolicyResolverTest,ClinicalRedlineMatcherTest test` 属于本轮后端目标组并退出码 0；红线匹配、疲劳策略和权限以后端测试为准。
- 审计员签字：@<reviewer>（owner ≠ reviewer）。
