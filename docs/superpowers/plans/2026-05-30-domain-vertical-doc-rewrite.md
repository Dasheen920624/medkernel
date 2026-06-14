# 四文档改透（业务域纵向推进）实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 把宪法 §0、落地规划 §0/§17/§18/§21、backlog（全量重排）、质量基线（域级验收）四份权威文档改写为「按业务域纵向推进·从登录起」的两波结构，口径彻底一致、无遗留矛盾。

**Architecture:** 依据已确认设计 [2026-05-30-domain-vertical-delivery-design.md](../specs/2026-05-30-domain-vertical-delivery-design.md)。先改宪法 §0（节奏权威源），再对齐落地规划，再全量重排 backlog（149 项按本计划映射表逐条落位 + INFRA-06/07 炸开为各域页面任务），最后质量基线补域级验收，收尾做全局一致性核查。**纯文档改写，无代码**；每份文档一个任务一次提交，"测试"＝grep 一致性核查。

**Tech Stack:** Markdown；验证用 `grep -n`。仓库根：`/Users/zhikunzheng/个人/郑志坤/medkernel/claude`。当前分支 `claude/r2-rule-path-maintenance`。

---

## 149 项落位映射表（Task 3 的权威依据）

> 原则：基础进 D0；引擎按"谁第一个用"进 D2/D3/D4 的 B0；业务包装(SVC)溶进各域；验收拆域级+GA；模型/AI/知识资产/领域门面整体后移第二波。原 ID 一律保留（任务标题与范围沿用旧 backlog 同名条目，仅换归属与状态口径）。

| 目的地 | 收纳的原 ID |
|---|---|
| **D0 登录域/平台脊柱** | BASE-01, BASE-02, BASE-03, BASE-04, BASE-05, BASE-06, BASE-07, BASE-08, BASE-09, BASE-10, BASE-11, OBS-01, API-13, SYS-01, SYS-02, SYS-03, SYS-05, INFRA-01, INFRA-02, INFRA-03, INFRA-04, INFRA-05, INFRA-08 |
| **D1 工作台** | INFRA-09 + 新增页面任务（见下「页面炸开」）|
| **D2 试点准备** | API-01, API-03, API-04, API-05, API-06, API-10, KNOW-01, KNOW-02, TERM-01, RULE-01, PATH-01, PKG-01, SYS-04, SYS-08, INTEG-01, INTEG-02, MED-C2, OPT-01, OPT-07, SVC-PILOT-01, SVC-PILOT-02, SVC-PILOT-03, SVC-INTEGRATION-01 + 页面任务 |
| **D3 临床运行** | API-02, API-07, API-09, API-11, CDSS-01, FOLLOW-01, EMBED-01, MED-C3, OPT-02, OPT-03, OPT-04, SVC-CLINICAL-01, SVC-CLINICAL-02, SVC-CLINICAL-03 + 页面任务 |
| **D4 质控改进** | API-08, EVAL-01, OPT-08, EMR-LEVEL-01, EMR-LEVEL-02, SVC-QUALITY-01, SVC-QUALITY-02, SVC-QUALITY-03 + 页面任务 |
| **D5 合规运维** | EVID-01, SYS-06, OPT-05, SVC-COMPLIANCE-01, SVC-COMPLIANCE-02 + 页面任务 |
| **D6 高级工具** | OPT-10 + 页面任务 |
| **第二波 · AI 加深** | API-12, LLM-01, LLM-02, LLM-03, LLM-04, LLM-05, LLM-06, LLM-07, LLM-08, AIK-STD-01~12（12 项）, OPT-06, OPT-09, SVC-DOMAIN-01, SVC-DOMAIN-02, NURSING-01, REPORT-01, POC-KNOW-01, PHARMACY-01, CRITICAL-01, SPECIAL-POP-01, PERIOP-01, ONCO-RENAL-01, ALLIED-CARE-01, TCM-HEALTH-01, INFECTION-PH-01, PRIMARY-CARE-01, REGION-COLLAB-01, SPECIALTY-EXT-01, RWD-01, KNOWGEN-01~15（15 项）|
| **GA 总验收** | QA-01, QA-02, QA-03, QA-04, QA-05, QA-06, QA-07, QA-08, DEGRADE-01, SYS-07, INFRA-07, INFRA-10 |
| **前置（本次重排即完成）** | DOC-01, DOC-02, DOC-03, DOC-04, DOC-05, DOC-06 |
| **炸开（不再独立成项）** | INFRA-06「全 36 页面真实化」→ 拆为 D1~D6 各页面任务；MED-C1 → 已并入 TERM-01 |

**页面炸开**（INFRA-06/07 拆解，按宪法 §2.2 二级菜单，每页一项 `<域>-PAGE-<页名>`，承载该页"每页必交"——见质量基线 §域级验收）：
- D1：工作台、演示与校验（2 页）
- D2：客户实施向导、租户开通、配置包中心、路径配置、规则库、字典映射、适配器中心（7 页）
- D3：患者主索引、患者路径、临床提醒治理、规则校验、待办中心、通知中心、智能随访（7 页）
- D4：院级质控驾驶舱、质控预警、医保智能审核、评估指标库、评估结果、AI 知识审核（6 页）
- D5：用户管理、身份绑定、审计日志、安全基线、Provider 状态、通知设置（6 页）
- D6：来源追溯、图谱查询、AI 工作流、国产化自检、开发者控制台（5 页）

> 总核对：原 149 项（X-MED 按 2 项独立计，C1 含于 TERM-01）全部落位，无遗漏、无新增编号。

---

### Task 1: 宪法 §0 节奏改写 + §9.1/§12/修订记录同步

**Files:**
- Modify: `docs/CONSTITUTION.md`（§0 节奏表 line 35-40；§9.1 backlog 注释 line ~250；§12 任务执行行 line ~314；§13 修订记录）

- [ ] **Step 1: 改写 §0「当前工程执行只讲一个节奏」表（替换 line 35-40 那段）**

把：
```
当前工程执行只讲一个节奏：
| 阶段 | 口径 |
|---|---|
| 0 业务引擎全能力上线 | 先打通知识、字典、规则、路径、推荐、评估、随访、包发布、嵌入、模型网关、审计证据和降级链路 |
| 业务服务包装 | 引擎全能力验收后，再按唯一详细规范包装试点、临床、质控、合规、第三方业务接口和专业领域服务 |
```
替换为：
```
当前工程执行只讲一个节奏 —— 按业务域纵向推进，从登录起：

| 波次 | 口径 |
|---|---|
| 第一波 · B0 真实纵向 | 沿真实用户旅程逐域交付（登录 → 工作台 → 试点准备 → 临床运行 → 质控改进 → 合规运维 → 高级工具）；每域名下页面做到"无模型也真实可用、可验证"（确定性规则/已发布知识/人工），域级验收通过才走下一域 |
| 第二波 · AI 加深 | D0~D6 的 B0 全跑通后，建 AI 工厂、模型网关、首发知识资产等跨域共享深引擎，再回灌相关域做模型增强 |

→ 引擎能力不再集中前置成阶段，而是被"第一个用到它的域"按需拉入并先做 B0 真实；模型/AI/知识自动生成整体后移第二波（铁律 #4：B0 先于模型）。
```

- [ ] **Step 2: 同步 §9.1 docs 树里 backlog 注释**

把 `backlog.md                 ← 单一任务台账（E1-E5 当前执行；E6 后置包装清单）`
改为 `backlog.md                 ← 单一任务台账（D0~D6 第一波 B0 逐域；第二波 AI 加深）`

- [ ] **Step 3: 同步 §12 表「任务执行」行**

把该行 `作用` 列 `AI 团队任务领取、状态和当前引擎全能力上线实施`
改为 `AI 团队任务领取、状态和按业务域纵向推进（D0~D6 + 第二波）实施`

- [ ] **Step 4: §13 修订记录追加一行**

追加：`| 2.5 | 2026-05-30 | 用户决策 + Claude | §0 工程节奏从"引擎优先·自底向上"改为"按业务域纵向推进·从登录起"两波结构，同步 §9.1/§12 口径 |`
并把文首 `> 版本：2.3 · 2026-05-28` 改为 `> 版本：2.5 · 2026-05-30`。

- [ ] **Step 5: 核查（旧节奏措辞已清除）**

Run: `grep -nE "0 业务引擎全能力上线|引擎全能力验收后" docs/CONSTITUTION.md`
Expected: 无输出（旧二元节奏表述已被替换）。

Run: `grep -nE "第一波|第二波|按业务域纵向推进" docs/CONSTITUTION.md`
Expected: §0 命中新表述。

- [ ] **Step 6: Commit**

```bash
git add docs/CONSTITUTION.md
git commit -m "docs(constitution): §0 节奏改为按业务域纵向推进·两波结构

Co-Authored-By: Claude Opus 4.8 <noreply@anthropic.com>"
```

---

### Task 2: 落地规划 §0/§17/§18/§21 对齐两波结构

**Files:**
- Modify: `docs/MEDKERNEL_IMPLEMENTATION_LANDING_PLAN.md`（§0 执行摘要 line ~10；§17 line 938-979；§18 line 980-1031；§21 line 1080+）

> 执行时先 Read 这些区段的当前文本，再按下列要求改写。改写必须与宪法 §0 新节奏 + 设计 §3 一致。

- [ ] **Step 1: 改写 §17「0 业务引擎全能力上线计划」**

要点（必须覆盖）：标题改为「按业务域纵向推进计划」；§17.1「上线阶段」从"引擎阶段"改为"第一波 D0~D6（旅程顺序＝依赖顺序）+ 第二波 AI 加深"，列出 D0~D6 的域名与各域 B0 坐实目标（同设计 §3.2 表）；§17.2「当前允许与阶段边界」改为"当前允许：D0 平台脊柱 + 登录域；后续阶段：第二波 AI/模型/知识自动生成"；§17.3 main 分支机制保留不变。

- [ ] **Step 2: 改写 §18「AI 团队任务拆分」**

要点：§18.1「当前引擎任务」改为「当前域任务（D0 起）」，指向 backlog D0 块；§18.2「后置业务包装任务」整节改为「第二波 AI 加深任务」，指向 backlog 第二波块（业务包装已溶进各域，不再是后置阶段）；§18.3 DoD 与 §18.4 代码净化保留，补一句"每域以质量基线域级验收为 DoD"。

- [ ] **Step 3: 改写 §0 执行摘要 + §21 近期执行顺序**

§0：把"先引擎全能力、后业务包装"的摘要句改为"按业务域纵向推进、从登录起、两波"。
§21「近期执行顺序」：改为 `D0 登录域（平台脊柱 + 登录界面）→ D1 工作台 → D2 试点准备 → …`，删除原引擎阶段顺序。

- [ ] **Step 4: 核查**

Run: `grep -nE "0 业务引擎全能力上线|后置业务包装|先引擎" docs/MEDKERNEL_IMPLEMENTATION_LANDING_PLAN.md`
Expected: 无输出，或仅在明确标注"（旧节奏，已废止）"的上下文。

Run: `grep -nE "第一波|第二波|D0|按业务域纵向" docs/MEDKERNEL_IMPLEMENTATION_LANDING_PLAN.md`
Expected: §0/§17/§18/§21 命中新表述。

- [ ] **Step 5: Commit**

```bash
git add docs/MEDKERNEL_IMPLEMENTATION_LANDING_PLAN.md
git commit -m "docs(landing): §0/§17/§18/§21 对齐按业务域纵向推进两波结构

Co-Authored-By: Claude Opus 4.8 <noreply@anthropic.com>"
```

---

### Task 3: backlog 全量重排为 D0~D6 + 第二波 + GA 验收

**Files:**
- Modify: `docs/backlog.md`（整篇重排；版本 7.0 → 8.0）

> 这是核心任务。**保留所有原任务 ID 与标题/范围文本**（从现有 backlog 同名条目搬运），仅改"归属章节"与"状态口径"。落位严格按本计划顶部「149 项落位映射表」。

- [ ] **Step 1: 重写文首 + §0 总则的推进节奏**

文首版本改为 `> 版本：8.0 · 2026-05-30 · 单一基线 · 项目未上线`，编制原则补一句"v8.0 按业务域纵向推进重排，废止 v7.0 的 E0~E6 自底向上编排"。
§0.4「推进节奏」整段替换为两波结构（同宪法 §0 新表 + 设计 §3.1/§3.2/§3.3）：第一波 D0~D6 旅程顺序，第二波 AI 加深；并写明"域级验收通过才走下一域"。

- [ ] **Step 2: 建 D0~D6 七个域章节**

每个域一个 `## D<n> <域名>` 章节，含：① 该域目标（一句话）；② 引擎/基础任务表（按映射表收纳的原 ID，列 ID/任务/范围/状态=pending）；③ 页面任务表（按「页面炸开」列表，每页一项 `<域>-PAGE-<页名>`，范围＝质量基线「每页必交」，状态=pending）；④ 域级验收一行（该域 E2E 主链路，状态=pending）。
D0 无页面炸开（脊柱+登录壳），但保留"登录域级验收"（按角色登入/菜单 RBAC/各页路由可打开到六态空态/退出会话）。

- [ ] **Step 3: 建「第二波 · AI 加深」章节**

`## 第二波 · AI 加深`，按映射表收纳 API-12 + 全部 LLM + 全部 AIK-STD + OPT-06/09 + SVC-DOMAIN + 全部 X-DOMAIN + 全部 KNOWGEN，分子块（模型网关/AI 工厂/首发知识资产/领域门面），状态=pending。

- [ ] **Step 4: 建「GA 总验收」章节**

`## GA 总验收`，收纳 QA-01~08 + DEGRADE-01 + SYS-07 + INFRA-07 + INFRA-10，状态=pending；保留原 v7.0 §17 的 14 条验收门禁（仍适用，作为 GA 门禁清单）。

- [ ] **Step 5: 删除旧 E0~E6 + X-* 章节结构，更新任务总数表**

删除原 `## 1. E0` ~ `## 15. X-INFRA` 的旧分块标题结构（任务条目已搬入 D0~D6/第二波/GA）。§16 任务总数表按新结构（D0~D6 各域数 + 第二波 + GA）重算；总数仍应等于 149 项原 ID + 新增页面任务数（页面任务另计小计）。

- [ ] **Step 6: 核查（所有原 ID 仍在 + 旧结构已清除）**

Run（抽样验证关键 ID 仍存在）：`grep -cE "BASE-01|API-12|RULE-01|KNOWGEN-15|QA-01|NURSING-01" docs/backlog.md`
Expected: ≥ 6（这些 ID 都还在，只是换了章节）。

Run（旧自底向上章节标题已删）：`grep -nE "^## .*E[0-6] |E6 业务服务包装|横切 X-" docs/backlog.md`
Expected: 无输出。

Run（新结构就位）：`grep -nE "^## D[0-6]|第二波|GA 总验收" docs/backlog.md`
Expected: 命中 D0~D6 + 第二波 + GA。

Run（无遗留旧节奏）：`grep -nE "E0 文档清场 → E1|自底向上" docs/backlog.md`
Expected: 无输出。

- [ ] **Step 7: Commit**

```bash
git add docs/backlog.md
git commit -m "docs(backlog): v8.0 全量重排为 D0~D6 + 第二波 + GA · 149 项按域纵向落位

Co-Authored-By: Claude Opus 4.8 <noreply@anthropic.com>"
```

---

### Task 4: 质量基线补「域级验收」

**Files:**
- Modify: `docs/audit/质量基线.md`（新增一节；版本递进）

- [ ] **Step 1: 新增「域级验收」节**

在「任务通过标准模板」之后新增一节，内容：
```
## 域级验收（按业务域纵向推进新增）

单项任务通过 ≠ 一个域落实。每个域（D0~D6）名下所有页面任务 + 引擎任务全部 `done` 后，再过一道域级验收：

1. 登录该域涉及角色（按 13 角色矩阵）
2. 逐页打开：无 Hook 错误 / null reference / API 404，六态可达
3. 跑通该域 B0 主链路 E2E（确定性 + 人工，无模型）
   - 例 D3：建患者 → 入径 → 节点推进 → 确定性提醒 → 采纳/拒绝带原因 → 待办闭环
   - 例 D0：按角色登入 → 菜单按 RBAC 正确呈现 → 各页路由可打开到六态空态 → 退出/会话过期生效
4. T-GATE 前后端门禁全绿；owner ≠ reviewer 签字

**域级验收过，才算该域落实，才走下一域。** 模型增强不在域级验收内（属第二波）。
```

- [ ] **Step 2: 同步版本与定位行**

文首版本 `3.0` → `3.1`，定位行补一句"v3.1 增加域级验收，配合 backlog v8.0 按业务域纵向推进"。

- [ ] **Step 3: 核查**

Run: `grep -nE "域级验收|走下一域" docs/audit/质量基线.md`
Expected: 命中新节。

- [ ] **Step 4: Commit**

```bash
git add docs/audit/质量基线.md
git commit -m "docs(audit): 质量基线 v3.1 增加域级验收

Co-Authored-By: Claude Opus 4.8 <noreply@anthropic.com>"
```

---

### Task 5: 全局一致性核查

**Files:**（只读核查，必要时回改前述文档）

- [ ] **Step 1: 跨文档旧节奏残留扫描**

Run: `grep -rnE "自底向上|E0→E6|E0 → E1|引擎优先.*业务后置|后置业务包装|0 业务引擎全能力上线" docs/CONSTITUTION.md docs/MEDKERNEL_IMPLEMENTATION_LANDING_PLAN.md docs/backlog.md docs/audit/质量基线.md`
Expected: 无输出（四份文档均无旧节奏残留）。

- [ ] **Step 2: 交叉引用解析核查**

Run: `grep -nE "backlog|质量基线|落地规划|D0~D6|两波" docs/CONSTITUTION.md`
Expected: §0/§9.1/§12 引用与新结构一致。

- [ ] **Step 3: 原 ID 完整性核查（149 项无丢失）**

Run: `for id in BASE API OBS SYS KNOW TERM RULE PATH CDSS EVAL FOLLOW PKG EMBED LLM EVID INTEG DEGRADE QA SVC AIK MED OPT EMR NURSING REPORT POC PHARMACY CRITICAL SPECIAL PERIOP ONCO ALLIED TCM INFECTION PRIMARY REGION SPECIALTY RWD KNOWGEN DOC INFRA; do echo -n "$id: "; grep -coE "$id-[0-9A-Z]+" docs/backlog.md; done`
Expected: 各前缀计数 ≥ 旧 backlog 对应数量（人工对照设计 §5 + 映射表确认无丢失）。

- [ ] **Step 4: 若发现矛盾/丢失，回到对应 Task 修正并重新提交；全绿则本计划完成。**

---

## Self-Review（写计划后自检结论）

- **Spec 覆盖**：设计 §6 四份文档落点 → Task 1（宪法）/ Task 2（落地规划）/ Task 3（backlog）/ Task 4（质量基线）逐一对应；§7 执行顺序 → 任务顺序（宪法→落地规划→backlog→质量基线→核查）一致；§4 域级验收 → Task 4 Step 1；§5 映射 → 计划顶部映射表 + Task 3。无缺口。
- **占位扫描**：无 TBD/TODO；落地规划区段为"要点+行号+先 Read 再改"（doc 改写的合理形式，非占位）。
- **类型一致**：域命名 D0~D6、波次"第一波/第二波"、章节名"GA 总验收"在四份文档与映射表中用词统一。
- **ID 完整**：映射表已核对原 149 项（X-MED 计 2）全部落位，Task 5 Step 3 提供机器核查。

---

**End of 四文档改透实施计划。**
