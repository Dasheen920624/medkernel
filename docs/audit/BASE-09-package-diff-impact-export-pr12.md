# BASE-09 配置包差异影响证据导出 PR12 记录

## 范围

- `PackageDiffResponse` 增加真实资产变更明细 `changes`，逐条标识 `ADDED` / `UPDATED` / `REMOVED`，包含资产类型、资产 ID、基准版本和目标版本。
- 差异影响科室计算补齐删除资产路径：被目标包移除的规则 / 路径 / 指标仍会按真实资产归属纳入 `affectedDepartments`，不再只看目标包现存资产。
- 新增 `GET /api/v1/engine/packages/{packageId}/diff/export`，按当前差异计算结果导出 NDJSON 证据，逐行包含摘要、受影响科室和资产变更，并写入 `AuditAction.EXPORT` 审计。
- 配置包中心页的差异弹窗增加“导出影响证据”入口，下载后端生成的证据文件，前端不拼装假证据。

## 红绿验证

- 绿色基线：改动前 `calculateDiffComputesCorrectStats` 通过，确认既有差异统计可执行。
- 红灯 1：新增 `calculateDiffIncludesRemovedAssetImpactAndChangedRows` 后，旧响应没有 `changes`，且删除资产责任科室无法被纳入影响范围。
- 绿灯 1：新增 `PackageDiffChange` / `PackageDiffChangeType`，补齐新增、升级、删除三类变更行，并把删除资产真实责任科室纳入影响范围。
- 红灯 2：新增 `exportDiffEvidenceReturnsNdjsonFromRealDiffAndPublishesAudit` 后，旧服务没有差异证据导出方法。
- 绿灯 2：服务导出 NDJSON 证据并发布 `EXPORT` 审计。
- 红灯 3：新增 `authorizedUserCanDownloadDiffEvidenceNdjson` 后，旧控制器没有下载路由，真实请求无法取得证据文件。
- 绿灯 3：新增下载端点，带 `tenant_id` 的授权 JWT 可下载 NDJSON；缺租户上下文仍由数据范围门禁拒绝。

## 已执行验证

- `mvn -B -q -Dtest=PackageEngineServiceTest#calculateDiffComputesCorrectStats test`
- `mvn -B -q -Dtest=PackageEngineServiceTest#calculateDiffIncludesRemovedAssetImpactAndChangedRows,PackageEngineServiceTest#exportDiffEvidenceReturnsNdjsonFromRealDiffAndPublishesAudit test`（红灯：缺 `changes` 与导出方法）
- `mvn -B -q -Dtest=PackageEngineControllerSecurityTest#authorizedUserCanDownloadDiffEvidenceNdjson test`（红灯：下载路由不存在）
- `mvn -B -q -Dtest=PackageEngineServiceTest#calculateDiffIncludesRemovedAssetImpactAndChangedRows,PackageEngineServiceTest#exportDiffEvidenceReturnsNdjsonFromRealDiffAndPublishesAudit test`
- `mvn -B -q -Dtest=PackageEngineControllerSecurityTest#authorizedUserCanDownloadDiffEvidenceNdjson test`
- `mvn -B -q -Dtest=PackageEngineServiceTest test`
- `mvn -B -q -Dtest=PackageEngineControllerSecurityTest test`
- `npm run typecheck`
- `npm run format:check`
- `npm run build`（通过；Vite 仅提示既有大 chunk 体积提醒）
- `npm test`（32 个测试文件 / 122 个测试通过）
- `mvn -B -q test`（`MAVEN_EXIT=0`；H2 / PostgreSQL / Oracle 迁移均验证至 v33）
- `node scripts/authenticity-guard.mjs --mode=inventory`（扫描 572 个文件，未发现阻断项）
- `node --test scripts/config-boundary-guard.test.mjs scripts/migration-convention-guard.test.mjs scripts/authenticity-guard.test.mjs`（23/23 通过）
- `git diff --check`

## 浏览器核查

- 本地前端工作树启动于 `http://127.0.0.1:5174/`，浏览器打开 `/config/packages` 可正常渲染配置包中心页面。
- 当前本地页面没有配置包数据，差异弹窗与“导出影响证据”按钮无法通过真实页面点击打开；本 PR 不伪造可见交互证据，入口由前端类型 / 构建 / 页面 smoke 和后端下载端点测试共同覆盖。

## 剩余边界

- 本批只收口“差异影响证据导出”和删除资产影响范围，不宣称 BASE-09 / PKG-01 / CFGPKG-01 完成。
- 离线包导入 / 导出、包完整性校验、剩余硬编码业务示例与域级验收仍需后续 PR 继续推进。
