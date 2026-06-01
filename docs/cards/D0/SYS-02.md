# SYS-02 · 引擎领域边界与服务契约

> 读卡前置：[核心 CONSTITUTION](../../CONSTITUTION.md) + [D0 域简报](_brief.md)。
> 迁移来源（覆盖矩阵锚点）：详规 §7.1 总体架构原则（L1332）/ §7.2 系统分层 / §7.3 领域模块与所有权 · 落地规划 §9.2 后端包结构。

## 身份
- 卡 ID：SYS-02
- 域：D0 登录域 / 平台脊柱
- 关联场景：横切（引擎模块边界与契约）
- 依赖卡：[BASE-03](BASE-03.md)（API 契约）· [BASE-02](BASE-02.md)（权限要求）· [BASE-04](BASE-04.md)（审计要求）
- 工作量：4d
- owner / reviewer：待派单（owner ≠ reviewer）

## 目标

交付**引擎领域边界与服务契约**：模块依赖单向（引擎核心不依赖业务包）、每服务暴露 OpenAPI、引擎间事件契约标准化、每服务声明五维权限 + 审计点——把"引擎核心 + 业务包装"两层架构的边界焊死。

## 功能要求（原子可测条目）

- [x] **FR-1 模块依赖单向**：依赖方向 业务包 → 引擎核心（单向）；引擎核心**不依赖**业务包；依赖图**无环**（ArchUnit/门禁校验）。
- [x] **FR-2 OpenAPI 契约**：每引擎服务暴露 OpenAPI 文档（契约即文档）。
- [x] **FR-3 事件契约**：引擎间领域事件 schema 标准化（版本化、向后兼容）。
- [x] **FR-4 权限审计要求**：每服务声明所需五维权限（[BASE-02](BASE-02.md)）+ 审计点（[BASE-04](BASE-04.md)）。
- [x] **FR-5 领域所有权**：每实体单一 owner 模块；禁跨模块直写他域表（经服务契约/事件）。
- [x] **FR-6 契约测试**：服务契约 + 事件契约被契约测试守护，破坏即 CI 红。

## 接口契约 / 页面契约
### 接口契约
- 端点：本卡定义**契约规约**（模块边界 + OpenAPI 规范 + 事件 schema 规范），非单一端点。
- DTO：领域事件 Record schema 规范。
- 响应信封：统一 `ApiResult`/`ProblemDetail`（[BASE-03](BASE-03.md)）。
- 状态机：N·A —— 架构契约层。
- 幂等 / 错误码 / traceId：事件携带 traceId（[OBS-01](OBS-01.md)）；事件消费幂等。

### 页面契约
N·A —— 无页面。OpenAPI 文档可在 D6 开发者控制台呈现。

## 数据与迁移
N·A —— 架构契约不落业务表（依赖校验/契约测试在 CI）。

## 视角清单（11 视角逐条）
1. **产品架构**：★本卡主战场 —— 引擎核心/业务包两层边界 + 领域所有权 + 单向依赖（详规 §7.1-7.3）。
2. **产品体验**：N·A。
3. **系统与数据架构**：★OpenAPI + 事件契约 + 依赖无环；模块解耦支撑独立演进。
4. **临床医疗安全**：业务包不绕引擎直写医疗结论（核心 §10），边界由本卡强制。
5. **知识与数据治理**：知识/资产实体单一 owner，禁跨模块直改（核心 §7）。
6. **安全合规与监管**：每服务声明权限 + 审计点，无"裸服务"无鉴权。
7. **集团化与多租户治理**：N·A —— 契约与租户无关。
8. **集成与互操作**：★引擎对外经统一服务契约 + 事件（核心 §10），外部不直连内部模块。
9. **运维 / SRE / 国产化**：契约稳定支撑灰度/回滚；OpenAPI 支撑联调。
10. **质量与真实性审计**：★依赖无环 + 契约测试是真实门禁；禁循环依赖/跨域直写。
11. **AI / 模型治理与可降级**：模型网关作为独立模块经服务契约接入（核心 §11/#13），业务不直绑厂商。

## 适用不变量
- 命中核心约束：**§10 不绕引擎/服务契约** · **#13 模型经网关（模块边界）** · **§7 领域所有权** · **§13 契约测试门禁**。
- 本卡落点：单向依赖 + OpenAPI + 事件契约 + 每服务权限审计声明，把两层架构边界做成 CI 可验证的硬约束。

## 验收 + 验证
- [x] **AC-1（FR-1）**：引擎核心依赖业务包的写法被 ArchUnit/门禁拒；依赖图无环。
- [x] **AC-2（FR-2）**：每引擎服务有 OpenAPI 且与实现一致（契约测试）。
- [x] **AC-3（FR-3/6）**：领域事件 schema 破坏性变更被契约测试捕获红。
- [x] **AC-4（FR-4）**：每服务声明五维权限 + 审计点；缺声明被校验。
- [x] **AC-5（FR-5）**：跨模块直写他域表的写法被拒（经服务/事件）。
- 关联 A1–A9：横切（架构守护各剧本）。
- T-GATE：CI 依赖校验 + 契约测试全绿。
- B0 验收：架构契约，天然 B0。

## 完工证据
- PR1 代码 permalink：`ModuleBoundaryArchTest` / `DomainOwnershipCatalog` / `DomainOwnershipContractTest`；同时将 `OrgLevel`、`JwtSecretResolver`、高危变更 guard、临床事件 worker 配置读取契约下沉到 shared，清除 shared 反向依赖 engine 的旧边界问题。
- PR1 测试：`mvn -B -q -Dtest=ModuleBoundaryArchTest,DomainOwnershipContractTest test` 已通过，覆盖引擎 / shared 不依赖业务包、shared 不依赖 engine、顶层包无环、`@Table` 单一 owner、源码 SQL 直写只能发生在 owner 包内。
- PR2 代码 permalink：`ServiceContractCatalog` / `OpenApiContractConfiguration` / `DomainEventSchemaCatalog` / `docs/contracts/events/*.json`；服务契约目录覆盖 35 个 `/api/v1` 控制器，OpenAPI 统一 group `medkernel-service-contracts` 从目录生成路径，事件 schema 用 5 个版本化 JSON 契约锁住 record 字段。
- PR2 真实缺口修复：为 `IntegrationController` 补齐方法级 `@PreAuthorize`，新增 `integration.read/write/execute` 与 `mpi.read/write` 权限码并纳入默认角色策略；`AuthController.changePassword` 与 `BootstrapController.bindMfa` 明确登录态要求。
- PR2 测试：`mvn -B -q -Dtest=ModuleBoundaryArchTest,DomainOwnershipContractTest,ServiceContractGovernanceTest,OpenApiContractConfigurationTest,DomainEventSchemaContractTest test` 已通过，覆盖服务目录 / OpenAPI 路径 / 公开端点 / 权限码 / 审计点 / 领域事件 schema 破坏性变更门禁。
- 审计员签字：@<reviewer>（owner ≠ reviewer）。

## 大卡工序（4d，后端/架构）
- PR1：模块边界 + 单向依赖 ArchUnit + 领域所有权 → AC-1/5。
- PR2：OpenAPI + 事件契约 schema + 契约测试 + 权限审计声明 → AC-2/3/4。
