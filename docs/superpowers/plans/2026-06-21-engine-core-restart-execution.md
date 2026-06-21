# 引擎核心从零重启 · 执行总计划

> **For agentic workers:** REQUIRED SUB-SKILL: 用 superpowers:subagent-driven-development（推荐）或 superpowers:executing-plans 逐任务实现。步骤用 `- [ ]` 勾选跟踪。
> **唯一设计锚点**：[2026-06-21 引擎核心从零重启总设计 v3](../specs/2026-06-21-engine-core-restart-design.md)。本计划只把 spec 落成可执行任务，不新增设计决策；冲突以 spec 为准。

**Goal:** 用全新轻量代码（零历史技术债、借鉴现有逻辑不搬代码）重建 MedKernel 为「医疗引擎核心」：一条全自动管道探索生成全领域医疗知识，经十大能力 API+嵌入供业务系统，唯一人工是上线旁路审批。

**Architecture:** 单一关系库权威（图库仅投影）；知识资产唯一信封 + 单主版本 + 多层级机构覆盖；生产全自动（探索→候选→确定性自动闸→ACTIVE）；上线旁路审批异步不阻塞；极简底座（密码+SSO、5 预设角色×范围×环境、国产 DB/服务器）。

**Tech Stack:** 后端 JDK 21 · Spring Boot 3.3 · Spring Security（密码+OIDC SSO）· Spring Data JDBC（无 JPA）· Flyway 五方言（h2/postgres/oracle/dm/kingbase）· 国密 SM2/3/4 · Neo4j（投影，可降级）。前端 React 18 · Antd 5 · Vite · FSD。

---

## 0. 怎么用本计划

1. **本文是程序级总清单**：§3 路线把 spec 全部目标拆成 S0–S5 + 横切 work package，每包有「目标 / 新建模块 / 任务 / 验收 / 依赖」，逐条可追溯到 spec，**保证团队能完整实现方案目标**。
2. **S0 已细化到可立即开工**（§4），先做 S0。
3. **S1–S5 在各自开工前用 writing-plans 即时细化**为 bite-sized TDD（它们依赖 S0 真实结构，提前写满=空想）。每片基于最新 `main`、一逻辑单元一 PR、先红测→实现→绿。
4. 每片必过 spec §14 验收门 + 真实性 T-GATE，并回写 [_HANDOFF](../../_HANDOFF.md)。

---

## 1. 新代码库架构（落地目标 · 决策 A）

**全新代码、不依赖旧 `medkernel-backend`/`frontend`**；老代码留作只读借鉴参考，本程序末尾退役（§3 横切 X3）。

新后端模块 `medkernel-engine/`（Maven 独立模块，不 import 旧包），按**职责分包**（对齐 spec §3 六件），文件保持小而专：

| 包 | 职责（spec 对应） | 借鉴参考（只读·不搬） |
|---|---|---|
| `knowledge.core` | 知识资产信封（身份/版本/来源/状态/适用域）；状态机（草稿→候选→ACTIVE→下线）| 旧 `engine/knowledge` 信封 |
| `knowledge.overlay` | 单主版本 + 多层级机构覆盖解析（就近叠加、逐级回落、安全单调）| 旧 `versioning/InheritanceResolver`（学逻辑）|
| `knowledge.store` | 关系库权威读写 + 图投影（可重建）| 旧 `projection` |
| `production` | 自动生产线：explore→acquire→parse→anchor→candidate→**auto-gate**→activate | 旧 `knowledge/acquisition`·`parsing`（学边界）|
| `production.gate` | 确定性自动质量闸（schema/红线结构/来源齐/去重/可复现 hash/引用真实性）| 旧 `llm/eval/MedicalRegressionEvaluator`（引用真实性逻辑）|
| `capability.query` `capability.terminology` `capability.rule` `capability.pathway` `capability.recommendation` `capability.quality` `capability.followup` `capability.report` `capability.provenance` `capability.pkg` | 十大服务能力，统一输出契约（结果+来源+AI标识+状态）| 旧对应域 + `rule/ConditionEvaluator`·`terminology`·`recommendation` 内核逻辑 |
| `capability.embed` | 页面嵌入契约（iframe/Web 组件/CDS Hooks 卡片）| 旧 `embed` |
| `runtime.alerting` | 运行期 5 档打扰梯度（L0–L4）渲染 | 旧 `cdshook`·`safety` |
| `approval` | 上线旁路审批（异步、`expertReviewed` 门、不阻塞管道）| —（新写）|
| `platform.auth` | 密码 + OIDC SSO 登录 | 旧 `security/auth`（去 MFA/bootstrap）|
| `platform.rbac` | 5 预设角色 × 范围(平台/机构) × 环境(内/外网)；内置超管 | 旧 `security`·`shared/datascope` |
| `platform.tenant` | 平台主租户 t-1 + 机构组织树（集团→医院→院区→科室）| 旧 `tenant`·`org` |
| `platform.config` | 配置中心（外置、可审计、高危护栏）| 旧 `shared/config` |
| `platform.audit` | 审计链（写时即留证 + 兜底）| 旧 `shared/audit`（学做法）|
| `platform.crypto` | 国密 SM2/3/4 + 字段级加密 | 旧 `shared/crypto`（学做法）|
| `shared` | hash(可复现) · idempotency · 标准临床上下文 · 可观测 trace · 统一 API 错误/分页 | 旧 `shared` 对应件 |

新前端 `engine-console/`（FSD），**4 一级入口**：工作台 / 知识生产 / 引擎能力 / 系统治理。

---

## 2. 验收基准（每片通用 · 来自 spec §14 + R1–R11）

- **真实性 T-GATE**：前端 `no-page-mock` + stylelint；后端禁造数/吞错/UUID 充哈希/占位 Javadoc；前后端全绿。
- 六态完整、服务端分页、**五方言迁移一致 + 中文 COMMENT + 索引约束**、中文优先、文档随代码同 PR。
- R1–R11 全程不破；降级诚实（关模型 B0 跑通 R6）；演示不造数。
- 每片回写 _HANDOFF 状态/下一步/证据。

---

## 3. 程序路线（S0–S5 + 横切 · 完整覆盖 spec 目标）

| 片 | 目标 | 新建模块 | 验收（除 §2 通用外）| 依赖 |
|---|---|---|---|---|
| **S0** 新库地基 | 骨架跑起来、能登录、4 入口空壳、V1 基线、CI 绿 | `platform.*` · `shared` · 前端外壳 | 见 §4 | — |
| **S1** 止血 | 一格领域走完 `探索→候选→自动闸→ACTIVE`，工作台+演示台看见 | `knowledge.core`·`production`·`production.gate`·`capability.query` | 知识数≥1；演示台查得到；全自动无伪造；关模型 B0 仍出确定性候选 | S0 |
| **S2** 知识库两层 | 资产信封 + 主版本/多层级覆盖 + 图投影 + 来源追溯 | `knowledge.overlay`·`knowledge.store`·`capability.provenance` | 同域唯一 ACTIVE 原子替换；机构覆盖就近叠加/逐级回落/不污染主源；图投影可重建 | S1 |
| **S3** 服务能力 | 查阅+字典+规则拦截，API+嵌入，运行期纯辅助+5 档 | `capability.terminology`·`capability.rule`·`capability.embed`·`runtime.alerting` | 各有真实 E2E；默认不打扰；L0–L4 逐规则可配；高危近似强制 HIGH；降级诚实 | S2 |
| **S4** 旁路审批 | 异步发布包审批，`expertReviewed` 门控 go-live | `approval`·`capability.pkg` | 审批不阻塞 S1–S3；未批知识仍可演示；平台/机构两侧审批留痕 | S3 |
| **S5** 全领域铺开 | 领域覆盖矩阵 + 覆盖率驱动批量生成；其余能力（路径/推荐/质控/随访/报告解读）按内容挂上 | `capability.{pathway,recommendation,quality,followup,report}` | 覆盖率可视；新格子复用同管道不另起；按 spec §8 P0→P1→P2 铺 | S4 |

**横切 work package（贯穿，不单独排期但必须完成）：**

| # | 目标 | 任务 | 验收 | 时点 |
|---|---|---|---|---|
| X1 | 宪法收口 | 按 spec §13 改宪法 §0(定位)/§2(IA 4 入口)/§6(单旁路审批+5 档)/§8·§11(自动闸)/§15(5 预设+密码SSO) | 宪法与 v3 一致、无旧口径残留 | S0 同期 |
| X2 | 旧文档定位收口 | LANDING_PLAN/FOUNDATION/业务场景 spec 顶部加横幅「范围有效·定位以 v3 为准」；范围仍作 §8 全领域参考 | 无"中枢=产品名"误导新 AI | S0 同期 |
| X3 | 旧库退役 | 新库达 S3 parity 后，归档/移除旧 `medkernel-backend`·`frontend`；134 旧实例按授权停服（先备份） | 仓库只剩新库；134 决策留痕 | S3 后 |
| X4 | 国产化基线 | dm/kingbase 方言 + 国密 smoke + 国产服务器(麒麟/统信/openEuler)部署核验 | 国产 DB 迁移一致、国密 smoke 过 | S0 起持续 |

---

## 4. S0 详细任务（新库地基 · 可立即开工）

> 目标：一个**能登录、4 入口可达六态空态、关模型可启动、CI 全绿**的最小新库。无业务、无知识，只证"地基真实可运行"。

### Task S0-1：新后端模块骨架 + 五方言 Flyway V1（空基线）

**Files:**
- Create: `medkernel-engine/pom.xml`（独立模块，依赖 Spring Boot 3.3 / Security / Data JDBC / Flyway；**不依赖** `medkernel-backend`）
- Create: `medkernel-engine/src/main/resources/db/migration/{h2,postgres,oracle,dm,kingbase}/V1__baseline.sql`
- Create: `medkernel-engine/src/main/java/com/medkernel/engine/EngineApplication.java`
- Test: `medkernel-engine/src/test/java/com/medkernel/engine/migration/MigrationBaselineContractTest.java`

- [ ] **Step 1: 写失败测试**——`MigrationBaselineContractTest` 断言：五方言 V1 文件均存在、含中文 COMMENT、平台主租户 `t-1` 种子行存在、五方言表清单一致。
- [ ] **Step 2: 跑测试确认失败**（文件不存在 → FAIL）。
- [ ] **Step 3: 写 V1 基线**——只建底座表：`mk_tenant`(含 t-1)、`mk_org_unit`、`mk_user`、`mk_role`(5 预设种子)、`mk_role_permission`、`mk_config`、`mk_audit_event`；每表中文 COMMENT + 索引/约束；五方言同步。
- [ ] **Step 4: 跑测试确认通过 + 应用启动到 h2**（`mvn -pl medkernel-engine spring-boot:run` readiness 200）。
- [ ] **Step 5: 提交**。

### Task S0-2：内置超管 + 5 预设角色 × 范围 × 环境

**Files:**
- Create: `platform/rbac/Role.java`(枚举：SUPERADMIN/OPERATIONS/KNOWLEDGE_PRODUCER/MEDICAL_EXPERT/COMPLIANCE_AUDITOR)、`Scope.java`(PLATFORM/INSTITUTION)、`Environment.java`(INTRANET/EXTRANET)、`RbacService.java`
- Test: `platform/rbac/RbacServiceTest.java`、`platform/rbac/SuperadminInvariantTest.java`

- [ ] **Step 1: 写失败测试**——超管不可降权/旁路、独立审计；非超管跨范围/跨环境访问被拒。
- [ ] **Step 2: 确认失败**。
- [ ] **Step 3: 实现** `RbacService`（权限=预设包；范围+环境维校验复用 `shared/datascope` 思路新写）。
- [ ] **Step 4: 确认通过**。
- [ ] **Step 5: 提交**。

### Task S0-3：登录（账户密码 + OIDC SSO）

**Files:**
- Create: `platform/auth/AuthController.java`（`POST /api/v1/auth/login` 密码登录；`/api/v1/auth/sso/*` OIDC）、`AuthService.java`、`SsoOidcConfig.java`
- Test: `platform/auth/AuthControllerTest.java`

- [ ] **Step 1: 写失败测试**——密码登录成功发 httpOnly cookie；错误密码返真实错误+traceId；SSO 回调建会话；登出失效会话。**不含** MFA/bootstrap。
- [ ] **Step 2: 确认失败**。
- [ ] **Step 3: 实现**（BCrypt 校验；OIDC 用 Spring Security oauth2-client；会话 httpOnly+CSRF）。
- [ ] **Step 4: 确认通过**。
- [ ] **Step 5: 提交**。

### Task S0-4：前端外壳 + 4 一级入口空壳 + 六态

**Files:**
- Create: `engine-console/`（Vite+React18+Antd5 FSD）、`src/app/routes.ts`（4 一级：工作台/知识生产/引擎能力/系统治理 + 二级空壳）、`src/shared/ui/PageShell.tsx`（六态）、登录页
- Test: `engine-console/src/__tests__/routes.test.ts`、Playwright 可打开性

- [ ] **Step 1: 写失败测试**——4 一级入口按 RBAC 渲染、每路由可打开到六态空态、登录页可渲染/提交/报错/登出。
- [ ] **Step 2: 确认失败**。
- [ ] **Step 3: 实现** PageShell 六态 + routes + 登录页（设计 token、无 hex 字面量、≤1 主按钮、≤3 筛选）。
- [ ] **Step 4: 确认通过**（`npm run lint && typecheck && test && build`；Playwright 截图）。
- [ ] **Step 5: 提交**。

### Task S0-5：T-GATE / CI 门禁

**Files:**
- Create: `.github/workflows/engine-ci.yml`、前端 `no-page-mock` eslint 规则 + stylelint、后端真实性检查脚本、`scripts/check-comment-zh.sh`、`MigrationBaselineContractTest` 接 CI
- Test: 门禁自测（故意造一条 `Math.random` 业务造数 → CI 必 fail）

- [ ] **Step 1: 写失败测试**——门禁能抓到：page-mock、hex 字面量、`Math.random` 造数、catch 吞错、UUID 充哈希、占位 Javadoc、五方言不一致、中文注释缺失。
- [ ] **Step 2: 确认失败**（门禁未接 → 漏抓）。
- [ ] **Step 3: 接齐门禁** + CI 工作流（前后端 lint/typecheck/test/build + 迁移合同 + 中文注释 + 真实性）。
- [ ] **Step 4: 确认通过**（造数样例被 fail，移除后全绿）。
- [ ] **Step 5: 提交**。

### Task S0-6：横切 X1/X2 文档收口（与 S0 同期）

- [ ] **Step 1:** 按 spec §13 改 [宪法](../../CONSTITUTION.md) §0/§2/§6/§8/§15。
- [ ] **Step 2:** LANDING_PLAN/FOUNDATION/业务场景 spec 顶部加横幅「范围有效 · 定位以 v3 spec 为准」。
- [ ] **Step 3:** 回写 _HANDOFF；提交。

**S0 验收门**：5 预设角色登录、4 入口路由六态、跨范围/环境拒绝、超管不可旁路、审计留痕、关模型可启动、五方言 V1 一致、T-GATE 全绿、宪法已改、旧文档已挂横幅。

---

## 5. S1–S5 work package（开工前各自细化为 bite-sized TDD）

> 下列每片给「核心新建文件 + 关键任务 + 验收」，足够排期与排序；逐步代码在该片开工时用 writing-plans 产出。

### S1 止血（依赖 S0）
- 新建：`knowledge.core`（资产信封 + 状态机）、`production`（explore/acquire/parse/anchor/candidate）、`production.gate`（自动闸 6 项）、`capability.query`（C1 查阅 API）、工作台知识数卡 + 演示台查阅页。
- 关键任务：① 资产信封 + V2 迁移；② 一个白名单来源的 explore→acquire→parse→anchor（借鉴旧 acquisition 边界：HTTPS/许可/robots/拒私网/大小上限，新写）；③ 候选生成（B0 确定性，关模型可跑）；④ 自动闸（schema/红线结构/来源齐/去重/可复现 hash/引用真实性——引用真实性借鉴 MedicalRegressionEvaluator 逻辑新写）；⑤ 过闸自动 ACTIVE；⑥ C1 查阅 + 演示台看见。
- 验收：一条真实知识自动跑完全链、工作台知识数≥1、演示台查得到、关模型 B0 仍产候选、无伪造。

### S2 知识库两层（依赖 S1）
- 新建：`knowledge.overlay`（多层级覆盖解析）、`knowledge.store`（关系库权威+图投影）、`capability.provenance`（来源追溯）。
- 关键任务：① 主版本 + 机构覆盖表 + V3 迁移；② 覆盖解析（借鉴 InheritanceResolver：按 orgPath 就近叠加、逐级回落主版本、批量查询不随资产膨胀、安全单调，新写）；③ 同域唯一 ACTIVE 原子替换；④ 图投影可重建（Neo4j 可降级）；⑤ 来源追溯链（类型/标题/版本/位置/hash/A–E 分级）。
- 验收：机构覆盖就近优先、逐级回落、不污染主源；原子替换；图关可重建；来源链完整。

### S3 服务能力（依赖 S2）
- 新建：`capability.terminology`（字典语义映射+高危近似）、`capability.rule`（DSL+5 档拦截）、`capability.embed`、`runtime.alerting`。
- 关键任务：① 字典语义映射（借鉴 TerminologyService：禁字符 LCS、高危近似强制 HIGH、批量含高危整批拒，新写）；② 规则 DSL 求值（借鉴 ConditionEvaluator 操作符语义，新写）；③ 运行期 5 档（L0–L4）逐规则可配，仅 L3/L4 阻断；④ API + 嵌入两交付；⑤ 统一输出契约（结果+来源+AI标识+状态）。
- 验收：查阅+字典+规则各真实 E2E；默认不打扰；高危强制 HIGH；嵌入不阻断主流程；降级诚实。

### S4 旁路审批（依赖 S3）
- 新建：`approval`（审批队列 + `expertReviewed` 门）、`capability.pkg`（包分发）。
- 关键任务：① 发布包模型 + V4 迁移；② 异步审批队列（专家点批准→标 `expertReviewed`）；③ go-live 门（仅 expertReviewed 包可推真实上线）；④ 证明审批不阻塞 S1–S3 管道；⑤ 平台/机构两侧审批留痕。
- 验收：未批知识仍自动 ACTIVE+可演示；批准后方可上线；审批全程不卡生产。

### S5 全领域铺开（依赖 S4）
- 新建：`capability.{pathway,recommendation,quality,followup,report}` + 领域覆盖矩阵 + 覆盖率看板。
- 关键任务：① 领域覆盖矩阵（spec §8：13 类资产 × 知识内容 × 专科 10 阶段）；② 覆盖率驱动批量自动探索-生成；③ 路径/推荐(CDSS-B0 借鉴 RecommendationDeterministicMatcher)/质控/随访/报告解读按"内容"挂同管道；④ 按 P0→P1→P2 铺。
- 验收：覆盖率可视、新格子复用同管道不另起引擎/页面/状态机；P0 共用引擎闭环先达成。

---

## 6. 自检（spec 覆盖核对）

- 定位/命名(§1)→X1 宪法 §0；IA 4 入口(§8 spec)→S0-4 + X1 §2；不变量 R1–R11→分散在 S0–S5 验收 + §2 基准。
- 知识模型两层(§4)→S2；自动生产线(§5)→S1；旁路审批(§6)→S4；十大能力(§7 spec)→S1/S3/S5；全领域(§8)→S5；角色登录(§9)→S0-2/S0-3；底座国产化(§10)→S0-1/X4；借鉴精华(§11)→各片"关键任务"已逐条点名借鉴源；施工路线(§12)→§3；宪法修订(§13)→X1。
- 无占位：S0 任务含具体文件/测试/命令；S1–S5 为 work package（按 §0.3 即时细化），已标注。
- 旧文档定位过时风险→X2 收口；旧库/134 退役→X3。

---

**计划已存。** 执行选项见下一条消息。
