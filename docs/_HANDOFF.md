# 会话接力

> 开工先读本文件。这里只保留当前可执行事实；产品范围见
> [PRODUCT_SCOPE](PRODUCT_SCOPE.md)，实施契约见当前 OpenSpec。历史过程使用 Git 追溯，不在当前工作树保留第二套事实源。

## 当前唯一主线

- OpenSpec 变更：`converge-full-launch-and-knowledge-platform`，schema 为 `spec-driven`。
- 隔离工作树：`/Users/zhikunzheng/.config/superpowers/worktrees/codex3/launch-convergence`。
- 实施分支：`codex/launch-convergence`。
- 固定输入锚点：`sourceBaseCommit=7217504ce82e1aa119c3402e3b5d054f9369e018`；该提交已知不是 RC，禁止直接提升。
- 本文件所在 Git 提交是下一步要验证的 `candidateCommit`；它只有在任务 `1.6` 从全新检出完成全部规定门禁并生成、迁移、独立重验 RC0 清单后，才具备 clean RC0 资格。
- 原工作树 `/Users/zhikunzheng/个人/郑志坤/medkernel/codex3` 仍停在上述输入锚点，且只有用户既有脏项 `docs/DEPLOYMENT_AND_REHEARSAL.md` 与 `test-results/`；不得回滚、暂存、清理或污染它们。

## OpenSpec 当前进度

任务 `1.1`～`1.5` 已完成，其余任务仍按 `openspec/changes/converge-full-launch-and-knowledge-platform/tasks.md` 顺序执行：

1. `1.1` 已实现 RC0 清单创建与独立重验，绑定固定输入锚点、完整候选提交、依赖声明与解析报告、run-id、时间窗、九类门禁证据和六类候选制品摘要。
2. 清单拒绝 sourceBase 直接提升、错误提交、tracked/untracked/ignored 残留、`skip-worktree`/`assume-unchanged`、仓库或 Git 元数据目录内 bundle、路径和索引符号链接、历史/越窗证据、缺失或未知门禁、制品漂移及未知清单字段。
3. `1.2` 已隔离 `IntegrationServiceTest` 非事务用例的提交数据，同时保留审计事实提交后真实回读。
4. `1.3` 已统一 35 个入口口径，修复前端严格校验和客户面工程化措辞，并补临床账号真实 403、审计角色回读及 `ASSIGNED → SUBMITTED → CLOSED` 状态证据。
5. `1.4` 已在唯一模式源补回正式 `DIAGNOSIS`，五方言生成结果一致。
6. `1.5` 已让 OpenSpec 上下文包含 `PRODUCT_SCOPE` 与本交接文件。

## 2026-07-10 新鲜自动验证

以下命令均在隔离工作树执行，未操作 134：

```bash
cd medkernel-backend
mvn -B -q -Dtest=IntegrationServiceTest,D0DomainAcceptanceTest test

cd ../frontend
npm run verify

cd ..
node --test scripts/authenticity-guard.test.mjs
node scripts/authenticity-guard.mjs --mode=inventory
node --test scripts/db/generate-migrations.test.mjs
node scripts/db/generate-migrations.mjs --check
node --test cli/test/*.test.mjs
node --test mcp-server/test/*.test.mjs
bash deploy/onprem/tests/validate-medkernel-deploy.sh
bash deploy/onprem/tests/validate-mk-publish-package.sh
bash scripts/check-shell-test-assertions.sh
bash deploy/onprem/tests/validate-medkernel-fresh-deploy.sh
bash deploy/onprem/tests/validate-ollama-model.sh
bash deploy/onprem/tests/validate-medkernel-failure-recovery.sh
bash deploy/onprem/tests/validate-medkernel-post-rehearsal-verify.sh
```

结果：后端定向测试退出码 0；前端 `verify` 为 116 个测试文件、2193 项测试通过；真实性 56/56 且 inventory 清零；数据库生成器 8/8；CLI 30/30；MCP 16/16；全部列出的部署合同退出码 0。

RC 清单最终新鲜验证：

```bash
node --check scripts/release/rc-manifest-lib.mjs
node --check scripts/release/rc-manifest.test.mjs
node --check scripts/release/rc-manifest.mjs
node --test scripts/release/rc-manifest.test.mjs
frontend/node_modules/.bin/prettier --check \
  scripts/release/rc-manifest-lib.mjs \
  scripts/release/rc-manifest.test.mjs \
  scripts/release/rc-manifest.mjs
openspec validate converge-full-launch-and-knowledge-platform \
  --strict --no-interactive
git diff --check
```

结果：RC 清单 42/42 通过；Prettier、OpenSpec strict validate 与 `git diff --check` 均通过。`openspec/config.yaml` 仅有 Git 的 CRLF 将转 LF 提示，不是校验失败。

## 当前不能宣称的结论

- **尚无 clean RC0。** 当前结果包含定向后端回归和现工作树前端验证，不能替代任务 `1.6` 的全新检出、按锁文件重建依赖及完整 clean 基线。
- **尚未运行本候选提交的全量 Playwright 浏览器 E2E。** 不得声称浏览器 E2E 或 `BROWSER_E2E` 门禁已通过。
- 尚未从本候选提交执行 `mvn -B -q clean test`，也尚未形成并独立重验九类门禁、六类制品的 RC0 bundle。
- 未操作 134、真实模型 Provider、真实第三方、清库、覆盖部署或发布；不得把本地合同测试冒充目标环境证据。

## 下一最小任务：`1.6`

1. 读取本文件所在提交的完整 40 位 `candidateCommit`，从该提交建立全新隔离检出；不得复用当前 `target/`、`dist/`、`tsbuildinfo`、`test-results/`、Playwright 产物或 `/tmp` 历史证据。
2. 严格按锁文件重建前后端依赖；RC bundle 必须位于工作树、`git-dir` 和 `git-common-dir` 之外。
3. 在同一 run-id 和 candidateCommit 下执行并保存九类本次证据：`BACKEND_TESTS`、`BROWSER_E2E`、`CLI_TESTS`、`DATABASE_GENERATOR`、`DEPLOYMENT_CONTRACTS`、`FORMAT_CHECK`、`FRONTEND_VERIFY_BUILD`、`MCP_TESTS`、`T_GATE`。
4. 只从本次候选构建形成六类制品：`BACKEND_JAR`、`FRONTEND_DIST`、`CLI_PACKAGE`、`MCP_PACKAGE`、`DATABASE_MIGRATIONS`、`ONPREM_DELIVERY`；复制到仓库外 bundle 后清除检出内构建与测试残留。
5. 使用 `scripts/release/rc-manifest.mjs create` 生成不可覆盖的 RC0 清单；把完整 bundle 搬迁到另一仓库外位置后，再用 `verify` 独立重验。
6. 任一门禁失败、未知、跳过，或摘要、提交、run-id、时间窗、依赖和制品不一致时，任务 `1.6` 保持未完成，不得宣称 RC0。

## 安全与操作边界

- 开发阶段只运行自动回归，不新增反复人工项目关卡；医疗资源发布仍必须由有资质责任人审核。
- 134 仅允许只读核查；清库、停机、覆盖部署前仍必须取得一次绑定主机、数据库、目录、提交、run-id、备份和窗口的原子破坏性确认。
- 不使用真实患者数据，不把密钥、凭据、数据库密码、JWT、证书私钥或未脱敏目标机证据写入仓库和日志。
