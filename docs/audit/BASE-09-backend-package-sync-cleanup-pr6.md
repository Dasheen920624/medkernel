# BASE-09 后端包同步真实性净化 PR6 记录

## 范围

- `LenientPackageSyncAdapter` 不再执行“模拟离线同步”并返回 `LNT-*` 时间戳摘要；默认适配器改为无真实物理通道时抛出 `PackageSyncNotConnectedException`。
- `PackageEngineService.syncPackage` 区分普通失败与未接入真实同步通道：未接入时写入 `SyncLogStatus.NOT_SYNCED`、`errorCode=NOT_SYNCED`、`syncEvidence=null`，发布计划落 `ReleasePlanStatus.NOT_SYNCED`，且不推进知识包状态。
- 五方言新增 V33，扩展 `release_plan.status` 与 `sync_log.status` 约束并补中文列注释。
- 真实性门禁新增 `backend.fake-sync-evidence`，阻断后端用模拟同步、时间戳摘要或 `LNT-*` 伪造同步证据，并补同步证据字段误报回归测试。

## 红绿验证

- 红灯：新增 `LenientPackageSyncAdapterTest` 与 `PackageEngineServiceTest.syncPackageMarksNotSyncedWhenDefaultPortHasNoRealChannel` 后，编译因缺少 `PackageSyncNotConnectedException`、`ReleasePlanStatus.NOT_SYNCED`、`SyncLogStatus.NOT_SYNCED` 失败。
- 绿灯：实现异常、状态机、服务落库语义和 V33 迁移后，目标测试通过。

## 本地验证

- `mvn -B -q -Dtest=LenientPackageSyncAdapterTest,PackageEngineServiceTest,MigrationBaselineContractTest,H2BaselineMigrationTest test`
- `node --test scripts/authenticity-guard.test.mjs`
- `node scripts/authenticity-guard.mjs --mode=inventory`
- `node scripts/migration-convention-guard.mjs --mode=files medkernel-backend/src/main/resources/db/migration/h2/V33__package_sync_not_synced_status.sql medkernel-backend/src/main/resources/db/migration/postgres/V33__package_sync_not_synced_status.sql medkernel-backend/src/main/resources/db/migration/kingbase/V33__package_sync_not_synced_status.sql medkernel-backend/src/main/resources/db/migration/oracle/V33__package_sync_not_synced_status.sql medkernel-backend/src/main/resources/db/migration/dm/V33__package_sync_not_synced_status.sql`
- `node --test scripts/config-boundary-guard.test.mjs scripts/migration-convention-guard.test.mjs scripts/authenticity-guard.test.mjs`
- `mvn -B -q test`（含 Docker Testcontainers 下 PostgreSQL / Oracle 迁移烟测）
- `git diff --check`
- `rg -n "模拟离线同步|LNT-|时间戳.*证据|syncEvidence[\\s\\S]{0,200}(Instant\\.now|System\\.currentTimeMillis)" medkernel-backend/src/main/java` 无命中。

## 剩余边界

- 本批只清理“无真实通道时伪造同步证据”的 Critical 问题，不宣称包发布域全部完成。
- 独立审计仍指出包发布存在“看影响科室真实计算”“回滚同步留痕/反向投影”“部分失败状态机更细分”等问题，需要后续业务卡或 BASE-09 后续批次继续收口。
- 后续 AI 必须保持纯净代码原则：同步证据只能来自真实目标回执或真实被投影内容指纹；没有通道时只能返回 `NOT_SYNCED`，禁止用本地时间、随机数、UUID、固定前缀或模拟注释伪造成功。
