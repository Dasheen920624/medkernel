# D2 试点准备 · 域简报

> 读卡前置：先读 [核心 CONSTITUTION](../../CONSTITUTION.md)，再读本简报，再读你领的那张卡。页面卡另读 [体验契约](../../EXPERIENCE_CONTRACT.md)。
> 本域所有卡共享的上下文放这里，**不在卡间复制**。冲突裁决：核心 > 本简报 > 卡。
> 准入：D1 域级验收通过后才进 D2（核心 §0 纵向推进）。

## 域目标

把**配置类引擎**做成 **B0 真实**——让医院能导入/配置**知识、字典、规则、路径**，经 **7 步流**（核心 §4）提交 → 灰度 → 全量 → 回滚，**全程无模型也真实可跑、可验收**，产出 **D3 临床主链路能直接消费的料**（已发布的规则/路径/知识/字典/标准上下文）。D2 是"准备料"的域：自己不做临床判断，但 D3 的每一次提醒/入径都消费 D2 在此发布的权威资产。

D2 **天然 B0**（铁律 #4 B0 先于模型）：所有引擎先交付"确定性 + 人工配置"路径并验收；AI 工厂、模型抽取/生成、语义自动发现整体后移第二波（wave2 的 AIK-*/KNOWGEN-*/LLM-*），D2 仅留模型增强挂点 + 诚实降级（`MODEL_DISABLED`），不在 B0 里伪造智能。

## 现状（搬迁时核查，2026-05-30；后端以仓库 `medkernel-backend/src` 为准复核）

D2 引擎**多有实质基础**，本域多数卡是"真实化/契约化/补全/框架化"而非从零：

- **标准上下文引擎**＝`engine/context/`：`ContextSnapshot{Controller/Service/Request/Filter/Response/Status/Summary/Resources/Repository}` + `CanonicalResource(+Type/Repository)` 与 `canonical/` 下 12 子类型（Patient/Encounter/Condition/NursingAssessment/Observation/Medication/Procedure/DiagnosticReport/Document/CarePlan/FollowUp/Claim）+ `ClinicalEventContext` + `ClinicalCodeMappingAnchor` + `PackageVersionPort`(+Lenient 适配) + `ContextValidator` + `MissingFieldEntry` + `ContextIdempotencyKey` + `TerminologyMappingPort`；端点 `POST /api/v1/engine/context/snapshots`、安全/trace/契约测试齐（[API-01](API-01.md) 承接）。
- **知识引擎**＝`engine/knowledge/`：`KnowledgeIdentity/KnowledgeAssetVersion/KnowledgeSupersession(+Type)/KnowledgeLineage/SourceVersion` + 版本状态机 `DRAFT→CANDIDATE→PENDING_REPLACEMENT_REVIEW/UNDER_REVIEW→ACTIVE→SUPERSEDED/WITHDRAWN/REJECTED` + `KnowledgeVersionService.activate/withdraw`（**已是悲观锁 identity + 替代旧 ACTIVE 的原子事务**）+ `V3__knowledge_asset_baseline` 至 `V52__knowledge_candidate_workflow` 五方言（[KNOW-01](KNOW-01.md)/[KNOW-02](KNOW-02.md) + [SYS-08](SYS-08.md) 承接）。
- **包/发布**＝`engine/pkg/`：`ReleasePlan(+Repository/Status)`、`PackageSyncPort`、`KnowledgePackage`、`PackageEngineService` 雏形（[PKG-01](PKG-01.md)/[API-10](API-10.md) 承接）。
- **第三方对接**＝`engine/integration/`：`integration_adapter` 表 + AdapterDto（`protocol_type` 枚举含 `FHIR` 字样，但**无任何 FHIR 资源门面实现**）（[INTEG-01](INTEG-01.md)/[INTEG-02](INTEG-02.md) + [OPT-01](OPT-01.md) 承接）。
- **明确缺口**（建卡"现状"段照实写、勿夸大）：FHIR R4/R5 资源门面（[OPT-01](OPT-01.md) 新建）；**统一版本继承/灰度/回滚/历史重放框架**（版本字段散落 context/knowledge/pathway/pkg/evaluation，无统一框架，[SYS-04](SYS-04.md) 抽取）；唯一有效约束的**完整适用域**维度 + 紧急失效 + 影响病例任务（[SYS-08](SYS-08.md) 框架化）；规则/路径三层前台 + 仿真；字典语义匹配 + 高危近似判别器。

## 登入 / 使用角色（13 角色矩阵的本域子集，全量矩阵见 [质量基线 §9](../../audit/质量基线.md)）

D2 是**实施与配置域**，主用角色集中在"把医院准备好"的人：

| 角色 | 在 D2 主要干什么 |
|---|---|
| 实施工程师 implementation | 客户实施向导、租户开通、配置包导入、灰度全量推进（D2 主驾驶） |
| 信息科 it-ops | 适配器中心、字段映射、数据质量、院内同步、国产化离线包 |
| 医务处 medical-admin | 规则集/路径/知识包审核与发布门禁、高危替换确认 |
| 专科专家 specialist | 规则条件树、专病路径、字典语义映射、测试病例 |
| 质控办 quality | 质控规则配置（消费规则引擎，质控运行在 D4） |
| 平台 / 医院管理员 | 组织树、许可、发布审批（脊柱在 D0，D2 消费其 `OrgContext` 与五维权限） |

> 角色 → 默认视图与可发布范围由各页五维 RBAC（[BASE-02](../D0/BASE-02.md)/[INFRA-05](../D0/INFRA-05.md)）+ 组织作用域（[BASE-01](../D0/BASE-01.md)）执行；**前端不写死角色逻辑、不前端兜底假数**。

## 共享数据模型 / 实体（D2 卡共用，单一归属在此声明、卡内只引用）

- **标准临床模型**：12 类标准对象 + `ClinicalEventContext` 的**类型骨架单一源在 [SYS-01](../D0/SYS-01.md)（D0）**；D2 只**消费/读取/映射**，不重定义。[API-01](API-01.md) = 读这些对象组装上下文快照；[OPT-01](OPT-01.md) = FHIR 资源门面映射这些对象。
- **版本与发布框架**：资产不可变 + 组织继承 + 灰度/全量/回滚 + 历史重放的**通用框架单一归属在 [SYS-04](SYS-04.md)**；所有配置类资产（知识/字典/规则/路径/包）的版本与发布**复用 SYS-04**，不各造一套。
- **权威知识唯一性**：同一适用域唯一 `ACTIVE` 权威版本 + 替代链 + 原子替换 + 紧急失效 + 影响病例任务的**框架单一归属在 [SYS-08](SYS-08.md)**（建在 SYS-04 之上、专管"权威知识"这一类）；知识域的新旧识别/去重/冲突/待审/旧版隔离**工作流**归 [KNOW-02](KNOW-02.md)（用 SYS-08 框架，不重造约束与事务）。
- **配置类四状态机 + 7 步流**：核心 §3 配置类（草稿→待审核→已发布→生效中→已下线）+ §3 变更类（待发布→灰度→全量→回滚）+ §4 七步流是**全域统一一套**，每张配置卡/页面卡引用不自创。
- **多维治理切片**：业务统计/灰度范围/证据导出统一用核心 §9 + 详规 §1.4.1 的**七维度组**（组织层级/临床对象/服务能力/资产版本/流程状态/外部接入/风险证据），默认视图 ≤3 维，更多进专家模式。
- **API 统一输入**：所有引擎 API 复用 [BASE-03](../D0/BASE-03.md) 的 `ApiResult`/`ProblemDetail`/Record DTO + 详规 §1.4 的 12 字段统一入参（request_id/trace_id/租户六层/user/role/patient/encounter/package_version）；大列表复用 [API-13](../D0/API-13.md)。

> **权威源铁律**：以上实体唯一权威源是**院内关系数据库**（核心 §7 / 铁律 #5）；图（Neo4j）/Dify/缓存/搜索只能是投影或执行器，替换/发布时必须随之失效或重建。

## 依赖

- **上游卡（D0 脊柱 + D1）**：[BASE-01](../D0/BASE-01.md) OrgContext/组织继承/包版本快照 · [BASE-02](../D0/BASE-02.md)/[INFRA-05](../D0/INFRA-05.md) 五维 RBAC（发布范围/审批权） · [BASE-03](../D0/BASE-03.md) API 契约 · [BASE-04](../D0/BASE-04.md) 审计（来源/审核/发布/同步/回滚留痕） · [BASE-05](../D0/BASE-05.md) 5 方言迁移 · [BASE-06](../D0/BASE-06.md)/[BASE-08](../D0/BASE-08.md)/[BASE-10](../D0/BASE-10.md) 前端骨架/体验底座/token（7 页面用） · [SYS-01](../D0/SYS-01.md) 标准临床模型 · [SYS-02](../D0/SYS-02.md) 引擎域边界 · [SYS-03](../D0/SYS-03.md) 关系库权威与投影 · [SYS-05](../D0/SYS-05.md) 在线/异步/批量/离线运行 · [OBS-01](../D0/OBS-01.md) 可观测/traceId · [API-13](../D0/API-13.md) 大列表 · [INFRA-09](../D1/INFRA-09.md) 复用的 StepFlow 组件。
- **下游消费（D3+）**：D3 临床运行消费 D2 已发布的规则/路径/知识/字典/标准上下文；D4 质控消费规则；D5 合规消费审计/发布证据。D2 是"料"的生产侧，**B0 真实是 D3 能起步的前提**。

## 本域涉及

- **租户层级**：全七层（核心 §9）——配置发布按 `平台→集团→医院→院区→社区→科室→专病` 继承覆盖；灰度默认 10% 床位，仅院级管理员可直接全量。
- **外部系统**：HIS/EMR/LIS/PACS/医保/病案/护理/手麻/区域平台经**适配器 + 标准上下文 + 临床事件 + 包发布 + 审计**统一对接（核心 §10）；FHIR/CDS Hooks 门面 + 字段映射 + 健康检查 + 重试死信；断连诚实标 `NOT_CONNECTED`、同步失败标 `NOT_SYNCED`，不伪造连接、不破坏权威版本。

## 本域最烫的不变量（点核心编号 + 为何在本域最关键）

- **核心 §7 唯一权威知识 + §6 旧版隔离 + #14**：D2 是权威资产的"出厂口"。同一适用域必须唯一 `ACTIVE`、新版原子替换旧版、未审新版绝不参与临床、旧版仅历史重放——配置错版会直达 D3 临床误判，是本域最高危。
- **核心 #5 状态机 + #4 7 步流（铁律 #11 配置外置 #19）**：所有配置走统一四状态机 + 7 步极简流（含"看影响"），不自创流程、不写死 yml；高危发布无"影响分析/审核/回滚证据"即拒。
- **核心 §9 集团多租户继承 + 灰度**：分层继承覆盖 + 局部覆盖可解释 + 安全红线不可被下级静默关闭 + 灰度默认 10%——发布框架的存在理由。
- **核心 §11 B0 先于模型 / §13 真实性**：D2 全部能力先 B0 真实可跑；包同步无通道返回 `NOT_SYNCED`、关模型返回 `MODEL_DISABLED`，绝不伪造哈希/同步/智能（铁律 #1/#2）。
- **核心 §10 集成边界**：外部系统只经统一对接链路 + 标准门面；不绕引擎直写医嘱/病历/上报/支付/设备；同步超时不阻断医生主流程。

## 域级验收（D2-验收）

D2 全部卡（23 ID + 7 页面）`done` 后过域级验收（[质量基线 §2.3](../../audit/质量基线.md)）：

1. 实施/信息科/医务处/专科专家逐角色登入 → 7 个二级菜单页按五维 RBAC 正确呈现、六态齐全；
2. 跑通 D2 B0 主链路 E2E（**全程关模型**）：导入/配置**规则 + 路径 + 知识 + 字典** → 7 步流（选模板/导入 → 自动校验 → 看影响 → 提交审核 → 灰度发布 → 全量 → 留证据/可回滚）提交 → 灰度（默认 10%）→ 全量 → **回滚**，每步状态机正确、证据可导出、回滚不丢审计、不破坏历史解释；
3. 权威知识替换：同一适用域并发激活第二版被拒或原子替换；替换后新诊疗请求只命中新版、历史可按旧版重放并标"历史版本"；高危替换派发受影响病例/路径/同步目标复核任务；
4. 第三方：适配器断连诚实标 `NOT_CONNECTED`、同步无通道标 `NOT_SYNCED`，FHIR 门面与院内适配器两路可达，外部断连不阻断主流程；
5. 关闭模型/Dify/图投影 → D2 主链路仍真实通过（返回 `B0`/`MODEL_DISABLED`/`NOT_SYNCED`，无伪造、无 dangling 404）；
6. T-GATE 前后端真实性门禁全绿（无 Math.random 造数/无写死医学常量/无假同步哈希/无绕 no-page-mock）；owner ≠ reviewer 签字。

**域级验收过，才算 D2 落实，才走 D3。** 模型增强不在 D2（第二波）。
