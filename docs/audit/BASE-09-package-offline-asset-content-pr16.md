# BASE-09 配置包离线资产内容迁移契约 PR16 记录

> 日期：2026-06-01  
> 分支：`codex/base-09-offline-asset-content`  
> 范围：BASE-09 第十六批净化，不宣称 BASE-09 完成。

## 目标

补齐配置包离线安装能力的第三段：PR14/PR15 已完成离线 JSON 导出、导入验签和草案条目绑定，本批必须让离线包携带真实资产内容快照。导出端按资产条目生成内容快照和独立摘要；导入端逐层验签，落真实资产内容或校验本地已有资产一致性；暂未支持完整内容契约的资产类型必须诚实拒绝，禁止“只绑定条目但声称完整迁移”的假成功。

## 改动

- `PackageEngineService.exportOfflinePackage` 的 payload 新增 `assetSnapshots`；manifest 新增 `assetSnapshotCount`，与 `itemCount` 一起校验离线包完整性。
- 规则资产快照包含 `RuleDefinition` 与指定 `RuleVersion`，导出时通过 `RuleVersionRepository.findByRuleIdAndTenantIdAndVersionNo` 读取条目版本号对应内容。
- 评估指标快照包含 `EvaluationIndicator` 完整口径字段。
- 每个快照写入真实 `contentSha256`；导入时先校验 payload 摘要，再校验每个快照的内容摘要、重复快照、缺失快照和条目匹配关系。
- 导入新资产时写入当前租户、当前操作者、当前 traceId 与本地创建/更新时间；已有资产则重新构造本地快照并比较真实摘要，不一致时以冲突拒绝。
- `PATHWAY` / `TERMINOLOGY` / `KNOWLEDGE` / `FOLLOWUP` 暂无完整内容迁移契约，本批以 `ENG_PACKAGE_002` 诚实拒绝，不退回引用式假迁移。
- 非法时间字段现在返回标准 `BAD_REQUEST` `ApiException`，不泄漏底层 `DateTimeParseException`。

## TDD 证据

- 后端绿色基线：`mvn -B -q -Dtest=PackageEngineServiceTest,PackageEngineControllerSecurityTest test` 在改动前通过。
- 后端红灯 1：补 `exportOfflinePackageReturnsPayloadWithPhysicalSha256AndPublishesAudit` / `importOfflinePackagePersistsDraftWithVerifiedPayloadAndNewLocalIds` 对 `assetSnapshots` 和真实资产落库的断言后，目标测试因 `PackageEngineService` 缺少 `RuleVersionRepository` 构造参数和离线快照能力失败。
- 后端绿灯 1：实现 `RULE` / `EVALUATION` 快照导出、内容摘要、导入落库与本地一致性校验后，`PackageEngineServiceTest` 通过。
- 后端红灯 2：新增 `importOfflinePackageRejectsMalformedAssetTimestampAsApiError` 后，非法 `publishedAt` 先泄漏为 `DateTimeParseException`。
- 后端绿灯 2：`parseInstant` 收敛非法时间为 `BAD_REQUEST` `ApiException` 后，目标用例通过。

## 已验证

- `mvn -B -q -Dtest=PackageEngineServiceTest#importOfflinePackageRejectsMalformedAssetTimestampAsApiError test` 通过。
- `mvn -B -q -Dtest=PackageEngineServiceTest,PackageEngineControllerSecurityTest test` 通过。
- 前端目标回归：`npm test -- src/pages/tenant/ConfigPackages.test.tsx src/shared/api/hooks.test.ts` 通过（2 files / 5 tests）。
- 后端全量：`mvn -B -q test` 通过；本机 Docker / Testcontainers 覆盖 H2、PostgreSQL 15、Oracle 21 迁移烟测。
- 前端全量：`npm test` 通过（34 files / 127 tests）。
- 前端静态验证：`npm run typecheck`、`npm run build`、`npm run lint`、`npm run format:check` 均通过；`lint` 仅保留既有 11 条 warning，未新增阻断项；`build` 仅保留既有 `vendor-antd` chunk 体积提示。
- T-GATE：`node scripts/authenticity-guard.mjs --mode=inventory` 通过；`node --test scripts/config-boundary-guard.test.mjs scripts/migration-convention-guard.test.mjs scripts/authenticity-guard.test.mjs` 通过（24 tests）；`git diff --check` 通过。
- 生产路径旧分页契约核查：`rg -n "totalCount" frontend/src medkernel-backend/src` 无输出，未恢复旧 `totalCount` 兼容层。
- 浏览器验收：在 `http://127.0.0.1:5173/login` 切换登录页暗黑主题，使用 dev 账号 `hospital-admin` / `t-1` 登录到 `/dashboard`；进入 `/config/packages` 后创建本地草案 `PKG.UI.PR16.091552`，列表显示总数 1、草案 1、状态 `DRAFT`，导入弹窗显示“导入后保持草案状态”，空提交提示“请先选择或粘贴离线包 JSON。”；页面错误日志 0。
- 导出接口补验：本地登录后请求 `/api/v1/engine/packages/73c5b883-235c-4d86-a1ae-119206d3d9b4/offline/export`，返回 `format=MEDKERNEL_PACKAGE_OFFLINE_V1`、`manifest.payloadSha256` 非空、`manifest.assetSnapshotCount=0`、`payload.assetSnapshots.length=0`、`manifest.itemCount=0`。浏览器下载事件受 Codex 内置浏览器限制，改用本地接口直接核对离线 JSON 内容，不影响页面入口与后端契约验证。

## 未完成与下一步

- 本 PR 只覆盖规则与评估指标的完整资产内容快照；路径模板、术语、知识资产、随访资产需要后续按各自权威实体补齐内容契约，不得在没有真实内容快照时宣称完整离线迁移。
- BASE-09 仍需继续清域级验收残留和最终净化报告；不得恢复引用式假迁移、旧 `totalCount` 字段、假同步证据、默认版本假填充或临时兼容层。
