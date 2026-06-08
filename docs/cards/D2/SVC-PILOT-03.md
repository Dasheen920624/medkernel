# SVC-PILOT-03 · 资产准备服务包

> 读卡前置：[核心 CONSTITUTION](../../CONSTITUTION.md) + [D2 域简报](_brief.md)。
> 迁移来源（覆盖矩阵锚点）：落地规划 §12 知识包、配置包与院内同步（L792，消费）· 详规 §S13 包发布与院内同步（L602）· FOUNDATION §4 业务服务包装（L113）· 核心 §4 7 步流。

## 身份
- 卡 ID：SVC-PILOT-03（= backlog 任务 ID）
- 域：D2 试点准备
- 关联场景：S13 包发布与院内同步（资产准备侧）
- 依赖卡：[PKG-01](PKG-01.md)（包发布引擎）· [SYS-04](SYS-04.md)（版本发布）· [KNOW-01](KNOW-01.md)/[TERM-01](TERM-01.md)/[RULE-01](RULE-01.md)/[PATH-01](PATH-01.md)（被打包资产）· [API-10](API-10.md)（包 API）
- 工作量：4d
- owner / reviewer：Codex / PR reviewer（owner ≠ reviewer）

## 目标
提供**资产准备**服务包：把知识包 / 配置包 / 字典 / 规则 / 路径**组装成试点首发配置包**（ConfigPackages），经 7 步流灰度/全量/回滚发布。本卡是**资产编排层**（编排 [PKG-01](PKG-01.md) + 各资产引擎），为 [CFGPKG-01](CFGPKG-01.md) 配置包中心页供服务。

## 现状（搬迁时核查 2026-05-30，以 `medkernel-backend/src` 为准）
`engine/pkg` + 各资产引擎 **已建**（见各卡现状），本卡＝**资产编排 + 试点首发包模板补全**：
- 已有：`engine/pkg`（`KnowledgePackage`/`PackageItem`/`ReleasePlan` 等，详见 [PKG-01](PKG-01.md)）；前端 `pages/tenant/ConfigPackages` 已存在。
- 本卡已补（2026-06-03，2026-06-09 按平台引用制收敛）：① **资产编排**（模板资产 → `PackageItem`）；② **试点首发包模板**（一键应用平台包引用，不复制平台资产）；③ 发布继续复用 [PKG-01](PKG-01.md)/[SYS-04](SYS-04.md) 灰度 / 全量 / 回滚链路；④ 资产就绪快照供配置包中心和实施向导消费。

## 功能要求（原子可测条目）
- [x] **FR-1 资产编排**：跨引擎选资产（知识/字典/规则/路径）加入配置包 `PackageItem`，依赖完整性校验。
- [x] **FR-2 首发包模板**：试点首发配置包模板（最小可运行集），一键应用平台包引用，不复制平台资产。
- [x] **FR-3 7 步流发布**：配置包走选模板/导入→校验→看影响→审核→灰度→全量→回滚（[PKG-01](PKG-01.md)/[SYS-04](SYS-04.md)）。
- [x] **FR-4 资产就绪回报**：配置包发布状态回报实施向导（[SVC-PILOT-01](SVC-PILOT-01.md)）就绪项。
- [x] **FR-5 B0 真实**：全程无模型可跑（导入/配置确定性资产），关模型返回 `B0`/`MODEL_DISABLED` 不伪造。

## 接口契约 / 页面契约
### 接口契约（引擎/API 卡）
- 端点：复用 [API-10](API-10.md) `/engine/pkg/**` + 资产编排接口；首发模板应用平台包引用端点。
- 新增端点：`GET /api/v1/engine/pkg/packages/pilot-templates`、`POST /api/v1/engine/pkg/packages/pilot-templates/{templateCode}/references`、`GET /api/v1/engine/pkg/packages/asset-readiness`。
- DTO：复用 `KnowledgePackage`/`PackageItem`/`ReleasePlan`；新增 `PilotPackageTemplate` / `PilotPackageTemplateItem` / `TenantPackageReference` / `PackageAssetReadinessResponse`。
- 响应信封：`ApiResult` / `ProblemDetail`（[BASE-03](../D0/BASE-03.md)）。
- 状态机：配置包核心 §3 配置类 + 变更类（[SYS-04](SYS-04.md)）。
- 幂等 / 错误码 / traceId：组包/发布幂等键；依赖缺失 → `PACKAGE_DEPENDENCY_MISSING`；traceId（[OBS-01](../D0/OBS-01.md)）。
### 页面契约（页面卡）
- `ConfigPackages` 新增“首发资产准备”区：展示模板数、草案 / 已发布 / 激活 / 平台引用数量、灰度证据与阻塞项；阻塞项来自后端复算，不在前端伪造。
- “应用首发引用”弹窗展示模板推荐的平台包清单，提交后只创建租户到平台包的引用，不复制 `knowledge_package` / `package_item`。

## 数据与迁移
- 表族（已有）：`knowledge_package`/`package_item`/`release_plan`（[PKG-01](PKG-01.md)）；本卡补 `mk_pkg_pilot_package_template` / `mk_pkg_pilot_template_item`，P5 引用制补 `mk_pkg_tenant_package_reference`。
- 不在迁移中写入假首发模板 / 假医学资产 seed；模板必须来自真实配置或导入。
- 主键 ULID；索引：`package_identity`、`org_path`。
- 5 方言迁移一致 + 中文注释。

## 视角清单（11 视角逐条）
1. **产品架构**：把分散资产组装成"试点能跑的一套"的服务包。
2. **产品体验**：N·A —— 配置包中心页（[CFGPKG-01](CFGPKG-01.md)）。
3. **系统与数据架构**：依赖完整性校验；大配置包分块；发布证据真实。
4. **临床医疗安全**：首发包经审核灰度才上临床；高危资产替换走 [SYS-08](SYS-08.md)。
5. **知识与数据治理**：资产版本化组包、可回滚、可溯。
6. **安全合规与监管**：组包/发布留审计（[BASE-04](../D0/BASE-04.md)）。
7. **集团化与多租户治理**：配置包七层继承下发（[SYS-04](SYS-04.md)）；集团首发包 + 院内定制。
8. **集成与互操作**：同步经 [PKG-01](PKG-01.md)/[INTEG-01](INTEG-01.md)；无通道 `NOT_SYNCED`。
9. **运维 / SRE / 国产化**：5 方言；离线首发包导入。
10. **质量与真实性审计**：组包/发布真实证据、无伪造（铁律 #1）。
11. **AI / 模型治理与可降级**：★**B0＝确定性资产组包**；AI 生成资产入包前必经审核（[KNOW-02](KNOW-02.md)），关模型组包发布不变。

## 适用不变量
- 命中核心约束：**§4 7 步流** · **铁律 #4 B0 先于模型** · **§9 继承** · **依赖 [PKG-01](PKG-01.md)/[SYS-04](SYS-04.md)**。
- 本卡落点：把"准备一套可发布资产"编排为模板化、可校验、可回滚的服务包，是 D3 能起步的料。

## 验收 + 验证
- [x] **AC-1（FR-1/2）**：从首发模板应用平台包引用 + 跨引擎加资产；依赖缺失 → `PACKAGE_DEPENDENCY_MISSING`。
- [x] **AC-2（FR-3）**：配置包 7 步流灰度→全量→回滚（[PKG-01](PKG-01.md)）。
- [x] **AC-3（FR-4/5）**：发布状态回报实施向导；关模型全程可跑（B0）。
- 关联 A1–A9 剧本：A3 资产准备、A4 发布回滚。
- T-GATE：真实性门禁全绿。
- B0 验收：确定性组包发布，**天然 B0**。

## 完工证据
- 代码 permalink：`PilotPackageTemplate` + 资产编排 + 发布接 [PKG-01](PKG-01.md)/[SYS-04](SYS-04.md) + 5 方言迁移。
- 测试：资产编排/依赖校验测试 + 首发模板平台引用测试 + 7 步流发布回滚测试 + 关模型 B0 测试。
- 2026-06-03 本地目标证据：`mvn -q -Dtest=PackageEngineServiceTest,PackageEngineControllerSecurityTest,MigrationBaselineContractTest,H2BaselineMigrationTest,ServiceContractGovernanceTest,DomainOwnershipContractTest test` 通过；`node scripts/migration-convention-guard.mjs --mode=files ...V65...` 通过；`npm test -- --run src/shared/api/hooks.test.ts src/pages/tenant/ConfigPackages.test.tsx src/pages/tenant/RulePathwayCleanliness.test.ts` 通过（3 files / 36 tests）。
- 2026-06-03 本地全量证据：rebase 到 `origin/main` 后 `mvn -q test` 通过（H2 / PostgreSQL 15.18 / Oracle 21.3 到 v65 并二次 no-op）；`npm run verify` 通过（47 files / 268 tests）；`npm audit --omit=dev --audit-level=moderate` 为 0 vulnerabilities；`npm run build` 通过（保留已登记的 `vendor-antd` 大 chunk 警告）；T-GATE 脚本自测 34/34、真实性 inventory、配置边界 inventory、V65 迁移 files、提交后 changed-mode（真实性 17 文件 / 配置边界 15 文件 / 迁移 5 文件）、中文注释和 `git diff --check` 均通过。
- 审计记录：[SVC-PILOT-03 资产准备服务包实施记录](../../audit/SVC-PILOT-03-asset-preparation-service-package.md)。
- 审计员签字：@<reviewer>（owner ≠ reviewer）。
