# P5 第一阶段正式收口报告

> 日期：2026-06-13
> 状态：随幕10收尾 PR 验证与合并生效
> 范围：P5 第二轮全新演练从干净基线到幕10审计导出审批的第一阶段端到端旅程。

## 1. 收口裁决

P5 第一阶段幕0–幕10端到端演练已在 134 真实环境跑完，并按仓库证据归档。当前结论：

- 第一阶段 B0 主链路已完成真实演练：部署接管、租户/组织/角色、术语、知识诚实边界、规则、路径、临床运行、随访质控、配置包、系统接入和审计导出审批。
- 发现的阻断缺陷均按 TDD 闭环并经对应 PR/部署/复验归档；脚本或数据收敛造成的失败批次均保留，不清库、不改写历史。
- 正式知识生产继续阻断：文献资料库根地址为空，不得进入 P6；无模型、无图、无外部 Provider 时系统按 B0 确定性主链路诚实降级。

## 2. 证据索引

- 总证据目录：`docs/release/evidence/p5-second-fresh-drill-20260612/`
- 阶段检查点：`docs/audit/p5-second-fresh-drill-checkpoint.md`
- 幕10收尾证据：`docs/release/evidence/p5-second-fresh-drill-20260612/幕10-审计导出审批/`
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

## 6. 未声明完成项

- 不声明真实院方 IdP、真实短信/邮件/移动推送、真实外部 Provider、真实 Dify/模型工作流、真实图谱/搜索投影已接通。
- 不声明正式知识生产已开放；文献资料库根地址仍为空，P6 继续阻断。
- 不声明备份恢复已最新演练通过；当前运行态按事实显示 `DEGRADED/NOT_AVAILABLE`。
