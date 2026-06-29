# 会话接力

> 开工先读本文件。这里只保留当前可执行事实；产品范围见
> [PRODUCT_SCOPE](PRODUCT_SCOPE.md)，历史过程使用 Git 追溯，不在当前工作树保留第二套事实源。

## 当前主线

- 最新远端主线：`origin/main` / `main` / 合并前本地 `HEAD` 均为
  `1561ba6bef8777dcef76432696f43de4277fdd3f`
  （`完善全角色上线演练与134复演闭环 (#653)`）。
- 远程分支已清理：`origin` 仅保留 `main`（另有 `origin/HEAD -> origin/main`）。
- 当前本地工作分支：`codex/final-handoff-product-optimization`，从 `1561ba6b` 创建；
  本阶段只做本地提交，不推送远程，不直接改写远端 `main`。
- 当前用户约束：全程按最优决策执行，不中途咨询；每阶段更新接力并提交到本地分支；
  最终统一确认前不推送远程 `main`。
- `.codex/config.toml` 为未跟踪本地配置，不提交。

## 当前目标闭环结论

- 本轮已完成 MedKernel 全新项目上线级整体梳理与落地：统一平台权威版本与全链路能力，补齐全角色真实页面表达，
  同步统一迁移生成与全系统演练覆盖矩阵，并完成代码、契约、前后端、文档、测试、构建核查。
- #650、#651、#652、#653 均已合入远端 `main`；当前 `main` 的上线候选状态为
  “已合并 + 134 已完成清库复演”。
- 134 已按 squash 前候选 `228b16a8d8da8eb5747af9ab1cefcc2716c0dc2d` 清库重新部署，并完成全功能、全知识、
  全流程、全角色真实演练闭环；该候选内容已通过 #653 squash 合入远端 `main` 的
  `1561ba6bef8777dcef76432696f43de4277fdd3f`。不要再把旧“未推送/未合并 main”、
  “134 未更新/未复演”或旧 `9d19fb3` 证据当作当前事实。

## 本轮落地结论

- 已生成候选制品并在 134 清库部署：
  - 主机：`193.112.107.134`，hostname：`VM-0-13-opencloudos`。
  - manifest：`/zoesoft/medkernel/manifest.properties`。
  - `source=228b16a8d8da8eb5747af9ab1cefcc2716c0dc2d`。
  - `commit=228b16a8d8da8eb5747af9ab1cefcc2716c0dc2d`。
  - `deployedAt=2026-06-29T15:37:16+08:00`。
  - `jarSha256=e420ffac8c3ff791ebd02913500982826e87486031d2253aef46fba54137cd0c`。
- 134 与 main 提交映射：
  - 134 manifest 记录的是 #653 squash 前候选提交
    `228b16a8d8da8eb5747af9ab1cefcc2716c0dc2d`。
  - 远端 `main` 当前提交为 #653 squash 后提交
    `1561ba6bef8777dcef76432696f43de4277fdd3f`。
  - 发布证据可按“134 候选部署提交 `228b16a8` 等价合入 `main@1561ba6b`”理解；
    后续若重做正式发布制品，应以新的 `main` 提交重新写入 manifest。
- 清库部署后数据库验证：
  - public base tables：`208`（207 业务表 + `flyway_schema_history`）。
  - 业务表数：`207`。
  - Flyway 成功版本：`1`。
  - 服务 `active=active` / `enabled=enabled`，readiness HTTP 200 / `{"status":"UP"}`。
- 清库备份：
  - fresh pre-clear 备份：`/zoesoft/medkernel/backups/fresh-preclear-228b16a8d8da-20260629-153700`。
  - 发布后验收隔离恢复备份：`/zoesoft/medkernel/backups/launch-acceptance-228b16a8d8da-20260629-161442`。
- TLS：本轮使用 `/zoesoft/medkernel/nginx/ssl/server.crt` 作为可信 SAN 证书校验根；
  远端命令需带 `MEDKERNEL_TLS_CA_FILE=/zoesoft/medkernel/nginx/ssl/server.crt`。
  Playwright/Node 只追加 `NODE_EXTRA_CA_CERTS=/zoesoft/medkernel/nginx/ssl/server.crt`，不要用
  `SSL_CERT_FILE` 覆盖系统 CA。
- 模型提供方：`ollama-launch`，类型 `OLLAMA`，端点 `http://127.0.0.1:11434`，
  模型版本 `medkernel-qwen25:1.5b-v1`。

## 134 证据

- 统一证据目录：`/zoesoft/medkernel/var/evidence/current-launch`。
- 八段总证据：`/zoesoft/medkernel/var/evidence/current-launch/full-system.json`：
  `status=PASSED`，`source=228b16a8d8da8eb5747af9ab1cefcc2716c0dc2d`，8 段全部通过。
  - `account-bootstrap`：9 个账号验证通过。
  - `model-provider`：`ollama-launch`，3 个评估用例通过。
  - `platform-baseline`：基线发布与字段目录版本创建通过。
  - `sandbox`：10 条规则、40 个用例、运行时就绪。
  - `full-knowledge`：11 个知识域，最终版本 `V2` / `ACTIVE`。
  - `runtime-resilience`：模型关闭诚实降级，B0 主链路 `17/17`，恢复后 readiness 与模型调用恢复。
  - `browser-e2e`：Playwright `52 passed`，`unexpected=0`，`flaky=0`。
  - `launch-coverage`：6 产品层、13 标准患者资源、13 版本资产、11 知识域、41 场景、12 角色视角等全部通过。
- 全知识证据：`/zoesoft/medkernel/var/evidence/current-launch/full-knowledge.json`：
  `status=PASSED`，阶段 `FULL_FUNCTION_FULL_KNOWLEDGE`，11 个知识域全部 `ACTIVE`，
  11 个权威来源校验通过，`requests=220`，V1/V2 回滚与恢复验证通过。
- 运行韧性证据：`/zoesoft/medkernel/var/evidence/current-launch/runtime-resilience.json`：
  `status=PASSED`，阶段 `RUNTIME_RESILIENCE_REHEARSAL`，禁用模型 Provider 时 readiness 诚实降级且
  `MODEL_PROVIDER` 阻断，B0 `evidenceCount=17` / `passedCount=17`，恢复后 Provider/Readiness/模型调用均恢复。
- 浏览器 E2E 证据：
  `/zoesoft/medkernel/var/evidence/current-launch/e2e/report/results.json`，
  Playwright `52 passed (17.2m)`，包含新增全角色真实视角。
- 独立全角色 E2E 复验：
  `/zoesoft/medkernel/var/evidence/current-launch/e2e-stakeholder-final/report/results.json`，
  `expected=1`，`unexpected=0`，`flaky=0`，`skipped=0`，耗时 `59583.751ms`；
  12 类视角截图与运行记录位于
  `/zoesoft/medkernel/var/evidence/current-launch/e2e-stakeholder-final/artifacts`。
- 完整覆盖审计：
  `/zoesoft/medkernel/var/evidence/current-launch/launch-coverage.json`，
  `status=PASSED`，覆盖 S0-S40 共 41 项、版本资产 13、标准患者资源 13、交付形态 5、服务组合 7、
  第三方系统族 13、组织层级 9、专病阶段 10、模型赋能界面 12、全角色视角 12。
- 发布后独立验收：
  `/zoesoft/medkernel/var/evidence/current-launch/release-acceptance.properties`：
  `release_status=PASSED`，`verified_at=2026-06-29T16:14:51+08:00`，
  `source=228b16a8d8da8eb5747af9ab1cefcc2716c0dc2d`，
  `strict_tls_verified=true`，`full_system_stage_count=8`，
  `database_restore_status=PASSED`。
  严格 TLS、八段证据结构、真实重启 readiness、登录、Provider、知识 readiness、关系库持久化、
  演练后数据库备份与隔离恢复均通过。

## 已合入 main 的 #653 代码改动

- `frontend/e2e/stakeholder-view-rehearsal.spec.ts`：
  新增 12 类真实视角复演，覆盖医生、护士、药师、医技、质控、患者/代理、平台管理员、医疗引擎运营员、
  审计员、信息科长、实施工程师、院长，验证可通过四职责账号进入真实页面并看到对应业务能力。
- `scripts/release/full-system-rehearsal-lib.mjs`：
  覆盖矩阵新增 `stakeholderViews`，并纳入全系统演练和覆盖审计。
- `scripts/release/full-system-rehearsal.test.mjs`、
  `scripts/release/launch-coverage-audit.test.mjs`：
  断言 12 类角色视角覆盖通过。
- `deploy/onprem/medkernel-post-rehearsal-verify.sh` 与
  `deploy/onprem/tests/validate-medkernel-post-rehearsal-verify.sh`：
  发布后验收同步校验 `.coverage.stakeholderViews | allPassed(12)`。
- `frontend/src/pages/clinical/CdssFatigue.tsx`：
  补齐医生确认、药师复核/DDI、医技报告解读且不改写报告的真实页面能力表达。
- `frontend/src/pages/clinical/Followup.tsx`：
  补齐护士代填、患者自填/报告回收、异常回院等随访闭环表达。
- `frontend/src/pages/compliance/AdminUsers.tsx`：
  补齐任职、登录账号、组织范围等平台管理员视角信息。
- `frontend/src/pages/compliance/AdminAudit.tsx`：
  补齐审计事件、导出证据、模型外调确认等审计员视角信息。
- `frontend/e2e/d6-graph-explore.spec.ts`、
  `frontend/e2e/pathway-graph-editor.spec.ts`、
  `frontend/e2e/product-role-journeys.spec.ts`、
  `frontend/e2e/real-frontdesk-rehearsal.spec.ts`：
  校准当前产品标签、证据详情边界和国产 Chromium 仿真下的稳定等待。

## 已合入 main 的 #653 本地验证

- 统一迁移生成：`node scripts/db/generate-migrations.mjs --check` 通过。
- 脚本门禁：
  `node --test scripts/authenticity-guard.test.mjs scripts/config-boundary-guard.test.mjs scripts/migration-convention-guard.test.mjs scripts/performance-contract-guard.test.mjs scripts/release/full-system-rehearsal.test.mjs scripts/release/launch-coverage-audit.test.mjs`
  通过；发布后验收脚本契约需单独执行
  `bash deploy/onprem/tests/validate-medkernel-post-rehearsal-verify.sh`。
- 前端：`npm --prefix frontend run verify` 通过，111 files / 867 tests passed。
- 前端构建：`npm --prefix frontend run build` 通过。
- 后端：`mvn -f medkernel-backend/pom.xml test` 通过，3042 tests，0 failures，0 errors，7 skipped
  （Docker/Testcontainers 不可用相关跳过）。
- 后端构建：`mvn -f medkernel-backend/pom.xml -Dmaven.test.skip=true clean package` 通过。
- 统一迁移、中文注释和空白检查：
  `node scripts/db/generate-migrations.mjs --check`、
  `bash scripts/check-comment-zh.sh --self-test && bash scripts/check-comment-zh.sh --mode=full`、
  `git diff --check` 均通过。

## 本次最终核查（codex/final-handoff-product-optimization）

- Git 事实核查：
  - `git fetch --prune` 成功。
  - `git status --short --branch`：`## codex/final-handoff-product-optimization`。
  - 阶段二提交后的 `git log --oneline --decorate -3`：本地分支最新为
    `2b4d854d docs: 校准接力文档与134主线映射`，下一个提交为
    `1561ba6b (origin/main, origin/HEAD, main) 完善全角色上线演练与134复演闭环 (#653)`。
  - `git branch -a`：远端仅 `origin/main` 与 `origin/HEAD -> origin/main`。
- 文档接力核查：
  - 已重读本文件和 `PRODUCT_SCOPE.md` 第 17 节 AI 接力检查。
  - 已用旧分支名、旧远端基线、旧“本地候选”标签和旧“远端未完成”措辞逐项查询本文件；
    除本条核查说明外无旧事实残留。
  - `git diff --check` 通过。
- 关键门禁：
  - 误用命令复盘：
    `node --test ... deploy/onprem/tests/validate-medkernel-post-rehearsal-verify.sh`
    会在 Node v24 下把 `.sh` 当 JavaScript 解析，并因 `set -euo pipefail` 失败；后续不要复制该旧组合命令。
  - 正确 Node 门禁：
    `node --test scripts/authenticity-guard.test.mjs scripts/config-boundary-guard.test.mjs scripts/migration-convention-guard.test.mjs scripts/performance-contract-guard.test.mjs scripts/release/full-system-rehearsal.test.mjs scripts/release/launch-coverage-audit.test.mjs`
    通过，`tests 80` / `pass 80` / `fail 0`。
  - 发布后验收脚本契约：
    `bash deploy/onprem/tests/validate-medkernel-post-rehearsal-verify.sh` 通过，
    输出 `onprem post rehearsal verification script contract passed`。
  - 真实性 inventory：
    `node scripts/authenticity-guard.mjs --mode=inventory` 通过，扫描 `2105` 个文件，未发现阻断项。
  - 配置边界 inventory：
    `node scripts/config-boundary-guard.mjs --mode=inventory` 通过，扫描 `1906` 个文件，未发现阻断项。
  - 迁移规约 inventory：
    `node scripts/migration-convention-guard.mjs --mode=inventory` 通过，扫描 `5` 个文件，未发现阻断项。
  - 中文注释：
    `bash scripts/check-comment-zh.sh --self-test` 通过，`7 pass, 0 fail`；
    `bash scripts/check-comment-zh.sh --mode=full` 通过，engine/shared 类级 Javadoc 和三方言 COMMENT 覆盖均为 `100%`。
  - 统一迁移生成：
    `node scripts/db/generate-migrations.mjs --check` 通过。

## 第二轮全角色体验优化首批落地

- 本阶段从真实前台继续收敛“同一页面服务多角色”时的客户可读语义，先选已完成 134 演练且跨角色最集中的四个入口：
  `/cdss/fatigue`、`/clinical/followup`、`/admin/users`、`/admin/audit`。
- 新增路由级 `stakeholderViews` 体验契约，字段为 `role` / `responsibility` / `boundary`；
  `PageExperienceShell` 在页面顶部渲染“角色视角”，让医生、护士、患者代理、药师、医技、平台管理员、
  实施工程师、审计员、信息科等角色的职责与边界来自路由单一真相源，而不是散落在页面文案中。
- 已登记的首批角色边界：
  - `/cdss/fatigue`：医生确认高风险提醒且不自动生成医嘱；药师复核联合用药和 DDI 且不替代医师确认；
    医技生成报告解读但不改写已签发报告。
  - `/clinical/followup`：护士代填并登记来源；患者代理回收自填问卷和报告但不直接形成诊疗决策；
    医生复核异常回院后再进入线下处置或医嘱系统。
  - `/admin/users`：平台管理员维护人员、任职、账号与组织范围；实施工程师可批量导入，但冲突未修正时不写入。
  - `/admin/audit`：审计员追溯事件、导出证据与验签结果；信息科诊断链需具备证据详情权限后展开。
- TDD 红灯证据：
  - `npm --prefix frontend test -- PageExperienceShell.test.tsx` 初次失败，页面未渲染“角色视角”。
  - `npm --prefix frontend test -- routes.test.ts` 初次失败，目标路由缺少 `stakeholderViews`。
  - 首次 `npm --prefix frontend run verify` 因 `frontend/src/shared/config/routes.ts` 格式未更新在
    `format:check` 失败；已用 Prettier 修正。
  - 第二次全量前端 verify 因 `不会改写已签发报告` 同时出现在路由角色摘要和原页面能力区，旧测试用
    `getByText` 误判重复；已改为断言至少存在一条，保留合法重复业务边界。
- 绿色验证：
  - `npm --prefix frontend test -- PageExperienceShell.test.tsx` 通过，`3` 个测试通过。
  - `npm --prefix frontend test -- routes.test.ts` 通过，`47` 个测试通过。
  - `npm --prefix frontend test -- CdssFatigue.test.tsx` 通过，`9` 个测试通过。
  - `npm --prefix frontend run verify` 通过，`111` 个测试文件、`869` 个测试全部通过。

## 调试记录

- 初始完整 E2E 红灯：`38 passed / 7 failed / 5 did not run`。
- 根因：
  - E2E 仍使用旧技术标签（`适配器标识`、`资产编码`、`节点编码`、`患者主索引 MPI`）。
  - 图谱脚本期待默认视图展示追踪号，和当前“追踪号只在证据详情中展示”的产品规则冲突。
  - 职责旅程脚本只等 `networkidle`，国产 Chromium 仿真下目标页仍处加载态。
- 修复后目标集：`20 passed / 2 failed`，剩余为真实前台值集旧标签。
- 修复值集/MPI 后，真实前台单独重跑：`2 passed (1.4m)`。
- 完整回归重跑：`52 passed (17.2m)`，随后独立全角色 E2E 再跑 `1 passed (59.6s)`。

## 下一步

1. 基于 `codex/final-handoff-product-optimization` 继续本地阶段提交；不推送远程，不直接改写 `main`。
2. 下一批产品级全视角优化建议优先看仍未纳入路由级 `stakeholderViews` 的高风险入口：
   质控、院长、医疗引擎运营员、专病管理、知识治理与数据互操作页面。
3. 每一批继续从真实前台操作发现页面分类、流程复杂度、语义误导、功能缺口和数据安全问题；
   优先把角色职责、证据边界、不可自动化医疗动作沉到路由或共享体验契约。
4. 继续保持证据详情边界：默认业务视图不暴露追踪号、原始标识、技术编码；需要时通过受控证据详情展开。
