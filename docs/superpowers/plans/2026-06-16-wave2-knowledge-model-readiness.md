# 第二阶段接入模型生成知识前置能力长任务计划

> **For agentic workers:** REQUIRED: Use superpowers:executing-plans or subagent-driven-development to execute this plan task-by-task. Code steps must follow TDD: write failing test, verify red, implement, verify green, then commit.

**Goal:** 把第二阶段推进到“可以开始接入模型生成知识”的最小安全状态：模型可作为受控生产器产出候选，但候选仍必须经过来源、门禁、评测、去重、审核、替换链；这不等于绕过 P6 或直接进入正式知识生产。

**Architecture:** 以现有 `origin/main=399ed29f` 为基线，继续复用 AIK-STD-01/02/04/05 PR1/12/13 与 LLM-03/05/06/07/08 机制。新增能力只补齐候选提审前的安全流水线、生成期去重分流、影子评测、替换共存、模型生产器闸控与 Agent 受控回写，不新建第二套资产表，不让模型绕过审核台。

**Tech Stack:** Spring Boot + Java records + Spring Data JDBC + Flyway 五方言 + JUnit5/AssertJ/Mockito + React/Vitest + 真实性门禁/T-GATE。

---

## 当前事实

- 已具备：文档解析锚点（AIK-STD-02）、B0 模板候选生成（AIK-STD-04）、候选门禁框架与 6 项确定性门禁（AIK-STD-05 PR1）、审核台 AI 候选入口与全专业模板（AIK-STD-12）、生产编排/双形态隔离/血缘/物化/路由（AIK-STD-13 PR1-4）、provider 真实接入机制与出域闸（LLM-03/07/08）。
- 仍阻断：P6 正式知识生产。文献资料库受管根地址、真实凭据、真实基准集、部署形态、独立验收等外部条件未齐时，只能做到“模型生产器可接入但被 readiness gate 阻断或降级”。
- 分支策略：本计划分支为 `codex/wave2-knowledge-model-readiness`，只做本地提交；全部完成并验证后再统一 PR，不推送远端、不合并 main。

## 必做功能清单

1. **AIK-STD-05 PR2：补齐 11 项门禁**
   - 红线/剂量/高危近似门禁接 `engine.safety.ClinicalRedlineService`。
   - 冲突仲裁门禁接 `engine.knowledge.ConflictArbitration`。
   - 许可/来源可用性门禁解析 `sourceRef`，不能解析即拒收。
   - 去重门禁留给 AIK-STD-10 接入，不在本任务重复实现。

2. **AIK-STD-10：生成期身份识别、去重与 8 态审核分流**
   - 新增或复用生成期分流记录，识别新增、重复、小修订、重大升级、冲突、降级、废止、存疑。
   - 接入候选生成/提审前流程，重复候选不重复入审，冲突/升级进入对应审核去向。
   - 分流结果可查、可审计，并回填审核台展示字段。

3. **AIK-STD-06 + OPT-06 对接：影子运行与回归评测**
   - 候选在影子模式运行，不出临床提醒、不写病历/医嘱。
   - 采集命中、误报、漏报、退化指标；不达阈值不得提审。
   - 真实基准集未配置时诚实 `FAILED/NOT_READY`，不得自动认证。

4. **AIK-STD-11 / AIK-STD-09：待审共存与替换闭环**
   - 待审新版只可审不可执行，现行权威继续执行。
   - 审核台展示现行 vs 待审差异与替换提醒。
   - 审过新版接 SYS-08/MED-C3 原子替换、旧版隔离、影响任务、回滚/紧急失效。

5. **模型生成 readiness gate**
   - 新建 `KnowledgeProductionReadinessService`，集中判定正式模型生成前置是否满足：文献资料库受管根地址、部署形态、provider 可用且过评测、出域白名单/审批、prompt/tool/model 版本、P6 独立验收状态。
   - 模型生产器在 readiness 未通过时必须返回结构化阻断原因，不调用外部模型、不伪造候选。
   - 增加只读端点供生产中心页面/运维查看。

6. **LLM-01/02/04 收口**
   - LLM-01：固化 provider 无关网关契约，修正文档中“未接 provider”的陈旧口径，确保 B0 空候选不写死医学事实。
   - LLM-02：把 provider 缺位、断连、限流、结构化失败、出域阻断的降级矩阵测试补齐。
   - LLM-04：建立 prompt/tool/model 版本仓与任务三元组绑定，支持重放、回滚、导出；模型生成候选必须带真实版本三元组。

7. **AIK-STD-13 FR2：模型生产器接入**
   - 新增 `ModelKnowledgeProducer`，只经 `ModelGatewayService` 调用模型。
   - 输出必须先转 `KnowledgeAssetEnvelope`，带来源锚点、内容 hash、AI 标识、版本三元组、模型模式；然后走同一门禁/评测/去重/审核链。
   - readiness 未通过、provider 失败或 schema 不合格时，诚实阻断或降级，不产伪候选。

8. **AIK-STD-14：Agent 受控回写协议**
   - 复用 DATASVC MCP/CLI 底座，新增受控回写工具 `submitProductionCandidate`。
   - Agent 只能读公开资料/受控来源，禁止患者数据/D5；回写候选必须带锚点/hash/AI 标识/幂等键。
   - Agent 任务进度可查、可中止、可审计；技术对象默认专家模式。

9. **前端生产中心补齐**
   - 知识生产侧展示 readiness、生产 job、门禁结果、8 态分流、影子评测、共存替换提醒。
   - 页面遵守体验契约：一页一目标、六态、服务端分页、技术 JSON 默认折叠。

10. **文档、卡片和门禁收尾**
    - 更新 AIK-STD-05/06/09/10/11/13/14、LLM-01/02/04、backlog 与 `_HANDOFF`。
    - 全量或分层验证：后端相关单测/集成、迁移基线、治理/arch、真实性/配置/迁移/中文注释 changed、前端 productCatalog 与相关页面测试、`git diff --check`。

## 执行顺序

### Chunk 1: 候选提审前安全闸

- [x] Task 1: 为 AIK-STD-05 PR2 写红线/剂量/高危 readiness、许可、权威冲突仲裁门禁测试。
- [x] Task 2: 实现对应 `CandidateGate`，接入 `CandidateSafetyGateService`。
- [x] Task 3: 更新 AIK-STD-05 卡与测试证据，本地提交。

### Chunk 2: 去重分流

- [x] Task 4: 设计并测试 AIK-STD-10 8 态枚举、分流结果记录和查询。
- [x] Task 5: 实现生成期身份识别/去重/分流服务。
- [x] Task 6: 接入候选生成与审核台读模型，本地提交。后端读模型和重复跳过已完成；前端展示留 Chunk 7。

### Chunk 3: 影子评测

- [ ] Task 7: 写 AIK-STD-06 影子运行/指标/达标门禁测试。
- [ ] Task 8: 实现影子运行记录、评测编排和不达标阻断。
- [ ] Task 9: 与 LLM-07/OPT-06 机制对齐，真实基准集缺失时诚实 NOT_READY，本地提交。

### Chunk 4: 共存替换

- [ ] Task 10: 写待审不执行、共存差异、替换提醒、回滚/紧急失效测试。
- [ ] Task 11: 实现 AIK-STD-11/09 接入 SYS-08/MED-C3 的服务和端点。
- [ ] Task 12: 更新审核台展示与验证，本地提交。

### Chunk 5: 模型 readiness 与网关治理

- [ ] Task 13: 写 `KnowledgeProductionReadinessService` 测试覆盖所有 P6 前置缺口。
- [ ] Task 14: 实现 readiness 服务与只读端点。
- [ ] Task 15: 补 LLM-01/02/04 测试和实现：降级矩阵、版本三元组、重放/回滚/导出，本地提交。

### Chunk 6: 模型/Agent 生产器

- [ ] Task 16: 写 `ModelKnowledgeProducer` 测试：readiness 阻断、provider 成功、schema 失败、出域阻断、B0 降级。
- [ ] Task 17: 实现模型生产器并接 AIK-STD-13 FR2，输出同一候选信封。
- [ ] Task 18: 写 Agent 回写工具测试：患者数据禁触、锚点必填、幂等、审计。
- [ ] Task 19: 实现 AIK-STD-14 受控回写协议与 MCP/CLI 接线，本地提交。

### Chunk 7: 前端与收尾

- [ ] Task 20: 前端生产中心补 readiness/job/gate/triage/shadow/coexistence 六态页面。
- [ ] Task 21: 更新产品目录、卡片、backlog、`_HANDOFF`。
- [ ] Task 22: 跑最终验证矩阵，修复失败。
- [ ] Task 23: 本地整理提交历史；全部完成后再统一开 PR。

## 外部前置（代码不能伪造）

- 文献资料库受管根地址必须配置为正式 URI，不能是本机 tmp 或临时目录。
- 真实 provider 凭据只能存引用，不能进入对话、仓库或日志。
- 真实医学基准集与专家复核签字需要独立验收。
- 134 部署、真实 IdP/短信/图谱/外部 Provider 验收需要用户本会话点名授权，且须备份、留痕、可回滚。

## 防中断方式

- 只使用透明工程手段：本地分支、小提交、计划文档、`_HANDOFF`、测试证据。
- 不做额度检测规避、隐藏执行、绕过远程中断或其它不透明操作。
