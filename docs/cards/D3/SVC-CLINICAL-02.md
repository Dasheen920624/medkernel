# SVC-CLINICAL-02 · 临床提醒与反馈服务包

> 读卡前置：[核心 CONSTITUTION](../../CONSTITUTION.md) + [D3 域简报](_brief.md)。
> 迁移来源（覆盖矩阵锚点）：详规 S8 临床嵌入运行 · 体验规范 §3 低打扰 · 详规 §1.4 反馈署名。

## 身份
- 卡 ID：SVC-CLINICAL-02（服务包卡）
- 域：D3 临床运行
- 关联场景：S8 临床嵌入运行
- 依赖卡：[CDSS-01](CDSS-01.md) 推荐引擎 · [RULE-01](../D2/RULE-01.md) 规则 · [OPT-04](OPT-04.md) 红线 · [API-07](API-07.md) 推荐契约 · 页 [REMIND-01](REMIND-01.md)/[RULECHK-01](RULECHK-01.md)
- 工作量：4d
- owner / reviewer：待派单（owner ≠ reviewer）

## 目标
把**提醒卡片 + 规则校验 + 疲劳治理 + 真实医师署名反馈**编排为服务包：医师收到确定性提醒 → 规则校验结果可见 → 采纳/拒绝带原因且**带真实署名**留痕 → 疲劳治理低打扰。

## 现状（搬迁时核查 2026-05-30，以 `medkernel-backend` 为准）
已有实质基础：`engine/recommendation/`（推荐卡 + 反馈，[CDSS-01](CDSS-01.md) 归属）+ `engine/rule/` `RuleDslEvaluator`（规则校验，[RULE-01](../D2/RULE-01.md) 归属）。本卡＝把提醒呈现 + 规则校验结果 + 疲劳治理 + 署名反馈编排为前端可消费的服务包契约，命中归 [CDSS-01](CDSS-01.md)、规则归 [RULE-01](../D2/RULE-01.md)。

## 功能要求（原子可测条目）
- [x] FR-1 提醒卡片：按患者/科室取确定性提醒卡（[CDSS-01](CDSS-01.md)），含解释。PR1 新增 `/clinical-cards` 聚合 DTO，补齐患者 / 就诊 / 路径 / 场景 / 触发点。
- [x] FR-2 规则校验：对医嘱/病历跑规则校验（[RULE-01](../D2/RULE-01.md) `RuleDslEvaluator`），结果可见可解释。PR1 修正前端规则校验页读取真实 `ruleId/versionId/actions/explanation`。
- [x] FR-3 署名反馈：采纳/拒绝带**原因 + 真实医师署名**（来自 [BASE-01](../D0/BASE-01.md) 身份），不可匿名伪造。PR1 明确前端不提交 `operatorId`，详情展示后端反馈历史 `operatorId/operatorRole`。
- [x] FR-4 疲劳治理：按阈值抑制重复/低价值提醒；红线（[OPT-04](OPT-04.md)）不可抑制。PR1 保留既有红线不可抑制服务校验，并把疲劳信号分页 SQL 改为 PostgreSQL + Oracle 兼容。
- [x] FR-5 采纳率统计：采纳/拒绝真实统计，供 D4 质控只读消费。PR1 新增 `/stats`，采纳率来自持久化推荐卡状态，并确保显式 `status` 筛选时总数与状态桶口径一致。

## 接口契约 / 页面契约
### 接口契约（引擎/API 卡）
- 复用 [API-07](API-07.md) 推荐契约 + 规则校验端点 `POST /api/v1/engine/rule/rules/evaluate`；本卡为编排，不另立模型。
- PR1 增量只读接口：`GET /api/v1/engine/recommendations/clinical-cards`、`GET /api/v1/engine/recommendations/stats`，权限均为 `recommendation.read`。
- 状态机：告警类（待处理→已采纳/已拒绝/已抑制）；署名反馈幂等。

## 数据与迁移
- 复用推荐卡/反馈表（[CDSS-01](CDSS-01.md)）+ 规则校验记录；署名取真实用户，不落明文密钥。

## 视角清单（11 视角逐条）
1. 产品架构：医师侧"提醒 + 校验 + 反馈"的服务编排。
2. 产品体验：低打扰、可解释、署名清晰（页 [REMIND-01](REMIND-01.md)/[RULECHK-01](RULECHK-01.md)）。
3. 系统与数据架构：提醒/校验 P95 ≤1s；疲劳窗口查询高效。
4. 临床医疗安全：★校验/提醒只建议不自动执行；红线强优先；署名可追责。
5. 知识与数据治理：提醒/校验追溯到规则/知识版本（[SYS-08](../D2/SYS-08.md)）。
6. 安全合规与监管：★反馈带真实署名 + 原因，全留审计（[BASE-04](../D0/BASE-04.md)）。
7. 集团化与多租户治理：提醒/阈值按科室作用域。
8. 集成与互操作：经 [API-07](API-07.md)/[EMBED-01](EMBED-01.md) 输出第三方。
9. 运维 / SRE / 国产化：提醒/校验可观测。
10. 质量与真实性审计：★无前端造提醒、无匿名伪造署名、采纳率真实。
11. AI / 模型治理与可降级：关模型用确定性提醒/校验 `MODEL_DISABLED`。

## 适用不变量
- 命中核心约束：**铁律 #1 真实性** · **核心 §13 低打扰** · **§临床安全（红线优先）** · **§6 安全（署名可追责）**。
- 本卡落点：提醒+校验+疲劳+署名反馈编排，命中归 [CDSS-01](CDSS-01.md)、规则归 [RULE-01](../D2/RULE-01.md)。

## 验收 + 验证
- [x] AC-1（FR-1/2）：提醒/规则校验真实可解释。
- [x] AC-2（FR-3/4）：反馈带原因+真实署名；疲劳抑制且红线不可抑制。
- [x] AC-3（FR-5）：采纳率统计真实。
- 关联 A1–A9 剧本：A5 提醒反馈。
- T-GATE：前后端真实性门禁全绿（无造提醒 / 署名真实）。
- B0 验收：关模型提醒/校验/反馈全可用。

## 完工证据
- 代码 permalink：PR1 引入 `RecommendationClinicalCardResponse` / `RecommendationStatsResponse`、推荐详情 trigger 上下文、`CdssFatigue` 与 `RuleValidate` 真实契约修复。
- 测试：`mvn -q -Dtest=RecommendationEngineServiceTest,RecommendationRepositoryTest,RecommendationRepositorySqlContractTest,RecommendationEngineControllerSecurityTest test`；`mvn -q test`（Docker Testcontainers PostgreSQL 15.18 + Oracle 21.3 迁移至 V76）；`npm test -- src/pages/clinical/CdssFatigue.test.tsx src/pages/clinical/RuleValidate.test.tsx`；`npm run verify`（57 文件 / 323 测试）；`npm run build`；`npm audit --omit=dev --audit-level=moderate`（0 vulnerabilities）。本 PR 额外覆盖 `recommendationStatsRespectExplicitStatusFilter`，防止状态筛选下统计桶跨状态误计。
- T-GATE：`node scripts/authenticity-guard.mjs --mode=all`（1007 文件）；`node scripts/config-boundary-guard.mjs --mode=inventory`（945 文件）；`node scripts/migration-convention-guard.mjs --mode=files $(git diff --cached --name-only)`（本 PR 无迁移文件）；`bash scripts/check-comment-zh.sh`；`git diff --cached --check`。历史迁移 inventory 债务仍归 `DEFER-016`，不得冒领清零。
- 审计员签字：@<reviewer>（owner ≠ reviewer）。
