# BASE-09 后端包回滚目标状态净化 PR10 记录

## 范围

- `PackageEngineService.rollbackPackage` 不再允许把 `PUBLISHED` 包作为回滚目标，只允许回滚到曾经执行并已下线的 `OFFLINE` 历史版本。
- 新增回归测试覆盖 `PUBLISHED` 目标，确认校验失败时不保存任何包状态，避免“预发布但从未激活”的版本绕过正式发布流程被直接激活。
- `ConfigPackages` 回滚弹窗只展示同一配置包编码下的 `OFFLINE` 历史版本，并清理“PUBLISHED 可快速回退备用”的误导文案。

## 红绿验证

- 绿色基线：改动前 `rollbackPackageSwitchesActiveStatusAndRecordsAudit` 通过，确认现有离线历史版本回滚路径可用。
- 红灯：新增 `rollbackPackageRejectsPublishedTargetAndKeepsStatus` 后，旧实现未抛异常，证明 `PUBLISHED` 目标仍会被接受。
- 绿灯：服务层目标状态校验改为仅允许 `OFFLINE` 后，新增拒绝用例与原成功回滚用例均通过。

## 已执行验证

- `mvn -B -q -Dtest=PackageEngineServiceTest#rollbackPackageSwitchesActiveStatusAndRecordsAudit test`
- `mvn -B -q -Dtest=PackageEngineServiceTest#rollbackPackageRejectsPublishedTargetAndKeepsStatus test`（红灯，旧代码未抛异常）
- `mvn -B -q -Dtest=PackageEngineServiceTest#rollbackPackageRejectsPublishedTargetAndKeepsStatus,PackageEngineServiceTest#rollbackPackageSwitchesActiveStatusAndRecordsAudit test`
- `mvn -B -q -Dtest=PackageEngineServiceTest test`
- `npm ci`（新 worktree 安装前端依赖；未提交依赖目录）
- `npm run typecheck`
- `npm run verify`（32 个前端测试文件 / 122 个测试通过；保留既有 lint warning 与 React Router / act 测试警告，未新增阻断）
- `npm run build`（Vite 构建成功；保留既有 vendor chunk 体积提示）
- `node scripts/authenticity-guard.mjs --mode=inventory`
- `node --test scripts/config-boundary-guard.test.mjs scripts/migration-convention-guard.test.mjs scripts/authenticity-guard.test.mjs`
- `mvn -B -q test`（含 Docker Testcontainers 下 PostgreSQL / Oracle 迁移烟测）
- `git diff --check`
- `rg -n 'PUBLISHED \|\| OFFLINE|PUBLISHED 或 OFFLINE|p\.status !== "DRAFT"|快速回退备用|可供灰度或快速回退' medkernel-backend/src frontend/src -S`（无生产路径残留）

## 剩余边界

- 本批只修复回滚目标状态绕过问题，不宣称包发布域全部完成。
- 回滚反向投影、回滚 `ReleasePlan` / `SyncLog` 证据链、影响范围导出、导入导出 / 离线安装能力仍需后续 PR 继续收口。
