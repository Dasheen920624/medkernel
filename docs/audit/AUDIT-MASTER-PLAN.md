# MedKernel 全系统深度审计总计划（可续接）

> 版本：1.0 · 创建：2026-05-29 · 维护者：AI 审计团队
> 状态：**进行中** · 当前进度见 §6 看板
> 触发：backlog 已把 E0–E6 全标 `done`，但 [真实性审计](2026-05-28-engine-capability-authenticity-audit.md) 与本轮抽查已证明"done 不可信"（详见 §5）。
> 性质：这是一份**可持久化、可续接**的审计台账。任意 AI 接手时，先读 §0，再从 §3 看板里取第一个「⬜ 待审」单元继续。

---

## 0. 给下一个 AI 的接续指令（务必先读）

你接手这份审计时，按以下步骤，不要重复已完成的工作：

1. **读三份权威文档**：`docs/CONSTITUTION.md`（17 条硬约束）、`docs/MEDKERNEL_BUSINESS_SCENARIO_DETAIL_SPEC.md`（唯一实现级详细规范）、`docs/MEDKERNEL_PRODUCT_EXPERIENCE_RULES.md`（体验规范）。
2. **读本文件 §5（已知问题）+ §6（看板）**：§5 列了已确认的实锤问题与"已修复确认"项，**不要再审已确认修复的点**；§6 看板告诉你哪些单元还「⬜ 待审」。
3. **取一个待审单元**：从 §6 看板按优先级（P0 → P1 → P2）取第一个 `⬜ 待审` 的单元，把其状态改为 `🔵 审计中` 并署名+日期。
4. **按 §2 的 10 维度 + §4 的 7 角色视角深审**：逐行读该单元的后端 service/controller + 前端页面 + 测试 + 迁移；用 grep 找造假模式（见 §2 维度 6 的嗅探清单）。
5. **写单元报告**：输出到 `docs/audit/units/{单元号}-{名}.md`，格式见 §7 模板。
6. **回填看板**：把 §6 对应行状态改为 `✅ 已审通过` / `⚠️ 需返工`，填上 C/H/M/L findings 数与报告链接；把关键 Critical/High findings 同步进 §5。
7. **纪律**：审计阶段**只读不改业务代码**（除非用户明确授权修复）；不 commit/push（除非用户要求）；findings 必须带 `file:line` 证据，**禁止凭"测试全绿"就判定真实**——上次造假正是被全绿单测固化的。
8. **判真伪的铁律**：方法 Javadoc 写"模拟/仿真/演示"、返回写死 `switch`/常量、`Math.random` 造结果、`catch` 块伪造成功、前端写死数据当真展示、`UUID` 充哈希——**一律判为假闭环（Critical）**，无论 backlog 标没标 done。

---

## 1. 背景与范围

- **系统规模**：后端 16 个 engine 域 + compliance + shared，约 500+ Java 文件、97 个测试类；前端 FSD 五区约 38 个页面、23 个测试文件；五方言迁移（postgres/oracle/dm/kingbase/h2）；backlog 共 ~62 个任务 ID 全标 done。
- **审计范围**：E1 底座 → E6 业务包，全功能实现 + 全业务范围，从 7 个客户角色视角评估。
- **本轮口径（用户决策 2026-05-29）**：**全系统全部重新深度核查，27 单元无一跳过**。§5.2 "已修复确认"仅免去重复验证那几个**具体点**，单元其余 9 个维度仍须全审。执行方式：**Claude 串行逐个深审**（不派并行 agent）。
- **审计目标**：① 鉴别"标 done 实为假"的任务；② 对真实现做 10 维度质量评审；③ 出优化意见与改造任务清单；④ 给出可否进入真实 GA 验收的结论。

---

## 2. 十大审计维度（每个单元逐项过）

| # | 维度 | 核查要点 |
|---|---|---|
| 1 | 业务正确性 | 对照详细规范，功能是否真实落地（非 stub/mock/写死/`switch` 假数据）；闭环是否完整 |
| 2 | 医疗安全合规 | AI 内容明显标识（宪法#9）；医师确认才进病历（#10）；高风险强制审核；旧版隔离（#14）；**禁止编造患者/引文/置信度** |
| 3 | 多租户隔离 | Repository 全带 tenant_id 过滤；`@DataScope(requireTenant=true)`；findById 跨租户泄露风险 |
| 4 | 审计与证据链 | 写/审/发/运行/反馈/导出/回滚走 `IsolatedAuditPublisher`；audit_event 含 trace_id/outcome/error_code；失败路径也发 FAILED audit |
| 5 | 五方言一致性 | V?? 在 5 方言齐全且等价；保留字（USER/ORDER/COMMENT/LEVEL）；VARCHAR 长度；JSON→CLOB 兼容 |
| 6 | 代码净化 | 嗅探：`Math.random`、`@RequestBody Map`、`System.out`、`TODO/FIXME`、`模拟/仿真/演示/占位`、`UUID`充哈希、`FORCE_*`测试钩子；前端 `style=`、`#[0-9a-f]{3,6}`、`console.`、`axios.`、`localStorage.`、`catch`伪造成功、写死数组当真展示 |
| 7 | 错误处理与降级 | ErrorCode 声明；ProblemDetail 统一；B0 无模型/无 Dify/无图投影降级**真实可用**（非贴标签） |
| 8 | 可观测性 | MDC traceId；StateTransitionRecorder 在状态转换点真调用；Micrometer 关键埋点 |
| 9 | 测试覆盖与有效性 | 是否覆盖：正常/参数失败/跨租户/权限不足/并发幂等/降级；**是否 mock 掉真实现把假算法固化为绿**；前端 23 测试 vs 38 页面缺口 |
| 10 | 前后端契约一致 | DTO↔type；接口路径↔React Query key；状态机字符串一致；错误码前端有处理 |

---

## 3. 七角色视角评估（宪法 §5）

每个面向客户的单元，按相关角色补充体验评估：

| 角色 | 验收硬指标 | 适用单元 |
|---|---|---|
| 院长 | 首屏 ≤10 秒、0 技术名词看懂系统状态 | 工作台、质控驾驶舱 |
| 医务处 | 30 分钟跑完 6 大剧本、不碰 JSON/DSL | 评估质控、规则校验、整改 |
| 临床医生 | 0 培训用 CDSS、"采纳/不采纳"两主按钮 | CDSS、患者路径、随访、待办 |
| 信息科主任 | 1 小时完成新医院接入、向导完成度≥80% | 实施向导、租户开通、适配器、Provider |
| 路径专家 | 专家模式编辑React Flow节点 | 路径配置、规则库、图谱 |
| 实施工程师 | 一键导入专病配置包，不进图谱/规则 | 配置包中心、字典映射 |
| 合规审计 | 任意页面右上角导出审计快照 | 审计日志、证据链、来源追溯 |

---

## 4. 严重度与优先级定义

**Finding 严重度**：
- **Critical**：违反宪法硬约束 / 医疗安全红线（编造患者数据、AI 假冒、绕过医师确认）/ 跨租户数据泄露 / 假闭环 / 失败 audit 丢失
- **High**：业务功能缺失或残缺 / 五方言不一致 / 状态机错乱 / 关键测试缺失 / 弱算法致临床误判
- **Medium**：代码质量 / 局部审计缺失 / 错误处理不完整 / 契约不一致
- **Low**：注释 / 命名 / 重构建议

**单元审计优先级**：
- **P0**（医疗安全 + 已知造假）：A7 CDSS、A12 LLM、A3 知识、A5 规则、A8 评估、A10 包发布、A15 证据、A6 路径
- **P1**（隔离/合规/对接）：A1 身份权限、A14 第三方、A4 字典、A16 审计可观测、A2 上下文、A9 随访、B1 五方言
- **P2**（底座/前端/验收）：A11 嵌入、A13 列表、A17 契约底座、C1–C6 前端、D1–D3 验收与测试有效性

---

## 5. 已确认问题清单（实锤，截至 2026-05-29）

### 5.1 已确认仍是假闭环 / 红线（必改）

| ID | 级 | 单元 | 证据（file:line）| 说明 |
|---|---|---|---|---|
| L1 | **Critical** | A12 LLM | `engine/llm/ModelGatewayService.java:465` `executeB1LocalInference`、`:480` `executeB2ExternalInference` | B1/B2 是写死 `switch`，Javadoc 自承"模拟"；根本不调模型/Dify |
| L2 | **Critical** | A12 LLM | `ModelGatewayService.java:482` | B2 编造患者"李建国/68岁"、编造体征禁忌；`:192` 编造引文"急性脑梗死规范化溶栓指南(2025版)§4.2"、`:193` 写死 confidence=0.96。违反宪法#9，患者安全风险 |
| L3 | High | A12 LLM | `ModelGatewayService.java:136` | `FORCE_FAIL_SCHEMA_` 测试钩子混入生产代码 |
| L4 | High | A12 LLM | `ModelGatewayService.java:440` `validateSchema` | Schema 校验仅字符串 `contains`，非真 JSON Schema |
| CDSS-CRIT-01 | **Critical** | A7 前端 | `CdssFatigue.tsx:172-189` | 真实触发后客户端额外伪造写死药理卡展示给医师（引擎未产出） |
| CDSS-CRIT-02 | **Critical** | A7 前端 | `CdssFatigue.tsx:204` | 医师反馈署名硬编码 PHYS-1002，非真实登录用户，破坏审计追责 |
| CDSS-H-02 | High | A7 前端 | `CdssFatigue.tsx:209` | 成功提示谎称「医嘱流转成功」，违背后端「不写医嘱」契约 |

> 注 1：LLM-01 / LLM-02 / GA-ENG-DEGRADE-01 三项 backlog `done` **名不副实**，应回退。
> 注 2：A7 **后端真实达标**，问题集中在前端展示层；详见 [units/A7](units/A7-recommendation-cdss.md)。前端"成功后仍叠加写死数据 + eslint-disable 门禁 + 硬编码身份"模式预计在 C2–C6 复现。
> 注 3（2026-05-29 修复）：上表 L1–L4 与 CDSS-CRIT-01/02、CDSS-H-02 **已全部修复并测试固化**（A12 后端 17/17 绿，前端 typecheck/lint/build/smoke 全过）。详见 [A12 §8](units/A12-llm-gateway.md) / [A7 §8](units/A7-recommendation-cdss.md)。R1 门禁失效根因（no-page-mock 误伤 `const columns`）一并修复。仅余 LLM-M-04(Low) 与 A7 后端 4 项 Medium 待办。

### 5.2 已确认真修复（下一个 AI 不要重复审这几点）

| 原 finding | 单元 | 复核结论（2026-05-29）|
|---|---|---|
| B8 EVID 导出返回假哈希 | A15 | ✅ 已修：`sha256-archive-` 假串消失（前端 Provenance 展示链路仍需复核） |
| B4/B5 INTEG `Math.random` 掷骰子 | A14 | ✅ 已修：`engine/integration/` 内已无 `Math.random` |
| B1 KNOW 片段无 SHA-256 | A3 | ✅ 已修：`KnowledgeIdentityService.java:209/235` 真实 `MessageDigest` 计算 + `findBySourceVersionIdAndContentHash` 唯一查重 |

### 5.3 上次审计确认真实（保持，复核时只补 10 维度其余维度）

- **A5 规则引擎** `RuleDslEvaluator`：真条件树 + 10 算子 + BigDecimal 比较。真。（仅做过 DSL 深读，租户/审计/五方言维度待补）
- **A3 KNOW 版本状态机** `activate/withdraw`：悲观锁原子切换 + supersession 链。真。
- **A15 EVID `verifyEvidence`**、**A14 INTEG `hmacSha256`**：算法真实。

### 5.4 尚未核查、高度可疑（重点靶区）

| 区域 | 可疑点 |
|---|---|
| A4 字典映射 | 上次 B2：`calculateSimilarity` 非 LCS、是字符命中比，致"肌钙蛋白T→血红蛋白"类临床误配——**是否已修未知** |
| E5 验收 QA-01..08 | 8 项 1 天内全标 done；E2E 测试是否真跑全链路、还是 happy-path mock |
| E6 业务包 ×14 | GA-SVC-* 全部 1 天内 done（v4.42–4.47）；按既往模式，前端极可能 `catch` 伪造 + 写死数据 |
| 前端测试 | 23 个测试文件 / 38 个页面；核心是单个 `pages.smoke.test.tsx`，疑为渲染冒烟、未验业务 |

---

## 6. 审计单元看板（核心追踪表）

> 状态图例：⬜ 待审 · 🔵 审计中 · ✅ 已审通过 · 🟡 部分已审(需补维度) · ⚠️ 已审需返工 · 🔧 修复中 · ☑️ 修复已复核
> 取单元规则：P0 优先，同级按编号。改状态请署名+日期。

> **27 单元已全部审完（2026-05-29，Claude 串行深审）。** 下方为终态看板。

### 后端引擎域

| 单元 | 名称 | 状态 | 报告 | C/H/M/L |
|---|---|---|---|---|
| A1 | 组织与身份权限 | ✅ 通过（隔离底座，测试最充分）| [A1](units/A1-org-security.md) | 0/0/2/0 |
| A2 | 标准上下文与临床事件 | ✅ 通过（契约/幂等/失败审计真）| [A2](units/A2-context.md) | 0/0/2/0 |
| A3 | 知识资产与版本 | ✅ 通过（旧版隔离红线真）| [A3](units/A3-knowledge.md) | 0/0/3/0 |
| A4 | 字典映射 | ✅ 通过（相似度已修为真 LCS）| [A4](units/A4-terminology.md) | 0/0/2/0 |
| A5 | 规则引擎 | ✅ 通过（真 DSL 引擎；前端归 C2）| [A5](units/A5-rule.md) | 0/1/2/0 |
| A6 | 路径引擎 | ⚠️ 后端优秀/前端中度 | [A6](units/A6-pathway.md) | 0/2/2/1 |
| A7 | 推荐/CDSS | ☑️ 修复已复核（前后端 8 项全清，2026-05-29）| [A7](units/A7-recommendation-cdss.md) | 2/2/4/0 |
| A8 | 评估质控 | ✅ 通过（前后端均达标）| [A8](units/A8-evaluation.md) | 0/0/3/1 |
| A9 | 随访 | ⚠️ 后端✅/前端归 C3 | [A9](units/A9-followup.md) | 0/1/1/0 |
| A10 | 包发布 | ⚠️ 需返工（看影响假+前端假）| [A10](units/A10-package-release.md) | 1/2/3/0 |
| A11 | 嵌入 | ⚠️ 后端✅/前端归 C3 | [A11](units/A11-embed.md) | 0/1/1/0 |
| A12 | 模型能力网关 | ☑️ 修复已复核（9 项全清，2026-05-29）| [A12](units/A12-llm-gateway.md) | 2/3/3/1 |
| A13 | 大规模列表 | ✅ 通过 | [A13](units/A13-large-list.md) | 0/0/2/0 |
| A14 | 第三方对接总线 | ☑️ 修复已复核（C1H2M2 全清/诚实未连接，2026-05-29）| [A14](units/A14-integration.md) | 1/2/2/0 |
| A15 | 证据链 | ⚠️ 后端✅/前端🔴 | [A15](units/A15-evidence.md) | 0/1/2/0 |
| A16 | 审计与可观测性底座 | ✅ 通过（失败审计基础设施）| [A16](units/A16-audit-observability.md) | 0/0/2/0 |
| A17 | API 契约与运行底座 | ✅ 通过（国密真）| [A17](units/A17-api-runtime-crypto.md) | 0/0/2/0 |

### 数据层

| 单元 | 名称 | 状态 | 报告 | C/H/M/L |
|---|---|---|---|---|
| B1 | 五方言迁移一致性 | ✅ 通过（各 24 文件对齐）| [B1](units/B1-migrations.md) | 0/0/2/0 |

### 前端

| 单元 | 名称 | 状态 | 报告 | C/H/M/L |
|---|---|---|---|---|
| C1 | 前端底座与视觉债 | ⚠️ 底座真/门禁失效(R1根因) | [C1](units/C1-frontend-base.md) | 0/1/2/0 |
| C2 | 试点准备页 (8) | 🔴 重灾区(4/8 假) | [C2](units/C2-tenant-pages.md) | 2/2/1/0 |
| C3 | 临床运行页 (8) | 🔴 重灾区(含 A7 2C) | [C3](units/C3-clinical-pages.md) | 2/3/1/0 |
| C4 | 质控改进页 (6) | ✅ 基本达标(诚实) | [C4](units/C4-quality-pages.md) | 0/0/3/0 |
| C5 | 合规运维页 (7) | ✅ 达标(全区最干净) | [C5](units/C5-compliance-pages.md) | 0/0/1/0 |
| C6 | 高级工具页 (5) | ⚠️ 2 假/3 干净 | [C6](units/C6-advanced-pages.md) | 2/0/1/0 |

### 跨切面与验收

| 单元 | 名称 | 状态 | 报告 | C/H/M/L |
|---|---|---|---|---|
| D1 | E5 引擎验收真实性 | 🔴 验收不可信 | [D1](units/D1-e5-acceptance.md) | 0/3/1/0 |
| D2 | 测试有效性 | ⚠️ 后端参差/前端仅冒烟 | [D2](units/D2-test-effectiveness.md) | 0/1/3/0 |
| D3 | E6 业务包真实性 | ⚠️ 继承前端假闭环 | [D3](units/D3-e6-business-packages.md) | 1/2/1/0 |

**进度统计**：共 27 单元 · **全部已审** · 通过 11 · 需返工/部分 16 · findings 合计约 **C13 / H24 / M50 / L4**

> 注：前端 C2/C3/C6 的 Critical 与后端 A7/A10/A12/A14/A15 重叠（同一假闭环两个视角登记），实际**唯一 Critical 问题约 8 处**，整改合并计。

---

## 6.1 全系统终态结论速览

| 区块 | 真实性 | 代表 |
|---|---|---|
| **后端引擎** | **多数为真**，11/17 通过 | A1/A2/A3/A4/A5/A8/A13/A16/A17 真；A6 优秀 |
| 后端例外 | A12 核心假、A14 半修复、A10 看影响假 | 需返工 |
| **前端** | **两极分化** | C5 最干净、C4 诚实；C2/C3/C6 假闭环重灾区 |
| **门禁** | 失效（R1） | C1-H-01：no-page-mock 可绕过，13 页 eslint-disable |
| **验收/测试** | 不可信 | D1 E5 速通、D2 前端仅冒烟、A12 测试固化假绿 |

**一句话**：**引擎内核（后端）大体是真的、质量不低；问题集中在 ①LLM 网关核心造假 ②第三方对接不真连外部 ③前端约 11 页系统性假闭环 ④防造假门禁失效 ⑤E5/E6 验收先标 done。**

## 6.2 改造任务清单（按优先级，供派单）

> 严重度对应整改优先级。Critical 多为医疗安全/假闭环红线。

### P0 红线（医疗安全 + 假闭环，必须先修）
| 任务 | 来源 | 工作量 | 验证 |
|---|---|---|---|
| **R-01** LLM B1/B2 诚实化：无 provider 一律标 B0，禁编造引文/置信度/患者数据 | A12-CRIT-01 | 3h（方案A）/ 2-3d（方案B 接真 provider）| 无 provider 时 mode=B0、citations=[] |
| **R-02** CdssFatigue 删伪造药理卡 + 反馈署名取真实用户 | A7-CRIT-01/02 | 2.5h | 断网显空态；audit actor=真实医生 |
| **R-03** 修 no-page-mock 门禁（检测 catch 伪造/写死数组/camelCase）+ 禁 eslint-disable | C1-H-01 | 1.5d | 13 页存量假闭环被门禁爆出 |
| **R-04** 前端 13 页逐页去假闭环（catch 改报错+空态、删写死数据集、删 mock fallback）| C2/C3/C6 | 5-7d | 后端失败→错误态，数据仅来自 query |
| **R-05** AdapterHub/ConfigPackages/Provenance/AiWorkflows 假闭环（R-04 子项重点）| A14/A10/A15/A12 | 含 R-04 | 各页导出/同步走后端真实证据 |

> **整改进度（2026-05-29，本次提交 PR）**：**R-01 ✅ / R-02 ✅** 已完成并测试固化（后端 LLM 17/17、前端 typecheck/lint(0 error)/build/vitest 92/92 全绿；详见 [A12 §8](units/A12-llm-gateway.md) / [A7 §8](units/A7-recommendation-cdss.md)）。**R-03 部分**：已修复 no-page-mock 误伤 `const columns` 的失效根因（启用文档声明却未生效的 `NAME_PATTERN`）并移除 CdssFatigue 整文件 eslint-disable；其"检测 catch 伪造/写死数组"增强与其余页 eslint-disable 清理并入 R-04 待办。**R-08 部分**：A12 LLM 单测已重写为诚实断言（无 provider→B0、负向无伪造），前端业务行为测试仍待补。

> **整改进度（2026-05-29 第二批）**：**A7 / A12 两单元 findings 全部清零并测试固化**（A12 9 项、A7 8 项）。其中 **R-10 部分**：A7 失败路径已补 IsolatedAuditPublisher 失败审计、A12 成功/失败留痕语义统一（EVAL/PKG/PATH 仍待统一）；**R-14 部分**：A7 已接 `medkernel_cdss_alerts_total` 真实计数（其余引擎业务指标仍待补）；LLM-M-04 已闭环。

### P1 功能真实性
| 任务 | 来源 | 工作量 |
|---|---|---|
| **R-06** 第三方对接接真连接器（ping 真探活、retry 真重投递，无连接器标 NOT_CONNECTED）| A14-H-01/02 | 2-3d |
| **R-07** 包发布「看影响」科室分析取真实责任科室（去 dept-default 写死）| A10-H-01 | 2h |
| **R-08** 重写 LLM 测试 + 前端业务行为测试（去固化假绿、抓假闭环）| D2-H-01/M-01 | 2-3d |
| **R-09** 真跨引擎全链路 E2E（源→推荐→评估→随访→包→嵌入→证据）| D1-H-01 | 2-3d |
| **R-10** 全平台统一 IsolatedAuditPublisher + 失败审计（CDSS/EVAL/PKG/PATH）| A16-M-02 | 2d |

### P2 质量与一致性
| 任务 | 来源 | 工作量 |
|---|---|---|
| **R-11** A8 扫描 DSL 静默吞异常显式化（防漏报）| A8-M-01 | 2h |
| **R-12** 五方言列级等价自动校验脚本入 CI | B1-M-02 | 1d |
| **R-13** 知识 contentHash 禁时间戳合成 + 旧版下游隔离端到端用例 | A3-M-01/02 | 2.5h |
| **R-14** 各引擎补 Micrometer 业务指标（采纳率/达标率/降级率等）| 多单元 M | 3d |
| **R-15** 清理误导注释（A14 旧造假注释等）+ 收回 C4/C5 eslint-disable | A14-M-01/C4 | 1d |

### 验收门禁建议
- backlog 据实回退：**LLM-01/02、DEGRADE-01、INTEG-01/02、QA-01/04/07/08** → in_progress。
- 前端假闭环清零 + R-01/02/06/07 修复 + R-09 真 E2E 通过后，方可重新进入 GA 验收。

### 跨单元规律（给下一个 AI / 整改者）
1. **后端引擎多为真**（11/17 通过）；唯 A12 核心造假、A14 半修复、A10 看影响假。
2. **前端假闭环重灾区 = C2/C3/C6**；**诚实样板 = C5（最干净）/C4/A8**——整改时照搬其"catch 报错 + 真调接口 + 数据仅来自 query"。
3. **门禁失效是系统性根因**：先修 no-page-mock（R-03）再逐页清理（R-04），否则边改边回潮。
4. **"测试全绿/标 done"≠"功能为真"**：本审计 27 单元即反例集合，验收须看真实链路而非状态位。

---

## 7. 单元报告模板（写到 `docs/audit/units/{单元号}-{名}.md`）

```markdown
# {单元号} {名称} · 深度审计报告
> 审计日期 / 审计人 / backlog 自报状态 / 审计结论

## 1. 单元概览（代码路径、关键类+行数、迁移、测试数、业务目标完成度）
## 2. 十维度审计结果（每维 findings + file:line 证据）
## 3. 七角色视角评估（适用角色逐个）
## 4. Findings 汇总表（Severity | ID | 一句话 | file:line）
## 5. 改造方案（每个 C/H 一节：问题/位置/影响/具体改动建议/工作量(小时)/验证方式）
## 6. 优化建议（非阻塞）
## 7. 总评（done 是否名副其实 / 可否进入真实验收 / 阻塞项）
```

---

## 8. 修订记录

| 版本 | 日期 | 维护者 | 主要变更 |
|---|---|---|---|
| 1.0 | 2026-05-29 | Claude | 建立可续接总计划：27 审计单元看板、10 维度、7 角色、严重度定义、续接指令；落地已确认问题清单（LLM B1/B2 实锤造假 L1–L4、3 项已修复确认、4 区可疑靶区） |
| 1.1 | 2026-05-29 | Claude | 用户决策：全系统全部重新深度核查、27 单元无一跳过、Claude 串行逐个深审；4 个"部分已审"单元重置为待审（仅免重验 §5.2 已修具体点） |
| 2.0 | 2026-05-29 | Claude | **27 单元全部审完**：出齐 units/ 报告；终态看板（通过 11 / 返工 16）；新增 §6.1 终态结论 + §6.2 改造任务清单 R-01..R-15 + 验收门禁建议。核心结论：后端内核大体为真，问题集中在 LLM 核心造假/第三方不真连/前端约 11 页假闭环/门禁失效/E5-E6 验收先标 done |
| 2.1 | 2026-05-29 | Claude | **整改首批落地**（用户授权"针对有问题的进行改造"）：R-01（A12 网关诚实 B0）、R-02（A7 CdssFatigue 去伪造 + 契约对齐）完成并测试固化；R-03 修复 no-page-mock 门禁失效根因；A7/A12 看板转「🔧 修复中」并补 §8 修复记录。后端 LLM 17/17、前端 92/92 全绿 |
| 2.2 | 2026-05-29 | Claude | **整改第二批：A7/A12 全清**。补 A7 后端 CDSS-M-01（失败审计）/M-03（cdss_alerts 指标）/M-04（跨租户测试强化）、澄清 M-02 契约；A12 LLM-M-04（成功/失败留痕语义统一）。A7/A12 看板转「☑️ 修复已复核」。后端 Recommendation/LLM/Repository 单测 18/18 绿（全量待 CI） |
| 2.3 | 2026-05-29 | Claude | **整改第三批：A14 第三方对接全清**。pingAdapter/retryMessage 诚实化（NOT_CONNECTED/MISCONFIGURED，绝不伪造 HEALTHY/SUCCESS）+ 修复 withPing 字段错位 bug + V26 五方言迁移放开健康状态约束；AdapterHub 删 6 处 catch 伪造/写死假哈希导出/写死兜底数据。A14 看板转「☑️ 修复已复核」。后端集成+迁移单测 26/26、前端 95/95 绿（多方言 smoke 待 CI）。R-06 真实连接器接入仍待办 |

---

**End of audit master plan.**
