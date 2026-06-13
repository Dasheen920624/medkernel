# 全真体验沙盘 · 阶段C（外圈引擎场景）实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: superpowers:subagent-driven-development 或 executing-plans。Steps 用 `- [ ]`。

**Goal:** 把规则引擎之外的引擎能力纳入沙盘场景库——路径入径/推进、推荐综合卡（含 SUGGEST_ORDER）、随访计划→异常回院、评估质控闭环、嵌入 SDK/API 两模式，达成"十大引擎"全覆盖。

**Architecture:** 扩展 `SandboxOrchestrationService` 支持多"剧本"（scenario 带 `engine` 与可选 `playbook`），对路径/随访/质控类场景在编排里追加既有引擎调用步骤（复用 main 上幕6 路径、幕7 随访/质控的真实端点）；推荐综合卡用 candidateCards 带 SUGGEST_ORDER；嵌入 SDK/API 模式在前端 `SandboxEmbedFrame` 加模式切换演示。

**Tech Stack:** 同 A/A2/B。复用 main 上已合并的 `scripts/drill/p5-act6/7` 端点知识。

**前置依赖：** A/A2/B 已合并；main 含幕6 路径(`PATH.ED.DISPOSITION`)、幕7 随访/质控端点。**先读 spec §7 #11-15、main 上 `scripts/drill/p5-act7-followup-quality.mjs` 与 `p5-act6-clinical-run.mjs` 的端点序列。**

---

## 文件结构
| 文件 | 职责 |
|---|---|
| Modify `…/engine/sandbox/SandboxScenario.java` | 加 `playbook` 字段（RULE_ONLY/PATHWAY/FOLLOWUP/EVALUATION/RECOMMENDATION_COMPOSITE） |
| Modify `…/engine/sandbox/SandboxOrchestrationService.java` | 按 playbook 追加引擎步骤（路径 enter/advance、随访计划、质控评估） |
| Modify `…/engine/sandbox/SandboxScenarioCatalog.java` | 登记 #11–#15 |
| Modify `…/engine/sandbox/SandboxRequestFactory.java` | 加各引擎请求构造（PatientPathwayEnterRequest 等，见 spec §16/§23.3） |
| Modify `frontend/src/features/sandbox/{sandboxScenarios.ts, SandboxEmbedFrame.tsx}` | 登记 #11-15 + 嵌入模式切换（IFRAME/SDK/API 演示） |
| Test | 编排服务各 playbook 单测（mock 引擎服务，断言追加步骤）；前端组件测试 |

---

## Task 1: 编排支持 playbook

**Files:** Modify `SandboxScenario.java`（加 `String playbook`）、`SandboxOrchestrationService.java`；Test `SandboxOrchestrationServiceTest`

- [ ] **Step 1: 失败测试**：新增用例——PATHWAY playbook 场景 run 后，steps 含额外 `PATHWAY` 阶段（mock `PathwayEngineService.enter` 返回 patientPathwayId），response `patientPathwayId` 非空。
- [ ] **Step 2: 跑确认失败 → 实现**：`run()` 在 TOKEN 步前/后按 `scenario.playbook()` 分派：
  - `PATHWAY`：调既有路径服务 enter（spec §16 入径契约、§23.3 templateId 非主键 / 响应嵌套）→ 追加 `PATHWAY` step，回填 `patientPathwayId`。
  - `FOLLOWUP`：调既有随访服务（幕7 端点）生成计划 → 追加 `FOLLOWUP` step。
  - `EVALUATION`：调既有质控评估服务（幕7）→ 追加 `EVALUATION` step。
  - `RECOMMENDATION_COMPOSITE`：trigger 带 candidateCards（含 `SUGGEST_ORDER` 动作卡）→ 覆盖矩阵 SUGGEST_ORDER。
  - `RULE_ONLY`（默认）：维持 A1 三步。
  注入对应既有 service 依赖（构造器加参）；**只编排既有服务，不复制逻辑**。
- [ ] **Step 3: 跑确认通过**　Run: `mvn -pl medkernel-backend -Dtest=SandboxOrchestrationServiceTest test`。
- [ ] **Step 4: 提交**。

> 各引擎 service 方法签名/请求 record 以源码为准（PatientPathwayEnterRequest 见 spec §16；随访/质控服务参照 main 幕7 脚本调用的端点反查 service 方法）。

---

## Task 2: 登记 #11–#15 场景

**Files:** Modify `SandboxScenarioCatalog.java` + `SandboxRequestFactory.java`；Test catalog

- [ ] **Step 1: 失败测试**：`catalog.all()` 含 15 场景；#11 playbook=PATHWAY/expectedTemplate=`PATH.ED.DISPOSITION`、#12 RECOMMENDATION_COMPOSITE/action=SUGGEST_ORDER、#13 FOLLOWUP、#14 EVALUATION、#15 engine=embed。
- [ ] **Step 2: 实现 register #11-15 + RequestFactory 对应构造** → 跑通。
- [ ] **Step 3: 提交**。

---

## Task 3: 前端登记 + 嵌入模式切换

**Files:** Modify `sandboxScenarios.ts`、`SandboxEmbedFrame.tsx`；Test 两者

- [ ] **Step 1:** `sandboxScenarios.ts` 加 #11-15。
- [ ] **Step 2:** `SandboxEmbedFrame.tsx` 加 `mode: "IFRAME"|"SDK"|"API"` 演示切换：IFRAME 同现状；SDK/API 模式展示等价接入代码片段 + 调同一 `/embed/launch` 兑换契约（spec §7 #15、嵌入引擎契约一致），如实标注"SDK/API 为契约一致的接入方式演示"。
- [ ] **Step 3: 测试**：断言 15 场景登记；切换 mode → 断言渲染对应接入演示。
- [ ] **Step 4: 提交**。

---

## Task 4: 十大引擎覆盖验收
- [ ] **Step 1:** 各 #11-15 场景 `run` → `result=PASS`；服务端回查：#11 `patient_pathway` 行、#13 随访计划行、#14 质控评估/问题行、#12 `recommendation_card` 含 SUGGEST_ORDER。
- [ ] **Step 2:** 断言矩阵 SUGGEST_ORDER 动作覆盖（#12），补齐 B 遗留的第 5 动作；十大引擎清单（规则/路径/推荐/随访/质控/嵌入 + 知识/字典/包发布/模型以来源·降级体现）逐项在场景或路径检查器可见。
- [ ] **Step 3:** 归档 `docs/release/evidence/.../sandbox/ten-engine-coverage.json`。

## 自审记录
- spec 覆盖：§7 #11-15、§3 十大引擎、§22 阶段C 验收、补齐 SUGGEST_ORDER。
- 占位：各引擎 service 签名标"以源码为准"——因路径/随访/质控 service 方法名需读源码确认，已指向 main 幕6/7 脚本作反查锚点，非逻辑占位。
- 类型一致性：`playbook` 枚举值在 `SandboxScenario`/编排分派/catalog 三处一致。
