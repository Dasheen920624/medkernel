# 会话接力

## 当前执行

- **主线「全流程演练·使用指南·体验重构」：幕0–10 L1/L2、总验收报告和 P1 第一项「提醒与推荐中枢 / 推荐链路一张图」已合入 main；当前转入 P1 下一项 `OPT-VIS-01`（规则自然语言与只读流程图）**（计划 #526；DOC-SYNC #528；幕0–8 = #529–#537；幕9 = #538 `3e4ba441`；实现路径纠偏 #539 `0b0d4cb2`；幕10 L1 = #540 `efbfd9ab`；幕8.5 第一批 = #541 `6aaba08b`；幕8.5 第二批 = #542 `859477f`；幕8.5 第三批 = #543 `516ad753`；幕10 L2 = #544 `dde82115`；总验收与培训材料 #545 `cd2268af`；推荐中枢 #546 `17f4cc4d`）。总体计划见 [docs/superpowers/plans/2026-06-10-full-flow-drill-usability-program.md](superpowers/plans/2026-06-10-full-flow-drill-usability-program.md)。
- **2026-06-11 实现路径纠偏（客户反馈触发，本线当前焦点）**：客户反馈「实现路径与原方案不一致、完全看不懂」。核查实锤：幕0–9 的后端运行链（L1）真实有效、成果保留，但演练逐幕漂移成「API 脚本跑链路」，**客户视角页面走查（L2）系统性缺位**——证据截图幕0=10 张逐幕递减至幕6/7/8/9=0 张；幕5「医生在图上口述路径」判据被 `/simulate` 接口替代；幕9「适配器健康状态页可读」判据无页面证据；四问审计只做了幕0/1，§6.5 登记表幕6后零新增；能力可见性矩阵未建；演练脚本 `/tmp` 即弃未入库（含 `/tmp/act9-third-party-cases.mjs`）；幕8 配置包业务 ID 混入随机批次码；计划「执行结果」段被 UUID 流水污染。
- **纠偏已立法（PR #539）**：计划新增 **§2.5 执行契约**——核心口径=**产品是给客户看和用的，不是给技术人员的**：客户面动作（配置/维护/审批/处理提醒/查看）一律前台完成，API 只许扮演外部系统（HIS/LIS/第三方报文）或铺无关前置数据，**API 替代客户操作=该幕不通过**；触发流程验证=触发源可 API 注入，但接收→处理→闭环必须在前台页面完成留痕。另立每幕 8 条硬性 DoD + 计划文件卫生；新增 **幕8.5 前台重新演练**（用户裁定：前面的也要补回来重新演练——幕0–9 全量前台重演：前台操作/维护规则/触发流程三类验证，先于幕10 收口）；幕6–9「执行结果」段瘦身为一行+证据链接；§6.5 补登 OPT-PKG-01 与四问欠账行；新建 `scripts/drill/` 脚本归档区。**若有在途幕10 会话：幕10 必须按 §2.5 DoD 执行（审计页前台操作为本体），幕10 收口不豁免幕8.5。**
- **用户授权仍有效**：演练、指南、体验重构全程由 AI 自主裁决，无需逐项咨询；质量准绳为最高质量和最佳体验。未上线产品按纯粹实现推进，不为旧低质实现加兼容层。所有外向/生产配置动作必须备份、留痕、可回滚或如实登记不能回滚原因。
- 幕8 主链/发布/后端修复细节见 [幕8证据 README](release/evidence/v1.0-drill-20260611/幕8-配置包与发布治理/README.md) 与 #537；幕9 六案例（C1–C6 全 `PASSED`）与 2 个后端修复（临床事件嵌套事务隔离、嵌入令牌 SQL 参数绑定）细节见 [幕9证据 README](release/evidence/v1.0-drill-20260611/幕9-第三方对接能力案例集/README.md) 与 #538，不在此重复。
- **幕9真实限制（保留登记）**：本地演练脚本访问 134 仍使用自签证书校验绕过，正式部署必须替换院方信任证书；C5 用 `203.0.113.10` TEST-NET-3 断连目标证明 `NOT_CONNECTED`/`DEAD_LETTER`，不是外部厂商真实接收成功；C3 FHIR 出站补偿目标为占位地址，主写入和查询通过，补偿失败状态如实保留。
- **幕10 L1 已完成并合入 main（PR #540，merge `efbfd9ab`）**：runTag `act10-mq8ww9f8`，证据在 [幕10证据目录](release/evidence/v1.0-drill-20260611/幕10-合规审计与降级/README.md)。A1–A7 全 `pass=true`：审计链、数据权限跨科室阻断、脱敏预览、敏感导出二人审批、模型 B0 诚实降级、国产化报告和 schema-only 备份恢复抽查。同步修复：登录 JWT 组织域改用 `org_unit.id`、新增数据权限 check 接口、新增脱敏 preview 接口、导出审批长 evidenceId 稳定压缩。远端发布备份 `/zoesoft/medkernel/backups/deploy-20260611-110134`，jar SHA `559c1ad8630df4dc34fe57c799b290e9c58a86fcc9b0efa8a7a1621aab02725a`，演练备份 `/zoesoft/medkernel/backups/act10-mq8ww9f8.schema.dump`。
- **幕10真实限制（必须保留）**：L1 后端/运维链路已实证，L2 前台走查本分支已补齐；仍不得把 API JSON 证据当作页面验收。`node scripts/drill/act10-audit-degrade.mjs` 仍因 134 自签证书设置 `NODE_TLS_REJECT_UNAUTHORIZED=0`，正式部署必须换院方信任证书。schema-only 恢复抽查只验证表结构与 Flyway 表存在，`migrationCount=0` 是 schema-only 预期。L2 继续登记两个体验缺口：`UI-ACT10-AUDIT-01`（审计页缺 traceId 直搜和诊断链跳转）、`UI-ACT10-SECBASE-01`（安全基线页缺权限试算和脱敏预览面板）。
- **幕8.5 第一批实证（已合入 #541）**：`node scripts/drill/act85-ui-replay-acts0-2.mjs` 已在 134 真实前台完成幕0–2 复演，截图落各幕 `ui-replay/`；幕0 4 步、幕1 7 步、幕2 5 步，全部带 URL 标头。幕2发现 `OPT-TERM-UI-01`：`/terminology/mapping` 能查看候选/冲突/构建包/发布入口，但不能前台新建映射、制造冲突、替换/回滚单条映射；禁用原因也不够直接。
- **幕8.5 第二批实证（已合入 #542）**：`node scripts/drill/act85-ui-replay-acts3-5.mjs` 已在 134 真实前台完成幕3–5 复演，截图落幕3–5 `ui-replay/`；幕3 3 步、幕4 4 步 / 8 图、幕5 4 步 / 7 图，全部带 URL 标头。新增/细化缺口：`OPT-KNOW-UI-01`、`OPT-VIS-01`、`OPT-VIS-02`、`OPT-PATH-UI-01`。
- **幕8.5 第三批实证（已合入 #543）**：`DRILL_START_ACT=9 node scripts/drill/act85-ui-replay-acts6-9.mjs` 已接续前序摘要并在 134 真实前台完成幕6–9 复演，截图落幕6–9 `ui-replay/`；幕6 15 图、幕7 11 图、幕8 4 图、幕9 6 图，全部带 URL 标头。新增/细化缺口：`OPT-WORKFLOW-01`、`OPT-FOLLOWUP-01`、`OPT-PKG-01`、`UI-ACT9-ADAPTER-01`。
- **幕10 L2 实证（已合入 #544）**：`node scripts/drill/act10-l2-ui-replay.mjs` 已在 134 完成真实前台走查，runTag `act10-l2-mq93tngz-9425`，截图落 [幕10 ui-replay](release/evidence/v1.0-drill-20260611/幕10-合规审计与降级/ui-replay/)；共 16 张 1440x1100 带 URL 截图。审计员在 `/admin/audit` 前台筛选审计事件、打开详情、提交审计日志导出申请；医院管理员在 `/admin/audit` 前台审批；信息科在 `/security/baseline`、`/system/providers`、`/advanced/domestic` 查看安全配置、数据权限、脱敏、运行状态、国产化自检并导出报告。验证与 CI 已通过并合入 main。
- **P1 体验实现进展（PR #546，merge `17f4cc4d`）**：`/cdss/fatigue` 已升级为「提醒与推荐中枢」，新增推荐链路总览、患者 / traceId / 来源对象本页检索、抽屉内「这条推荐是怎么来的」七段链路（触发事件→命中规则→知识来源→路径上下文→待办 / 通知→医生反馈→药师复核），并修正移动端筛选表单布局。本地验证、GitHub Actions、134 前端发布和医生账号桌面 / 390px 移动复验均已通过；134 发布备份 `/zoesoft/medkernel/backups/deploy-20260611-154250`，复验证据见 [推荐中枢 134 复验](release/evidence/v1.0-drill-20260611/P1-体验重构/推荐中枢-134复验/README.md)。`OPT-IA-01` / `OPT-TRACE-01` 第一批销项；待办/通知状态同步仍归 `OPT-WORKFLOW-01`。

## 当前状态

- main：`17f4cc4d`（推荐中枢 #546 已合）；当前分支 `codex/demo-drill-recommendation-hub-134-proof` 只补 134 复验证据与接力状态，下一轮实现应从最新 main 开 `OPT-VIS-01` 分支。
- 134：演练数据在库**未清**，幕1 的 9 个角色账号可用（凭据在服务器 `/zoesoft/medkernel/conf/drill-act1-credentials-20260611.json`），正好支撑后续体验实现复验；后端为幕10 L1 发布版本（backup `/zoesoft/medkernel/backups/deploy-20260611-110134`），前端为 #546 推荐中枢版本（backup `/zoesoft/medkernel/backups/deploy-20260611-154250`），readiness `UP`。
- 指南现状：4 本手册 + 第三方对接案例集 + 3 本角色培训均有实质内容且章节结构合规；合规运维手册已补幕0、幕9、幕10 UI 复演图和审计/权限/脱敏/审批/降级章；试点准备手册已补幕1–4、幕8 UI 复演图和幕2/3/4/8缺口；临床运行手册已补幕5–7 UI 复演图和推荐/路径/随访缺口；质控改进手册已补幕7 UI 复演图。

## 下一步

1. 合并当前复验证据分支后，从最新 main 开 `OPT-VIS-01`：在规则库 / 校验页做规则自然语言回显与只读流程图，先补失败测试，再实现，再本地 + 134 复验。
2. 继续体验实现线后续优先级：`OPT-VIS-02` + `OPT-PATH-UI-01`（医生只读路径图）、`UI-ACT10-AUDIT-01`（traceId 搜索与诊断链跳转）。
3. P1 体验项全部合入并在 134 复验后，发起独立指南验收；六项全绿后再按总体计划 §8 备份、清库、重迁和 health 校验，最后在 `_HANDOFF.md` 宣告进入第二阶段。
