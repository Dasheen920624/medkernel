# 会话接力

> 开工先读本文件。这里只保留当前可执行事实；产品范围见
> [PRODUCT_SCOPE](PRODUCT_SCOPE.md)，实施契约见当前 OpenSpec。历史过程使用 Git 追溯，不在当前工作树保留第二套事实源。

## 当前唯一主线

- OpenSpec 变更：`converge-full-launch-and-knowledge-platform`，schema 为 `spec-driven`。
- 隔离工作树：`/Users/zhikunzheng/.config/superpowers/worktrees/codex3/launch-convergence`；实施分支：`codex/launch-convergence`。
- 固定输入锚点：`sourceBaseCommit=7217504ce82e1aa119c3402e3b5d054f9369e018`；该提交不是 RC，禁止直接提升。
- 原 `candidateCommit=d4514938e6ba7d6f0d09eb736a0c66ab72863b07` 及其 run-id `rc0-20260710T155756Z-d4514938e` 已作废，禁止推送、提升或复用其 `PROMOTABLE` 结论和制品摘要。
- 试运行候选 `b81f2e9b84a7874e89485ce32ff2b9238e60b32a` 已作废：clean 后端测试本身退出 0，但无 Docker/未开启专项容量环境的 3 组条件套件生成 7 条 skipped，独立验证器按合同正确拒绝；其 checkout、bundle、run 只能保留作失败诊断，禁止提升或复用。
- 试运行候选 `c369a4be2c842820bac4872538cae068c308b712` 已作废：clean 后端门禁全绿，Playwright 两项目实际 `expected=114, unexpected=0, flaky=0, skipped=0`，但同步 E2E 子进程阻塞事件循环约 33 分钟后，`AFTER_E2E` 单次 fetch 返回 `fetch failed`；后端当时仍存活并随后由运行器优雅停止，该现象与长阻塞后的陈旧连接复用一致，但旧错误包装已丢失底层 cause，不能把推断冒充已观测错误码。其 checkout、bundle、run 和通过报告只能用于根因诊断，禁止提升、复制或拼接到新候选。
- 试运行候选 `6b72c7bf4dbcd5ed40d6e2422e2c368338ac7451` 已作废：首次 detached clean 运行被桌面长任务续接机制终止前台 PTY，未形成最终清单；随后 one-shot LaunchAgent 误设 `ProcessType=Background`，后端全测从前台约 4 分钟被宿主 QoS 限速至约 25 分钟，并在已成功导出 10 万条数据后触发 `KnowledgeExportServiceLargeScaleTest` 的墙钟断言（实际 56 秒，要求小于 30 秒）。该失败同时暴露四处 10 万级墙钟合同漏标 `performance`，因此该候选及全部运行根目录只作诊断，禁止提升或复用。
- 试运行候选 `118969301fe03f03278877c8c23ef5def2544255` 已作废：r4 起跑后发现旧作废运行残留的 `embed-business-host-server` 占用 4174，Playwright 正确拒绝端口污染；清理残留后建立的全新 r5 由本轮进程独占 4174/5173/28086，后端 clean 门禁实际 `tests=3175, failures=0, errors=0, skipped=0`，真实 Browser E2E 两项目各 57/57、合计 `expected=114, unexpected=0, flaky=0, skipped=0`。但 RC 解析器的单测夹具错误地把 Playwright 官方 JSON 中可选的叶子 `suites` 字段假定为必填数组，因而在 E2E 全绿后报“Playwright suite 结构非法”；r5 未生成完整九门禁、六制品清单，仍不得提升、复制或拼接证据。
- 当前不存在可提升 RC0。必须完成本文件列出的安全修复、清单加固和全新干净重跑，才能固定新的 `candidateCommit`。
- 受保护原工作树 `/Users/zhikunzheng/个人/郑志坤/medkernel/codex3` 仍有用户改动；不得回滚、暂存、清理或污染。

## 旧 RC0 作废原因

1. `ClinicalRuntimeReleaseService` 曾允许当前机构版本里的 `WITHDRAWN` 医疗资产进入新的机构生效版本，违反 `CONSTITUTION §3`“撤回阻止危险或错误版本进入新的机构生效版本；历史证据仍可重放”的红线。
2. 旧 RC 验证器只校验自报 `PASSED` 的摘要 JSON 和任意文件哈希，没有独立解析原始执行结果、退出码、依赖重建和制品格式/来源。删除原始日志与报告后仍可返回 `VERIFIED`，普通文本也可冒充 JAR/TAR 制品。
3. 因候选代码和证据契约都将变化，旧候选的九类门禁日志只能作为诊断材料，不能作为发布证据；不得在新清单里继承其状态或摘要。

## 当前实施状态

- 前后端 `WITHDRAWN` 穿透已按 TDD 修复：平台标准发布、机构激活、历史回滚和离线恢复会在事务内按稳定顺序锁定精确版本账本行，重新核对租户、层级、类型、身份、版次、内容摘要和状态；撤回版本只能保留历史重放，不能进入任何新版本。前端只允许本次查询确认的 `DRAFT/PUBLISHED` 候选进入评估和提交。
- 候选后端 JAR 内嵌 `META-INF/medkernel-build.json`，公开 ping 只从制品资源解析完整提交；RC 浏览器门禁同时保存 readiness 与运行时身份原始响应，拒绝误连任意返回 `UP` 的旧服务。
- RC schema v2、六制品构建器和无人工中间门的运行器已实现：全新检出按锁文件重建依赖，执行九类门禁，直接解析 Surefire/Playwright/T-GATE 原始输出，构建并核验六类候选制品，绑定运行标识、完整提交、时间窗、命令、退出码、来源树、逐文件摘要和内嵌元数据；复制、删除、篡改、越界、符号链接、旧 schema、伪制品和工作区残留攻击均有拒绝用例。
- clean RC0 首次真实执行暴露并确认了专项环境边界：普通后端 clean 门禁显式排除 `docker,performance` 标签，机器可读计划声明这些套件必须在后续 PostgreSQL 16/openEuler 目标环境和 10 万级容量门禁中独立完成；实际生成的 Surefire 报告仍严格要求零 skipped。定向 Maven 验证仅执行未标记的 H2 方言测试，报告 `tests=1, failures=0, errors=0, skipped=0`。
- RC 机器计划现显式枚举五个 10 万级墙钟套件；起跑前扫描全部 Java 测试并拒绝漏登记、漏类级或方法级 `performance` 标签。三个纯容量类使用类级标签，`KnowledgeIdentityRepositoryTest` 只标记单个 10 万级方法，保留其余普通仓储回归；定向 Maven 排除验证实际执行 `tests=7, failures=0, errors=0, skipped=0`，三个纯容量类未生成 Surefire 报告。
- Playwright 原始报告解析器现遵守仓内锁定版本官方 `JSONReportSuite` 契约：`specs` 必须为数组，叶子 suite 可省略可选的嵌套 `suites`，但显式非数组值仍立即拒绝。修复测试先以真实叶子结构稳定复现旧误拒，再转绿；新解析器直接重算已作废 r5 的原始 JSON，得到两个项目各 57/57、合计 114/114，仅用于证明解析根因，不把旧运行提升为 RC。
- 候选运行探针已改为每次显式短连接，并仅对带 `RUNTIME_PROBE_TRANSPORT`/网络错误码的瞬断做最多 30 秒条件重试；每次尝试仍须完整通过 readiness 和 JAR 内嵌提交身份。身份错配、响应结构或内容错误立即失败，持续瞬断会保留完整 cause/code 后阻断，不能把不稳定运行时伪报为通过。
- 当前最新验证：后端受影响回归 74/74，新增专项分类定向回归 7/7 且零跳过；前端 `npm run verify` 为 116 文件、2210 测试全绿，生产构建成功；本次 Playwright 结构修复的 RC runner 测试 11/11，完整 RC/release 工具 112/112，且能从 r5 原始报告独立重算 114/114；OpenSpec strict、RC JS Prettier 与 `git diff --check` 均通过。以上不能替代下一个候选的完整 clean RC0。
- OpenSpec `tasks.md` 已收敛为 205 个可供后续 AI 顺序执行的原子任务（4 完成、201 待执行）；覆盖缺口总账、首次信任根、签发者密钥隔离、自包含 `.mkp`、16 语义族医疗资源工厂、离线依赖仓、openEuler 空机部署、目标工具本地实现、134 一次确认部署、全量资源生产、医院复制和知识源迁移。
- 本逻辑单元提交在已作废的 `118969301` 基础上只包含 Playwright 官方结构兼容修复、回归测试和同步文档；其干净 `git rev-parse HEAD` 是下一次待重跑候选，提交后任何漂移都必须形成新候选。不得把构建残留或 r4/r5 证据纳入候选。

## 下一执行序列

1. 只暂存本逻辑单元五个文件并提交，以干净 `git rev-parse HEAD` 固定完整 `candidateCommit`；提交后任何源码或合同漂移都必须形成新候选并废止旧证据。
2. 从新候选建立全新 detached 干净检出，使用不带 Background QoS 的 one-shot 标准任务执行 `rc-runner.mjs run`，自动重建依赖并完整执行后端、真实 Browser E2E、CLI、五方言数据库、部署合同、格式/OpenSpec、前端验证与构建、MCP、T-GATE 九类门禁。
3. 由候选内验证器从原始报告独立计算结果，核验六类真实制品格式、内容、运行身份和候选来源；将整个 bundle 复制到异目录重验，再逐类删除或篡改证据确认非零退出。
4. 只有新 RC0 完整通过后，才完成 OpenSpec 1.1/1.6，推送成果保全 PR、等待远端 CI、squash 合入 `main` 并确认 `origin/main` 含合并提交。
5. 从最新 `origin/main` 建立新的小写集工作树，按 `tasks.md` 继续完整平台知识权威、`.mkp`、医疗资源工厂、离线空机安装和最终 `LAUNCH-01`～`LAUNCH-15` 验收；不得回流旧候选或另建平行计划。

## 上线与 134 边界

- 开发阶段只运行自动回归，不新增反复人工项目关卡；医疗资源发布仍必须由有资质责任人审核，AI 不自动开嘱。
- 在系统完整验证稳定前不得部署 134。当前只允许对 134 做已授权的只读核查。
- 清库、停机、覆盖部署只保留一次最终原子确认；确认必须绑定主机、数据库、目录、候选提交、run-id、备份恢复点和实施窗口。
- 134 或未来其它服务器可作为平台知识管理源，但院内运行包必须固定版本、可签名校验、可离线安装、可回滚；平台更新不得绕过医院侧审核和机构生效版本。
- 不使用真实患者数据，不把密钥、凭据、数据库密码、JWT、证书私钥或未脱敏目标机证据写入仓库和日志。
