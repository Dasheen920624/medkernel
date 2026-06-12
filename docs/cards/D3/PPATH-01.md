# PPATH-01 · 患者路径页

> 读卡前置：[核心 CONSTITUTION](../../CONSTITUTION.md) + [D3 域简报](_brief.md) + [体验契约](../../EXPERIENCE_CONTRACT.md)。
> 迁移来源（覆盖矩阵锚点）：详规 §3 S6 路径引擎配置（运行侧）· 详规 S8 临床嵌入运行 · 体验规范 §3 角色体验标准。
> 实化映射：占位 `D3-PAGE-患者路径` → 本卡 **PPATH-01**。

## 身份
- 卡 ID：PPATH-01（页面卡；= backlog `D3-PAGE-患者路径` 实化）
- 域：D3 临床运行
- 关联场景：S8 临床嵌入运行
- 依赖卡：[SVC-CLINICAL-01](SVC-CLINICAL-01.md)（路径实例/时钟后端）· [PATH-01](../D2/PATH-01.md)（路径模型）· [BASE-08](../D0/BASE-08.md)/[BASE-10](../D0/BASE-10.md)（体验/token）· [INFRA-09](../D1/INFRA-09.md)（清演示页门禁）
- 工作量：3d
- owner / reviewer：待派单（owner ≠ reviewer）

## 目标
把患者路径页**真实化**：呈现患者在径路径节点/进度/关键时钟到期，支持节点推进与变异记录，全部接 [SVC-CLINICAL-01](SVC-CLINICAL-01.md) 真实路径实例，**不前端写死节点**。

## 现状（搬迁时核查 2026-05-30，以 `frontend/src` 为准）
页面**已存在待真实化**：`pages/clinical/PatientPathways.tsx`（路由 `/pathway/patients` 已注册 `app/router.tsx`）。本卡＝去占位/mock + 接路径实例/节点推进/时钟 API + 六态/五维 RBAC 齐全。

## SVC-CLINICAL-01 PR1 增量（2026-06-04）
- 已新增 `GET /api/v1/engine/pathway/patient-pathways` 服务端分页列表，页面列表改为消费真实后端数据。
- 已清理页面本地 `sessionPathways` 会话态运行台账，入径、推进、变异、退出后统一 refetch 后端列表，不再在前端伪造路径运行结果。
- 仍未在本页收口：关键时钟到期的完整页面呈现、六态逐态验收、五维 RBAC 与页面 E2E。本页后续实现不得回退为写死节点或本地假进度。

## 页面收口增量（2026-06-05，本地目标红绿）
- 已实现：患者路径列表 / 详情抽屉完整消费后端分页、节点、关键时钟、变异证据与 traceId；节点推进、变异登记、入径 / 退出后均刷新服务端事实。
- 已覆盖：详情时间轴不写死节点，关键时钟逾期 / 到期状态和变异原因来自后端；列表查询失败显示错误态，组织数据范围拒绝显示无权限态。
- RBAC 边界：前端路由具备 `menu.patient-pathways` 登录菜单保护，推进 / 变异动作与组织数据范围以后端路径控制器权限、`PathwayEngineControllerSecurityTest` 和 `OrgContext` 为准；页面不在浏览器伪造角色授权。

## 功能要求（原子可测条目）
- [x] FR-1 路径概览：列患者在径路径 + 当前节点 + 进度，数据真实。
- [x] FR-2 节点推进：推进节点状态（调 [SVC-CLINICAL-01](SVC-CLINICAL-01.md) advance），关键时钟到期可见。
- [x] FR-3 变异记录：节点变异/偏离登记带原因，可追溯。
- [x] FR-4 六态：加载/空/错误/无权限/部分成功/正常齐全（[BASE-08](../D0/BASE-08.md)）。
- [x] FR-5 五维 RBAC：仅主管医生/专科专家可推进；数据按 `OrgContext` 作用域。

## 接口契约 / 页面契约
### 接口契约（引擎/API 卡）
N·A —— 本卡为页面，不新增后端；消费 [SVC-CLINICAL-01](SVC-CLINICAL-01.md) 路径实例 API（路径模型归 [PATH-01](../D2/PATH-01.md)）。
### 页面契约（页面卡）
- 路由元数据：sectionKey `clinical-collaboration` / menuKey `patient-pathways` / menuLabel `患者路径` / path `/pathway/patients` / requiredPermissions 路径运行 / requiredRoles 临床决策使用者、护理协同人员、临床治理负责人。
- 结构：PageShell（[BASE-08](../D0/BASE-08.md)）+ 路径泳道/节点时间轴（StepFlow [INFRA-09](../D1/INFRA-09.md) 组件）+ 关键时钟标识 + 六态。
- 主按钮 ≤1（推进节点）/ 默认筛选 ≤3 / 默认角色视图（主管医生）。
- 五维 RBAC：菜单 / 动作（推进/变异）/ 数据（org）/ 资产（路径版本）/ 环境。
- 样式：仅引用 [BASE-10](../D0/BASE-10.md) token + [体验契约](../../EXPERIENCE_CONTRACT.md)；禁硬编码 hex/px。

## 数据与迁移
N·A —— 页面卡不落库；消费 [SVC-CLINICAL-01](SVC-CLINICAL-01.md) 后端。

## 视角清单（11 视角逐条）
1. 产品架构：患者在径运行的"进度驾驶舱"。
2. 产品体验：★节点时间轴清晰 + 关键时钟提示 + 六态；国产浏览器可读。
3. 系统与数据架构：路径实例查询 P95 ≤1s；时钟到期实时。
4. 临床医疗安全：推进只走真实节点状态机；变异带原因不静默跳。
5. 知识与数据治理：路径实例绑路径版本（[SYS-08](../D2/SYS-08.md)）；旧版历史重放标识。
6. 安全合规与监管：推进/变异留审计（[BASE-04](../D0/BASE-04.md)）。
7. 集团化与多租户治理：按 `OrgContext`/科室作用域。
8. 集成与互操作：节点事件可触发 [API-02](API-02.md) 临床事件。
9. 运维 / SRE / 国产化：内网慢场景骨架；时钟降级可见。
10. 质量与真实性审计：★无前端写死节点/假进度；无演示路由（[INFRA-09](../D1/INFRA-09.md) no-page-mock）。
11. AI / 模型治理与可降级：N·A（确定性页面）。

## 适用不变量
- 命中核心约束：**铁律 #1 真实性** · **§2 菜单 IA** · **§5 状态机** · **§9 多租户作用域** · **依赖 [SVC-CLINICAL-01](SVC-CLINICAL-01.md)**。
- 本卡落点：把患者路径页变为接真实路径实例、可推进、可变异追溯的运行页。

## 验收 + 验证
- [x] AC-1（FR-1/2）：路径/节点/时钟数据真实；推进生效。
- [x] AC-2（FR-3）：变异带原因可追溯。
- [x] AC-3（FR-4/5）：六态齐全；非授权角色不可推进。
- 关联 A1–A9 剧本：A3 节点推进。
- T-GATE：前端真实性门禁全绿（no-page-mock、无写死节点、无演示路由）。
- B0 验收：N·A（无模型；纯确定性页面）。

## 完工证据
- 代码 permalink：`frontend/src/pages/clinical/PatientPathways.tsx` 接 [SVC-CLINICAL-01](SVC-CLINICAL-01.md) 患者路径分页、详情、推进、关键时钟与变异 API。
- 测试：`npm test -- src/pages/clinical/PatientPathways.test.tsx` 覆盖后端分页行、详情时钟 / 变异证据、推进 mutation + refetch、变异登记、错误态与无权限态；同轮 D3 页面组 8 文件 / 65 用例通过。
- 后端证据：`mvn -q -Dtest=PathwayEngineServiceTest,PathwayEngineControllerSecurityTest,PathwayProgressorTest test` 属于本轮后端目标组并退出码 0；推进 / 变异权限与状态机以后端测试为准。
- 审计员签字：@<reviewer>（owner ≠ reviewer）。
