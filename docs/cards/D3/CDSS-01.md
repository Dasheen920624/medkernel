# CDSS-01 · 推荐 / CDSS 引擎

> 读卡前置：[核心 CONSTITUTION](../../CONSTITUTION.md) + [D3 域简报](_brief.md)。
> 迁移来源（覆盖矩阵锚点）：详规 S8 临床嵌入运行 · S16 辅助诊疗 · 核心 §11 B0 先于模型 · 体验规范 §3 低打扰。

## 身份
- 卡 ID：CDSS-01（引擎卡；`RecommendationCard` 模型/状态/反馈/疲劳单一归属）
- 域：D3 临床运行
- 关联场景：S8 临床嵌入运行 · S16 辅助诊疗与鉴别诊断
- 依赖卡：[RULE-01](../D2/RULE-01.md) 规则引擎 · [PATH-01](../D2/PATH-01.md) 路径引擎 · [KNOW-01](../D2/KNOW-01.md) 知识 · [API-01](../D2/API-01.md) 上下文 · [SYS-08](../D2/SYS-08.md) 权威版本 · [OPT-04](OPT-04.md) 红线 · [API-07](API-07.md) 对外契约
- 工作量：5d
- owner / reviewer：待派单（owner ≠ reviewer）

## 目标
把 CDSS 做成 **B0 真实**：综合 D2 已发布**规则/路径/知识**对临床上下文做确定性命中 → 产出推荐卡（**可解释追溯到来源版本**）→ 接收医师反馈（采纳/不采纳带原因）→ 疲劳治理低打扰。**不写死医学常量、不前端造卡、关模型仍可跑**。

## 现状（搬迁时核查 2026-05-30，以 `medkernel-backend` 为准）
已有实质基础：`engine/recommendation/` 下 `RecommendationCard{+DetailResponse/Filter/Repository/Request/Status/Type}` + `RecommendationEngineController`。本卡＝把"综合命中（规则+路径+知识）+ 解释追溯 + 反馈 + 疲劳治理"框架化为引擎核心；命中复用 D2 `RuleDslEvaluator`/路径/知识，非从零。

## 功能要求（原子可测条目）
- [x] FR-1 综合命中：对临床上下文（[API-01](../D2/API-01.md)）跑规则（[RULE-01](../D2/RULE-01.md)）+ 路径节点（[PATH-01](../D2/PATH-01.md)）+ 知识（[KNOW-01](../D2/KNOW-01.md)）产出推荐卡。
- [x] FR-2 解释追溯：每张卡记录命中的规则/路径/知识 **ID + 版本**，可回链来源。
- [x] FR-3 反馈闭环：采纳/不采纳带**结构化原因**，回写影响疲劳与统计。
- [x] FR-4 疲劳治理：同患者重复/低价值卡按阈值抑制，抑制可解释、可审计、阈值按科室可配。
- [x] FR-5 红线优先：命中 [OPT-04](OPT-04.md) 红线（DDI/危急值/禁忌）的卡强制高优先、不可被疲劳抑制。
- [x] FR-6 B0 降级：模型语义增强为挂点，关闭只用确定性命中 + `MODEL_DISABLED`。

## 接口契约 / 页面契约
### 接口契约（引擎/API 卡）
- 对外端点契约归 [API-07](API-07.md)；本卡负责引擎内 `RecommendationEngineService` 命中/解释/疲劳算法 + `RecommendationCardStatus` 状态机（待处理→已采纳/已拒绝/已抑制）。
- 幂等 / traceId：同上下文+版本命中可复现；命中链路 trace（[OBS-01](../D0/OBS-01.md)）。

## 数据与迁移
- 表族：`recommendation_card`（卡 + 命中来源版本数组 + 状态 + 组织字段 + 审计）+ 反馈表 + 疲劳抑制表；五方言（[BASE-05](../D0/BASE-05.md)）
- 唯一约束：同患者+触发点+来源版本去重；索引：患者/状态/科室

## 视角清单（11 视角逐条）
1. 产品架构：临床决策支持核心引擎，消费 D2 全部料。
2. 产品体验：低打扰、可解释（页面 [REMIND-01](REMIND-01.md)）。
3. 系统与数据架构：单次命中 P95 ≤1s；疲劳抑制基于历史窗口；10万患者级。
4. 临床医疗安全：★推荐非自动执行；红线强优先；不采纳带原因；命中只用 `ACTIVE` 版本。
5. 知识与数据治理：★每卡可追溯到规则/知识版本（[SYS-08](../D2/SYS-08.md)）；旧版仅历史重放。
6. 安全合规与监管：反馈/抑制/命中留审计（[BASE-04](../D0/BASE-04.md)）。
7. 集团化与多租户治理：疲劳阈值/推荐范围按 `OrgContext`/科室继承。
8. 集成与互操作：经 [API-07](API-07.md)/[EMBED-01](EMBED-01.md) 输出；触发点 [OPT-02](OPT-02.md)。
9. 运维 / SRE / 国产化：命中可观测、可回放复现。
10. 质量与真实性审计：★无写死医学常量、无假采纳率、无前端造卡；命中可复现。
11. AI / 模型治理与可降级：★B0 确定性优先；模型为增强挂点，关闭 `MODEL_DISABLED` 不降可用性。

## 适用不变量
- 命中核心约束：**核心 §11 B0 先于模型** · **§13 真实性/低打扰** · **§6 旧版隔离** · **§7 唯一权威** · **铁律 #1/#2**。
- 本卡落点：确定性综合命中 + 可解释 + 反馈 + 疲劳治理，红线（[OPT-04](OPT-04.md)）不可被抑制。

## 验收 + 验证
- [x] AC-1（FR-1/2）：命中产出卡且可追溯到规则/路径/知识版本。
- [x] AC-2（FR-3/4）：反馈带原因回写疲劳；抑制可解释可审计。
- [x] AC-3（FR-5/6）：红线卡强优先不被抑制；关模型确定性命中仍跑。
- 关联 A1–A9 剧本：A5 推荐 · A6 鉴别诊断。
- T-GATE：前后端真实性门禁全绿（无写死医学常量 / 无假命中）。
- B0 验收：★关模型/Dify/图投影，确定性命中 + 解释 + 反馈全可用。

## 大卡工序（5d）
- PR1：引擎综合命中 + 解释追溯 + 状态机 + 门禁 → 验收
- PR2：反馈闭环 + 疲劳治理 + 红线优先 → 验收
- PR3：B0 降级挂点 + 复现/回放测试 → 验收

### PR1 实施记录（2026-06-04）
- 本轮只收口 PR1 的确定性主链路，不冒领整卡：新增 `RecommendationDeterministicMatcher`，从 API-01 标准上下文快照读取资源，遍历当前租户已发布规则及 active 发布版本，用 `RuleDslEvaluator` 复用 D2 规则 DSL 命中逻辑生成非 AI 推荐候选。
- 推荐卡解释中记录 `matchType=RULE`、触发编码、场景、上下文快照、规则 ID / 编码 / 版本 ID / 版本号 / 来源引用，并透传规则解释的 `conditionEvidence`；来源表落 `RULE` + `CONTEXT`，存在患者在径实例时追加 `PATHWAY` 来源和模板版本 / 当前节点定位。
- `RecommendationEngineService.evaluate` 先聚合确定性命中，再合并调用方提交的非 AI 候选，按卡编码去重后进入既有状态机、来源落库、疲劳信号和 `MODEL_DISABLED` 诚实返回；AI 候选只计入总数，不落库成 B0 卡。
- 临床事件到推荐的适配器改走 `evaluate`，避免临床事件只创建空触发而不执行确定性推荐。
- 未在 PR1 冒领：知识全文/语义命中、反馈闭环、疲劳阈值配置、红线优先和复现/回放仍归 PR2/PR3；完整 AC-1 已在 PR3 通过 ACTIVE 知识版本来源链补齐。

### PR2 实施记录（2026-06-04）
- 本轮收口反馈闭环、疲劳治理和 CDSS 层红线优先，不冒领完整 OPT-04 红线库：`RecommendationEngineService` 继续在反馈后回写疲劳信号，并把 `ACCEPT` / `REJECT` / `DISMISS` 都收紧为必须携带结构化原因代码和说明，避免“关闭忽略”变成不可解释的低价值信号。
- 新增 `RecommendationFatiguePolicyResolver` 与 `RecommendationFatiguePolicy`，疲劳抑制优先读取配置中心键 `medkernel.cdss.fatigue.policy`，支持 `departmentScenarios`（科室+场景）＞ `departments` ＞ `scenarios` ＞ `default` 的覆盖顺序；配置缺失时只兼容旧请求阈值，配置存在但非法时安全不抑制，避免错误配置静默压掉临床提醒。
- `SystemConfigSeeder` 播种默认空 JSON 策略，配置中心可展示和审计该键，但默认 `{}` 不开启自动抑制；低/中风险只有在解析到正整数阈值与回看窗口时才统计低价值信号并落 `SUPPRESSED`。
- `RecommendationRiskLevel.HIGH` / `CRITICAL` 在疲劳策略解析与历史低价值计数前直接返回不可抑制，CRITICAL + 强打断 + 医师确认作为 CDSS 层红线级推荐卡验证；完整 DDI / 危急值 / 禁忌红线资产库仍归 [OPT-04](OPT-04.md) 后续卡，不在本 PR 伪造。
- 已跑证据：`mvn -q -Dtest=RecommendationDeterministicMatcherTest,RecommendationFatiguePolicyResolverTest,RecommendationEngineServiceTest,RecommendationEngineControllerSecurityTest,RecommendationRepositoryTest,ClinicalEventEngineAdapterTest test` 通过；`mvn -q -Dtest=SystemConfigServiceTest,SystemConfigControllerTest test` 通过；最终代码清理后复跑后端全量 `mvn -q test` 192 reports / 1177 tests / 0 failures / 0 errors / 0 skipped（本机 Docker PostgreSQL 15.18 与 Oracle 21.3 迁移冒烟均运行至 V70 并二次 no-op）；前端 `npm run verify` 51 files / 311 tests 通过（既有 React Router/act warning 仍归 [DEFER-003](../../audit/deferred-issues.md)）；T-GATE 脚本自测 34 项、提交后 changed-mode 真实性扫描 7 文件 / 配置边界 7 文件 / 迁移 0 文件、中文注释和 diff 检查通过。完整 AC-3 和 B0 回放仍归 PR3。

### PR3 实施记录（2026-06-04）
- 本轮收口 B0 降级挂点、知识来源解释链与复现/回放口径：`RecommendationDeterministicMatcher` 继续以规则 DSL 为医学判断入口，不写死医学常量；当规则版本 `sourceRef` 使用 `knowledge:<identityCode>` 时，按当前租户覆盖优先、平台主租户 `t-1` 回退的口径解析 ACTIVE 知识身份与版本，并把 `KNOWLEDGE` 来源写入推荐卡来源列表。
- 推荐卡解释 JSON 新增 `knowledgeIdentityId`、`knowledgeIdentityCode`、`knowledgeSourceTenantId`、`knowledgeVersionId`、`knowledgeVersionNo` 与内容 hash；来源链保持 `RULE` + `KNOWLEDGE` + `CONTEXT`，有患者在径实例时继续追加 `PATHWAY` 来源，满足同一命中可追溯到规则 / 知识 / 路径版本。
- `RecommendationDeterministicMatcher` 补齐与 D2 一致的平台主源继承：客户租户没有本地规则或知识覆盖时，消费平台已发布规则与 ACTIVE 知识；客户租户有同编码覆盖时优先使用本地版本，避免漏掉首发平台资产。
- `RecommendationEngineService.evaluate` 在 `MODEL_DISABLED` 下只统计真实确定性候选；AI 生成候选继续过滤且不落库、不计入 `totalCardCount`，避免 B0 响应把不可用模型候选展示成真实推荐数量。
- PR3 新增红绿证据：知识来源测试先因命中器构造器缺少知识仓储失败；B0 计数测试先因 AI 候选仍计入总数失败；实现后 `mvn -q -Dtest=RecommendationDeterministicMatcherTest test`、`mvn -q -Dtest=RecommendationEngineServiceTest test`、`mvn -q -Dtest=RecommendationDeterministicMatcherTest,RecommendationFatiguePolicyResolverTest,RecommendationEngineServiceTest,RecommendationEngineControllerSecurityTest,RecommendationRepositoryTest,ClinicalEventEngineAdapterTest test` 均通过。
- PR3 本地收口证据：后端全量 `mvn -q test` 192 reports / 1179 tests / 0 failures / 0 errors / 0 skipped，H2 / PostgreSQL 15.18 / Oracle 21.3 均迁移至 V70 并二次 no-op；前端先因新 worktree 未安装依赖出现 `eslint: command not found`，执行 `npm ci --prefer-offline --no-audit` 后 `npm run verify` 51 files / 311 tests 通过（既有 React Router / act warning 仍归 [DEFER-003](../../audit/deferred-issues.md)）；T-GATE 脚本自测 34 项通过，真实性全量扫描 953 文件、配置边界 inventory 扫描 891 文件、迁移 files-mode 扫 0 文件、中文注释 0 fail / 0 warn、`git diff --check` 均通过；提交后 changed-mode 真实性扫描 2 文件、配置边界扫描 2 文件、迁移扫描 0 文件，中文注释和 `git diff --check origin/main..HEAD` 通过。完整 DDI / 危急值 / 禁忌红线资产库仍归 [OPT-04](OPT-04.md)，本卡只完成 CDSS 层强优先、不可疲劳抑制和 B0 推荐主链路。

## 完工证据
- 代码 permalink：`engine/recommendation` 命中/解释/疲劳 + B0 降级。
- 测试：命中复现 / 追溯 / 反馈 / 疲劳 / 红线优先 / 关模型 B0。
- 审计员签字：@<reviewer>（owner ≠ reviewer）。
