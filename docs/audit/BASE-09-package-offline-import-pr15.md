# BASE-09 配置包离线导入验签与草案落库 PR15 记录

> 日期：2026-06-01  
> 分支：`codex/base-09-package-offline-import`  
> 范围：BASE-09 第十五批净化，不宣称 BASE-09 完成。

## 目标

补齐配置包离线安装能力的第二段：导入 PR14 生成的离线包 JSON 时，必须先校验格式、租户、manifest / payload 一致性和真实 SHA-256 摘要；通过后只生成本地 `DRAFT` 草案和配置条目绑定，写入 `IMPORT` 审计，不自动激活、不绕过发布流程、不伪造资产内容。

## 改动

- `PackageEngineService.importOfflinePackage` 新增离线包导入：拒绝错误格式、错误摘要算法、`payloadSha256` 篡改、manifest / payload 不一致、跨租户导入、重复包编码版本和重复资产条目。
- 导入成功时生成新的本地 `KnowledgePackage.packageId` 和新的 `PackageItem.itemId`，状态固定为 `DRAFT`；源包 ID 只作为校验上下文，不复用远端 ID。
- 导入条目按资产类型校验真实资产状态：规则、路径、评估指标、术语字典和知识资产使用既有校验入口；暂不把离线 JSON 当成资产内容来源。
- `PackageEngineController` 新增 `POST /api/v1/engine/packages/offline/import`，沿用 `package.publish` 权限。
- `AuditAction` 增加 `IMPORT`，导入成功后记录 `knowledge_package` 审计。
- 配置包中心新增“导入离线包”入口，支持粘贴 JSON 或选择 JSON 文件，导入后关闭弹窗、刷新列表，并明确提示“保持草案状态”。
- 修复浏览器验收发现的分页契约旧字段：配置包列表 hook 改回统一 `PageResponse.total`，总数统计和分页不再读取旧 `totalCount`。

## TDD 证据

- 后端红灯：`mvn -B -q -Dtest=PackageEngineServiceTest,PackageEngineControllerSecurityTest test` 先因缺少 `PackageOfflineImportRequest`、`PackageOfflineImportResponse`、`importOfflinePackage` 和 `AuditAction.IMPORT` 失败。
- 后端绿灯：同目标测试通过；新增用例覆盖导入草案落库、新本地 ID、条目绑定、`IMPORT` 审计，以及 payload 被篡改时以 `ENG_EVID_002` 拒绝且不保存。
- 前端红灯：`npm test -- src/shared/api/hooks.test.ts src/pages/tenant/ConfigPackages.test.tsx` 先因 `importPackageOfflinePackage` / “导入离线包”入口缺失失败。
- 前端绿灯：同目标测试通过，覆盖导入 helper 和页面导入交互。
- 浏览器发现的分页契约红灯：模拟后端真实 `PageResponse.total` 后，配置包累计统计仍显示 0；修复后目标测试通过并在真实浏览器复验为 2。

## 已验证

- `mvn -B -q test` 通过；Docker Desktop 可用，Testcontainers 覆盖 H2、PostgreSQL 15、Oracle 21 迁移烟测。
- `npm test -- src/pages/tenant/ConfigPackages.test.tsx src/shared/api/hooks.test.ts` 通过：2 files / 5 tests。
- `npm test` 通过：34 files / 127 tests。
- `npm run typecheck` 通过。
- `npm run build` 通过；保留既有 `vendor-antd` chunk 大小 warning。
- `npm run lint` 通过，仍有 11 个既有 warning，未新增 touched file error。
- `npm run format:check` 通过。
- 浏览器验收：当前分支前端 `http://127.0.0.1:5177` + 当前分支后端 `18080`，登录 `hospital-admin`，进入 `/config/packages`，页面导入 `PKG.UIIMPORT.1780281522933` 后表格可见新草案；总配置包版本 = 2，草案 = 2，分页总数 = 2；控制台错误 `[]`。

## 未完成与下一步

- 本 PR 只实现离线包元数据和条目绑定导入，不把离线包 JSON 当作完整资产内容仓库；离线迁移完整资产内容需要后续卡补充资产内容导出 / 导入契约后再做。
- BASE-09 仍需继续清域级验收残留和最终净化报告；不得恢复旧示例、旧 `totalCount` 字段、假同步证据、默认版本假填充或临时兼容层。
