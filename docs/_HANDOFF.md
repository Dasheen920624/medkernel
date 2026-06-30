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
- 134 的清库复演基线为 squash 前候选 `228b16a8d8da8eb5747af9ab1cefcc2716c0dc2d`，已完成全功能、全知识、
  全流程、全角色真实演练闭环；该候选内容已通过 #653 squash 合入远端 `main` 的
  `1561ba6bef8777dcef76432696f43de4277fdd3f`。之后 134 已在该清库基线上做日常更新部署至本地分支
  `8fa23c9c80fc`，用于字段契约、随访发布和真实前台复演修复验证。不要再把旧“未推送/未合并 main”、
  “134 未更新/未复演”、旧 `9d19fb3` 证据或清库基线 manifest 当作当前运行版本。

## 本轮落地结论

- 已生成候选制品并在 134 清库部署：
  - 主机：`193.112.107.134`，hostname：`VM-0-13-opencloudos`。
  - manifest：`/zoesoft/medkernel/manifest.properties`。
  - `source=228b16a8d8da8eb5747af9ab1cefcc2716c0dc2d`。
  - `commit=228b16a8d8da8eb5747af9ab1cefcc2716c0dc2d`。
  - `deployedAt=2026-06-29T15:37:16+08:00`。
  - `jarSha256=e420ffac8c3ff791ebd02913500982826e87486031d2253aef46fba54137cd0c`。
- 134 与 main 提交映射：
  - 134 清库复演时 manifest 记录的是 #653 squash 前候选提交
    `228b16a8d8da8eb5747af9ab1cefcc2716c0dc2d`。
  - 远端 `main` 当前提交为 #653 squash 后提交
    `1561ba6bef8777dcef76432696f43de4277fdd3f`。
  - 发布证据可按“134 候选部署提交 `228b16a8` 等价合入 `main@1561ba6b`”理解；
    后续若重做正式发布制品，应以新的 `main` 提交重新写入 manifest。
  - `2026-06-29T23:44:37+08:00` 后，134 当前运行 manifest 已更新为本地分支提交
    `8fa23c9c80fc23b0c72a991060fbbbfb2412c224`；该更新是基于清库环境的日常修复部署，
    不改变 #653 清库复演基线证据的归属。
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

## 134 当前运行版本补充

- `2026-06-29T22:45:48+08:00`，134 使用
  `/zoesoft/medkernel/bin/medkernel-deploy.sh` 完成日常更新部署：
  - source / commit：`11e9e38a0588`（`fix: 修复真实演练字段契约与凭据租户`）。
  - jarSha256：`c36f82b1f9e85d1345591efa81c895606aaf14efed57f1c02586e7891d2b8623`。
  - 部署前备份：`/zoesoft/medkernel/backups/deploy-20260629-224545`。
  - readiness：HTTP 200，`{"status":"UP"}`。
- 该更新修复真实前台继续演练发现的字段契约历史空白说明问题；清库数据与 #653 全系统复演证据仍沿用
  `228b16a8` 基线，不把本次日常部署伪装成重新清库。
- 部署后用 `rehearsal` 四职责账号经真实 cookie 会话直接复核
  `GET /engine/integration/data-contract`，返回 HTTP 200，字段数 `61`；不要使用 bearer token 复核该接口，
  当前登录态为 httpOnly cookie 会话。
- `2026-06-29T23:44:37+08:00`，134 继续使用本地分支提交
  `8fa23c9c80fc23b0c72a991060fbbbfb2412c224` 完成日常更新部署：
  - source / commit：`8fa23c9c80fc23b0c72a991060fbbbfb2412c224`
    （`fix: 修复随访发布生效域`）。
  - jarSha256：`8f696f6e885c44838144709e0a65574038e3e5f8d91ab74f684e8883d9cd76d3`。
  - 部署前备份：`/zoesoft/medkernel/backups/deploy-20260629-234435`。
  - readiness：HTTP 200，`{"status":"UP"}`；`systemctl is-active medkernel` 为 `active`。
- `2026-06-30T09:31:44+08:00`，134 已部署本地分支提交
  `363e5990998e1647ac3cc6b3ee932aab7d6b47d2`
  （`fix: 补齐患者360上下文生成链路`）：
  - source / commit：`363e5990998e1647ac3cc6b3ee932aab7d6b47d2`。
  - jarSha256：`e88102b67fdf6a41d0ec2a8a4f29310b150929eab0b8d2991652d05900afb1f9`。
  - 部署前备份：`/zoesoft/medkernel/backups/deploy-20260630-093142`。
  - readiness：HTTP 200，`{"status":"UP"}`。
- `2026-06-30T09:54:39+08:00`，134 已部署本地分支提交
  `02b47944237fea5df89c519bf7613fb4b6c6f5ed`
  （`fix: 随访模板发布启用机构版本`）：
  - source / commit：`02b47944237fea5df89c519bf7613fb4b6c6f5ed`。
  - jarSha256：`44a3c78b490ecd3a9478b56e7a7a20d391ed7ceab4f132f0eb86dac5e2f4db30`。
  - 部署前备份：`/zoesoft/medkernel/backups/deploy-20260630-095437`。
  - readiness：HTTP 200，`{"status":"UP"}`，服务 `active/enabled`，`NRestarts=0`。

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

## 第二轮全角色体验优化第二批落地

- 本阶段继续按真实前台和全角色视角扩展高风险入口，优先处理“职责容易混淆、医疗边界容易误解、运行状态容易被包装成成功”的页面：
  `/qc/dashboard`、`/knowledge/governance`、`/knowledge/production`、`/adapter/hub`、`/system/providers`。
- 路由级 `stakeholderViews` 已新增第二批职责边界：
  - `/qc/dashboard`：院长查看全院质量趋势和整改成效，但不直接下发临床处置或考核结论；
    质控负责人定位高风险问题并分派整改责任，整改闭环必须保留责任人、期限和复核证据。
  - `/knowledge/governance`：医疗引擎运营员审核知识候选并执行发布/驳回/替换/恢复；
    临床专家复核医学内容、适用人群和禁忌边界，专家意见进入治理记录但不绕过平台发布门禁。
  - `/knowledge/production`：医疗引擎运营员配置模型服务、医学评测和知识候选生成；
    模型安全负责人确认公网模型患者上下文外调策略和字段预览，核心敏感标识默认屏蔽，发送前需要责任确认。
  - `/adapter/hub`：信息科核查院内系统连接、字段映射、死信和健康状态；
    实施工程师完成协议联调、回放校验和上线前数据质量确认，联调通过不等于生产启用。
  - `/system/providers`：信息科核查数据库、知识图谱、模型服务和备份恢复状态；
    平台管理员确认运行保障项是否满足上线和恢复要求，不能用手工口径覆盖健康检查和恢复证据。
- TDD 红灯证据：
  - `npm --prefix frontend test -- routes.test.ts` 初次失败，
    `为高风险治理与运维页面登记全视角职责边界` 断言发现 `/qc/dashboard` 尚未登记 `stakeholderViews`。
- 绿色验证：
  - `npm --prefix frontend test -- routes.test.ts` 通过，`48` 个测试全部通过。
  - `git diff --check` 通过。
  - `npm --prefix frontend run verify` 通过，`111` 个测试文件、`870` 个测试全部通过。

## 第二轮全角色体验优化第三批落地

- 本阶段继续覆盖临床协同和运行诊断入口，优先处理日常真实前台高频操作里最容易出现“谁能看、谁能改、是否自动形成医疗动作”的边界：
  `/mpi`、`/pathway/patients`、`/workflow/todos`、`/notifications`、`/advanced/domestic`、
  `/system/runtime-diagnostics`。
- 路由级 `stakeholderViews` 已新增第三批职责边界：
  - `/mpi`：医生查阅授权范围内患者 360 与身份状态，但不处理身份合并；
    信息科复核重复身份、合并拆分和跨系统标识质量，高风险操作必须留复核理由和审计证据。
  - `/pathway/patients`：医生查看路径节点、变异原因和下一步建议，但路径建议不能自动替代医嘱或病程记录；
    护士跟进路径节点任务、随访提醒和执行状态；患者代理接收提醒和回院提示，反馈需由临床人员复核。
  - `/workflow/todos`：医生处理临床确认、会诊和复核类待办；护士接收护理执行、随访和转交任务；
    药师处理用药复核和风险提醒待办，药师意见不替代医师最终确认。
  - `/notifications`：临床使用者查看职责相关提醒，平台管理员识别账号/配置/运行通知，审计员关注导出、验签和高风险操作通知；
    通知只提示关注，不直接完成业务动作。
  - `/advanced/domestic`：信息科核查国产数据库、国密、浏览器和中间件适配状态；
    实施工程师补齐现场驱动、证书和运行参数，现场修复必须回写配置中心或部署脚本。
  - `/system/runtime-diagnostics`：信息科查看运行诊断、追踪链路和故障定位信息；
    审计员核对诊断链是否具备审计追溯和导出证据，只验证证据，不修改运行状态。
- TDD 红灯证据：
  - `npm --prefix frontend test -- routes.test.ts` 初次失败，
    `为临床协同和运行诊断入口登记全视角职责边界` 断言发现 `/mpi` 尚未登记 `stakeholderViews`。
- 绿色验证：
  - `npm --prefix frontend test -- routes.test.ts` 通过，`49` 个测试全部通过。
  - 首次 `npm --prefix frontend run verify` 暴露 `WorkflowTodos.test.tsx` 中“医生”文本查询过宽；
    根因是路由新增“角色视角”后同文案在页面说明和业务表格中同时出现，已改为定位“随访异常复核”所在表格行再断言负责人。
  - `npm --prefix frontend test -- WorkflowTodos.test.tsx` 通过，`15` 个测试全部通过。
  - `git diff --check` 通过。
  - `npm --prefix frontend run verify` 通过，`111` 个测试文件、`871` 个测试全部通过。

## 第二轮全角色体验优化第四批落地

- 本阶段继续覆盖上线配置、机构开通、知识建模和安全治理入口，优先处理院方管理员、信息科、实施工程师、临床专家、
  医疗引擎运营员、审计员和模型安全负责人共同使用时容易误解的职责边界：
  `/onboarding/guide`、`/tenant/onboarding`、`/config/releases`、`/pathway/templates`、
  `/rule/definitions`、`/terminology/mapping`、`/security/baseline`、`/security/identity-binding`、
  `/advanced/provenance`、`/advanced/graph`、`/advanced/ai-workflows`。
- 路由级 `stakeholderViews` 已新增第四批职责边界：
  - `/onboarding/guide`：实施工程师推进开通、联调、验收和交接；信息科确认网络、账号、接口、证书和备份恢复。
    未完成验收证据不能标记上线完成，现场问题必须回写配置或待处理清单。
  - `/tenant/onboarding`：平台管理员维护服务机构、组织层级、数据范围和上线状态；院方管理员核对院区、科室、岗位与启用范围。
    组织范围变更需留版本与审计，确认范围不授予超职责数据权限。
  - `/config/releases`：医疗引擎运营员发布平台标准版本并生成机构生效版本；实施工程师核对机构生效版本、灰度范围和回滚窗口。
    发布必须绑定迁移、回滚和验证证据，上线窗口外不能直接替换生产版本。
  - `/pathway/templates`：临床专家复核路径节点、变异规则和退出条件；医疗引擎运营员配置模板、机构覆盖和验证用例。
    专家确认不绕过版本发布，路径模板不能自动改写患者当前医嘱。
  - `/rule/definitions`：临床专家确认触发条件、建议动作和禁忌边界；医疗引擎运营员维护 DSL、字段条件、测试用例和灰度范围。
    高风险规则必须完成逐条责任确认，医学意见进入版本证据后再走发布。
  - `/terminology/mapping`：医疗引擎运营员确认院内码、标准码和来源系统映射；信息科核对接口字段、值域版本和上游系统变更。
    冲突映射未处理前不能进入发布链，也不在本页修改上游业务数据。
  - `/security/baseline` 与 `/security/identity-binding`：平台管理员维护安全策略和身份绑定，审计员/信息科核验证据、证书和国密一致性。
    安全策略变更必须版本校验和审计，绑定冲突未解除不能激活新身份。
  - `/advanced/provenance`、`/advanced/graph`、`/advanced/ai-workflows`：来源血缘、图谱关系和模型能力分别明确审计追溯、临床专家复核、
    模型安全负责人患者上下文外调/脱敏职责；图谱或模型不可用时诚实降级，模型结果只进入候选或辅助链路，不自动发布。
- TDD 红灯证据：
  - `npm --prefix frontend test -- routes.test.ts` 初次失败，
    `为上线配置与知识建模入口登记全视角职责边界` 断言发现 `/onboarding/guide` 尚未登记 `stakeholderViews`。
- 绿色验证：
  - `npm --prefix frontend test -- routes.test.ts` 通过，`50` 个测试全部通过。
  - `npm --prefix frontend run verify` 通过，`111` 个测试文件、`872` 个测试全部通过。

## 第二轮全角色体验优化第五批落地

- 本阶段继续覆盖剩余真实前台与交付入口，并新增全局守卫，避免后续新增认证路由遗漏角色视角：
  `/dashboard`、`/`、`/workbench/readiness-validation`、`/sandbox`、`/qc/alerts`、`/qc/insurance`、
  `/qc/eval/sets`、`/qc/eval/results`、`/knowledge/institution`、`/knowledge/diagnosis`、
  `/authoring/assets`、`/rule/validate`、`/notifications/settings`、`/embed/launch`。
- 路由级 `stakeholderViews` 已新增第五批职责边界：
  - 工作台与验收自检：临床使用者、院长、平台管理员、实施工程师、信息科分别看到本人待办、运行态势、阻塞项和
    readiness 证据；工作台不直接完成医疗处置，高风险配置仍回到对应页面确认，无权限或未连接必须诚实展示。
  - 全真体验沙盘与嵌入式终端：临床使用者/医生以真实上下文体验规则、路径和嵌入建议；信息科核查嵌入来源、
    宿主回调和访问凭证生命周期；沙盘不写生产诊疗记录，嵌入结果不直接写回医嘱。
  - 质量问题、医保审核、评价指标和评价来源：质控负责人、临床科室负责人、医保审核员、医生、数据治理人员、
    审计员分别处理整改、审核、指标配置、仿真样本和证据链；整改/审核/考核结论均需人工确认和证据闭环。
  - 机构知识、诊断知识和知识资产：医疗引擎运营员、临床专家、实施工程师分别维护机构覆盖、诊断知识、资产复用和上线适配；
    机构覆盖不改写平台标准源，诊断知识不自动生成患者诊断结论，资产库不直接发布运行版本。
  - 规则试运行和通知偏好：医生/临床专家查看规则解释并复核误报，临床使用者/平台管理员配置个人与机构通知；
    试运行不自动开嘱，静默设置不能关闭红线提醒。
- 新增守卫：
  - `routes.test.ts` 新增“为所有认证路由登记客户可读的角色视角”，扫描所有 `requireAuth=true` 路由。
  - 当前缺口扫描只剩未认证入口 `/login` 与 `/bootstrap`；所有认证路由均已登记 `stakeholderViews`。
- TDD 红灯证据：
  - `npm --prefix frontend test -- routes.test.ts` 初次失败，
    `为剩余真实前台与交付入口登记全视角职责边界` 断言发现 `/dashboard` 尚未登记 `stakeholderViews`。
  - 新增全局守卫后，`npm --prefix frontend test -- routes.test.ts` 再次失败，缺口为隐藏认证落点 `/`。
- 绿色验证：
  - `npm --prefix frontend test -- routes.test.ts` 通过，`52` 个测试全部通过。
  - `npm --prefix frontend run verify` 通过，`111` 个测试文件、`874` 个测试全部通过。
  - 缺口扫描：
    `npm exec -- tsx -e 'import { routeMetas } from "./src/shared/config/routes"; ...'`
    仅输出 `/login` 与 `/bootstrap` 两个未认证入口。

## 最终门禁核查补充

- 本轮在 `codex/final-handoff-product-optimization` 本地分支执行，不推送远程、不合并 `main`。
- 前端：
  - `npm --prefix frontend run verify` 通过，`111` 个测试文件、`871` 个测试全部通过。
  - `npm --prefix frontend run build` 通过，Vite 生产构建完成。
- 后端：
  - `mvn -f medkernel-backend/pom.xml test` 通过，`3042` 个测试统计，`0` failures、`0` errors、`7` skipped。
  - 跳过项来自当前本机未提供 Docker/Testcontainers 环境，PostgreSQL 与容器化多方言 smoke 未在本机真实执行；
    已登记到 `docs/audit/deferred-issues.md` 的 `DEFER-002`，目标环境需补真实容器或目标库 surefire 证据。
- 仓库门禁：
  - `node --test scripts/authenticity-guard.test.mjs` 通过，`51` 项。
  - `node scripts/authenticity-guard.mjs --mode=inventory` 通过，扫描 `2105` 个文件，未发现阻断项。
  - `node --test scripts/config-boundary-guard.test.mjs` 通过，`2` 项。
  - `node scripts/config-boundary-guard.mjs --mode=inventory` 通过，扫描 `1906` 个文件，未发现阻断项。
  - `node --test scripts/migration-convention-guard.test.mjs` 通过，`14` 项。
  - `node scripts/migration-convention-guard.mjs --mode=inventory` 通过，扫描 `5` 个迁移文件，未发现阻断项。
  - `node --test scripts/db/generate-migrations.test.mjs` 通过，`8` 项；`node scripts/db/generate-migrations.mjs --check` 退出码 `0`。
  - `node --test scripts/performance-contract-guard.test.mjs` 通过，`4` 项。
  - `bash scripts/check-comment-zh.sh --self-test` 通过，`7` 项。
  - `bash scripts/check-comment-zh.sh --mode=full` 通过，引擎/共享层 Javadoc 与 oracle/postgres/kingbase V1 表注释均为 `100%`。
  - `node --test scripts/release/full-system-rehearsal.test.mjs scripts/release/launch-account-bootstrap.test.mjs scripts/release/launch-coverage-audit.test.mjs scripts/release/model-provider-launch.test.mjs scripts/release/platform-baseline-bootstrap.test.mjs scripts/release/runtime-resilience-rehearsal.test.mjs scripts/knowledge/full-knowledge-rehearsal.test.mjs scripts/sandbox/scenario-rules.test.mjs scripts/sandbox/seed-scenarios.test.mjs scripts/git-scan-files.test.mjs`
    通过，`70` 项。
  - `git diff --check` 通过。
- 134 只读复核补充（`2026-06-29T22:12:42+08:00`）：
  - 只读 SSH 复核未重新部署、未清库、未写入远端；本地分支仍只做本地提交，不推送远程。
  - 服务状态：`medkernel=active`、`nginx=active`、`postgresql=active`、`medkernel=enabled`。
  - 后端真实监听端口为 `127.0.0.1:18080`；正确内部 readiness 为
    `http://127.0.0.1:18080/medkernel/actuator/health/readiness`，返回 `{"status":"UP"}`。
  - 正确严格 TLS readiness 为
    `https://127.0.0.1/medkernel/actuator/health/readiness`，使用
    `/zoesoft/medkernel/nginx/ssl/server.crt` 校验，返回 `{"status":"UP"}`。
  - 不要再把 `127.0.0.1:8080` 当作 MedKernel 后端端口；134 上 `:8080` 由 nginx/其他 server block 监听，
    无 Host 直连出现 `Empty reply from server` 是入口误用，不代表后端 down。
  - `2026-06-29T22:12:42+08:00` 只读复核时，manifest 记录候选部署提交
    `228b16a8d8da8eb5747af9ab1cefcc2716c0dc2d`；
    `2026-06-29T23:44:37+08:00` 后已更新为 `8fa23c9c80fc23b0c72a991060fbbbfb2412c224`，
    见上方“134 当前运行版本补充”。
    `deployedAt=2026-06-29T15:37:16+08:00`，`jarSha256=e420ffac8c3ff791ebd02913500982826e87486031d2253aef46fba54137cd0c`。
  - 数据库：public 表 `208`，业务表 `207`，Flyway 成功版本 `1`。
  - `full-system.json`：`status=PASSED`，`stageCount=8`，失败阶段 `0`。
  - `full-knowledge.json`：`status=PASSED`，阶段 `FULL_FUNCTION_FULL_KNOWLEDGE`，`11/11` 知识域发布，`requests=220`，
    `readinessReady=true`。
  - `runtime-resilience.json`：`status=PASSED`，`providerCode=ollama-launch`，B0 `17/17`，恢复后
    `providerStatus=HEALTHY`、`readinessReady=true`、`modelInvocationAllowed=true`。
  - `launch-coverage.json`：`status=PASSED`，`scenarios=41`、`stakeholderViews=12`、`deliveryShapes=5`、
    `versionedAssets=13`、`standardPatientResources=13`、`knowledgeDomains=11`，所有阶段通过。
  - `release-acceptance.properties`：`release_status=PASSED`、`strict_tls_verified=true`、
    `full_system_stage_count=8`、`database_restore_status=PASSED`。
  - `/zoesoft/mimoModel` 为 `root:root`、`600`、普通文件，字段名包含 `key`、`baseUrl`、`model`；密钥未写入仓库、
    未写入接力、后续回复也不得展示。公网/外部模型可以使用患者上下文，但必须走核心敏感标识屏蔽、字段预览和责任确认；
    院内/本地模型可在授权和审计边界内使用必要患者信息。

## 调试记录

- 初始完整 E2E 红灯：`38 passed / 7 failed / 5 did not run`。
- 根因：
  - E2E 仍使用旧技术标签（`适配器标识`、`资产编码`、`节点编码`、`患者主索引 MPI`）。
  - 图谱脚本期待默认视图展示追踪号，和当前“追踪号只在证据详情中展示”的产品规则冲突。
  - 职责旅程脚本只等 `networkidle`，国产 Chromium 仿真下目标页仍处加载态。
- 修复后目标集：`20 passed / 2 failed`，剩余为真实前台值集旧标签。
- 修复值集/MPI 后，真实前台单独重跑：`2 passed (1.4m)`。
- 完整回归重跑：`52 passed (17.2m)`，随后独立全角色 E2E 再跑 `1 passed (59.6s)`。
- 真实前台继续演练补充（`2026-06-29T22:43:13+08:00`）：
  - 上线 READY 凭据权威路径是 `/zoesoft/medkernel/var/credentials/current-launch.json`，当前包含
    `platform` 与 `rehearsal` 两组四职责账号；真实前台演练必须优先使用 `rehearsal` 机构账号，避免把业务数据写入平台治理租户。
    `/zoesoft/medkernel/conf/medkernel-accounts.json` 是旧汇总文件，不再作为当前前台演练登录权威。
  - 按 `rehearsal` 四职责账号重跑真实前台时，适配器由前台真实创建成功，但
    `GET /medkernel/api/v1/engine/integration/data-contract` 返回 `400`：
    机构生效运行版本字段目录存在历史空白 `description`，后端运行解析器把低风险元数据缺口升级成页面不可用。
  - 已本地修复：E2E 登录支持读取 READY 凭据中的 `rehearsal` 四职责账号；运行字段目录解析与草稿生成会把空白字段说明规范化为
    `展示名 + 字段说明`，既保护历史生效版本，又避免继续产生空白说明。
  - 本地验证通过：
    `npm --prefix frontend test -- e2eRoleCredentials.test.ts e2eAuthCredentialContract.test.ts`（`3 passed`）；
    `mvn -f medkernel-backend/pom.xml -Dtest=RuntimeReleaseFieldCatalogResolverTest,ContextFieldCatalogDraftServiceTest test`（`7 passed`）；
    `npm --prefix frontend run typecheck`；
    `git diff --check`。
  - 当时待办已完成：已构建当前分支后端包，用 134 现有 `/zoesoft/medkernel/bin/medkernel-deploy.sh` 做日常更新，
    并复核 `data-contract` 与真实前台 E2E；继续按全角色体验优化闭环处理页面交互、分类、权限和敏感信息问题。
- 真实前台基础路线复演闭环（`2026-06-29T23:05:55+08:00`）：
  - 已完成 134 日常更新部署至 `11e9e38a0588`，readiness HTTP 200；部署后直接复核
    `GET /engine/integration/data-contract` 返回 HTTP 200，字段数 `61`。
  - 真实前台 E2E 使用 `rehearsal` 四职责账号、`https://193.112.107.134/medkernel/api/v1` 后端和本地
    `http://localhost:5173` 前台代理重跑通过：
    `/tmp/medkernel-e2e-codex3/evidence-current/report/results.json`，
    `expected=1`、`unexpected=0`、`flaky=0`、`skipped=0`、耗时 `25431.015ms`。
  - 本次复演数据由前台真实操作产生，覆盖“创建系统接入适配器、创建知识值集草稿、配置模型外调安全策略、
    创建脱敏患者主索引、创建随访模板”5 段；运行记录
    `/tmp/medkernel-e2e-codex3/evidence-current/artifacts/real-frontdesk-rehearsal-全-1ad6c-、外调策略、患者资源与临床随访数据均由前台页面提交产生-chromium/attachments/real-frontdesk-runtime-records-e34ba4d525b7c7b672ceab165fff687799cbf884.json`
    显示每段 `browserErrors=0`、`serverErrors=0`、`networkFailures=0`。
  - 复演暴露并已本地修复：
    - 134 后端生产 cookie 为 `Secure`，本地 HTTP 前台代理无法直接持有会话；E2E 仅在 loopback HTTP 代理下镜像
      Secure cookie 为本地测试 cookie，生产安全策略不降级。
    - `/adapter/hub` 数据契约默认表格过技术化且长字段路径造成横向溢出；默认视图收敛为“字段名称 / 接入字段 / 接入要求”，
      字段结构、类型、单位/字典、说明进入可展开详情，避免误导实施人员先看内部编码。
    - `AdapterHub`、`Mpi`、`Followup` 改用 AntD `App.useApp()` 消除静态 message 上下文警告；
      `DeclarativeAssetWorkbench` 与 `Mpi` 弹窗表单增加受控挂载，消除未连接表单实例警告。
  - 绿色验证：
    `npm --prefix frontend test -- Followup.test.tsx Mpi.test.tsx DeclarativeAssetWorkbench.test.tsx AdapterHub.test.tsx e2eAuthCredentialContract.test.ts e2eRoleCredentials.test.ts`
    通过，`48` 项；`npm --prefix frontend run typecheck` 通过；`git diff --check` 通过。
- 全角色真实前台操作体验优化首轮闭环（`2026-06-29T23:16:17+08:00`）：
  - 已按真实前台顺序先跑通全角色页面进入与基础能力识别，再修复发现的问题；本轮仍使用 `rehearsal` 四职责账号、
    `https://193.112.107.134/medkernel/api/v1` 后端和本地 `http://localhost:5173` 前台代理，不新增远程部署。
  - 12 类角色视角 E2E 覆盖医生、护士、患者代理、药师、医技、质控、医疗引擎运营员、平台管理员、审计员、
    信息科长、实施工程师、院长；复跑结果：
    `/tmp/medkernel-e2e-codex3/evidence-stakeholder-current/report/results.json`，
    `expected=1`、`unexpected=0`、`flaky=0`、`skipped=0`、耗时 `40403.024ms`。
    运行记录
    `/tmp/medkernel-e2e-codex3/evidence-stakeholder-current/artifacts/stakeholder-view-rehearsal-b11d5-务视角均能通过四职责账号进入真实页面并看到对应业务能力-chromium/attachments/stakeholder-view-runtime-records-ec412cc1b632722b1f006a577616089694cdf2b9.json`
    显示 12 个角色段落均为 `browserErrors=0`、`serverErrors=0`、`networkFailures=0`。
  - 本轮真实复演先红后绿：医疗引擎运营员进入 `/knowledge/production` 时触发
    `GET /engine/knowledge/identities/1/candidates` 的 404。根因是生产中心复用了治理评审队列查询，
    即使 `mode=production` 也会加载“待审核候选”接口；已修正为仅在 `mode=review` 时传入身份并发起候选查询，
    生产中心保留身份选择能力但不再误打评审台接口。
  - “高级信息/证据详情”表达已统一优化为“追溯证据”：默认业务视图不显式包装成专家模式，也不把技术细节放在独立产品空间；
    展开入口用 tooltip 说明“审计追溯、原始标识和受控诊断字段”，开关 `aria-label="证据详情"` 保留给可访问性和既有自动化。
    已覆盖共享 `EvidenceDetailsToggle` 以及 `/rule/validate`、`/embed/launch`、`/pathway/templates`、
    `/rule/definitions` 四个手写入口，并新增源代码守卫防止裸露 `证据详情` 标签回退。
  - 绿色验证：
    `npm --prefix frontend test -- PageExperienceShell.test.tsx operationalControlPages.test.tsx RuleValidate.test.tsx EmbedLaunch.test.tsx PathwayTemplates.test.tsx RuleDefinitions.test.tsx KnowledgeGovernance.test.tsx`
    通过；`npm --prefix frontend run typecheck` 通过；`git diff --check` 通过。
- 真实前台深度随访链路红绿闭环（`2026-06-29T23:31:25+08:00`）：
  - 在基础五段真实前台数据路线后，继续把随访链路延伸到“创建模板草稿 → 发布模板 → 生成随访计划 → 患者/代理问卷回收 → 异常回院登记”。
  - 首次深度 E2E 红灯：临床账号在 `/clinical/followup` 可看到草稿模板的“发布模板”按钮，点击后后端按
    `followup.publish` 正确拒绝，返回 `403 权限不足`；这说明后端医疗安全边界正确，但前端交互把不可执行动作暴露给临床用户。
  - 根因与产品决策：
    - 临床使用者可以创建随访模板草稿与生成/办理计划，但不得发布模板版本。
    - 医疗引擎运营员已有 `followup.read/write/publish`，但缺 `menu.clinical-followup`，导致“有发布权限、无真实前台入口”。
    - 随访协同页面补充医疗引擎运营员视角：发布模板只形成受控模板版本，不替代临床复核，也不直接生成患者计划。
  - 已本地修复：
    - 后端默认权限策略为医疗引擎运营员补齐 `menu.clinical-followup`，并用菜单快照与有效权限测试守住边界。
    - 前端随访模板列表按 `followup.publish` 决定动作可见性；无发布权限时显示“需运营发布 / 医疗引擎运营员复核后用于新计划”，避免临床用户点击后才 403。
    - 真实前台深度 E2E 改为临床账号创建模板、运营账号发布模板、临床账号继续生成计划与办理反馈，符合职责分工。
  - 红绿证据：
    - 红灯：`npm --prefix frontend test -- Followup.test.tsx routes.test.ts` 失败 2 项；
      `mvn -f medkernel-backend/pom.xml -Dtest=DefaultPermissionPolicyTest,EffectivePermissionServiceTest test` 失败 3 项。
    - 绿灯：同两条命令重跑通过，前端 `65` 项、后端 `16` 项；`npm --prefix frontend run typecheck` 通过；`git diff --check` 通过。
  - 下一步：本地阶段提交后用该提交通过 `/zoesoft/medkernel/bin/medkernel-deploy.sh` 日常更新 134，再重跑深度真实前台 E2E。
- 真实前台深度随访发布生效域二次红绿闭环（`2026-06-29T23:48:00+08:00`）：
  - 已将上一阶段提交 `f111ff88` 部署到 134，manifest 记录
    `commit=f111ff88b2d47676abf06bda285cabcb7fb9353c`，readiness HTTP 200；
    重新跑深度真实前台 E2E 时，前五段真实前台操作均通过且无浏览器、服务端、网络错误。
  - 二次红灯发生在医疗引擎运营员发布随访模板：前端按钮与权限入口均正确，但后端返回
    `发布命令与版本生效域不一致`。
  - 根因：随访模板表中的 `organization_scope` 保存的是前台业务选项标签（如 `p5-hospital`），
    版本资产表中的 `organization_scope` 保存的是运行发布门禁使用的规范机构路径
    （如 `/t-rehearsal/REHEARSAL-HOSPITAL`）；发布命令误用模板业务标签，导致
    `VersionReleaseService` 正确拒绝不一致命令。
  - 已本地修复：
    - 后端发布命令改用关联 `AssetVersion` 的规范机构路径与适用范围，不放宽版本门禁。
    - 前端发布影响摘要不再把 `contentHash` 直接展示给运营员，改为
      `仅影响新生成随访计划：模板编码@版本`，保持业务可读。
  - 红绿证据：
    - 红灯：`npm --prefix frontend test -- Followup.test.tsx` 失败，实际发布摘要仍为技术 hash；
      `mvn -f medkernel-backend/pom.xml -Dtest=FollowupTemplateServiceTest test` 失败，发布命令目标机构仍为
      `p5-hospital`。
    - 绿灯：`npm --prefix frontend test -- Followup.test.tsx` 通过，`13` 项；
      `mvn -f medkernel-backend/pom.xml -Dtest=FollowupTemplateServiceTest test` 通过，`7` 项；
      `npm --prefix frontend test -- Followup.test.tsx routes.test.ts` 通过，`65` 项；
      `mvn -f medkernel-backend/pom.xml -Dtest=FollowupTemplateServiceTest,DefaultPermissionPolicyTest,EffectivePermissionServiceTest test`
      通过，`23` 项；`npm --prefix frontend run typecheck` 与 `git diff --check` 通过。
  - 下一步：提交本地阶段版本后部署 134，再重跑深度真实前台 E2E，确认发布、生成计划、患者/代理回收和异常回院全链路真实通过。
- 真实前台深度随访上下文入口红绿闭环（`2026-06-30T09:24:00+08:00`）：
  - 已将上一阶段提交 `8fa23c9c` 部署到 134，manifest 记录
    `commit=8fa23c9c80fc23b0c72a991060fbbbfb2412c224`，readiness HTTP 200；
    重新跑深度真实前台 E2E 时，随访模板发布已通过，新的红灯变为
    `真实前台深度演练需要至少一条已生效上下文快照`。
  - 根因：E2E 先通过前台真实创建了脱敏 MPI 患者，但旧路线仍从后台查询“已有 ACTIVE 快照”；
    真实产品缺少医生从患者 360 为新患者建立当前就诊上下文的前台入口，导致随访、路径和 CDSS 的共同前置条件无法由真实操作产生。
  - 产品决策：
    - `/mpi` 患者 360 在“暂无已生效上下文”时提供“建立当前就诊上下文”动作。
    - 表单只写入脱敏患者、当前就诊、诊断/随访病种、风险分层和建立原因；不自动开嘱，不写入姓名、证件号、电话或住址。
    - 临床使用者补齐 `context.write`，用于建立临床上下文快照；仍不授予随访模板发布等治理权限。
    - 深度真实前台 E2E 改为“前台创建 MPI 患者 → 患者 360 建立上下文快照 → 创建模板 → 运营发布 → 生成计划 → 患者/代理回收 → 异常回院登记”，不再依赖历史预置快照。
  - 红绿证据：
    - 红灯：`npm --prefix frontend test -- Mpi.test.tsx` 初次失败，患者 360 缺少上下文创建入口；
      `mvn -f medkernel-backend/pom.xml -Dtest=DefaultPermissionPolicyTest test` 初次失败，临床使用者缺少 `CONTEXT_WRITE`。
    - 绿灯：`npm --prefix frontend test -- Mpi.test.tsx` 通过，`10` 项；
      `npm --prefix frontend test -- Mpi.test.tsx Followup.test.tsx routes.test.ts` 通过，`75` 项；
      `mvn -f medkernel-backend/pom.xml -Dtest=DefaultPermissionPolicyTest test` 通过，`9` 项；
      `mvn -f medkernel-backend/pom.xml -Dtest=DefaultPermissionPolicyTest,EffectivePermissionServiceTest test` 通过，`16` 项；
      `npm --prefix frontend run typecheck`、`npx prettier --write ...`（unchanged）与 `git diff --check` 通过。
  - 下一步：提交本地阶段版本后部署 134，再重跑深度真实前台 E2E，确认新建患者、上下文快照、随访计划、患者/代理回收和异常回院全链路均由前台真实操作产生。
- 真实前台深度随访运行时启用红绿闭环（`2026-06-30T09:52:18+08:00`）：
  - 已将上一阶段提交 `363e5990` 部署到 134，manifest 记录
    `commit=363e5990998e1647ac3cc6b3ee932aab7d6b47d2`，readiness HTTP 200；
    继续跑深度真实前台 E2E 时，前七段真实操作均成功：
    创建系统接入适配器、创建知识值集草稿、配置模型外调安全策略、创建脱敏患者主索引、
    建立当前就诊上下文快照、创建随访模板、发布随访模板。
  - E2E 辅助脚本已随真实页面行为校准：
    上下文空态断言改为命中 `alert`，空选择框读取不再等待不存在的 `.ant-select-selection-item`，
    随访模板选择使用服务端搜索，生成按钮按可访问名称空白兼容匹配。
  - 最新红灯发生在生成随访计划：
    后端返回 `当前机构生效版本未启用随访模板: <templateId>`。
  - 根因：
    - `VersionReleaseService.publish` 只把统一资产版本置为 `PUBLISHED` 并记录发布计划/激活事务。
    - `FollowupEngineService` 生成计划时严格通过上下文快照锁定的 `runtimeReleaseId` 读取
      `clinical_runtime_release_item`。
    - 随访模板发布成功后没有生成下一版机构生效版本，因此 UI 文案“可用于计划生成”和运行时门禁事实不一致。
  - 产品决策：
    - 不绕过机构生效版本门禁；生成计划继续只消费当前机构生效版本锁定的 FOLLOWUP 资产。
    - 医院上下文内发布随访模板时，基于当前机构生效版本复制全部 ACTIVE 资产选择，替换/加入新 FOLLOWUP 模板，
      再调用 `ClinicalRuntimeReleaseService.activate` 生成下一版不可变机构生效版本。
    - 无医院上下文的纯资产发布仍只完成统一版本发布，避免后台治理动作隐式改变某家医院运行版本。
  - 已本地修复：
    - `FollowupTemplateService` 显式依赖当前机构生效版本解析、内容解析和机构版本激活服务。
    - 发布模板后若当前请求带医院上下文且新模板尚未在当前 release 中 ACTIVE，则生成完整 active selection：
      平台资产保留平台选择，本地/集团/医院资产保留精确 versionId，同身份旧随访模板替换为新版本。
  - 红绿证据：
    - 红灯：`mvn -f medkernel-backend/pom.xml -Dtest=FollowupTemplateServiceTest#publishTemplateActivatesCurrentHospitalRuntimeReleaseWithoutDroppingExistingAssets test`
      失败于缺少运行时启用构造/行为。
    - 绿灯：同命令重跑通过；随后
      `mvn -f medkernel-backend/pom.xml -Dtest=FollowupTemplateServiceTest,RuntimeReleaseFollowupTemplateSelectorTest,FollowupEngineServiceTest test`
      通过，`29` 项。
    - `npm --prefix frontend run typecheck`、`npx prettier --write frontend/e2e/real-frontdesk-rehearsal.spec.ts`
      （unchanged）与 `git diff --check` 通过。
  - 后续状态：本阶段已提交并部署为 `02b47944`；重跑深度真实前台 E2E 后发现旧快照锁定旧机构版本的新红灯，见下一条记录。
- 真实前台深度随访旧快照版本提示红绿闭环（`2026-06-30T10:02:00+08:00`）：
  - 已将上一阶段提交 `02b47944` 部署到 134，manifest 记录
    `commit=02b47944237fea5df89c519bf7613fb4b6c6f5ed`，readiness HTTP 200；
    继续跑深度真实前台 E2E 时，前七段真实操作均成功且无浏览器、服务端、网络错误：
    创建系统接入适配器、创建知识值集草稿、配置模型外调安全策略、创建脱敏患者主索引、
    建立当前就诊上下文快照、创建随访模板、发布随访模板。
  - 新红灯仍发生在生成随访计划，提示 `当前机构生效版本未启用随访模板`；
    这次根因不是模板发布未生成新机构版本，而是演练在发布模板前已经建立上下文快照。
    上下文快照按医疗安全要求锁定当时的 `runtimeReleaseId`，发布模板后不能被后台静默升级，否则会造成临床事实和生成依据串版。
  - 产品决策：
    - 真实前台演练顺序调整为“创建脱敏患者主索引 → 创建随访模板 → 发布模板并生成新机构生效版本 → 建立当前就诊上下文快照 → 生成随访计划”。
    - 随访生成弹窗在所选快照 `runtimeReleaseId` 与当前医院生效版本不一致时，展示“所选快照不是当前机构生效版本”，提示新发布模板不会自动套用到旧快照。
    - 不新增后台绕过接口，不让生成计划自动改用当前版本；需要新版本能力时由前台重新建立当前就诊上下文。
  - 红绿证据：
    - 红灯：`npm --prefix frontend test -- Followup.test.tsx -t '提醒旧机构生效版本快照不会自动套用新发布模板'`
      初次失败，页面未展示旧快照版本提示。
    - 绿灯：同命令重跑通过；随后 `npm --prefix frontend test -- Followup.test.tsx` 通过，`14` 项；
      `npm --prefix frontend run typecheck`、`npx prettier --write ...` 与 `git diff --check` 通过。
  - 下一步：提交本地阶段版本后，用该提交重跑 134 深度真实前台 E2E；若通过，再补记证据并继续逐角色真实操作优化。

## 下一步

1. 基于 `codex/final-handoff-product-optimization` 提交当前旧快照版本提示与真实演练顺序阶段版本；不推送远程，不直接改写 `main`。
2. 目标环境上线前补跑 `DEFER-002` 中的 Docker/Testcontainers 或目标库迁移 smoke，并保留脱敏 surefire 证据。
3. 用新本地提交重跑 134 深度真实前台 E2E；如果失败，先定位根因再优化页面、权限、数据契约或流程。
4. 路由级角色视角已覆盖所有认证路由；后续继续转向真实前台逐角色操作演练与页面交互细节优化，
   重点看操作步数、默认筛选、追溯证据开关、权限提示、患者敏感信息屏蔽和高风险确认是否仍有不顺。
5. 134 已完成字段目录修复部署、数据接入契约复核、真实前台基础路线复演和全角色页面进入首轮复演；
   下一阶段继续做逐页真实操作，不只看页面可进入，还要从医生、护士、患者/代理、药师、医技、质控、信息科、
   实施工程师、审计员、院长等视角提交真实表单、触发真实状态变化并修复流程和交互问题。
6. 每一批继续从真实前台操作发现页面分类、流程复杂度、语义误导、功能缺口和数据安全问题；
   优先把角色职责、证据边界、不可自动化医疗动作沉到路由或共享体验契约。
7. 继续保持追溯证据边界：默认业务视图不暴露追踪号、原始标识、技术编码；需要时通过受控追溯证据展开。
