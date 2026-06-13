# 全真体验沙盘 · 阶段B（规则全类型内容）实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: superpowers:subagent-driven-development 或 executing-plans。Steps 用 `- [ ]`。

**Goal:** 经真治理 API 幂等发布覆盖 9 类规则类型 × 触发点 × 动作/严重度的真规则**（含 #1 沙盘自有 `SBX.LAB.CRITICAL.K`，共 10 条）**，让沙盘场景库全真填满规则引擎能力矩阵。**完整有效数据案例（载荷+DSL+测试用例+期望卡）见 spec §24。**

> **【清库前提】**134 数据均演练数据、后续会清库——seed **覆盖全部 #1–#10（不复用任何其它幕的规则/患者）**，连同沙盘铺底患者一并幂等发布；清库后重跑即全量恢复。

**Architecture:** 新增幂等 seed 脚本 `scripts/sandbox/seed-scenarios.mjs`（复用幕4 `p5-act4-rule-governance.mjs` 基建），对每条规则走"创建 DSL → 测试用例 → test → simulate → 会签 → 灰度→全量发布"真链路；同步扩展后端 `SandboxScenarioCatalog`（A1）与前端 `sandboxScenarios.ts`（A2）登记 9 场景。

**Tech Stack:** Node.js + playwright(API context)；后端 Java record 扩展；规则 DSL 见 spec §19.2 模板、§16 RuleCreateRequest。

**前置依赖：** 阶段A/A2 已合并。**§7 各场景临床内容（阈值/字段/文案）须经用户/临床校准定稿后填入**（本计划给出 DSL 模板 + 参数表骨架 + 占位标注）。先读 spec §20/§19/§16/§23、幕4 脚本。

> ⚠ **临床安全门**：每条规则的医学逻辑（条件阈值、动作、严重度）须经临床评审；本计划提供工程脚手架，临床内容为 `<待定稿>`，不得用未经评审的阈值上线。

---

## 文件结构
| 文件 | 职责 |
|---|---|
| Create `scripts/sandbox/seed-scenarios.mjs` | 幂等治理发布脚本（9 条规则） |
| Create `scripts/sandbox/scenario-rules.json` | **10 条规则**定稿参数（含 #1 `SBX.LAB.CRITICAL.K`；ruleCode/ruleType/triggerPoint/riskLevel/dsl/动作卡文案/来源）——#1 用 spec §24.1 金样可直接落；#2–#10 临床值 §24 已给示例，**定稿后填** |
| Modify `…/engine/sandbox/SandboxScenarioCatalog.java` | 登记 #2–#10 场景 |
| Modify `frontend/src/features/sandbox/sandboxScenarios.ts` | 前端登记 #2–#10（status 发布后由 `ready` 标记） |
| Test | `SandboxScenarioCatalogTest` 扩展（10 场景可解析）；seed 脚本 `node --check` + 幂等复跑 |

---

## Task 1: 规则参数清单（临床定稿）

**Files:** Create `scripts/sandbox/scenario-rules.json`

- [ ] **Step 1: 落参数骨架**（按 spec §7 行；`dsl`/阈值/文案标 `<待临床定稿>`，定稿后替换）

```json
[
  {"id":"sbx-med-warfarin-asa","ruleCode":"SBX.MED.WARFARIN.ASA","ruleType":"ORDER","triggerPoint":"medication-prescribe","riskLevel":"HIGH","actionCode":"STRONG_REMINDER",
   "name":"华法林+阿司匹林出血风险","sourceRef":"<指南名待定稿>","dsl":{"trigger":"medication-prescribe","version":"cdshooks-1.0","conditions":{"all":[{"fact":"<medications[].code 含华法林 且 含阿司匹林，待定稿>","operator":"exists"}]},"actions":[{"actionCode":"STRONG_REMINDER","severity":"HIGH","summary":"<卡文案待定稿>","detail":"<处置说明含‘不自动开立或修改医嘱’，待定稿>","requiresPhysicianConfirmation":true,"sources":[{"label":"<来源待定稿>"}]}]}},
  {"id":"sbx-order-contrast-ckd","ruleCode":"SBX.ORDER.CONTRAST.CKD","ruleType":"ORDER","triggerPoint":"order-sign","riskLevel":"HIGH","actionCode":"BLOCK","name":"肾功能不全开含碘造影剂","//":"BLOCK 须带强制 override+不阻断主流程，见 spec §12","dsl":"<待定稿>"},
  {"id":"sbx-dx-acs","ruleCode":"SBX.DX.ACS","ruleType":"DIAGNOSIS","triggerPoint":"patient-view","riskLevel":"MEDIUM","actionCode":"REMIND","name":"胸痛+肌钙蛋白↑提示ACS","dsl":"<待定稿>"},
  {"id":"sbx-report-critical","ruleCode":"SBX.REPORT.CRITICAL","ruleType":"REPORT","triggerPoint":"result-review","riskLevel":"HIGH","actionCode":"STRONG_REMINDER","name":"影像报告危急征象回报","dsl":"<待定稿>"},
  {"id":"sbx-discharge-check","ruleCode":"SBX.DISCHARGE.CHECK","ruleType":"DISCHARGE","triggerPoint":"discharge-sign","riskLevel":"MEDIUM","actionCode":"REMIND","name":"出院带药/随访缺失核查","dsl":"<待定稿>"},
  {"id":"sbx-followup-inr","ruleCode":"SBX.FOLLOWUP.INR","ruleType":"FOLLOWUP","triggerPoint":"followup-alert","riskLevel":"HIGH","actionCode":"STRONG_REMINDER","name":"随访INR超治疗窗未处理","dsl":"<待定稿>"},
  {"id":"sbx-insurance-drg","ruleCode":"SBX.INSURANCE.DRG","ruleType":"INSURANCE","triggerPoint":"order-sign","riskLevel":"LOW","actionCode":"INFO","name":"医保DRG/DIP不合理项提示","dsl":"<待定稿>"},
  {"id":"sbx-quality-record","ruleCode":"SBX.QUALITY.RECORD","ruleType":"QUALITY","triggerPoint":"patient-view","riskLevel":"MEDIUM","actionCode":"REMIND","name":"病历内涵质控缺陷提示","dsl":"<待定稿>"},
  {"id":"sbx-record-completeness","ruleCode":"SBX.RECORD.COMPLETENESS","ruleType":"RECORD","triggerPoint":"patient-view","riskLevel":"LOW","actionCode":"INFO","name":"病历结构化完整性提示","dsl":"<待定稿>"}
]
```

- [ ] **Step 2: 临床评审 gate**：与用户/临床确认每条 `dsl`/阈值/文案/来源，替换所有 `<待定稿>`。**未定稿不得进 Step 后续发布。**
- [ ] **Step 3: 提交参数清单**（定稿后）。

---

## Task 2: 幂等治理发布脚本

**Files:** Create `scripts/sandbox/seed-scenarios.mjs`

- [ ] **Step 1: 写脚本**：读 `scenario-rules.json`；登录 `clinical-governor`（持 rule.write/publish）；对每条规则按 spec §20 序列：
  1. 幂等：`GET /engine/rule/rules?ruleCode=` 已存在且 PUBLISHED → 跳过；
  2. `POST /engine/rule/rules`（RuleCreateRequest：统一入参信封 §23.2 + ruleCode/name/ruleType/riskLevel/sourceRef/changeSummary/dsl）；
  3. `POST /engine/rule/rules/{id}/test-cases`（阳/阴/边界，覆盖 `REQUIRED_RELEASE_CASE_TYPES`）；
  4. `POST /engine/rule/rules/{id}/test`（全 PASS）；
  5. `POST /engine/rule/rules/{id}/simulate`（选铺底 ACTIVE 快照，期望命中）；
  6. `POST /engine/rule/rules/{id}/governance/signoffs`（委员会会签）；
  7. `POST /engine/rule/rules/{id}/governance/transitions`（GRAY→FULL；高风险带 `publishEvidence.electronicSignature`）；
  复用幕4 的 `login/csrfToken/apiGet/apiPost(双提交 X-XSRF-TOKEN)/findActiveSnapshot`。支持 `SEED_ONLY=<ruleCode,...>` 单条；汇总 `failures=[]`。
- [ ] **Step 2: 语法检查**：`node --check scripts/sandbox/seed-scenarios.mjs`　Expected: 通过。
- [ ] **Step 3: 干跑/幂等核对**：对目标库跑一次 → 服务端回查每条 `rule_definition.status=PUBLISHED`；再跑一次 → 全跳过、`failures=[]`、无重复落库。
- [ ] **Step 4: 提交**。

---

## Task 3: 后端场景目录扩展

**Files:** Modify `SandboxScenarioCatalog.java`；Test `SandboxScenarioCatalogTest`

- [ ] **Step 1: 扩展失败测试**：断言 `catalog.all()` 含 10 场景、各 `expectedRuleCode` 与 `scenario-rules.json` 一致、`triggerPoint`/`ruleType` 正确。
- [ ] **Step 2: 跑确认失败 → 在 catalog 构造器 register #2–#10（patientId/encounterId 取各场景专用 seed 患者）→ 跑确认通过。** Run: `mvn -pl medkernel-backend -Dtest=SandboxScenarioCatalogTest test`。
- [ ] **Step 3: 提交**。

> 注：#2–#10 的上下文构造（`SandboxRequestFactory.snapshot`）需按各触发点必填资源扩展（medications/orders/dischargeSummary/followupPlanId 等，见 `ClinicalEventTriggerPoint.requiredContextFields`）。每个触发点的 resources 字段以 `ContextSnapshotResources` 源码为准。

---

## Task 4: 前端场景注册表扩展

**Files:** Modify `frontend/src/features/sandbox/sandboxScenarios.ts`；Test 同目录

- [ ] **Step 1: 登记 #2–#10**（servicePackage 分组：#2-7 clinical-run，#8-10 qc-improve；status 初 `pending-seed`，seed 发布并验证后改 `ready`）。
- [ ] **Step 2: 测试**：断言 10 场景、按服务包分组数正确、覆盖 5 动作×4 严重度集合（用断言遍历）。
- [ ] **Step 3: 提交**。

---

## Task 5: 矩阵覆盖验收
- [ ] **Step 1:** seed 全跑通后，对每条场景调 `POST /engine/sandbox/scenarios/{id}/run`，断言 `result=PASS`、`cardCount>=1`、命中对应 `expectedRuleCode`。
- [ ] **Step 2:** 服务端回查 9 条 `rule_definition.status=PUBLISHED`、`rule_governance.state=FULL`。
- [ ] **Step 3:** 断言矩阵覆盖：9 规则类型全有 PUBLISHED 规则；5 动作（INFO/REMIND/STRONG_REMINDER/BLOCK/SUGGEST_ORDER）与 4 严重度被场景集合覆盖（SUGGEST_ORDER 由阶段C #12 承接，本阶段覆盖前 4 动作 + 在文末注明）。
- [ ] **Step 4:** 归档矩阵覆盖证据 `docs/release/evidence/.../sandbox/matrix-coverage.json`。

## 自审记录
- spec 覆盖：§7 #2–#10、§8 资产授权、§20 seed 序列、§19.2 DSL 模板、§22 阶段B 验收。
- 占位：`<待定稿>` 是**临床内容 gate**（spec §7 明确待评审），非工程占位；Task 1 Step 2 强制定稿门。SUGGEST_ORDER 动作覆盖明确移交阶段C #12，已注明。
- 类型一致性：`scenario-rules.json` 的 ruleCode/triggerPoint/ruleType 在后端 catalog（Task3）、前端注册表（Task4）、seed（Task2）三处一致。
