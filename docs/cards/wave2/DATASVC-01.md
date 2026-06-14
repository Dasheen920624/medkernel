# DATASVC-01 · 引擎数据服务层 + 产品级 CLI + MCP 服务

> 读卡前置：[核心 CONSTITUTION](../../CONSTITUTION.md) + [wave2 域简报](_brief.md)（§3 接入底座）。
> 迁移来源：`docs/superpowers/specs/2026-05-26-engine-data-service-mcp-cli-clinical-design.md`（方案 B，有完整规范但从未落卡，本卡回收）· 核心 §10 集成边界 · §11 模型治理 · §8 安全合规。

## 身份
- 卡 ID：DATASVC-01（= backlog `DATASVC-01`，wave2 接入底座）
- 域：wave2（X-SVC 引擎数据服务，新建子块）
- 关联场景：S3 AI 知识工厂（Agent 生产底座）· S8 临床嵌入 · S15 AI 验证与验收
- 依赖卡：[KNOW-01](../D2/KNOW-01.md)（知识料源）· [RULE-01](../D2/RULE-01.md)/[EVAL-01](../D4/EVAL-01.md)（规则/质控运行事实）· [LLM-01](LLM-01.md)（模型网关策略）· [BASE-03](../D0/BASE-03.md)（API 信封）· [BASE-04](../D0/BASE-04.md)（审计）· [API-13](../D0/API-13.md)（大列表）· [SYS-03](../D0/SYS-03.md)（关系库权威）
- 工作量：12d（大卡，按 PR 分期，见末段工序）
- owner / reviewer：待派单（owner ≠ reviewer）

## 目标
建**统一医疗智能引擎数据服务层**：把规则/知识/路径/推荐/质控/随访/模型网关/审计的运行事实沉淀为可权限控制、可脱敏、可审计、可解释、可统计的读模型，并对外开**四个入口**——临床端嵌入 / 管理质控端 / **产品级 CLI** / **MCP 服务**——**共用同一后端受控合同**。MCP 同时是 [AIK-STD-14](AIK-STD-14.md) 第三方 Agent 协助生产的技术底座。**CLI/MCP/模型不绕治理**：只调受控工具，不直连库、不读原始病历、不绕身份/权限/脱敏/审计/降级。

## 现状（核查 2026-06-13，以 `medkernel-backend/src` 为准）
**规范有、落卡无**：2026-05-26 方案 B 规范完整（四入口/数据分级 D0–D5/CLI 命令域/MCP 工具/不绕治理/降级矩阵/分期 P0–P4），但从未建卡、从未实现；卡体系内仅 D6 [DEVCON-01](../D6/DEVCON-01.md) 开发者控制台（API 目录/Trace/插件，已 done）≠ 本数据服务层/CLI/MCP。上游运行事实源（规则执行 `rule_execution_log`、知识身份/引用、评估运行、审计 `BASE-04`）已真实存在，可作为读模型上游。本卡＝**从零落地引擎数据服务层 + CLI + MCP，B0 优先、不绕治理**。

## 功能要求（原子可测条目）
- [ ] FR-1 统一数据服务层：规则使用/知识使用/临床信号/质控趋势形成读模型，四入口共用 `/api/v1/engine-data/{rule-usage,knowledge-usage,clinical-signals,tools}/*`；服务端分页 + 默认筛选 + 异步导出 + total 估算 + 降级返回。
- [ ] FR-2 数据分级 D0–D5：每查询按角色 + 用途返回不同字段；**后端脱敏**（前端/CLI/MCP/提示词不承担首要脱敏）；D3/D4 落库字段级加密（索引/日志/导出/审计摘要不含明文）；**D5 重要个人信息禁入数据服务/CLI/MCP/模型输入**。
- [ ] FR-3 产品级 CLI：命令域 `knowledge`/`rules`/`clinical-signals`/`privacy`/`exports`/`diagnostics`；走后端 API 鉴权，**不读本地库连接串、不绕导出审批**。
- [ ] FR-4 MCP 受控工具：`searchKnowledge`/`checkKnowledgeExistence`/`explainRule`/`queryRuleUsage`/`summarizeEngineSignals`/`validatePrivacyPolicy`/`getClinicalContextExplanation`；返回必带 `traceId`/数据级别/脱敏策略/来源版本/权限结果/降级状态；失败返结构化原因，**不暴露内部异常/SQL/原始提示词/敏感字段**；D4 工具须绑临床 launch token + 用途 + 过期 + 能力码 + 组织范围，缺一即降级/无权限。
- [ ] FR-5 不绕治理：CLI/MCP/模型**不直连数据库、不读原始病历、不绕身份/权限/脱敏/审计/降级**；工具清单/权限/脱敏由后端裁决。
- [ ] FR-6 全审计：CLI 登录/工具调用/导出，MCP 调用方/工具/用途/级别/输出 hash，临床端查看依据/采纳/反馈全留审计（[BASE-04](../D0/BASE-04.md)）；只存最小输入摘要 + 输出 hash，不存完整敏感入参。
- [ ] FR-7 降级矩阵：模型不可用→确定性 B0 查询仍可用、增强摘要降级结构化列表；MCP 不可用→临床/管理端不受影响、CLI 走 REST；聚合延迟→显示最新可用时间不伪装实时；上游/权限不足→诚实降级，不以空数据伪装。

## 接口契约 / 页面契约
### 接口契约（引擎/API 卡）
- 端点四组：`/api/v1/engine-data/rule-usage/*`、`/knowledge-usage/*`、`/clinical-signals/*`、`/tools/*`（MCP 与 CLI 共用受控工具执行入口）。
- DTO：Record DTO + Bean Validation；含 D3/D4 字段的 DTO 必须标注字段级加密、脱敏展示值、可检索方式、禁入日志字段清单。
- 响应信封：`ApiResult`/`ProblemDetail`（[BASE-03](../D0/BASE-03.md)）；分页/游标（[API-13](../D0/API-13.md)）；写/导出具幂等键。
- 状态机：配置类（脱敏/数据级别策略）+ 变更类（异步导出任务：待发布→进行→完成/失败，复用导出框架）。
- 错误码：数据级别越权、临床 launch 缺失、导出审批未过、工具不可用，均结构化 + traceId。
### 页面契约（页面卡）
- 临床端只读解释嵌入承载于 D3（[EMBED-01](../D3/EMBED-01.md)/[REMIND-01](../D3/REMIND-01.md)）；管理统计承载于 D4 驾驶舱（[QCDASH-01](../D4/QCDASH-01.md)）。
- **CLI / MCP 控制台 = 技术对象，进专家模式**（挂 D6 [DEVCON-01](../D6/DEVCON-01.md) 壳上，按权限折叠；遵核心 §14 与 §2.0 双产品面，不进客户临床主路径）。

## 数据与迁移
- 读模型：规则使用/知识使用/临床信号/工具调用读模型表（按 `tenant_id`+`org_path`+场景+版本+`trace_id` 索引）；运行事实采集不含非必要明文个人信息。
- 数据分级元数据 + 导出任务表（筛选条件/操作者/时间/审批）。
- D3/D4 字段级加密：密钥边界独立于业务表（密钥来源/轮换/停用/本地开发替代在实施计划声明）；可检索字段只用不可逆 hash/分桶/枚举/批准 token。
- 5 方言迁移（h2/postgres/oracle/dm/kingbase）一致 + 中文 COMMENT + 索引约束；能复用现有审计/任务/执行日志表的复用、不重造。

## 视角清单（11 视角逐条）
1. 产品架构：引擎数据"活起来"的统一读模型 + 四入口受控网关；MCP 为 Agent 生产底座。
2. 产品体验：临床端低打扰只读解释 + 反馈；管理端可下钻；CLI/MCP 入专家模式不污染临床路径（核心 §2.0）。
3. 系统与数据架构：服务端分页/游标、P95 ≤2s 筛选、10 万级聚合、异步导出；读模型按租户+场景索引。
4. 临床医疗安全：临床端不阻断主流程、不暗示自动诊断；D4 工具须临床 launch；医师确认才进病历（核心 §6/§10）。
5. 知识与数据治理：统计引既有权威事实，不写死；来源版本/替换状态可查。
6. 安全合规与监管：★数据分级 D0–D5 + 后端脱敏 + 字段级加密 + 小样本抑制（默认 <10 例 suppressed）+ 全审计（核心 §8）。
7. 集团化与多租户治理：数据范围五维之一；跨租户不串；策略组织继承。
8. 集成与互操作：四入口同一后端合同；MCP/CLI 只经受控工具，不绕引擎（核心 §10）。
9. 运维 / SRE / 国产化：★内网纯 B0 + 无 MCP/CLI 主链路可跑；生产中心部署随容器化平台（`containerized-development-platform` 规范，作部署依赖）；国产化栈适配。
10. 质量与真实性审计：★禁伪造统计/命中率/采纳率；输出 hash 真实；无权限不以空数据伪装（铁律 #1）。
11. AI / 模型治理与可降级：★MCP 默认不返回可拼提示词的患者上下文；模型调引擎数据工具须声明用途/级别/脱敏/B0 降级（核心 §11）。

## 适用不变量
- 命中核心约束：**铁律 #1 真实性** · **#4 B0 先于模型** · **#5 关系库权威** · **核心 §8 脱敏/字段级加密/审计** · **§10 不绕引擎** · **§11 模型只产候选 + 降级诚实** · **§2.0 双产品面（CLI/MCP 入专家模式）** · **§14 技术对象藏专家模式**。
- 本卡落点：统一受控数据服务层，四入口同治理、不绕权限脱敏审计降级，B0 与无 MCP/CLI 均可运行。

## 验收 + 验证
- [ ] AC-1（FR-1/2）：规则/知识使用统计真实、服务端分页筛选导出；同一查询按角色返回不同字段，D5 不出现，D3/D4 落库字段级加密、日志/索引/导出无明文。
- [ ] AC-2（FR-3/4/5）：CLI 6 命令域走后端鉴权可用、不直连库；MCP 7 工具返回含 traceId/级别/脱敏/来源/权限/降级，D4 缺 launch token 降级；CLI/MCP 越权/绕治理被拒。
- [ ] AC-3（FR-6/7）：CLI/MCP/临床查看/导出全审计；模型/MCP/CLI/聚合延迟/上游/权限不足各诚实降级，不伪装。
- 关联 A1–A9 剧本：A6 合规运维（证据导出）· A9 AI 审核（工具调用）。
- T-GATE：前后端真实性门禁全绿（无伪造统计/hash，无绕 no-page-mock）。
- B0 验收：★关模型 + 无 MCP/无 CLI 时，规则/知识统计、确定性 MCP 工具、REST 查询仍真实可运行。

## 完工证据
- 代码 permalink：数据服务层四组 API + CLI 6 命令域 + MCP 7 工具 + 数据分级/字段级加密 + 审计 + 降级 + 5 方言迁移。
- 测试：正常/空/错误/无权限/部分成功/降级/字段越权/模型关闭/MCP·CLI 越权/导出审批失败/审计写入失败 全矩阵。
- 审计员签字：@<reviewer>（owner ≠ reviewer）。

## 大卡工序（12d，按 2026-05-26 规范分期 P0–P4，一张卡一份合同）
- PR1（P0+P1 数据服务最小闭环）：规格对齐 + 规则/知识使用统计 + 脱敏聚合 + 数据分级 + 审计 + 五方言迁移 → AC-1。
- PR2（P2 CLI + MCP）：CLI 6 命令域骨架 + MCP 7 受控工具 + 工具调用审计 + 不绕治理门禁 → AC-2/AC-3（降级）。
- PR3（P3 临床端只读解释合同预留 + P4 管理扩展骨架）：嵌入依据/反馈合同（接 D3）+ 管理下钻/导出（接 D4）→ AC-3。

## 实现进度（2026-06-14，按工序逐 PR；卡 FR/AC 因大卡分期未全勾，诚实标切片范围）
- **PR1-a（本切片，已实现）= 规则使用统计 D2 去标识聚合**：`com.medkernel.engine.datasvc` 新建引擎数据服务层；`EngineDataLevel`(D0–D5) 数据分级枚举；`RuleUsageStatsRepository` **只读聚合** `rule_execution_log`（不写、不违域归属）跨方言 OFFSET/FETCH 分页 + 子查询计数；`RuleUsageStatsService`（服务端分页 + 默认 90 天窗 + 每次查询审计 + 上游不可用诚实降级不以空数据伪装）；`EngineDataController` `GET /api/v1/engine-data/rule-usage`（`engine-data.read`，授质量与医保治理员，§8.4 管理质控端）；契约登记 `engine-data`。测试：`RuleUsageStatsServiceTest`(4) + `EngineDataControllerSecurityTest`(2) + `RuleUsageStatsRepositoryIntegrationTest`(1 真实 H2 聚合)。**B0/真实性**：纯读现有真实执行事实，空上游诚实返回不伪造命中/采纳率（铁律 #1）。
- **PR1-b（本切片，已实现）= 知识使用统计 D2 去标识聚合**：`KnowledgeUsageStatsRepository` **只读聚合** `recommendation_source` 中 `source_type='KNOWLEDGE'` 且 `source_ref_id` 非空子集（engine-recommendation 所属，仅读不写、不违域归属），按知识引用键聚合被引用次数 / 去重推荐卡片数（`COUNT(DISTINCT card_id)`）/ 最近使用时刻，跨方言 OFFSET/FETCH 分页 + 子查询计数；`KnowledgeUsageStatsService`（服务端分页 + 默认 90 天窗 + 每次查询审计 `EXECUTE recommendation_source` + 上游不可用诚实降级不以空数据伪装）；`EngineDataController` 加 `GET /api/v1/engine-data/knowledge-usage`（复用 `engine-data.read`，同 §8.4 管理质控端）；契约 `engine-data` 补声明 `recommendation_source` 审计点；产品功能目录重生成（端点列加 `knowledge-usage`）。测试：`KnowledgeUsageStatsServiceTest`(4) + `EngineDataControllerSecurityTest` 知识用例(+2) + `KnowledgeUsageStatsRepositoryIntegrationTest`(1 真实 H2 聚合，校验 KNOWLEDGE 子集、排除 RULE 与无 ref 键)。**B0/真实性**：纯读现有真实推荐引用事实，空上游诚实返回不伪造引用/采纳率（铁律 #1）。
- **PR1-c（本切片，已实现）= 临床信号统计 D2 去标识聚合**：`ClinicalSignalsRepository` **只读聚合** `recommendation_card`（engine-recommendation 所属真实 CDSS 决策信号事实，仅读不写、不违域归属；card 表无患者标识天然 D2），按 `card_type` 信号类别聚合 `COUNT(*)` 信号总数 / 高危数（risk_level IN HIGH,CRITICAL）/ 真实记录的采纳数（ACCEPTED）/ 驳回数（REJECTED）/ `MAX(created_at)` 最近信号时刻，跨方言 OFFSET/FETCH 分页 + 子查询计数；`ClinicalSignalsService`（服务端分页 + 默认 90 天窗 + 每次查询审计 `EXECUTE recommendation_card` + 上游不可用诚实降级不以空数据伪装）；`EngineDataController` 加 `GET /api/v1/engine-data/clinical-signals`（复用 `engine-data.read`）；契约 `engine-data` 补声明 `recommendation_card` 审计点；产品功能目录重生成（端点列加 `clinical-signals`）。测试：`ClinicalSignalsServiceTest`(4) + `EngineDataControllerSecurityTest` 临床信号用例(+2) + `ClinicalSignalsRepositoryIntegrationTest`(1 真实 H2 聚合，校验按类别聚合、高危/采纳/驳回计数、信号量倒序)。**B0/真实性**：纯读现有真实 CDSS 信号事实，采纳·驳回为真实状态计数非伪造率，空上游诚实返回（铁律 #1）。
- **PR1 待续切片（未实现，诚实标）**：工具调用读模型（FR-1 第 4 组，随 PR2 工具入口落地）；**D3/D4 字段级加密**（PR1-a/b/c 均 D2 去标识聚合不落患者字段故未触发，须随 D3/D4 数据落地切片实现，AC-1 字段级加密部分未达）；数据分级元数据表 + 异步导出（FR-1 导出）。
- **PR2-a（本切片，已实现）= 受控工具执行入口（CLI/MCP 共用，FR-4/5/6）**：`ControlledToolService` 把已建受控读模型以「受控工具」形式统一暴露——**仅派发到既有受控服务执行，不直连库、不绕权限脱敏审计降级**（FR-5）；每次执行包裹 `ToolExecutionEnvelope` 治理信封（traceId/数据级别/脱敏策略/来源版本/权限结果/降级状态/**真实 SHA-256 输出 hash**，FR-4）+ 工具调用审计含用途/级别/输出 hash（FR-6）；上游降级诚实透传不伪装（FR-7/铁律 #1）；未知工具结构化 404 不泄漏内部。本切片注册两个 D2 工具 `queryRuleUsage`（派发规则使用读模型）、`summarizeEngineSignals`（汇总规则/知识/临床信号分组数，不虚构未建上游路径/质控）。`EngineDataController` 加 `GET /api/v1/engine-data/tools`（目录）+ `POST /api/v1/engine-data/tools/{toolName}:execute`（执行），复用 `engine-data.read`；契约 `engine-data` 补声明 `engine_data_tool` 审计点；产品功能目录重生成。测试：`ControlledToolServiceTest`(6：目录/信封/汇总/降级透传/未知工具结构化/审计含用途级别) + `EngineDataControllerSecurityTest` 工具用例(+2)。**B0/真实性**：纯调既有 B0 读模型，关模型仍可跑；输出 hash 真实非伪造（铁律 #1/#10）。
- **PR2-b（本切片，已实现）= MCP 受控工具 `explainRule` + `checkKnowledgeExistence`（D1 已发布资产元数据）**：在 PR2-a 成熟受控工具底座上续登记两个单对象 D1 工具，不绕治理仅派发既有受控读服务（FR-4/5/6）。`ToolExecutionRequest` 加 `target` 目标标识字段（单对象工具自校验，缺失结构化 400 `ENG-API-001`）。① `RuleExplanationService` 只读 `rule_definition`（engine-rule 所属，仅读 SELECT 不违域归属，强租户隔离）映射单条规则已发布资产元数据为 D1 解释；规则不存在结构化 404（`ApiException.notFound`，不泄漏内部），上游不可用诚实降级（字段留空不伪造元数据，铁律 #1）。② `KnowledgeExistenceService` 只读 `knowledge_identity`（engine-knowledge 所属，仅读）回答存在性；**真实不存在＝ `exists=false` 且 `degraded=false`（诚实回答非报错非降级，铁律 #1）**，仅上游不可用 `degraded=true` 不以「不存在」伪装。`ControlledToolService` 注册两工具（D1）+ `policyFor(level)` 按数据级别给脱敏策略标识（D1=已发布资产元数据无需脱敏，D2=去标识聚合，D3+ 最严占位不以宽松伪装）；信封含真实 SHA-256 输出 hash + 工具调用审计（用途/级别/hash，FR-6）。契约 `engine-data` 补声明 `rule_definition`/`knowledge_identity` 审计点；**无新控制器/新端点**（两工具走既有 `/tools/{toolName}:execute`），产品功能目录 `--check` 无漂移。测试：`RuleExplanationServiceTest`(3) + `KnowledgeExistenceServiceTest`(3) + `ControlledToolServiceTest` 新增(4：D1 工具登记 / explainRule 信封 / 缺 target 结构化拒绝 / checkKnowledgeExistence 不存在诚实非降级)。**B0/真实性**：纯读现有真实规则定义与知识身份事实，关模型仍可跑（铁律 #1/#4）。
- **PR2-c（本切片，已实现，与 PR2-b 批入同一 PR #612）= MCP 受控工具 `searchKnowledge`（D1）+ `validatePrivacyPolicy`（D0），MCP 工具达 6/7**：① `KnowledgeSearchService` 按关键词只读检索 `knowledge_identity`（engine-knowledge 所属，仅读，复用现成 `pageByFilter/countByFilter` keyword 归一＝trim+lower+`%`，强租户隔离）映射 D1 命中列表，服务端分页；真实无匹配诚实空结果非降级、上游不可用诚实降级不伪装（铁律 #1）。② `PrivacyPolicyService` 纯数据分级 D0–D5 准入策略判定（无上游表，结果为 D0 策略元数据）：D0/D1/D2 准入；**D3/D4 须字段级加密、当前数据服务尚未实现字段级加密故诚实判不准入（不以「已支持」伪装，铁律 #1）**；D5 重要个人信息禁入（FR-2）；非法级别结构化 400。`ControlledToolService` 注册两工具 + 派发；契约/产品目录无新增（searchKnowledge 走既有 `knowledge_identity` 审计点，validatePrivacyPolicy 不碰表，无新端点）。测试：`KnowledgeSearchServiceTest`(3) + `PrivacyPolicyServiceTest`(4) + `ControlledToolServiceTest` 新增(3)。**B0/真实性**：纯读现有真实知识身份事实 + 确定性策略判定，关模型仍可跑。
- **PR2 待续切片（未实现，诚实标）**：MCP 第 7 工具 `getClinicalContextExplanation`（D4 须绑临床 launch token + 用途 + 过期 + 能力码 + 组织范围，缺一即降级/无权限，留专门切片）；CLI 6 命令域骨架（FR-3）；MCP 协议层适配；**D3/D4 字段级加密** + 数据分级元数据表 + 异步导出。
- **PR3**：临床端只读解释合同（接 D3）+ 管理下钻/导出（接 D4）——未启动。
