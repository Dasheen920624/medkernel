# PKG-01 · 配置包发布引擎

> 权威前置：[CONSTITUTION](../../CONSTITUTION.md) · [D2 域简报](_brief.md) · [SYS-04](SYS-04.md) · [INTEG-01](INTEG-01.md)

## 身份
- 卡 ID：PKG-01
- 域：D2 试点准备
- 场景：配置资产打包、灰度、全量、同步、留证、回滚
- owner / reviewer：待派单（owner ≠ reviewer）

## 目标
把知识、术语、规则、路径和评估指标组成不可变配置包，经统一版本发布框架投放到组织范围，并通过统一集成适配器执行真实外部投递。无连接器或外部断连必须返回 `NOT_SYNCED`，不得生成伪成功或伪证据。

## 唯一方案
- 版本权威：`AssetVersion` / `ReleasePort`；包域状态仅作业务投影。
- 外部连接权威：`integration_adapter`；配置包不维护第二套同步目标或连接参数。
- 投递实现：`IntegrationPackageSyncAdapter` 复用 `IntegrationConnector`。
- 发布目标接口：`GET /engine/pkg/packages/release-adapters`，只返回当前租户启用适配器及健康、连接器状态。
- 发布请求：`PackageSyncRequest.adapterIds` 引用适配器 ID；`reason` 必填。
- 发布日志：`sync_log.adapter_id` 记录实际适配器；成功证据包含消息 ID、适配器、协议、快照摘要和载荷摘要。
- 诚实降级：协议无连接器或外部不可达为 `NOT_SYNCED`；配置非法或投递拒绝为 `FAILED`。
- 项目未上线，不保留 `sync_target`、`SyncTarget*` 或永远失败的兼容兜底实现。

## 功能要求
- [x] FR-1：创建配置包草案并加入已发布配置资产。
- [x] FR-2：发布前校验依赖、版本、状态和内容摘要。
- [x] FR-3：导入、导出可验签的离线配置包。
- [x] FR-4：默认 10% 灰度；院级管理员才可直接全量。
- [x] FR-5：通过健康集成适配器投递机构有效快照。
- [x] FR-6：记录成功、失败、未连通日志并导出证据。
- [x] FR-7：仅向原成功适配器执行回滚投递；任一失败不切换包版本。

## 接口与状态
- 客户面：创建、详情、校验、差异、离线导入导出、发布、日志、证据导出、回滚。
- 包状态：`DRAFT → PUBLISHED → ACTIVE → OFFLINE`。
- 通用版本流：`DRAFT → PENDING_REVIEW → PUBLISHED → GRAY/ACTIVE → OFFLINE`。
- 发布计划：`EXECUTING → SUCCESS | FAILED | NOT_SYNCED | ROLLBACKED`。
- 权限：`package.read` / `package.publish` / `package.rollback`；高风险角色只信认证上下文。

## 数据
- 包域：`knowledge_package`、`package_item`、`release_plan`、`sync_log`。
- 集成域：`integration_adapter`。
- 通用版本域：`mk_version_asset_version`、`mk_version_release_plan`、`mk_version_activation_transaction`。
- 五方言保持同表、同字段、同约束语义；公共注释使用中文。

## 产品体验
- 配置包中心只展示适配器名称、协议、健康状态，不展示或复制连接密钥。
- 只有 `ACTIVE + HEALTHY + connectorAvailable` 的适配器可选择。
- 没有可用适配器时阻止发布，并提供“前往适配器中心”入口。
- 失败或未连通站点展示真实原因；没有证据时明确为空。

## 11 视角
1. 产品：一条打包到回滚主流程。
2. 体验：一页一目标，发布为唯一主动作。
3. 数据：关系库记录版本、计划、日志和证据。
4. 医疗安全：失败不激活，高风险全量与回滚受控。
5. 治理：内容摘要、来源、版本与组织范围可追溯。
6. 合规：发布、同步、回滚全留审计。
7. 多租户：适配器与包均按租户隔离。
8. 集成：只复用统一适配器目录和连接器。
9. 运维：探活、RTT、失败原因和离线包可用。
10. 质量：不伪造同步、哈希、灰度或成功状态。
11. 模型：纯确定性 B0，关闭模型不影响发布。

## 验收
- [x] AC-1：真实 HTTP 连接器接收完整有效快照后才返回 `SUCCESS`。
- [x] AC-2：无连接器、未连通、配置非法分别落入真实状态。
- [x] AC-3：灰度、全量、回滚驱动同一通用版本生命周期。
- [x] AC-4：页面只允许健康适配器，零可用项不可提交。
- [x] AC-5：五方言不存在第二套 `sync_target` 表。

## 验证
- 后端：`IntegrationPackageSyncAdapterTest`、`PackageEngineServiceTest`、`PackageEngineControllerSecurityTest`、`RelationalRuleImpactIndexTest`。
- 迁移：`MigrationBaselineContractTest`、`H2BaselineMigrationTest`、`FlywayMultiDialectSmokeTest`。
- 前端：`ConfigPackages.test.tsx`、`hooks.test.ts`、`npm run typecheck`。
- 完成声明仍须通过后端全量、前端全量、T-GATE 与浏览器验收。
