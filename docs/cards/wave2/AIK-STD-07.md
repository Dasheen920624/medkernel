# AIK-STD-07 · 知识包/配置包生成与院内同步

> 读卡前置：[核心 CONSTITUTION](../../CONSTITUTION.md) + [wave2 域简报](_brief.md)。
> 迁移来源：详规 §8 包生成 · backlog 第二波 X-AIK · 铁律 #2 诚实降级。

## 身份
- 卡 ID：AIK-STD-07（= backlog `AIK-STD-07`）
- 域：wave2（X-AIK）
- 关联场景：S3、S13 包发布
- 依赖卡：[PKG-01](../D2/PKG-01.md)（包发布引擎）· [SYS-04](../D2/SYS-04.md)（版本发布框架）· [AIK-STD-01](AIK-STD-01.md)
- 工作量：4d
- owner / reviewer：待派单（owner ≠ reviewer）

## 目标
把审过的生成资产**打包成知识包/配置包并同步院内**：复用 [PKG-01](../D2/PKG-01.md) 灰度/全量/回滚，无通道诚实 `NOT_SYNCED`。

## 现状（核查 2026-06-17）
承载＝D2 [PKG-01](../D2/PKG-01.md) 包发布引擎（导入/导出/校验/灰度/全量/同步/回滚）已建；本卡后端已补「已审知识资产 → 包」装配：
- `POST /api/v1/engine/pkg/packages/aik` 只接受当前租户 `ACTIVE` 知识版本 ID，生成 `knowledge_package` 草稿与 `package_item`。
- V141 五方言新增 `mk_aik_pack_job`，保存资产 manifest、`manifest_sha256`、包引用和作业状态。
- 发布仍复用 PKG-01 `/release|/sync|/rollback`；`PackageSyncRequest.adapterIds=[]` 会落 `ReleasePlanStatus.NOT_SYNCED`，不再伪造成功。
- 前端批量装配入口仍归 Phase 8/T3.5 体验收尾；当前后端 API 可被生产编排、CLI 或前端调用。

## 功能要求（原子可测条目）
- [x] FR-1 装配：审过资产装配为知识包/配置包（带清单 + 版本）。
- [x] FR-2 校验：包完整性 + 依赖校验，不合格拒发。
- [x] FR-3 同步：接 [PKG-01](../D2/PKG-01.md) 灰度 → 全量 → 可回滚。
- [x] FR-4 诚实同步：无同步通道返回 `NOT_SYNCED`，不伪造成功。
- [x] FR-5 证据：同步留真实证据（清单 hash + 时点）。

## 接口 / 数据契约
- 复用 PKG-01 包/同步表 + `mk_aik_pack_job`（资产清单/包 ref/状态），五方言。
- 新增 `AikPackageBuildRequest` / `AikPackageBuildResponse`：标准上下文 + `packageCode` / `packageVersion` / `assetVersionIds[]`，响应 `jobId`、包草稿、`itemCount`、`manifestSha256`。

## 视角清单（11 视角）
1. 产品架构：生成资产到院内的交付层。 2. 产品体验：N·A。 3. 系统与数据架构：包同步异步。 4. 临床医疗安全：仅审过资产可打包。 5. 知识与数据治理：包版本化、可回滚。 6. 安全合规与监管：同步证据可审计。 7. 集团化与多租户治理：按 org 灰度。 8. 集成与互操作：复用 PKG-01 同步通道。 9. 运维/SRE/国产化：★无通道诚实 NOT_SYNCED。 10. 质量与真实性审计：★同步证据真实、不伪造成功。 11. AI/模型治理与可降级：N·A（包内容与产出方式无关）。

## 适用不变量
- 命中核心约束：**铁律 #2 诚实降级**（NOT_SYNCED）· **#1 真实性** · **核心 §13 发布证据**。
- 本卡落点：审过资产打包接 PKG-01 同步，无通道诚实、证据真实。

## 验收 + 验证
- [x] AC-1（FR-1~3）：装配 + 校验 + 接 PKG-01 灰度/全量/回滚。
- [x] AC-2（FR-4/5）：无通道 NOT_SYNCED；同步证据真实。
- T-GATE：后端真实性门禁全绿。
- B0 验收：★无 AI 时手工/既有资产同样可打包同步。

## 完工证据
- 代码 permalink：`AikKnowledgePackageService` / `PackageEngineController#buildAikPackage` / `PackageEngineService#recordNotSyncedPlanForMissingChannel`。
- 测试：`AikKnowledgePackageServiceTest`、`PackageEngineServiceTest#syncPackageReturnsNotSyncedWhenNoAdapterChannelIsSelected`、`PackageEngineControllerSecurityTest#aikPackageBuildUsesUnifiedPackageRoot`、`MigrationBaselineContractTest#aikPackJobIsPersistedAcrossAllDialects`。
- 审计员签字：@<reviewer>（owner ≠ reviewer）。
