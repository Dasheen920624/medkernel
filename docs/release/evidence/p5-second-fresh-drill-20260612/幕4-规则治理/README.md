# P5 幕4 · 规则治理（治理侧完整旅程到院级全量）

> 执行日期：2026-06-13
> 环境：`https://193.112.107.134`，manifest `f75f7edbe57035c7b7409454f9c8ed8935be344d`（jar SHA-256 `2774c6b558039b916a1346863cb855796f60c705d49e25b8b824592296b6a5c9`）
> 脚本：[scripts/drill/p5-act4-rule-governance.mjs](../../../../../scripts/drill/p5-act4-rule-governance.mjs)（阶段闸门 `seed|create|cases|simulate|govern|all`，幂等可断点续跑；成功判定一律以服务端回查为准）
> 凭据：服务器受控文件本机副本，不入仓库

## 1. 目的

本幕走通**红线规则治理侧完整旅程**：集成运维员铺底标准上下文快照 → 机构知识治理员真实前台用「危急值回报」模板创建血钾危急值红线规则 → 医疗安全红线断言 → 四类发布门禁测试用例全绿 + 真实快照试运行命中 → 治理链 DRAFT→同行评审→委员会双人独立会签→影子→灰度(CANARY)→**院级全量(FULL)**。规则真实执行与医师确认留幕6。

## 2. 红线规则与铺底快照

- 规则 `P5.ACT4.CRITICAL.K`（`rule-95d0454a-fe4a-4e80-938a-45a3744bde98`）：风险 **CRITICAL**，检验项编码 `2823-3`（血清钾）、阈值 `≥5.5 mmol/L`，命中动作 **STRONG_REMINDER**（强提醒「需立即回报并人工确认」），**需医师确认、不自动开立或修改医嘱**，包版本 `2026.06.1`。
- 4 份 ACTIVE 标准上下文快照（集成运维员经 API 模拟外部系统铺底，仅集成/系统角色有 `context.write`）：阳性 6.8、边界 5.5、阴性 4.2、血钠冲突 160（血钠危急但无血钾命中项，规则按 code 过滤不应误触发）。

## 3. 旅程结构与结论（`00-act4-summary.json`，failures=[]）

| 段 | 角色 | 动作 | 服务端断言 | 结论 |
|---|---|---|---|---|
| 1 | 集成运维员 | API 铺底 4 份标准上下文快照 | `context_snapshot` ACTIVE=4 | ✅ |
| 2 | 机构知识治理员 | `/rule/definitions` 模板创建红线规则草稿 | `rule_definition` 1 条，CRITICAL、检验项 2823-3、阈值 5.5 | ✅ |
| 3 | 医疗安全红线断言 | 定义时验证 | 动作为提醒类、`requiresPhysicianConfirmation`、`blocking`「不自动开立或修改医嘱」 | ✅ |
| 4 | 机构知识治理员 | 补齐四类门禁用例 + 阳性快照试运行 | 四类用例 POSITIVE/BOUNDARY/NEGATIVE/CONFLICT 全 PASS；阳性试运行命中（强提醒 + 必须医师确认） | ✅ |
| 5.1 | 机构知识治理员 | DRAFT → 提交同行评审 | `PEER_REVIEW` | ✅ |
| 5.2 | 临床治理员 | 同行评审通过 | 同行评审 APPROVED | ✅ |
| 5.3–5.4 | 临床治理员 + 质量治理员 | 委员会**双人独立**会签 | `COMMITTEE` 会签 2/2（独立成员） | ✅ |
| 5.5 | 机构管理员（职责分离发布人） | COMMITTEE → 进入影子运行 | `SHADOW` | ✅ |
| 5.6 | 机构管理员 | SHADOW → 进入灰度验证 | `CANARY` | ✅ |
| 5.7 | 机构管理员 | CANARY → **院级全量激活（独立电子签名）** | `rule_governance.state=FULL` | ✅ |

法定角色（客户租户）：作者=机构知识治理员；委员会双人独立会签=临床治理员 + 质量治理员；唯一职责分离合规发布人=机构管理员（后端 `validateTransition` 强制作者/会签人/发布人相互分离）。

## 4. 关键服务端回查（2026-06-13，134 上 `f75f7edb`）

- `rule_governance`（`rg-01KTZHK9Y843SX3S7N1MTSYYKX`）：`state=FULL`、`required_signoffs=2`、`review_round=1`。
- `rule_signoff`（版本 `rv-97b94b1e-b4fd-4e7d-8866-f8843ce85320`）：
  - `PEER_REVIEW` 第1轮 clinical-governor `APPROVED`；
  - `COMMITTEE` 第1轮 clinical-governor `APPROVED` + quality-governor `APPROVED`（**双人独立会签，签名人互异**）。
- `mk_version_release_plan`（RULE / `P5.ACT4.CRITICAL.K`）发布计划演进：`IN_REVIEW(ALL)` → `APPROVED(ALL)` → `GRAY(FACILITY)` → **`PUBLISHED(ALL)` 携带独立电子签名**：
  - `electronic_signature_id=esig-p5-act4-…`、`electronic_signature_subject=clinical-governor|临床治理负责人`（**复核人独立于发布人机构管理员**）、`electronic_signature_hash` 存在（SHA-256）、`electronic_signature_signed_at=2026-06-13 14:23:51`。
  - 后端 `VersionReleaseService` 高风险/红线发布电子签名门：未提供合法独立电子签名时 FULL 必被拒；能到达 `PUBLISHED(ALL)` 即证明电子签名被捕获并通过校验。
- 试运行：`hit=true`、`strongReminderVisible=true`、`physicianConfirmVisible=true`。

## 5. 截图证据（全部带 URL 栏）

| 文件 | 内容 |
|---|---|
| `04-ui-rule-readable-safety.png` | 红线规则医疗安全断言：提醒类动作、需医师确认、不自动开嘱 |
| `06-ui-testcases-run-result.png` | 四类发布门禁用例执行结果全 PASS |
| `07-ui-simulate-hit.png` | 阳性快照（血钾 6.8）试运行命中：强提醒 + 必须医师确认 |
| `14-ui-gov-activate-full.png` | 院级全量激活成功（含独立电子签名）：步骤条到「全量」、委员会会签 2/2、三条签署记录、成功提示「规则已完成院级全量激活」 |

> 幂等说明：本会话从 CANARY 续跑（前序会话已推进到灰度），`DRILL_PHASE=govern` 真实执行 CANARY→FULL 并捕获 `14`；随后 `DRILL_PHASE=all` 幂等复跑（seed/create 跳过、cases/simulate 复跑、govern 因终态 FULL 跳过确认），重拍 `04/06/07` 并刷新 `00-act4-summary.json`（failures=[]）。已抵达终态的前序治理步骤不重复造数、不重拍。

## 6. 旅程中暴露并 TDD 闭环的三项阻断缺陷

红线规则治理是跨四个法定客户角色的密集旅程，真实前台走查实锤三处「后端授权且产品要求、前端却进不去/采不到」的阻断缺陷，均已 TDD 闭环并合并 `main`、精确部署 134：

| 缺陷 | PR / 合并 | 根因 | 修复 |
|---|---|---|---|
| [P5-ACT4-01](defect-p5-act4-01-discovery/README.md) | [#582](https://github.com/Dasheen920624/medkernel/pull/582) → `3532f624` | 质量治理员（法定委员）缺 `menu.rule-definitions`，被前端路由守卫挡死，双人独立会签走不完 | `qualityGovernancePermissions` 增补菜单 |
| [P5-ACT4-02](defect-p5-act4-02-discovery/README.md) | [#583](https://github.com/Dasheen920624/medkernel/pull/583) → `7978c9d6` | 机构管理员（唯一职责分离合规发布人）缺 `menu.rule-definitions`，影子/灰度/全量无人能发 | `organizationAdministrationPermissions` 增补菜单 |
| [P5-ACT4-03](defect-p5-act4-03-discovery/README.md) | [#584](https://github.com/Dasheen920624/medkernel/pull/584) → `f75f7edb` | 红线规则院级全量缺独立电子签名捕获，FULL 被后端门禁拒 | `RuleDefinitions.tsx` 高风险 FULL 弹独立电子签名弹窗、`handleGovernanceTransition` 回传 `publishEvidence` |

回归：`DefaultPermissionPolicyTest` / `PermissionDimensionModelTest` / `RuleDefinitions.test.tsx` 全绿。

## 7. 诚实性说明

- 本幕全程以服务端回查为成功判定，前台截图作可见性佐证。规则创建、会签、发布推进均走真实前台（携带 XSRF 双提交令牌）；铺底快照走集成运维员 API 仅用于模拟外部系统前置（临床角色只有 `context.read`，无 `context.write`）。
- 红线规则停在**治理侧闭环（院级全量激活）**：规则真实执行与医师确认在幕6 临床运行触发，本幕不模拟临床命中下的开嘱动作。
- 未配置任何正式文献资料库根地址、未生成任何正式知识；`knowledge_package=1` 为幕9 演练映射包，非正式知识生产；P6 继续阻断。
