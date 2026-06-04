# 附录 S — 安全与权威护栏（权威）

> 目标：平台权威不可被悄悄弱化，患者安全不可被覆盖破坏，每个决策可追责、可重放。

## S1 覆盖策略护栏 override_policy（呼应 §4.4）
平台版本携带 `override_policy ∈ {FREE, REVIEW, LOCKED}`：
- **FREE**：自由覆盖。
- **REVIEW**：覆盖须评审通过（高风险临床项）。
- **LOCKED**：禁止 DISABLE；REPLACE 仅允许"收紧"（safety floor）。

## S2 安全单调性（Safety Monotonicity，LOCKED 的核心）
LOCKED 资产的 REPLACE 仅当**不放宽安全约束**时被接受。各域提供单调性谓词 `isAtLeastAsStrict(platformVersion, overrideVersion)`：
- 给药禁忌：覆盖不得移除任何平台禁忌项，只能新增。
- 剂量上限：覆盖的上限 ≤ 平台上限（更保守）。
- 过敏/相互作用核查：覆盖不得关闭核查、不得缩小核查范围。
- 红线触发条件：覆盖不得放宽触发（不得把"必拦截"改为"提醒"）。
违反 → 解析期拒绝该覆盖并审计告警；编辑期前置禁用（附录 M8）。

## S3 平台红线联动
`engine/safety` 平台级红线天然视为 LOCKED；红线更新无视 PINNED 强制下发（§8.5 安全例外），仅记录告知。租户可"更严"，不可"更松"。

## S4 版本不可变与防篡改
- `AssetVersion` 内容不可变，发布即冻结，改动只能发新版本。
- `content_hash`（复用 `VersionContentHash`）锁内容；分发快照携带 hash，下游可校验未被篡改。
- 平台发布走审批 + （高风险）电子签名（见附录 L 生命周期），签名进审计。

## S5 决策固化与法律级重放
- 一次临床决策解析出的有效资产集，以 `VersionReplayBinding` 钉定 `asset_identity→content_hash` 快照。
- 事后可按 `trace_id` 完整重放"当时用了哪些版本、平台还是覆盖、为何触发"，满足举证与质控。

## S6 权限分离（最小授权）
| 能力 | 权限 | 约束 |
|---|---|---|
| 平台发布/激活/设 policy | `platform.publish` | 仅平台管理员 |
| 高风险平台发布 | `platform.publish` + 电子签名 | 双人复核可选 |
| 机构覆盖（REPLACE/DISABLE/ADD） | `tenant.override` | 仅自身 org 闭包 |
| 高风险/REVIEW 覆盖发布 | `tenant.override` + 评审通过 | safety 域评审 |
| 跨租户读取 | —— | 永久禁止（§10 隔离） |

## S7 跨租户与多层隔离不变量
- 解析输入恒为"平台只读基线 + 本租户组织闭包"；任何路径不得读到他租户覆盖/ADD/diff。
- ArchUnit/契约测试断言：解析、包合成、差异计算的查询都带 `tenant_id` + org 闭包约束。

## S8 审计完整性
版本、覆盖、传播、policy 变更、升级/钉点、回滚、LOCKED 拦截、评审、签名——全部 append-only 审计，含 `trace_id`/`content_hash`/before-after/操作者/时刻。审计不可删改。
