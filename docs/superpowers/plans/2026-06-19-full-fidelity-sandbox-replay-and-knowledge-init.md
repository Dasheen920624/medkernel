# 全真沙盘、现场重放与生产知识初始化 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:test-driven-development task-by-task；完成前使用 superpowers:verification-before-completion。当前会话不得把真人医学签署、Provider 放行或 P6 冒充为自动化完成。

**Goal:** 把现有沙盘升级为支持机构定制规则与平台主源规则的全真演练平台，移除场景固定配置包版本，补齐 10 条可执行演练规则，支持现场历史原样重放与新旧对比；随后建立生产知识内置初始化候选和低风险批量审核能力。

**Architecture:** 正式规则解析仍保持“机构优先、平台补充、同编码机构覆盖平台”。`CURRENT` 从演练机构唯一激活的运行绑定解析并冻结配置包版本；`HISTORICAL_EXACT` 仅按不可变重放清单装载历史资产；`COMPARE` 对同一脱敏上下文执行历史与当前两套基线。沙盘允许内部演练数据落库，但所有外部副作用强制关闭。生产知识初始化只生成有来源、有结构、可审核的候选，不直接激活；LOW 可批审，MEDIUM 单审，HIGH 双签。

**Tech Stack:** Java 21、Spring Boot、Spring Data JDBC、Flyway 五方言、PostgreSQL/H2、React、TypeScript、Vitest、Node.js 24、Python 3 标准库。

---

## 交付边界与真相源

| 路径 | 职责 |
|---|---|
| `docs/superpowers/specs/2026-06-19-full-fidelity-sandbox-replay-design.md` | 已批准架构、不变量和三种运行模式 |
| `docs/superpowers/plans/2026-06-19-full-fidelity-sandbox-replay-and-knowledge-init.md` | 本工作线唯一实施清单 |
| `docs/_HANDOFF.md` | 当前事实、验证证据、下一步和生产门禁 |
| `docs/handoff/2026-06-19-pilot-sandbox-demo.md` | 试点租户、演练账号和 134 运行接力 |
| `scripts/drill/p9-pilot-*.py` | 试点租户账号准备与只读验证工具 |
| `medkernel-backend/.../engine/sandbox/` | 沙盘基线、运行、重放与对比领域实现 |
| `scripts/sandbox/` | 十条演练规则、铺底与离线一致性验证 |
| `medkernel-backend/.../engine/knowledge/` | 生产知识候选、审核分流与激活状态机 |

## Task 1：统一接管并收口现有未提交产物

**Files:**
- Create: `scripts/drill/tests/test_p9_pilot_tools.py`
- Modify: `scripts/drill/p9-pilot-tenant-provision.py`
- Modify: `scripts/drill/p9-gen-seed-creds.py`
- Modify: `scripts/drill/p9-pilot-verify.py`
- Delete: `scripts/drill/p9-pilot-fix3.py`
- Modify: `docs/_HANDOFF.md`
- Modify: `docs/handoff/2026-06-19-pilot-sandbox-demo.md`

- [x] **Step 1: 为凭据生成、幂等账号规划和验证结果写失败测试**

覆盖：

1. 受控总表缺租户、缺账号或重复账号时失败；
2. 输出文件权限恒为 `0600`，日志不包含密码；
3. 跨租户同名角色自动使用租户前缀内部 `userId`；
4. 验证工具对租户、12 账号、角色、登录或权限任一缺失返回非零；
5. `clinical-decision-user` 必须有 `sandbox.run`，`quality-governor` 不得被误判为运行者。

Run:

```bash
python3 -m unittest discover -s scripts/drill/tests -p 'test_p9_pilot_tools.py'
```

Expected: FAIL，命中当前脚本顶层执行、不可注入路径、修复脚本非幂等和验证不阻断等缺口。

- [x] **Step 2: 将一次性脚本重构为幂等、可测试、失败关闭的 CLI**

要求：

- API、总表路径、输出路径和租户通过参数或环境变量注入；
- 先读取实际用户再决定创建/派权/改密，不因 `409` 猜测内部 `userId`；
- 账号结果按 `(tenantId, username)` 覆盖写入，不追加重复项；
- 任何阶段失败不写“完成”状态；
- 把 `p9-pilot-fix3.py` 的跨租户内部 ID 规则合并进 provisioner 后删除该脚本；
- 不打印密码、MFA、恢复码或 Cookie。

- [x] **Step 3: 测试转绿并执行静态安全扫描**

Run:

```bash
python3 -m unittest discover -s scripts/drill/tests -p 'test_p9_pilot_tools.py'
python3 -m py_compile scripts/drill/p9-pilot-tenant-provision.py scripts/drill/p9-gen-seed-creds.py scripts/drill/p9-pilot-verify.py
rg -n "print\\(.*password|print\\(.*mfa|print\\(.*recovery|平台主源零改动|2026\\.06\\.1" scripts/drill/p9-*.py
```

Expected: 测试和编译 exit 0；敏感输出与错误沙盘口径无命中。

- [x] **Step 4: 重写活接力口径**

必须写清：

- 沙盘规则归属演练机构，但运行时可使用平台主源规则；
- 不再等待固定版本或用户二选一；
- 场景模板不含固定配置包版本，运行时由机构绑定解析；
- 10 条规则将补齐为真实可执行演练资产；
- 134 已上线库不再清空，任何新代码部署后须重新生成正式评测证据，真人签署仍不可自动化。

Run:

```bash
rg -n "2026\\.06\\.1|平台主源一律不碰|平台主源零改动|待用户决策|仅.*1.*可运行|其余 9.*阻断" \
  docs/_HANDOFF.md docs/handoff/2026-06-19-pilot-sandbox-demo.md
git diff --check
```

Expected: 无旧口径命中；diff 检查通过。

## Task 2：建立 CURRENT 运行绑定与不可变运行基线

**Files:**
- Create: `medkernel-backend/src/main/resources/db/migration/{h2,postgres,oracle,dm,kingbase}/V153__sandbox_runtime_baseline.sql`
- Modify: `medkernel-backend/src/test/java/com/medkernel/migration/MigrationBaselineContractTest.java`
- Modify: `medkernel-backend/src/test/java/com/medkernel/migration/H2BaselineMigrationTest.java`
- Create: `medkernel-backend/src/main/java/com/medkernel/engine/sandbox/SandboxRunMode.java`
- Create: `medkernel-backend/src/main/java/com/medkernel/engine/sandbox/SandboxRuntimeBinding.java`
- Create: `medkernel-backend/src/main/java/com/medkernel/engine/sandbox/SandboxRuntimeBindingRepository.java`
- Create: `medkernel-backend/src/main/java/com/medkernel/engine/sandbox/SandboxRuntimeBaseline.java`
- Create: `medkernel-backend/src/main/java/com/medkernel/engine/sandbox/SandboxRuntimeBaselineResolver.java`
- Create: `medkernel-backend/src/main/java/com/medkernel/engine/sandbox/SandboxRun.java`
- Create: `medkernel-backend/src/main/java/com/medkernel/engine/sandbox/SandboxRunRepository.java`
- Create: `medkernel-backend/src/test/java/com/medkernel/engine/sandbox/SandboxRuntimeBaselineResolverTest.java`
- Create: `medkernel-backend/src/test/java/com/medkernel/engine/sandbox/SandboxRuntimeRepositoryTest.java`

- [x] **Step 1: 写五方言迁移与解析器红测**

断言：

- 每租户最多一个 `ACTIVE` 沙盘绑定；
- 绑定保存 package id/code/version、激活人、traceId 和时间；
- 运行记录冻结 mode、baselineId、解析版本、解析来源和外部副作用状态；
- 无绑定、多个激活绑定、非可运行包或跨租户包均明确失败；
- 解析器不得用“更新时间最新”替代明确绑定。

- [x] **Step 2: 运行红测**

Run:

```bash
cd medkernel-backend
mvn -q -Dtest=MigrationBaselineContractTest,H2BaselineMigrationTest,SandboxRuntimeBaselineResolverTest,SandboxRuntimeRepositoryTest test
```

Expected: FAIL，原因是 V153 和领域对象尚不存在。

- [x] **Step 3: 实现 V153、仓储和 CURRENT 解析器**

复用 `KnowledgePackageRepository` / `EffectiveKnowledgePackageResolver` 校验包归属和可运行状态；解析结果一次生成后不得在单次运行中重新查询。

- [x] **Step 4: 目标测试转绿**

Run:

```bash
cd medkernel-backend
mvn -q -Dtest=MigrationBaselineContractTest,H2BaselineMigrationTest,SandboxRuntimeBaselineResolverTest,SandboxRuntimeRepositoryTest test
```

Expected: exit 0。

## Task 3：移除场景固定版本并接入 CURRENT 全链路

**Files:**
- Modify: `medkernel-backend/src/main/java/com/medkernel/engine/sandbox/SandboxScenario.java`
- Modify: `medkernel-backend/src/main/java/com/medkernel/engine/sandbox/SandboxScenarioCatalog.java`
- Modify: `medkernel-backend/src/main/java/com/medkernel/engine/sandbox/SandboxScenarioCatalogItem.java`
- Delete: `medkernel-backend/src/main/java/com/medkernel/engine/sandbox/SandboxScenarioStatus.java`
- Modify: `medkernel-backend/src/main/java/com/medkernel/engine/sandbox/SandboxRunRequest.java`
- Modify: `medkernel-backend/src/main/java/com/medkernel/engine/sandbox/SandboxRunResponse.java`
- Modify: `medkernel-backend/src/main/java/com/medkernel/engine/sandbox/SandboxRequestFactory.java`
- Modify: `medkernel-backend/src/main/java/com/medkernel/engine/sandbox/SandboxOrchestrationService.java`
- Modify: `medkernel-backend/src/main/java/com/medkernel/engine/sandbox/SandboxScenarioController.java`
- Create: `medkernel-backend/src/main/java/com/medkernel/engine/sandbox/SandboxRuntimeBindingRequest.java`
- Create: `medkernel-backend/src/main/java/com/medkernel/engine/sandbox/SandboxRuntimeBindingService.java`
- Create: `medkernel-backend/src/main/java/com/medkernel/engine/sandbox/SandboxRuntimeStatusResponse.java`
- Modify: `medkernel-backend/src/main/java/com/medkernel/engine/pkg/EffectiveKnowledgePackageResolver.java`
- Modify: `medkernel-backend/src/test/java/com/medkernel/engine/sandbox/SandboxScenarioCatalogTest.java`
- Modify: `medkernel-backend/src/test/java/com/medkernel/engine/sandbox/SandboxOrchestrationServiceTest.java`
- Modify: `medkernel-backend/src/test/java/com/medkernel/engine/sandbox/SandboxScenarioApiContractTest.java`
- Modify: `medkernel-backend/src/test/java/com/medkernel/engine/sandbox/SandboxScenarioControllerSecurityTest.java`
- Create: `medkernel-backend/src/test/java/com/medkernel/engine/sandbox/SandboxRuntimeBindingServiceTest.java`
- Modify: `medkernel-backend/src/test/java/com/medkernel/engine/pkg/EffectiveKnowledgePackageResolverTest.java`

- [x] **Step 1: 写固定版本移除与运行冻结红测**

断言：

1. `SandboxScenario` 不再暴露 `packageVersion`；
2. 10 条规则场景不再因静态 `CLINICAL_REVIEW_REQUIRED` 被目录阻断；
3. 编排器开始时只解析一次基线，并把同一版本传给上下文、路径、随访、评估与推荐；
4. 响应包含 `runId`、`baselineId`、`mode`、`resolvedPackageVersion`、`resolutionSource`、`externalSideEffects=false`；
5. 基线缺失时在调用任何领域服务前失败并保存可审计运行记录；
6. 现有正式规则继承服务不修改。

- [x] **Step 2: 运行红测**

Run:

```bash
cd medkernel-backend
mvn -q -Dtest=SandboxScenarioCatalogTest,SandboxOrchestrationServiceTest,SandboxScenarioApiContractTest,SandboxScenarioControllerSecurityTest test
```

Expected: FAIL，命中固定常量、静态状态和缺少基线字段。

- [x] **Step 3: 最小实现 CURRENT 模式**

默认请求 mode=`CURRENT`。编排器用基线中的版本构造所有下游请求；保留真实领域服务，不复制规则判断。新增绑定读取/激活 API，写操作使用治理权限和审计，高危操作不得下放给普通沙盘运行者。

- [x] **Step 4: 目标测试转绿与固定值扫描**

Run:

```bash
cd medkernel-backend
mvn -q -Dtest=SandboxScenarioCatalogTest,SandboxOrchestrationServiceTest,SandboxScenarioApiContractTest,SandboxScenarioControllerSecurityTest test
cd ..
rg -n 'PACKAGE_VERSION = "2026\\.06\\.1"|packageVersion\\(\\)' \
  medkernel-backend/src/main/java/com/medkernel/engine/sandbox
```

Expected: 测试 exit 0；沙盘包无固定版本和场景取版本调用。

## Task 4：让前端展示运行基线和动态就绪状态

**Files:**
- Modify: `frontend/src/shared/api/hooks.ts`
- Modify: `frontend/src/shared/api/hooks.test.ts`
- Modify: `frontend/src/features/sandbox/sandboxScenarios.ts`
- Modify: `frontend/src/features/sandbox/sandboxScenarios.test.ts`
- Modify: `frontend/src/features/sandbox/SandboxDataEntry.tsx`
- Modify: `frontend/src/features/sandbox/SandboxDataEntry.test.tsx`
- Modify: `frontend/src/pages/sandbox/SandboxHost.tsx`
- Modify: `frontend/src/pages/sandbox/SandboxHost.test.tsx`
- Modify: `frontend/src/pages/sandbox/SandboxHost.module.css`

- [x] **Step 1: 写 UI 红测**

覆盖默认 CURRENT、当前绑定、解析来源、机构/平台规则来源、缺资产诚实失败、运行证据和“外部副作用已关闭”标识；移除静态“待临床评审”阻断。

- [x] **Step 2: 运行红测**

Run:

```bash
cd frontend
npm test -- --run src/shared/api/hooks.test.ts src/features/sandbox/sandboxScenarios.test.ts src/pages/sandbox/SandboxHost.test.tsx
```

Expected: FAIL。

- [x] **Step 3: 实现页面与 API 类型**

一页一目标：左侧场景，顶部基线，中央输入/运行，底部证据。普通操作者不手填 package version；缺绑定时只显示明确修复指引。

- [x] **Step 4: 测试、类型与样式门禁**

Run:

```bash
cd frontend
npm test -- --run src/shared/api/hooks.test.ts src/features/sandbox/sandboxScenarios.test.ts src/pages/sandbox/SandboxHost.test.tsx
npm run typecheck
npm run lint
npx stylelint "src/**/*.css"
```

Expected: 全部 exit 0，零 warning。

## Task 5：补齐十条机构演练规则与动态铺底

**Files:**
- Modify: `scripts/sandbox/scenario-rules.json`
- Modify: `scripts/sandbox/scenario-rules.mjs`
- Modify: `scripts/sandbox/scenario-rules.test.mjs`
- Modify: `scripts/sandbox/seed-scenarios.mjs`
- Modify: `scripts/drill/sandbox-fulltruth-run.mjs`
- Create: `docs/release/evidence/sandbox-rule-sources/README.md`

- [x] **Step 1: 为十条规则完整性写红测**

每条必须有：

- 真实 ruleCode、机构归属、触发点、动作和风险；
- 可执行 DSL/结构化条件；
- 至少命中、未命中、边界和缺字段四类测试；
- 来源类型、来源链接/文号、版本或发布日期、检索日期、适用范围；
- `clinicalContent` 和演练免责声明；
- 不含 `CLINICAL_REVIEW_REQUIRED`、空 source、空 change 或固定配置包版本。

- [x] **Step 2: 运行红测**

Run:

```bash
node --test scripts/sandbox/scenario-rules.test.mjs
```

Expected: FAIL，列出当前 9 条不完整规则。

- [x] **Step 3: 基于权威来源补齐演练资产**

医学阈值与动作必须查权威原始来源；无法从权威来源确定的内容改成机构可配置条件，不伪造全国统一阈值。医保/病历质量规则使用结构完整性与流程提醒，不做自动拒付或诊断。所有规则以 `pilot-hospital` 机构资产创建，平台主源规则只作为运行时补充，不复制。

- [x] **Step 4: seeder 改为运行时读取绑定**

铺底顺序：

1. 创建/发布十条机构规则；
2. 创建演练配置包并加入规则及外圈资产；
3. 激活沙盘运行绑定；
4. 调 readiness API 验证资产；
5. 输出独立证据目录，不污染历史 P5 证据。

脚本不再声明固定 package version；第三方或现场事件显式传入的版本仍保留为业务输入。

- [x] **Step 5: 离线测试与代码扫描**

Run:

```bash
node --test scripts/sandbox/scenario-rules.test.mjs
node --check scripts/sandbox/seed-scenarios.mjs
node --check scripts/drill/sandbox-fulltruth-run.mjs
rg -n "2026\\.06\\.1|CLINICAL_REVIEW_REQUIRED|runnableRuleCodes.*SBX\\.LAB\\.CRITICAL\\.K" \
  scripts/sandbox scripts/drill/sandbox-fulltruth-run.mjs
```

Expected: 全部 exit 0；当前沙盘脚本无旧固定版本和单规则口径。

## Task 6：实现历史原样重放清单

**Files:**
- Create: `medkernel-backend/src/main/resources/db/migration/{h2,postgres,oracle,dm,kingbase}/V154__sandbox_replay_manifest.sql`
- Create: `medkernel-backend/src/main/java/com/medkernel/engine/sandbox/replay/` 下清单、资产绑定、导入、校验和仓储类
- Modify: `medkernel-backend/src/main/java/com/medkernel/engine/sandbox/SandboxRuntimeBaselineResolver.java`
- Modify: `medkernel-backend/src/main/java/com/medkernel/engine/sandbox/SandboxOrchestrationService.java`
- Modify: `medkernel-backend/src/main/java/com/medkernel/engine/sandbox/SandboxScenarioController.java`
- Create: `medkernel-backend/src/test/java/com/medkernel/engine/sandbox/replay/` 下单元与集成测试
- Modify: `frontend/src/pages/sandbox/SandboxHost.tsx`
- Modify: `frontend/src/pages/sandbox/SandboxHost.test.tsx`

- [x] **Step 1: 写迁移、脱敏、哈希和精确版本红测**

断言：

- 导入必须有 source tenant 不可逆别名、脱敏 profile、上下文摘要、历史 package、资产版本 ID、内容 hash 和整体 manifest hash；
- 明文患者姓名/证件/电话等 D4 字段被拒绝；
- 缺任一历史资产、hash 不一致或跨租户直接引用时失败；
- 历史运行不调用当前有效规则解析器；
- 归档/下线版本只读执行，不重新激活。

- [x] **Step 2: 实现 V154 与清单导入**

导入使用标准 Record DTO + Bean Validation + 审计；清单和资产绑定不可变，只允许撤销整案，不允许原地覆盖。

- [x] **Step 3: 实现 `HISTORICAL_EXACT`**

以 `replayCaseId` 为唯一业务输入，按清单装载上下文和历史规则版本。若现有规则执行接口只能按当前规则取数，先抽取“显式版本集合评估端口”，正式 CURRENT 适配器与历史适配器共享执行内核。

- [x] **Step 4: 后端与前端目标测试转绿**

Run:

```bash
cd medkernel-backend
mvn -q -Dtest=MigrationBaselineContractTest,H2BaselineMigrationTest,'*SandboxReplay*Test',SandboxOrchestrationServiceTest test
cd ../frontend
npm test -- --run src/pages/sandbox/SandboxHost.test.tsx src/shared/api/hooks.test.ts
```

Expected: 全部 exit 0。

## Task 7：实现新旧对比模拟

**Files:**
- Create: `medkernel-backend/src/main/java/com/medkernel/engine/sandbox/compare/` 下执行与差异 DTO
- Modify: `medkernel-backend/src/main/java/com/medkernel/engine/sandbox/SandboxOrchestrationService.java`
- Modify: `medkernel-backend/src/main/java/com/medkernel/engine/sandbox/SandboxRunResponse.java`
- Create: `medkernel-backend/src/test/java/com/medkernel/engine/sandbox/compare/SandboxComparisonServiceTest.java`
- Modify: `frontend/src/pages/sandbox/SandboxHost.tsx`
- Modify: `frontend/src/pages/sandbox/SandboxHost.test.tsx`

- [x] **Step 1: 写对比红测**

覆盖新增命中、取消命中、严重度变化、动作变化、规则来源变化、版本/hash 变化、缺资产不可比较；历史 A 与当前 B 共享同一脱敏上下文，任何一侧不得触发外部副作用。

- [x] **Step 2: 实现 `COMPARE`**

对历史和当前分别构建冻结执行计划，执行后按稳定业务键比较，不按数组位置比较。

- [x] **Step 3: UI 展示差异而非原始 JSON**

默认折叠无变化项；高风险变化置顶；缺资产显示诚实原因。

- [x] **Step 4: 目标测试转绿**

Run:

```bash
cd medkernel-backend
mvn -q -Dtest=SandboxComparisonServiceTest,SandboxOrchestrationServiceTest test
cd ../frontend
npm test -- --run src/pages/sandbox/SandboxHost.test.tsx
```

Expected: 全部 exit 0。

## Task 8A：最小化补齐统一知识承载断点

**Files:**
- Modify: `medkernel-backend/src/main/java/com/medkernel/engine/factory/ProfessionalAssetTemplateRegistry.java`
- Modify: `medkernel-backend/src/main/java/com/medkernel/engine/factory/KnowgenSpecializedAssetSkeletonRegistry.java`
- Modify: `medkernel-backend/src/main/java/com/medkernel/engine/factory/KnowgenSpecializedPayloadValidator.java`
- Modify: `medkernel-backend/src/main/java/com/medkernel/engine/knowledge/production/generation/SourceCandidateGenerator.java`
- Modify: `medkernel-backend/src/main/java/com/medkernel/engine/knowledge/production/generation/CandidateGenerationOrchestrationService.java`
- Modify: `medkernel-backend/src/main/java/com/medkernel/engine/knowledge/KnowledgeVersionService.java`
- Modify: `medkernel-backend/src/main/java/com/medkernel/engine/knowledge/ReviewAssignmentRepository.java`
- Test: 对应 registry、generator、orchestration 和 version service 测试

- [x] **Step 1: 写模板覆盖与知识领域选择红测**

断言：除由专用服务装配的 `PACKAGE` 外，每个可独立生产的 `VersionedAssetType` 都有一个确定性结构模板；生成 `KNOWLEDGE` 时必须显式使用目标身份的 `KnowledgeDomain`，不得按 `null` 查模板；现有身份须从仓储加载领域，新身份使用 `NewIdentitySpec.domain`。

- [x] **Step 2: 补齐现有资产类型模板并修复生成器选择**

至少覆盖当前缺口：

- `FIELD_CATALOG`、`VALUE_SET`、`SAFETY`、`CDSS_RISK`；
- `CONDITION_FRAGMENT`、`ORDER_SET`、`ACTION_CARD`、`SUBPATHWAY`；
- 已有 `KNOWLEDGE` 领域模板的正确选择。

模板只定义结构和来源要求；不得用模型或种子编造官方编码、医学常量、单位换算、器械注册或兼容事实。

- [x] **Step 3: 写 HIGH 双签强制红测**

覆盖未分派操作者拒绝、首签保持待审、同一人员不能完成两签、全部不同分派签署完成后才激活，以及任一 RETURN/REJECT 终止候选。LOW/MEDIUM 仍须命中自己的分派。

- [x] **Step 4: 最小实现真实分派审核状态机**

复用 `mk_knowledge_review_assignment`，按 candidate classification 查询全部分派；更新当前操作者命中的待办。只有全部必需分派均 APPROVE 时调用 `activate`。不得增加第二套签署表。

- [x] **Step 5: 目标测试转绿**

Run:

```bash
cd medkernel-backend
mvn -q -Dtest=ProfessionalAssetTemplateRegistryTest,KnowgenSpecializedAssetSkeletonRegistryTest,KnowgenSpecializedPayloadValidatorTest,SourceCandidateGeneratorTest,CandidateGenerationOrchestrationServiceTest,KnowledgeVersionServiceTest,CandidateMaterializationIntegrationTest test
```

Expected: 全部 exit 0；后续医学内容可通过统一资产链生产，HIGH 候选不能单签激活。

## Task 8：生产知识内置初始化与分级审核

**Files:**
- Create: `medkernel-backend/src/main/resources/db/migration/{h2,postgres,oracle,dm,kingbase}/V155__knowledge_initialization_batch.sql`
- Create: `medkernel-backend/src/main/java/com/medkernel/engine/knowledge/production/initialization/` 下批次、清单、服务、仓储和 API
- Modify: `medkernel-backend/src/main/java/com/medkernel/engine/knowledge/production/generation/SourceCandidateGenerator.java`
- Modify: `medkernel-backend/src/main/java/com/medkernel/engine/knowledge/KnowledgeVersionService.java`
- Modify: `medkernel-backend/src/main/java/com/medkernel/engine/knowledge/ReviewAssignmentRepository.java`
- Modify: `frontend/src/pages/quality/KnowledgeGovernance.tsx`
- Modify: `frontend/src/pages/quality/KnowledgeGovernance.test.tsx`
- Create: `medkernel-backend/src/test/java/com/medkernel/engine/knowledge/production/initialization/` 下测试

- [x] **Step 1: 建立基础发行版清单与依赖拓扑红测**

初始化批次必须区分：

- `FOUNDATION`：基础知识发行版；
- `CLINICAL_CONTENT`：临床内容发行版；
- `COMPOSITE`：规则、路径、推荐、随访等组合资产。

`FOUNDATION` 至少登记以下覆盖维度：

1. 来源/许可/官方发行清单；
2. 医疗数据元与上下文字段目录；
3. 编码系统与标准术语；
4. 值集、系统状态字典和动作字典；
5. 标准单位、量纲、别名与换算；
6. 机构、科室、专科、角色、服务项目、剂型、给药途径、频次和标本等主数据；
7. 互操作字段映射与 profile 版本；
8. 语义关系、废止、替代与重定向；
9. GRADE、推荐强度和来源 A–E；
10. 依赖图、兼容版本和影响范围；
11. 权威来源、许可和官方发行 manifest；
12. 金标回归集、发行 BOM 和覆盖结果。

`CLINICAL_CONTENT` 使用现有领域与专科分类承载指南、药学、护理、报告、中医、政策、诊断、预防、联合照护和器械安全内容；`COMPOSITE` 承载条件片段、安全红线、风险矩阵、动作卡、医嘱套餐、子路径、机构参数 schema 与流程组合。二者不得反向修改基础 canonical ID。

红测断言：

- 基础批次缺任一必备域不能标记 COMPLETE；
- 选定官方发行版的导入条目数、文件数和 hash 与发行清单不一致时失败；
- 基础编码、数据元、值集成员和单位换算带 `generatedByModel=true` 时拒收；
- 有孤儿引用、循环依赖、重复 canonical ID、非法层级或量纲冲突时失败；
- 临床包不能引用未激活或版本范围不兼容的基础包。
- 六维覆盖矩阵任一必需单元没有责任资产、来源策略、审核策略或测试时不能通过首发总验收。

- [x] **Step 2: 写初始化与审核状态机红测**

断言：

1. 初始化只能从已批准来源版本生成；
2. 无模型时 B0 生成结构化骨架并明确“待编著”，不得伪造医学内容；
3. Provider/P6 未开放时不能声称生成完整医学正文；
4. 模型生成内容必须逐字段带来源锚点和内容 hash，且恒为 DRAFT/CANDIDATE；
5. LOW 才允许批量批准，批次有预览摘要、整体 hash、幂等键和原子事务；
6. MEDIUM 必须逐条确认；
7. HIGH 必须两个不同角色/人员完成会签后才可发布；
8. 任一驳回或来源漂移阻断整批激活。

- [x] **Step 3: 实现稳定 canonical ID 与语义版本策略**

要求：

- 基础包与临床包分包，临床包显式锁定基础包版本；
- canonical ID、namespace、code system URI 和 data element ID 发布后不可变；
- patch 只做兼容纠错，minor 只做兼容新增，major 才允许破坏性变化；
- 废止资产保留历史，必须声明 replacement、effectiveTo 和迁移影响；
- 基础发行版生成 coverage matrix、dependency graph、manifest hash 和 compatibility report。

- [x] **Step 4: 修复当前 HIGH 双签只路由、不强制的问题**

`reviewCandidate(APPROVE)` 先核验当前操作者是否命中待办分派；HIGH 首签只更新分派，不发布，第二个不同签署人完成后才调用发布端口。LOW/MEDIUM 也必须核验分派与职责分离。

- [x] **Step 5: 实现初始化批次与 LOW 批审**

批次保存来源集合、候选集合、风险统计、模型/模板版本、整体摘要、创建者和状态。批审 API 只接收批次 ID、预期 hash、决定和理由；服务端重新加载候选并校验，不信任前端 ID 列表。

- [x] **Step 6: 实现候选正文补全边界**

保持 `SourceCandidateGenerator` 的 B0 安全骨架；新增受控“正文补全”步骤，仅在正式知识生产 readiness 通过后调用已放行 Provider。模型输出经 schema、来源锚点、隐私、安全门和 shadow evaluation 后才进入人工审核。

- [x] **Step 7: 固化最优生产顺序**

初始化调度按依赖拓扑执行：

1. F0 来源、许可、官方清单；
2. F1 数据元、术语、编码系统、值集、系统字典、单位、主数据、互操作映射、权威来源目录；
3. F2 证据分级与冲突仲裁；
4. F3 说明书、指南、法规、制度、政策等原始事实；
5. F4 评分量表、公式、指标、参考区间、条件片段、风险矩阵等确定性构件；
6. F5 DDI、危急值、剂量、PGx、输血、急救、核心制度、适当性等高风险派生；
7. F6 路径、子路径、医嘱套餐、动作卡、推荐、随访、护理、报告、诊断、罕见病、中医、医保、公卫、患教、预防、联合照护和器械安全等组合资产；
8. F7 机构参数、本地化映射和试点覆盖；
9. F8 金标回归、六维覆盖矩阵、跨资产一致性、发行 BOM、A1–A9、红线、B0、灰度、回滚和同步。

同层可并行，不得越过未满足的依赖。基础发行版对选定官方版本必须全量，不允许以 Top-N 标记完成；Top-N 只可作为临床内容灰度批次。

- [x] **Step 8: UI 提供分风险审核**

LOW：批次预览 + 二次确认；MEDIUM：逐条；HIGH：双签进度与待签角色。禁止把批量按钮用于 MEDIUM/HIGH。

- [x] **Step 9: 目标测试转绿**

Run:

```bash
cd medkernel-backend
mvn -q -Dtest=MigrationBaselineContractTest,H2BaselineMigrationTest,KnowledgeVersionServiceTest,CandidateReviewRouterTest,'*KnowledgeInitialization*Test',SourceCandidateGeneratorTest test
cd ../frontend
npm test -- --run src/pages/quality/KnowledgeGovernance.test.tsx src/shared/api/hooks.test.ts
```

Expected: 全部 exit 0。

## Task 9：总体验证、文档同步与生产顺序冻结

**Files:**
- Modify: `docs/_HANDOFF.md`
- Modify: `docs/handoff/2026-06-19-pilot-sandbox-demo.md`
- Modify: `docs/superpowers/plans/2026-06-19-full-fidelity-sandbox-replay-and-knowledge-init.md`
- Modify: `docs/audit/deferred-issues.md`（仅记录真人/现场外部事项）

- [x] **Step 1: 后端全量验证**

Run:

```bash
cd medkernel-backend
MEDKERNEL_EVENTS_WORKER_ENABLED=false mvn -q clean test
```

Expected: exit 0。

Evidence（2026-06-19）: 本轮生成 481 份 Surefire XML，共 3085 tests、0 failures、0 errors；7 项按环境条件跳过，无崩溃或超时转储。

- [x] **Step 2: 前端全量验证**

Run:

```bash
cd frontend
npm test -- --run
npm run typecheck
npm run lint
npx stylelint "src/**/*.css"
npm run build
```

Expected: 全部 exit 0，零 warning。

Evidence（2026-06-19）: `npm run verify && npm run build` 退出 0；100 个测试文件、804 tests 全绿，lint、stylelint、格式、typecheck 与生产构建通过。

- [x] **Step 3: 脚本与 T-GATE**

Run:

```bash
python3 -m unittest discover -s scripts/drill/tests
node --test scripts/sandbox/scenario-rules.test.mjs
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

Evidence（2026-06-19）: 演练/初始化/B0 合并 109 tests、真实性/配置边界/迁移规约 38 tests、Python 演练工具 7 tests 全绿；changed 三门禁、B0、中文注释、产品目录和 `git diff --check` 均通过。

- [x] **Step 4: 运行时与文档旧口径扫描**

Run:

```bash
rg -n "2026\\.06\\.1|CLINICAL_REVIEW_REQUIRED|待用户决策|平台主源一律不碰|平台主源零改动" \
  medkernel-backend/src/main/java/com/medkernel/engine/sandbox \
  frontend/src/features/sandbox frontend/src/pages/sandbox \
  scripts/sandbox scripts/drill/sandbox-fulltruth-run.mjs \
  docs/_HANDOFF.md docs/handoff/2026-06-19-pilot-sandbox-demo.md
```

Expected: 无命中。历史 P5 演练证据脚本不在本扫描范围，保留其历史可追溯性。

Evidence（2026-06-19）: 指定范围零命中；部署发布、前向部署与 fresh-deploy 三套合同脚本均通过。

- [x] **Step 5: 冻结生产操作顺序**

文档明确：

1. 合并并部署本功能后，原正式评测证据因 JAR/迁移变化作废；
2. Provider 保持关闭、P6 保持 false；
3. 重新生成医学评测；
4. 真人独立专家逐例签署；
5. 精确启用一个已评测 Provider；
6. P6 高危放行；
7. 低风险真实小样本；
8. 生产知识初始化批次只生成候选，按 LOW/MEDIUM/HIGH 审核后逐步激活。

- [ ] **Step 6: 代码评审与本地提交**

先完成独立代码审查并修复高/中风险问题，再按逻辑单元本地提交。用户已授权完成本地提交后前向部署 134；仍不 push、不创建或合并远程 PR。

## Task 10：精确制品冻结、134 前向部署与基础候选生成

**Files:**
- Read: `deploy/onprem/mk-publish.sh`
- Read: `deploy/onprem/medkernel-deploy.sh`
- Create ignored runtime evidence: `runtime/release-freeze/<commit>/`
- Copy controlled registry: `/zoesoft/medkernel/conf/knowledge-init/foundation-authority-registry-1.0.0.json`
- Modify after runtime verification: `docs/_HANDOFF.md`
- Modify after runtime verification: `docs/handoff/2026-06-19-pilot-sandbox-demo.md`

- [ ] **Step 1: 从干净本地提交构建并冻结精确字节**

记录提交 SHA、JAR SHA-256、前端归档 SHA-256、五方言迁移清单 SHA-256；后续部署只允许使用这组冻结字节，不从文档提交后的 HEAD 临时重建。

- [ ] **Step 2: 对 134 执行不清库的前向部署**

使用现有部署器完成备份、替换、重启、健康检查和失败回滚；Flyway 只允许从 V152 前向迁移到 V155，不执行 fresh-deploy 或清库。

- [ ] **Step 3: 部署后验证运行真相**

验证 source commit、JAR hash、前端清单、Flyway V155、readiness 200、服务 active/enabled、`NRestarts`、Provider 全停用和 P6=false。部署前的模型评测证据一律视为对旧制品的历史证据，不可用于新制品放行。

- [ ] **Step 4: 受控生成稳定 B0 基础知识候选**

把权威来源注册表复制到受控只读路径；通过 SSH 隧道和临时 `0600` 凭据文件运行 `foundation-initialization.mjs`。只允许生成 8 个 `generatedByModel=false`、`PENDING_AUTHORING`、`MEDIUM` 候选和一个 F8 `IN_REVIEW` 批次，不执行批审、双签、Provider、P6 或知识激活。

- [ ] **Step 5: 部署后证据与接力收尾**

记录来源版本/片段批准、候选数、批次风险统计、批次状态、零自动激活、Provider/P6 状态和运行制品哈希；文档收尾可形成独立本地提交，但不因此重建或重部署代码制品。
