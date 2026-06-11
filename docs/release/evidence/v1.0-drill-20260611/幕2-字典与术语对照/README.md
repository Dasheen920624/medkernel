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

## 幕8.5 UI 重演（2026-06-11）

本次补做客户视角前台走查。页面能展示标准字典、院内待映射、高危候选、冲突、构建映射包、发布/回滚入口；但没有发现「新建本地映射/手工登记映射」前台入口，无法按剧本在页面里走完草稿→确认→替换→回滚全状态机。本项按体验缺口登记，不能用 API 补做后宣称前台通过。

| 角色 | 页面路由 | 前台动作 | 结果 | 截图 |
|---|---|---|---|---|
| 信息科 | `/terminology/mapping` | 打开字典映射页，查看标准字典、院内待映射、高危候选和冲突 | 总览可读：标准 8、院内待映射 1、高危 1、冲突 1 | [20-ui-terminology-overview.png](ui-replay/20-ui-terminology-overview.png) |
| 信息科 | `/terminology/mapping` | 打开高危候选确认弹窗但不提交 | 必须勾选高危确认并填写理由；未确认钾/钠高危候选 | [21-ui-high-risk-confirmation-modal.png](ui-replay/21-ui-high-risk-confirmation-modal.png) |
| 信息科 | `/terminology/mapping` | 查看冲突待裁列表 | 一对多冲突、风险和待裁说明可见；无前台制造冲突入口 | [22-ui-conflict-readable.png](ui-replay/22-ui-conflict-readable.png) |
| 信息科 | `/terminology/mapping` | 检查构建映射包入口 | 构建草稿弹窗可见，本批不提交新版本以免污染业务命名 | [23-ui-build-package-entry.png](ui-replay/23-ui-build-package-entry.png) |
| 信息科 | `/terminology/mapping` | 检查发布与回滚入口 | 发布/回滚按钮可见但当前状态禁用，缺少可读禁用原因和回滚历史 | [24-ui-package-publish-rollback-entry.png](ui-replay/24-ui-package-publish-rollback-entry.png) |

结构化摘要见 [00-ui-replay-summary.json](ui-replay/00-ui-replay-summary.json)。前台缺口已同步登记到计划 §6.5 与能力可见性矩阵。

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
| `ui-replay/` | 幕8.5 前台重演截图和脱敏摘要，含 UI 缺口登记 |
| `trace-ids.txt` | 本幕 API traceId 汇总 |

## 未通过项

发布/回滚未执行。原因是 `/engine/pkg/packages/release-adapters` 返回空数组，`PackageSyncRequest.adapterIds` 为必填非空。该问题已登记到待处理清单，归属发布适配器/配置包发布阶段；在关闭前，只能说“草稿包已构建”，不能说“运行态已覆盖”。
