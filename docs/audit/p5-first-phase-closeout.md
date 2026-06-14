# P5 第一阶段收口复核报告

> 日期：2026-06-14
> 状态：主线正式收官；PR #600 已合并，远端 CI 8/8 通过，功能收官提交 `b410f5a3`
> 范围：P5 第二轮全新演练从干净基线到幕10审计导出审批的第一阶段端到端旅程。
> 当前目标环境：134 manifest `e7392c8f`，readiness 200，沙盘与整改闭环已留证

## 1. 收口裁决

P5 第一阶段幕0–幕10既有演练已在 134 真实环境跑完并按仓库证据归档；完整性复核发现的院内业务系统嵌入链路、沙盘、遗留整改和本地覆盖缺口已在当前分支补齐并部署复演。当前事实：

- 第一阶段 B0 主链路已完成真实演练：部署接管、租户/组织/角色、术语、知识诚实边界、规则、路径、临床运行、随访质控、配置包、系统接入和审计导出审批。
- 发现的阻断缺陷均按 TDD 闭环并经对应 PR/部署/复验归档；脚本或数据收敛造成的失败批次均保留，不清库、不改写历史。
- 2026-06-14 最终目标环境部署为 `e7392c8f`：发布前备份与隔离恢复通过，部署后 manifest/jar/readiness/Flyway/表计数/xattr 通过；最终目标状态保持 `medkernel/nginx/postgresql=active`、HTTPS readiness 200。
- 全真体验沙盘目标环境复演通过：6 个已评审可运行场景 `failures=[]`，9 个未评审场景继续阻断；IFRAME/SDK/API 嵌入模式均可兑换令牌；沙盘评估场景形成 `resultCount=1/findingCount=1/taskCount=1`。
- 第一阶段整改闭环通过：幕7 历史遗留已在早前关闭，本轮沙盘评估演练新增 4 条整改任务由临床治理角色提交、质量治理角色复核关闭；最终整改报告 `totalTasks=7/openTasks=0/closureRate=1`。
- P5 核心只读 readiness 探针通过：7 类角色、21 个代表 API 均可达，未发现演示或固定医学文本。
- 正式知识生产继续阻断：文献资料库根地址为空，不得进入 P6；无模型、无图、无外部 Provider 时系统按 B0 确定性主链路诚实降级。

因此 P5 第一阶段收官主线结论成立：PR #600「P5第一阶段最终收官复演闭环」已于 2026-06-14 09:46（Asia/Shanghai）squash 合并，远端 CI run `27484891439` 8/8 通过，功能收官合并提交 `b410f5a356161a41eca4e434ee2b9a8adda974fc` 已包含本报告、代码与证据。部署边界仍需诚实区分：134 目标环境实测程序 manifest 为 `e7392c8f`；`b410f5a3` 是主线 squash 合并提交，尚未按发布流程重发到 134。

## 2. 证据索引

- 总证据目录：`docs/release/evidence/p5-second-fresh-drill-20260612/`
- 阶段检查点：`docs/audit/p5-second-fresh-drill-checkpoint.md`
- 幕10收尾证据：`docs/release/evidence/p5-second-fresh-drill-20260612/幕10-审计导出审批/`
- 第一阶段最终收官本地门禁摘要：`docs/release/evidence/p5-second-fresh-drill-20260612/第一阶段最终收官/02-local-gate-summary.json`
- `e7392c8f` 发布前备份、部署与最终目标状态：`docs/release/evidence/p5-second-fresh-drill-20260612/第一阶段最终收官/deploy-e7392c8f/`
- 沙盘全真复演：`docs/release/evidence/p5-second-fresh-drill-20260612/sandbox/00-sandbox-summary.json`
- 整改闭环：`docs/release/evidence/p5-second-fresh-drill-20260612/第一阶段最终收官/01-rectification-closeout.json`
- 核心 readiness：`docs/release/evidence/p5-second-fresh-drill-20260612/core-readiness/p5-core-readiness-probe.json`
- 接力状态：`docs/_HANDOFF.md`

## 3. 幕级结果

| 幕 | 结果 | 关键证据 |
| --- | --- | --- |
| 幕0 部署接管与首次登录 | 通过 | 首发管理员接管、首次改密、MFA 与独立重登录 |
| 14 角色与基础就绪 | 通过 | 租户、组织树、客户/平台职责角色、菜单与核心探针 |
| 幕2 术语与字典 | 通过 | 高危候选驳回、普通候选确认、映射包发布链回收 |
| 幕3 知识治理诚实边界 | 通过 | 零知识空态、AI 能力 BASELINE、文献根非法值拒绝 |
| 幕4 规则治理 | 通过 | 危急值红线、测试用例、试运行、会签、全量发布 |
| 幕5 路径治理 | 通过 | 路径模板创建、试运行、灰度与院级全量 |
| 幕6 临床运行 | 通过 | 患者入径、规则命中、医师确认 override |
| 幕7 随访质控 | 通过 | 随访计划、异常回院、结果回流、整改复核 |
| 幕8 配置包与发布治理 | 通过 | v1/v2 发布、离线包、差异、重复导入 409、回滚 |
| 幕9 系统接入正幕 | 通过 | HIS HEALTHY、EMR NOT_CONNECTED、接入申请、死信重放 |
| 幕10 审计导出审批 | 通过 | 自审批拒绝、真实 CSV、SM3/SM2 验签、证据包、运行态诚实降级 |

## 4. 幕10收口锚点

幕10 canonical 批次：`p5-act10-audit-20260613-234800`，`00-act10-summary.json failures=[]`。

- 导出申请：`exp-audit-event-p5-act10-60613-234800`
- 自审批负向：`403 / ENG-API-004`
- 导出任务：`6777d2b2-f0e6-4668-b1bc-df9b8fb1673d`
- 导出文件：`audit-events-export.csv`，75838 bytes
- 导出摘要：`sm3:45da5bd18e13717d78aece32926e7c32f0c991f6a96bf47073c30b30ba0a188d`
- 审批证据：`evd-exp-audit-event-p5-act10-60613-234800-approval`
- 导出证据：`evd-exp-audit-event-p5-act10-60613-234800-export`
- 证据包：`sm3:a2be67e6e512bc2abfa0cba7f8508b1435edec48ed2b535c2d22b7074608126a`

证据包内的 `signatureValue` 是可验签的公开签名材料，不是私钥、口令或令牌；仓库证据未写入密码、MFA 密钥、恢复码、Cookie 或 Token。

## 5. 本批验证

- `node --check scripts/drill/p5-act10-audit-export-approval.mjs`：通过。
- `DRILL_RUN_TAG=p5-act10-audit-20260613-234800 node scripts/drill/p5-act10-audit-export-approval.mjs`：通过，`failures=[]`。
- `npm test -- src/pages/compliance/AdminAudit.test.tsx src/pages/compliance/SystemProviders.test.tsx src/shared/api/hooks.test.ts`：3 文件 / 109 测试通过。
- `node scripts/authenticity-guard.mjs --mode=all`：扫描 1582 文件，通过。
- `node scripts/config-boundary-guard.mjs --mode=inventory`：扫描 1492 文件，通过。
- `scripts/check-comment-zh.sh`：0 fail / 0 warn。
- `git diff --check`：通过。
- 幕10 JSON 证据可解析，PNG/CSV/NDJSON 文件非空。
- PR #596 CI：8/8 通过并合并。
- PR #600 CI：8/8 通过并合并为功能收官提交 `b410f5a3`。

## 6. 未声明完成项

- 不声明真实院方 IdP、真实短信/邮件/移动推送、真实外部 Provider、真实 Dify/模型工作流、真实图谱/搜索投影已接通。
- 不声明正式知识生产已开放；文献资料库根地址仍为空，P6 继续阻断。
- 不声明 134 已部署主线 squash 提交 `b410f5a3`；134 已验证运行提交为 `e7392c8f`，如需重发主线须按发布流程重新备份、隔离恢复、部署和取证。
- 不声明未评审沙盘场景已可运行；9 个未评审场景继续按 `CLINICAL_REVIEW_REQUIRED` 阻断。
- 不把收敛期失败批次或沙盘生成的历史数据清理成“干净通过”；目标库保留真实演练数据。

## 7. 2026-06-14 收敛验证

- `node --check scripts/drill/sandbox-fulltruth-run.mjs && node --check scripts/drill/p5-first-phase-rectification-closeout.mjs && node --check scripts/sandbox/seed-scenarios.mjs && node --test scripts/sandbox/scenario-rules.test.mjs`：通过，3 个 Node 测试通过。
- 后端定向回归：沙盘、主数据同步、身份/人员适配、安全权限、模板资产与迁移合同相关组合均通过；最终聚焦 owner/模板/迁移回归 110 项通过。
- `cd medkernel-backend && mvn test`：2282 项测试通过，0 failure / 0 error / 0 skipped；覆盖 `FlywayMultiDialectSmokeTest`，H2/PostgreSQL/Oracle 迁移到 V123 并验证重复迁移。
- `cd frontend && npm test`：94 个文件 / 695 项测试通过；覆盖沙盘、嵌入、术语一对多冲突处置、配置包、路径、规则、随访、审计与运维页面。
- `cd frontend && npm run build && npm run lint`：通过，ESLint 无 error。
- T-GATE：真实性门禁扫描 1633 文件通过；配置边界扫描 1492 文件通过；迁移规约扫描 25 文件通过；中文注释 0 fail / 0 warn；`git diff --check` 通过。
- 目标环境发布前备份与隔离恢复：`deploy-e7392c8f/predeploy-backup-e7392c8f.properties`，`restore_status=PASSED`，Flyway/表/知识包/质控指标/Origin 白名单/沙盘触发计数主库与恢复库一致，`restore_cleanup_database_count=0`。
- 目标环境部署与 post-deploy：`deploy-e7392c8f/postdeploy-e7392c8f.properties`，manifest/jar 指向 `e7392c8f`，HTTPS readiness 200，Flyway `123|success=123|max_rank=123`，181 张表，AppleDouble 0。
- 沙盘复演：`sandbox/00-sandbox-summary.json`，6 个可运行场景 `failures=[]`，9 个未评审场景阻断，SDK/API 模式兑换成功。
- 整改复演：`01-rectification-closeout.json`，4 条沙盘评估整改任务提交并复核关闭，最终 `openTasks=0`。
- 核心 readiness：`core-readiness/p5-core-readiness-probe.json`，`status=PASSED`，21 个探针通过。
- 最终目标状态：`deploy-e7392c8f/final-target-state-e7392c8f.properties`，readiness 200，沙盘触发 44、沙盘路径 8、随访计划 8、评估运行 4，整改 `open=0`，AppleDouble 0。
