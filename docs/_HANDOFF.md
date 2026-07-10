# 会话接力

> 开工先读本文件。这里只保留当前可执行事实；产品范围见
> [PRODUCT_SCOPE](PRODUCT_SCOPE.md)，实施契约见当前 OpenSpec。历史过程使用 Git 追溯，不在当前工作树保留第二套事实源。

## 当前唯一主线

- OpenSpec 变更：`converge-full-launch-and-knowledge-platform`，schema 为 `spec-driven`。
- 隔离工作树：`/Users/zhikunzheng/.config/superpowers/worktrees/codex3/launch-convergence`；实施分支：`codex/launch-convergence`。
- 固定输入锚点：`sourceBaseCommit=7217504ce82e1aa119c3402e3b5d054f9369e018`；该提交不是 RC，禁止直接提升。
- clean RC0 候选固定为 `candidateCommit=d4514938e6ba7d6f0d09eb736a0c66ab72863b07`。后续任务勾选和本文件更新属于 bookkeeping，不得把更新后的分支 HEAD 冒充已验证候选。
- OpenSpec 任务 `1.1`～`1.6` 已完成，当前进度应为 `6/82`；本批必须停在这里，不得越过任务 `1.7` 直接进入 `2.1`。
- 受保护原工作树 `/Users/zhikunzheng/个人/郑志坤/medkernel/codex3` 仍停在输入锚点，现有用户改动包括 `docs/DEPLOYMENT_AND_REHEARSAL.md`、三个前端 E2E/凭据合同文件和 `test-results/`；不得回滚、暂存、清理或污染。

## clean RC0 与可重算证据

- run-id：`rc0-20260710T155756Z-d4514938e`；时间窗：`2026-07-10T15:57:56.000Z`～`2026-07-10T16:48:19.111Z`。
- 全新 detached 检出：`/Users/zhikunzheng/.medkernel-rc0-runs/rc0-20260710T155756Z-d4514938e/checkout`；依赖按锁文件重新建立，未复用实施工作树的 `target/`、`dist/`、测试产物或历史证据。
- 创建目录：`/Users/zhikunzheng/.medkernel-rc0-runs/rc0-20260710T155756Z-d4514938e/bundle-create`。
- 异目录重验副本：`/Users/zhikunzheng/.medkernel-rc0-verified/rc0-20260710T155756Z-d4514938e`；共复制 402 个普通文件。
- RC 清单：`rc-manifest.json`，SHA-256 为 `6754cbaf6beec00aad2cf204ea857c0fff8a0ebbb80a52e3f0cca4b076af5fb3`。
- 候选提交中的独立验证器对异目录返回：`status=VERIFIED`、sourceBase/candidate/run-id 全部一致。

### 九类门禁

| 门禁 | 本次结果 |
|---|---|
| `BACKEND_TESTS` | 523 份 Surefire 报告，3180 项测试，0 failure、0 error、7 条条件跳过 |
| `BROWSER_E2E` | 114/114；Chromium 57、国产 Chromium 内核仿真 57；workers=1、retries=0、unexpected/flaky/skipped 均为 0 |
| `CLI_TESTS` | 30/30 |
| `DATABASE_GENERATOR` | 8/8，五方言 `--check` 通过 |
| `DEPLOYMENT_CONTRACTS` | 7 个合同脚本通过，未执行目标机破坏性操作 |
| `FORMAT_CHECK` | RC manifest 42/42、Node 语法、Prettier、OpenSpec strict、`git diff --check` 通过 |
| `FRONTEND_VERIFY_BUILD` | 116 个测试文件、2207/2207；生产构建 3436 modules |
| `MCP_TESTS` | 16/16 |
| `T_GATE` | 真实性 56/56 且 inventory 无阻断；配置、迁移、性能合同与中文注释门禁通过 |

后端 7 条条件跳过的边界必须原样保留：PostgreSQL 空库 smoke 3 项因 Docker 不可用；PostgreSQL/Oracle Flyway smoke 2 项因 Docker 不可用；PostgreSQL/Oracle 10 万级 smoke 2 项因性能开关未开启。它们不是目标方言或 10 万级性能已通过的证据。

Browser E2E 首次仅发生执行器对子进程托管失败，readiness 预检连接失败且 Playwright 未启动；最终结果来自受管会话的完整重跑。第二项目只是“国产 Chromium 内核仿真（非现场认证）”，不得冒充真实国产浏览器或医院现场认证。E2E 后仅停止本轮 PID 88923/端口 39483；受保护端口 38083 仍为 `UP`。

### 六类候选制品

| 制品 | SHA-256 |
|---|---|
| `BACKEND_JAR` | `4146216023ebb0548ce892e14e7d4b9a7447917f72a8b705a113064bc294c6c0` |
| `FRONTEND_DIST` | `f33833d961f5d99209eeafd2fc9e29ddb71eaf26b0b8508ac49ab833a7ada699` |
| `CLI_PACKAGE` | `1f1e7ff8dedec2868d7efcc5f15c8fbf74622a67f8d70434df8139ef9ecaa1b4` |
| `MCP_PACKAGE` | `1527c16172bffd92d5b13a25dcd68e6f44721437e69364f93d6596241ff1c9cd` |
| `DATABASE_MIGRATIONS` | `1d55f4753ff03c3255a567c8196dfcfa7673feaf1b669ae2c686e833e00834b2` |
| `ONPREM_DELIVERY` | `6ffae4d03dcf3394381a24c78c45b2ea83a3083c9d5477dfd85229a369e1376c` |

前端、数据库迁移和 on-prem 归档均已按最终字节重打包并确认不存在 `._*` AppleDouble 条目；不得使用第一次打包的旧摘要。

## 当前不能宣称的结论

- 任务 `1.7` 尚未执行：本分支未因本批推送、未创建成果保全 PR、未等待远端 CI、未 squash 合入 `main`，也未确认 `origin/main` 包含本变更。
- 未操作 134、真实模型 Provider、真实第三方、清库、停机、覆盖部署或发布；本地自动合同不能冒充目标环境证据。
- 尚未完成 35 入口机器总账、平台知识权威、完整 `.mkp`、医疗资源工厂、院内空机安装或最终 `LAUNCH-01`～`LAUNCH-15` 验收；这些仍按 OpenSpec 后续任务实施。

## 下一最小任务：`1.7`

1. 从本文件读取固定 RC0 候选、run-id、异目录 `VERIFIED` 结果和上述边界；不得重新解释或替换候选字节。
2. 将 OpenSpec、基线修复和本次验证事实作为成果保全 PR 推送到 `codex/launch-convergence`，使用中文说明范围、验证、7 条条件跳过、医疗安全、部署和迁移影响。
3. 等待远端检查全绿后 squash 合入 `main`，确认 `origin/main` 含合并提交；随后从最新 `origin/main` 建立新的小写集工作树，才可进入任务 `2.1`。
4. 若远端检查失败，按失败证据修复并重新形成候选与 RC 清单；不得沿用已漂移的摘要或冒领当前 RC。

## 安全与操作边界

- 开发阶段只运行自动回归，不新增反复人工项目关卡；医疗资源发布仍必须由有资质责任人审核。
- 134 仅允许只读核查；清库、停机、覆盖部署前仍必须取得一次绑定主机、数据库、目录、提交、run-id、备份和窗口的原子破坏性确认。
- 不使用真实患者数据，不把密钥、凭据、数据库密码、JWT、证书私钥或未脱敏目标机证据写入仓库和日志。
