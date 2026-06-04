# 附录 L — 编辑生命周期 / 循证溯源 / 弃用后继 / 质量门

> 目标：平台与租户的资产都有统一、可治理的生命周期；权威建立在循证与复审之上；退役有序、不断链。

## L1 统一生命周期状态机
平台版本与租户覆盖共用一套状态：
```
DRAFT → IN_REVIEW → APPROVED → PUBLISHED → DEPRECATED → RETIRED
                   ↘ REJECTED (回 DRAFT)
```
- 仅 PUBLISHED 版本参与解析（DEPRECATED 仍解析但带告警，RETIRED 不再解析、保留可重放）。
- 状态迁移走统一 `VersionReleaseService`/`VersionActivationTransaction`，每次迁移审计。
- 高风险（LOCKED/REVIEW）资产 PUBLISH 需**电子签名**（+ 可选双人复核），签名入审计（呼应附录 S4）。

## L2 循证溯源与证据分级（权威根基）
平台知识资产 SHALL 携带溯源元数据（复用/扩展 `knowledge/SourceVersion`）：
- 来源：指南/共识/文献/专家组（出处、机构、年份、URL/DOI）。
- 证据等级：GRADE（高/中/低/极低）或来源既有分级。
- 复审周期与下次复审日期（review cycle）、最近复审人。
覆盖（租户定制）SHALL 记录定制理由（`override_reason` 已存在）与本地证据，便于追责。

## L3 新鲜度与复审治理
- 临近/超过复审日期的资产进"待复审"队列，看板预警（附录 N7）。
- 平台可对过期资产标 DEPRECATED 并推荐后继；租户覆盖若其平台基线已弃用，收到 rebase/迁移提示。

## L4 弃用与后继（不断链）
- 弃用 SHALL 提供**后继指针**（复用 `KnowledgeSupersession`）：A 退役 → 指向 A'。
- 退役设**宽限期**：期间双解析（旧+新提示），到期切新；引用 A 的资产/覆盖收到迁移引导。
- 租户对已退役资产的覆盖：自动悬置 + 引导迁移到后继身份，不静默失效。

## L5 发布质量门（平台 publish gate）
平台发布前流水线（任一不过则阻断）：
1. Schema/结构校验；2. 术语/字段绑定完整性（编码均可解析）；3. 依赖完整性（附录 D2）；4. 安全单调性（若 LOCKED，附录 S2）；5. 影响模拟通过（附录 R）；6. 同行评审 + 签名。

## L6 资产身份治理（identity governance）
- **命名空间**：平台身份 `plat:<域>:<slug>`，租户独有 `t:<tenantId>:<域>:<slug>`，防碰撞。
- **稳定性**：身份与展示名/编码解耦，改名不换身份，迁移/合并用后继指针，保证历史引用与重放稳定。
- 身份由统一发号器分配（参考 `OrgUnitIdGenerator` 模式），登记入册可审计。

## L7 落地对接
- 状态机字段并入 `AssetVersion`（lifecycle_state）与覆盖记录；溯源并入/扩展 `SourceVersion`；后继用 `KnowledgeSupersession`；质量门下沉 `VersionReleaseService` 前置校验链。
