# 会话接力

> 开工先读本文件。这里只保留当前可执行事实；产品范围见
> [PRODUCT_SCOPE](PRODUCT_SCOPE.md)，历史过程使用 Git 追溯，不在当前工作树保留第二套事实源。

## 当前主线

- 基线：最新权威为 PR #650「补齐完整上线覆盖审计门禁」；更早 PR、偏离设计和旧执行计划只留在
  Git 历史，不作为当前事实入口。
- 当前分支：`codex/engine-core-golive`，本地已领先 `origin/main`；只允许本地提交，禁止推送远程、
  禁止合并 `main`。
- 当前用户约束：不使用子代理；后续对话只是补充信息时，仍按本文、CONSTITUTION、PRODUCT_SCOPE 和
  当前产品诉求继续主线，不做片面调整。
- 当前目标：完成 MedKernel 全新项目上线级整体梳理与落地，统一平台权威版本与全链路能力，移除旧兼容
  和冗余设计，完善真实功能页面与统一迁移生成，完成代码、契约、前后端、文档、测试、构建核查，并在
  134 清库重新部署完成全功能与全知识全流程演练。
- 当前阶段结论：`2de9ebf069292aa2331c649eee8dcdb95b3ddba4` 已在 134 完成清库重部署、八段全系统
  演练和发布后独立验收；全前台真实操作薄切片已在普通 Chromium 与国产 Chromium 内核仿真项目中通过。
  下一阶段必须继续做全角色、全流程、全视角真实前台体验与产品优化，不能把脚本式演练或薄切片误判为
  完整前台真实演练完成。

## 当前唯一权威

按需读取，不考古旧卡、旧计划和阶段审计：

1. [CONSTITUTION](CONSTITUTION.md)
2. [PRODUCT_SCOPE](PRODUCT_SCOPE.md)
3. [ARCHITECTURE](ARCHITECTURE.md)
4. [EXPERIENCE_CONTRACT](EXPERIENCE_CONTRACT.md)
5. [DATABASE_SCHEMA](DATABASE_SCHEMA.md)
6. [DEPLOYMENT_AND_REHEARSAL](DEPLOYMENT_AND_REHEARSAL.md)
7. [功能目录](audit/product-function-catalog.md)
8. [职责旅程](audit/product-role-journeys.md)
9. [质量基线](audit/质量基线.md)
10. [待处理问题](audit/deferred-issues.md)

## 当前产品模型

- MedKernel 是集团医疗智能中枢，不是单独规则引擎、模型平台或知识库。
- 产品按医疗引擎、知识生产、平台管理三类空间组织；空间只是分区，不裁剪真实功能。
- 客户可分配职责只有平台管理员、医疗引擎运营员、临床使用者和审计员；医生、护士、药师、医技、
  质控、医保、患者等通过业务任职、组织范围和前台场景表达。
- 系统超级管理员只用于首次接管和应急，不是客户日常四职责。
- 关系数据库是唯一权威业务事实源；图、缓存、搜索、Dify 和模型都是可关闭、可重建的投影或执行器。
- 大模型只产生候选、草稿或解释，不直接形成临床事实、机构生效版本或自动医嘱。
- 平台标准版本和机构生效版本都是不可变清单；沙盘 CURRENT 读取 `clinical_runtime_release`，
  不再维护独立 `mk_sandbox_runtime_binding`。
- 影子评测可接受状态是 `PASSED` 或 `PENDING_REVIEW` 且 `ready_for_review=true` 且无退化；
  `PENDING_REVIEW` 是医疗安全复核语义，不是失败。

## 134 当前事实

- 目标主机：`193.112.107.134`，hostname 为 `VM-0-13-opencloudos`。
- 当前运行候选：`2de9ebf069292aa2331c649eee8dcdb95b3ddba4`。
- 运行 manifest：`/zoesoft/medkernel/manifest.properties`：
  `source=2de9ebf069292aa2331c649eee8dcdb95b3ddba4`，
  `commit=2de9ebf069292aa2331c649eee8dcdb95b3ddba4`，
  `deployedAt=2026-06-25T21:11:34+08:00`，
  `jarSha256=3818c0d54dc88aa1e7f54604fb7a3503beb9a1e18408e67e54938a6554cf6f21`。
- 本地候选前端包 SHA-256：
  `2bd78d981b08a68797d9dd40edd073a46df4e39b3151e1fbdea7c30390602fb4`。
- `medkernel` 服务 active 且 enabled；严格 readiness 正确路径为
  `https://193.112.107.134/medkernel/actuator/health/readiness`，返回 `{"status":"UP"}`。
  `/medkernel/api/v1/actuator/health/readiness` 返回 401 是 API 安全边界，不是健康失败。
- 134 使用 `/zoesoft/medkernel/nginx/ssl/server.crt` 作为本轮可信 SAN 证书校验根，
  `MEDKERNEL_TLS_CA_FILE=/zoesoft/medkernel/nginx/ssl/server.crt` 下严格 TLS、SAN、有效期和 readiness 已通过。
  ACME/HTTP-01 入站不是本轮阻塞项；绑定正式公网域名时再处理公网 CA 证书。
- Node/Playwright 演练只追加 `NODE_EXTRA_CA_CERTS=/zoesoft/medkernel/nginx/ssl/server.crt`；不要把
  `SSL_CERT_FILE` 指到该自签 SAN 证书，否则会覆盖系统 CA，导致 WHO 等官方 HTTPS 来源抓取失败。
- 本轮模型提供方：`ollama-launch`，类型 `OLLAMA`，端点 `http://127.0.0.1:11434`，
  模型版本 `medkernel-qwen25:1.5b-v1`。服务器模型信息目录为 `/zoesoft/mimoModel`；不得输出、提交或记录密钥。
- 134 已安装 `google-noto-cjk-fonts` 并刷新字体缓存；这是浏览器 E2E 截图中文可读证据的环境依赖，
  后续清库或换机不能遗漏。

## 本轮已验证

- 本地修复并提交 MPI 统计投影问题：`MpiPatientRepository.GenderCount` 改为 record 投影，
  `GET /engine/mpi/stats` 能处理前台新建且性别未知/脏值的患者；`UNKNOWN` 归并逻辑有单测和集成测试。
- 临床使用者新增 `mpi.create` 权限，能创建脱敏 MPI 患者，但不能合并、拆分或执行高风险 MPI 治理动作；
  前台会隐藏无权限的高风险动作。
- 134 清库部署已成功，使用业务表数 207、Flyway 版本 1（public 表 208 = 业务 207 + Flyway 1）；
  最终清库前证据目录：
  `/zoesoft/medkernel/backups/fresh-preclear-2de9ebf06929-20260625-211120/evidence`。
- 本轮最终部署备份目录：`/zoesoft/medkernel/backups/deploy-20260625-211134`。
- 八段全系统演练已通过，当前证据目录：
  `/zoesoft/medkernel/var/evidence/current-launch`；总索引：
  `/zoesoft/medkernel/var/evidence/current-launch/full-system.json`。
- 八段包括：`account-bootstrap`、`model-provider`、`platform-baseline`、`sandbox`、`full-knowledge`、
  `runtime-resilience`、`browser-e2e`、`launch-coverage`；`full-system.json` 记录 `status=PASSED`、
  `source=2de9ebf069292aa2331c649eee8dcdb95b3ddba4`、`stageCount=8`。
- 全知识演练通过，证据：
  `/zoesoft/medkernel/var/evidence/current-launch/full-knowledge.json`；11 个知识域全部发布：
  `GUIDELINE`、`DRUG`、`PATHWAY_KNOWLEDGE`、`NURSING`、`DIAGNOSTIC_ITEM`、`TCM`、`PROTOCOL`、
  `POLICY`、`LITERATURE`、`OTHER`、`DIAGNOSIS`；观察到结构模板 12 个，版本生命周期最终 `ACTIVE`。
- 运行时韧性演练通过，证据：
  `/zoesoft/medkernel/var/evidence/current-launch/runtime-resilience.json`；B0 主链路证据 `17/17` 通过。
- 浏览器 E2E 通过，证据：
  `/zoesoft/medkernel/var/evidence/current-launch/e2e/report/results.json`；Playwright `50 passed (14.0m)`，
  `expected=50`、`unexpected=0`、`flaky=0`。
- 本轮全前台真实操作用例已经在普通 Chromium 与国产 Chromium 内核仿真项目中纳入全系统 E2E：
  平台接入、知识值集、模型外调安全策略、MPI 患者和随访模板均由前台页面提交产生；截图和运行记录位于
  `/zoesoft/medkernel/var/evidence/current-launch/e2e/artifacts/real-frontdesk-rehearsal-*`。
- 发布后独立验收已通过并写入
  `/zoesoft/medkernel/var/evidence/current-launch/release-acceptance.properties`：
  `release_status=PASSED`，`verified_at=2026-06-25T21:39:23+08:00`，
  `source=2de9ebf069292aa2331c649eee8dcdb95b3ddba4`，
  `full_system_stage_count=8`，`strict_tls_verified=true`，
  `database_restore_status=PASSED`。
- 发布后验收备份目录：
  `/zoesoft/medkernel/backups/launch-acceptance-2de9ebf06929-20260625-213915`；
  数据库备份 SHA-256：
  `7f2796d03a4b7337983c99a550f79223904aff6722334083c6359df3705b3c06`。
- 覆盖审计通过，证据：
  `/zoesoft/medkernel/var/evidence/current-launch/launch-coverage.json`；产品层、标准患者资源 13 类、
  版本资产 13 类、知识域 11 类、语义族 16 类、专科域 15 类、场景 S0-S40、交付形态、服务组合、
  三方系统族、组织层级、专病阶段和模型赋能面均为 `PASSED`。
- 本地已通过：
  `mvn -q -Dtest=MpiServiceTest,MpiServiceIntegrationTest,MpiControllerContractTest test`、
  `npm test -- Mpi.test.tsx`、`npm run typecheck`、`npm run stylelint`、`npm run build`、
  `mvn -q -DskipTests package`、`git diff --check`。
- 继续前台体验优化的本地核查已通过，并已随 `2de9ebf069292aa2331c649eee8dcdb95b3ddba4` 重新部署 134：
  `npm test` 全量 `109 passed / 778 passed`、`npm run typecheck`、`npm run lint`、
  `npm run stylelint`、`npm run build`、`npm run format:check`、`git diff --check`。
- 模型外调患者上下文预览薄片已在本地通过，并已随 `2de9ebf069292aa2331c649eee8dcdb95b3ddba4` 重新部署 134：
  `npm test -- --run src/pages/advanced/AiWorkflows.test.tsx`、`npm run typecheck`、`npm run lint`、
  `npm run stylelint`、`npm run format:check`、`npm run build`、`git diff --check`。
- 模型外调真实任务用途确认薄片已在本地通过，并已随 `2de9ebf069292aa2331c649eee8dcdb95b3ddba4` 重新部署 134：
  `npm test -- --run src/shared/api/hooks.test.ts -t "confirms high-sensitivity model egress purpose"`、
  `npm test -- --run src/pages/advanced/AiWorkflows.test.tsx -t "允许具备权限的实施人员"`、
  `npm test -- --run src/pages/advanced/AiWorkflows.test.tsx src/shared/api/hooks.test.ts`、
  `npm run typecheck`、`npm run lint`、`npm run stylelint`、`npm run format:check`、`npm run build`、
  `git diff --check`。
- 模型外调用途确认审计回看薄片已在本地通过，并已随 `2de9ebf069292aa2331c649eee8dcdb95b3ddba4` 重新部署 134：
  `mvn -q -Dtest=ModelEgressGovernanceServiceTest,ModelEgressGovernanceRepositoryTest,ModelEgressControllerSecurityTest test`、
  `mvn -q -DskipTests package`、
  `npm test -- --run src/pages/compliance/AdminAudit.test.tsx src/shared/api/hooks.test.ts`、
  `npm run typecheck`、`npm run lint`、`npm run stylelint`、`npm run format:check`、`npm run build`、
  `git diff --check`。
- 知识生产真实任务触发模型外调责任确认闭环已在本地通过，并已随
  `2de9ebf069292aa2331c649eee8dcdb95b3ddba4` 重新部署 134：
  `mvn -q -Dtest=ModelEgressControllerSecurityTest,ModelEgressGovernanceServiceTest,ModelEgressGuardTest,ModelGatewayServiceTest,ModelKnowledgeProducerTest,KnowledgeProductionControllerSecurityTest test`、
  `mvn -q -DskipTests package`、
  `npm test -- --run src/pages/quality/KnowledgeGovernance.test.tsx src/shared/api/hooks.test.ts`、
  `npm run typecheck`、`npm run lint`、`npm run stylelint`、`npm run format:check`、`npm run build`、
  `git diff --check`。
- 临床协同任务和随访协同前台体验薄片已在本地通过：协同任务页按真实待办来源、风险和到期优先级给出
  医生/护士可执行的队列摘要；随访问卷提交来源改由患者、护士或医生复核录入真实选择，不再默认假定医生提交。
  已验证：`npm test -- --run src/pages/clinical/WorkflowTodos.test.tsx src/pages/clinical/Followup.test.tsx`、
  `npm run format:check`、`npm run lint`、`npm run stylelint`、`npm run test:lint-rules`、`npm run build`。
- 证据详情语义与旧页面标题清理已在本地通过：前端源码和当前文档已无旧低频证据标签、旧随访/协同标题、
  实现侧读取状态等客户面误导词；共享开关、服务端表格门禁、审计页、运行保障页、随访页和页面烟测
  均使用“证据详情”和当前功能目录标题。已验证：`npm test` 全量 `109 passed / 786 passed`、
  `npm test -- --run src/pages/pages.smoke.test.tsx`、
  `npm test -- --run src/shared/ui/PageExperienceShell.test.tsx src/shared/ui/ServerDataTable.test.tsx src/pages/compliance/AdminAudit.test.tsx src/pages/compliance/SystemProviders.test.tsx src/pages/clinical/Followup.test.tsx`、
  `npm run typecheck`、`npm run lint`、`npm run stylelint`、`npm run format:check`、`npm run test:lint-rules`、
  `npm run build`。
- 沙盘场景目录体验清理已在本地通过：全真体验沙盘的目录未就绪、目录默认文案和缺契约阻断原因统一改成
  医院/实施可理解的场景目录语义，不再暴露“后端目录”“前端兜底”“模拟场景”或“占位”等误导词；
  默认不可用场景 id 改为 `sandbox-catalog-required`。已验证：`npm test` 全量 `109 passed / 788 passed`、
  `npm test -- --run src/features/sandbox/sandboxScenarios.test.ts src/pages/sandbox/SandboxHost.test.tsx src/features/sandbox/SandboxDataEntry.test.tsx`、
  `npm run typecheck`、`npm run lint`、`npm run stylelint`、`npm run format:check`、`npm run test:lint-rules`、
  `npm run build`。
- 退役“高级工具”概念的剩余文档、注释和测试夹具已在本地清理：现行表达改为诊断能力、知识关系、模型能力
  或证据详情；负向测试仍防止独立旧主域回流，但不再把旧词作为连续现行文本保留。已验证：
  `rg -n "高级工具" frontend/src docs --glob '!docs/_HANDOFF.md'` 无结果、
  `npm test -- --run src/shared/ui/PageExperienceShell.test.tsx src/widgets/AppLayout.test.tsx src/shared/config/menu.test.ts src/shared/config/productRoleJourneys.test.ts src/pages/pages.smoke.test.tsx src/pages/advanced/GraphExplore.test.tsx`、
  `npm run typecheck`、`npm run lint`、`npm run format:check`、`npm run build`。
- 全局治理语言与安全校验口径清理已在本地通过：客户面、当前权威文档、后端契约说明、五方言 COMMENT
  和 schema 均不再使用 `SRE`、`技术字段`、`技术降级原因`、`技术校验`、`技术安全门`、`技术评测`、
  `技术闸`、`技术门禁`、`技术门` 等旧口径；真实性门禁新增前端、当前文档、后端和数据库注释规则。
  已验证：`node --test scripts/authenticity-guard.test.mjs`、`node scripts/authenticity-guard.mjs --mode=inventory`、
  `rg -n "SRE|技术字段|技术降级原因|技术校验|技术安全门|技术评测|技术闸|技术门禁|技术门" frontend/src docs medkernel-backend/src/main/java medkernel-backend/src/main/resources/db/schema medkernel-backend/src/main/resources/db/migration --glob '!docs/_HANDOFF.md' --glob '!**/*.test.*' --glob '!**/*.stories.*'`
  无结果、`npm test -- --run` 全量 `109 passed / 788 passed`、`npm run typecheck`、`npm run lint`、
  `npm run stylelint`、`npm run format:check`、`npm run test:lint-rules`、`npm run build`、
  `bash scripts/check-comment-zh.sh --mode=full`、`mvn -q test`、`mvn -q -DskipTests package`、
  `git diff --check`；尚未重新部署 134。
- 全局实施内部口径清理已在本地通过：前端路由体验、后端契约/Javadoc、五方言 COMMENT 和 schema 均不再使用
  `技术核验`、`技术发布链`、`来源版本技术信息`、`调试接口`、`调试前`、`通道调试`、`测试 Payload`、
  `平台开发者`、`受控调试`、`技术类型` 等旧表达；真实性门禁新增共享路由配置、后端实施内部口径和数据库
  `技术发布链` 拦截。已验证：`node --test scripts/authenticity-guard.test.mjs`、
  `node scripts/authenticity-guard.mjs --mode=inventory`、
  `rg -n "调试|测试 Payload|签名测试|技术发布链|技术核验|来源版本技术信息|平台开发者|受控调试|技术类型" frontend/src medkernel-backend/src/main/java medkernel-backend/src/main/resources/db/schema medkernel-backend/src/main/resources/db/migration --glob '!**/*.test.*' --glob '!**/*.stories.*'`
  无结果、`npm test -- --run src/shared/config/routes.test.ts src/shared/config/customerLanguageGate.test.ts src/pages/operationalControlPages.test.tsx`、
  `npm run typecheck`、`npm run lint`、`npm run stylelint`、`npm run format:check`、`npm run test:lint-rules`、
  `npm run build`、`mvn -q -DskipTests package`、`bash scripts/check-comment-zh.sh --mode=full`、
  `git diff --check`；尚未重新部署 134。
- 临床提醒来源空态与 Webhook 验证口径清理已在本地通过：医生侧来源缺失时不再暴露工程自证话术，
  改为提示暂不展示来源证据并结合患者病情与院内制度复核；前端共享 API、客户标签、后端权限说明、服务契约、
  审计事件和第三方集成契约文档统一使用 Webhook 验证或连通性验证。真实性门禁新增来源空态工程话术与
  Webhook 验证旧命名防回流拦截。已验证：`node --test scripts/authenticity-guard.test.mjs`、
  `node scripts/authenticity-guard.mjs --mode=inventory`、
  `rg -n "Webhook 测试|签名测试|双向连通测试|连通测试|签名生成与双向测试|兜底伪造|页面不做兜底|不做任何兜底" frontend/src medkernel-backend/src/main/java docs medkernel-backend/src/main/resources/db/schema medkernel-backend/src/main/resources/db/migration --glob '!docs/_HANDOFF.md' --glob '!**/*.test.*' --glob '!**/*.stories.*'`
  无结果、`npm test -- --run src/pages/clinical/CdssFatigue.test.tsx src/shared/api/hooks.test.ts src/shared/config/customerLanguageGate.test.ts`、
  `npm run typecheck`、`npm run lint`、`npm run stylelint`、`npm run format:check`、`npm run test:lint-rules`、
  `npm run build`、`mvn -q -DskipTests package`、`bash scripts/check-comment-zh.sh --mode=full`、
  `git diff --check`；尚未重新部署 134。
- 领域门面 B0 主链路证据与离线评测 provider 命名已在本地通过：公开接口从 `/b0-fixtures`、
  `/{code}/b0-fixture` 统一为 `/b0-evidence`、`/{code}/b0-evidence`，DTO 字段改为 `evidenceId`
  和 `engineEvidence`；运行韧性和全系统演练汇总改为 `evidenceCount/b0EvidenceCount`；模型质量评测
  默认 provider 从 `offline-fixture` 改为 `offline-baseline`，测试基线 provider 改为 `b0-baseline`。
  真实性门禁新增领域门面 fixture 旧口径和 offline/b0 fixture provider 拦截。已验证：
  `node --test scripts/authenticity-guard.test.mjs`、`node scripts/authenticity-guard.mjs --mode=inventory`、
  `rg -n "offline-fixture|b0-fixture|b0-fixtures|fixtureCount|b0FixtureCount|DomainFacadeB0Fixture|DomainFacadeEngineFixture|fixture 证据|fixture 验证|engineFixtures|fixtureId" medkernel-backend/src/main/java medkernel-backend/src/test/java scripts/release docs/audit/product-function-catalog.md frontend/src --glob '!docs/_HANDOFF.md' --glob '!**/target/**' --glob '!**/*.test.*'`
  无结果、`node --test scripts/release/runtime-resilience-rehearsal.test.mjs scripts/release/full-system-rehearsal.test.mjs scripts/release/launch-coverage-audit.test.mjs`、
  `mvn -q -Dtest=ModelEvalServiceTest,AiQualityEvalControllerSecurityTest,DomainFacadeApiContractTest,DomainFacadeB0EvidenceServiceTest,DomainFacadeControllerSecurityTest test`、
  `mvn -q -DskipTests package`、`bash scripts/check-comment-zh.sh --mode=full`、`git diff --check`；尚未重新部署 134。
- 知识发布质量门与规则提示卡引用口径已在本地通过：前台知识审核和诊断知识发布质量门显示“影响评估”，
  保留既有 API 字段 `impactSimulationPassed`；后端默认质量门摘要和全知识演练发布理由同步为“影响评估”；
  规则引擎 actionCardRef 静态校验动作不再使用“静态校验占位”表达，改为机构生效版本物化前的引用格式校验。
  真实性门禁新增“影响模拟”旧口径和规则校验占位口径拦截。已验证：
  `npm test -- --run src/pages/quality/KnowledgeGovernance.test.tsx src/pages/quality/DiagnosisKnowledgePanel.test.tsx`、
  `mvn -q -Dtest=VersionPublishQualityGateTest,VersionReleaseServiceTest test`、
  `node --test scripts/authenticity-guard.test.mjs`、`node scripts/authenticity-guard.mjs --mode=inventory`、
  `node --test scripts/knowledge/full-knowledge-rehearsal.test.mjs`、
  `rg -n "影响模拟|静态校验占位|校验占位|临床提示卡引用静态" frontend/src medkernel-backend/src/main/java medkernel-backend/src/test/java scripts docs --glob '!docs/_HANDOFF.md' --glob '!**/target/**' --glob '!scripts/authenticity-guard.test.mjs' --glob '!scripts/authenticity-guard.mjs'`
  无结果、`npm run typecheck`、`npm run lint`、`npm run stylelint`、`npm run format:check`、
  `npm run test:lint-rules`、`npm run build`、`mvn -q -DskipTests package`、
  `bash scripts/check-comment-zh.sh --mode=full`、`git diff --check`；尚未重新部署 134。
- 权威体验契约旧口径已在本地清理：`EXPERIENCE_CONTRACT` 中“演示重构原则”改为“体验重构原则”，
  真实性门禁新增当前权威体验文档旧说法拦截。已验证：`node --test scripts/authenticity-guard.test.mjs`、
  `node scripts/authenticity-guard.mjs --mode=inventory`、
  `rg -n "演示重构原则|影响模拟|静态校验占位|fixture|b0-fixture|offline-fixture|兜底伪造|技术安全门|技术门禁|测试 Payload" docs frontend/src medkernel-backend/src/main/java scripts --glob '!docs/_HANDOFF.md' --glob '!**/target/**' --glob '!scripts/authenticity-guard.test.mjs' --glob '!scripts/authenticity-guard.mjs' --glob '!scripts/check-comment-zh/**'`
  仅命中测试自检或合法表单/临床路径语义，`git diff --check` 通过；尚未重新部署 134。
- 平台管理员工作台治理卡片前台表达已在本地清理：标题、加载、错误、空态和角色落地断言统一为
  “治理概览”，不再把内部能力术语“治理切片”暴露给平台管理员、信息科长或院长视角。已验证：
  `npm test -- --run src/widgets/WorkbenchPanel.test.tsx`、
  `rg -n "治理切片" frontend/src/widgets/WorkbenchPanel.tsx frontend/src/widgets/WorkbenchPanel.test.tsx`
  无结果；尚未重新部署 134。
- 发布治理服务契约与后端错误口径已在本地清理：服务契约、审计说明、灰度发布前置校验和影响评估服务错误
  统一使用“发布影响评估/影响评估摘要”，不再把正式发布治理步骤描述为“发布模拟”。真实性门禁新增
  发布模拟旧口径拦截。已验证：`mvn -q -Dtest=ReleaseGovernanceControllerTest,ServiceContractGovernanceTest test`、
  `node --test scripts/authenticity-guard.test.mjs`、`node scripts/authenticity-guard.mjs --mode=inventory`、
  `rg -n "发布模拟|模拟摘要|模拟参数|模拟证据" frontend/src medkernel-backend/src/main/java medkernel-backend/src/test/java scripts docs --glob '!docs/_HANDOFF.md' --glob '!**/target/**' --glob '!scripts/authenticity-guard.test.mjs' --glob '!scripts/authenticity-guard.mjs'`
  无结果；尚未重新部署 134。
- 工作台通用空态已在本地清理：可读来源无数据时提示“确认组织范围或进入对应页面处理”，不再承诺“后续来源上线”
  或“自动回灌”；真实性门禁新增前端未上线承诺旧文案拦截。已验证：
  `npm test -- --run src/widgets/WorkbenchPanel.test.tsx`、`node --test scripts/authenticity-guard.test.mjs`、
  `node scripts/authenticity-guard.mjs --mode=inventory`；尚未重新部署 134。
- 通知偏好和共享导出能力状态已在本地清理：前台错误、禁用和设置失败提示统一指向通知设置、信息科确认
  导出范围或导出配置，不再向医生、护士、审计员或实施人员暴露实现层接入状态；真实性门禁新增共享 UI
  客户面实现层旧口径拦截。已验证：
  `npm test -- --run src/pages/clinical/Notifications.test.tsx src/shared/ui/AsyncExportAction.test.tsx`、
  `node --test scripts/authenticity-guard.test.mjs`、`node scripts/authenticity-guard.mjs --mode=inventory`、
  前端通知和导出能力旧接口口径组合搜索无结果、`npm run typecheck`、`npm run lint`、`npm run stylelint`、`npm run format:check`、
  `npm run test:lint-rules`、`npm run build`；尚未重新部署 134。
- 客户面“接口/接入”实现层口径已按全角色体验继续清理：临床消息、协同任务、随访统计、质量概览、
  实施与服务机构、字段目录、路径/规则配置、国产化核验、运行保障、诊断工具、沙盘嵌入、模型生产方式和
  路由体验元数据统一改为服务状态、服务目录、服务契约、服务对接或来源状态，不再让医生、护士、患者随访、
  信息科、实施、审计员或院长看到实现层命名；真实性门禁新增这组旧口径拦截。已验证：
  `node --test scripts/authenticity-guard.test.mjs`、`node scripts/authenticity-guard.mjs --mode=inventory`、
  客户面服务状态旧口径组合搜索无结果、
  `npm test -- --run src/pages/clinical/Notifications.test.tsx src/pages/clinical/WorkflowTodos.test.tsx src/pages/clinical/Followup.test.tsx src/pages/quality/QcDashboard.test.tsx src/pages/quality/KnowledgeGovernance.test.tsx src/pages/tenant/ImplementationGuide.test.tsx src/pages/tenant/TenantOnboarding.test.tsx src/pages/tenant/RuleDefinitions.test.tsx src/pages/tenant/PathwayTemplates.test.tsx src/features/sandbox/SandboxEmbedFrame.test.tsx src/shared/ui/condition/FieldCatalogManager.test.tsx src/pages/operationalControlPages.test.tsx src/shared/config/routes.test.ts`
  13 个文件 / 172 个用例通过、`npm run typecheck`、`npm run lint`、`npm run stylelint`、
  `npm run format:check`、`npm run test:lint-rules`、`npm run build`；尚未重新部署 134。
- 统一身份登录和身份来源待配置口径已在本地通过：登录页、页面烟测、身份来源测试夹具、共享 API 测试、
  路由测试、后端委托登录状态和错误契约统一使用“统一身份服务待配置 / 身份来源待配置”，不再保留
  统一身份未配置类旧话术、IdP 实现侧旧命名或实现层返回说明等误导性旧口径；
  登录页提供方待配置状态只在当前登录上下文显示“待配置”，未改变全局 `NOT_CONNECTED` 来源状态标签。
  真实性门禁新增统一身份旧口径拦截。已验证：`mvn -q -Dtest=AuthControllerTest test`、
  `npm test -- --run src/pages/Login.test.tsx src/pages/pages.smoke.test.tsx src/pages/operationalControlPages.test.tsx src/shared/api/hooks.test.ts src/app/router.test.tsx`、
  `node --test scripts/authenticity-guard.test.mjs`、`node scripts/authenticity-guard.mjs --mode=inventory`、
  统一身份旧口径组合搜索无结果、`npm run typecheck`、`npm run lint`、`npm run stylelint`、`npm run format:check`、
  `npm run test:lint-rules`、`npm run build`、`mvn -q -DskipTests package`、
  `bash scripts/check-comment-zh.sh --mode=full`、`git diff --check`；尚未重新部署 134。
- 工作台第一屏多角色体验已在本地继续清理：临床使用者、医疗引擎运营员、平台管理员看到的是可执行页面入口、
  真实数据来源位置和知识同步配置状态，不再用自证式聚合说明、未来 API 承诺或笼统配置标签解释当前能力。
  真实性门禁新增工作台假闭环和聚合占位旧口径拦截。已验证：
  `npm test -- --run src/widgets/WorkbenchPanel.test.tsx src/pages/pages.smoke.test.tsx`、
  `npm run typecheck`、`npm run lint`、`npm run stylelint`、`npm run format:check`、`npm run build`、
  `node --test scripts/authenticity-guard.test.mjs`、`node scripts/authenticity-guard.mjs --mode=inventory`、
  工作台第一屏旧口径组合搜索无结果；尚未重新部署 134。
- 全局客户面实现层口径已继续清理：退出会话、临床路径变异、临床提醒闭环、质量问题、评价结果、医保审核、
  审计验签、安全基线、实施接入、模型外调、路由证据和相关测试说明统一改为平台、服务、来源事实、
  数据脱敏、服务契约或安全策略语义，不再让医生、护士、患者随访、信息科、实施、审计员或院长看到实现层
  后台命名。真实性门禁新增这组客户面实现层旧句式拦截。已验证：
  `npm test -- --run` 全量 `109 passed / 791 passed`、
  `npm test -- --run src/widgets/AppLayout.test.tsx src/pages/clinical/PatientPathways.test.tsx src/pages/clinical/CdssFatigue.test.tsx src/pages/quality/QcAlerts.test.tsx src/pages/quality/QcEvalResults.test.tsx src/pages/quality/InsuranceAudit.test.tsx src/pages/compliance/AdminAudit.test.tsx src/pages/compliance/SecurityBaseline.test.tsx src/pages/tenant/ImplementationGuide.test.tsx src/pages/tenant/AdapterHub.test.tsx src/pages/advanced/AiWorkflows.test.tsx src/pages/pages.smoke.test.tsx src/pages/operationalControlPages.test.tsx src/pages/clinical/Mpi.test.tsx src/pages/compliance/NotificationSettings.test.tsx src/shared/api/hooks.test.ts`、
  `npm run typecheck`、`npm run lint`、`npm run stylelint`、`npm run format:check`、`npm run test:lint-rules`、
  `npm run build`、`node --test scripts/authenticity-guard.test.mjs`、
  `node scripts/authenticity-guard.mjs --mode=inventory`、客户面实现层旧句式组合搜索无结果；尚未重新部署 134。
- 前端测试说明、共享注释和开发代理提示中的实现层旧口径已继续清理：测试名、夹具说明、API 注释、
  dev proxy 提示和浏览器兼容性报告统一改为平台 API、服务端、服务契约、服务报告或真实任务状态；仅保留
  后端源码路径、路由/菜单快照契约和负向断言中的必要命名。已验证：
  `npm test -- --run src/widgets/AppLayout.test.tsx src/pages/knowledge-production/ProviderSetupPanel.test.tsx src/pages/compliance/NotificationSettings.test.tsx src/pages/compliance/SystemProviders.test.tsx src/shared/api/errors.test.ts src/shared/api/mutation.test.tsx src/shared/api/hooks.test.ts src/pages/clinical/WorkflowTodos.test.tsx src/pages/tenant/TerminologyMapping.test.tsx src/pages/tenant/ImplementationGuide.test.tsx src/pages/clinical/RuleValidate.test.tsx src/shared/config/ruleLayeredEditor.test.ts src/test/visualDebtGuard.test.ts src/test/viteProxyGuard.test.ts src/shared/lib/browserCompatibility.test.ts src/pages/tenant/TenantOnboarding.test.tsx src/shared/config/customerLanguageGate.test.ts src/shared/config/conditionModel.test.ts src/pages/clinical/Notifications.test.tsx`、
  `npm run typecheck`、`npm run lint`、`npm run stylelint`、`npm run format:check`、
  `node scripts/authenticity-guard.mjs --mode=inventory`、`node --test scripts/authenticity-guard.test.mjs`、
  `npm run test:lint-rules`、`npm run build`、`git diff --check`；尚未重新部署 134。
- 数据库五方言 COMMENT 与 schema 残留旧口径已继续清理：脱敏规则、敏感字段匹配、诊断时序约束、知识原件
  资料类型和发布质量门摘要统一改为平台、服务、统一规则求值和影响评估语义；真实性门禁同步拦截数据库
  COMMENT 中的影响模拟和后端实现层旧口径。已验证：
  数据库旧 COMMENT 口径组合搜索无结果、`node --test scripts/authenticity-guard.test.mjs`、`node scripts/authenticity-guard.mjs --mode=inventory`、
  `bash scripts/check-comment-zh.sh --mode=full`、`mvn -q -Dtest=MigrationBaselineContractTest test`、
  `mvn -q -DskipTests package`、`git diff --check`；尚未重新部署 134。
- 规则和诊断知识发布门禁的客户面验证材料口径已全局清理：前台、当前功能目录、后端中文契约、测试夹具、
  五方言 COMMENT、schema、沙盘脚本和能力导出均从“测试用例/测试病例”改为“验证用例/验证病例”，避免
  医疗引擎运营员、医生复核、实施培训和后续 AI 把发布门禁材料理解成 QA 测试数据。真实性门禁新增
  产品验证材料旧口径拦截。已验证：`npm test -- --run src/pages/tenant/RuleDefinitions.test.tsx src/pages/quality/DiagnosisKnowledgeMaintenance.test.tsx src/shared/config/routes.test.ts src/shared/config/customerLanguageGate.test.ts`、
  `node --test scripts/authenticity-guard.test.mjs`、`node scripts/authenticity-guard.mjs --mode=inventory`、
  `mvn -q -Dtest=DiagnosisKnowledgeServiceTest,DiagnosisKnowledgeApiContractTest,RuleEngineServiceTest,RuleEngineControllerSecurityTest,MigrationBaselineContractTest test`、
  `npm run typecheck`、`npm run lint`、`npm run stylelint`、`npm run format:check`、`npm run test:lint-rules`、
  `npm run build`、`mvn -q -DskipTests package`、`bash scripts/check-comment-zh.sh --mode=full`；尚未重新部署 134。

## 本轮落地内容

- 修复知识生产候选共存读态：非 `PENDING_REPLACEMENT_REVIEW` 候选在只读共存端点返回明确“不可替换复核”
  视图，不再用 409 阻断知识生产页面和演练。
- 发布后验收脚本升级为八段证据契约，核对完整覆盖矩阵，不再使用旧布尔字段。
- 发布后验收脚本的权限口径改为客户四职责 12 个有效分配 + 系统超级管理员 1 个保留分配，
  防止旧“8 个分配”误导。
- 发布后验收脚本的影子评测口径改为 `PASSED/PENDING_REVIEW + ready_for_review + 无退化`，
  符合医疗安全复核模型。
- 发布后验收脚本的沙盘 CURRENT 口径改查统一机构生效版本 `clinical_runtime_release` 和
  `clinical_runtime_release_item`，禁止旧 `mk_sandbox_runtime_binding` 回流。
- 新增全前台真实操作 E2E 门禁，覆盖平台接入、知识资产、模型外调策略、患者主索引和临床随访写入链路，
  证明演练数据可以由前台页面产生，而不是只靠脚本/API 绕过页面。
- 模型能力页面新增前台外调安全策略配置入口：具备 `llm.egress.manage` 权限的实施/运营人员可配置
  字段允许范围、脱敏算子、敏感级别和责任确认阈值，并明确公网外部模型可在授权用途内使用患者上下文。
- 后端模型外调安全闸收紧 `NONE` 语义：`NONE` 只保留非核心业务值，姓名、证件、手机号、地址、患者编号、
  身份证后四位等核心患者标识仍必须递归遮蔽或置空，不能作为“完全不脱敏”理解。
- 模型能力页面已统一使用“模型能力”命名，并新增患者上下文外调边界提示：公网模型可按授权用途
  使用患者上下文，但核心标识先遮蔽；院内本地模型可按授权使用必要信息，日志和证据不留患者明文。
- 全系统 E2E 已按“模型能力”页面标题断言，覆盖普通 Chromium 与国产 Chromium 内核仿真下的模型能力页面
  和全前台真实操作演练，避免后续 AI 或实施人员被历史页面名误导。
- 模型能力页的外调安全策略弹窗新增字段出域预览：实施人员配置允许字段时，可直接看到提示词、年龄、性别、
  诊断摘要、患者姓名、证件号、手机号、地址和患者编号的出域结果；核心标识默认不出域，即使被纳入允许字段，
  运行时也只可传出遮蔽后值；高敏用途达到阈值时提示每次出域前责任确认，证据只保存字段清单、处理策略和摘要。
- 模型能力页的外调安全策略弹窗新增本次外调用途确认：实施人员可填写脱敏载荷摘要和用途说明，
  前台调用数据最小化确认端点生成审计记录；确认记录不能替代医生确认，也不能使模型输出自动形成医嘱。
- 数据最小化策略正式入口新增模型外调用途确认分页回看；审计管理页新增“模型外调确认”证据标签页，
  审计员和具备模型外调管理权限的实施/运营人员可直接查看能力、用途、脱敏载荷摘要、确认人和确认时间。
- 后端模型网关将高敏外调缺责任确认从普通 B0 降级拆为 `CONFIRMATION_REQUIRED` 可操作状态，返回能力、
  脱敏载荷摘要、字段清单、模型服务和提示信息；知识生产器将其归为生产安全门禁，不生成候选、不伪造降级。
- 知识生产页在真实模型生成任务被外调责任确认拦截时，直接在当前任务上下文弹出“确认模型外调用途”，操作者
  填写用途后调用统一确认端点落审计，并用同一生成请求重试；确认、摘要和重试都停留在当前任务上下文。
- 模型外调责任确认与策略管理已拆边界：`knowledge.write` 可确认当前知识生产任务的脱敏载荷用途，
  `llm.egress.manage` 仍独占字段允许范围、脱敏规则、确认阈值等策略维护，避免真实前台流程卡在运营专属权限。
- 审计管理页按真实审计员、信息科长和院长视角优化：默认列表直接展示审计事项、中文操作、执行结果、
  追踪号、链签名状态和证据对象；对象类型、执行结果是常用筛选，默认可用，不再放进证据详情。
- 证据详情不是要禁止开关本身；更好的体现方式是随任务上下文渐进展开低频证据，例如事件编号、环境标识、
  载荷摘要和原始变更快照。关键业务判断、医疗安全和审计追踪证据必须默认可见，不能藏在高级开关后；
  页面如需开关，优先表达为证据详情、诊断信息、变更明细或上下文证据展开。
- 知识审核候选的 AI 生产来源溯源已按新定义调整：默认展示医院可判断的 AI 标识、生产器、目标管道、
  置信度、备用能力和来源依据；模型任务编号、策略、版本、提示词、工具、降级原文等低频证据改由
  当前审核对照抽屉内的“生产证据详情”展开。
- 全局低频证据能力已收束为证据详情：前端偏好键、权限判断、页面壳、证据抽屉、服务端表格保存视图和租户品牌
  字段统一使用证据详情语义；追溯字段统一作为 `advancedOnly` 渐进展示，租户品牌五方言迁移和 schema 使用
  `evidence_details_enabled`，不再保留身份化模式字段。
- 全知识正式演练和八阶段全系统演练已补充 stdout 进度与证据可观察性：知识域开始/完成、模型任务耗时、
  V2 刷新、回滚、阶段耗时、完成/失败阶段数都会进入运行输出和总证据，避免长任务被误判为卡死。
- 模型能力页外调安全策略的前台文案已从安全工程术语改为“模型使用字段预览”“发送给模型前责任确认”
  和“拟供模型使用字段”，患者信息安全边界仍保持核心标识遮蔽、用途确认和审计留痕。
- MPI 创建权限从 `mpi.write` 拆出为 `mpi.create`：临床使用者可创建脱敏患者索引，平台/治理动作仍需更高权限。
- 修复 MPI 性别统计投影在 PostgreSQL/Hibernate 下的新建患者 500 问题；未知、空值和非 M/F 值统一归入
  `UNKNOWN`。
- 前台 MPI 文案修正：将“在径路径实例”改为“活跃路径实例”。
- 临床随访页移除实现视角说明，改为围绕真实随访计划、问卷回收和异常回院处理表达页面目标。
- 协同任务页按医生、护士和随访团队真实工作方式优化：页面标题对齐功能目录为“协同任务”，优先级改为中文
  医疗可读标签，并新增今日队列摘要，默认告诉操作者待处理数量、安全复核、护理任务、危急和高优先任务，
  以及先处理哪类任务。
- 随访协同问卷填报补齐真实执行者来源：患者自填、护士代填、医生复核录入由前台表单明确选择并写入提交契约，
  避免把护士或患者产生的数据错误归到医生名下。
- 随访协同统计、模板、计划生成、问卷回收和异常回院登记继续按医生、护士、患者随访和信息科视角收束：
  统计口径使用当前范围，读取失败提示登录状态、组织范围和信息科核查随访服务，模板页说明发布后可用于生成计划，
  异常回院操作改为登记，模板表单不暴露内部版本、阶段性来源或技术字段名。
- 全局低频证据展开统一改为“证据详情”：共享开关、页面壳、审计页、运行保障页和服务端表格门禁都不再使用
  旧标签；CONSTITUTION、体验契约、术语表和质量基线同步改口径，避免代码与权威文档说两套话。
- 页面烟测已同步当前功能目录标题：协同任务、随访协同不再被旧标题断言拉回；随访读取失败改成登录状态、
  组织范围和信息科核查随访服务的可执行提示，不暴露实现视角。
- 全真体验沙盘目录降级语义已收敛：目录读取失败时页面只提示沙盘场景目录暂不可用，并明确不会生成或暗示
  可运行临床场景；远端目录缺少数值录入或可执行输入契约时继续阻断运行，但原因使用产品语义表达。
- 退役的独立“高级工具”主域不再作为连续文本保留在前端源码和当前文档中；路由/API 中的 `advanced`
  仍是既有技术路径，产品表达按知识关系、模型能力、国产化核验、诊断工具和证据详情落入对应空间。
- 性能压测契约已切到当前产品模型：脚本只打医疗引擎、知识生产、质量管理、平台管理和模型能力入口，
  使用 `/api/v1/model-capabilities/*`、`/api/v1/model-providers` 与环境注入 Bearer 令牌，不再保留旧模型路径、
  历史四域分组或固定患者病例文本；质量基线新增 `scripts/performance-contract-guard.test.mjs` 门禁。
- 当前文档、前端共享 API 注释、后端观测/权限 Javadoc、模型能力目录、五方言 V1 迁移和 schema 已同步
  当前产品口径：质量能力归入“质量管理”，低频证据按“证据详情”渐进展示，不再用旧域名或旧体验词误导后续实现。
- 上述性能契约与旧口径清理已在本地通过：`node --test scripts/performance-contract-guard.test.mjs`、
  `mvn -q -Dtest=MigrationBaselineContractTest,ModelGatewayServiceTest test`、`mvn -q -DskipTests package`、
  `npm run typecheck`、`npm run lint`、`npm run stylelint`、`npm run format:check`、`npm run test:lint-rules`、
  `npm run build`、真实性/配置边界/迁移规约 inventory、`bash scripts/check-comment-zh.sh --mode=full`、
  `git diff --check`；尚未重新部署 134。
- 客户面错误态和路径向导已继续按真实产品体验清理：图谱投影读取失败、规则快照读取失败和路径原型说明
  改为真实服务状态与可执行下一步，不再用退役演示说明或上线前结构口吻解释页面；`RequestContext` Javadoc
  改为当前 TraceId、OrgScope 和 userId 的运行事实。真实性门禁新增客户面退役文案与后端早期任务口吻拦截。
- 上述客户面退役说明清理已在本地通过：`node --test scripts/authenticity-guard.test.mjs`、
  `node scripts/authenticity-guard.mjs --mode=inventory`、
  `npm test -- --run src/pages/advanced/GraphExplore.test.tsx src/pages/tenant/PathwayTemplates.test.tsx src/pages/tenant/RuleDefinitions.test.tsx`、
  `npm run typecheck`、`npm run lint`、`npm run stylelint`、`npm run format:check`、`npm run build`、
  `mvn -q -DskipTests package`、`bash scripts/check-comment-zh.sh --mode=full`；尚未重新部署 134。
- 全局治理语言和客户面工程术语已继续收束：系统运维入口统一为“诊断工具”，规则、路径、评价指标、
  知识生产和资产发布前治理统一表达为“安全复核 / 安全门”，规则/路径低频精确结构统一表达为
  “受控配置文本 / 配置明细”；前后端契约、Javadoc、CONSTITUTION 和页面测试同步改口径。
  真实性门禁新增客户面工程语言拦截，禁止“开发者控制台、技术验证、技术配置、技术闸、技术阻断、
  技术门禁、技术门”等表达回流。已验证：`node --test scripts/authenticity-guard.test.mjs`、
  `node scripts/authenticity-guard.mjs --mode=inventory`、
  `npm test -- --run src/pages/operationalControlPages.test.tsx src/pages/quality/QcEvalSets.test.tsx src/pages/tenant/RuleDefinitions.test.tsx src/pages/tenant/PathwayTemplates.test.tsx src/shared/ui/StepFlow.test.tsx src/pages/quality/KnowledgeGovernance.test.tsx`、
  `npm run typecheck`、`npm run lint`、`npm run stylelint`、`npm run format:check`、`npm run test:lint-rules`、
  `npm run build`、`mvn -q -DskipTests package`、`bash scripts/check-comment-zh.sh --mode=full`、
  `git diff --check`；尚未重新部署 134。
- 全局治理语言口径进一步收束为医院角色可理解的产品语义：诊断工具失败提示指向信息科，来源启用、
  资产发布、模型评测统一为安全校验或安全评测，低频字段统一为追溯字段并进入证据详情；权威文档、
  Java 契约说明、五方言 COMMENT 与 schema 同步，并由真实性门禁防回流。
- 知识生产模型外调用途确认重交互测试补用同文件既有 15s 交互预算，消除全量并发下 5s 默认超时；
  该调整只扩大真实前台交互测试预算，不改变产品运行逻辑。
- 实施和信息科视角的内部口径继续收束：诊断工具路由说明改为“运行诊断信息”，Webhook 说明改为签名验证
  和连通性验证，来源启用改为 robots 安全校验，来源版本缺项改为追溯信息不完整，路径试运行用于发布或复核前
  回放，平台登录目录只说明平台接管和运行保障人员，规则治理 COMMENT 改为完整发布链。
- 临床提醒详情的知识来源缺失空态已从工程自证改为医生可执行的复核提示；Webhook 相关页面、契约、权限、
  审计和集成说明统一表达为验证，避免医院实施、信息科和外部系统联调时把一次性测试理解成上线能力。
- 领域门面、运行韧性演练和模型质量评测不再使用 fixture 作为公开契约或生产证据字段，统一表达为
  B0 主链路证据和离线基线评测，避免把正式能力误解为临时样本或测试夹具。
- 知识发布质量门中文口径从“影响模拟”统一为“影响评估”；全知识演练证据、前台复选项和后端默认摘要同步，
  规则提示卡引用的静态校验动作不再写“占位”，避免审核员、院长或后续 AI 把校验动作误解为临时数据。
- 权威体验契约把页面改造原则从“演示重构”改为“体验重构”，强调真实客户任务和全角色体验，不再用演示页思路
  牵引产品优化。
- 平台管理员工作台的治理卡片从客户难理解的“治理切片”改为“治理概览”，错误和空态也改为可执行的数据状态，
  保留底层多维治理能力，不让内部架构词成为医院用户的第一屏认知负担。
- 发布治理服务的客户可见契约从“发布模拟”统一为“发布影响评估”：灰度发布必须复用服务端重新计算的
  影响评估摘要，目标组织、候选资产和证据序列化错误都按影响评估表达，避免院长、信息科和实施人员误以为
  上线前治理只是演示或临时模拟。
- 工作台来源卡片空态从“后续来源上线后自动回灌”改为当前组织数据状态和可执行下一步，避免医生、护士、
  信息科或院长在无数据时被未来承诺误导。
- 通知偏好和导出能力的不可用状态从实现层接入说明改为业务可执行提示：临床消息页提示回到通知设置确认，
  导出控件提示联系信息科确认范围或配置，避免前台用户把平台能力误读成临时未完成页面。
- 全局客户面服务状态口径继续收束：临床、质量、实施、系统运维、沙盘、诊断工具、模型生产和字段目录
  页面不再把读取失败、对接方式或低频证据表达成实现层接口名，统一改为医院角色可理解的服务状态、
  服务目录、服务契约、服务对接和来源状态。
- 统一身份登录、身份来源和委托登录错误契约继续按医院角色真实理解收束：医生/护士/管理员登录页看到的是
  “统一身份服务待配置”和“待配置”的可执行状态，信息科/实施通过身份来源完成配置；测试夹具和后端契约也不再
  用统一身份实现侧旧话术误导后续实现。
- 工作台第一屏继续按全角色真实体验收束：临床使用者进入患者路径、提醒推荐、随访协同和通知，医疗引擎运营员
  进入知识来源、术语映射、发布影响和质量整改，平台管理员看到知识同步来源配置状态；聚合统计和跨页汇总只在
  对应治理页面展示真实数据，第一屏不再自证、不承诺未来来源。
- 客户面实现层语言继续按真实医疗产品体验收束：页面默认解释平台服务状态、来源事实、数据脱敏、安全策略、
  服务契约和可执行下一步；后台实现命名只留在必要代码契约中，不能进入医生、护士、患者、实施、信息科、
  审计或院长的默认任务界面。
- 前端测试说明和共享注释同步收束到当前产品语义：测试用例名、共享 API 注释、开发代理提示、规则配置说明
  和浏览器兼容性报告不再把后台实现层当成客户面事实，避免后续 AI 读取测试时被旧口径误导。
- 数据库 COMMENT 和 schema 同步收束：脱敏规则、诊断约束、知识原件和发布质量门不再保留实现层、
  阶段性或旧影响评估事实，五方言迁移继续保持一致。
- 规则和诊断知识发布门禁的验证材料统一称为“验证用例/验证病例”：页面按钮、弹窗、错误提示、审计摘要、
  后端 Javadoc、数据库 COMMENT、沙盘演练脚本和功能目录同步，避免把医疗发布门禁误读成普通软件测试数据。
- 前后端服务状态与未来接入口径继续收束：模型能力、规则中枢、共享 API/config/ui 注释、脱敏服务、
  受控工具、资料库存储和模型外调安全闸不再使用阶段性、实现层或低频配置旧表达；真实性门禁新增前端客户面、共享 API/config、
  后端契约和数据库 COMMENT 拦截，避免后续 AI 把已上线能力误读为临时实现或未来计划。已验证：
  `node --test scripts/authenticity-guard.test.mjs`、`node scripts/authenticity-guard.mjs --mode=inventory`、
  `npm test -- --run src/pages/advanced/AiWorkflows.test.tsx src/pages/tenant/RuleDefinitions.test.tsx src/shared/ui/PageShell.test.tsx src/shared/config/conditionModel.test.ts src/shared/api/hooks.test.ts`、
  `mvn -q -Dtest=MaskingServiceTest,ManagedDocumentMaterialStorageTest,ModelEgressGuardTest,ControlledToolServiceTest test`、
  `npm run typecheck`、`npm run lint`、`npm run stylelint`、`npm run format:check`、`npm run test:lint-rules`、
  `npm run build`、`mvn -q -DskipTests package`、`bash scripts/check-comment-zh.sh --mode=full`；
  尚未重新部署 134。
- 路径原型、沙盘外圈路径和诊断工具角色口径已继续按全局产品体验收束：路径维护前台默认原型改为
  “基础节点闭环”，沙盘脚本和服务端目录使用通用临床路径资产 `PATH.CLINICAL.CYCLE`，不再把固定急诊处置
  当作平台默认原型；诊断工具功能目录任务责任改为信息科和实施角色。真实性门禁新增前端、后端和沙盘脚本
  固定急诊原型拦截，避免医生、护士、实施或后续 AI 把单专科样例误认为平台标准能力。已验证：
  `node --test scripts/authenticity-guard.test.mjs`、`node scripts/authenticity-guard.mjs --mode=inventory`、
  `npm test -- --run src/pages/tenant/PathwayTemplates.test.tsx`、
  `node --test scripts/sandbox/scenario-rules.test.mjs scripts/sandbox/seed-scenarios.test.mjs`、
  `mvn -q -Dtest=SandboxScenarioCatalogTest,SandboxOrchestrationServiceTest,SandboxRuntimeStatusServiceTest,PathwayPublicationStatusSynchronizerTest,ClinicalRuntimeReleaseServiceTest,AuthoringPreviewRunServiceTest test`；
  尚未重新部署 134。
- 运行保障状态和模型赋能覆盖契约已继续按信息科、实施、院长和医疗引擎运营员视角收束：系统运行依赖明细
  用连接健康验证、模型能力页和服务对接页解释当前状态；模型 B0 降级信封改为无模型确定性主链路说明；
  全业务模型能力治理中文统一为“模型赋能覆盖矩阵 / 待配置”。真实性门禁新增运行状态阶段性口吻拦截，防止上线前表达回流。已验证：
  `mvn -q -Dtest=RuntimeOperationsServiceTest,ModelGatewayServiceTest test`、
  `mvn -q -Dtest=ModelEnhancementMatrixServiceTest,ModelEnhancementMatrixControllerSecurityTest,DefaultPermissionPolicyTest,ServiceContractGovernanceTest,RuntimeOperationsServiceTest,ModelGatewayServiceTest test`、
  `node --test scripts/authenticity-guard.test.mjs`、`node scripts/authenticity-guard.mjs --mode=inventory`；
  尚未重新部署 134。
- 知识解析、外部集成、运行任务、资产库和阶段性 Javadoc 口径已继续清理：解析格式无解析器时返回
  “解析器待配置”，运行任务无处理器时返回“没有可用执行器”，外部连接阻断改为“外部连接器待配置或外部不可达”，
  资产类型错误改为“不在资产库支持范围”；后端注释不再保留阶段性实现计划说法。真实性门禁新增阶段性接入口吻拦截。已验证：
  `mvn -q -Dtest=DefaultRuntimeTaskExecutorTest,DocumentParseOrchestrationServiceTest,IntegrationServiceTest test`、
  `node --test scripts/authenticity-guard.test.mjs`、`node scripts/authenticity-guard.mjs --mode=inventory`；
  尚未重新部署 134。
- 运行保障页、工作台质量闭环入口、集成契约和后端生产注释已继续按全局体验收束：普通运行保障视图直接说明
  依赖状态、安全降级主链路和处理入口，证据详情只承载部署、迁移、配置来源等追溯信息；医疗引擎运营员质量入口
  统一指向指标口径、责任对象、整改进度和医保审核；集成契约改为连接器健康验证语义；患者主索引和图投影注释
  改为当前状态语义。真实性门禁新增后端注释和集成契约旧口径拦截。已验证：
  `npm test -- --run src/pages/compliance/SystemProviders.test.tsx`、
  `npm test -- --run src/widgets/WorkbenchPanel.test.tsx`、
  `node --test scripts/authenticity-guard.test.mjs`、`node scripts/authenticity-guard.mjs --mode=inventory`；
  尚未重新部署 134。
- 临床使用者工作台待办空态已继续按医生、护士视角收束：无待办时不再引导到发布治理，而是提示进入患者路径、
  提醒与推荐、随访协同或消息通知查看实时事项；仍不伪造待办数量。已验证：
  `npm test -- --run src/widgets/WorkbenchPanel.test.tsx`；尚未重新部署 134。
- 临床提醒反馈与频次治理已继续按医生、护士真实使用视角收束：医师反馈页签、反馈历史、状态筛选和频次治理
  不再暴露内部反馈枚举、生硬否定措辞、内部策略动作或单药品示例，改为采纳/不采纳建议、未采纳、已限频、
  真实理由和登录态记录；高危红线和必须医师确认的提醒明确不会被自动减少或隐藏。已验证：
  `npm test -- --run src/pages/clinical/CdssFatigue.test.tsx`；尚未重新部署 134。
- 随访协同统计、模板发布、计划生成、问卷回收和异常回院登记已继续按医生、护士、患者随访和信息科视角收束：
  统计改为当前范围，读取失败指向登录状态、组织范围和信息科核查随访服务，模板页说明发布后可用于生成计划，
  异常回院操作改为登记，模板表单不再暴露内部版本、阶段性来源和技术字段名。已验证：
  `npm test -- --run src/pages/clinical/Followup.test.tsx`、
  `npm run typecheck`、`npm run lint`、`npm run stylelint`、`npm run format:check`、`npm run test:lint-rules`、
  `npm run build`、`git diff --check`；尚未重新部署 134。
- 临床通知、协同任务和 MPI 活跃患者目录已继续按医生、护士、患者服务团队和信息科视角收束：通知与待办
  读取失败统一指向登录状态、组织范围和信息科核查对应服务；MPI 统计和合并目录使用当前组织范围，避免把
  内部运行分区暴露给临床用户。已验证：
  `npm test -- --run src/pages/clinical/Notifications.test.tsx src/pages/clinical/WorkflowTodos.test.tsx src/pages/clinical/Mpi.test.tsx`；
  尚未重新部署 134。
- 全局顶栏用户菜单和权限指纹已继续按全角色第一屏体验收束：租户级数据范围在用户菜单和权限浮层中显示为
  服务机构，医院、科室等组织层级保持可读，不再把内部运行分区作为所有角色的身份范围名称。已验证：
  `npm test -- --run src/widgets/AppLayout.test.tsx src/features/permission-chip/PermissionChip.test.tsx`；
  尚未重新部署 134。
- 实施与验收、服务机构实施就绪空态和错误态已继续按实施工程师、信息科和院方管理员视角收束：读取失败
  指向追踪号和对应实施/组织服务核查；无步骤时提示确认服务机构与组织范围，不再使用内部运行分区或空间
  口径解释上线准备。已验证：
  `npm test -- --run src/pages/tenant/ImplementationGuide.test.tsx src/pages/tenant/TenantOnboarding.test.tsx`；
  尚未重新部署 134。
- 通知偏好、审计与证据和共享路由体验元数据已继续按审计员、信息科、平台管理员和临床用户视角收束：
  通知偏好读取失败指向登录状态、组织范围和信息科核查；审计链、审计详情和身份绑定元数据使用服务机构与
  组织范围隔离，不再用内部运行分区解释关键证据边界。已验证：
  `npm test -- --run src/pages/compliance/NotificationSettings.test.tsx src/pages/compliance/AdminAudit.test.tsx`、
  `npm test -- --run src/shared/config/routes.test.ts src/pages/pages.smoke.test.tsx`；尚未重新部署 134。
- 服务机构开通台账、开通表单、开通结果、组织编码提示和品牌归属预览已继续按平台管理员、实施工程师和
  信息科视角收束：平台治理入口统一使用服务机构/服务机构标识，不再把内部运行分区术语交给院方用户；
  组织节点和品牌预览也回到当前服务机构语义。已验证：
  `npm test -- --run src/pages/tenant/TenantOnboarding.test.tsx`；尚未重新部署 134。
- 首次部署接管和安全基线系统配置已继续按院方管理员、信息科、实施工程师和平台管理员视角收束：
  初始管理员创建后的后续账号、集团/医院开通提示和安全基线服务机构覆盖输入框统一使用服务机构语义，
  不再用内部运行分区术语解释用户需要维护的机构边界。已验证：
  `npm test -- --run src/pages/Bootstrap.test.tsx`、
  `npm test -- --run src/pages/compliance/SecurityBaseline.test.tsx`；尚未重新部署 134。
- 模型能力、知识治理、嵌入临床建议、规则配置、标准上下文、术语映射、系统接入、知识血缘、词汇表和开通服务
  注释已继续按医生、护士、信息科、实施工程师、医疗引擎运营员、知识管理员和平台管理员视角收束：
  模型配置摘要、平台知识维护提示、嵌入来源允许清单、规则适用范围/编码校验、标准上下文错误、术语资产范围、
  适配器质量快照和知识身份错误态统一使用服务机构/医疗机构/机构范围语义，不再把内部运行分区术语交给前台用户
  或当前协作文档。已验证：
  `npm test -- --run src/pages/advanced/AiWorkflows.test.tsx src/pages/quality/KnowledgeGovernance.test.tsx src/pages/clinical/EmbedLaunch.test.tsx src/pages/tenant/RuleDefinitions.test.tsx src/shared/api/hooks.test.ts src/pages/tenant/TerminologyMapping.test.tsx src/pages/tenant/AdapterHub.test.tsx src/pages/advanced/Provenance.test.tsx`；
  旧空间类客户面词扫描在 `frontend/src`、`medkernel-backend/src` 和 `docs` 当前文档中无命中；尚未重新部署 134。
- 平台治理入口、平台内置名称、登录目录、首次部署接管、机构知识提示、平台发布错误和测试契约已继续按
  平台管理员、实施工程师、信息科、知识管理员和前台协作者视角收束：平台侧统一称平台治理入口/平台权威范围/
  平台管理入口，测试 fixture 也不再保留旧客户面范围词作为真实名称；真正技术含义的命名空间仍保留在后端注释和
  校验错误里。已验证：
  `npm test -- --run` 覆盖 24 个受影响前端测试文件，275 个用例通过；
  `mvn -q -f medkernel-backend/pom.xml -Dtest=PlatformTenantTest,AuthControllerTest,ComplianceUserControllerTest,RuntimeReleaseControllerTest test`；
  旧客户面范围词扫描在 `frontend/src`、`medkernel-backend/src/test` 和 `docs` 中无命中；尚未重新部署 134。
- 规则详情、路径详情和服务机构品牌偏好已继续按医疗引擎运营员、实施工程师、信息科和院方管理员视角收束：
  低频精确结构不再作为孤立开关呈现，改为上下文内“配置明细”；品牌偏好改为“默认展示验收证据”，并同步
  五方言迁移 COMMENT 与 schema。已验证：
  `npm test -- --run src/pages/tenant/RuleDefinitions.test.tsx src/pages/tenant/PathwayTemplates.test.tsx src/pages/tenant/TenantOnboarding.test.tsx`；
  `bash scripts/check-comment-zh.sh --mode=full`；旧低频明细入口词扫描在 `frontend/src`、`docs` 和
  `medkernel-backend/src` 中无命中；尚未重新部署 134。
- 后端 Javadoc、共享 API 注释、FHIR capability 契约、五方言迁移 COMMENT、schema 和后端测试材料已继续按
  当前上线能力清理历史阶段标签：规范编号不再夹带旧批次，字段注释改为当前生效清单事实，FHIR capability
  版本统一为 `OPT-01`，真实性门禁新增历史阶段标签拦截。已验证：
  `node --test scripts/authenticity-guard.test.mjs`；当前源码、测试和当前文档目录的历史阶段标签扫描无命中；
  尚未重新部署 134。
- 正确前端部署包格式必须包含 `dist/index.html`：
  `COPYFILE_DISABLE=1 tar --no-xattrs -czf dist.tar.gz -C frontend dist`。仅打包 `frontend/dist` 内容会被部署脚本
  拒绝，不能作为候选包。
- 完整演练访问 134 自签 SAN 证书时只追加 `NODE_EXTRA_CA_CERTS`；曾因同时设置
  `SSL_CERT_FILE=/zoesoft/medkernel/nginx/ssl/server.crt` 覆盖系统 CA，导致全知识官方来源抓取失败。
  根因已确认并用修正后的环境重跑通过，不要把这个失败当作知识来源或业务代码问题。

## 模型与患者信息安全

- 公网部署的系统本身可以处理患者信息；调用公网 API 模型或外部模型时，允许在授权用途内使用患者上下文，
  但必须先做最小必要、核心敏感字段屏蔽、目的绑定、责任确认、租户/机构边界和审计；`NONE` 脱敏算子
  也不能放出核心患者标识。
- 外网、公网模型和 Codex/CLI 知识生成场景都必须默认不外泄患者核心敏感信息；确需患者上下文时，只传递
  已授权且已屏蔽核心标识的最小字段集合。
- 院内本地模型可以在授权范围内使用必要敏感信息，但仍要标注敏感边界，限制留存，证据和日志不得含患者明文。
- 无论内外模型，模型输出只能是候选、草稿、解释或摘要，必须保留 AI 标识、模型版本、提示版本、
  输入/输出摘要、引用与校验结果；不得伪造模型调用或把模型输出直接变成医嘱/临床事实。

## 下一步

1. 进入全角色、全流程、全视角真实前台体验与产品优化：医生、护士、药师、医技、患者/随访、平台管理员、
   医疗引擎运营员、审计员、医疗实施工程师、信息科长、院长、医疗产品经理等都要体验。发现功能分类不合理、
   流程过长、理解困难、六态不足、页面空壳、权限误导、患者信息安全表达不清或医疗安全边界不清时，
   直接按最符合真实医疗产品的方案优化。
2. 将脚本式全链路已通的路线转为更多真实前台操作：尽可能通过前台创建机构、账号、来源、患者资源、字典映射、
   知识候选、版本发布、机构生效版本、沙盘运行、临床调用、审计与恢复证据；脚本只作为辅助校验，不再作为
   唯一数据来源。
3. 继续扩展公网/外网患者信息使用体验：字段出域预览、手工用途确认、审计回看和知识生产真实任务阻断确认闭环
   已完成本地薄片并随 134 清库演练验证；还要补强不同部署模式默认策略和更多前台真实操作证据，使其可理解、
   可检查、可追溯。
4. 按“证据详情新定义”继续回扫历史页面：知识审核候选来源溯源和全局证据详情偏好已完成；其它旧页面如仍存在
   生硬身份化开关、孤立技术入口或把关键安全/审计/业务判断证据藏起来的设计，后续都要改成上下文里的渐进证据、
   诊断信息或变更明细。
5. 本地临床协同任务、随访协同体验薄片、沙盘场景目录语义清理、退役工具主域文本清理、性能压测契约、
   旧口径门禁、客户面退役说明清理、全局治理语言清理、安全校验口径清理、实施内部口径清理、
   临床提醒来源空态、Webhook 验证口径清理、领域门面 B0 主链路证据和离线评测 baseline 命名清理、
   知识发布质量门影响评估与规则提示卡引用占位口径清理、权威体验契约演示重构旧说法清理、
   平台管理员工作台治理概览表达清理、发布治理服务影响评估契约口径清理、工作台空态未上线承诺清理、
   通知偏好和导出能力实现层旧口径清理、全局客户面服务状态旧口径清理、统一身份登录和身份来源待配置口径清理、
   工作台第一屏多角色数据状态表达清理、全局客户面实现层口径清理、前端测试说明与共享注释旧口径清理、
   数据库 COMMENT 和 schema 旧口径清理、验证用例/验证病例产品口径清理、前后端服务状态与未来接入口径清理、
   路径原型和沙盘外圈路径通用化清理、运行保障状态和模型赋能覆盖契约口径清理、
   知识解析和外部集成阶段性接入口径清理、运行保障普通视图与证据详情分层清理、
   工作台质量闭环入口清理、集成契约和生产注释旧口径清理、临床待办空态角色体验清理、
   临床提醒反馈与频次治理医生/护士视角清理、随访协同统计/模板/异常回院登记多角色体验清理、
   临床通知/协同任务/MPI 组织范围和信息科提示清理、全局顶栏与权限指纹范围语言清理、
   实施与服务机构就绪空态/错误态清理、通知偏好/审计证据/路由体验范围语言清理、
   服务机构开通台账与表单语言清理、首次部署接管和安全基线范围语言清理、
   模型/知识/嵌入/规则/术语/接入/知识血缘范围语言清理、平台治理入口和测试契约范围语言清理、
   低频配置明细与验收证据呈现清理、历史阶段标签清理和门禁增强
   还未重新部署 134；下一次清库/发布演练要纳入真实前台操作证据，不能把当前本地薄片或本地门禁误记为
   134 已验收。
6. 继续清理旧兼容、冗余设计和误导性历史事实；`.codex/config.toml` 是本地未跟踪文件，不要纳入提交。
