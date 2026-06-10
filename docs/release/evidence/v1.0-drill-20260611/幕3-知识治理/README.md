# 幕3 · 知识治理证据

> 日期：2026-06-11
> 环境：`https://193.112.107.134`
> 租户：`drill-hospital-20260611`（演练总医院）
> 凭据位置：服务器 `/zoesoft/medkernel/conf/drill-act1-credentials-20260611.json`，仓库不保存口令、恢复码或会话值。

## 结论

- 已登记 CAP 指南、国家卫健委临床实验室管理办法、演练医院危急值目录等来源。
- 已发布 `CAP 经验性抗感染推荐`，当前版本 ID 为 `4`，来源证据数为 1。
- 已发布 `血钾危急值阈值与报告时限`，当前版本 ID 为 `6`，来源证据数为 1。
- 已完成血钾资产租户级升级：旧租户级版本 `5` 变为 `SUPERSEDED`，当前版本 `6` 为 `ACTIVE`。
- 已验证未绑定来源不可发布、高风险缺电子签名不可发布、跨来源引用被拒绝、重复内容跳过、引用片段偏移校验、来源追溯按租户级上下文解析。
- 已修正前端知识治理页会签提交 ID：后端 `/candidates/{candidateId}/review` 的 path id 实际为 `CandidateClassification.id`，不能使用候选版本 ID。
- 知识治理页和来源追溯页均已用远端真实账号截图；截图不含口令或会话值。

## 关键文件

| 文件 | 内容 |
|---|---|
| `00-actors-and-permissions.json` | 演练角色与权限画像 |
| `01-health-readiness.json` | 134 readiness |
| `02-source-cap-guideline.json` | CAP 指南来源登记 |
| `07-citation-cap-v1.json` | CAP 知识版本来源引用 |
| `08-cap-publish-without-signature-denied.json` | 高风险缺签名发布拒绝 |
| `09-cap-publish-approved.json` | CAP 知识发布成功 |
| `11-source-nhc-lab-management.json` | 国家卫健委临床实验室管理办法来源登记 |
| `19-potassium-activate-without-citation-denied.json` | 血钾版本无引用发布拒绝 |
| `21-cross-source-citation-denied.json` | 跨来源引用拒绝 |
| `27-potassium-v2-publish-approved.json` | 血钾部门级升级发布 |
| `35-cap-tenant-scope-publish-approved.json` | CAP 租户级发布 |
| `39-potassium-v3b-tenant-scope-approved.json` | 血钾租户级基线发布 |
| `44-potassium-v4-tenant-upgrade-approved.json` | 血钾租户级升级发布 |
| `45-cap-provenance-final.json` | CAP 最终来源追溯 |
| `46-potassium-provenance-final.json` | 血钾最终来源追溯 |
| `47-cap-versions-final.json` | CAP 版本列表 |
| `48-potassium-versions-final.json` | 血钾版本列表与替代关系 |
| `49-review-queue-final.json` | 发布后审核队列 |
| `50-ui-knowledge-governance.png` | 远端知识治理页截图 |
| `51-ui-provenance-potassium.png` | 远端血钾来源追溯截图 |
| `52-ui-screenshot-check.json` | 截图链路校验摘要 |
| `32-summary.json` | 幕3结构化汇总 |
| `trace-ids.txt` | 本幕 API traceId 汇总 |

## 来源说明

- CAP 指南来源登记引用 [CAP 2016 经验性抗感染相关指南发布页](https://www.medjournals.cn/journalContribute/getContributeInfo.do?bizId=2036)。
- 临床实验室管理制度来源登记引用 [国家卫健委《医疗机构临床实验室管理办法》](https://www.nhc.gov.cn/zwgk/wtwj/201304/f4d5cbc861fd43bb928d6ea124f87a19.shtml)。
- 血钾运行态当前版本引用演练医院危急值目录锚点；公开法规只作为制度背景来源，不把法规文本伪造成院内阈值。

## 未通过项

- 多来源证据合并仍受当前引用模型限制：同一知识版本的引用片段必须来自该版本绑定的来源版本，跨来源引用被真实拒绝。多来源证据增强已登记到待处理清单，不影响当前主来源可追溯。
- 客户租户知识身份未执行平台级退役。当前后端退役接口仅允许平台主租户 `t-1` 操作，幕3用版本替代关系证明旧版本不再作为当前权威版本。
