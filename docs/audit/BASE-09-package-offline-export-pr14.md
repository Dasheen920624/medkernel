# BASE-09 配置包离线导出与完整性清单 PR14 记录

> 日期：2026-06-01  
> 分支：`codex/base-09-package-offline-export`  
> 范围：BASE-09 第十四批净化，不宣称 BASE-09 完成。

## 目标

补齐配置包离线安装能力的第一段：提供真实后端离线包导出端点、基于 payload 字节的 SHA-256 完整性摘要、前端下载入口，并清理触碰页面里残留的医学示例和默认版本假填充。

## 改动

- `PackageEngineService.exportOfflinePackage` 新增离线包 JSON 导出：格式为 `MEDKERNEL_PACKAGE_OFFLINE_V1`，包含 `manifest` 与 `payload`，`payloadSha256` 基于导出 payload 的真实 UTF-8 字节计算。
- `PackageEngineController` 新增 `GET /api/v1/engine/packages/{packageId}/offline/export`，沿用 `package.read` 权限与租户数据范围，返回 JSON 附件。
- `frontend/src/shared/api/hooks.ts` 新增 `downloadPackageOfflineExport`，前端配置包中心每行新增“导出离线包”操作。
- `ConfigPackages` 触碰范围内删除旧示例：不再预填 `STROKE_DECISION`、固定 `v1.0.0` / `v1.0`，表单占位改为中性输入提示。
- 配置包中心改用 Ant Design `App.useApp()` 消息上下文，应用根节点补 `AntdApp`，清除动态主题下静态消息运行时告警。
- 新增前后端红绿测试覆盖离线导出摘要、控制器附件下载、前端真实下载动作。

## TDD 证据

- 红灯：`mvn -B -q -Dtest=PackageEngineServiceTest,PackageEngineControllerSecurityTest test` 先因 `exportOfflinePackage` 不存在失败。
- 绿灯：同命令通过；新增服务层测试会重新计算 payload 的 SHA-256 并断言与 manifest 一致。
- 红灯：`npm test -- src/shared/api/hooks.test.ts` 先因 `downloadPackageOfflineExport is not a function` 失败。
- 绿灯：`npm test -- src/shared/api/hooks.test.ts src/pages/tenant/ConfigPackages.test.tsx` 通过，前端行操作调用真实离线导出 helper。
- 浏览器复现：首次真实页面验收在创建配置包草案后触发 Ant Design 静态消息告警；接入 `App.useApp()` 后复验，控制台错误清零。

## 已验证

- `mvn -B -q -Dtest=PackageEngineServiceTest,PackageEngineControllerSecurityTest test` 通过。
- `mvn -B -q test` 通过。
- `npm test -- src/shared/api/hooks.test.ts src/pages/tenant/ConfigPackages.test.tsx` 通过。
- `npm test -- src/pages/tenant/ConfigPackages.test.tsx src/pages/pages.smoke.test.tsx` 通过：2 files / 22 tests。
- `npm test` 通过：34 files / 124 tests。
- `npm run typecheck` 通过。
- `npm run build` 通过；保留既有 `vendor-antd` chunk 大小 warning。
- `npm run lint` 通过，仍有 11 个既有 warning，未新增 touched file error。
- `npm run format:check` 通过。
- `git diff --check` 通过。
- `node scripts/authenticity-guard.mjs --mode=inventory` 通过：扫描 575 个文件，0 阻断。
- `node --test scripts/config-boundary-guard.test.mjs scripts/migration-convention-guard.test.mjs scripts/authenticity-guard.test.mjs` 通过：24/24。
- 浏览器验收：本分支前端 `http://127.0.0.1:5176` 登录 `hospital-admin`，进入 `/config/packages`，创建草案后可见“导出离线包”入口；占位符无旧医学示例 / 默认版本假填充；`consoleErrors=[]`、`pageErrors=[]`，截图 `/tmp/medkernel-pr14-config-packages.png`。
- 触碰生产路径残留 grep：`STROKE|缺血|神经|危急|Class I|例如|v1.0.0|v1.0|todoMock|演示与校验|客户验收剧本|DemoModeToggle|模拟页面主体` 在本批触碰生产文件中无命中。

## 未完成与下一步

- 本 PR 只完成离线包导出与完整性清单，不实现离线包导入 / 验签 / 安装落库。
- 下一批继续 BASE-09：离线包导入验签、重复版本冲突处理、导入审计与前端导入入口。
