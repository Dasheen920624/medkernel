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

| 脚本                                           | 幕       | 用途                                                                                                                   |
| ---------------------------------------------- | -------- | ---------------------------------------------------------------------------------------------------------------------- |
| `act10-audit-degrade.mjs`                      | 幕10     | 合规审计、数据权限、脱敏预览、导出审批、模型降级、国产化自检与 schema-only 备份恢复的 L1 远程演练脚本                  |
| `audit-trace-diagnosis-ui-proof.mjs`           | P1/幕10  | `UI-ACT10-AUDIT-01` 审计页 Trace ID 直搜与诊断链跳转的 134 桌面 / 390px 移动前台复验脚本                               |
| `guide-acceptance-proof.mjs`                   | 指南验收 | 使用指南、角色培训、术语表和 P1 证据锚点的可复跑一致性验收脚本                                                         |
| `security-baseline-trial-preview-ui-proof.mjs` | P1/幕10  | `UI-ACT10-SECBASE-01` 安全基线页权限试算与脱敏预览的 134 桌面 / 390px 移动前台复验脚本                                 |
| `p5-core-readiness-probe.mjs`                  | P5       | P5 第二轮全新演练核心只读探针：知识/规则/路径/临床/质控/审计等代表 API 与演示文本扫描                                  |
| `p9-t98-readiness-preflight.mjs`               | P9/T9.8  | 知识生产上线只读预检：核验 health、指定 producer/provider/capability 的 9 闸与受控来源，输出脱敏证据；除登录外只发 GET |
| `p9-pre-signoff-rehearsal.mjs`                 | P9/预演  | 签署前真实模型预演：白名单限定健康检查、评测创建/读取与 readiness；Provider 必须保持停用，运行只能停在 `PENDING_REVIEW` |
| `p9-engineering-rehearsal-check.mjs`           | P9/预演  | 纯只读聚合 manifest 显式列出的 11 类工程证据；缺项、非通过或安全边界不明时阻断，只能推进至 `REHEARSAL_READY`           |
| `p5-act2-terminology-cross-role.mjs`           | P5/幕2   | 幕2 术语与字典跨角色旅程：API 铺底（参考字典 + HIS/LIS 模拟）→ 医技候选确认/驳回 → 知识治理员映射包 → 角色边界走查     |
| `sandbox-fulltruth-run.mjs`                    | P5/沙盘  | 遍历已通过临床门禁的沙盘场景，完成真实编排、嵌入令牌兑换、推荐卡读取、医师反馈与服务端事实归档；未评审场景保持阻断     |
| `p5-first-phase-rectification-closeout.mjs`    | P5/收官  | 仅关闭幕7失败演练命名空间内的遗留整改，并验证租户整改报告无未关闭任务                                                  |
