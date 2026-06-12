# P2 全系统产品信息架构门禁精简上下文快照

> 日期：2026-06-12  
> 用途：当前长目标会话的上下文压缩入口。不要回读旧会话流水；除非核查历史证据，只读本快照、`docs/_HANDOFF.md` 和本阶段验收报告；不得据此创建或引导切换新线程。

## 当前状态

- 分支：`codex/global-product-ia-refactor`
- 阶段：P2 全系统产品门禁已通过；“平台知识文献资料库根地址”配置中心追加补丁已完成本地验证，待提交推送。
- 下一阶段：P3 演练前发布准备。进入 134 前必须先备份、留痕、确认恢复路径；不得跳过。

## 已完成

- 全量功能目录和唯一产品裁决：`docs/audit/product-function-catalog.md`
- 五大主域 IA 裁决：`docs/audit/product-ia-matrix.md`
- 14 角色菜单快照与任务旅程：`docs/audit/product-role-journeys.md`
- 前后端菜单、权限、路由、页面命名和角色工作台同源重构。
- 全中文、专家模式隐藏、客户主任务入口和桌面/移动端打开性验收。
- 新增正式文献资料存储入口：配置中心键 `medkernel.knowledge.literature.material-root-uri`，客户可见名“平台知识文献资料库根地址”，当前默认资料库根 `cos://medkernel-platform-knowledge/medkernel/platform-knowledge/t-1/literature-materials/`；兼容 COS/S3/OSS/OBS/MinIO/HTTPS 网关等受管 URI，不在路径中硬编码当前服务器 IP 或存储厂商；系统配置页可查看维护，`tmp`、`file://`、非加密 HTTP 被后端校验拒绝。

## 关键证据

- 后端 `mvn -q test`：2214 tests，0 failures，0 errors，0 skipped；退出码 0，末尾有测试 JVM 关闭期调度线程连接噪声。
- 前端 `npm run verify`：89 files / 649 tests 全通过。
- 前端 `npm run build`：生产构建通过，3408 modules transformed。
- E2E：`npx playwright test --project=chromium`，31 passed。
- T-GATE：40 项门禁自测通过；真实性、配置边界、迁移规约 all-mode 均 0 阻断；中文注释 0 fail / 0 warn；`git diff --check` 通过。
- Browser 冒烟：`http://127.0.0.1:5173/login` 可打开，显示中文登录、平台治理切换、所在机构和统一身份入口。
- 追加聚焦：`mvn -q -Dtest=SystemConfigControllerTest#knowledgeLiteratureMaterialRootUriUsesManagedStorageSeedAndRejectsTmp test` 通过；`npm test -- src/pages/compliance/SecurityBaseline.test.tsx` 7 tests 通过。
- Docker 容器烟测：本机 Docker Desktop 已恢复；旧 `medkernel-dev-*`、`medkernel-dify-*` 和过期 Oracle 容器已清理；`mvn -q -Dtest=FlywayMultiDialectSmokeTest test` 通过，真实 PostgreSQL、Oracle、H2 均迁移到 V116。

## 风险与未冒领

- `DEFER-023` 已解除：本机 Testcontainers PostgreSQL/Oracle/H2 迁移烟测已通过。P3/P4 必须在 134 目标环境提交真实备份、恢复和从零迁移证据。
- 当前没有对 `193.112.107.134` 做外向操作。
- wave2 AI、知识生成、15 领域门面和 GA 验收未开始。
- `193.112.107.134` 被指定为当前后续主平台知识管理服务器；所有平台知识生成必须在该服务器完成，并且只能在全功能完美验收、结构冻结、清库双演练和第一阶段正式验收通过后开始。正式文献资料库根地址不得写入 yml，不得指向 `tmp` 或服务器本地临时目录，主机迁移或更换存储时通过系统配置页维护。

## 下一步

1. 提交并推送追加补丁，更新现有 P2 PR。
2. 在当前长目标会话内压缩整理上下文后进入 P3，不创建或切换新线程。
3. P3 首个动作：核验 134 连接、部署目录、数据库和当前版本；生成备份计划、恢复命令和操作留痕清单。
4. P3 未通过前不得清库、发布或启动演练。
5. 进入 P6 前确认 134 的平台知识结构已经冻结，并确认“平台知识文献资料库根地址”指向正式受管资料库根；不允许边生成知识边改主结构。
