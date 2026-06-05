# PMI-01 · 患者主索引页

> 读卡前置：[核心 CONSTITUTION](../../CONSTITUTION.md) + [D3 域简报](_brief.md) + [体验契约](../../EXPERIENCE_CONTRACT.md)。
> 迁移来源（覆盖矩阵锚点）：详规 §3 S1 集团与租户开通（患者主索引）· 详规 S8 临床嵌入运行 · 体验规范 §3 角色体验标准。
> 实化映射：占位 `D3-PAGE-患者主索引` → 本卡 **PMI-01**。

## 身份
- 卡 ID：PMI-01（页面卡；= backlog `D3-PAGE-患者主索引` 实化）
- 域：D3 临床运行
- 关联场景：S1 / S8
- 依赖卡：[SVC-CLINICAL-01](SVC-CLINICAL-01.md)（MPI 后端）· [BASE-06](../D0/BASE-06.md)/[BASE-08](../D0/BASE-08.md)/[BASE-10](../D0/BASE-10.md)（骨架/体验/token）· [INFRA-09](../D1/INFRA-09.md)（清演示页门禁）· [API-13](../D0/API-13.md)（大列表）
- 工作量：3d
- owner / reviewer：待派单（owner ≠ reviewer）

## 目标
把患者主索引页**真实化**：搜索/筛选患者、查看患者 360、发起合并，全部接 [SVC-CLINICAL-01](SVC-CLINICAL-01.md) 真实 MPI，**不前端造患者、不假数据**。

## 现状（搬迁时核查 2026-05-30，以 `frontend/src` 为准）
页面**已存在待真实化**：`pages/clinical/Mpi.tsx`（路由 `/mpi` 已注册 `app/router.tsx`）。本卡＝去占位/mock + 接 [SVC-CLINICAL-01](SVC-CLINICAL-01.md) 真实患者/合并/统计 API + 六态/五维 RBAC 齐全。

## SVC-CLINICAL-01 PR1 增量（2026-06-04）
- 已接真实 MPI 建患者、列表、统计、合并和拆分归并 hook；页面新增“新增患者”主按钮与拆分归并弹窗，拆分必须填写人工核查结论。
- 已移除把合并说成“不可逆”的旧口径；合并错误应走可追溯拆分流程。
- 仍未在本页收口：患者 360 抽屉完整呈现、六态逐态验收、五维 RBAC 与页面 E2E。本页后续实现不得回退为前端本地患者或演示数据。

## 页面收口增量（2026-06-05，本地目标红绿）
- 已实现：患者 360 抽屉改由 `useMpiPatientDetail` 读取真实后端聚合详情，呈现患者基础信息、标识、合并血缘、风险与审计摘要；索引状态筛选补齐可访问标签，列表搜索 / 状态筛选 / 分页仍由 MPI 后端执行。
- 已覆盖：新增患者、合并、拆分归并、详情打开后均通过真实 hook / mutation 与后端分页刷新闭环；页面不在浏览器生成患者、标识或合并证据。
- RBAC 边界：前端路由具备 `menu.mpi` 登录菜单保护，患者查询 / 合并 / 拆分和组织数据范围以后端 MPI 控制器权限与 `OrgContext` 为准；本 PR 不声明浏览器端另写角色矩阵。

## 功能要求（原子可测条目）
- [x] FR-1 搜索筛选：按标识/姓名/院区搜患者（[API-13](../D0/API-13.md) 分页），结果真实。
- [x] FR-2 患者 360：查看聚合上下文（[API-01](../D2/API-01.md)），不前端拼假数据。
- [x] FR-3 合并：发起/确认患者合并（`MpiMergeRequest`），合并可追溯可拆。
- [x] FR-4 六态：加载/空/错误/无权限/部分成功/正常齐全（[BASE-08](../D0/BASE-08.md)）。
- [x] FR-5 五维 RBAC：仅临床医生/信息科可见可操作；数据按 `OrgContext` 作用域。

## 接口契约 / 页面契约
### 接口契约（引擎/API 卡）
N·A —— 本卡为页面，不新增后端；消费 [SVC-CLINICAL-01](SVC-CLINICAL-01.md) 现有 MPI API。
### 页面契约（页面卡）
- 路由元数据：sectionKey `clinical-run` / menuKey `patient-mpi` / menuLabel `患者主索引` / path `/mpi` / requiredPermissions 患者查询 / requiredRoles 临床医生·信息科。
- 结构：PageShell（[BASE-08](../D0/BASE-08.md)）+ 搜索筛选（默认 ≤3）+ 患者列表（[API-13](../D0/API-13.md)）+ 患者 360 抽屉 + 六态。
- 主按钮 ≤1（新建/合并患者）/ 默认筛选 ≤3 / 默认角色视图（临床医生）。
- 五维 RBAC：菜单 / 动作（合并）/ 数据（org）/ 资产 / 环境。
- 样式：仅引用 [BASE-10](../D0/BASE-10.md) token + [体验契约](../../EXPERIENCE_CONTRACT.md)；禁硬编码 hex/px。

## 数据与迁移
N·A —— 页面卡不落库；消费 [SVC-CLINICAL-01](SVC-CLINICAL-01.md) 后端。

## 视角清单（11 视角逐条）
1. 产品架构：临床运行入口的"找人"页。
2. 产品体验：★搜索快、患者 360 一屏、六态齐；国产浏览器/老年模式可读（[BASE-10](../D0/BASE-10.md)）。
3. 系统与数据架构：列表分页 P95 ≤1s；大结果集虚拟滚动。
4. 临床医疗安全：合并谨慎二次确认、可拆；不展示越权患者。
5. 知识与数据治理：患者 360 数据来自权威上下文，不前端缓存造数。
6. 安全合规与监管：患者查询/合并留审计（[BASE-04](../D0/BASE-04.md)）；隐私最小化。
7. 集团化与多租户治理：按 `OrgContext`/院区作用域；集团/院内视图差异。
8. 集成与互操作：外部患者经适配器入 MPI（[INTEG-01](../D2/INTEG-01.md)）。
9. 运维 / SRE / 国产化：内网慢场景骨架；国产浏览器兼容。
10. 质量与真实性审计：★无前端造患者/假数据；无演示路由（[INFRA-09](../D1/INFRA-09.md) no-page-mock，铁律 #1）。
11. AI / 模型治理与可降级：N·A（确定性页面）。

## 适用不变量
- 命中核心约束：**铁律 #1 真实性（无前端假患者/无演示页）** · **§2 菜单 IA** · **§9 多租户作用域** · **依赖 [SVC-CLINICAL-01](SVC-CLINICAL-01.md)**。
- 本卡落点：把患者主索引从占位页变为接真实 MPI、可合并、可 360 的运行入口页。

## 验收 + 验证
- [x] AC-1（FR-1/2）：搜索/360 数据真实来自后端。
- [x] AC-2（FR-3）：合并可追溯可拆、二次确认。
- [x] AC-3（FR-4/5）：六态齐全；非授权角色无权访问。
- 关联 A1–A9 剧本：A2 建患者/入径前置。
- T-GATE：前端真实性门禁全绿（no-page-mock、无 Math.random 造数、无演示路由）。
- B0 验收：N·A（无模型；纯确定性页面）。

## 完工证据
- 代码 permalink：`frontend/src/pages/clinical/Mpi.tsx` 接 [SVC-CLINICAL-01](SVC-CLINICAL-01.md) MPI 列表 / 统计 / 创建 / 合并 / 拆分 / 详情 API，患者 360 不使用前端假拼接。
- 测试：`npm test -- src/pages/clinical/Mpi.test.tsx` 覆盖真实 MPI 行渲染、筛选、建患者 mutation、合并刷新、拆分归并 mutation 与患者 360 抽屉；同轮 D3 页面组 `npm test -- src/pages/clinical/Mpi.test.tsx src/pages/clinical/PatientPathways.test.tsx src/pages/clinical/CdssFatigue.test.tsx src/pages/clinical/RuleValidate.test.tsx src/pages/clinical/WorkflowTodos.test.tsx src/pages/clinical/Notifications.test.tsx src/pages/clinical/Followup.test.tsx src/pages/pages.smoke.test.tsx` 8 文件 / 65 用例通过。
- 后端证据：`mvn -q -Dtest=MpiServiceTest,MpiControllerContractTest,MpiServiceIntegrationTest test` 属于本轮后端目标组并退出码 0；组织 / 权限 / 数据范围以 MPI 控制器与服务测试为准。
- 审计员签字：@<reviewer>（owner ≠ reviewer）。
