# 会话接力

> 开工先读本文件。这里只保留当前可执行事实；产品范围见
> [PRODUCT_SCOPE](PRODUCT_SCOPE.md)，实施契约见当前 OpenSpec。历史过程使用 Git 追溯，不在当前工作树保留第二套事实源。

## 当前唯一主线

- OpenSpec 变更：`converge-full-launch-and-knowledge-platform`，schema 为 `spec-driven`。
- 隔离工作树：`/Users/zhikunzheng/.config/superpowers/worktrees/codex3/launch-convergence`；当前实施分支：`codex/launch-ledger`，基于 `origin/main=4c6795aaa6ba6481756e239fefff90afd1c724a2`。
- 固定输入锚点：`sourceBaseCommit=7217504ce82e1aa119c3402e3b5d054f9369e018`；该提交不是 RC，禁止直接提升。
- 原 `candidateCommit=d4514938e6ba7d6f0d09eb736a0c66ab72863b07` 及其 run-id `rc0-20260710T155756Z-d4514938e` 已作废，禁止推送、提升或复用其 `PROMOTABLE` 结论和制品摘要。
- 试运行候选 `b81f2e9b84a7874e89485ce32ff2b9238e60b32a` 已作废：clean 后端测试本身退出 0，但无 Docker/未开启专项容量环境的 3 组条件套件生成 7 条 skipped，独立验证器按合同正确拒绝；其 checkout、bundle、run 只能保留作失败诊断，禁止提升或复用。
- 试运行候选 `c369a4be2c842820bac4872538cae068c308b712` 已作废：clean 后端门禁全绿，Playwright 两项目实际 `expected=114, unexpected=0, flaky=0, skipped=0`，但同步 E2E 子进程阻塞事件循环约 33 分钟后，`AFTER_E2E` 单次 fetch 返回 `fetch failed`；后端当时仍存活并随后由运行器优雅停止，该现象与长阻塞后的陈旧连接复用一致，但旧错误包装已丢失底层 cause，不能把推断冒充已观测错误码。其 checkout、bundle、run 和通过报告只能用于根因诊断，禁止提升、复制或拼接到新候选。
- 试运行候选 `6b72c7bf4dbcd5ed40d6e2422e2c368338ac7451` 已作废：首次 detached clean 运行被桌面长任务续接机制终止前台 PTY，未形成最终清单；随后 one-shot LaunchAgent 误设 `ProcessType=Background`，后端全测从前台约 4 分钟被宿主 QoS 限速至约 25 分钟，并在已成功导出 10 万条数据后触发 `KnowledgeExportServiceLargeScaleTest` 的墙钟断言（实际 56 秒，要求小于 30 秒）。该失败同时暴露四处 10 万级墙钟合同漏标 `performance`，因此该候选及全部运行根目录只作诊断，禁止提升或复用。
- 试运行候选 `118969301fe03f03278877c8c23ef5def2544255` 已作废：r4 起跑后发现旧作废运行残留的 `embed-business-host-server` 占用 4174，Playwright 正确拒绝端口污染；清理残留后建立的全新 r5 由本轮进程独占 4174/5173/28086，后端 clean 门禁实际 `tests=3175, failures=0, errors=0, skipped=0`，真实 Browser E2E 两项目各 57/57、合计 `expected=114, unexpected=0, flaky=0, skipped=0`。但 RC 解析器的单测夹具错误地把 Playwright 官方 JSON 中可选的叶子 `suites` 字段假定为必填数组，因而在 E2E 全绿后报“Playwright suite 结构非法”；r5 未生成完整九门禁、六制品清单，仍不得提升、复制或拼接证据。
- 试运行候选 `88a1663777260e66c325588c3ce1948a03700c9f` 已作废：r6 因操作者把合同要求的“预先存在且为空” bundle/run 根目录误设为不存在，在执行任何门禁前被总控拒绝；使用全新根目录、run-id 和端口重建的 r7 完整通过九类门禁，其中后端为 519 份报告、3175 项测试零失败/错误/跳过，Browser E2E 两项目各 57/57、合计 114/114，CLI、五方言数据库、部署、格式/OpenSpec、前端 verify/build、MCP、T-GATE 均退出 0。r7 随后构建出前五类制品，但 `ONPREM_DELIVERY` 校验器错误地用规范化 LF 的 Git raw blob 比较 `.gitattributes` 要求导出为 BOM+CRLF 的 `mk-publish.ps1`，在实际 tar、构建暂存源和 `git archive` 三者字节一致时误拒；未形成六制品清单，r6/r7 全部证据仍禁止提升或拼接。
- 试运行候选 `2e60d2a9fa47917d73157153b7fc7c377cb9b742` 已作废：r8 从 detached clean checkout 按锁文件重建依赖，完整通过九类门禁并构建六类制品；后端为 519 份报告、3175 项测试零失败/错误/跳过，Browser E2E 两项目各 57/57、合计 114/114，前端为 116 文件、2210 项测试全绿且生产构建成功。但运行器与终态清单各维护一套 Playwright 结构解析器，终态副本仍把叶子 suite 的可选 `suites` 当必填；继续诊断又确认终态日志解析器写死旧 `#` 前缀且未处理 Vitest 原生 ANSI SGR 显示码。原始运行因此未生成终态清单，r8 checkout、bundle、run 和全部通过证据永久禁止提升、复制或拼接；修复后的代码对完整 r8 bundle 成功执行诊断性 `CREATED` 与 `VERIFIED`，仅证明根因和证据完整性，不改变 r8 作废状态。
- 候选 `6bd805b3c40e68b95efeeb2e07e7eefe07ef3f96`（r9）已作废：本地 clean RC0 的九类门禁、六类制品、复制复验和篡改拒绝均真实通过，但 PR #654 的 required CI 运行 `29153747137` 在前端全量覆盖率中暴露两个测试时序合同不稳定点；按“测试有任何变化即形成新候选并完整重跑”的规则，r9 不再可提升。其原始 bundle `/Users/zhikunzheng/.medkernel-rc0-runs/rc0-20260711-6bd805b3c-r9/bundle`、复制件和诊断副本仅保留作根因与完整性证据，禁止发布、部署、拼接或复用 `PROMOTABLE/VERIFIED` 结论。
- 当前唯一可提升 RC0 为冻结候选 `7674532fdb4f3db8373bb996369cfc1fe359c553`，run-id `rc0-20260711T132737Z-7674532fd-r10`，来源锚点仍为 `7217504ce82e1aa119c3402e3b5d054f9369e018`。它从 detached clean checkout 按锁文件重建依赖，九类门禁、六类制品和候选内独立验证器均真实通过；原始 bundle 位于 `/Users/zhikunzheng/.medkernel-rc0-runs/rc0-20260711-7674532fd-r10/bundle`。PR #654 的同候选 required CI 运行 `29154337443` 为 8/8 success。候选代码、测试与发布合同已冻结；后续证据文档提交不是新候选，不得用其提交哈希替代该 candidateCommit。
- PR #654 的最终证据提交 `8739d71d6a0577e08ca9e7a02cb0434343f700ef` 对应 required CI 运行 `29156309904`，8/8 全部 success；该 PR 已于 2026-07-11 squash 合入 `main`，squash commit 为 `4c6795aaa6ba6481756e239fefff90afd1c724a2`。重新 fetch 后 `origin/main` 精确指向该提交，`git merge-base --is-ancestor` 退出 0，远端 `codex/launch-convergence` 已删除。
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
- 运行器与终态清单现调用同一共享 Playwright suite/spec 结构解析器，消除两份契约独立漂移。终态日志解析器按锁定 Node 24 同时接受 TAP/spec reporter 的 `#` 与 `ℹ` 计数前缀，并仅剥离标准 ANSI SGR 显示码后核验原始语义；计数不一致、真实失败、缺构建标记和畸形结构的反例仍全部拒绝。
- 候选制品来源校验现按同一候选提交的 `git archive` 导出字节逐文件重算，保留 `.gitattributes` 的确定性 EOL/导出语义后再与 tar 比较，不以文本归一化或跳过校验放行。测试夹具显式证明 PowerShell raw blob 为 LF、交付包为 CRLF，并继续拒绝任何非候选来源或字节篡改。
- 候选运行探针已改为每次显式短连接，并仅对带 `RUNTIME_PROBE_TRANSPORT`/网络错误码的瞬断做最多 30 秒条件重试；每次尝试仍须完整通过 readiness 和 JAR 内嵌提交身份。身份错配、响应结构或内容错误立即失败，持续瞬断会保留完整 cause/code 后阻断，不能把不稳定运行时伪报为通过。
- r9 的本地真实结果仍是后端 519 份 Surefire 报告、3175 项测试零失败/错误/跳过，Browser E2E 两项目各 57/57、合计 `expected=114, unexpected=0, flaky=0, skipped=0`，九类门禁与六类制品完整；这些事实只用于诊断验证器和运行链，不能抵消远端 required CI 失败，也不能提升 r9。运行结束后 LaunchAgent 已卸载，4174/5173/28090 无监听。
- r10 后端生成 519 份 Surefire 报告、3175 项测试，失败/错误/跳过均为 0；Browser E2E 的 Chromium 与国产 Chromium 模拟各 57/57，合计 `expected=114, unexpected=0, flaky=0, skipped=0`，且 E2E 前后 readiness 与 JAR 内嵌完整提交均匹配。CLI、五方言数据库、部署合同、格式/OpenSpec、前端 verify/build、MCP、T-GATE 其余门禁均退出 0；六类制品为后端 JAR、CLI 包、五方言迁移包、前端静态包、MCP 包和院内离线交付包。运行结束后 LaunchAgent `com.medkernel.rc0.7674532fd.r10` 已卸载，4174/5173/28091 均无监听，checkout 保持 clean。
- r10 完整 bundle 已复制至 `/Users/zhikunzheng/.medkernel-rc0-verification/7674532fd-r10-copy` 并由候选内验证器独立返回 `VERIFIED`。原包、复制件和恢复后的篡改验证副本各有 567 个文件、0 个符号链接，按逐文件 SHA-256 排序聚合后的摘要均为 `bf84dc649759edfd11cebff2016b6ef3f59c988f2af8a258c88beaab66ccbba3`。验证器分别对缺 `BACKEND_TESTS` 门禁记录、缺其原始日志、缺 Maven 本次解析报告和 CLI 制品摘要漂移返回非零拒绝；每次恢复后重新返回同一 candidateCommit/run-id 的 `VERIFIED`。
- OpenSpec `tasks.md` 已收敛为 205 个可供后续 AI 顺序执行的原子任务（16 完成、189 待执行）；覆盖缺口总账、首次信任根、签发者密钥隔离、自包含 `.mkp`、16 语义族医疗资源工厂、离线依赖仓、openEuler 空机部署、目标工具本地实现、134 一次确认部署、全量资源生产、医院复制和知识源迁移。
- 任务 2.1 已按 TDD 完成：`node --test scripts/release/product-entry-catalog.test.mjs` 先因 `docs/contracts/product/product-entry-catalog.v1.json` 缺失以退出码 1 失败；补齐合同后 1/1 通过。合同恰含 35 个唯一 `entryCode` 和 35 条唯一路由，承载位置为 33 个主导航、1 个页头、1 个个人入口，四职责覆盖数为平台管理员 13、医疗引擎运营员 22、临床使用者 9、审计员 6；逐项声明权限、有效任职组织交集、核心动作、权威服务回读、`shared-audit-event.v1`、六态和证据键。一次性反向核对现有后端菜单、前端路由、默认职责策略与功能目录均无漂移；该 JSON 自此作为入口唯一机器合同，后续 2.2-2.3 必须让消费者由其生成并删除旧并行集合。
- 任务 2.2 已完成：`generate-product-entry-consumers.mjs --check` 先同时报告后端 `menu-permission-catalog.generated.json` 与前端 `productEntryCatalog.generated.ts` 缺失并以退出码 1 失败；生成后源合同 SHA-256 为 `178954986bb5d083a87c00bbe7fa515d4f490aaf90f47432d8820412f90991a6`，两个消费者均由同一摘要绑定。临时篡改后端嵌入摘要时 `--check` 精确报告单文件漂移并退出 1，重新生成后恢复 `VERIFIED`；前端 Prettier 与 TypeScript 全量类型检查退出 0。CI 的前端构建作业已接入合同测试和生成器检查，禁止只改源合同不重生成。
- 任务 2.3 已完成：后端 `MenuPermissionCatalog` 改为严格读取生成资源，前端路由、菜单和四职责快照均由生成合同派生，上线覆盖矩阵也由合同枚举全部入口；第 35 项 `domain-facade-b0-evidence` 只有目标 Playwright 用例通过且严格附件可验证时才计入核心动作总证据。TDD 先分别证明后端缺 `route`/职责消费者无法编译、前端移除 Java 源码快照后职责矩阵变空，以及第 36 项夹具会被唯一合同校验拒绝；实现后 `rg -n 'ALL_34|34 个入口|entryCount *= *35' frontend/src medkernel-backend/src scripts` 零命中，生成器 `--check` 返回 `VERIFIED`，发布脚本 17/17、前端 116 文件 2217/2217、生产构建和定向后端测试全绿。后端按 RC0 同口径 fresh `clean test` 并明确排除 `docker,performance` 后生成 519 份 Surefire 报告、3177 项测试，失败/错误/跳过均为 0；Docker/PostgreSQL/openEuler 与容量门禁仍由后续目标环境任务独立完成，不能据此冒领。
- 任务 2.4 已完成：`launch-entry-evidence.schema.json` 固定 `ROUTE_ONLY`、`READBACK_ONLY`、`CORE_ACTION`、`CORE_ACTION_WITH_PERMISSION`、`CORE_ACTION_WITH_SIX_STATE` 五级单调证据强度，完整入口合同要求逐项覆盖路由、权威回读、真实核心动作、审计回读、允许/拒绝权限、有效组织范围与六态。覆盖审计按已验证能力反算强度，不信自报等级，并把恰好 35 行逐项绑定入口编码、路由、动作、权限、组织范围、六态、观察时间、覆盖边界和未覆盖范围；负向测试证明 `ROUTE_ONLY` 不能冒充权限/六态证据，真实 `CORE_ACTION` 也不能冒充完整合同。当前 Browser E2E 只如实产出 `CORE_ACTION` 和明确的权限/组织/六态缺口，故完整上线审计仍须在 2.9 补齐真实测试后才能通过，不能提前自报 `PASSED`。验证结果为覆盖审计 10/10、发布脚本 20/20、前端 116 文件 2217/2217、生产构建成功，生成器检查、严格 OpenSpec 与差异检查均通过。
- 任务 2.5 已完成：`launch-ledger.v1.schema.json` 成为 `LAUNCH-01` 至 `LAUNCH-15` 编码、中文验收语义、要求覆盖范围和 `PASSED/FAILED` 状态的唯一合同，原运行器内 15 项硬编码已删除并改为从 schema 加载。审计输出的每项账本严格携带要求范围、实际证据范围、缺失范围、逐条前置证据引用、同一 RC0 `candidateCommit`、`runId` 和判定时间，并重新与覆盖矩阵逐项核对；缺项、重复、未知码、自由文本状态、字段漂移或候选/运行标识漂移均不能过账。全系统总控不再在总账缺失时现场重算放行，而是要求审计产出的固定总账并再次校验编码、语义、状态和覆盖一致性；`LAUNCH_RUN_ID` 已从演练入口传入审计并同步部署文档。TDD 红灯先因 `validateLaunchLedger` 不存在退出 1；实现后发布相关 22/22、JSON 语法、严格 OpenSpec 和差异检查通过。
- 任务 2.6 已完成：全系统总控在前九阶段逐项验证后、进入最终覆盖审计前原子生成固定路径 `source-provenance.json`，记录同一 `candidateCommit`、`runId`、捕获时间、精确证据路径和解析后 JSON 内容 SHA-256；最终审计只接受九个预定义阶段及路径，独立重算摘要，并把来源运行标识、候选提交、摘要和捕获时间写入每条 LAUNCH 前置引用。`validateEvidenceSource` 同时拒绝最终审计自证、白名单外路径、旧 run-id、观察/捕获晚于判定时间、来源清单时间倒置和内容摘要漂移；账本再次要求全部来源与本次候选、运行标识和时间窗一致。TDD 红灯先因校验器不存在退出 1，五类负向用例随后全部转绿；发布相关 23/23、生成器、JSON、格式、严格 OpenSpec 和差异检查通过。
- 任务 2.7 已完成：`launch-gap-classifier.mjs` 固定 `IMPLEMENTATION`、`TEST`、`DATA`、`ENVIRONMENT` 四类及其唯一 `gapKind` 映射；每项必须绑定 `LAUNCH-01` 至 `LAUNCH-15`、稳定缺口 ID、证据键、缺口说明和规范仓库相对 `ownerPath`。分类器拒绝未知/多分类、原因错配、原型键绕过、重复 ID、同一 LAUNCH/证据键重复登记和越界路径，并为无缺口输出四类全零结果；CI 已接入该合同。TDD 先因模块不存在退出 1，并分别以原型键和重复逻辑键复现红灯；实现后定向 6/6、发布脚本全量 131/131、语法与格式检查通过。
- 任务 2.8 已完成：`IMPLEMENTATION` 缺口必须携带严格 `remediationPlan`，其中 `failingTest` 必须是仓内现存的 Java、Node 或部署测试，`implementationPath` 必须与唯一 `ownerPath` 一致，`consumerReadback` 与 `auditReadback` 必须是两个不同的规范证据键；缺少任一项、布尔自报、路径错配或伪测试路径均拒绝。完整计划本身仍保持缺口 `OPEN`，不会冒充修复；只有实现项从重跑输入中消失才生成 `launch.gap.implementation.closed` 的 `CLOSED`、剩余数 0。TDD 先因 `remediationPlan` 被旧合同拒绝而红，部署 Shell 测试路径也单独先红；实现后定向 9/9、发布脚本全量 134/134、格式与语法检查通过。
- 任务 2.9 已完成：`TEST` 缺口的 `remediationPlan` 必须绑定仓内现存可执行测试、规范观察证据键和稳定大写观察码，缺字段、静态布尔或非法观察码均拒绝；计划存在时仍为 `OPEN`，只有测试缺口从重跑输入移除后才生成 `launch.gap.test.closed=CLOSED`。产品入口六态已按体验权威统一为加载、空、正常、错误、无权和部分成功，运行依赖降级继续单独诚实表达；35 入口只有在目标 Playwright 用例真实附带六个 DOM 观察码、逐入口允许/拒绝权限画像、有效组织范围和空职责账号回读后才升级为完整入口合同。最终本地 Chromium 复演为 1/1、`duration=62341ms`、`unexpected=0`、`flaky=0`、`skipped=0`，附件恰含 35 个唯一入口且允许/拒绝画像均实测 HTTP 200；发布脚本 135/135、前端 116 文件 2221/2221、生产构建、生成器、真实性门禁、格式、严格 OpenSpec 和差异检查均通过。全量前端同时清理了 LAUNCH 总账 schema 化后遗留的旧硬编码源码断言，未恢复第二套真相源。
- 新候选提交一旦冻结，不得再修改其代码、测试或发布合同；任何此类变化都必须再次形成新候选并完整重跑 RC0。证据状态与接力文档可在候选之后单独提交，但不得把文档提交哈希冒充候选。

## 下一执行序列

1. 在 `codex/launch-ledger` 按 TDD 执行 2.10：让 `DATA` 缺口必须绑定真实发布、生效、消费者回读和审计回读，拒绝仅有候选、文件或分类名的静态覆盖声明。
2. 依次执行 2.11-2.12 的环境缺口闭环和严格归零总账；每个 checkbox 独立验证、提交并同步本文件。
3. 35 入口与总账归零后进入平台知识权威、`.mkp`、16 语义族资源工厂、离线空机安装、openEuler/容量验证和医院复制；不得绕过依赖跳到 134。
4. 只在系统、资源包、离线部署和目标环境证据全部稳定后进入 134；134 继续保持只读，最终清库、停机、覆盖部署只申请一次绑定范围的原子确认。

## 上线与 134 边界

- 开发阶段只运行自动回归，不新增反复人工项目关卡；医疗资源发布仍必须由有资质责任人审核，AI 不自动开嘱。
- 在系统完整验证稳定前不得部署 134。当前只允许对 134 做已授权的只读核查。
- 清库、停机、覆盖部署只保留一次最终原子确认；确认必须绑定主机、数据库、目录、候选提交、run-id、备份恢复点和实施窗口。
- 134 或未来其它服务器可作为平台知识管理源，但院内运行包必须固定版本、可签名校验、可离线安装、可回滚；平台更新不得绕过医院侧审核和机构生效版本。
- 不使用真实患者数据，不把密钥、凭据、数据库密码、JWT、证书私钥或未脱敏目标机证据写入仓库和日志。
