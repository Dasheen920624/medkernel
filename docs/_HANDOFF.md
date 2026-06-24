# 会话接力

> 开工先读本文件。这里只保留当前执行事实；产品范围见
> [PRODUCT_SCOPE](PRODUCT_SCOPE.md)，历史过程使用 Git 追溯。

## 当前主线

- 基线：PR #647 合并提交 `acd511c0`；
- 分支：`codex/647-launch-simplification`；
- worktree：`/Users/zhikunzheng/.config/superpowers/worktrees/codex3/647-launch-simplification`；
- 不读取、比较或继承 #648、#649；
- 用户已明确冻结远程动作：只保留本地分支、本地验证和本地提交，不推送、不创建 PR、不触碰
  远程 `main`，直至用户后续明确授权；
- 项目尚未上线，不兼容旧角色、旧迁移、旧包发布链路、旧接口或旧文档；
- 目标是完整上线，保留真实页面与业务能力，不把用户举例缩成产品边界。
- 原始 #647 的有效诉求已经统一吸收到 `PRODUCT_SCOPE.md`：五种交付形态、完整组织拓扑、七类
  业务组合、完整医疗语义、专病十阶段、全中枢模型赋能、第三方系统矩阵和中国医院落地约束；
  旧原文、旧卡、旧计划、阶段审计和历史截图不再保留。

## 当前唯一模型

- 产品按六层、S0–S40 和全医疗专业领域验收；
- 客户可分配职责只有平台管理员、医疗引擎运营员、临床使用者和审计员；
- 13 类标准患者资源承接院内真实数据；
- 13 类版本化资产共用稳定身份、自动版本、精确依赖和最小发布流；
- 资产稳定身份状态为 `ACTIVE/RETIRED`，内容版本状态为
  `DRAFT/PUBLISHED/WITHDRAWN`；
- 平台发布不可变平台标准版本，机构生成不可变机构生效版本，离线交付文件只负责传输和恢复；
- 临床调用方不提交包、领域或版本，服务端锁定当前机构生效版本；
- 大模型只生成候选和解释，关系库是权威源，图只是可重建投影；
- 辅助诊疗是上位能力，推荐诊断与鉴别诊断是并列、可组合的诊断子能力。

## 已完成并有定向证据

- 44 条路由、54 个页面组件和 34 个菜单保留；
- 三产品空间和四职责已建立，旧角色兼容、机构权限覆盖和主要人员分离门阀已移除；
- 多因素认证默认关闭，开启后使用真实 TOTP 和受限绑定会话；
- 双签、委员会、独立专家签字及模型跨角色等待门槛已移除；
- 条件片段、子路径、路径继承和循环引用已删除；路径只单向调用规则；
- 规则与路径已支持稳定身份、服务端自动 V1/V2/V3、发布版本不可变和独立下一版草稿；
- 规则、路径、诊断、临床事件、上下文快照和互操作证据已切到机构生效版本/证据导出模型；
- 大模型知识、规则和路径候选已统一进入正式草稿入口并执行类型校验；
- 诊断支持只消费机构生效版本锁定的诊断知识，响应包含鉴别诊断依据；
- 数据库已由一份模式源生成 PostgreSQL、Kingbase、Oracle、达梦和 H2 的单一 V1；
- 当前模式模型为 207 张表；H2、PostgreSQL 和 Oracle 迁移验证曾通过，最终结构变更后必须重跑；
- 前端与后端多组定向测试曾通过，具体结果只作为阶段证据，不能代替最终全量门禁。

## 2026-06-24 阶段检查点

本阶段是本地检查点，不是上线完成声明；长任务目标继续保持 active，下一会话从本文件续接即可。

已完成的新增收口：

- `SandboxCurrentRuleExecutor` 已按冻结机构生效版本 `releaseId` 物化规则 DSL 中的
  `VALUE_SET` / `FORMULA` / `ACTION_CARD` 引用；
- `SandboxReplayRuleExecutor` 已改为只从不可变历史重放清单的资产绑定快照物化引用，缺失资产或重复资产
  诚实报错，不查询当前激活资产；
- `RuleReleaseSimulationReplayEvaluator` 已按每个真实上下文快照的 `runtimeReleaseId` 做发布模拟回放；
- `AuthoringPreviewRunService` 已按请求机构 + 所选真实快照 `runtimeReleaseId` 做草稿规则即配即试；
- `RecommendationDeterministicMatcher` 已按请求机构 + 快照 `runtimeReleaseId` 生成确定性推荐卡，平台主源规则也
  通过当前机构生效版本叠层解析临床提示卡；
- 复扫生产代码后，正式运行入口已不再裸跑 `RuleDslEvaluator.evaluate(dsl, context)`；剩余两参调用仅用于
  规则测试/回放样例和静态 DSL 校验，不代表临床运行链路。
- 灰度暂停通知深链已从旧 `/tenant/packages` 切到发布治理页 `/config/releases`；
- 整套上线演练沙盘阶段已从旧“上线容器”改为“机构生效版本”，全知识演练夹具同步到当前 11 个知识域
  `DIAGNOSTIC_ITEM`；
- 审计导出、质控导出和产品范围里的用户可见旧“证据导出包”口径已收敛为“证据导出”；
- 质控驾驶舱下钻响应已从旧 `QualityEvidencePackage/evidencePackage` 收敛为
  `QualityEvidenceExport/evidenceExport`；证据导出载荷补齐真实 SHA-256 证据范围摘要，前台显示为
  “证据范围摘要”，不再把 `scopeDigest` 字段名直接展示给用户。
- 复扫生产源码、前端源码、脚本和契约文档后，旧 `packageId/packageVersion`、旧 `/engine/pkg/packages`
  路径和旧上线容器词只保留在历史负向护栏说明中。
- `ClinicalRuntimeDeclarativeAssetResolver` 已允许 `FIELD_CATALOG` 按机构生效版本解析不可变正文；
- 新增 `RuntimeReleaseFieldCatalogResolver`，将字段目录资产正文恢复为 `ContextFieldDescriptor`；
- 第三方数据接入契约已改为只从当前机构生效版本的字段目录资产生成，不再读取当前字段目录工作区。
- 手工规则/路径 DSL 中的 `field` / `fact` 引用已统一折叠为
  `FIELD.CATALOG.CLINICAL_CONTEXT` 资产依赖；同一条件对象同时引用字段和值集/公式时会同时登记字段目录与
  对应运行资产依赖，避免机构生效版本装配时漏激活字段目录。
- 发布模拟对没有专门病例级回放执行器的版本化资产已不再返回阻断式 `UNSUPPORTED`；现在基于资产依赖图返回
  目标组织和适用范围内的在用依赖资产，并以“依赖影响评估”说明该类资产不执行病例级重算，避免术语、
  字段目录、值集、公式等基础资产在有历史快照时被旧“仅规则回放”模型卡住。
- 发布治理页已接入真实发布影响评估：候选资产列表带出适用人群或上下文，机构运营员选择集团/本院内容后可在
  生成机构生效版本前调用 `/engine/versioning/releases/simulations`，并用“可发布/需处理、病例回放、
  依赖影响、阻断原因”等医疗产品语言展示结果，不再让前台猜测评估域。
- 医技报告解读已从“部分数据骨架”补成运行闭环：服务端只接收已生效标准上下文快照，按快照锁定的
  `runtimeReleaseId` 读取机构生效版本中的 `DIAGNOSTIC_ITEM` 医技项目说明书，输出辅助解读与复核建议；
  解读结果会通过推荐引擎生成临床提示卡，不改写已签发报告、不自动开立医嘱。前台“提醒与推荐中枢”
  已新增“生成报告解读”入口，共用快照选择器，不暴露包、版本或知识域选择器。
- 前台产品语言门禁已扩展到启动凭证、来源允许范围、模型版本组合、运行环境、无模型规则链路、
  多因素认证、生产前校验、生产安全校验、发布质量校验、发布验证用例、开通条件和时窗校验等
  医疗引擎中枢语言。
- 2026-06-24 已纠偏一次过度通俗化：前台不再把专业治理词机械替成“检查”，也不再把 MFA 写成
  “安全验证码”或把 B0 写成“基础规则模式”。后续表达按医疗产品体验师视角处理：
  保留医疗与治理专业性，隐藏实现代号；按用户任务表达条件、风险、校验和结果。
- 模型服务、知识生产、诊断知识发布、规则/路径发布、机构开通、安全设置和国产化自检的前后台文案已按
  “用户可理解且专业准确”口径同步；技术枚举可留在专家模式、日志、测试数据和工程契约，不应直接进入前台。
- 用户最新反馈已明确：MedKernel 是医疗引擎中枢系统，前台字典和页面语言必须从医疗产品体验师视角优化，
  不是机械替换同义词，更不能为了“通俗”损失临床、治理和机构运行含义。后续前台表达默认采用医生、
  医疗引擎运营员、机构管理员和审计员能理解的业务语言；如“机构生效版本”“发布质量校验”
  “生产安全校验”“红线风险”“临床提示卡”等可作为专业产品词保留，内部枚举和实现代号只放专家模式或工程契约。
- 声明式资产工作台和规则 L2 编辑器已把旧“动作卡/动作码/建议/卡片动作”等泛化技术词收敛为
  “临床提示卡”“命中后处理”“医生可选操作”“风险等级”“提醒等级”“依据名称/链接”等面向用户的医疗业务词；
  值集、计算公式、医嘱套餐和临床提示卡的作者选项抽到
  `frontend/src/shared/config/declarativeAssetAuthoring.ts`，避免页面内散落重复字典。
- 前台与外部可见字典已继续按医疗引擎中枢产品语境扩展到“计算公式”“医嘱套餐”“临床提示卡”
  “命中后处理”等业务词；前端页面、共享字典、后端校验/权限/模板/错误消息和五方言 V1 注释已同步，
  旧技术词仅允许出现在翻译兜底、语言门禁、负向测试和历史接力说明中。
- 当前上线候选明确为简体中文产品体验，不提供多语言版本或语言切换入口；已移除前端未落地的
  `i18next` / `i18next-browser-languagedetector` / `react-i18next` 运行依赖，并新增护栏避免依赖层
  继续暗示已支持多语言。
- 领域归属契约已补齐 `engine-domaincatalog` 以及资产身份、资产验证记录、资产触发绑定表归属；候选生成、
  候选物化、价值指标、质控看板和全链路 E2E 的测试前置数据已对齐真实租户根组织、机构生效版本外键和
  清单哈希算法，不再靠裸字符串或占位哈希绕过关系库权威。

本阶段新鲜验证证据：

- 红灯已分别复现临床提示卡引用未物化时的 `规则 DSL 缺少字段: atSeverity`：
  `SandboxCurrentRuleExecutorTest#materializesActionCardReferenceFromTheFrozenRuntimeRelease`、
  `SandboxReplayRuleExecutorTest#materializesActionCardReferenceFromHistoricalReplayAssetSnapshot`、
  `AuthoringPreviewRunServiceTest#previewRunsDraftRuleWithActionCardFromSnapshotRuntimeRelease`、
  `RecommendationDeterministicMatcherTest#materializesActionCardFromSnapshotRuntimeReleaseWhenBuildingRecommendation`；
- 发布模拟护栏先改为只接受
  `evaluate(dsl, context, "tenant-A", "runtime-release-test")`，旧实现红灯为空评估结果；
- 后端消费者闭环：
  `mvn -q -Dtest=SandboxCurrentRuleExecutorTest,SandboxReplayRuleExecutorTest,RuleReleaseSimulationReplayEvaluatorTest,AuthoringPreviewRunServiceTest,RecommendationDeterministicMatcherTest test`
  通过；
- 规则服务相关回归：
  `mvn -q -Dtest=RuleEngineServiceTest,RuleDslAssetMaterializerTest,RuleDslEvaluatorTest,RecommendationDeterministicMatcherTest,SandboxCurrentRuleExecutorTest,SandboxReplayRuleExecutorTest,RuleReleaseSimulationReplayEvaluatorTest,AuthoringPreviewRunServiceTest test`
  通过。
- 灰度通知旧深链红灯：
  `RolloutWorkflowNotificationAdapterTest` 先失败于实际值 `/tenant/packages?releasePlanId=vrl-1`，修复后
  `mvn -q -Dtest=RolloutWorkflowNotificationAdapterTest test` 通过；
- 上线演练旧“上线容器”红灯：
  `node --test scripts/release/full-system-rehearsal.test.mjs` 先失败于沙盘阶段仍显示旧上线容器口径，修复后
  5 个测试通过；
- 审计权限旧“证据导出包”红灯：
  `PermissionCodeTest` 先失败于显示名仍显示旧证据导出口径，修复后
  `mvn -q -Dtest=PermissionCodeTest test` 通过。
- 字段目录运行正文红灯：
  `mvn -q -Dtest=ClinicalRuntimeDeclarativeAssetResolverTest,RuntimeReleaseFieldCatalogResolverTest,IntegrationDataContractServiceTest test`
  先失败于缺少 `RuntimeReleaseFieldCatalogResolver`；修复后通过；
- 字段目录相关回归：
  `mvn -q -Dtest=ContextFieldCatalogDraftServiceTest,ContextFieldCatalogServiceMergeTest,ContextFieldCatalogControllerTest,ClinicalRuntimeReleaseServiceTest,ClinicalRuntimeDeclarativeAssetResolverTest,RuntimeReleaseFieldCatalogResolverTest,IntegrationDataContractServiceTest,TerminologyCoverageGateTest,RuleDslAssetMaterializerTest test`
  通过。
- 手工规则字段目录依赖红灯：
  `mvn -q -Dtest=AssetReferenceConsistencyTest,RuleEngineServiceTest#createRuleRegistersStableRuntimeAssetDependenciesFromDsl+createRuleAcceptsActionCardReferenceAndRegistersRuntimeDependency test`
  先失败于规则资产只登记 `VALUE_SET` / `ACTION_CARD`、未登记字段目录；修复后通过；
- 同节点字段 + 值集引用红灯：
  `mvn -q -Dtest=AssetReferenceConsistencyTest#extractsTypedRuntimeAssetReferencesFromNestedDefinitions test`
  先失败于只抽取 `VALUE_SET`、未抽取 `FIELD_CATALOG`；修复后通过；
- 资产依赖与机构生效版本装配回归：
  `mvn -q -Dtest=AssetReferenceConsistencyTest,RuleEngineServiceTest,AssetDependencyServiceTest,RuleVersionedAssetAdapterTest,PathwayVersionedAssetAdapterTest,ClinicalRuntimeReleaseServiceTest,AssetAuthoringRegistryTest test`
  通过。
- 非规则类资产发布模拟红灯/绿灯：
  `mvn -q -Dtest=ReleaseSimulationServiceTest#usesDependencyImpactReplayForAssetsWithoutDedicatedCaseEvaluator,AssetDependencyServiceTest#listsPublishedDependentsInTargetScopeForReleaseImpact test`
  先失败于缺少 `activeDependentsOf` 和 `impactedAssets`；修复后通过；
  `mvn -q -Dtest=ReleaseSimulationServiceTest,AssetDependencyServiceTest,RuleReleaseSimulationReplayEvaluatorTest,ReleaseGovernanceControllerTest,VersioningCommandContractTest test`
  通过；`mvn -q -DskipTests compile` 通过；`git diff --check` 通过。生产代码已无
  `尚未接入确定性历史回放执行器` 默认提示残留。
- 产品语言门禁红灯/绿灯：
  `npm test -- --run src/shared/config/customerLanguageGate.test.ts` 先失败于启动凭证、来源允许清单、医学公式和生产前校验等
  前台可见旧技术文案；修复后 4 个测试通过；
- 模型与发布治理相关回归：
  `mvn -q -Dtest=KnowledgeProductionReadinessServiceTest,KnowledgeProductionReleaseStateMachineIntegrationTest,ReleaseModelContractTest,RuntimeArchitectureCleanlinessTest,RuleDslAssetMaterializerTest,ClinicalRuntimeDeclarativeAssetResolverTest,MigrationBaselineContractTest,FormalKnowledgeProductionPolicyTest,ModelGatewayServiceTest,ModelProviderGovernanceServiceTest,ModelVersionGovernanceServiceTest,H2BaselineMigrationTest test`
  通过。
- 用户可理解语言扩展回归：
  `npm test -- --run src/shared/config/customerLanguageGate.test.ts src/pages/quality/KnowledgeGovernance.test.tsx src/pages/quality/DiagnosisKnowledgePanel.test.tsx src/pages/tenant/RuleDefinitions.test.tsx src/pages/tenant/RulePathwayCleanliness.test.ts src/pages/knowledge-production/ProductionReadinessPanel.test.tsx src/pages/knowledge-production/ModelProductionConsole.test.tsx src/pages/knowledge-production/ProviderSetupPanel.test.tsx src/pages/workbench/ReadinessValidation.test.tsx`
  9 个文件 / 105 个用例通过；
- 医疗产品语言纠偏回归：
  `npm test -- --run src/shared/config/customerLanguageGate.test.ts src/pages/advanced/AiWorkflows.test.tsx src/pages/knowledge-production/ModelProductionConsole.test.tsx src/pages/knowledge-production/ProductionReadinessPanel.test.tsx src/pages/quality/KnowledgeGovernance.test.tsx src/pages/quality/DiagnosisKnowledgePanel.test.tsx src/pages/tenant/RuleDefinitions.test.tsx src/pages/tenant/TenantOnboarding.test.tsx src/pages/tenant/PathwayTemplates.test.tsx src/pages/Bootstrap.test.tsx src/pages/Login.test.tsx src/pages/compliance/SecurityBaseline.test.tsx src/pages/operationalControlPages.test.tsx src/widgets/AppLayout.test.tsx src/shared/api/hooks.test.ts src/shared/lib/browserCompatibility.test.ts`
  16 个文件 / 289 个用例通过；
- 后端产品语言与契约回归：
  `mvn -q -Dtest=EmbedEngineServiceTest,EmbedEngineControllerTest,EmbedEngineExternalHostTest,EmbedLaunchTokenRepositoryTest,ClinicalContextServiceTest,AcquisitionOrchestrationServiceTest,KnowledgeProductionReadinessServiceTest,ModelKnowledgeProducerTest,CandidateSafetyGateServiceTest,CandidateSafetyGateIntegrationTest,ModelEgressGovernanceServiceTest,ModelEgressGuardTest,ModelEgressGovernanceRepositoryTest,ModelGatewayServiceTest,ModelProviderGovernanceServiceTest,ModelProviderRegistryTest,ModelFallbackMatrixTest,DiagnosisKnowledgeServiceTest,DiagnosisKnowledgeApiContractTest,H2BaselineMigrationTest,IntegrationContractDocumentationTest,PermissionCodeTest test`
  通过。
- 后端医疗产品语言纠偏回归：
  `mvn -q -Dtest=DiagnosisKnowledgeServiceTest,ModelKnowledgeProducerTest,PublicationQualityRecordServiceTest,VersionReleaseServiceTest,RuleEngineServiceTest,SystemConfigControllerTest,SecurityMeControllerTest,AuthControllerTest test`
  通过。
- 构建与迁移轻量核查：
  `npm run build` 通过；`mvn -q -DskipTests compile` 通过；
  `node scripts/db/generate-migrations.mjs --check` 通过；`git diff --check` 通过。
- 本轮医疗产品语言与真实约束收口定向验证：
  `npm test -- DeclarativeAssetWorkbench.test.tsx declarativeAssetAuthoring.test.ts RuleDefinitions.test.tsx ruleLayeredEditor.test.ts`
  通过，4 个文件 / 40 个测试通过；
  `mvn -q -Dtest=CandidateMaterializationIntegrationTest,CandidateGenerationIntegrationTest,QualityDashboardServiceTest,ValueMetricsServiceTest,EngineEndToEndIntegrationTest,ContextSnapshotTraceEndToEndTest test`
  通过；
  `mvn -q -Dtest=DomainOwnershipContractTest test` 通过；
- 本轮全量门禁：
  `mvn -q test` 通过（本机 Docker 不可用导致 Testcontainers 输出环境检测错误日志，但 Maven 退出码为 0）；
  `npm run verify` 通过，前端 lint / stylelint / 真实性规则 / format / typecheck / Vitest 全部完成，
  Vitest 汇总为 106 个测试文件、763 个测试通过。
- 医疗产品语言扩展后的后端定向回归：
  `mvn -q -Dtest=DeclarativeAssetContentValidatorTest,RecommendationDeterministicMatcherTest,PathwayEngineServiceTest,RuleDslEvaluatorTest test`
  先暴露规则断言仍期望旧动作表述，修正为临床提示卡口径后通过；
- 收口核查：`git diff --check` 通过；残留旧前台词扫描只命中翻译兜底、语言门禁、负向测试断言和历史接力说明，
  未发现生产前台页面或后端外部消息继续直接暴露旧技术词。
- 简体中文产品版边界红灯/绿灯：
  `npm test -- --run src/shared/config/i18nLaunchBoundary.test.ts` 先失败于仍声明未使用的
  `i18next` / `i18next-browser-languagedetector` / `react-i18next`；移除依赖并更新体验契约后通过；
  `npm test -- --run src/shared/config/i18nLaunchBoundary.test.ts src/shared/config/customerLanguageGate.test.ts src/widgets/AppLayout.test.tsx`
  3 个文件 / 29 个测试通过；`npm run verify` 通过，前端汇总 107 个测试文件 / 764 个测试通过；
  `npm run build` 通过；`git diff --check` 通过。
- 质控证据导出契约红灯/绿灯：
  `mvn -q -Dtest=QualityDashboardServiceTest#drilldownReturnsTraceableEvidencePackageForFindings test`
  先失败于响应仍序列化旧 `evidencePackage` 字段；
  `mvn -q -Dtest=QualityDashboardServiceTest#drilldownReturnsTraceableEvidenceExportForFindings test`
  又暴露证据导出缺少真实 `scopeDigest`；
  修复后 `mvn -q -Dtest=QualityDashboardServiceTest#drilldownReturnsTraceableEvidenceExportForFindings,QualityDashboardControllerSecurityTest test`
  通过，`mvn -q -Dtest=QualityDashboardServiceTest,QualityDashboardControllerSecurityTest,MigrationBaselineContractTest test`
  通过；`npm test -- --run src/pages/quality/QcDashboard.test.tsx src/shared/api/hooks.test.ts`
  2 个文件 / 122 个测试通过；`mvn -q -DskipTests compile` 通过；`npm run verify` 通过，前端汇总
  107 个测试文件 / 764 个测试通过；`git diff --check` 通过。生产源码已无旧 `evidencePackage`
  字段或用户可见 `scopeDigest：` 标签，旧词只留历史说明和负向护栏。
- 部署与演练脚本本地契约核查：
  `bash deploy/onprem/tests/validate-medkernel-fresh-deploy.sh` 通过；
  `bash deploy/onprem/tests/validate-medkernel-post-rehearsal-verify.sh` 通过；
  `bash deploy/onprem/tests/validate-medkernel-failure-recovery.sh` 通过；
  `node --test scripts/release/full-system-rehearsal.test.mjs scripts/release/runtime-resilience-rehearsal.test.mjs scripts/release/launch-account-bootstrap.test.mjs scripts/release/model-provider-launch.test.mjs scripts/knowledge/full-knowledge-rehearsal.test.mjs scripts/sandbox/seed-scenarios.test.mjs`
  36 个用例通过；
- 清库部署文档已同步 `--external-base-url`，单机手册的业务表数改为从候选 schema 读取；
  当前候选 schema 为 207 张业务表，`node scripts/db/generate-migrations.mjs --check` 通过。
- 发布治理页影响评估红灯/绿灯：
  `npm test -- --run src/pages/tenant/ReleaseGovernance.test.tsx src/shared/api/hooks.test.ts` 先失败于页面没有
  “评估发布影响”按钮；`mvn -q -Dtest=ReleaseCandidateQueryServiceTest test` 先失败于候选资产响应缺少
  `applicableScope()`；修复后前端 2 个文件 / 126 个用例通过，后端候选查询测试通过，并补齐“未评估的集团/
  本院内容不能直接生成机构生效版本”的前台安全门禁；
  `mvn -q -Dtest=ReleaseCandidateQueryServiceTest,RuntimeReleaseControllerTest,ReleaseGovernanceControllerTest,ReleaseSimulationServiceTest test`
  通过；`mvn -q -DskipTests compile` 通过；`npm run verify` 通过，前端汇总 107 个测试文件 / 767 个测试通过；
  `npm run build` 通过；`git diff --check` 通过。本轮 `npm run verify` 中曾暴露 Hook 依赖稳定性与 Prettier
  格式问题，已修复后复跑通过。
- 医技报告解读运行闭环红灯/绿灯：
  `mvn -q -Dtest=RuntimeReleaseDiagnosticItemSelectorTest,ReportInterpretationServiceTest test` 先失败于缺少
  运行选择器、解读服务和请求/响应契约；补齐后又暴露检验报告类型被小写归一后误归为检查类，修复后通过；
  `mvn -q -Dtest=RuntimeReleaseDiagnosticItemSelectorTest,ReportInterpretationServiceTest,ReportInterpretationControllerSecurityTest test`
  通过，覆盖机构生效版本医技项目说明书选择、未激活版本拒绝、空态不误判无风险、临床提示卡持久化、
  未认证/审计员/访客/缺租户安全门；
  `npm test -- --run src/pages/clinical/CdssFatigue.test.tsx src/shared/api/hooks.test.ts` 通过，
  2 个文件 / 127 个测试通过，覆盖前台从已生效快照生成报告解读且不显示触发时点或版本选择器；
  `mvn -q -Dtest=RuntimeReleaseDiagnosticItemSelectorTest,ReportInterpretationServiceTest,ReportInterpretationControllerSecurityTest,RecommendationEngineControllerSecurityTest test`
  通过；`mvn -q -DskipTests compile` 通过；`npm run lint` 通过；`npm run format:check` 通过；
  `npm run build` 通过。

## 2026-06-23 阶段检查点

本阶段是本地检查点，不是上线完成声明；长任务目标继续保持 active，下一会话从本文件续接即可。

已完成的新增收口：

- 规则/路径字段引用统一到字段目录允许范围，普通字段只能来自标准上下文目录，院内扩展只能落在
  `extensions.local.*`；
- 旧 `servicePackage/package` 业务表达继续收缩到服务线、服务组合、机构生效版本和离线交付文件边界；
- 规则生成器、模型候选和规则草稿入口统一使用 `then: [{ actionCardRef: "..." }]`，废弃
  `then.actions` 包裹形态；
- 规则运行时可以从当前机构生效版本物化临床提示卡，生成完整 CDS 卡片字段，保留
  `actionCardRef`、物化版本和正文摘要作为证据；
- 规则维护端已允许稳定 `actionCardRef` 草稿引用，并登记 `ACTION_CARD` 运行资产依赖；内联动作仍走
  完整字段严格校验；
- 临床提示卡资产正文从泛化 `actions[]` 空壳改为可执行 CDS 卡结构（命中后处理、风险等级、提醒等级、
  摘要、明细、来源、医生可选操作、改用方案原因、医师确认要求）；
- 前端声明式资产工作台的临床提示卡维护表单已切换到新结构，不再生成旧 `actions[]`；
- 路径 `ORDER_SET` 节点已能在运行时从机构生效版本解析医嘱套餐正文，并只记录证据和建议项，
  不自动开医嘱；
- 医嘱套餐高风险/建议医嘱场景必须保留医师确认要求；
- 条件片段、子路径、旧包发布链路、旧独立审核证据等历史概念继续删除，不留兼容层。

本阶段新鲜验证证据：

- 后端关键闭环：
  `mvn -q -Dtest='com.medkernel.engine.rule.RuleEngineServiceTest,com.medkernel.engine.rule.RuleDslAssetMaterializerTest,com.medkernel.engine.versioning.DeclarativeAssetContentValidatorTest,com.medkernel.engine.versioning.AssetReferenceConsistencyTest,com.medkernel.engine.pathway.PathwayProgressorTest,com.medkernel.engine.authoring.AssetAuthoringRegistryTest,com.medkernel.engine.knowledge.production.generation.SourceCandidateGeneratorTest,com.medkernel.engine.knowledge.production.model.ModelKnowledgeProducerTest' test`
  通过；
- 后端编译打包：
  `mvn -q -DskipTests package` 通过；
- 前端定向回归：
  `npm test -- --run src/pages/tenant/DeclarativeAssetWorkbench.test.tsx src/shared/config/ruleLayeredEditor.test.ts src/features/sandbox/sandboxScenarios.test.ts src/features/sandbox/SandboxDataEntry.test.tsx src/pages/sandbox/SandboxHost.test.tsx src/shared/api/hooks.test.ts src/pages/quality/InsuranceAudit.test.tsx`
  通过，7 个文件、148 个测试通过；
- 前端构建：
  `npm run build` 通过。

## 正在迁移的旧实现

旧包发布表、领域模型、临床包组合和主要包选择器已删除或切到机构生效版本；生产用户可见的旧包深链、
旧上线容器文案和旧证据导出包文案已清一轮。当前剩余风险集中在：负向测试护栏里的旧字段字面量、类名级历史
命名、沙箱服务组合字段，以及尚未逐项补证的资产运行消费者。它们不是目标产品模型，不能继续扩展。
下一轮清理顺序固定为：

```text
13 类资产真实消费者闭环
→ 规则发布模拟、沙箱当前生效版本、历史回放等直接 evaluator 消费者
→ 前端/API/CLI/MCP 和沙箱场景
→ 质量/合规证据导出命名边界
→ 重新生成并校验五方言单一 V1
```

删除旧模型前必须先迁移真实消费者；不得通过兼容字段、双写或第二套状态机保留历史包袱。

## 当前最高优先级

1. 继续完成 13 类资产“身份—版本—正文—校验—发布—生效—证据—撤回/回滚”闭环，优先从仍缺完整生效
   消费证据的术语、字段目录、评价、随访、质量和知识开始；
2. 复扫生产代码和前端页面，继续消除旧包发布命名、包选择器和接口残留；
3. 只保留 `runtimeReleaseId`、精确资产版本和内容摘要作为机构生效事实；
4. 重写全系统演练脚本，使其覆盖六层、13 类资源、13 类资产、11 个知识分类、完整医疗语义、
   专病十阶段、全专业领域、S0–S40、五种交付形态和七类业务组合；
5. 完成前后端、CLI、MCP、T-GATE、构建和部署资产全量验证；
6. 在 134 完成备份恢复预演、清库 V1、重部署、八段全系统演练、重启和再次恢复。

## 已知阻断或缺口

- 生产用户可见旧 Package 文案/深链已清一轮，但类名级历史命名仍需结合证据导出边界逐项评估；
- 值集、计算公式、医嘱套餐和临床提示卡的规则/路径核心消费者已切到机构生效版本语义，但更多资产类型的真实
  消费者闭环仍需逐项补证；
- 医技报告解读已补齐本地运行闭环和前台入口，但尚未在 134 清库环境完成真实病例与全知识演练；
- 字段目录已补第三方接入契约的机构生效版本消费证据，规则/路径字段引用也已登记字段目录资产依赖；但术语
  覆盖门禁、评价、随访、质量和知识仍需继续按机构生效版本复核；
- 离线交付文件、集成契约、CLI 和 MCP 尚未完全切换到新模型；
- 最终五方言 V1 和 134 真实清库重部署演练尚未完成；本地后端全量 `mvn -q test` 与前端全量
  `npm run verify` 最新已通过，但不能替代 134 环境验收。

## 134 外部事实

- 当前仍运行旧部署提交 `2c502f1e547a185dc5ab95a76d7a3329c4d1f724`；
- 当前数据库属于清洁 V1 基线以前的历史链，必须先备份并在隔离库恢复成功后再清库；
- 2026-06-24 只读探测：`medkernel`、`nginx`、`postgresql` 均 active，内部 readiness 为
  `{"status":"UP"}`；数据库最新 Flyway 为 `159`，public base tables 为 `215`；
- 2026-06-24 使用当前 `medkernel-fresh-deploy.sh --validate-environment-only` 远程预检失败于
  `MEDKERNEL_BOOTSTRAP_INIT_TOKEN` 未配置；未读取或输出任何密钥值；
- 模型服务已登记但停用，尚无正式模型知识激活；
- 文献根目录为 `file:///medkernel-data/platform-knowledge/t-1/literature-materials/`；
- 134 当前 HTTPS 证书仍为自签 `CN=193.112.107.134`，无 Subject Alternative Name；
  `curl` 严格校验失败于 self-signed certificate，`openssl s_client` 返回 verify code 18。
  必须先配置可信且具备 SAN 的证书；严格 TLS 和浏览器验收通过前不得宣称上线通过。

## 完成边界

本地工作只有在完整产品矩阵、全量质量门和 134 真实演练都通过后才可称为上线候选。当前不得
推送或创建 PR；远程合并不是本阶段任务。
