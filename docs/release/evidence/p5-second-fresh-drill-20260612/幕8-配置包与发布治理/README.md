# 幕8 · 配置包与发布治理证据

> 批次：`p5-act8-20260613-225241`
>
> 环境：134 真实环境；后端已发布 `f347924a`，readiness 200。
>
> 结论：配置包 v1 基线全量发布、v2 灰度发布、v2 全量发布、差异导出、离线包导出、重复导入 409 保护、高危确认回滚 v2 -> v1 均通过，`failures=[]`。

## 关键事实

| 项 | 值 |
|---|---|
| 目标机构 | `P5-HOSP` / `01KTXW1ER1K0N7CS4YSJZV2XH6` |
| 发布适配器 | `p5-his-gateway`，`ACTIVE/HEALTHY`，`connectorAvailable=true` |
| Canonical 包编码 | `P5.ACT8.CONFIG.260613225241` |
| v1 基线 | `2026.06.1-act8-260613225241-v1`，发布后 `ACTIVE`，最终回滚后仍 `ACTIVE` |
| v2 候选 | `2026.06.1-act8-260613225241-v2`，灰度后 `PUBLISHED`，全量后 `ACTIVE`，回滚后 `OFFLINE` |
| 灰度发布计划 | `c0f92a53-12d3-4263-9fee-429ccf7ef09a` |
| 全量发布计划 | `0dbc7258-71bd-4963-9e44-d6cf87191f6b` |
| 回滚同步 | v1 回滚后同步日志成功数 `2` |
| 差异导出 | `diffAddedCount=3`，`diffChangeCount=3` |
| 离线包导出 | `14503` bytes；同租户重复导入返回 `409` |
| 质控指标 | `P5.ACT7.FOLLOWUP.QUALITY` v2 `ACTIVE`，组织域 `tenant:p5-hospital` |

## 证据文件

| 文件 | 内容 |
|---|---|
| `00-act8-summary.json` | 最终 PASS 汇总、关键包 ID、发布计划、导出和回滚结果 |
| `00-readiness-actors-adapter.json` | 机构管理员、质量治理员、临床治理员权限画像；目标机构和发布适配器状态 |
| `01-evaluation-asset-resolution.json` | 质控指标可继承版本选择，最终使用 v2 `tenant:p5-hospital` |
| `01-source-assets.json` | 入包资产：术语、规则、路径、质控指标 |
| `02-package-release-lifecycle.json` | v1/v2 创建、校验、灰度发布、全量发布服务端事实 |
| `03-v2-vs-v1-diff-export.jsonl` | v2 相对 v1 的差异导出 |
| `04-v2-offline-package.json` | v2 离线包导出 |
| `05-v2-sync-evidence.jsonl` | v2 发布同步证据 |
| `07-v1-rollback-sync-evidence.jsonl` | 回滚后 v1 同步证据 |
| `09-post-drill-db-state.properties` | 演练后数据库状态、失败留痕包、v2 指标状态 |
| `deploy-9261346a/` | 首次配置包解析修复发布前后证据 |
| `deploy-f347924a/` | 质控指标组织域修复发布前备份、隔离恢复、部署后核验证据 |
| `01-*.png`、`02-*.png`、`06-*.png`、`08-*.png` | 配置包中心和发布治理入口截图，均带 URL 栏 |
| `trace-ids.txt` | 请求 traceId 索引 |

## 修复与失败留痕

- 首次真实演练在 v2 灰度发布时失败：`EVALUATION:ei-d718e273...@1` 未接入统一版本资产。根因是配置包解析未把质控指标内部 ID 映射到 `indicatorCode`，已提交 `9261346a` 修复并部署；部署前备份隔离恢复通过。
- 第二次真实演练仍失败：旧幕7质控指标 v1 的统一资产组织域为展示名 `P5第二轮演练机构`，不在目标机构继承路径内。已提交 `f347924a`，质控服务登记/发布统一资产时将展示语义规范为可继承的 `tenant:<tenantId>`；幕7脚本也改为写入 `tenant:p5-hospital`。
- `f347924a` 首次复跑又在规则条目失败：演练脚本把 `rule.packageVersion=2026.06.1` 当成统一资产版本号，真实规则统一版本号为 `1`。脚本已修正为规则版本号缺省 `1`，最终 PASS run 为 `p5-act8-20260613-225241`。
- 所有失败产生的真实包均保留：`P5.ACT8.CONFIG.781361049473`、`P5.ACT8.CONFIG.260613224122`、`P5.ACT8.CONFIG.260613225142` 的 v1/v2 状态记录在 `09-post-drill-db-state.properties`，未清库、未伪造通过。

## 部署与备份

- `9261346a`：远端 manifest=`9261346a`，jar SHA `48bd5e3a...`，readiness 200；发布前备份 `/zoesoft/medkernel/backups/p5-act8-9261346a-predeploy-20260613-223706`，隔离恢复 `restore_status=PASSED`。
- `f347924a`：远端 manifest=`f347924a`，jar SHA `33d78f2e...` 与本地构建一致，服务 `medkernel/nginx/postgresql` 均 active，HTTPS readiness 200；发布前备份 `/zoesoft/medkernel/backups/p5-act8-f347924a-predeploy-20260613-224847`，隔离恢复 `restore_status=PASSED`。

## 医疗安全与边界

- 本幕仅治理配置资产发布、同步、导出、导入冲突保护和回滚，不自动开嘱、不修改患者诊疗事实。
- v2 发布包含规则、路径、质控指标等配置资产；发布请求携带电子签名、质量门禁摘要和真实适配器同步日志。
- 回滚必须显式确认当前版本、目标版本和 `confirmedHighRisk=true`，回滚后 v2 为 `OFFLINE`、v1 为 `ACTIVE`。
