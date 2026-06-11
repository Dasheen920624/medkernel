# 演练脚本归档区

> 依据 [演练总体计划 §2.5 执行契约](../../docs/superpowers/plans/2026-06-10-full-flow-drill-usability-program.md) DoD 第 4 条：
> 演练用脚本（造数 / 铺底 / 链路验证 / 走查截图）一律入库本目录，命名 `actN-<主题>.mjs`，
> **禁止 `/tmp` 即弃——不可复现的脚本不构成证据**。

## 诚实登记

幕0–9 的演练脚本当时写在执行机 `/tmp`（如 `/tmp/act6-recommendation-runtime.mjs`、`/tmp/act8-package-release.mjs`、`/tmp/act9-third-party-cases.mjs`）未保留，已不可恢复；其产出以 [docs/release/evidence/v1.0-drill-20260611/](../../docs/release/evidence/v1.0-drill-20260611/) 归档 JSON 为准。**自幕8.5 起强制入库本目录。**

## 脚本规约

- 凭据从服务器 `/zoesoft/medkernel/conf/` 凭据文件读取，**不得硬编码进脚本**；
- 每个脚本头部注释写明：所属幕、对应剧本动作、产出证据文件清单；
- 走查截图类脚本输出文件名遵循 §2.5 DoD 第 2 条命名（`NN-ui-<页面>-<动作>.png`，带 URL 栏）。

## 已归档脚本

| 脚本                                           | 幕      | 用途                                                                                                  |
| ---------------------------------------------- | ------- | ----------------------------------------------------------------------------------------------------- |
| `act10-audit-degrade.mjs`                      | 幕10    | 合规审计、数据权限、脱敏预览、导出审批、模型降级、国产化自检与 schema-only 备份恢复的 L1 远程演练脚本 |
| `audit-trace-diagnosis-ui-proof.mjs`           | P1/幕10 | `UI-ACT10-AUDIT-01` 审计页 Trace ID 直搜与诊断链跳转的 134 桌面 / 390px 移动前台复验脚本              |
| `security-baseline-trial-preview-ui-proof.mjs` | P1/幕10 | `UI-ACT10-SECBASE-01` 安全基线页权限试算与脱敏预览的 134 桌面 / 390px 移动前台复验脚本                |
