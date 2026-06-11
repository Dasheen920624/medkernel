# 幕8 · 配置包与发布治理证据

> 结论：幕8已在 134 真实跑通配置包组包、灰度、全量发布、离线导出验签、模拟导入对账和安全撤回阻断。模拟导入返回 `409` 是预期的重复版本/对账保护，不是演练失败。

## 运行概要

| 项 | 值 |
|---|---|
| 环境 | `https://193.112.107.134` |
| 批次 | `act8-8sinb347c5` |
| 配置包 | `DRILL.ACT8.CONFIG.ACT8-8SINB347C5@2026.06.11-act8-8sinb347c5` |
| 配置包 ID | `e845b6cc-fbe7-4577-836f-6fed3bdae47d` |
| 发布适配器 | `drill-local-runtime-package-sink-20260611`，健康状态 `HEALTHY` |
| 灰度计划 | `348c1ac9-d715-4e1f-b4e2-26b591697d56` |
| 全量计划 | `cb2db40d-cd7a-47b8-bd32-8204a22d5df3` |
| 全量后包状态 | `ACTIVE` |
| 安全撤回 | `withdrawalId=1`，撤回后再发布被 `400` 阻断 |

## 证据清单

| 环节 | 证据 | 验收点 |
|---|---|---|
| 角色、登录、适配器探活 | [00-readiness-actors-adapter.json](00-readiness-actors-adapter.json) | 信息科、医务处、发布适配器均可用，适配器健康 |
| 撤回沙箱知识 | [01-withdrawal-sandbox-knowledge.json](01-withdrawal-sandbox-knowledge.json) | 为撤回演示创建独立沙箱资产，不污染幕3主知识 |
| 源资产盘点 | [02-source-assets.json](02-source-assets.json) | 术语、知识、规则、路径和撤回沙箱资产均来自前序真实演练 |
| 草稿与校验 | [03-package-draft-validate.json](03-package-draft-validate.json) | 配置包草稿校验通过，条目指向统一版本资产 |
| 灰度边界 | [04-gray-release-boundary.json](04-gray-release-boundary.json) | 呼吸科在灰度范围内，心内科不受影响 |
| 全量发布与同步 | [05-full-release-sync-evidence.json](05-full-release-sync-evidence.json)、[05-sync-logs-export.ndjson](05-sync-logs-export.ndjson) | 全量后包状态 `ACTIVE`，同步日志 2 条 |
| 离线导出与导入对账 | [06-offline-export-import-check.json](06-offline-export-import-check.json)、[06-package-diff-export.ndjson](06-package-diff-export.ndjson) | manifest 摘要与包体一致；4 个条目、4 个资产快照；模拟导入 `409` 证明重复版本对账保护生效 |
| 安全撤回 | [07-safety-withdrawal-impact.ndjson](07-safety-withdrawal-impact.ndjson)、[07-safety-withdrawal-release-block.json](07-safety-withdrawal-release-block.json) | 撤回影响摘要可追溯；撤回后发布链路被阻断 |
| 总览与 traceId | [08-act8-runtime-overview.json](08-act8-runtime-overview.json)、[trace-ids.txt](trace-ids.txt) | 汇总运行对象、traceId 和最终状态 |

## 修复与部署留痕

| 类别 | 证据 | 说明 |
|---|---|---|
| 继承范围修复发布 | [00-backend-deploy-inheritance-scope.json](00-backend-deploy-inheritance-scope.json) | 统一版本继承解析兼容组织树路径与语义范围 |
| 包条目身份归一发布 | [00-backend-deploy-package-identity-normalization.json](00-backend-deploy-package-identity-normalization.json) | 配置包条目业务 ID 可解析到统一版本资产 ID |
| 路径范围修复发布 | [00-backend-deploy-pathway-scope-fix.json](00-backend-deploy-pathway-scope-fix.json) | 路径模板统一发布到租户语义范围 |
| 术语包桥接发布 | [00-backend-deploy-terminology-package-bridge.json](00-backend-deploy-terminology-package-bridge.json) | 外部术语包条目可映射到统一 `PACKAGE` 资产 |
| 路径历史数据修复 | [00-remote-data-repair-pathway-scope.json](00-remote-data-repair-pathway-scope.json) | 134 演练路径资产改为 `tenant:drill-hospital-20260611` |
| 术语包历史数据修复 | [00-remote-data-repair-terminology-package-scope.json](00-remote-data-repair-terminology-package-scope.json) | 134 演练术语包资产改为小写语义组织范围 |

## 幕8.5 前台复演

幕8.5 第三批补齐幕8客户视角页面证据：本轮只做前台入口、台账可读性、发布弹窗和发布治理页面复测，不重复执行真实全量发布或撤回，避免扰动幕8 L1 已经完成并撤回校验过的资产。截图统一落在 [ui-replay/](ui-replay/)。

| 角色 | 页面路由 | 前台操作 | 截图 |
|---|---|---|---|
| 信息科管理员 | `/config/packages` | 检索幕8配置包并查看台账业务状态 | [01-config-package-ledger.png](ui-replay/01-config-package-ledger.png) |
| 信息科管理员 | `/config/packages` | 打开发布配置包弹窗，核对灰度 / 全量策略与真实适配器 | [02-config-package-release-modal.png](ui-replay/02-config-package-release-modal.png) |
| 医务处质控员 | `/config/releases` | 查看发布治理影响模拟与灰度入口 | [03-release-governance-simulation.png](ui-replay/03-release-governance-simulation.png) |
| 医务处质控员 | `/config/releases` | 查看覆盖模板和批量复用入口 | [04-release-governance-template.png](ui-replay/04-release-governance-template.png) |

四问结论：配置包与发布治理入口可被客户找到，发布弹窗能解释灰度 / 全量与适配器；`OPT-PKG-01` 继续成立，普通台账仍同时暴露业务编码和统一版本资产 / 包 ID，应把技术 ID 收进专家或调试视图。`UI-ACT8-REPLAY-01` 登记为本轮限制：L2 不重复真实全量 / 撤回，幕8 L1 证据仍是运行链主证据。

## 真实限制

- 本地脚本访问 134 仍需 `NODE_TLS_REJECT_UNAUTHORIZED=0`，原因是演练环境使用自签 TLS 证书。
- 幕8验证的是知识/配置资产包的离线导出与模拟导入；包含患者、账号和租户配置的整套演练场景包仍由 OPT-DEMO-01 承接。
