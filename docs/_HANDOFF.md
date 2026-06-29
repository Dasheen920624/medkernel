# 会话接力

> 开工先读本文件。这里只保留当前可执行事实；产品范围见
> [PRODUCT_SCOPE](PRODUCT_SCOPE.md)，历史过程使用 Git 追溯，不在当前工作树保留第二套事实源。

## 当前主线

- 当前分支：`codex/golive-platform-unification`。
- 最新权威基线：`origin/main` / `main` / 当前分支基线均为
  `9d19fb317f8c51f8de6db70c59a01acab6ade02c`（`更新远端主线合并接力`）。
- 当前用户约束：不使用子代理；只做本地提交；不推送远程；不合并 `main`。
- 当前本地工作：本轮只改 E2E 验收脚本和本接力文档；`.codex/config.toml` 为未跟踪本地配置，不提交。

## 本轮落地结论

- 已按最新 `origin/main` 生成候选制品并在 134 清库部署：
  - 主机：`193.112.107.134`，hostname：`VM-0-13-opencloudos`。
  - manifest：`/zoesoft/medkernel/manifest.properties`。
  - `source=9d19fb317f8c51f8de6db70c59a01acab6ade02c`。
  - `commit=9d19fb317f8c51f8de6db70c59a01acab6ade02c`。
  - `deployedAt=2026-06-29T10:45:51+08:00`。
  - `jarSha256=d517db8340aca9dcaf4c3e1abdeae33fe407077387883c1e99357f84e784f8b9`。
- 清库部署后数据库验证：
  - 业务表数：`207`。
  - Flyway 版本：`1`。
  - public base tables：`208`（207 业务表 + `flyway_schema_history`）。
  - 服务 active/enabled，readiness HTTP 200。
- TLS：本轮使用 `/zoesoft/medkernel/nginx/ssl/server.crt` 作为可信 SAN 证书校验根；
  远端命令需带 `MEDKERNEL_TLS_CA_FILE=/zoesoft/medkernel/nginx/ssl/server.crt`。
  Playwright/Node 只追加 `NODE_EXTRA_CA_CERTS=/zoesoft/medkernel/nginx/ssl/server.crt`，不要用
  `SSL_CERT_FILE` 覆盖系统 CA。
- 模型提供方：`ollama-launch`，类型 `OLLAMA`，端点 `http://127.0.0.1:11434`，
  模型版本 `medkernel-qwen25:1.5b-v1`。

## 134 证据

- 统一证据目录：`/zoesoft/medkernel/var/evidence/current-launch`。
- 八段总证据：`/zoesoft/medkernel/var/evidence/current-launch/full-system.json`：
  `status=PASSED`，`stages=8`，`source=9d19fb317f8c51f8de6db70c59a01acab6ade02c`。
- 全知识证据：`/zoesoft/medkernel/var/evidence/current-launch/full-knowledge.json`：
  11 个知识域全部发布，V1/V2 回滚与恢复验证通过。
- 运行韧性证据：`/zoesoft/medkernel/var/evidence/current-launch/runtime-resilience.json`：
  模型关闭诚实降级通过，B0 主链路 `17/17`，恢复启用后 Provider/Readiness/模型调用恢复。
- 浏览器 E2E 证据：
  `/zoesoft/medkernel/var/evidence/current-launch/e2e/report/results.json`，
  Playwright `50 passed (14.1m)`，两套浏览器项目均通过。
- 真实前台链路：完整 E2E 覆盖并通过页面提交产生系统接入适配器、知识值集、模型外调安全策略、MPI 患者、
  随访模板；对应截图在 `current-launch/e2e/artifacts`。
- 完整覆盖审计：
  `/zoesoft/medkernel/var/evidence/current-launch/launch-coverage.json`，
  `status=PASSED`，S0-S40 共 41 项、版本资产 13、标准患者资源 13、交付形态 5、服务组合 7。
- 发布后独立验收：
  `/zoesoft/medkernel/var/evidence/current-launch/release-acceptance.properties`，
  `release_status=PASSED`，`verified_at=2026-06-29T12:19:13+08:00`；
  严格 TLS、八段证据结构、真实重启 readiness、登录、Provider、知识 readiness、关系库持久化、
  演练后数据库备份与隔离恢复均通过。
  - 备份目录：`/zoesoft/medkernel/backups/launch-acceptance-9d19fb317f8c-20260629-121903`。

## 本轮代码改动

- `frontend/e2e/d6-graph-explore.spec.ts`：
  图谱验收先断言默认业务视图展示“追踪证据/追踪证据已记录”，再打开证据详情验证“追踪号”；
  去掉重复网页登录，复用 `ensureReadySession` 建立的真实角色会话。
- `frontend/e2e/pathway-graph-editor.spec.ts`：
  路径图关键时钟节点使用当前业务标签“节点身份”，不再查找旧“节点编码”。
- `frontend/e2e/product-role-journeys.spec.ts`：
  主动作跳转后改为条件等待主内容和加载态结束，避免国产 Chromium 仿真下只等 `networkidle` 的竞态。
- `frontend/e2e/real-frontdesk-rehearsal.spec.ts`：
  真实前台演练同步当前产品业务表达：稳定适配器身份、稳定资产身份、患者索引标题。

## 本地验证

- 统一迁移生成：`node scripts/db/generate-migrations.mjs --check` 通过。
- 脚本门禁：
  `node --test scripts/authenticity-guard.test.mjs scripts/config-boundary-guard.test.mjs scripts/migration-convention-guard.test.mjs scripts/performance-contract-guard.test.mjs scripts/release/full-system-rehearsal.test.mjs scripts/release/launch-coverage-audit.test.mjs`
  通过，80 tests passed。
- 前端：`npm --prefix frontend run verify` 通过，111 files / 867 tests passed。
- 前端构建：`npm --prefix frontend run build` 通过。
- 后端构建：`mvn -q -f medkernel-backend/pom.xml -DskipTests package` 通过。
- 后端迁移/契约子集：
  `mvn -q -f medkernel-backend/pom.xml -Dtest=MigrationBaselineContractTest,H2BaselineMigrationTest,FlywayMultiDialectSmokeTest,ServiceContractGovernanceTest test`
  退出 0；本机 Docker/Testcontainers socket 不可用有 WARN，H2/Flyway 迁移验证通过。
- CLI：`npm --prefix cli test` 通过，30 tests passed。
- MCP：`npm --prefix mcp-server test` 通过，16 tests passed。
- Inventory：
  - `node scripts/authenticity-guard.mjs --mode=inventory` 通过。
  - `node scripts/config-boundary-guard.mjs --mode=inventory` 通过。
  - `node scripts/migration-convention-guard.mjs --mode=inventory` 通过。
- 中文注释：`bash scripts/check-comment-zh.sh --self-test && bash scripts/check-comment-zh.sh --mode=full` 通过。
- 空白检查：`git diff --check` 通过。

## 调试记录

- 初始完整 E2E 红灯：`38 passed / 7 failed / 5 did not run`。
- 根因：
  - E2E 仍使用旧技术标签（`适配器标识`、`资产编码`、`节点编码`、`患者主索引 MPI`）。
  - 图谱脚本期待默认视图展示追踪号，和当前“追踪号只在证据详情中展示”的产品规则冲突。
  - 职责旅程脚本只等 `networkidle`，国产 Chromium 仿真下目标页仍处加载态。
- 修复后目标集：`20 passed / 2 failed`，剩余为真实前台值集旧标签。
- 修复值集/MPI 后，真实前台单独重跑：`2 passed (1.4m)`。
- 完整回归重跑：`50 passed (14.1m)`。

## 下一步

1. 本轮只需完成本地提交，不推送远程、不合并 `main`。
2. 后续若继续上线工作，先从当前分支和本文件核实，不要回到旧 `930745d5` 或旧“134 未部署”事实。
3. 继续保持证据详情边界：默认业务视图不暴露追踪号、原始标识、技术编码；需要时通过受控证据详情展开。
