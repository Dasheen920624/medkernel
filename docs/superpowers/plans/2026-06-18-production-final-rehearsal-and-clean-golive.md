# 生产全流程预演、最终清库与正式上线 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 在不冒充医学专家的前提下完成全部工程预演与问题清零，冻结候选后对 134 最终清库初始化，再由真人签署并完成 readiness、P6 和真实小样本正式上线。

**Architecture:** 采用 `ENGINEERING → REHEARSAL_READY → RELEASE_FROZEN → FRESH_DEPLOYED → AWAITING_EXPERT_SIGNOFF → READINESS_PASSED → SAMPLE_ACTIVATED → LIVE_ACCEPTED` 单向状态机。最终清库前只形成工程证据；最终清库后重新生成正式评测证据，真人签署是不可自动化的硬门，签署后才允许 provider、P6 与知识激活。

**Tech Stack:** Java 21、Spring Boot、Spring Security、Spring Data JDBC、Flyway 五方言、PostgreSQL、React、TypeScript、Vitest、Playwright、Node.js 24、Bash、systemd、Nginx。

---

## 文件与证据职责

| 路径 | 职责 |
|---|---|
| `docs/superpowers/specs/2026-06-18-production-final-rehearsal-and-clean-golive-design.md` | 上线状态机、清库纪律和证据失效规则 |
| `docs/superpowers/plans/2026-06-18-production-final-rehearsal-and-clean-golive.md` | 本长任务唯一逐项执行清单 |
| `docs/superpowers/plans/2026-06-16-autonomous-knowledge-production-golive-master-plan.md` | Phase 9–11 产品总计划和最终完成状态 |
| `docs/_HANDOFF.md` | 当前事实、最新验证、阻断和下一步 |
| `docs/audit/deferred-issues.md` | 只能由现场资源完成且不阻断当前工程工作的事项 |
| `docs/release/evidence/p9-production-golive-20260618/` | 已有 134 预演证据，只能作为预演历史 |
| `docs/release/evidence/p9-final-golive-<执行日期>/` | 最终清库、正式签署、readiness 和小样本上线证据 |
| `deploy/onprem/` | 备份、恢复、最终清库和候选发布脚本 |
| `scripts/drill/p9-t98-readiness-preflight*.mjs` | 最终写入前的只读 readiness 预检 |

### Task 1: 收口当前 Provider 受控启停切片

**Files:**
- Modify: `medkernel-backend/src/main/java/com/medkernel/engine/llm/provider/`
- Modify: `medkernel-backend/src/test/java/com/medkernel/engine/llm/provider/`
- Create: `medkernel-backend/src/main/resources/db/migration/{h2,postgres,oracle,dm,kingbase}/V152__model_provider_lock_version.sql`
- Modify: `docs/cards/wave2/LLM-08.md`
- Modify: `docs/_HANDOFF.md`

- [x] **Step 1: 等待当前后端全量测试完成**

Run:

```bash
cd medkernel-backend
MEDKERNEL_EVENTS_WORKER_ENABLED=false mvn -q clean test
```

Expected: Maven exit 0；任何失败先按根因修复并重新从 `clean test` 开始。

- [x] **Step 2: 汇总 Surefire 新鲜结果**

Run:

```bash
cd medkernel-backend
node -e 'const fs=require("fs"),p="target/surefire-reports";let t=0,f=0,e=0,s=0;for(const n of fs.readdirSync(p).filter(n=>/^TEST-.*\.xml$/.test(n))){const x=fs.readFileSync(`${p}/${n}`,"utf8"),m=x.match(/<testsuite\b[^>]*tests="(\d+)"[^>]*errors="(\d+)"[^>]*skipped="(\d+)"[^>]*failures="(\d+)"/);if(m){t+=+m[1];e+=+m[2];s+=+m[3];f+=+m[4];}}console.log(JSON.stringify({tests:t,failures:f,errors:e,skipped:s}));if(f||e)process.exit(1)'
```

Expected: `failures=0`、`errors=0`；跳过项逐一确认仅为显式环境假设。

- [x] **Step 3: 构建同源候选制品**

Run:

```bash
cd medkernel-backend
mvn -q -DskipTests package
```

Expected: exit 0，生成可执行 JAR。

- [x] **Step 4: 执行当前切片 T-GATE**

Run:

```bash
node --test scripts/authenticity-guard.test.mjs
node --test scripts/config-boundary-guard.test.mjs
node --test scripts/migration-convention-guard.test.mjs
node scripts/authenticity-guard.mjs --mode=changed
node scripts/config-boundary-guard.mjs --mode=changed
node scripts/migration-convention-guard.mjs --mode=changed
node scripts/b0-perfect-check.mjs
bash scripts/check-comment-zh.sh
node scripts/audit/export-product-capabilities.mjs --check
git diff --check
```

Expected: 全部 exit 0。

- [x] **Step 5: 自审安全边界**

Run:

```bash
rg -n "credentialRef" medkernel-backend/src/main/java/com/medkernel/engine/llm/provider
rg -n "enabled" medkernel-backend/src/main/java/com/medkernel/engine/llm/provider/ModelProviderUpsertRequest.java
rg -n "NODE_TLS_REJECT_UNAUTHORIZED|trustAll|HostnameVerifier" medkernel-backend/src
git status --short --branch
```

Expected: 治理响应 DTO 不包含凭据引用；配置 DTO 不允许直接启用；无 TLS 绕过；改动均属于本切片。

- [x] **Step 6: 更新证据并本地提交**

在 `docs/_HANDOFF.md` 写入测试总数、构建和 T-GATE 结果，在实现计划勾选完成项。

Run:

```bash
git add medkernel-backend docs
git diff --cached --check
git commit -m "feat: 增加模型Provider受控启停"
```

Expected: 本地 commit 成功；不 push、不部署 134。

### Task 2: 建立工程预演总门禁

**Files:**
- Modify: `docs/superpowers/plans/2026-06-16-autonomous-knowledge-production-golive-master-plan.md`
- Modify: `docs/_HANDOFF.md`
- Modify or Create: `scripts/drill/p9-engineering-rehearsal-check.mjs`
- Create: `scripts/drill/p9-engineering-rehearsal-check.test.mjs`

- [x] **Step 1: 写预演聚合器红测**

测试要求输入证据必须精确包含后端、前端、CLI、MCP、迁移、T-GATE、清库预演、备份恢复、provider、评测逐例证据和只读预检；缺一项或任一项非 `PASSED` 时输出 `BLOCKED`。

- [x] **Step 2: 运行红测**

Run:

```bash
node --test scripts/drill/p9-engineering-rehearsal-check.test.mjs
```

Expected: FAIL，原因是聚合器尚未实现。

- [x] **Step 3: 实现纯只读聚合器**

聚合器只读取显式 JSON 路径，输出：

```json
{
  "status": "PASSED",
  "stage": "REHEARSAL_READY",
  "checks": [],
  "failures": [],
  "containsCredentials": false,
  "containsPatientData": false
}
```

禁止发网络请求、禁止修改 134、禁止把 `PENDING_REVIEW` 记为真人签署通过。

- [x] **Step 4: 单测转绿并执行当前证据聚合**

Run:

```bash
node --test scripts/drill/p9-engineering-rehearsal-check.test.mjs
node scripts/drill/p9-engineering-rehearsal-check.mjs
```

Expected: 单测通过；当前生产证据因真人签署未完成可以显示 `REHEARSAL_READY`，但不得显示 `LIVE_ACCEPTED`。

- [x] **Step 5: 本地提交**

Run:

```bash
git add scripts/drill docs
git diff --cached --check
git commit -m "feat: 增加生产上线工程预演总门禁"
```

Expected: 本地 commit 成功；不 push。

### Task 3: 验证签署后状态机但不污染 134

**Files:**
- Modify: `medkernel-backend/src/test/java/com/medkernel/engine/llm/eval/`
- Modify: `medkernel-backend/src/test/java/com/medkernel/engine/llm/provider/`
- Modify: `medkernel-backend/src/test/java/com/medkernel/engine/knowledge/production/`

- [x] **Step 1: 写跨服务集成红测**

在隔离测试数据库中构造完整逐例评测，断言：

1. `PENDING_REVIEW` 不能启用 provider；
2. 独立、有 MFA 的治理角色签署后才变为 `PASSED`；
3. provider 启用必须精确匹配模型版本、能力和基准指纹；
4. provider 启用后 readiness 仍因 P6=false 阻断；
5. P6 只能由内置超管放行；
6. 九项全绿后才允许知识生产任务；
7. 任一版本或基准漂移会重新阻断。

- [x] **Step 2: 运行红测并确认真实缺口**

Run:

```bash
cd medkernel-backend
mvn -q -Dtest='*Model*IntegrationTest,*KnowledgeProduction*IntegrationTest' test
```

Expected: 至少一项因缺少跨服务状态机保证失败；不得为转绿而放宽生产门禁。

实际红灯：启用请求缺少 `capabilityCode`，生产门此前只按 tenant、provider、modelVersion 查找任意能力的最新已签署评测，存在跨能力误放行缺口。

- [x] **Step 3: 最小实现根因修复**

仅补状态机、事务、版本匹配、审计或错误语义缺口；不新增测试专用生产接口，不新增绕过专家签署的配置。

- [x] **Step 4: 目标测试与全量测试转绿**

Run:

```bash
cd medkernel-backend
mvn -q -Dtest='*Model*IntegrationTest,*KnowledgeProduction*IntegrationTest' test
MEDKERNEL_EVENTS_WORKER_ENABLED=false mvn -q clean test
```

Expected: 全部 exit 0。

实际验证：目标集成测试、相关 service/security 测试、`MEDKERNEL_EVENTS_WORKER_ENABLED=false mvn -q clean test` 均退出 0；全量汇总为 465 份 Surefire 报告、2999 tests、0 failures、0 errors、7 skipped。`mvn -q -DskipTests package`、38 项真实性/配置边界/迁移规约守卫自测、三项 changed 扫描、中文注释、B0、产品目录与差异门禁均退出 0。

- [x] **Step 5: 本地提交**

Run:

```bash
git add medkernel-backend docs
git diff --cached --check
git commit -m "test: 锁定知识生产正式放行状态机"
```

Expected: 本地 commit 成功；134 仍保持 provider 停用、P6=false。

### Task 4: 完整工程预演与缺陷清零

**Files:**
- Modify: 预演发现缺陷对应的代码、测试和文档
- Modify: `docs/_HANDOFF.md`
- Modify: `docs/audit/deferred-issues.md`

- [x] **Step 1: 执行后端、前端、CLI、MCP 全量门禁**

Run:

```bash
cd medkernel-backend && MEDKERNEL_EVENTS_WORKER_ENABLED=false mvn -q clean test
cd ../frontend && npm test -- --run && npm run typecheck && npm run lint && npm run format:check && npm run build
cd ../cli && npm test
cd ../mcp-server && npm test
```

Expected: 全部 exit 0、lint 零 warning、format 无漂移。

- [x] **Step 2: 执行迁移和首发空库验证**

Run:

```bash
cd medkernel-backend
mvn -q -Dtest=MigrationBaselineContractTest,H2BaselineMigrationTest,FlywayMultiDialectSmokeTest,FirstDeployEmptyPostgresSmokeTest test
```

Expected: 可用方言全部通过；缺少真实外部数据库时只允许测试内显式 assumption skip，并登记现场复核项。

- [x] **Step 3: 执行全局 T-GATE**

Run:

```bash
node scripts/b0-perfect-check.mjs
node scripts/audit/export-product-capabilities.mjs --check
node scripts/authenticity-guard.mjs
node scripts/config-boundary-guard.mjs
node scripts/migration-convention-guard.mjs
bash scripts/check-comment-zh.sh
git diff --check
```

Expected: 全部 exit 0。

- [x] **Step 4: 在 134 预演环境重新跑签署前真实链路**

保持 provider 停用、P6=false，执行健康检查、真实 provider 调用、真实医学评测和逐例证据读取，终态只能为 `PENDING_REVIEW`。

Expected: 逐例证据完整、基准当前、可复核；无自动签署、无 provider 启用、无 P6 变更、无正式候选。

- [x] **Step 5: 修复所有可在开发阶段解决的问题**

每个失败均按“复现→红测→根因→最小修复→目标测试→全量回归”闭环；现场真人身份、真实国产环境等不能由开发完成的事实写入 `docs/audit/deferred-issues.md`，但不得把工程缺陷推给现场。

- [x] **Step 6: 达成 `REHEARSAL_READY`**

Run:

```bash
node scripts/drill/p9-engineering-rehearsal-check.mjs
```

Expected: `status=PASSED`、`stage=REHEARSAL_READY`；正式上线状态仍未通过。

实际验证：后端 465 份 Surefire / 2999 tests、前端 99 文件 / 795 tests、CLI 30 tests、MCP 16 tests 及完整 build/typecheck/lint/stylelint/format 均通过；冻结前复核发现 `form-data` 生产依赖 high 漏洞和开发工具链审计债，已在 `13b69304` 升级 Vite 6.4.3、Vitest 3.2.6 等依赖并重新全量回归，生产与开发依赖审计均为 0，`DEFER-002` 关闭。H2 从空库迁移至 V152 并重复执行无变化，当前工作机 Docker socket 不可用导致 PostgreSQL / Oracle Testcontainers 按 assumption skip，已登记 `DEFER-025`。四项 on-prem 部署/发布/Ollama 合同、全局 T-GATE 和 134 既有真实备份隔离恢复证据均复核通过。新增入库脚本以请求白名单阻断 enable/disable/sign-off/P6 写入，并 fail-closed 校验明确启停位、医学安全逐例裁决与精确九闸集合；134 最终新运行 `9`、`10` 均真实 1/1、逐例证据完整、基准当前、可复核并保持 `PENDING_REVIEW`，两个 provider 全程 HEALTHY 且停用、P6=false。11 类脱敏 JSON 聚合结果为 `status=PASSED`、`stage=REHEARSAL_READY`，不代表正式上线。Task 6 检查又发现最终清库脚本未强制校验目标主机，已以 TDD 在 `c70787c9` 增加 `--expected-host` 精确匹配并在任何 root/停服/破坏性检查前拒绝缺失或错误主机；随后重跑上述完整门禁及依赖审计，结果不变且全绿。

### Task 5: 冻结最终候选

**Files:**
- Create: `docs/release/evidence/p9-final-golive-<执行日期>/release-freeze.json`
- Modify: `docs/_HANDOFF.md`

- [x] **Step 1: 确认工作树和本地提交**

Run:

```bash
git status --short --branch
git log -1 --format='%H'
```

Expected: 工作树干净；所有实现和证据已本地提交；未 push。

- [x] **Step 2: 从同一 commit 构建最终制品**

Run:

```bash
cd medkernel-backend && mvn -q clean package
cd ../frontend && npm ci && npm run build
```

Expected: exit 0。

- [x] **Step 3: 固定哈希**

Run:

```bash
shasum -a 256 medkernel-backend/target/*.jar
find frontend/dist -type f -print0 | sort -z | xargs -0 shasum -a 256
git log -1 --format='%H'
```

Expected: `release-freeze.json` 记录 commit、JAR、前端清单、最新迁移版本和生成时间，不含凭据。

- [x] **Step 4: 冻结后变更检测**

任何源码、迁移、依赖锁文件或部署脚本变化都使 `RELEASE_FROZEN` 失效，必须返回 Task 4 重新验证和 Task 5 重新构建。

实际重新冻结：新候选提交为 `95b53321c7baeb4e05e70b62834074fc59df323e`，未 push；后端同提交 `clean package` 465 份 Surefire / 2999 tests、前端 `npm ci && npm run build` 均退出 0。冻结 JAR SHA-256 为 `ead024428eb79095729565a678a6eeded83d5ac5665706e0cbfe4e26ee0c5b9a`，前端 276 个文件的仓库相对路径清单 SHA-256 为 `26a200ab214ed482f10450ff34583c3f610bc25553124c9b4cb432dff8ac1742`，五方言 760 个迁移文件清单 SHA-256 为 `33af80dc0e1c4b969076aeb3aac882997c5f8fd9c29ff92ac2cc21ebafe806a3`，最新迁移 V152。五个精确制品以 0600 权限保存在 git 忽略的 `runtime/release-freeze/95b53321c7baeb4e05e70b62834074fc59df323e/`，整包清单 SHA-256 为 `18b0ee621066dc7e12441f1b01b6743fc893c67a53b841c63c68ef477885208c`。历史候选 `1603b5a7` 已在证据中明确失效且禁止部署；当前未部署 134。

### Task 6: 最终清库前安全检查

**Files:**
- Modify: `deploy/onprem/tests/validate-medkernel-fresh-deploy.sh`
- Modify: `deploy/onprem/medkernel-fresh-deploy.sh`
- Create: `docs/release/evidence/p9-final-golive-<执行日期>/pre-clear/`

- [x] **Step 1: 验证发布脚本**

Run:

```bash
bash deploy/onprem/tests/validate-medkernel-fresh-deploy.sh
bash deploy/onprem/tests/validate-medkernel-deploy.sh
bash deploy/onprem/tests/validate-mk-publish-package.sh
bash -n deploy/onprem/medkernel-fresh-deploy.sh
bash -n deploy/onprem/mk-publish.sh
```

Expected: 全部 exit 0。

- [x] **Step 2: 生成并校验数据库备份**

在 134 生成时间戳备份，将 dump 恢复到独立临时库，校验 Flyway 版本、表数、owner 和关键计数；删除临时恢复库但保留备份和恢复日志。

Expected: `restore_verified=true`、`destructive_action_performed=false`。

- [x] **Step 3: 复核最终确认参数**

清库脚本必须同时要求目标主机、数据库名、冻结 commit 全哈希和显式 `--confirm-fresh`；任一不匹配必须在停服前退出。

Expected: 负向预检退出非零且 134 服务、数据库和旧制品未改变。

实际结果：使用候选 `95b53321c7baeb4e05e70b62834074fc59df323e` 中 SHA-256 为 `59335e94fac0d24dde925f7ff34955fbb365da5f3b2e35338104ff82ae3da7a5` 的精确全新发布脚本，在 134 依次验证缺目标主机、错目标主机、缺全新确认、错数据库名、缺来源提交和非 40 位来源提交六组负向输入，均以 exit 1 在停服前拒绝。每组前后服务 MainPID `526720`、`NRestarts=0`、运行 JAR、当时的 Flyway V151、207 张 public 基表及三条 Provider 停用状态完全一致；P6 仍为 false，一次性上传目录已删除。

按最终清库口径又删除 7 个旧备份目录，只保留已验证恢复锚点 `p9-final-preclear-20260618-214927`，dump SHA-256 仍为 `166655c57369195848a896b932cc4de5f58898ed5ed99eae61ce45d943f8831e`。V152 PostgreSQL 空库现场预检最终通过：冻结 JAR 在独立临时库两次 readiness 200，均为 V152 / 207 张 public 基表 / 152 条成功迁移，迁移清单 SHA-256 均为 `2d8fd58b8f6ee8682c4eab6077e79658f38102ef7cb48428a12ca19cc948ff30`，第二次为 no-op，临时库、制品、环境文件和 transient units 已删除。

必须保留的偏差：首次临时 unit 错误同时引用生产 EnvironmentFile 与命令行 Environment，正式数据库 URL 覆盖临时值；候选进程因正式端口占用而退出前，已把正式库从 V151 前向迁移到 V152。该迁移只为 `mk_llm_provider` 新增默认 0 的 `lock_version`，未删除数据、未替换运行制品、未重启正式服务；当前正式服务仍为 MainPID `526720`、`NRestarts=0`、readiness 200，三条 Provider 全停用、P6=false。纠正后改用唯一隔离的 0600 临时环境文件并完成上述空库实跑；完整事实见 `docs/release/evidence/p9-final-golive-20260618/pre-clear/05-postgres-empty-database-smoke.json`，不得改写为“正式库未被修改”。

### Task 7: 对 134 执行最后一次清库与全新部署

**Files:**
- Create: `docs/release/evidence/p9-final-golive-<执行日期>/fresh-deploy/`
- Modify: `docs/_HANDOFF.md`

- [ ] **Step 1: 执行受控最终清库**

使用已验证脚本执行“备份→隔离恢复确认→停服务→重建空库→清旧运行物→安装冻结候选→启动”。

Expected: 仅此步骤允许破坏性动作；日志明确 `destructive_action_performed=true`。

- [ ] **Step 2: 验证从零迁移**

Expected:

- Flyway 从空库迁移到冻结版本；
- 无历史业务数据回灌；
- 数据库 owner、schema 权限和迁移校验正确；
- 应用与 Nginx active；
- HTTP/HTTPS readiness 200；
- systemd `NRestarts=0`。

- [ ] **Step 3: 验证制品同源**

Run on local and target:

```bash
shasum -a 256 medkernel-backend/target/*.jar
```

Expected: manifest commit、候选 JAR、运行 JAR 和 `release-freeze.json` 完全一致。

- [ ] **Step 4: 验证干净正式库**

Expected: 知识生产任务、候选、评测运行、签署、获取运行和旧演练业务表均为 0；仅保留迁移 clean baseline。

- [ ] **Step 5: 标记 `FRESH_DEPLOYED`**

若任一检查失败，停止正式配置；恢复或修复后重新执行完整 Task 5–7，不在半初始化库上人工修补。

### Task 8: 重建正式配置与生成可签署评测

**Files:**
- Create: `docs/release/evidence/p9-final-golive-<执行日期>/formal-pre-signoff/`
- Modify: `docs/_HANDOFF.md`

- [ ] **Step 1: 完成首发管理员接管与 MFA**

使用正式受控账号完成首次接管、强制改密、MFA 绑定和独立重登录；凭据不写入仓库或证据。

- [ ] **Step 2: 配置正式运行前置**

配置受管资料库、`PRODUCTION_CENTER`、受控来源、provider、出域策略、能力策略和 prompt/tool/model 版本三元组。

Expected: provider 仅 HEALTHY 但保持停用；P6=false。

- [ ] **Step 3: 运行正式医学评测**

使用当前正式启用基准和精确模型版本执行评测。

Expected: 状态 `PENDING_REVIEW`；逐例证据完整、基准当前、来源引用精确、无红线失败、`reviewable=true`。

- [ ] **Step 4: 执行只读预检**

Run:

```bash
node scripts/drill/p9-t98-readiness-preflight.mjs
```

Expected: `BLOCKED`，且只因专家签署、provider 启用、版本联动和 P6 等尚未执行的正式门禁；业务写请求为 0。

- [ ] **Step 5: 标记 `AWAITING_EXPERT_SIGNOFF`**

此时开发可自动完成的工作结束，但长任务不结束；不得代签、不得提前启用 provider、不得提前开启 P6。

### Task 9: 真人独立医学专家正式签署

**Files:**
- Create: `docs/release/evidence/p9-final-golive-<执行日期>/expert-signoff/`
- Modify: `docs/_HANDOFF.md`

- [ ] **Step 1: 核验专家身份和职责分离**

Expected: 独立医学专家账号、真实姓名/岗位、MFA、授权范围和评测执行人分离均通过；证据不包含 MFA secret 或恢复码。

- [ ] **Step 2: 逐例复核**

专家在复核页核对输入、期望、模型输出、来源引用、红线结果、模型版本、基准指纹和失败原因。

- [ ] **Step 3: 留意见并签署**

Expected: 仅在全部逐例认可后状态转为 `PASSED`；拒绝或退回则保持安全关闭并返回 Task 8 重新评测。

- [ ] **Step 4: 验证签署审计**

Expected: 签署人、执行人、时间、意见、MFA 事实、基准指纹、模型版本和逐例证据关联完整，且签署后不可静默覆盖。

### Task 10: Provider、readiness 与 P6 正式放行

**Files:**
- Create: `docs/release/evidence/p9-final-golive-<执行日期>/readiness-release/`
- Modify: `docs/_HANDOFF.md`

- [ ] **Step 1: 受控启用精确匹配 Provider**

通过治理 API 提供 MFA、二次确认、原因和当前 `expectedVersion`。

Expected: 只有 HEALTHY、部署形态允许且存在当前 `PASSED` 评测的 provider 可以启用。

- [ ] **Step 2: 运行只读预检**

Expected: P6 仍为 false 时只剩 `P6_ACCEPTANCE` 阻断；其他八项全绿。

- [ ] **Step 3: 内置超管开启 P6**

内置 `SYSTEM_SUPERADMIN` 通过配置中心高危二次确认，将 `medkernel.knowledge.production.p6-independent-acceptance` 从 false 改为 true。

Expected: 乐观锁、MFA、原因、成功审计和失败审计契约均满足；禁止手工改库。

- [ ] **Step 4: 再次只读预检**

Run:

```bash
node scripts/drill/p9-t98-readiness-preflight.mjs
```

Expected: 精确九项全部 `ready=true`，聚合 `ready=true`、`modelInvocationAllowed=true`，输出 `PASSED`。

- [ ] **Step 5: 标记 `READINESS_PASSED`**

若任一模型、基准、版本或证据漂移，立即关闭 provider 和 P6，按证据失效规则返回 Task 8–9。

### Task 11: 正式低风险小样本知识闭环

**Files:**
- Create: `docs/release/evidence/p9-final-golive-<执行日期>/formal-sample/`
- Modify: `docs/_HANDOFF.md`

- [ ] **Step 1: 选择低风险正式来源与资产类型**

仅使用已审批权威来源，选择非剂量、非处方、非输血、非配型、非自动诊疗的低风险资产；记录选择依据。

- [ ] **Step 2: 运行真实自主获取**

Expected: 获取 URL、时间、SHA-256、资料 URI、解析 job 和来源许可证据完整。

- [ ] **Step 3: 生成 DRAFT 候选**

Expected: AI 标识、来源锚点、prompt/tool/model 三元组、模型模式、置信和降级证据齐全；模型输出不直接成为 ACTIVE。

- [ ] **Step 4: 执行门禁和独立审核**

Expected: 11 项门禁、8 态分流、冲突仲裁和影响分析通过；审核人与生产执行人分离。

- [ ] **Step 5: 激活并复核**

Expected: 唯一 ACTIVE、版本历史、审计链、查询可见性、影响任务和回滚入口均正常。

- [ ] **Step 6: 标记 `SAMPLE_ACTIVATED`**

任一步失败时不激活，保留真实失败和补偿证据，修复后重新运行新的样本批次。

### Task 12: 上线观察、证据归档与远程集成

**Files:**
- Create: `docs/release/evidence/p9-final-golive-<执行日期>/final-acceptance.json`
- Modify: `docs/superpowers/plans/2026-06-16-autonomous-knowledge-production-golive-master-plan.md`
- Modify: `docs/_HANDOFF.md`

- [ ] **Step 1: 完成上线观察**

检查服务健康、重启次数、错误日志、数据库连接、任务积压、死信、审计链和小样本读取；观察期间禁止再次清库。

- [ ] **Step 2: 复核备份与回滚**

生成上线后备份并验证可读；执行非破坏性的回滚预检，确认知识版本与 provider/P6 均有安全关闭路径。

- [ ] **Step 3: 最终全量门禁**

Run:

```bash
cd medkernel-backend && MEDKERNEL_EVENTS_WORKER_ENABLED=false mvn -q clean test
cd ../frontend && npm test -- --run && npm run typecheck && npm run lint && npm run format:check && npm run build
cd ../cli && npm test
cd ../mcp-server && npm test
cd ..
node scripts/b0-perfect-check.mjs
node scripts/audit/export-product-capabilities.mjs --check
git diff --check
```

Expected: 全部 exit 0。

- [ ] **Step 4: 标记 `LIVE_ACCEPTED`**

`final-acceptance.json` 必须引用最终清库、运行 manifest、真人签署、九闸、P6、小样本、审计、备份和门禁证据，且不包含凭据或患者数据。

- [ ] **Step 5: 统一远程 PR**

只有 `LIVE_ACCEPTED` 后执行：

```bash
git push -u origin codex/p6-independent-acceptance
gh pr create --base main --head codex/p6-independent-acceptance
```

PR 使用中文说明范围、验证、未完成、医疗安全、部署和迁移影响。等待 CI 全绿后 squash 合并，确认 `origin/main` 包含合并提交。

- [ ] **Step 6: 从最新 main 继续 Phase 10**

合并后删除远程功能分支，从最新 `origin/main` 新建 `codex/` 分支；134 不再清库，继续 KNOWGEN 首发知识生产与 Phase 11 GA。
