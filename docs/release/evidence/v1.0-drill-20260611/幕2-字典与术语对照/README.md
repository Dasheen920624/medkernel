# 幕2 · 字典与术语对照证据

> 日期：2026-06-11
> 环境：`https://193.112.107.134`
> 租户：`drill-hospital-20260611`（演练总医院）
> 凭据位置：服务器 `/zoesoft/medkernel/conf/drill-act1-credentials-20260611.json`，仓库不保存密码或会话凭据。

## 结论

- 已登记 8 条标准术语：ICD-10、LOINC、ATC。
- 已登记 4 条院内术语：LIS、HIS-PHARMACY、HIS-DIAG。
- 已生成 5 条候选，其中 `NA001 血钠 -> LOINC 2823-3 血清钾` 命中钾/钠高危近似，风险 `HIGH`。
- 高危候选批量确认返回 `ENG-TERM-001`，缺少二次确认的逐条确认返回 `ENG-TERM-002`。
- 3 条普通候选已批量确认，本地术语状态推进为 `MAPPED`。
- `ZD0456 社区获得性肺炎` 同时命中 `J15.9` 与 `J18.9`，保留 1 条一对多冲突。
- 已构建术语映射包草稿 `TERM.DRILL.ACT2@2026.06.11-act2-024101`。
- 发布适配器列表为空，映射包未发布；覆盖分析仍按运行态有效快照显示 `UNMAPPED`，不得写成已生效。

## 关键文件

| 文件 | 内容 |
|---|---|
| `00-health.json` | 134 readiness |
| `01-it-ops-security.json` | 信息科账号权限画像 |
| `03-standard-*.json` | 标准术语登记响应 |
| `04-local-*.json` | 院内术语登记响应 |
| `08-candidates-after-generation.json` | 候选总表 |
| `09-high-risk-batch-denied.json` | 高危批量确认拒绝 |
| `10-high-risk-direct-denied.json` | 高危缺少二次确认拒绝 |
| `11-safe-batch-confirm.json` | 普通候选批量确认 |
| `12-mappings-after-confirm.json` | 已确认映射 |
| `13-local-terms-after-confirm.json` | 本地术语 `MAPPED` 状态 |
| `14-open-conflicts.json` | 冲突待裁 |
| `16-package-draft.json` | 术语映射包草稿 |
| `18-release-adapters.json` | 发布适配器为空 |
| `19-summary.json` | 幕2结构化汇总 |
| `20-ui-terminology-mapping.png` | 远端页面总览截图 |
| `21-ui-high-risk-confirmation.png` | 高危逐条确认弹窗截图 |
| `trace-ids.txt` | 本幕 API traceId 汇总 |

## 未通过项

发布/回滚未执行。原因是 `/engine/pkg/packages/release-adapters` 返回空数组，`PackageSyncRequest.adapterIds` 为必填非空。该问题已登记到待处理清单，归属发布适配器/配置包发布阶段；在关闭前，只能说“草稿包已构建”，不能说“运行态已覆盖”。
