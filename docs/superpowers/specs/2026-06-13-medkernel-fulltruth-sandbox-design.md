# MedKernel 全真体验沙盘（业务系统场景模拟器）设计

- 日期：2026-06-13
- 状态：设计待评审
- 范围：一份整体 spec（用户裁定不分解），覆盖平台框架 + 引擎能力全矩阵内容；文档内标注内部建设顺序，实施分阶段。

## 1. 背景与目标

MedKernel 的真实临床价值发生在**嵌入式 CDSS**（`/embed/launch`，宿主 HIS/EMR 经 launch token 拉起），但该界面只能被宿主拉起、当前演练（幕6）只用独立站 `/rule/validate` + API 验证过规则执行，**嵌入式生产体验从未端到端展示**。

本设计建一个**全真体验沙盘** `/sandbox`：在 MedKernel 前端内模拟宿主业务系统（HIS/EMR/LIS），**按能力目录一键或自定义录入真实数据 → 调真引擎 → 嵌入式终端真体验 → 完整路径可核查**。目标：

1. **能力全展现**：呈现规则引擎全部能力面（见 §3 能力矩阵）与外圈十大引擎，每种类型都可被真实重现。
2. **全真**：调真引擎、用真发布资产产卡、服务端真留痕，绝不脚本造假。
3. **真实数据录入**：既有预置场景一键重现，也支持手工录入真实临床数据驱动引擎。
4. **完美体验**：演示叙事 + 验证可核查兼顾，给客户全真体验。

## 2. 范围与非目标

**范围**：沙盘前端页、能力目录、真实数据录入、后端编排、完整路径检查器、嵌入体验复用、按业务服务包分组、覆盖能力矩阵的真发布资产（经治理 seed）。

**非目标**：
- 不新起一套业务实现——沙盘只组合调用既有引擎能力（与"业务服务包不绕过引擎"一致）。
- 不替代真实第三方 HIS（同源沙盘的 origin 白名单/postMessage 是同源便捷态；真跨域留待独立宿主验证，登记为后续项）。
- 不放开 P6 正式知识生产；沙盘资产是演示/验证资产，归入演练数据域。
- 不接真实模型厂商；模型网关按既有 `MODEL_DISABLED` 诚实降级展示。

## 3. 规则引擎能力矩阵（"全部能力/每种类型"的边界）

| 维度 | 取值 |
|---|---|
| 规则类型 `RuleType`（9） | DIAGNOSIS / ORDER / LAB / REPORT / DISCHARGE / FOLLOWUP / INSURANCE / QUALITY / RECORD |
| 触发点 `ClinicalEventTriggerPoint`（6） | patient-view / order-sign / medication-prescribe / result-review / discharge-sign / followup-alert |
| 动作 `RuleActionCode`（5） | INFO / REMIND / STRONG_REMINDER / BLOCK / SUGGEST_ORDER |
| 严重度 `RuleRiskLevel`（4） | LOW / MEDIUM / HIGH / CRITICAL |
| DSL 算子 | gte/lte/gt/lt/eq/in/exists/between/contains/and/or/not |

外圈**十大引擎**：知识资产 / 字典映射 / 规则 / 路径 / 推荐CDSS / 评估质控 / 随访 / 包发布 / 嵌入 / 模型网关。

"全覆盖"定义：每种规则类型至少 1 条真发布规则；动作与严重度集合被场景目录整体覆盖；6 触发点全部有真场景；推荐/路径/随访/质控/嵌入/包发布各有 1 条真路径。详见 §7 场景目录。

## 4. 架构与组件

```
/sandbox 页（前端，权限受控）
├─ 左：业务系统宿主面板
│   ├─ 能力目录（按 业务服务包 → 引擎 → 触发点 分组的场景树）
│   ├─ 预置场景（一键重现）
│   └─ 自定义数据录入表单（按触发点必填上下文字段动态生成）
├─ 中：触发动作 → 调后端 SandboxController 编排（真引擎）
├─ 右：iframe 嵌真 /embed/launch?token=…（真终端真卡片真决策真回传）
└─ 底：完整路径检查器（每步真请求/响应 + 服务端留痕链接）
```

**前端组件**
- `SandboxHost.tsx`（页面骨架，路由 `/sandbox`，placement 受控）。
- `sandboxScenarios.ts`（声明式场景注册表：`{id, servicePackage, engine, triggerPoint, ruleType, title, narrative, presetContext, expectedRuleCode, expectedAction, expectedSeverity}`）。
- `SandboxDataEntry.tsx`（按触发点 `requiredContextFields` 动态渲染录入表单：患者/就诊/检验/医嘱/用药/出院/随访字段）。
- `SandboxPathInspector.tsx`（路径检查器：7 阶段，每阶段可展开 req/resp JSON + 服务端事实）。
- `SandboxEmbedFrame.tsx`（包裹 `/embed/launch` iframe + postMessage 监听回传到宿主面板）。

**后端组件**
- `SandboxScenarioController`（`/api/v1/engine/sandbox`，权限 `sandbox.run`）：编排真引擎调用，返回结构化路径轨迹 + launch URL。
- `SandboxOrchestrationService`：按场景/录入数据顺序调既有引擎服务（事件入口扇出 / 快照 / 适配器 inbound 三选一），每步记真 actor/traceId、聚合路径轨迹。**不复制业务逻辑，只编排既有服务**。

**隔离原则**：沙盘是引擎能力的"组合视图"，所有真实行为发生在既有引擎服务里；`SandboxOrchestrationService` 只负责顺序编排与轨迹聚合，可独立测试。

## 5. 数据入口与编排（真引擎）

三条**真实**数据入口（沙盘可演示全部三种，体现"从哪传给引擎"）：
1. **临床事件**（首选）`POST /engine/clinical-events`（event.write）→ `ClinicalEventEngineDispatcher` 自动扇出到 `Rule`+`Recommendation`+`Pathway` 三适配器，一条事件触发全评估。最贴近"业务系统推事件"。
2. **标准上下文快照** `POST /engine/context/snapshots`（context.write）→ 显式调推荐触发/规则评估。当前态对象，适合"复核态"场景。
3. **集成适配器 inbound** `POST /engine/integration/webhooks/{id}/inbound` → HIS/LIS/FHIR 报文映射 → 上下文/事件。体现真实第三方接入。

**编排序列**（以临床事件入口为例）：
```
录入/预置数据 → ① 铺/更新标准上下文 → ② 推临床事件（dispatcher 扇出：规则命中写 rule_execution_log /
推荐产卡 recommendation_card / 路径推进 patient_pathway）→ ③ 签发 embed launch-token →
④ 前端 iframe /embed/launch?token= 拉真终端显示推荐卡 → ⑤ 医师采纳/拒绝 → /embed/feedback +
postMessage 回宿主 →（可选 override 留痕）→ ⑥ 服务端回查留痕
```

## 6. 真实数据录入

- 按触发点的 `requiredContextFields` 动态生成表单（如 order-sign 需 patientId/encounterId/packageVersion/orders；result-review 需 results；medication-prescribe 需 medications）。
- 录入数据组装为 §5 的真实入口 payload（canonical 资源：CanonicalPatient/Encounter/Observation 等，`encounterType` 限 INPATIENT/OUTPATIENT/ED/FOLLOWUP）。
- 提供"从预置场景预填 → 改字段 → 触发"的快捷路径，兼顾演示与自定义。
- 校验沿用引擎契约（缺字段 400 如实展示，不在前端伪装通过）。

## 7. 场景目录（覆盖能力矩阵；临床内容待评审校准）

按业务服务包分组；每条对应一条真发布规则资产。标注：✅已有 / 🆕待 seed 授权发布。

| # | 服务包 | 引擎 | 触发点 | 规则类型 | 场景 | 动作/严重度 | 状态 |
|---|---|---|---|---|---|---|---|
| 1 | 临床运行 | 规则 | result-review | LAB | 血钾 6.8 危急值红线 `P5.ACT4.CRITICAL.K` | STRONG_REMINDER/CRITICAL | ✅ |
| 2 | 临床运行 | 规则 | medication-prescribe | ORDER | 华法林+阿司匹林出血风险 | STRONG_REMINDER/HIGH | 🆕 |
| 3 | 临床运行 | 规则 | order-sign | ORDER | 肾功能不全开含碘造影剂 | BLOCK/HIGH | 🆕 |
| 4 | 临床运行 | 规则 | patient-view | DIAGNOSIS | 胸痛+肌钙蛋白↑ 提示 ACS | REMIND/MEDIUM | 🆕 |
| 5 | 临床运行 | 规则 | result-review | REPORT | 影像报告危急征象回报 | STRONG_REMINDER/HIGH | 🆕 |
| 6 | 临床运行 | 规则 | discharge-sign | DISCHARGE | 出院带药/关键随访缺失核查 | REMIND/MEDIUM | 🆕 |
| 7 | 临床运行 | 规则 | followup-alert | FOLLOWUP | 随访 INR 超治疗窗未处理 | STRONG_REMINDER/HIGH | 🆕 |
| 8 | 质控改进 | 规则 | order-sign | INSURANCE | 医保 DRG/DIP 不合理项提示 | INFO/LOW | 🆕 |
| 9 | 质控改进 | 规则 | patient-view | QUALITY | 病历内涵质控缺陷提示 | REMIND/MEDIUM | 🆕 |
| 10 | 质控改进 | 规则 | patient-view | RECORD | 病历结构化完整性提示 | INFO/LOW | 🆕 |
| 11 | 临床运行 | 路径 | result-review | — | 急诊处置路径入径→推进（`PATH.ED.DISPOSITION`）| — | ✅ |
| 12 | 临床运行 | 推荐CDSS | order-sign | — | 综合推荐卡（规则+路径+知识）含 SUGGEST_ORDER | SUGGEST_ORDER/MEDIUM | 🆕 |
| 13 | 临床运行 | 随访 | followup-alert | — | 出院随访计划生成→异常回院 | — | 🆕 |
| 14 | 质控改进 | 评估质控 | — | — | 指标命中→问题→整改→复核 | — | 🆕 |
| 15 | 第三方接口 | 嵌入 | — | — | iframe/SDK/API 三模式接入 | — | 🆕(SDK/API) |

覆盖核对：9 规则类型全覆盖（#1-10）；6 触发点全覆盖；5 动作全覆盖（INFO #8/10、REMIND #4/6/9、STRONG_REMINDER #1/2/5/7、BLOCK #3、SUGGEST_ORDER #12）；4 严重度全覆盖；十大引擎中规则/路径/推荐/随访/质控/嵌入各有真路径，知识资产/字典映射/包发布/模型网关以"资产来源/降级"形式在路径检查器与场景前置中体现。

## 8. 推荐内容与资产授权（全真、不造假）

- 推荐卡内容来自**已发布知识资产**：当前主要内嵌在规则动作定义（summary/detail/sources/severity，带 GRADE 证据强度），运行时 `RecommendationDeterministicMatcher` 评估已发布规则命中产卡。
- 🆕 场景的规则经**幂等 seed 脚本走真治理 API 授权发布** `scripts/sandbox/seed-scenarios.mjs`：对每条规则真实"创建 DSL → 测试用例 → 灰度 → 全量发布"（复用幕4 脚本基建），跑完即真发布资产，任意环境（134/本地）可复现。
- 备选 Flyway 直插被否（绕过治理门，违背全真）。

## 9. 完整路径检查器

7 阶段：① 数据录入/上下文 → ② 引擎扇出（规则/推荐/路径）→ ③ 规则命中 → ④ 推荐卡 → ⑤ 嵌入展示 → ⑥ 医师决策 → ⑦ 服务端留痕。每阶段可展开：真实请求体、响应体、traceId、对应服务端表行（rule_execution_log / recommendation_card / patient_pathway / embed_launch_token / recommendation_feedback / audit）。是"全真可核查"的核心。

## 10. 嵌入体验

复用既有 `EmbedLaunch.tsx`（无需改造）：沙盘签发真 launch-token → iframe 加载 `/embed/launch?token=` → 真终端兑换上下文、取推荐卡、采纳/拒绝、postMessage 回传。沙盘 `SandboxEmbedFrame` 监听 postMessage 把决策回填到左侧宿主面板，闭合"业务系统 ↔ MedKernel"双向。

## 11. 权限与角色

- 新增权限 `sandbox.run`（编排）；沙盘页菜单 `menu.sandbox`，路由守卫一致性入 `routes.test.ts`。
- **编排越权机制（消歧）**：`SandboxScenarioController` 仅以 `@PreAuthorize('sandbox.run')` 为门；`SandboxOrchestrationService` **在进程内直接调既有引擎服务方法**（`ContextSnapshotService`/`ClinicalEventService`/`RecommendationEngineService`/`EmbedEngineService` 等），不经各自 HTTP 控制器，故不重复触发各端点的 `@PreAuthorize`——即编排只需 `sandbox.run` 一项，不给演示角色撒 context.write/event.write 等一线权（域判断：沙盘编排是系统能力，不是临床执行权）。
- **嵌入终端查看者权限**：右侧 iframe `/embed/launch` 由当前登录用户加载，token 兑换需 `embed.read`、取卡需 `recommendation.read`、反馈需 `recommendation.accept`/`embed.write`——核对沙盘操作员角色是否已含，缺则按既有两处断言同步补全 `PermissionDimensionModelTest`+`DefaultPermissionPolicyTest`，发布前跑全量 mvn test（记取幕5 教训）。

## 12. 错误处理与诚实降级

- token 一次性/过期/来源不在白名单 → 嵌入终端"会话安全隔离"（既有）。
- 规则未发布/未命中 → 场景标"未就绪"或"暂无建议"空态，不伪装。
- 录入缺字段 → 引擎 400 如实展示含 traceId。
- 模型网关无真实厂商 → `MODEL_DISABLED` 降级标识，不伪装 AI。
- **BLOCK 动作（#3 等）医疗安全红线**：BLOCK 必须演示**强制医师 override + 不阻断宿主主流程**的降级路径（产品体验规则禁止"嵌入式提醒阻断医生主流程且无降级策略"）——即 BLOCK 在嵌入终端呈现为强阻断提示但保留可留痕的 override 出口，宿主主流程不被物理卡死。
- 编排任一步失败 → 路径检查器在该阶段标红 + 原始错误，后续阶段不空跑。

## 13. 测试策略

- 后端：`SandboxScenarioController` 单测 + `SandboxOrchestrationService` 编排契约测试（mock 各引擎服务，断言调用序列与轨迹聚合）；不重复测引擎本身逻辑。
- 前端：`SandboxHost`/`SandboxDataEntry`/`SandboxPathInspector` 组件测试；`routes.test.ts` 前后端路由守卫一致性回归。
- seed 脚本：幂等复跑 failures=[]；每条规则发布后服务端回查 status=PUBLISHED。
- 端到端：沙盘演练脚本对真引擎跑各场景，服务端回查 rule_execution_log/recommendation_card/patient_pathway/embed/audit，复用幕6 模式。

## 14. 内部建设顺序（一份 spec，实施分阶段）

1. **阶段A（框架）**：`/sandbox` 页 + 能力目录 + 真实数据录入 + `SandboxController/Service` 编排（事件入口扇出）+ 路径检查器 + 嵌入体验，用 ✅场景#1/#11 端到端跑通。框架支持全矩阵登记。
2. **阶段B（规则全类型内容）**：seed 授权发布 #2-#10，填满 9 规则类型 × 触发点 × 动作/严重度矩阵。
3. **阶段C（外圈引擎）**：#12 推荐综合卡、#13 随访、#14 质控、#15 嵌入三模式（SDK/API）。
4. **阶段D（打磨）**：演示叙事、截图证据、可达性守卫、跨域真宿主验证项评估。

## 15. 风险与未决

- **内容规模**：矩阵全真=大量真规则授权（每条一次治理周期），seed 脚本摊薄成本但 DSL 内容仍需逐条设计与临床校核（#7 临床内容待评审）。
- **同源 vs 真跨域**：同源沙盘 origin 白名单/postMessage 是便捷态；真跨域第三方宿主验证登记为后续项。
- **演示角色权限**：需核对/同步权限白名单（§11），注意两处断言 + 全量测试教训。
- **生产数据边界**：沙盘资产属演练数据域，不触发 P6 正式知识生产；部署 134 沿用备份/隔离恢复/留痕纪律。
- **路径检查器与嵌入终端的"实时性"**：事件扇出是否同步产卡需现场核（异步则检查器需轮询/提示），实施阶段A 验证。

---

# 实现就绪附录（供实施团队直接照做）

> 以下契约均核对自代码（2026-06-13，main=`27924efa`），字段名/路径为权威。所有 API 前缀 `/medkernel/api/v1`（nginx 后）或 `/api/v1`（直连后端）。

## 16. 真实接口契约（编排各步）

### 16.1 标准上下文快照　`POST /engine/context/snapshots`（`context.write`）
请求体（顶层 + `resources` 嵌套，已在幕6 验证通过）：
```json
{
  "request_id": "sbx-snap-<scenarioId>-<ts>", "trace_id": "sbx-<ts>",
  "tenant_id": "<dataScope.tenantId>", "user_id": "<me.userId>",
  "role_codes": ["<me.roles[].code>"], "package_version": "2026.06.1",
  "patientId": "<patientId>", "encounterId": "<encounterId>", "orgUnitId": "<dataScope.hospitalId>",
  "resources": {
    "patient": {"mpi":"...","name":"...","birthDate":"1965-08-15","gender":"MALE","specialPopulations":[],"sourceSystem":"HIS","sourceRecordId":"...","mappedVersion":"2026.06.1","eventTime":"<iso>","receivedTime":"<iso>","qualityStatus":"VALID"},
    "encounters": [{"encounterId":"...","encounterType":"ED","admissionTime":"<iso>","dischargeTime":null,"departmentId":"<orgUnitId>","attendingDoctorId":"...","bedId":"...","sourceSystem":"HIS","sourceRecordId":"...","mappedVersion":"2026.06.1","eventTime":"<iso>","receivedTime":"<iso>","qualityStatus":"VALID"}],
    "observations": [{"observationId":"...","code":"2823-3","displayName":"血清钾","valueNumeric":6.8,"unit":"mmol/L","referenceRange":"3.5-5.5","criticalFlag":"HIGH","sourceSystem":"LIS","sourceRecordId":"...","mappedVersion":"2026.06.1","eventTime":"<iso>","receivedTime":"<iso>","qualityStatus":"VALID"}]
  }
}
```
响应：`data.snapshotId`（如 `ctx-...`）或经 `GET /engine/context/snapshots?patientId=&status=ACTIVE` 回查 `items[0].snapshotId`。`resources` 其它资源类型见 `ContextSnapshotResources`（allergyIntolerances/conditions/nursingAssessments/diagnosticReports/medications/procedures/documents/carePlans/followUps/claims）。

### 16.2 推荐触发　`POST /engine/recommendations/triggers`（`recommendation.write`）
```json
{"triggerCode":"sbx-<scenarioId>","triggerType":"<wire 触发点 如 result-review>","contextSnapshotId":"<快照>","patientId":"...","encounterId":"...","scenarioCode":"<scenarioId>","packageVersion":"2026.06.1","occurredAt":"<iso>","candidateCards":[]}
```
响应：`{triggerId, status(EVALUATED|NO_CARD), cardCount, traceId}`。匹配器对已发布规则评估命中→产卡；`candidateCards` 可空（纯靠已发布资产）。

### 16.3 取推荐卡（嵌入终端读）
- 嵌入页 `EmbedLaunch.tsx` 用 `useRecommendationCards`（`frontend/src/shared/api/hooks.ts:4892`）→ **`GET /engine/recommendations/cards?patientId=&status=ACTIVE`**（`recommendation.read`），响应 `PageResponse<RecommendationCard>`（`data.items[]`，字段含 cardId/cardCode/title/summary/suggestedAction/requiresPhysicianConfirmation/sourceSummary/explanationJson/severity 等）。
- 另有更富的临床视图 `GET /engine/recommendations/clinical-cards`（`RecommendationClinicalCardResponse`，多 triggerId/scenarioCode/triggerType/patientPathwayId 等），路径检查器可用它展示更全字段。
- 实现以 `hooks.ts` 现有 `useRecommendationCards` 为准，沙盘**不改嵌入页**。

### 16.4 临床事件（自动扇出入口）　`POST /engine/clinical-events`（`event.write`）
```json
{"eventId":"sbx-evt-<ts>","eventType":"ORDER","patientId":"...","encounterId":"...","clinicalSetting":"ED","sourceSystem":"HIS","packageVersion":"2026.06.1","triggerPoint":"order-sign","idempotencyKey":"...","callbackWebhookId":null,"payload":{ ...canonical 资源(同 §16.1 resources) },"occurredAt":"<iso>"}
```
`eventType` 仅 `DIAGNOSIS/ORDER/REPORT/DISCHARGE/FOLLOWUP`；`clinicalSetting` 仅 `INPATIENT/OUTPATIENT/ED/FOLLOWUP`；`triggerPoint` 为 wire 值。经 `ClinicalEventEngineDispatcher` 自动扇出到规则/推荐/路径三适配器。**阶段A 须验证此路径是否同步产 `recommendation_card`**；若异步，编排改用 §16.2 显式触发。

### 16.5 签发 embed launch-token　`POST /engine/embed/launch-tokens`（`embed.write`）
```json
{"userId":"<me.userId>","roleCode":"<主角色code>","patientId":"...","encounterId":"...","triggerPoint":"<wire>","hook":null,"hookInstance":null}
```
响应：`{token, expiredAt, embedUrl, launchEndpoint, hook}`。前端 iframe 加载 `/embed/launch?token=<token>`。

### 16.6 兑换/反馈（嵌入终端内，既有 `EmbedLaunch.tsx`，无需改）
- 兑换 `POST /engine/embed/launch`（`embed.read`）→ `EmbedLaunchContextResponse{...,active,parentOrigin,traceId}`（token 一次性消费）。
- 反馈 `POST /engine/embed/feedback`（`embed.write`）`{token,actionType(ADOPT|REJECT),reason}` → `{callbackDelivered,degradationReason,traceId}`。
- 危急值需医师确认时另走 override：`POST /engine/rule/rules/executions/{executionId}/override`（`rule.override`）`{actionCode,reason}`（见幕6）。

## 17. 类型与数据模型

### 17.1 前端场景注册表　`frontend/src/features/sandbox/sandboxScenarios.ts`
```ts
export type EngineKey = "rule"|"pathway"|"recommendation"|"followup"|"evaluation"|"embed"|"terminology"|"package"|"knowledge"|"model";
export type ServicePackage = "pilot-prep"|"clinical-run"|"qc-improve"|"compliance-ops"|"third-party"|"specialty";
export interface SandboxScenario {
  id: string;                       // 'sbx-lab-critical-k'
  servicePackage: ServicePackage;   // 分组
  engine: EngineKey;
  triggerPoint: "patient-view"|"order-sign"|"medication-prescribe"|"result-review"|"discharge-sign"|"followup-alert";
  ruleType?: "DIAGNOSIS"|"ORDER"|"LAB"|"REPORT"|"DISCHARGE"|"FOLLOWUP"|"INSURANCE"|"QUALITY"|"RECORD";
  title: string; narrative: string;
  expectedRuleCode?: string;        // 'P5.ACT4.CRITICAL.K'
  expectedAction?: "INFO"|"REMIND"|"STRONG_REMINDER"|"BLOCK"|"SUGGEST_ORDER";
  expectedSeverity?: "LOW"|"MEDIUM"|"HIGH"|"CRITICAL";
  preset: SandboxContextPreset;     // 预置可被表单覆盖
  status: "ready"|"pending-seed";   // 规则是否已发布
}
export interface SandboxContextPreset { patient: CanonicalPatientInput; encounter: CanonicalEncounterInput; observations?: CanonicalObservationInput[]; orders?: OrderInput[]; medications?: MedicationInput[]; /* 按触发点取用 */ }
```

### 17.2 后端编排　`SandboxRunRequest`/`SandboxRunResponse`（`/engine/sandbox`）
```
POST /engine/sandbox/scenarios/{scenarioId}/run   @PreAuthorize("@perm.has('sandbox.run')")
Body SandboxRunRequest { entryMode: "EVENT"|"SNAPSHOT"|"ADAPTER", contextOverride?: <同 §16.1 resources>, occurredAt? }
Resp SandboxRunResponse {
  scenarioId, traceId,
  steps: [ { stage: "CONTEXT|EVENT|RULE|RECOMMENDATION|TOKEN", endpoint, request(JsonNode), response(JsonNode), serverFacts(Map), status:"OK|FAIL", error? } ],
  snapshotId, triggerId, cardCount, executionIds:[], patientPathwayId?,
  embedToken, embedUrl
}
```
`SandboxOrchestrationService` 进程内顺序调既有服务（不经 HTTP 控制器、不重复 `@PreAuthorize`），聚合 `steps` 轨迹。

## 18. 编排时序（SNAPSHOT 模式，DTO 已全验证；EVENT 模式阶段A 验证后切换）
```
1 ContextSnapshotService.create(§16.1)            → snapshotId            [stage CONTEXT]
2 RecommendationEngineService.trigger(§16.2)       → triggerId,cardCount   [stage RECOMMENDATION]
   (内部经 DeterministicMatcher 评估已发布规则→命中写 rule_execution_log + 产 recommendation_card)
3 (可选) RuleEngineService.evaluate 显式留 executionId（如需 override 演示）  [stage RULE]
4 EmbedEngineService.generateToken(§16.5)          → embedToken,embedUrl   [stage TOKEN]
5 返回 SandboxRunResponse；前端据 embedUrl 加载 iframe → 终端 §16.3 取卡渲染
6 终端内医师 ADOPT/REJECT(§16.6) → postMessage 回宿主面板
7 路径检查器各 stage 展开 request/response/serverFacts
```

## 19. 场景定义（#1 全样例 + 模板；全 15 见 §7）
### 19.1 #1 已就绪样例（实现可直接复用幕6 数据）
- preset：patient `P5-ACT6-CLINICAL-001`/encounter `...-ENC-001`/observation 血钾6.8（code 2823-3, criticalFlag HIGH）；triggerPoint `result-review`；expectedRuleCode `P5.ACT4.CRITICAL.K`；expectedAction `STRONG_REMINDER`；expectedSeverity `CRITICAL`；status `ready`。
### 19.2 新规则 DSL 模板（seed 用，#2-#10 照此填）
```json
{ "trigger": "<wire 触发点>", "version": "cdshooks-1.0",
  "conditions": { "all": [ { "fact":"observations[].valueNumeric", "operator":"gte", "value": 5.5 } ] },
  "actions": [ { "actionCode":"STRONG_REMINDER", "severity":"CRITICAL", "summary":"<卡标题文案>", "detail":"<处置说明，含‘不自动开立或修改医嘱’>", "requiresPhysicianConfirmation": true, "sources":[{"label":"<制度/指南名>"}] } ] }
```
> DSL 中 `trigger` 必须等于场景 triggerPoint 的 wire 值（匹配器据此筛选）；条件 `fact` 路径见字段目录 `GET /engine/context/field-catalog`；动作 `summary/detail/sources` 即推荐卡内容来源。每条规则的具体阈值/字段/文案见 §7 临床内容（待评审定稿后填表）。

## 20. seed 脚本　`scripts/sandbox/seed-scenarios.mjs`（复用幕4 `p5-act4-rule-governance.mjs` 基建）
对每条 `pending-seed` 规则，幂等执行真治理链：
```
0 login(knowledge-governor 或 clinical-governor)；幂等：先 GET /engine/rule/rules?ruleCode= 存在且 PUBLISHED 则跳过
1 POST /engine/rule/rules              (RuleCreateRequest：envelope + ruleCode/name/ruleType/riskLevel/sourceRef/changeSummary/dsl)
2 POST /engine/rule/rules/{id}/test-cases   (REQUIRED_RELEASE_CASE_TYPES 阳/阴/边界用例)
3 POST /engine/rule/rules/{id}/test         (全 PASS 才可发布)
4 POST /engine/rule/rules/{id}/simulate     (选 ACTIVE 快照，期望命中)
5 POST /engine/rule/rules/{id}/governance/signoffs     (委员会会签)
6 POST /engine/rule/rules/{id}/governance/transitions  (GRAY→FULL；高风险/平台发布带 publishEvidence.electronicSignature)
7 服务端回查 rule_definition.status=PUBLISHED / rule_governance.state=FULL
```
脚本须可幂等复跑 `failures=[]`，支持 `SEED_ONLY=<ruleCode,...>` 单条重跑。

## 21. 文件清单
**新增（前端）**：`frontend/src/pages/sandbox/SandboxHost.tsx`、`.module.css`、`SandboxHost.test.tsx`；`frontend/src/features/sandbox/{sandboxScenarios.ts, SandboxDataEntry.tsx, SandboxPathInspector.tsx, SandboxEmbedFrame.tsx}` + 各 `.test.tsx`；`frontend/src/shared/api/hooks.ts` 加 `useRunSandboxScenario`。
**新增（后端）**：`com.medkernel.engine.sandbox.{SandboxScenarioController, SandboxOrchestrationService, SandboxRunRequest, SandboxRunResponse, SandboxStepTrace}` + 测试 `SandboxScenarioControllerSecurityTest`、`SandboxOrchestrationServiceTest`、`SandboxScenarioApiContractTest`。
**新增（脚本/证据）**：`scripts/sandbox/seed-scenarios.mjs`；`scripts/drill/sandbox-fulltruth-run.mjs`；证据目录 `docs/release/evidence/.../sandbox/`。
**改动**：`frontend/src/shared/config/routes.ts`（加 `/sandbox` 条目，placement 受控、`requiredRoles`）+ `routes.test.ts`；`DefaultPermissionPolicy.java`（新增 `sandbox.run`、`menu.sandbox`，给沙盘角色补 embed/recommendation 读权）+ `PermissionCode.java`/`ServiceContractCatalog.java`；权限两处断言 `PermissionDimensionModelTest`+`DefaultPermissionPolicyTest`。

## 22. 测试与验收
**测试**：后端单测/契约（编排序列、轨迹聚合，mock 引擎服务）；前端组件测试 + `routes.test.ts` 前后端守卫一致性；seed 幂等复跑 `failures=[]`；端到端演练脚本服务端回查（`rule_execution_log.hit`、`recommendation_card`、`embed_launch_token`、`recommendation_feedback`/override、`patient_pathway`）。
**验收（每阶段可度量）**：
- 阶段A：clinical-decision-user（或沙盘角色）进 `/sandbox`，#1 一键 run → 路径检查器 7 step 全 OK、右侧 iframe 显真危急值卡、ADOPT/REJECT 回传成功、服务端回查命中+反馈留痕。
- 阶段B：9 规则类型各 1 条 `status=PUBLISHED`，矩阵 5 动作×4 严重度均被场景覆盖；各场景 run 全真命中。
- 阶段C：路径入径推进/随访异常/推荐综合卡/质控闭环各跑通；嵌入 SDK/API 两模式契约可达。
- 阶段D：演示叙事 + 截图证据齐；可达性/守卫回归绿；全量 `mvn test` 绿。

## 23. 实现者注意事项（踩坑预警，务必先读）
1. **canonical 闭集**：`encounterType`/`clinicalSetting` 仅 `INPATIENT/OUTPATIENT/ED/FOLLOWUP`（急诊用 `ED` 非 `EMERGENCY`，否则 `ClinicalSetting.requireCanonical` 抛 `ENG-API-001` 解析错）。
2. **统一入参信封**：路径 enter、规则 evaluate、规则 create、推荐 trigger 都要 `request_id/trace_id/tenant_id/user_id/role_codes/package_version`（`@Valid` 先于 `@PreAuthorize`，缺字段先报 400 掩盖权限）；据真实登录 `/security/me` 的 `dataScope.tenantId`/`userId`/`roles[].code` 动态构建。
3. **标识区分**：路径 enter 用业务标识 `templateId`(pt-…) 非数字主键 `id`；入径响应 `patientPathwayId` 嵌套在 `data.patientPathway` 下。
4. **执行记录无 GET-by-id**（404）：用列表 `GET /engine/rule/rules/executions?hit=true` 按 `executionId` 定位。
5. **嵌入卡来源**：推荐卡内容来自已发布规则动作定义（summary/detail/sources/severity）；无规则=无卡，场景标 `pending-seed` 不伪装。
6. **权限两处断言 + 全量测试**：改 `DefaultPermissionPolicy` 角色菜单/权限白名单须同步 `PermissionDimensionModelTest` 与 `DefaultPermissionPolicyTest` 两处，发布前跑全量 `mvn test`（非定向类），否则 CI `jdk-matrix-smoke` 全红。
7. **医疗安全**：`BLOCK` 必须带强制 override + 不阻断宿主主流程（§12）；动作以提醒/确认为主，不自动开嘱。
8. **生产纪律**（若部署 134）：发布前备份 + 隔离恢复 + 留痕（`destructive_action_performed=false`）；`mk-publish.sh --source <全哈希>` 内置 `COPYFILE_DISABLE=1 tar --no-xattrs`；post-deploy 核 jar SHA=本地构建/服务 active/Flyway/xattr 0/数据保留；演练数据保留不清库（生产库 DELETE 会被守卫拦，演练脚本须设计成幂等）。
9. **CSRF**：API POST 须带 `X-XSRF-TOKEN`（双提交 cookie）。
10. **诚实边界**：不接真实模型厂商（`MODEL_DISABLED` 降级）；不开放 P6 正式知识生产；沙盘资产属演练数据域。
