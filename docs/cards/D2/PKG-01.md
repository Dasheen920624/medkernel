# PKG-01 · 包发布引擎

> 读卡前置：[核心 CONSTITUTION](../../CONSTITUTION.md) + [D2 域简报](_brief.md)。
> 迁移来源（覆盖矩阵锚点）：落地规划 §12 知识包、配置包与院内同步（L792 / 12.1 包结构 L794 / 12.2 发布流程 L811 / 12.3 同步验收 L834）· 详规 §8.10 知识包标准（L1781）· 核心 §10 集成边界 / 铁律 #2 不伪造同步。

## 身份
- 卡 ID：PKG-01（= backlog 任务 ID）
- 域：D2 试点准备
- 关联场景：S13 包发布与院内同步
- 依赖卡：[SYS-04](SYS-04.md)（版本与发布框架，本卡是其包级落地）· [SYS-08](SYS-08.md)（权威知识替换同步）· [INTEG-01](INTEG-01.md)（同步通道）· [BASE-04](../D0/BASE-04.md)（同步审计）· [BASE-05](../D0/BASE-05.md)（5 方言）
- 工作量：6d
- owner / reviewer：待派单（owner ≠ reviewer）

## 目标
提供知识包/配置包的**导入 / 导出 / 校验 / 灰度 / 全量 / 同步 / 回滚**引擎 + **真实同步证据**：把规则/路径/知识/字典打包，经 [SYS-04](SYS-04.md) 发布流推到院内/离线节点；**无同步通道诚实返回 `NOT_SYNCED`、绝不伪造哈希/同步成功**（铁律 #2）。

## 现状（搬迁时核查 2026-05-30，以 `medkernel-backend/src` 为准）
`engine/pkg` **已实质建成**，本卡＝**契约化 + 同步证据/回滚/校验补全**：
- 已有：`KnowledgePackage`(+`Status`)、`PackageItem`(+`AssetType`)、`ReleasePlan`(+`Status`/`ReleaseScopeType`/`ReleaseStrategy`)、`PackageSyncPort` + `LenientPackageSyncAdapter`、`SyncTarget`(+`Status`/`Type`)、`SyncLog`(+`Status`)、`PackageEngineService`/`Controller`、`PackageDiffResponse`、`PackageSyncRequest`/`Response`。
- 缺口（本卡补）：① 包**校验**（完整性/依赖/版本/content_hash）；② **灰度/全量/回滚**对齐 [SYS-04](SYS-04.md) 变更类状态机；③ **真实同步证据**（同步水位 + 失败站点 + `NOT_SYNCED`）；④ 离线包导入/导出（内外网双形态）。
- 2026-06-03 PR1 基线（#277，merge `db92d43b`）：发布前校验响应新增稳定 `contentSha256`，摘要来自包编码 / 版本 / 资产类型 / 资产 ID / 资产版本声明；`validatePackage` 已对 `RULE` / `PATHWAY` / `EVALUATION` 资产做真实存在与已发布状态校验，缺失或未发布返回 `BLOCKING` 问题；尚未接入统一依赖适配器的资产类型在发布前被阻断，且 `syncPackage` / `releasePackage` 创建发布计划前强制复用同一校验，避免未核验资产绕过校验直接发布。
- 2026-06-03 后续硬化（#278，merge `8fe8634e`）：`KNOWLEDGE` 已补知识身份编码 + 权威版本号的稳定入包契约、依赖状态校验、离线导出 / 导入快照；`TERMINOLOGY` 已补术语映射包编码 + 范围键 + 包版本的稳定入包契约、包级依赖校验、映射条目离线导出 / 导入快照，配置包中心选择器改为读取已发布术语包并自动带出版本，不再生成 `term-map-*` 临时资产 ID。`FOLLOWUP` 当前只有患者运行计划 / 任务，禁止作为配置包资产入包，已登记 `DEFER-019` 等 D3 随访模板资产化后回接；不得用患者运行数据冒充可发布配置资产。
- 2026-06-03 PR2（#280，merge `a0a13416`）：包发布入口开始对齐 [SYS-04](SYS-04.md) 灰度 / 全量 / 回滚语义：`GRAYSCALE` 缺省作用域时生成 `{strategy=BED_PERCENT, percentage=10, scopeCode=<targetOrgUnitId>}` 的灰度范围快照并保持包 `PUBLISHED`、不覆盖 ACTIVE；`FULL` 发布必须由院级管理员 / 等价院级管理角色确认，非院级角色在创建发布计划和触发同步前被拒；回滚沿用既有高危确认、同编码 OFFLINE 历史目标、成功同步证据、失败 / `NOT_SYNCED` 不切包状态的安全闭环。配置包中心默认选择灰度发布、非院级角色禁用全量，并清理触碰范围旧 `Timeline.Item` 废弃用法。
- 2026-06-03 PR3 本轮（`codex/d2-pkg-01-pr3-sync-evidence`）：同步证据导出补齐 AC-3 最小闭环：`PackageEngineService.exportSyncEvidence` 按包、发布计划和目标同步日志导出 NDJSON 摘要 / 计划 / 目标行；`FAILED` / `NOT_SYNCED` 站点保留真实状态、原因和空 `syncEvidence`，不伪造成已同步水位；客户面新增 `GET /engine/pkg/packages/{packageId}/sync-logs/export`；配置包中心在同步弹窗显示失败 / 未接入站点汇总、逐站点原因和“导出同步证据”入口。

## 功能要求（原子可测条目）
- [ ] **FR-1 打包/导出**：选规则/路径/知识/字典资产打成 `KnowledgePackage`（含 `PackageItem` + 依赖 + content_hash）；可导出离线包。
- [ ] **FR-2 导入校验**：导入校验完整性/依赖/版本/hash；缺依赖或 hash 不符 → 拒绝并报缺口，不静默吞。
- [ ] **FR-3 灰度/全量发布**：经 [SYS-04](SYS-04.md) 变更类状态机（灰度默认 10% 床位 → 全量）；仅院级管理员可直接全量。
- [ ] **FR-4 同步**：经 `PackageSyncPort` 推到 `SyncTarget`；记 `SyncLog` 水位；**无通道 → `NOT_SYNCED`**，不伪造成功。
- [ ] **FR-5 回滚**：回滚到上一包版本，不丢审计、不破坏历史解释；高危包回滚过安全校验。
- [ ] **FR-6 真实证据**：每次发布/同步/回滚生成证据（范围/差异 `PackageDiffResponse`/同步结果/失败站点），可导出。

## 接口契约 / 页面契约
### 接口契约（引擎/API 卡）
- 端点：引擎能力；REST 客户面在 [API-10](API-10.md)。`PackageEngineService` 承接打包/校验/发布/同步/回滚。
- DTO：复用 `KnowledgePackage`/`PackageItem`/`ReleasePlan`/`PackageSyncRequest`/`Response`/`PackageDiffResponse`/`SyncLog`。
- 状态机：包版本核心 §3 配置类 + 变更类（[SYS-04](SYS-04.md)）；同步任务 `SyncTargetStatus`/`SyncLogStatus`。
- 幂等 / 错误码 / traceId：发布/同步幂等键；无通道 → `NOT_SYNCED`；hash 不符 → `PACKAGE_INTEGRITY_FAILED`；高危回滚 → `ROLLBACK_SAFETY_DENIED`；traceId（[OBS-01](../D0/OBS-01.md)）。
### 页面契约（页面卡）
N·A —— 本卡无页面。呈现在 **D2 配置包中心页**（发布/同步/回滚 + 失败站点）；本卡供引擎。

## 数据与迁移
- 表族（已有）：`knowledge_package`/`package_item`/`release_plan`/`sync_target`/`sync_log`；本卡补 content_hash/校验/证据字段。
- 主键 ULID；唯一约束：`(package_identity, version)`；索引：`status`、`sync_target`、`org_path`。
- 5 方言迁移一致 + 中文注释；与 [SYS-04](SYS-04.md) `release_plan` 复用并泛化。

## 视角清单（11 视角逐条）
1. **产品架构**：配置资产的"出厂 + 分发"引擎；[SYS-04](SYS-04.md) 框架的包级落地。
2. **产品体验**：N·A —— 配置包中心页（D2）呈现；失败站点可见。
3. **系统与数据架构**：同步水位/差异/证据真实；大包导入分块；离线包内外网双形态。
4. **临床医疗安全**：高危包（含权威知识替换）同步走 [SYS-08](SYS-08.md)，旧包限制继续服务。
5. **知识与数据治理**：包版本化、content_hash、可回滚、替代非覆盖。
6. **安全合规与监管**：发布/同步/回滚全留审计（[BASE-04](../D0/BASE-04.md)）；证据可导出供监管。
7. **集团化与多租户治理**：包按七层继承下发（[SYS-04](SYS-04.md)）；灰度默认 10%、仅院级直接全量。
8. **集成与互操作**：★同步经 [INTEG-01](INTEG-01.md) 通道；**无通道 `NOT_SYNCED` 不伪造**（核心 §10）。
9. **运维 / SRE / 国产化**：5 方言；离线包导入；失败站点告警与补同步；内网离线/外网在线双形态。
10. **质量与真实性审计**：★无伪造同步哈希/无假灰度（铁律 #1/#2）；同步证据真实可核。
11. **AI / 模型治理与可降级**：包内容确定性；AI 生成资产入包前必经审核（[KNOW-02](KNOW-02.md)）；关模型打包/同步不变。

## 适用不变量
- 命中核心约束：**§10 集成边界 / 同步不破坏权威版本** · **铁律 #2 不伪造同步** · **§9 灰度继承** · **依赖 [SYS-04](SYS-04.md)/[SYS-08](SYS-08.md)/[INTEG-01](INTEG-01.md)**。
- 本卡落点：把"打包→校验→灰度→全量→同步→回滚"做成真实可证、无通道诚实降级的发布引擎。

## 验收 + 验证
- [ ] **AC-1（FR-1/2）**：打包导出 + 导入校验；缺依赖/hash 不符 → `PACKAGE_INTEGRITY_FAILED` 报缺口。
- [ ] **AC-2（FR-3/5）**：灰度 10%→全量→回滚（[SYS-04](SYS-04.md)）；回滚审计/历史解释完整。
- [ ] **AC-3（FR-4/6）**：无同步通道 → `NOT_SYNCED`（非伪造成功）；有通道 → `SyncLog` 水位真实、失败站点可见、证据可导出。
- 关联 A1–A9 剧本：A4 发布回滚、A6 合规运维（同步证据）。
- T-GATE：真实性门禁全绿（无伪造同步哈希/灰度）。
- B0 验收：打包/同步纯确定性，**天然 B0**；关模型行为不变。

## 完工证据
- 代码 permalink：`PackageEngineService` 校验/灰度/回滚 + `PackageSyncPort` 真实证据 + `NOT_SYNCED` + content_hash + 5 方言迁移。
- 测试：导入校验/完整性测试 + 灰度回滚测试 + 无通道 `NOT_SYNCED` 测试 + 同步水位/失败站点测试 + 证据导出测试。
- PR1 基线证据（#277）：后端包发布聚焦基线 `mvn -q -Dtest=PackageEngineServiceTest,PackageEngineControllerSecurityTest,LenientPackageSyncAdapterTest test` 通过；红灯先失败于缺 `PackageValidateResponse.contentSha256()`，新增红灯又先失败于 `releasePackage` 未阻断术语资产，随后补稳定内容摘要、缺失资产依赖阻断、未接入适配器资产类型阻断和发布入口强制校验；`mvn -q -Dtest=PackageEngineServiceTest#validatePackageReturnsValidWhenPackageHasRealItems+validatePackageBlocksWhenDeclaredAssetDependencyIsMissing test`、`mvn -q -Dtest=PackageEngineServiceTest#releasePackageBlocksWhenValidationHasBlockingIssues test`、`mvn -q -Dtest=PackageEngineServiceTest test`、后端全量 `mvn -q test` 通过，含 PostgreSQL 15.18 / Oracle 21.3 迁移至 V53；前端 `npm run typecheck`、`npm test -- src/shared/api/hooks.test.ts src/pages/tenant/ConfigPackages.test.tsx`、`npm run verify`（44 文件 / 231 测试）和 `npm run build` 通过；T-GATE 34/34、真实性全仓、配置边界、迁移 changed、中文注释、`git diff --check` 和生产依赖 `npm audit --omit=dev` 通过。
- 后续硬化证据（#278，merge `8fe8634e`）：红灯先失败于 `KNOWLEDGE` / `TERMINOLOGY` 仍被未接入适配器阻断，随后补知识 / 术语包级依赖校验；红灯再失败于 `TERMINOLOGY` 离线导出缺完整资产快照，随后补知识身份 / 版本与术语包 / 映射 / 包条目离线导出；新增离线导入持久化知识 / 术语快照测试，并补坏术语映射枚举在落库前返回 `ENG_PACKAGE_002` 的回归测试。已验证 `mvn -q -Dtest=PackageEngineServiceTest test`、`mvn -q -Dtest=PackageEngineServiceTest,PackageEngineControllerSecurityTest,LenientPackageSyncAdapterTest test`、后端全量 `mvn -q test` 通过，含 Docker Testcontainers PostgreSQL 15.18 / Oracle 21.3 迁移至 V53；前端 `npm test -- src/shared/api/hooks.test.ts src/pages/tenant/ConfigPackages.test.tsx` 2 文件 / 19 测试、`npm run typecheck`、`npm run verify`（44 文件 / 232 测试）、`npm run build`、`npm audit --omit=dev`（0 漏洞）通过；T-GATE 34/34、真实性全仓 796 文件、配置边界 inventory 738 文件、迁移 changed 0 文件、中文注释 0 fail / 0 warn、`git diff --check` 通过；远端 CI 8/8 通过后合入。项目 Playwright 访问 `http://127.0.0.1:5176/config/packages` 验证配置包中心：术语包版本自动带出 `2026.06`、版本输入只读、提交载荷为 `assetType=TERMINOLOGY`、`assetId=TERM.LAB|DEPARTMENT|CARD`、`assetVersion=2026.06`、标准上下文 `package_version=3.0.0`，无控制台错误，截图 `/tmp/medkernel-config-packages-term-pkg.png`。本机 in-app browser 仍无可用 `iab` 实例，按 `DEFER-004` 登记，不阻塞本卡；`DEFER-019` 关闭前不得宣称随访配置资产入包完成。
- PR2 证据（#280，merge `a0a13416`）：红灯先失败于配置包中心默认全量和缺少默认 10% 灰度说明；后端红灯先失败于非院级角色全量未在发布计划前拒绝、灰度缺省范围被拒。随后补 `PackageEngineService` 发布请求规范化、院级角色门禁、默认 10% 床位灰度范围快照；配置包中心改为默认灰度、非院级角色禁用全量、提交载荷为 `strategy=GRAYSCALE/scopeType=ALL` 交由后端生成灰度快照，并迁移 `Timeline.Item` 到 `Timeline items`。已通过 `mvn -q -Dtest=PackageEngineServiceTest#releasePackageRejectsDirectFullForNonHospitalAdminRole+releasePackageDefaultsGrayscaleToTenPercentBedScopeWhenNoScopeProvided test`、`mvn -q -Dtest=PackageEngineServiceTest test`、`mvn -q -Dtest=PackageEngineServiceTest,PackageEngineControllerSecurityTest,LenientPackageSyncAdapterTest test`、后端全量 `mvn -q test`（含 Docker Testcontainers PostgreSQL 15.18 / Oracle 21.3 迁移至 V53）、`npm test -- src/pages/tenant/ConfigPackages.test.tsx`（7 tests）、`npm test -- src/shared/api/hooks.test.ts src/pages/tenant/ConfigPackages.test.tsx`（2 文件 / 21 tests）、`npm run typecheck`、`npm run verify`（44 文件 / 234 测试）、`npm run build`、`npm audit --omit=dev`（0 漏洞）。T-GATE 34/34、真实性全仓 796 文件、配置边界 inventory 738 文件、迁移 changed 0 文件、中文注释 0 fail / 0 warn、`git diff --check` 通过。项目 Playwright 访问 `http://127.0.0.1:5176/config/packages` 验证配置包中心：灰度发布默认选中、全量发布对非院级角色禁用、10% 床位灰度说明可见、提交载荷为 `strategy=GRAYSCALE`、`scopeType=ALL`、`scopeValue=""`、`targetIds=["target-his"]`、标准上下文 `package_version=3.0.0`、`role_codes=["implementation-engineer"]`，页面错误 / 控制台错误 / 未覆盖 API 均为 0，截图 `/tmp/medkernel-pkg-pr2-rollout.png`。本轮不冒领 PR3 同步证据导出和失败站点。
- PR3 本轮证据（待 PR 合并后补 merge）：红灯先失败于 `PackageEngineService` 缺 `exportSyncEvidence`、控制器缺同步证据 NDJSON 下载端点、配置包中心缺失败 / 未接入站点汇总和导出入口。随后补服务层按发布计划 / 目标日志导出 `PACKAGE_SYNC_EVIDENCE_SUMMARY` / `PACKAGE_SYNC_PLAN` / `PACKAGE_SYNC_TARGET` NDJSON，`FAILED` / `NOT_SYNCED` 保留真实原因且不补空证据；补客户面 `GET /engine/pkg/packages/{packageId}/sync-logs/export` 与 `package.read` 权限；前端共享 API 增加 `downloadPackageSyncEvidenceExport`，配置包中心显示失败 / 未接入站点告警、逐站点状态和“系统不会伪造成已同步”提示。已通过 `mvn -q -Dtest=PackageEngineServiceTest#exportSyncEvidenceIncludesFailedSitesAndDoesNotForgeEvidence,PackageEngineControllerSecurityTest#authorizedUserCanDownloadSyncEvidenceNdjson test`、`mvn -q -Dtest=PackageEngineServiceTest,PackageEngineControllerSecurityTest,LenientPackageSyncAdapterTest test`、后端全量 `mvn -q test`（含 Docker Testcontainers PostgreSQL 15.18 / Oracle 21.3 迁移至 V53）、`npm test -- src/shared/api/hooks.test.ts src/pages/tenant/ConfigPackages.test.tsx`（2 文件 / 23 tests）、`npm run typecheck`、`npm run verify`（44 文件 / 236 测试）、`npm run build`、`npm audit --omit=dev`（0 漏洞）。T-GATE 34/34、真实性全仓 796 文件、配置边界 inventory 738 文件、迁移 changed 0 文件、中文注释 0 fail / 0 warn、`git diff --check` 通过。项目 Playwright 访问 `http://127.0.0.1:5173/config/packages` 验证配置包中心：失败 / 未接入站点汇总可见、同步证据导出命中、提交载荷为 `strategy=GRAYSCALE`、`scopeType=ALL`、`scopeValue=""`、`targetIds=["target-his"]`、标准上下文 `package_version=3.0.0`、`role_codes=["implementation-engineer"]`，页面错误 / 控制台错误 / 未覆盖 API 均为 0，截图 `/tmp/medkernel-pkg-pr3-sync-evidence.png`。本机 in-app browser 仍无可用 `iab` 实例，按 `DEFER-004` 登记并用项目 Playwright 代验。
- 审计员签字：@<reviewer>（owner ≠ reviewer）。

## 大卡工序（6d，后端引擎；按 PR 拆分）
- PR1：打包/导出 + 导入校验（依赖/hash）→ AC-1。
- PR2：灰度/全量/回滚接 [SYS-04](SYS-04.md) → AC-2。
- PR3：同步证据 + `NOT_SYNCED` + 失败站点 + 导出 → AC-3。
