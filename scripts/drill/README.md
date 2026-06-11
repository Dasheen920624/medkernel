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
