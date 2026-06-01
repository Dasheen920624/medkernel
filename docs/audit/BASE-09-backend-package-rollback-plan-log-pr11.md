# BASE-09 后端包回滚计划与日志证据链净化 PR11 记录

## 范围

- `PackageEngineService.rollbackPackage` 不再直接切换当前包和历史包状态；回滚前必须复用当前在用包最近一次成功发布 / 回滚的真实同步目标，创建新的 `ReleasePlan`，再逐目标执行反向投影。
- 回滚反向投影为每个原同步目标写入 `SyncLog`：先落 `RUNNING`，成功后落 `SUCCESS` 并保存真实 `syncEvidence`；未接入真实适配器落 `NOT_SYNCED`；目标缺失或同步异常落 `FAILED`。
- 回滚反向投影的成功结果必须返回非空同步证据；空白 `syncEvidence` 不允许被当成成功，计划落 `FAILED`，包状态保持不变。
- 只有所有反向投影成功时，才把当前在用包置为 `OFFLINE`、目标历史包置为 `ACTIVE`，并把回滚计划置为 `ROLLBACKED`；任一目标失败或未接入时，计划落 `FAILED` / `NOT_SYNCED`，包状态保持不变。
- 同步与回滚中的 `PackageSyncNotConnectedException` 属于诚实降级，日志降为告警，不再以错误栈污染运行日志；真实写入失败仍保留错误日志。

## 红绿验证

- 绿色基线：改动前 `rollbackPackageSwitchesActiveStatusAndRecordsAudit` 通过，确认既有离线历史版本回滚路径可执行。
- 红灯 1：新增 `rollbackPackageCreatesRollbackPlanAndSyncLogsBeforeSwitchingStatus` 后，旧实现没有保存任何 `ReleasePlan`，证明回滚缺少计划与日志证据链。
- 绿灯 1：新增回滚计划、同步日志和反向投影逻辑后，成功回滚会先写计划 / 日志，再切换包状态。
- 红灯 2：新增 `rollbackPackageMarksPlanFailedWhenOriginalSyncTargetMissing` 后，缺失同步目标时旧实现直接抛 `ENG_PACKAGE_001`，没有落失败计划和失败日志。
- 绿灯 2：缺失同步目标现在写入 `FAILED` 同步日志与失败计划，再统一返回 `ENG_PACKAGE_005`，包状态不变。
- 红灯 3：新增 `rollbackPackageMarksPlanFailedWhenReverseProjectionReturnsBlankEvidence` 后，旧实现把空白同步证据当成成功并切换包状态。
- 绿灯 3：空白同步证据现在写入 `FAILED` 同步日志与失败计划，再统一返回 `ENG_PACKAGE_005`，包状态不变。

## 已执行验证

- `mvn -B -q -Dtest=PackageEngineServiceTest#rollbackPackageSwitchesActiveStatusAndRecordsAudit test`
- `mvn -B -q -Dtest=PackageEngineServiceTest#rollbackPackageCreatesRollbackPlanAndSyncLogsBeforeSwitchingStatus test`（红灯，旧实现没有保存回滚计划）
- `mvn -B -q -Dtest=PackageEngineServiceTest#rollbackPackageMarksPlanFailedWhenOriginalSyncTargetMissing test`（红灯，旧实现直接抛出目标不存在，未落失败证据）
- `mvn -B -q -Dtest=PackageEngineServiceTest#rollbackPackageMarksPlanFailedWhenReverseProjectionReturnsBlankEvidence test`（红灯，旧实现未抛异常）
- `mvn -B -q -Dtest=PackageEngineServiceTest#rollbackPackageMarksPlanFailedWhenReverseProjectionReturnsBlankEvidence test`
- `mvn -B -q -Dtest=PackageEngineServiceTest#rollbackPackageMarksPlanFailedWhenOriginalSyncTargetMissing test`
- `mvn -B -q -Dtest=PackageEngineServiceTest#rollbackPackageCreatesRollbackPlanAndSyncLogsBeforeSwitchingStatus,PackageEngineServiceTest#rollbackPackageKeepsStatusAndMarksPlanNotSyncedWhenReverseProjectionNotConnected,PackageEngineServiceTest#rollbackPackageMarksPlanFailedWhenOriginalSyncTargetMissing,PackageEngineServiceTest#rollbackPackageMarksPlanFailedWhenReverseProjectionReturnsBlankEvidence,PackageEngineServiceTest#rollbackPackageSwitchesActiveStatusAndRecordsAudit,PackageEngineServiceTest#rollbackPackageRejectsPublishedTargetAndKeepsStatus test`
- `mvn -B -q -Dtest=PackageEngineServiceTest test`
- `mvn -B -q test`（含 Docker Testcontainers 下 PostgreSQL / Oracle 迁移烟测；Oracle amd64 镜像在本机 arm64 Docker 下有仿真变慢告警，验证最终通过）
- `node scripts/authenticity-guard.mjs --mode=inventory`
- `node --test scripts/config-boundary-guard.test.mjs scripts/migration-convention-guard.test.mjs scripts/authenticity-guard.test.mjs`
- `git diff --check`

## 剩余边界

- 本批只修复回滚反向投影、`ReleasePlan` / `SyncLog` 证据链和失败不切状态，不宣称 BASE-09 / 包发布域全部完成。
- 影响范围导出、剩余硬编码业务示例、导入导出 / 离线安装能力和域级验收仍需后续 PR 继续收口。
