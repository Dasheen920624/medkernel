# 会话接力

## 唯一执行组织

- 当前分支：`codex/authoring-asset-library`
- 基线：`origin/main` = `9233049a`（P12-5 条件片段库已合入）
- 执行原则：线1统一承接全部任务；线2 / 线3 / 线4不再独立开发、抢合并或维护重复实现。
- 项目未上线：只保留唯一模型、唯一接口、唯一主流程，不做旧方案兼容层。

## 当前状态

- OpenSpec `pathway-rule-authoring-overhaul` 完成 P12-6：统一资产库 + 标签 + 收藏 + 条件片段克隆/另存为 + 知识包纳入条件片段资产。
- 新增唯一表 `mk_engine_authoring_asset_profile`、`mk_engine_authoring_asset_favorite`，覆盖 H2/PostgreSQL/Kingbase/Oracle/DM 五方言迁移；`VersionedAssetType` 扩展 `CONDITION_FRAGMENT`、`VALUE_SET`、`ORDER_SET`、`ACTION_CARD`、`SUBPATHWAY`。
- 后端新增 `/api/v1/engine/authoring/assets`，统一检索规则、路径、条件片段，支持分类/标签、个人收藏、条件片段克隆为独立 DRAFT；服务契约与领域 owner 已登记。
- 知识包支持 `CONDITION_FRAGMENT` 入包校验、离线导出、离线导入；路径内 `ORDER_SET` / `SUBPATHWAY` 继续作为路径节点配置随路径快照进入知识包，不伪造不存在的独立实体。
- 前端新增「统一资产库」页面 `/authoring/assets`，支持检索、标签维护、收藏/取消收藏、条件片段克隆；配置包中心追加资产时规则/路径/条件片段统一走资产库，不再各自拉规则/路径来源。
- 只同步本文件与 OpenSpec 任务清单，不新增施工文档。

## 当前证据

- 红灯：`AuthoringAssetLibraryServiceTest#exposesCloneEntryOnlyForImplementedDraftCloneAssets` 曾失败，暴露规则/路径被误标为可克隆；已收敛为仅条件片段显示克隆入口。
- 红灯：`ServiceContractGovernanceTest,DomainOwnershipContractTest` 曾失败，暴露新增控制器和新增表未登记；已补齐。
- 红灯：前端统一资产库 hook/page/route 测试曾失败于缺少 hook、缺少页面、缺少路由元数据；已补齐。
- 红灯：前端全量 `npm run verify` 曾失败于新增「统一资产库」二级菜单破坏宪法 27 项 IA 锁；已改为隐藏路由 + 配置包主流程次级入口，`menu.test.ts` 回绿。
- 红灯：配置包测试曾失败于 `Link` 依赖 Router 上下文；已改为普通 `Button href` 次级入口，`ConfigPackages.test.tsx` 回绿。
- 红灯：PR #490 CI 曾失败于 V101 PostgreSQL/Kingbase 使用 `CLOB`、Oracle 重复索引、H2 多方言版本仍为 100；已改为 PostgreSQL/Kingbase `TEXT`、删除重复索引、版本基线升至 101。
- 后端聚焦已通过：`mvn -Dtest=AuthoringAssetLibraryServiceTest,AuthoringAssetLibraryControllerTest,PackageEngineServiceTest,ServiceContractGovernanceTest,DomainOwnershipContractTest test`。
- 后端迁移聚焦已通过：`mvn -Dtest=H2BaselineMigrationTest,MigrationBaselineContractTest test`。
- 前端聚焦已通过：`npm test -- --run src/shared/api/hooks.test.ts src/pages/tenant/ConfigPackages.test.tsx`、`npm test -- --run src/pages/tenant/AuthoringAssets.test.tsx`、`npm test -- --run src/shared/config/routes.test.ts -t "unified authoring assets"`、`npm test -- --run src/pages/pages.smoke.test.tsx -t "unified authoring asset"`。
- 前端全量已通过：`npm run verify`（80 files / 570 tests / lint / stylelint / lint-rules / format / typecheck 全绿）。
- 前端构建已通过：`npm run build`（Vite 3401 modules transformed）。
- 后端全量已通过：`mvn test`（1954 tests / 0 failures / 0 errors / 3 skipped；3 skipped 为本机无 Docker 的 Testcontainers 多方言 smoke）。
- T-GATE 已通过：`node --test scripts/authenticity-guard.test.mjs scripts/config-boundary-guard.test.mjs scripts/migration-convention-guard.test.mjs`（38 tests）；`node scripts/authenticity-guard.mjs --mode=all`（1408 files）；`node scripts/config-boundary-guard.mjs --mode=all`（1325 files）；`node scripts/migration-convention-guard.mjs --mode=changed --base=origin/main`（5 files）；`bash scripts/check-comment-zh.sh`；`git diff --check`。
- OpenSpec 已通过：`openspec validate pathway-rule-authoring-overhaul --strict`。
- 浏览器冒烟已通过：前端 `5173` 代理后端 `18080`，登录页可渲染；未登录访问业务页会回登录；本地 dev JWT 进入 `/authoring/assets` 后可见「统一资产库」业务内容、资产类型筛选、关键词输入、空态表格；进入 `/config/packages` 后可见配置包中心空态；左侧菜单没有新增「统一资产库」二级项；控制台 error = 0。临时服务已停。

## 下一步

1. amend 并重推 PR #490，等待 CI 绿后 squash 合入 `main`。
2. 合入 `main` 后继续 P12-7 批量导入/导出，不恢复并行线。
