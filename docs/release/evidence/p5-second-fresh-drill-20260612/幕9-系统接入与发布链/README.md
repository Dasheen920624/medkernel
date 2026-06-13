# P5 幕9 · 系统接入与映射包发布链（跨角色，含幕2 遗留回收）

> 执行日期：2026-06-13
> 环境：`https://193.112.107.134`，最终 manifest `7f69c94617cc879304b6841edde95b3ba29a2778`
> 脚本：[scripts/drill/p5-act9-integration-release-chain.mjs](../../../../../scripts/drill/p5-act9-integration-release-chain.mjs)（状态感知幂等，可整链复跑；成功判定一律服务端回查）
> 凭据：服务器受控文件本机副本，不入仓库

## 1. 旅程结构

| 段 | 角色 | 动作 | 性质 |
|---|---|---|---|
| 前置 | 部署侧 | 134 本机部署模拟第三方接收端（`mock-third-party-receiver.py`，systemd `medkernel-mock-third-party.service`，仅监听 127.0.0.1:9301，`/health` 探活 + `/messages` JSONL 落盘） | 演练用模拟外部系统，留痕真实 |
| 接入 | 集成运维员 | `/adapter/hub` 新增 REST 适配器 `p5-his-gateway` 指向接收端，健康诊断到 HEALTHY | 真实前台 |
| 构建+灰度 | 机构知识治理员 | `/terminology/mapping` 构建映射包（先按默认最窄范围得到可见 409，再改选服务空间范围真实落库），按 10% 灰度发布（同步通道=健康适配器） | 真实前台 |
| 全量 | 机构管理员 | `/config/packages` 「院内同步发布」以 FULL 全量激活 | 真实前台 |
| 复验 | 机构知识治理员 | P5-ACT2-04 双路径复验：重复版本构建 409、窄范围构建 409，均可见报错且弹窗不误关、零落库副作用 | 真实前台 |

## 2. 执行时间线（含中途缺陷闭环，诚实记录）

1. `13b930e4`（P5-ACT2-04 修复）部署复验通过后起跑。集成运维员前台建适配器并健康诊断 HEALTHY（`01`–`03`）。
2. 机构知识治理员构建映射包：默认最窄范围（当前科室）提交 → **可见** 409「当前范围没有已确认映射」（修复前该失败被静默吞掉，正是 #576 假阳性的根源）；改选「当前服务空间」→ `TERM.P5.MAPPING 2026.06.1` DRAFT 服务端真实落库。
3. 灰度发布提交被前端预校验拦死 → 实锤 `P5-ACT2-05`（TENANT 级包灰度被拦，详见 [幕2 README §8](../幕2-术语与字典/README.md) 与 `defect-p5-act2-05-discovery/`）。
4. PR #578 修复合并为 `7f69c946`，发布前备份 `p5-7f69c946-predeploy-20260613-091455` 隔离恢复全过后部署，post-deploy 精确匹配（`evidence/post-deploy-7f69c946.properties`）。
5. 重跑发布链：灰度发布成功（`04`–`06`，包状态 `DRAFT → PUBLISHED`，接收端收到第 1 条投递）；机构管理员 `/config/packages` FULL 全量发布成功（`07`–`09`，包状态 `PUBLISHED → ACTIVE`，接收端收到第 2 条投递）。
6. P5-ACT2-04 部署复验双路径通过（`10` 重复版本、`11` 窄范围），错误均可见且含 traceId，弹窗不误关，包数量零变化。

## 3. 服务端终态（`server-side-facts.txt`）

- 适配器：`p5-his-gateway | P5模拟院内HIS网关 | REST | ACTIVE | HEALTHY`。
- 映射包：`TERM.P5.MAPPING | 2026.06.1 | ACTIVE`（创建/更新者均为 knowledge-governor）。
- 接收端：systemd `active`，脚本 SHA-256 与仓库版本一致（`a4a70808…185cf`）。

## 4. 发布同步投递留痕（`receiver-messages.jsonl`）

| receivedAt (UTC) | eventType | messageId | 说明 |
|---|---|---|---|
| 2026-06-12T23:31:33 | SELFTEST | deploy-selftest-001 | 接收端部署自检（基线） |
| 2026-06-13T01:17:50 | MEDKERNEL_PACKAGE_RELEASE | package-release:f8e2daea…:p5-his-gateway | 灰度发布投递 |
| 2026-06-13T01:22:23 | MEDKERNEL_PACKAGE_RELEASE | package-release:30ef394a…:p5-his-gateway | 全量发布投递 |

两条发布投递对应不同 release plan，载荷 SHA-256 互异，携带 `X-MedKernel-Message-Id` / `X-MedKernel-Trace-Id` / `X-MedKernel-Event-Type`。

## 5. 汇总 JSON（`00-act9-summary.json`，failures=[]）

- `integrationOperator`：`adapterStatus=ACTIVE`、`healthStatus=HEALTHY`。
- `knowledgeGovernor`：`statusAfterGrayRelease=PUBLISHED`、`grayReleased=true`（幂等复跑时为 `grayAlreadyDoneBefore=true`）。
- `organizationAdmin`：`statusAfterFullRelease=ACTIVE`、`fullReleased=true`。
- `silentErrorFixVerification`：重复版本构建可见报错「知识包版本在该租户内已存在: 2026.06.1（traceId: f3bddf50…）」、弹窗保持打开、包数量不变。
- `narrowScopeBuildVerification`：窄范围构建可见报错「当前范围没有已确认映射，无法构建知识包（traceId: d1a26606…）」、弹窗保持打开、包数量不变。

## 6. 结论与遗留

- 幕2 遗留第 1 项（发布链依赖健康适配器）已回收：「系统接入（集成运维员）→ 灰度（机构知识治理员）→ 全量（机构管理员）」跨角色发布链全链真实前台走通，投递留痕齐全。
- `P5-ACT2-04`（静默吞错）与 `P5-ACT2-05`（TENANT 级灰度被拦）两项阻断缺陷在生产部署上复验关闭。
- 仍遗留：一对多冲突无前台处置入口（观察项，幕2 README §6 第 2 条）；幕9 正幕（完整系统接入旅程：接入申请/回调通道/区域来源等）后续单独执行，本轮仅覆盖发布同步通道前置。
