# BASE-09 后端包回滚二次确认净化 PR9 记录

## 范围

- `PackageEngineController.rollbackPackage` 从 query 参数改为 `PackageRollbackRequest` 请求体，要求目标包、当前版本确认、目标版本确认、回滚原因和高危确认。
- `PackageEngineService.rollbackPackage` 在服务层强制校验高危确认、原因、当前包 `ACTIVE`、同一 `packageCode`、当前版本与目标版本一致；校验失败不保存任何包状态。
- `ConfigPackages` 回滚弹窗同步采集审计原因和高危确认，并只展示同一配置包编码下的可回退版本；前端不再把弹窗当作唯一安全门禁。
- 回滚审计文案补充原因和操作人，避免只记录“版本从 A 到 B”的薄证据。

## 红绿验证

- 红灯：新增 `PackageEngineServiceTest.rollbackPackageRejectsMissingHighRiskConfirmationAndKeepsStatus`、`rollbackPackageRejectsVersionMismatchAndKeepsStatus`、`rollbackPackageRejectsTargetFromDifferentPackageCode` 后，旧代码因缺少 `PackageRollbackRequest` 和新服务入口编译失败，证明旧接口无法承载后端二次确认契约。
- 绿灯：新增 DTO 与服务层校验后，上述测试及原成功回滚用例通过。

## 已执行验证

- `mvn -B -q -Dtest=PackageEngineServiceTest#rollbackPackageSwitchesActiveStatusAndRecordsAudit test`（改动前绿色基线）
- `mvn -B -q -Dtest=PackageEngineServiceTest#rollbackPackageRejectsMissingHighRiskConfirmationAndKeepsStatus,PackageEngineServiceTest#rollbackPackageRejectsVersionMismatchAndKeepsStatus,PackageEngineServiceTest#rollbackPackageRejectsTargetFromDifferentPackageCode,PackageEngineServiceTest#rollbackPackageSwitchesActiveStatusAndRecordsAudit test`
- `mvn -B -q -Dtest=PackageEngineServiceTest#rollbackPackageRejectsMissingHighRiskConfirmationAndKeepsStatus,PackageEngineServiceTest#rollbackPackageRejectsVersionMismatchAndKeepsStatus,PackageEngineServiceTest#rollbackPackageRejectsTargetFromDifferentPackageCode,PackageEngineServiceTest#rollbackPackageSwitchesActiveStatusAndRecordsAudit,PackageEngineControllerSecurityTest#doctorCannotPublishOrRollbackPackage test`
- `mvn -B -q -Dtest=PackageEngineServiceTest test`
- `npm run typecheck`
- `npm run verify`（32 个前端测试文件 / 122 个测试通过；保留既有 lint warning 与 React Router / act 测试警告，未新增阻断）
- `npm run build`（Vite 构建成功；保留既有 vendor chunk 体积提示）
- `node scripts/authenticity-guard.mjs --mode=inventory`
- `node --test scripts/config-boundary-guard.test.mjs scripts/migration-convention-guard.test.mjs scripts/authenticity-guard.test.mjs`
- `mvn -B -q test`（含 Docker Testcontainers 下 PostgreSQL / Oracle 迁移烟测）
- `git diff --check`
- `rg -n "/rollback\\?targetPackageId|rollbackPackage\\([^,]+,\\s*\\\"|targetPackageId: targetPkgId|RequestParam String targetPackageId" medkernel-backend/src frontend/src docs -S`（无旧 query 回滚调用残留）

## 剩余边界

- 本批只把回滚二次确认和跨包误回滚安全边界落到后端，不宣称包发布域全部完成。
- 回滚反向投影、回滚 plan/log 证据链、影响范围导出、导入导出 / 离线安装能力仍需后续 PR 继续收口。
