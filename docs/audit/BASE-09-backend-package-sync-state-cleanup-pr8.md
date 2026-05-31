# BASE-09 后端包同步状态机净化 PR8 记录

## 范围

- `PackageEngineService.syncPackage` 不再在同步未全部成功时推进知识包生命周期。
- 全部目标未接入真实同步通道时，发布计划保持 `NOT_SYNCED`，草稿包仍保持草稿状态。
- 任一目标失败或部分成功 / 部分失败时，发布计划落 `FAILED`，不再停留在容易被误解为仍在运行的 `EXECUTING`。
- 灰度发布只有在所有目标同步成功后，才允许把草稿包推进为 `PUBLISHED`；全量发布仍保持全成功才激活 `ACTIVE`。
- 审计文案不再把非全量路径统称为灰度，改为输出真实发布策略与最终状态。

## 红绿验证

- 红灯 1：新增 `PackageEngineServiceTest.syncPackageDoesNotPublishDraftWhenAllTargetsAreNotSynced` 后，旧代码在 `NOT_SYNCED` 情况下仍保存草稿包为 `PUBLISHED`。
- 红灯 2：新增 `PackageEngineServiceTest.syncPackageFailsPlanAndDoesNotPublishDraftWhenAnyTargetFails` 后，旧代码把部分失败计划保持为 `EXECUTING`，且仍存在推进草稿包的风险。
- 绿灯：收紧最终状态与包生命周期推进条件后，上述测试通过。

## 已执行验证

- `mvn -B -q -Dtest=PackageEngineServiceTest test`
- `node scripts/authenticity-guard.mjs --mode=inventory`
- `node --test scripts/config-boundary-guard.test.mjs scripts/migration-convention-guard.test.mjs scripts/authenticity-guard.test.mjs`
- `mvn -B -q test`（含 Docker Testcontainers 下 PostgreSQL / Oracle 迁移烟测）
- `git diff --check`

## 剩余边界

- 本批只收紧同步结果与包生命周期状态，不宣称包发布域全部完成。
- 回滚二次确认、回滚反向投影、回滚 plan/log 证据链、影响范围导出、导入导出 / 离线安装能力仍需后续 PR 继续收口。
