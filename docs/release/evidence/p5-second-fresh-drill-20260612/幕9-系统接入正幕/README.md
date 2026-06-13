# P5 幕9 · 系统接入正幕

> 执行时间：2026-06-13T15:20:57.668Z
> 环境：`https://193.112.107.134`
> 脚本：[scripts/drill/p5-act9-main-stage.mjs](../../../../../scripts/drill/p5-act9-main-stage.mjs)
> runTag：`p5-act9-main-20260613-232500`

## 1. 旅程结构

| 段 | 角色 | 动作 | 证据 |
|---|---|---|---|
| 适配器接入 | 集成运维员 | `/adapter/hub` 真实前台新增 HIS 适配器并探活到 `HEALTHY` | `01`–`04` 截图 + `10-server-facts.json` |
| 断连诚实降级 | 集成运维员 | 新增 EMR 断连适配器，真实探活返回 `NOT_CONNECTED` | `04` 截图 + 服务端事实 |
| 回调通道 | 集成运维员 | 创建回调通道并生成 HMAC-SHA256 签名预览；共享密钥只生成一次且不入仓库 | `06` 截图 + `00` 汇总 |
| 接入申请 | 集成运维员 | ADAPTER 与 FHIR R4 双路径按 `REQUESTED → AUTH_CONFIGURED → MAPPING_CONFIGURED → ONLINE` 推进 | `05` 截图 + `00` 汇总 |
| 区域来源 | 集成运维员 | 未可信分级先被 `REGIONAL_SOURCE_UNGRADED` 拒绝，再登记 `MEDIUM` 可信来源 | `07` 截图 + trace |
| 数据质量 | 集成运维员 | 生成数据质量报告，诚实暴露无 ACTIVE MPI 与 NOT_CONNECTED 缺口 | `09` 截图 + `00` 汇总 |
| 死信补偿 | 集成运维员 | 断连出站消息进入 `DEAD_LETTER`，回调管理视角重放创建补偿消息且不删除原件 | `08` 截图 + `10` 服务端事实 |

## 2. Canonical 结果

- `00-act9-main-summary.json failures=[]`。
- HIS 适配器：`p5-his-main-260613232500`，`ACTIVE/HEALTHY`。
- EMR 适配器：`p5-emr-main-260613232500`，`ACTIVE/NOT_CONNECTED`，用于证明断连不伪装成功。
- ADAPTER 接入申请：`p5-onb-his-260613232500`，`ONLINE`，`routeReference=/api/v1/engine/integration/adapters/p5-his-main-260613232500`，`blockers=[]`。
- FHIR 接入申请：`p5-onb-fhir-260613232500`，`ONLINE`，`routeReference=/api/v1/engine/integration/fhir/R4`；仍如实带 `NOT_CONNECTED` blocker，不阻断主流程。
- 区域来源：未分级负向探针返回 `409 / REGIONAL_SOURCE_UNGRADED`；可信来源 `p5-regional-lab-260613232500` 登记为 `ACTIVE/MEDIUM`。
- 数据质量报告：`dqr-01KV0S4JTR1ZDFX7T1YC5HRR86`，`adapterTotal=7`、`mappingRate=100`、`notConnectedCount=3`，缺口摘要为“暂无 ACTIVE MPI 患者，必填字段达标情况无法证明；NOT_CONNECTED 适配器：3”。
- 死信：原始消息 `p5-act9-dead-260613232500` 保持 `DEAD_LETTER/retryCount=1`；回调重放新建 `replay-e66db8e801e944a4b1a5aa38aa125098`，随后真实投递仍 `NOT_CONNECTED`，`blocksMainFlow=false`。

## 3. 必接源判定

`10-server-facts.json` 同时保留两个视角：

- `canonicalRequiredSourceBindings`：本批正幕的可读判定。HIS 通过 ADAPTER 路线 ready；EMR 明确 NOT_CONNECTED；LIS 走 FHIR R4 ONLINE 但仍诚实标注 NOT_CONNECTED。
- `adapterHubStatus.requiredSources`：租户全局实时看板。因 134 保留了前两次 PASS 收敛批次，HIS/EMR 必接源可能指向较早同类适配器；这是全局选择顺序事实，不影响本批 run-specific 服务端回查。

## 4. 诚实收敛说明

本目录最终保留 canonical 批次 `p5-act9-main-20260613-232500`。在生成 fullPage 截图与补充 `canonicalRequiredSourceBindings` 前，134 上还真实跑过两次 PASS 收敛批次 `p5-act9-main-20260613-231300` 与 `p5-act9-main-20260613-232050`；这些演练数据未清库，故本批数据质量报告中的 `adapterTotal=7`、`notConnectedCount=3` 是当前租户真实累计状态。

仓库证据不包含 Webhook 共享密钥、密码、MFA 密钥、恢复码、Cookie 或 Token；敏感扫描仅允许出现 `sharedSecretWrittenToEvidence=false` 这类布尔事实。

## 5. 证据文件

- `00-act9-main-summary.json`：主汇总，未写入 Webhook 共享密钥。
- `10-server-facts.json`：适配器、AdapterHub、接入申请、区域来源、死信和回调通道服务端事实。
- `trace-ids.json`：脚本请求 traceId 与响应 traceId。
- `01-*.png` 至 `09-*.png`：真实前台 fullPage 截图，带 URL 栏。
