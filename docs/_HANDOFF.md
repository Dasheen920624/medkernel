# 会话接力

> **开工先读本文件续接，别考古。** 本文件只保留会影响下一步执行的当前事实；历史复盘查 git、卡片、计划或审计文档。

## 当前真相

- 最新主线：`origin/main=8520b741`，已包含 #634「自主公域知识生产 + AI 工厂收尾 + 整体上线主计划」。
- 当前本地分支：`codex/knowledge-fullflow-audit-production`；用户要求长任务自主执行到完成，只本地提交，暂不合并远程 `main`。
- 当前主线口径：仍属于 B0 第一阶段全功能核查与完美化的知识生产到上线长线整改；每个切片必须保留测试、T-GATE 和接力证据。
- 国产化边界：国产化真实环境本轮暂不处理，后续全面验收再回到国产 OS/JDK、达梦、金仓、真实国产数据和现场环境。
- 134 发布口径：按全新项目上线；P9 发布前停服务并清空数据库、旧制品和旧运行数据，从最新迁移基线全新初始化，不迁就历史数据、不回灌、不依赖旧部署回退路径。
- 资料库口径：受管 `file://` 本地磁盘、对象存储或 HTTPS 网关均是正式后端；不得把任一种资料后端写成唯一选项。
- 主计划入口：[`docs/superpowers/plans/2026-06-16-autonomous-knowledge-production-golive-master-plan.md`](superpowers/plans/2026-06-16-autonomous-knowledge-production-golive-master-plan.md)。

## 本地已完成

- Phase 1：文档原件资料库存储层。
  - `ManagedDocumentMaterialStorage` 支持显式配置的受管 `file://` 本地根；未配置或协议未接入时结构化阻断，不回退临时目录。
  - `mk_knowledge_material_object` 已入五方言基线；解析成功后 `SourceVersion.file_uri` 指向受管 URI；支持原件审计读取和成功 job 重解析。
- Phase 2：真实医学回归基线与 readiness。
  - 启动期从 OPT-04 ACTIVE 已审红线投影回归基线，不编医学题/答案；`mk_llm_regression_case.source_reference`、维护端点和前端 readiness 已补。
  - `MODEL_EVALUATION` 仍要求真实 `PASSED` 评测，种子只补真实基线，不绕过闸门。
- Phase 3：AI 工厂收口。
  - AIK-STD-05/08/09/10/11/03/07 关键后端与前端证据面已补：结构化红线、8 态分流、差异/过期任务、原子替换影响任务、知识包装配、术语候选生成入口、Agent 进度/中止和共存对照。
  - `KnowledgeVersionService.activate` 替换 ACTIVE 时落 `SUPERSEDED_REPLACEMENT` 失效证据并派医师复核、包补同步、同步告警三类任务；高危 `WITHDRAWN` 仍禁止一键回滚。
- Phase 4 当前：自主公域知识获取后端闭环。
  - 新增 `engine.knowledge.acquisition`：`AcquisitionOrchestrationService` 仅允许 `PRODUCTION_CENTER` 手动触发；URL 必须命中已审批 allowlist、HTTPS、许可 `PERMITTED` 且 robots 策略允许。
  - V142 五方言新增 `mk_knowledge_acquisition_source` / `mk_knowledge_acquisition_run`，记录域名、A-E 权威、许可、robots 策略、审批人、真实 URL、抓取时点、sha256、资料 URI、解析 job 和状态。
  - `WebContentFetcher` / `RestWebContentFetcher` 真实抓取公开资料；获取内容进入既有 AIK-STD-02 解析链路和 P1 受管资料库，不新造存储。
  - 新增 `POST /api/v1/engine/knowledge/acquisition/runs`、`GET /api/v1/engine/knowledge/acquisition/{sources,runs}`，服务契约、产品功能目录和领域表归属已同步；请求可携带可选 `generation` 计划，把成功解析或重复复用的 `SourceVersion` 接入统一候选生成/审核池。
  - V143 五方言新增调度字段：`schedule_enabled_flag`、`schedule_interval_minutes`、`next_check_at`、`last_check_at`、`default_format`、`generation_plan_json`；默认关闭，不做旧数据回填。
  - `AcquisitionScheduleScheduler` / `AcquisitionScheduleWorker` 已接配置中心动态间隔，按到期白名单来源提交 SYS-05 `KNOWLEDGE_ACQUISITION_DISCOVERY` 批任务；任务 handler 调 `runScheduled`，失败项进入 SYS-05 失败明细、重试和死信闭环，不另建队列表。
  - DATASVC 新增 `fetchPublicMaterial` 受控工具（D1 / `knowledge.write`），只把 Agent/MCP/CLI 的结构化公域资料载荷转入既有获取编排；CLI 已接 `agent fetch-public-material`，MCP 沿动态工具目录暴露；D3/D4/D5 入参拒绝。
- Phase 5 已收口：LLM-01/LLM-02/LLM-04 模型网关、降级矩阵、版本三元组、质量评测、出域最小化、候选真实化与降级路径预验。
  - `model_capability_policy` 已按 134 全新清库口径改为 `scope_type/scope_ref` clean baseline；唯一键为 `tenant_id+capability_code+scope_type+scope_ref`，不保留旧租户唯一策略过渡层。
  - `ModelGatewayService` / `KnowledgeProductionReadinessService` 统一按当前组织链由近到远继承策略到租户；`getStatus` 返回策略来源与是否继承，前端 AI 工作流页展示策略来源。
  - T5.2 已补 `fallback_order_json`、`timeout_ms`、`rate_limit_per_minute` clean baseline；`ModelFallbackMatrix` 校验 B2→B1→B0 / B1→B0 顺序，运行时按顺序尝试 provider，并在 provider 调用前执行策略限流，失败归因串联到 `fallbackReason`，前端展示降级顺序和调用预算。
  - T5.3 已补 `mk_llm_model_version_bundle` V139 clean baseline、`model_capability_task.tool_version`、ACTIVE 版本包发布/回滚/hash-only 导出、B0 脱敏摘要重放、provider 成功任务真实 prompt/tool/model 三元组记录，以及服务层发布前载荷校验；空版本或空正文不再能先退役旧 ACTIVE。
  - T5.4 已补 OPT-06 AI 质量评测中心：V126 clean baseline 复用 `mk_llm_regression_case`/`mk_llm_eval_run` 增加质量维度、术语期望、禁用断言、最低分、质量/术语分、幻觉标记、case summary 与 prompt/tool/model 版本趋势；新增 `/api/v1/ai-eval/runs`、`/api/v1/ai-eval/trends`，支持离线 B0 输出或真实 provider 输出评测；真实领域题库只允许由真实来源导入，不预置伪医学题。
  - T5.5 已补 OPT-09 数据最小化策略引擎：V144 五方言在 `mk_llm_egress_whitelist` 扩展 `desensitization_rules`、`approval_threshold_level`、锁定式 `guardrail_locked_flag`；新增 `/api/v1/data-minimization/policies/model-egress/{capabilityCode}` 与 `/model-egress/approvals` 管理入口，统一复用 `llm.egress.manage` 权限和出域审计表。
  - T5.5 运行时护栏已接入 `ModelEgressGuard`：字段白名单外继续阻断，字段级 `MASK`/`MASK_ALL`/`GENERALIZE`/`NULLIFY`/`NONE` 策略在出域前执行；缺省或异常规则按最严 `MASK_ALL` 处理；审批门槛按 `LOW/MEDIUM/HIGH` 可配置，命中门槛且无有效审批时仍返回 `ENG_LLM_007`，不绕过既有证据链。
  - T5.6 已收口 API-12/LLM-01：模型能力网关 `status/catalog/tasks/retry/replay/policies` 端点、权限、OpenAPI、前端共享 hook 与 prompt/tool/model 三元组消费口径一致；补齐任务查询/重试跨租户拒绝证据，前端接入 B0 replay hook；`API-12` 与 `LLM-01` backlog 均为 done。
  - T5.7 已补候选真实化语义：`ModelKnowledgeProducer` 成功模型输出只产 DRAFT 候选并走同一门禁/分流/影子/提交链；payload 不落生产提示正文，仅落 `promptInputHash`、AI 标识、任务 ID、模型模式、prompt/tool/model 三元组、来源引用、模型输出和真实内容 hash；B2→B1 本地模型真实成功可入链并保留 fallback 证据，B0/非成功/readiness 未齐/schema 不合格仍跳过或阻断不产伪候选。
  - T5.8 已补降级路径预验证：非成功 B2/B1 模型任务返回诚实 `status/mode` 跳过原因，不再误写成 B0 降级；真正 B0 仍明确标注 B0。知识生产中心 readiness/job 主证据可见时，候选血缘、门禁、8 态、影子评测、共存提醒任一下游 evidence 读取失败会显示“生产证据部分读取失败”与分项错误，不用空表掩盖断连。
- Phase 6 已收口：院内覆盖管道全实现。
  - T6.1 已补院内上传增强：`DocumentParseController` 新增 multipart `POST /api/v1/engine/knowledge/documents:upload-parse`；上传原件复用 AIK-STD-02 解析与 P1 受管资料库存储，`DocumentMaterialStoreRequest.scopeKey=tenantId`，不落临时目录；可选生成计划只声明领域与物化目标，服务端用解析出的真实 `SourceVersion` 固定构造 `TENANT_OVERLAY` 候选生成请求，继续走门禁、8 态、影子评测和审核链，不新增平台主源写入口。
  - T6.2 已补本地模型生产器：`ModelKnowledgeProducer` 对 `LOCAL_MODEL` job 强制 `TENANT_OVERLAY` 院内覆盖，发现平台主源管道在 readiness/模型调用前即拒；模型网关 `ModelTaskRequest` 支持 `requiredRouteStrategy/providerCode`，知识生产器传入 `LOCAL_MODEL`/指定本地 provider，网关在 provider 解析前校验策略匹配并按指定 provider 走本地健康检查，策略漂移时不落任务、不外调、不伪造候选。
  - T6.3 已补双形态隔离强化测试：平台主源 job 收到客户候选时返回 `KNOWLEDGE_PRODUCTION_PIPELINE_VIOLATION`，不再落泛化 BAD_REQUEST；`t-1` 候选进入平台主源保持平台归属并路由平台知识治理员；客户租户不能把平台 `identityId` 当写入目标提交候选，只能读 effective 主源或走本租户覆盖。
  - T6.4 已补 DATASVC-01 D3/D4 字段级加密与分级元数据：V145 五方言新增 `mk_engine_data_encrypted_field` / `mk_engine_data_field_policy`，后端通过 `FieldLevelEncryptionService` 使用独立字段级密钥派生 SM4 密文、SM3 检索 hash、字段策略和审计凭证；`PrivacyPolicyService` 对 D3/D4 返回 `allowed=true` 且 `requiresFieldEncryption=true`，D5 仍禁入；Agent 写工具继续拒绝 D3/D4/D5，CLI/MCP 测试确认不绕治理。
- Phase 7 已收口：领域门面代码 + KNOWGEN 资产类型专用代码。
  - 新增 `engine.domainfacade` 只读目录：17 张 X-DOMAIN 卡按总计划顺序声明共享引擎链路、依赖卡、B0 工作流摘要、服务包成员、模型可选、不预填真实医学内容、不新增领域专属业务引擎。
  - 新增 `/api/v1/engine/domain-facades`、`/{code}`、`/b0-fixtures` 与 `/{code}/b0-fixture`，受 `knowledge.read` 与租户 DataScope 约束；B0 fixture 证据逐门面返回共享处理器、确定性路由、模型非必需、未预填医学内容、服务包成员可解析和扩展专科诚实空态；服务契约和产品功能目录已同步。
  - T7.2 已补 KNOWGEN 资产类型专用代码骨架：`FORMULA` 评分/计算器模板接入 `SourceCandidateGenerator` B0 候选生成；RULE 模板新增测试病例结构；新增 `KnowgenSpecializedAssetSkeletonRegistry` / `KnowgenSpecializedPayloadValidator` 覆盖 KNOWGEN-16/04/18/20/19；`ClinicalFormulaCalculatorService` 用传入公式定义确定性复算，不内置医学常量、不预填真实医学内容。
- Phase 8 当前已完成 T8.1：菜单 IA 双产品面。
  - 审核台 `/knowledge/governance` 只保留候选审核/发布边界，要求 `knowledge.review`；实施运维员不再进入审核台。
  - 机构知识独立为 `/knowledge/institution`，承载从平台标准派生机构版本、机构覆盖血缘和恢复平台标准；不再藏在审核页签里。
  - 知识生产独立为一级域 `/knowledge/production`，模型能力 `/advanced/ai-workflows` 改名并迁入“知识生产”；临床运行角色不可见，实施/集成运维员通过生产面查看 readiness/job/模型能力。
  - 后端同步 `MENU_INSTITUTION_KNOWLEDGE`、`MENU_KNOWLEDGE_PRODUCTION`、`menu.ai-workflows=查看模型能力`、V146 五方言迁移、14 角色菜单快照、产品功能目录、角色旅程和 IA 矩阵。
  - 同类问题审计结论：`/clinical/followup` 仍混有运行侧随访协同与治理侧模板治理，本批不纳入知识生产链路提交；后续应按“运行只做运行，模板治理独立归知识/配置治理”拆分。

## 最新验证

- Phase 8 T8.1 提交前验证：`cd frontend && npm test -- --run src/shared/config/routes.test.ts src/shared/config/menu.test.ts src/shared/config/productRoleJourneys.test.ts src/widgets/AppLayout.test.tsx src/pages/quality/KnowledgeGovernance.test.tsx`、`cd frontend && npm run typecheck`、`cd frontend && npm run lint`、`cd medkernel-backend && mvn -q -Dtest=MigrationBaselineContractTest,H2BaselineMigrationTest,DefaultPermissionPolicyTest,PermissionDimensionModelTest test`、`node scripts/b0-perfect-check.mjs`、`node scripts/audit/export-product-capabilities.mjs --check`、`git diff --check` 均退出 0；浏览器烟测 `/knowledge/institution`、`/knowledge/production`、`/advanced/ai-workflows`、`/knowledge/governance` 均非空渲染诚实权限失败态且无 console error。前端 lint 仅保留既有 warning。
- Phase 3 收口后：`MEDKERNEL_EVENTS_WORKER_ENABLED=false mvn -q test`、`node scripts/b0-perfect-check.mjs`、`git diff --check` 均曾退出 0。
- Phase 4 首片目标验证：`mvn -q -Dtest=AcquisitionOrchestrationServiceTest,AcquisitionControllerSecurityTest,CandidateGenerationOrchestrationServiceTest,CandidateGenerationIntegrationTest,MigrationBaselineContractTest,H2BaselineMigrationTest,ServiceContractGovernanceTest,OpenApiContractConfigurationTest,DomainOwnershipContractTest test` 已退出 0。
- Phase 4 调度目标验证：`mvn -q -Dtest=DefaultRuntimeTaskExecutorTest,AcquisitionRuntimeTaskHandlerTest,AcquisitionScheduleWorkerTest,AcquisitionScheduleSchedulerTest,KnowledgeAcquisitionSourceRepositoryTest,AcquisitionOrchestrationServiceTest,AcquisitionControllerSecurityTest,SystemConfigServiceTest#runtimeKnowledgeAcquisitionScheduleIntervalReadsConfigCenterAndFallsBackSafely,MigrationBaselineContractTest,H2BaselineMigrationTest,ServiceContractGovernanceTest,OpenApiContractConfigurationTest,DomainOwnershipContractTest test` 已退出 0。
- Phase 4 调度提交前全量验证：`MEDKERNEL_EVENTS_WORKER_ENABLED=false mvn -q test`、`node scripts/b0-perfect-check.mjs`、`node scripts/audit/export-product-capabilities.mjs --check`、`git diff --check` 均退出 0；旧状态扫描无命中。
- Phase 4 Agent 取数提交前全量验证：`MEDKERNEL_EVENTS_WORKER_ENABLED=false mvn -q test`、`node scripts/b0-perfect-check.mjs`、`node scripts/audit/export-product-capabilities.mjs --check`、`git diff --check`、`cd cli && npm test`、`cd mcp-server && npm test` 均退出 0；旧状态扫描无命中。
- Phase 5 T5.1 目标验证：`mvn -q -Dtest=ModelGatewayServiceTest,KnowledgeProductionReadinessServiceTest,ModelGatewayControllerTest,MigrationBaselineContractTest,H2BaselineMigrationTest test`、`cd frontend && npm test -- AiWorkflows.test.tsx` 均退出 0。
- Phase 5 T5.2 提交前验证：`mvn -q -Dtest=ModelGatewayServiceTest#submitTask_policyRateLimitExceededSkipsProviderAndFallsBackToB0 test` 红→绿；`mvn -q -Dtest=ModelGatewayServiceTest,ModelGatewayControllerTest,KnowledgeProductionReadinessServiceTest,MigrationBaselineContractTest,H2BaselineMigrationTest,OllamaProviderTest,ExternalProviderTest,ModelFallbackMatrixTest test`、`MEDKERNEL_EVENTS_WORKER_ENABLED=false mvn -q test`、`cd frontend && npm test -- AiWorkflows.test.tsx`、`cd frontend && npm run typecheck`、`node scripts/b0-perfect-check.mjs`、`node scripts/audit/export-product-capabilities.mjs --check`、`git diff --check` 均退出 0；旧状态扫描无命中。
- Phase 5 T5.3 提交前验证：`mvn -q -Dtest=ModelVersionGovernanceServiceTest#publishBundleRejectsBlankVersionPayloadBeforeRetiringActiveBundle test` 红→绿；`mvn -q -Dtest=ModelVersionGovernanceServiceTest,ModelVersionGovernanceControllerTest,ModelGatewayServiceTest,ModelGatewayControllerTest,KnowledgeProductionReadinessServiceTest,MigrationBaselineContractTest,H2BaselineMigrationTest,ServiceContractGovernanceTest test`、`cd medkernel-backend && MEDKERNEL_EVENTS_WORKER_ENABLED=false mvn -q test`、`node scripts/b0-perfect-check.mjs`、`node scripts/audit/export-product-capabilities.mjs --check`、`git diff --check` 均退出 0；旧状态扫描无命中。
- Phase 5 T5.4 提交前验证：AI 质量评测目标测试、V126 五方言迁移契约、`MEDKERNEL_EVENTS_WORKER_ENABLED=false mvn -q test`、`node scripts/b0-perfect-check.mjs`、`node scripts/audit/export-product-capabilities.mjs --check`、`git diff --check` 均退出 0；T5.4 范围旧构造器、旧表名、legacy/backfill/ROLLBACK 扫描无命中。
- Phase 5 T5.5 提交前验证：`mvn -q -Dtest=ModelEgressGuardTest,ModelEgressGovernanceServiceTest,ModelEgressGovernanceRepositoryTest,ModelEgressControllerSecurityTest,MigrationBaselineContractTest,H2BaselineMigrationTest,FlywayMultiDialectSmokeTest,ServiceContractGovernanceTest,OpenApiContractConfigurationTest test`、`MEDKERNEL_EVENTS_WORKER_ENABLED=false mvn -q test`、`node scripts/b0-perfect-check.mjs`、`node scripts/audit/export-product-capabilities.mjs --check`、`git diff --check` 均退出 0；OPT-09 旧 pending 口径、T5.5 未完成勾选、旧迁移版本哨兵、旧 data-min-policy 路径扫描无命中。
- Phase 5 T5.6 提交前验证：`cd frontend && npm test -- hooks.test.ts` 红灯命中缺失 `useReplayModelTask` 后转绿；`cd frontend && npm run typecheck`、`mvn -q -Dtest=ModelGatewayServiceTest test`、`mvn -q -Dtest=ModelGatewayServiceTest,ModelGatewayControllerTest,ModelGatewayControllerSecurityTest,ModelCapabilityDefinitionRepositoryTest,ServiceContractGovernanceTest,OpenApiContractConfigurationTest test`、`node scripts/b0-perfect-check.mjs`、`node scripts/audit/export-product-capabilities.mjs --check`、`git diff --check` 均退出 0；API-12/LLM-01 pending 口径和前端缺失 `toolVersion` 扫描无命中。
- Phase 5 T5.7 提交前验证：`mvn -q -Dtest=ModelKnowledgeProducerTest test` 红灯命中 prompt 原文落 payload 与 B1 fallback 被跳过后转绿；`mvn -q -Dtest=ModelKnowledgeProducerTest,KnowledgeProductionReadinessServiceTest,KnowledgeProductionControllerSecurityTest,KnowledgeProductionOrchestrationServiceTest,CandidateMaterializationIntegrationTest,CandidateProvenanceServiceTest,SourceCandidateGeneratorTest,CandidateGenerationOrchestrationServiceTest,CandidateGenerationIntegrationTest,KnowledgeGenerationTriageServiceTest,KnowledgeShadowEvaluationServiceTest test`、`node scripts/b0-perfect-check.mjs`、`node scripts/audit/export-product-capabilities.mjs --check`、`git diff --check` 均退出 0；T5.7 未勾、prompt 原文落候选 payload、B1 fallback 误跳过旧口径扫描无命中。
- Phase 5 T5.8 提交前验证：`mvn -q -Dtest=ModelKnowledgeProducerTest test` 红灯命中 provider 非成功 B2 被误写 B0 降级后转绿；`cd frontend && npm test -- KnowledgeGovernance.test.tsx` 红灯命中下游 evidence 失败无局部告警后转绿；`mvn -q -Dtest=ModelKnowledgeProducerTest,KnowledgeProductionReadinessServiceTest,KnowledgeProductionControllerSecurityTest,KnowledgeProductionOrchestrationServiceTest,CandidateMaterializationIntegrationTest,CandidateProvenanceServiceTest,SourceCandidateGeneratorTest,CandidateGenerationOrchestrationServiceTest,CandidateGenerationIntegrationTest,KnowledgeGenerationTriageServiceTest,KnowledgeShadowEvaluationServiceTest,ModelGatewayServiceTest,ModelFallbackMatrixTest test`、`cd frontend && npm test -- KnowledgeGovernance.test.tsx AiWorkflows.test.tsx`、`cd frontend && npm run typecheck`、`node scripts/b0-perfect-check.mjs`、`node scripts/audit/export-product-capabilities.mjs --check` 均退出 0。
- Phase 6 T6.1 提交前验证：`mvn -q -Dtest=DocumentParseOrchestrationServiceTest test` 红灯命中缺失院内上传响应/生成计划/服务方法后转绿；`mvn -q -Dtest=DocumentParseControllerSecurityTest test` 红灯命中缺失 multipart 上传端点后转绿；`mvn -q -Dtest=DocumentParseIntegrationTest,CandidateGenerationIntegrationTest test`、`mvn -q -Dtest=DocumentParseOrchestrationServiceTest,DocumentParseControllerSecurityTest,CandidateGenerationOrchestrationServiceTest test`、`mvn -q -Dtest=DocumentParseOrchestrationServiceTest,DocumentParseControllerSecurityTest,DocumentParseIntegrationTest,CandidateGenerationOrchestrationServiceTest,CandidateGenerationIntegrationTest,ServiceContractGovernanceTest,OpenApiContractConfigurationTest,DomainOwnershipContractTest test`、`node scripts/b0-perfect-check.mjs`、`node scripts/audit/export-product-capabilities.mjs --check`、`git diff --check` 均退出 0。
- Phase 6 T6.2 提交前验证：`mvn -q -Dtest=ModelKnowledgeProducerTest,ModelGatewayServiceTest test` 红灯命中 `ModelTaskRequest` 缺失路由/provider 约束字段后转绿；`mvn -q -Dtest=ModelKnowledgeProducerTest,ModelGatewayServiceTest,ModelProviderRegistryTest test`、`mvn -q -Dtest=ModelKnowledgeProducerTest,ModelGatewayServiceTest,ModelProviderRegistryTest,KnowledgeProductionReadinessServiceTest,ModelGatewayControllerTest,ModelGatewayControllerSecurityTest,OpenApiContractConfigurationTest,ServiceContractGovernanceTest,DomainOwnershipContractTest test`、`cd frontend && npm test -- hooks.test.ts`、`cd frontend && npm run typecheck`、`node scripts/b0-perfect-check.mjs`、`node scripts/audit/export-product-capabilities.mjs --check` 均退出 0。
- Phase 6 T6.3 提交前验证：`mvn -q -Dtest=KnowledgeProductionOrchestrationServiceTest#submitCandidateRejectsCustomerCandidateIntoPlatformSourcePipelineAsReadOnlyViolation test` 红灯命中平台主源客户候选仅返回 BAD_REQUEST 后转绿；`mvn -q -Dtest=KnowledgeVersionServiceTest#classifyCandidateInCustomerTenantTreatsPlatformIdentityAsReadOnly test`、`mvn -q -Dtest=KnowledgeProductionOrchestrationServiceTest,KnowledgeVersionServiceTest,CandidateReviewRouterTest test`、`mvn -q -Dtest=KnowledgeProductionOrchestrationServiceTest,KnowledgeVersionServiceTest,CandidateReviewRouterTest,CandidateGenerationOrchestrationServiceTest,DocumentParseOrchestrationServiceTest,ModelKnowledgeProducerTest,ControlledToolServiceTest,CandidateMaterializationIntegrationTest,DomainOwnershipContractTest,ServiceContractGovernanceTest,OpenApiContractConfigurationTest test` 均退出 0。
- Phase 6 T6.4 提交前验证：`mvn -q -Dtest=PrivacyPolicyServiceTest,FieldLevelEncryptionServiceTest,ControlledToolServiceTest#execute_submitProductionCandidate_rejectsD4PatientDataBeforeSubmit test` 红灯命中 D3/D4 隐私策略与字段级加密服务/实体缺口后转绿；`mvn -q -Dtest=PrivacyPolicyServiceTest,FieldLevelEncryptionServiceTest,FieldLevelEncryptionRepositoryIntegrationTest,ControlledToolServiceTest#execute_submitProductionCandidate_rejectsD4PatientDataBeforeSubmit test`、`mvn -q -Dtest=MigrationBaselineContractTest,H2BaselineMigrationTest test`、`mvn -q -Dtest=PrivacyPolicyServiceTest,FieldLevelEncryptionServiceTest,FieldLevelEncryptionRepositoryIntegrationTest,ControlledToolServiceTest,ClinicalContextServiceTest,MigrationBaselineContractTest,H2BaselineMigrationTest,ServiceContractGovernanceTest,OpenApiContractConfigurationTest,DomainOwnershipContractTest test`、`cd cli && npm test`、`cd mcp-server && npm test`、`MEDKERNEL_EVENTS_WORKER_ENABLED=false mvn -q test`、`node scripts/b0-perfect-check.mjs`、`node scripts/audit/export-product-capabilities.mjs --check`、`git diff --check` 均退出 0；字段级加密旧 pending 口径扫描无命中。
- Phase 7 T7.1 验证：`mvn -q -Dtest=DomainFacadeCatalogServiceTest,DomainFacadeControllerSecurityTest test` 红灯命中领域门面目录服务/定义/引擎枚举缺失后转绿；`mvn -q -Dtest=DomainFacadeB0FixtureServiceTest,DomainFacadeApiContractTest test` 红灯命中 B0 fixture 服务/证据缺失后转绿；`mvn -q -Dtest=DomainFacadeCatalogServiceTest,DomainFacadeB0FixtureServiceTest,DomainFacadeControllerSecurityTest,DomainFacadeApiContractTest,ServiceContractGovernanceTest,OpenApiContractConfigurationTest test`、`node scripts/b0-perfect-check.mjs`、`node scripts/audit/export-product-capabilities.mjs --check`、`git diff --check` 均退出 0。
- Phase 7 T7.2 验证：`mvn -q -Dtest=ProfessionalAssetTemplateRegistryTest,SourceCandidateGeneratorTest,KnowgenSpecializedAssetSkeletonRegistryTest,KnowgenSpecializedPayloadValidatorTest,ClinicalFormulaCalculatorServiceTest test` 红灯命中 FORMULA 模板、专用骨架、payload 校验器和公式计算服务缺失后转绿；补结构化 `inputs`/`test_vectors` 回归后该命令仍退出 0；`mvn -q -Dtest=ProfessionalAssetTemplateRegistryTest,KnowledgeAssetSchemaValidatorTest,SourceCandidateGeneratorTest,KnowgenSpecializedAssetSkeletonRegistryTest,KnowgenSpecializedPayloadValidatorTest,ClinicalFormulaCalculatorServiceTest,CandidateGenerationOrchestrationServiceTest,CandidateGenerationIntegrationTest,KnowledgeProductionControllerSecurityTest test`、`node scripts/b0-perfect-check.mjs`、`node scripts/audit/export-product-capabilities.mjs --check`、`git diff --check` 均退出 0。

## 仍不可宣称

- 不得宣称正式知识生产已开放：P6 独立验收、真实 provider/凭据、真实医学基准评测、出域白名单、版本三元组和专家验收未全部现场闭环前，只能产受控候选和工程证据。
- 不得宣称 134 已部署最新主线：未进入 P9 并完成清库全新初始化、部署和 readiness 留证前不得冒领。
- 不得宣称 KNOWGEN 首发知识包或试点医院上线完成：这些属于 P10/P11，必须发生在生产中心真实上线之后。
- 不得宣称 Phase 4 现场验收全部完成：手动/调度/MCP/CLI 公域获取→解析→可选候选生成触发已完成；真实生产中心联调和更细出域审批证据仍待 P5/P9 验证。

## 下一步

1. 继续 Phase 8：进入 T8.2 生产者工作台，围绕下任务→看进度→审候选→批处置，继续保持审核/机构知识/生产面职责边界。
2. 每个切片仍按 TDD：先失败测试 → 实现 → 验绿 → 门禁 → 本地提交。
3. 新增表/端点继续同步五方言迁移、领域归属、服务契约、产品目录和中文注释门禁。

## 常用指针

- 协作规则：`AGENTS.md`
- 产品红线：[`docs/CONSTITUTION.md`](CONSTITUTION.md)
- 体验契约：[`docs/EXPERIENCE_CONTRACT.md`](EXPERIENCE_CONTRACT.md)
- 质量基线：[`docs/audit/质量基线.md`](audit/质量基线.md)
- 当前计划：[`docs/superpowers/plans/2026-06-16-autonomous-knowledge-production-golive-master-plan.md`](superpowers/plans/2026-06-16-autonomous-knowledge-production-golive-master-plan.md)
- backlog Phase 对照：[`docs/backlog.md`](backlog.md)
