# D5 合规运维 · 域简报

> 读卡前置：先读 [核心 CONSTITUTION](../../CONSTITUTION.md)，再读本简报，再读你领的那张卡。页面卡另读 [体验契约](../../EXPERIENCE_CONTRACT.md)。
> 本域所有卡共享的上下文放这里，**不在卡间复制**。冲突裁决：核心 > 本简报 > 卡。
> 准入：D4 域级验收通过后才进 D5（核心 §0 纵向推进）——D5 消费 D0 安全脊柱（身份/权限/审计/配置）+ 全域运行/质控的真实数据出证据。

## 域目标

把**合规运维**做成 **B0 真实**：用户/权限管理、身份绑定、审计可查、**证据链（真实文件 + 国密签名 + 验签）**、导出审批、外部依赖**无连接诚实显示（`NOT_CONNECTED`/`MODEL_DISABLED`）**、备份恢复就绪与隔离演练证据、断外网本地运行、互联互通测评映射，**全程关模型也真实可跑、可验收**。

D5 **天然 B0**：身份/审计/证据/配置全确定性；模型/Provider 不可用时诚实降级（`MODEL_DISABLED`/`NOT_CONNECTED`），**绝不伪造连接/签名/证据**。

## 现状（搬迁时核查 2026-05-30；后端以 `medkernel-backend` 真实包为准复核）

D5 合规底座**部分已成型**：

- **证据链**＝`com/medkernel/compliance/evidence/`：`EvidenceController` + `EvidenceService` + `EvidenceSnapshot(+Repository)` + `EvidenceCreateDto` + `EvidenceResponse` + `EvidenceVerifyResult`（[EVID-01](EVID-01.md) 承接真实文件 + 国密签名 + 验签）。
- **审计**＝`com/medkernel/compliance/audit/`：`AuditController` + `AuditEvent`（建在 D0 [BASE-04](../D0/BASE-04.md) 审计骨干上，[SVC-COMPLIANCE-02](SVC-COMPLIANCE-02.md) 承接查询/导出）。
- **身份/权限**＝D0 `engine/security/` 提供认证与权限脊柱；D5 `ComplianceUserController/Service` 提供唯一用户管理入口。`tenant_user` 是用户主体权威源，`platform_credential` 仅负责平台凭证。
- **前端 6 页面已存在待真实化**（`frontend/src/pages/compliance/`，`app/router.tsx` 真实路由）：`AdminUsers`→`/admin/users` · `IdentityBinding`→`/security/identity-binding` · `AdminAudit`→`/admin/audit` · `SecurityBaseline`→`/security/baseline` · `SystemProviders`→`/system/providers` · `NotificationSettings`→`/notifications/settings`；现状＝页面壳已存在，页面卡＝去占位/mock + 接真实身份/审计/证据/Provider API + 六态/五维 RBAC 齐全。
- **当前收口**：安全合规与证据框架、互联互通测评映射已具备；[SVC-COMPLIANCE-02](SVC-COMPLIANCE-02.md) 统一审计、运行状态、部署侧备份恢复证据和离线运行就绪，前端 `SystemProviders.tsx` 负责普通/专家视图边界。

## 登入 / 使用角色（13 角色矩阵本域子集，全量见 [质量基线 §9](../../audit/质量基线.md)）

| 角色 | 在 D5 主要干什么 |
|---|---|
| 平台 / 医院管理员 | 用户/权限管理、身份绑定、安全基线与系统配置（D5 主驾驶） |
| 合规 compliance | 审计查询、证据包导出审批、互联互通测评证据 |
| 安全管理员 security | 安全基线、数据权限/脱敏、高危护栏 |
| 审计员 auditor | 审计日志查询、证据验签（只读 + 导出） |
| 信息科 it-ops | 依赖运行状态、备份恢复就绪与演练证据、离线运行、国产化 |

> 角色 → 默认视图与可操作范围由各页五维 RBAC（[BASE-02](../D0/BASE-02.md)/[INFRA-05](../D0/INFRA-05.md)）+ 组织作用域（[BASE-01](../D0/BASE-01.md)）执行；**前端不写死角色逻辑、不前端伪造连接/签名**。

## 共享数据模型 / 实体（D5 卡共用，单一归属在此声明、卡内只引用）

- **证据链（EvidenceSnapshot）**：真实文件 + 国密签名 + 验签 + 导出 URI 的单一归属在 [EVID-01](EVID-01.md)；[SYS-06](SYS-06.md) 证据框架与导出审批引用、页 [AUDITLOG-01](AUDITLOG-01.md) 消费。
- **安全合规框架（数据权限/脱敏/导出审批）**：单一归属 [SYS-06](SYS-06.md)（建在 D0 [BASE-02](../D0/BASE-02.md) 权限 + [BASE-04](../D0/BASE-04.md) 审计之上）。
- **身份/权限脊柱**：用户/角色/权限/凭证/会话的单一归属在 **D0**（[AUTH-01](../D0/AUTH-01.md)/[BASE-02](../D0/BASE-02.md)/[INFRA-05](../D0/INFRA-05.md)/[INFRA-08](../D0/INFRA-08.md)）；D5 [SVC-COMPLIANCE-01](SVC-COMPLIANCE-01.md) 与页 [USERS-01](USERS-01.md)/[IDBIND-01](IDBIND-01.md) 只编排/呈现。
- **系统配置中心**：功能开关/认证/备份/国产化/Provider/日志级别的单一归属在 [CONFIG-01](../D0/CONFIG-01.md)（D0）；页 [SECBASE-01](SECBASE-01.md) 是其前台，高危护栏置灰不可关、**不净增二级菜单**（27 槽不变）。
- **运行状态**：外部依赖、模型、备份就绪和国产化状态单一归属 [SVC-COMPLIANCE-02](SVC-COMPLIANCE-02.md)；无连接诚实标 `NOT_CONNECTED`/`MODEL_DISABLED`，页 [PROVIDER-01](PROVIDER-01.md) 消费。
- **审计**：审计事件骨干在 D0 [BASE-04](../D0/BASE-04.md)；D5 查询/导出归 [SVC-COMPLIANCE-02](SVC-COMPLIANCE-02.md)。
- **API 统一输入**：所有 D5 API 复用 [BASE-03](../D0/BASE-03.md) `ApiResult`/`ProblemDetail`/Record DTO + §1.4；大列表复用 [API-13](../D0/API-13.md)。

> **权威源铁律**：唯一权威源是院内关系库（核心 §7 / 铁律 #5）；证据文件落对象存储但元数据/签名在库。

## 依赖

- **上游（D0 脊柱 + 全域数据）**：[AUTH-01](../D0/AUTH-01.md)/[BASE-02](../D0/BASE-02.md)/[INFRA-05](../D0/INFRA-05.md) 身份权限 · [BASE-04](../D0/BASE-04.md) 审计 · [CONFIG-01](../D0/CONFIG-01.md) 配置中心 · [BASE-01](../D0/BASE-01.md) OrgContext · [BASE-03](../D0/BASE-03.md) API · [BASE-05](../D0/BASE-05.md) 方言 · [BASE-08](../D0/BASE-08.md)/[BASE-10](../D0/BASE-10.md) 前端体验/token · [API-13](../D0/API-13.md) 大列表 · 全域运行/质控数据（出证据）。
- **下游（GA）**：GA 验收消费 D5 证据包/审计/互联互通测评映射。

## 本域最烫的不变量（点核心编号 + 为何在本域最关键）

- **核心 §13 真实性 + 铁律 #1/#2**：合规直面监管/等保/测评——审计/证据/签名/连接状态必须真实，**绝不伪造连接、不伪造签名哈希、不假审计**；无连接标 `NOT_CONNECTED`。
- **核心 §6 安全合规**：数据权限 + 脱敏 + 导出审批 + 国密签名验签；证据不可篡改、可验签。
- **核心 §9 多租户隔离**：用户/权限/审计/证据严格按租户隔离，跨租户不可见；安全基线下级不可静默关。
- **配置外置（铁律 #19/#11）**：系统配置走 [CONFIG-01](../D0/CONFIG-01.md) 外置，高危护栏置灰不可关、不写死 yml、不净增二级菜单。
- **核心 §11 B0 / §运维**：外部依赖不可用诚实降级；部署侧备份恢复与隔离演练真实可跑；本地包和本地执行器断外网可运行。

## 域级验收（D5-验收）

D5 全部卡（5 ID + 6 页面）`done` 后过域级验收（[质量基线 §2.3](../../audit/质量基线.md)）：

1. 平台/医院管理员/合规/审计逐角色登入 → 6 个二级菜单页按五维 RBAC 正确呈现、六态齐全（27 二级菜单不净增）；
2. 跑通 D5 B0 主链路 E2E（**全程关模型**）：管权限（用户/角色/数据权限）→ 改身份绑定 → 查审计（按条件）→ 生成证据包（真实文件 + 国密签名）→ 导出审批 → 验签通过；每步状态机正确、证据可验签、审计留痕；
3. 系统配置：安全基线与系统配置页（[CONFIG-01](../D0/CONFIG-01.md) 前台）改功能开关/日志级别等，高危护栏置灰不可关；
4. 运行保障：无连接诚实标 `NOT_CONNECTED`/`MODEL_DISABLED`；应用只读展示备份就绪和演练证据；断外网本地执行可验证；
5. 关闭模型/Dify/外部 Provider → D5 身份/审计/证据主链路仍真实通过（`B0`/`NOT_CONNECTED`，无伪造签名/连接）；
6. T-GATE 前后端真实性门禁全绿（无 Math.random 造数/无伪造签名哈希/无假连接/无绕 no-page-mock）；owner ≠ reviewer 签字。

**域级验收过，才算 D5 落实，才走 D6。**
