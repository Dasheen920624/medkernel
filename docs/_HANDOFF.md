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
- 项目尚未上线，不兼容旧角色、旧迁移、旧发布容器、旧接口或旧文档；
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
- 平台发布不可变权威基线，医院发布不可变运行修订，制品只负责传输和恢复；
- 临床调用方不提交包、领域或版本，服务端锁定医院当前运行修订；
- 大模型只生成候选和解释，关系库是权威源，图只是可重建投影；
- 辅助诊疗是上位能力，推荐诊断与鉴别诊断是并列、可组合的诊断子能力。

## 已完成并有定向证据

- 44 条路由、54 个页面组件和 34 个菜单保留；
- 三产品空间和四职责已建立，旧角色兼容、租户权限覆盖和主要人员分离门阀已移除；
- MFA 默认关闭，开启后使用真实 TOTP 和受限绑定会话；
- 双签、委员会、独立专家签字及模型跨角色等待门槛已移除；
- 条件片段、子路径、路径继承和循环引用已删除；路径只单向调用规则；
- 规则与路径已支持稳定身份、服务端自动 V1/V2/V3、发布版本不可变和独立下一版草稿；
- 规则、路径、诊断、临床事件、上下文快照和互操作证据已切到医院运行修订/证据导出模型；
- 大模型知识、规则和路径候选已统一进入正式草稿入口并执行类型校验；
- 诊断支持只消费医院运行修订锁定的诊断知识，响应包含鉴别诊断依据；
- 数据库已由一份模式源生成 PostgreSQL、Kingbase、Oracle、达梦和 H2 的单一 V1；
- 当前模式模型为 208 张表；H2、PostgreSQL 和 Oracle 迁移验证曾通过，最终结构变更后必须重跑；
- 前端与后端多组定向测试曾通过，具体结果只作为阶段证据，不能代替最终全量门禁。

## 2026-06-24 阶段检查点

本阶段是本地检查点，不是上线完成声明；长任务目标继续保持 active，下一会话从本文件续接即可。

已完成的新增收口：

- `SandboxCurrentRuleExecutor` 已按冻结医院运行修订 `releaseId` 物化规则 DSL 中的
  `VALUE_SET` / `FORMULA` / `ACTION_CARD` 引用；
- `SandboxReplayRuleExecutor` 已改为只从不可变历史重放清单的资产绑定快照物化引用，缺失资产或重复资产
  诚实报错，不查询当前激活资产；
- `RuleReleaseSimulationReplayEvaluator` 已按每个真实上下文快照的 `runtimeReleaseId` 做发布模拟回放；
- `AuthoringPreviewRunService` 已按请求租户 + 所选真实快照 `runtimeReleaseId` 做草稿规则即配即试；
- `RecommendationDeterministicMatcher` 已按请求租户 + 快照 `runtimeReleaseId` 生成确定性推荐卡，平台主源规则也
  通过医院当前运行修订叠层解析动作卡；
- 复扫生产代码后，正式运行入口已不再裸跑 `RuleDslEvaluator.evaluate(dsl, context)`；剩余两参调用仅用于
  规则测试/回放样例和静态 DSL 校验，不代表临床运行链路。
- 灰度暂停通知深链已从旧 `/tenant/packages` 切到运行发布页 `/config/releases`；
- 整套上线演练沙盘阶段已从“运行包”改为“运行修订”，全知识演练夹具同步到当前 11 个知识域
  `DIAGNOSTIC_ITEM`；
- 审计导出、质控导出和产品范围里的用户可见“证据包”口径已收敛为“证据导出”；
- 复扫生产源码、前端源码、脚本和契约文档后，旧 `packageId/packageVersion`、旧 `/engine/pkg/packages`
  路径和“运行包/证据包”词只剩测试护栏命中。
- `ClinicalRuntimeDeclarativeAssetResolver` 已允许 `FIELD_CATALOG` 按医院运行修订解析不可变正文；
- 新增 `RuntimeReleaseFieldCatalogResolver`，将字段目录资产正文恢复为 `ContextFieldDescriptor`；
- 第三方数据接入契约已改为只从医院当前运行修订的字段目录资产生成，不再读取当前字段目录工作区。

本阶段新鲜验证证据：

- 红灯已分别复现动作卡引用未物化时的 `规则 DSL 缺少字段: atSeverity`：
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
- 上线演练“运行包”红灯：
  `node --test scripts/release/full-system-rehearsal.test.mjs` 先失败于沙盘阶段标签
  `演练机构十规则四十用例与运行包`，修复后 5 个测试通过；
- 审计权限“证据包”红灯：
  `PermissionCodeTest` 先失败于显示名 `导出审计快照 / 证据包`，修复后
  `mvn -q -Dtest=PermissionCodeTest test` 通过。
- 字段目录运行正文红灯：
  `mvn -q -Dtest=ClinicalRuntimeDeclarativeAssetResolverTest,RuntimeReleaseFieldCatalogResolverTest,IntegrationDataContractServiceTest test`
  先失败于缺少 `RuntimeReleaseFieldCatalogResolver`；修复后通过；
- 字段目录相关回归：
  `mvn -q -Dtest=ContextFieldCatalogDraftServiceTest,ContextFieldCatalogServiceMergeTest,ContextFieldCatalogControllerTest,ClinicalRuntimeReleaseServiceTest,ClinicalRuntimeDeclarativeAssetResolverTest,RuntimeReleaseFieldCatalogResolverTest,IntegrationDataContractServiceTest,TerminologyCoverageGateTest,RuleDslAssetMaterializerTest test`
  通过。

## 2026-06-23 阶段检查点

本阶段是本地检查点，不是上线完成声明；长任务目标继续保持 active，下一会话从本文件续接即可。

已完成的新增收口：

- 规则/路径字段引用统一到字段目录白名单，普通字段只能来自标准上下文目录，院内扩展只能落在
  `extensions.local.*`；
- 旧 `servicePackage/package` 业务表达继续收缩到服务线、服务组合、运行修订和发布制品边界；
- 规则生成器、模型候选和规则草稿入口统一使用 `then: [{ actionCardRef: "..." }]`，废弃
  `then.actions` 包裹形态；
- 规则运行时可以从医院当前运行修订物化动作卡，生成完整 CDS 动作卡字段，保留
  `actionCardRef`、物化版本和正文摘要作为证据；
- 规则维护端已允许稳定 `actionCardRef` 草稿引用，并登记 `ACTION_CARD` 运行资产依赖；内联动作仍走
  完整字段严格校验；
- 动作卡资产正文从泛化 `actions[]` 空壳改为可执行 CDS 卡结构（动作码、严重度、indicator、摘要、
  明细、来源、建议、覆盖原因、医师确认要求）；
- 前端声明式资产工作台的动作卡维护表单已切换到新结构，不再生成旧 `actions[]`；
- 路径 `ORDER_SET` 节点已能在运行时从医院运行修订解析医嘱套餐正文，并只记录证据和建议项，
  不自动开医嘱；
- 医嘱套餐高风险/建议医嘱场景必须保留医师确认要求；
- 条件片段、子路径、旧发布包、旧独立审核证据等历史概念继续删除，不留兼容层。

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

旧发布容器表、领域模型、临床包三元组和主要包选择器已删除或切到运行修订；生产用户可见的旧包深链、
运行包文案和证据包文案已清一轮。当前剩余风险集中在：负向测试护栏里的旧字段字面量、类名级历史
命名、沙箱服务组合字段，以及尚未逐项补证的资产运行消费者。它们不是目标产品模型，不能继续扩展。
下一轮清理顺序固定为：

```text
13 类资产真实消费者闭环
→ 规则发布模拟、沙箱当前运行、历史回放等直接 evaluator 消费者
→ 前端/API/CLI/MCP 和沙箱场景
→ 质量/合规证据导出命名边界
→ 重新生成并校验五方言单一 V1
```

删除旧模型前必须先迁移真实消费者；不得通过兼容字段、双写或第二套状态机保留历史包袱。

## 当前最高优先级

1. 继续完成 13 类资产“身份—版本—正文—校验—发布—运行—证据—撤回/回滚”闭环，优先从仍缺完整运行
   消费证据的患者报告解读、术语、字段目录、评价、随访、质量和知识开始；
2. 复扫生产代码和前端页面，继续消除旧发布容器 Package 命名、包选择器和接口残留；
3. 只保留 `runtimeReleaseId`、精确资产版本和内容摘要作为运行事实；
4. 重写全系统演练脚本，使其覆盖六层、13 类资源、13 类资产、11 个知识分类、完整医疗语义、
   专病十阶段、全专业领域、S0–S40、五种交付形态和七类业务组合；
5. 完成前后端、CLI、MCP、T-GATE、构建和部署资产全量验证；
6. 在 134 完成备份恢复预演、清库 V1、重部署、八段全系统演练、重启和再次恢复。

## 已知阻断或缺口

- 生产用户可见旧 Package 文案/深链已清一轮，但类名级历史命名仍需结合证据导出边界逐项评估；
- 值集、公式、医嘱套餐和动作卡的规则/路径核心运行消费者已切到运行修订语义，但更多资产类型的真实
  消费者闭环仍需逐项补证；
- 患者报告解读只有部分数据骨架，尚未形成完整运行闭环；
- 字段目录已补第三方接入契约的运行修订消费证据，但术语、规则/路径字段校验、评价、随访、质量和知识
  仍需继续按医院运行修订复核；
- 发布制品、前端发布页、集成契约、CLI 和 MCP 尚未完全切换到新模型；
- 最终五方言 V1、全量测试和 134 演练尚未完成。

## 134 外部事实

- 当前仍运行旧制品提交 `2c502f1e547a185dc5ab95a76d7a3329c4d1f724`；
- 当前数据库属于清洁 V1 基线以前的历史链，必须先备份并在隔离库恢复成功后再清库；
- Provider 已登记但停用，尚无正式模型知识激活；
- 文献根目录为 `file:///medkernel-data/platform-knowledge/t-1/literature-materials/`；
- 134 必须先配置可信且具备 SAN 的证书；严格 TLS 和浏览器验收通过前不得宣称上线通过。

## 完成边界

本地工作只有在完整产品矩阵、全量质量门和 134 真实演练都通过后才可称为上线候选。当前不得
推送或创建 PR；远程合并不是本阶段任务。
