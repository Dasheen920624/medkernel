# 完整上线收敛实施任务

> 执行约定：每个 checkbox 是可独立失败、验证、提交和接力的小写集；严格按依赖执行。失败测试必须先红再绿，证据只收本次 run-id 的真实命令结果。所有路径相对仓库根目录；创建新类时保持 com.medkernel.engine/shared.\*\* 公共 Javadoc 为中文。禁止把医疗责任审核、临床确认或 134 单次破坏性确认伪造成自动通过。

## 命令别名

- OSV = openspec validate converge-full-launch-and-knowledge-platform --strict --no-interactive
- BE = cd medkernel-backend && mvn -B -q
- FE = cd frontend && npm
- NT = node --test
- DB = node --test scripts/db/generate-migrations.test.mjs && node scripts/db/generate-migrations.mjs --check
- TG = node --test scripts/authenticity-guard.test.mjs && node scripts/authenticity-guard.mjs --mode=all
- E2E = cd frontend && CI=true npm run e2e -- --workers=1 --retries=0
- DEP = for f in deploy/onprem/tests/validate-\*.sh; do bash "$f"; done
- DIFF = git diff --check

## 1. 从输入锚点形成 RC0 并恢复可信基线

- [x] 1.1 升级 RC 清单为不可删原始证据的严格合同。依赖：无；主要文件/接口：scripts/release/rc-manifest-lib.mjs、rc-manifest.mjs、rc-manifest.test.mjs；失败测试：先证明删除任一门禁原始证据后旧验证器仍错误 VERIFIED，并新增对旧 schema、缺原始文件、缺精确命令/退出码、摘要/时间/run-id/候选不一致、制品或依赖无可重验来源证明的拒绝用例；实现：新 schema 对每个 gate 绑定原始证据路径与摘要、执行命令、退出码、开始/结束时间和观察计数，对每个制品/依赖绑定可重建输入、来源提交、构建命令与逐文件摘要，独立验证器必须直接重读原始文件和来源链而非只信摘要 JSON；验证：NT scripts/release/rc-manifest.test.mjs 且复制后逐项删除原始证据均非零退出；证据键：rc0.manifest.raw-evidence-independent-recalculation。
- [x] 1.2 隔离非事务审计测试数据且保留提交后回读。依赖：无；主要文件/接口：IntegrationServiceTest；失败测试：BE -Dtest=IntegrationServiceTest test 曾复现跨用例 2/6 污染；验证：同命令重复全绿；证据键：rc0.backend.integration-isolation。
- [x] 1.3 收敛 35 入口口径、E2E ESLint 和客户面空态语言。依赖：1.2；主要文件/接口：D0DomainAcceptanceTest、e2eLaunchCoverageEvidence.test.ts、DomainFacadeB0Evidence.tsx；失败测试：后端定向测试、FE run lint、TG 曾分别失败；验证：三命令全绿；证据键：rc0.scope35-and-ui-truthfulness。
- [x] 1.4 由唯一 schema 生成五方言 V1。依赖：1.3；主要文件/接口：medkernel.schema.json、db/migration/{h2,postgres,oracle,dm,kingbase}/V1\_\_baseline.sql；失败测试：DB 曾报告 ck_rec_card_type 漂移；验证：DB 全绿；证据键：rc0.database.generated-v1。
- [x] 1.5 补齐 OpenSpec 上下文并严格校验。依赖：1.4；主要文件/接口：openspec/config.yaml、本变更全部产物；失败测试：OSV 在上下文缺失时失败；验证：OSV 通过；证据键：rc0.openspec.strict。
- [x] 1.6 废止不可重验的 d4514938e6ba7d6f0d09eb736a0c66ab72863b07 清单并形成新的 clean RC0。依赖：1.1-1.5；主要文件/接口：修复后的 rc-manifest v2、全新 candidateCommit、隔离 checkout 与证据 bundle；失败测试：旧 d451 清单、旧 schema、缺任一原始证据、从旧 target/dist/test-results 拼接结果、E2E skipped/flaky/unexpected、10 万级墙钟套件漏登记/漏 `performance` 标签或制品/依赖来源不可重建时均必须拒绝；执行：先提交 1.1 修复形成新的完整 candidateCommit，再从该提交全新检出按锁文件重建依赖；RC 机器计划必须枚举全部 10 万级墙钟套件，起跑前扫描源码并核对类级或方法级 `performance` 标签；普通后端 clean 门禁显式排除 `docker,performance` 标签且对实际生成的 Surefire 报告保持零 skipped，Docker/PostgreSQL 16/openEuler 与 10 万级容量套件转入后续目标环境门禁独立完成，不得伪造或视为本门禁已覆盖；同时完整重跑前端 verify/build、浏览器 E2E（workers=1、retries=0）、CLI/MCP、DB、DEP、TG、格式/OpenSpec，并保存逐门禁原始命令、退出码和来源证明；Playwright 解析器须遵守锁定版本官方 JSON reporter 契约，接受叶子 suite 省略可选嵌套 `suites` 字段，同时拒绝非数组畸形结构、空壳或逐测试结果与统计不一致，运行器与终态清单必须调用同一共享结构解析器；终态验证器须接受锁定 Node 24 原生 TAP/spec reporter 的 `#`/`ℹ` 计数前缀，并只在语义解析前剥离标准 ANSI SGR 显示码，任何真实失败、计数漂移或必需语义缺失仍须拒绝；六类制品须逐文件比较同一候选 `git archive` 导出字节，确定性保留 `.gitattributes` 的 EOL/导出语义，禁止用 raw blob 误拒合法 CRLF，也禁止文本归一化或跳过字节校验放行篡改；E2E 前后探针须使用短连接，每次完整校验 readiness 与 JAR 内嵌提交身份，仅对明确传输瞬断做最多 30 秒条件重试，身份/内容不一致立即失败；验证：将整个 bundle 复制到异目录由候选内独立验证器 VERIFIED，再逐类删除原始证据确认全部被拒绝；证据键：rc0.clean.new-candidate-full-raw-gates、rc0.browser-e2e.full-replay。
- [x] 1.7 保全 RC0 成果并以 squash 合入 main。依赖：1.6；主要文件/接口：codex/launch-convergence 分支、远端 PR、docs/\_HANDOFF.md；失败测试：远端 CI 任一 required check 非 success 时禁止合并；验证：git fetch origin && git merge-base --is-ancestor <squash-commit> origin/main；证据键：rc0.pr.preserved-main。

## 2. 建立 35 入口、LAUNCH 总账与自动缺口闭环

- [x] 2.1 定义唯一 35 入口合同。依赖：1.7；主要文件/接口：创建 docs/contracts/product/product-entry-catalog.v1.json、scripts/release/product-entry-catalog.test.mjs；失败测试：NT scripts/release/product-entry-catalog.test.mjs 应先因缺文件失败；验证：恰好 35 个唯一 entryCode 且字段、路由、四职责、权限、组织范围、动作、回读、审计、六态、证据键齐全；证据键：catalog.entries.schema。
- [x] 2.2 生成后端与前端入口消费者。依赖：2.1；主要文件/接口：创建 scripts/release/generate-product-entry-consumers.mjs，生成 MenuPermissionCatalog 资源和 frontend/src/shared/contracts/productEntryCatalog.generated.ts；失败测试：生成器 --check 应先报告消费者缺失；验证：node scripts/release/generate-product-entry-consumers.mjs --check；证据键：catalog.entries.generated-consumers。
- [x] 2.3 删除 34/35 并行集合和数量常量。依赖：2.2；主要文件/接口：MenuPermissionCatalog、frontend 导航、D0DomainAcceptanceTest、e2eLaunchCoverageEvidence.test.ts、职责矩阵校验；失败测试：在测试夹具加入第 36 项或硬编码 ALL_34 后目录漂移测试失败；验证：rg -n 'ALL_34|34 个入口|entryCount *= *35' frontend/src medkernel-backend/src scripts 返回零非生成命中；证据键：catalog.entries.no-parallel-truth。
- [x] 2.4 逐入口绑定真实证据强度。依赖：2.3；主要文件/接口：scripts/release/launch-entry-evidence.schema.json、launch-coverage-audit.mjs；失败测试：ROUTE_ONLY 冒充 CORE_ACTION_WITH_PERMISSION 或 CORE_ACTION_WITH_SIX_STATE 时失败；验证：NT scripts/release/launch-coverage-audit.test.mjs；证据键：catalog.entries.evidence-strength。
- [x] 2.5 定义 LAUNCH-01 至 LAUNCH-15 固定 schema。依赖：2.4；主要文件/接口：创建 docs/contracts/release/launch-ledger.v1.schema.json，修改 launch-coverage-audit.mjs；失败测试：缺项、重复、未知码、自由文本状态应失败；验证：NT scripts/release/launch-coverage-audit.test.mjs；证据键：launch.ledger.schema15。
- [x] 2.6 强制证据来源不可自证。依赖：2.5；主要文件/接口：launch-coverage-audit.mjs 的 validateEvidenceSource；失败测试：最终审计引用自身、白名单外路径、旧 run-id、晚于判定时间或摘要被替换时失败；验证：NT scripts/release/launch-coverage-audit.test.mjs；证据键：launch.evidence.non-self-attestation。
- [x] 2.7 建立缺口四分类。依赖：2.6；主要文件/接口：创建 scripts/release/launch-gap-classifier.mjs、launch-gap-classifier.test.mjs；失败测试：未知分类或无 ownerPath 的缺口失败；验证：NT scripts/release/launch-gap-classifier.test.mjs，且每项只归 IMPLEMENTATION、TEST、DATA、ENVIRONMENT 之一；证据键：launch.gap.classification。
- [x] 2.8 闭环 IMPLEMENTATION 缺口。依赖：2.7；主要文件/接口：launch-gap-classifier.mjs 的 remediationPlan、目标实现与对应单测；失败测试：实现缺口无 failingTest、implementationPath、consumerReadback 或 auditReadback 时失败；验证：分类器重跑后该类缺口归零；证据键：launch.gap.implementation.closed。
- [x] 2.9 闭环 TEST 缺口。依赖：2.7；主要文件/接口：launch-gap-classifier.mjs、对应单测/契约/E2E；失败测试：测试缺口只有静态布尔或没有真实观察码时失败；验证：受影响测试和覆盖审计重跑归零；证据键：launch.gap.test.closed。
- [x] 2.10 闭环 DATA 缺口。依赖：2.7；主要文件/接口：medical-resource-coverage.v1.json、资源生产 API、消费者回读；失败测试：仅有候选/文件/分类名而无发布、生效、消费、审计时失败；验证：资源闭环证据重跑归零；证据键：launch.gap.data.closed。
- [x] 2.11 约束 ENVIRONMENT 缺口。依赖：2.7；主要文件/接口：docs/audit/deferred-issues.md、launch-gap-classifier.mjs；失败测试：仓库内可解决缺口或无目标资源事实却标 ENVIRONMENT 时失败；验证：仅不可控现场项进入清单且 LAUNCH 保持未通过；证据键：launch.gap.environment.honest。
- [x] 2.12 输出严格归零总账。依赖：2.8-2.11；主要文件/接口：launch-coverage-audit.mjs 的 summarizeLaunchLedger；失败测试：FAILED、UNKNOWN、SKIPPED、缺证、未解释失败、人工豁免任一非零时禁止 PASSED；验证：NT scripts/release/launch-coverage-audit.test.mjs；证据键：launch.ledger.zero-unknown。

## 3. 持久化平台知识权威、首次信任与密钥围栏

- [x] 3.1 定义权威领域模型与仓储合同。依赖：2.12；主要文件/接口：创建 com.medkernel.engine.knowledge.authority 下 Authority、IssuerInstance、TrustRoot、SigningKey、Handover、Revocation、PackageRegistration 及 repository 端口；失败测试：AuthorityRepositoryContractTest 先因类型缺失失败；验证：BE -Dtest=AuthorityRepositoryContractTest test；证据键：authority.domain.contract。
- [x] 3.2 扩展唯一 schema 并生成五方言。依赖：3.1；主要文件/接口：medkernel.schema.json 及五方言 V1；失败测试：schema 测试对缺 tenant/version/audit/唯一约束/中文 COMMENT 失败；验证：DB；证据键：authority.database.five-dialects。
- [x] 3.3 初始化稳定 authorityId 与唯一 t-1。依赖：3.2；主要文件/接口：AuthorityService.initialize、AuthorityRepository；失败测试：第二 authorityId、宿主/IP/目录变化和备份恢复生成新身份时失败；验证：BE -Dtest=AuthorityServiceTest test；证据键：authority.identity.stable-t1。
- [ ] 3.4 为每个 issuer 创建独立身份和独立 keyId。依赖：3.3；主要文件/接口：IssuerRegistrationService、SigningKeyPort；失败测试：两个 issuer 复用 keyId 或密钥材料时失败；验证：BE -Dtest=IssuerRegistrationServiceTest test；证据键：authority.issuer.independent-key。
- [ ] 3.5 建立外置 HSM/KMS 密钥端口。依赖：3.4；主要文件/接口：SigningKeyPort、HsmKmsSigningAdapter、测试 InMemorySigningAdapter；失败测试：私钥进入数据库实体、普通备份、包或日志时失败；验证：BE -Dtest=SigningKeyBoundaryTest test 和秘密 inventory；证据键：authority.key.external-boundary。
- [ ] 3.6 预置首次信任根。依赖：3.5；主要文件/接口：软件 manifest 的 authorityId/rootFingerprint、site trust-bootstrap 配置、HospitalTrustCheckpointService；失败测试：空医院没有已签软件 manifest 或独立认证通道时无法初始化根；验证：BE -Dtest=TrustBootstrapServiceTest test；证据键：authority.trust.preprovisioned-root。
- [ ] 3.7 明确禁止 TOFU。依赖：3.6；主要文件/接口：TrustBootstrapService、PackageSignatureVerifier；失败测试：与 .mkp 同介质携带的未知根、自签公钥或 134 IP 匹配均被拒绝；验证：BE -Dtest=TrustBootstrapServiceTest,PackageSignatureVerifierTest test；证据键：authority.trust.no-tofu。
- [ ] 3.8 实现唯一活动 issuer 的 CAS。依赖：3.4；主要文件/接口：IssuerActivationService.compareAndSwap、AuthorityRepository；失败测试：并发接管只允许一个成功，待命/冻结/交接/吊销 issuer 不得签发；验证：BE -Dtest=IssuerActivationConcurrencyTest test；证据键：authority.issuer.single-active。
- [ ] 3.9 实现固定根锚定的 SM2 签发验签。依赖：3.5-3.8；主要文件/接口：PackageSigner、PackageSignatureVerifier、现有国密组件；失败测试：错误 authorityId、issuerInstanceId、keyId、有效期、链或活动序号均失败；验证：BE -Dtest=PackageSignatureVerifierTest test；证据键：authority.signature.sm2-chain。
- [ ] 3.10 实现轮换、根过渡和单调吊销。依赖：3.9；主要文件/接口：KeyLifecycleService、RevocationRepository；失败测试：未知/过期/已吊销 key、未经旧根签署的新根、回退吊销序号均失败；验证：BE -Dtest=KeyLifecycleServiceTest test；证据键：authority.key.rotation-revocation。
- [ ] 3.11 在交接原子边界围栏旧 HSM/KMS key。依赖：3.8、3.10；主要文件/接口：AuthorityHandoverService、SigningKeyPort.disableKey；失败测试：新 issuer 激活但旧 key 未禁用时事务失败并恢复旧状态；验证：BE -Dtest=AuthorityHandoverServiceTest test；证据键：authority.handover.key-fenced。
- [ ] 3.12 拒绝旧快照恢复后的分叉签发。依赖：3.11；主要文件/接口：PackageRegistrationService、HospitalTrustCheckpointService；失败测试：旧 134 数据库快照使用已围栏 key 或旧交接序号签发的新包被平台和医院共同拒绝；验证：BE -Dtest=StaleIssuerSnapshotRejectionTest test；证据键：authority.handover.stale-snapshot-rejected。
- [ ] 3.13 实现不可变包注册表和审计链。依赖：3.9；主要文件/接口：PackageRegistrationService、PackageRegistrationRepository、AuditPort；失败测试：同包异摘要、同序号异摘要、重放或缺审计失败；验证：BE -Dtest=PackageRegistrationServiceTest test；证据键：authority.registry.immutable-audited。
- [ ] 3.14 实现冻结、摘要核对、签署交接与原子激活。依赖：3.11-3.13；主要文件/接口：AuthorityHandoverService.freeze/verify/activate/abort；失败测试：数据库、原件、审计、注册表、信任链任一摘要不一致时保持旧 issuer；验证：BE -Dtest=AuthorityHandoverServiceTest test；证据键：authority.handover.atomic-migration。
- [ ] 3.15 提供最小只读 API 与权限审计。依赖：3.13；主要文件/接口：AuthorityController、AuthorityResponse DTO、权限目录；失败测试：无权、跨租户、返回私钥/接管码或缺 traceId 审计时失败；验证：BE -Dtest=AuthorityControllerSecurityTest test；证据键：authority.api.readonly-secure。
- [ ] 3.16 提供权威状态六态页面。依赖：3.15；主要文件/接口：创建 frontend/src/pages/platform/KnowledgeAuthority.tsx 及测试；失败测试：载入、空、正常、错误、无权限、降级任一缺失时失败；验证：FE test -- KnowledgeAuthority.test.tsx；证据键：authority.ui.six-state。

## 4. 实现自包含、可下载上传和在线拉取的 .mkp

- [ ] 4.1 定义版本化 .mkp 规范和确定性编码器。依赖：3.13；主要文件/接口：创建 com.medkernel.engine.knowledge.packageio 的 MedicalPackageManifest、CanonicalManifestEncoder；失败测试：不同宿主同输入产生不同字节、内容变化摘要不变时失败；验证：BE -Dtest=CanonicalManifestEncoderTest test；证据键：mkp.manifest.deterministic。
- [ ] 4.2 建立 13 类适配器登记合同。依赖：4.1；主要文件/接口：MedicalAssetPackageAdapter、MedicalAssetPackageAdapterRegistry、VersionedAssetType；失败测试：缺类型、重复类型或未实现 export/validate/materialize 时失败；验证：BE -Dtest=MedicalAssetPackageAdapterRegistryTest test；证据键：mkp.adapter.registry13。
- [ ] 4.3 闭环 KNOWLEDGE 适配器。依赖：4.2；主要文件/接口：KnowledgePackageAdapter；失败测试：缺正文、来源许可、引用锚点、版本、依赖或测试向量时失败；验证：BE -Dtest=KnowledgePackageAdapterTest test；证据键：mkp.adapter.knowledge。
- [ ] 4.4 闭环 TERMINOLOGY 适配器。依赖：4.2；主要文件/接口：TerminologyPackageAdapter；失败测试：缺概念正文、标准码、映射版本或回读时失败；验证：BE -Dtest=TerminologyPackageAdapterTest test；证据键：mkp.adapter.terminology。
- [ ] 4.5 闭环 RULE 适配器并强制完整条件树。依赖：4.2；主要文件/接口：RulePackageAdapter；失败测试：引用条件片段库、共享片段、外部数据库坐标或循环时失败；验证：BE -Dtest=RulePackageAdapterTest test；证据键：mkp.adapter.rule-self-contained。
- [ ] 4.6 闭环 PATHWAY 适配器并强制完整节点树。依赖：4.2；主要文件/接口：PathwayPackageAdapter；失败测试：引用共享子路径、路径继承、外部子路径或循环嵌套时失败；验证：BE -Dtest=PathwayPackageAdapterTest test；证据键：mkp.adapter.pathway-self-contained。
- [ ] 4.7 闭环 EVALUATION 适配器。依赖：4.2；主要文件/接口：EvaluationPackageAdapter；失败测试：缺公式/值集精确依赖、评价正文或回放向量时失败；验证：BE -Dtest=EvaluationPackageAdapterTest test；证据键：mkp.adapter.evaluation。
- [ ] 4.8 闭环 FOLLOWUP 适配器。依赖：4.2；主要文件/接口：FollowupPackageAdapter；失败测试：缺计划正文、触发条件、任务/问卷依赖时失败；验证：BE -Dtest=FollowupPackageAdapterTest test；证据键：mkp.adapter.followup。
- [ ] 4.9 闭环 FIELD_CATALOG 适配器。依赖：4.2；主要文件/接口：FieldCatalogPackageAdapter；失败测试：缺字段语义、单位、类型、缺失策略或稳定身份时失败；验证：BE -Dtest=FieldCatalogPackageAdapterTest test；证据键：mkp.adapter.field-catalog。
- [ ] 4.10 闭环 SAFETY 适配器。依赖：4.2；主要文件/接口：SafetyPackageAdapter；失败测试：缺高危等级、阻断动作、责任确认语义或测试向量时失败；验证：BE -Dtest=SafetyPackageAdapterTest test；证据键：mkp.adapter.safety。
- [ ] 4.11 闭环 CDSS_RISK 适配器。依赖：4.2；主要文件/接口：CdssRiskPackageAdapter；失败测试：缺风险输入、阈值、解释、降级策略或测试时失败；验证：BE -Dtest=CdssRiskPackageAdapterTest test；证据键：mkp.adapter.cdss-risk。
- [ ] 4.12 闭环 VALUE_SET 适配器。依赖：4.2；主要文件/接口：ValueSetPackageAdapter；失败测试：缺成员版本、代码系统、包含/排除语义或摘要时失败；验证：BE -Dtest=ValueSetPackageAdapterTest test；证据键：mkp.adapter.value-set。
- [ ] 4.13 闭环 FORMULA 适配器。依赖：4.2；主要文件/接口：FormulaPackageAdapter；失败测试：缺输入单位、表达式、边界、解释或固定向量时失败；验证：BE -Dtest=FormulaPackageAdapterTest test；证据键：mkp.adapter.formula。
- [ ] 4.14 闭环 ORDER_SET 适配器。依赖：4.2；主要文件/接口：OrderSetPackageAdapter；失败测试：缺医嘱候选正文、安全依赖、适用范围或医师确认边界时失败；验证：BE -Dtest=OrderSetPackageAdapterTest test；证据键：mkp.adapter.order-set。
- [ ] 4.15 闭环 ACTION_CARD 适配器。依赖：4.2；主要文件/接口：ActionCardPackageAdapter；失败测试：缺展示正文、动作、权限、确认与审计语义时失败；验证：BE -Dtest=ActionCardPackageAdapterTest test；证据键：mkp.adapter.action-card。
- [ ] 4.16 验证 13 类空库往返。依赖：4.3-4.15；主要文件/接口：MedicalPackageRoundTripIT；失败测试：任一类型导出后无法在空 V1 库物化并运行回读时失败；验证：BE -Dtest=MedicalPackageRoundTripIT test；证据键：mkp.adapter.roundtrip13。
- [ ] 4.17 生成规范目录、流式 SM3 和 SM2 信封。依赖：4.16；主要文件/接口：MedicalPackageExporter、StreamingDigestWriter、PackageSigner；失败测试：条目顺序/时间戳漂移、大小不实、任一字节篡改验真时失败；验证：BE -Dtest=MedicalPackageExporterTest test；证据键：mkp.file.signed-bytes。
- [ ] 4.18 执行严格内容白名单。依赖：4.17；主要文件/接口：PackageContentBoundaryPolicy；失败测试：患者、医院覆盖、凭据、私钥、运行证据或无法证明合成的测试向量进入包时失败；验证：BE -Dtest=PackageContentBoundaryPolicyTest test；证据键：mkp.file.no-patient-secret。
- [ ] 4.19 实现真实导出文件和下载 API。依赖：4.17-4.18；主要文件/接口：MedicalPackageExportService、MedicalPackageFileController GET /api/knowledge/packages/{deliveryId}/file、下载 DTO；失败测试：仅有 evidenceId/登记行而文件缺失、Range 越界、无权或摘要漂移时失败；验证：BE -Dtest=MedicalPackageFileControllerTest test；证据键：mkp.transport.download-real-file。
- [ ] 4.20 实现受限离线上传 API。依赖：4.19；主要文件/接口：MedicalPackageImportController POST /api/knowledge/packages/imports/upload、multipart DTO、QuarantineStore；失败测试：绝对路径、..、符号链接、文件数/大小/解压比/并发限额、重复幂等键或跨租户时失败；验证：BE -Dtest=MedicalPackageUploadControllerTest test；证据键：mkp.transport.upload-quarantine。
- [ ] 4.21 实现 HTTPS 在线拉取 API。依赖：4.20；主要文件/接口：MedicalPackagePullRequest DTO、OnlinePackageFetcher、POST /api/knowledge/packages/imports/pull；失败测试：非 HTTPS、SSRF 地址、重定向越界、无大小限制、无权限或平台直写数据库时失败；验证：BE -Dtest=OnlinePackageFetcherTest,MedicalPackagePullControllerTest test；证据键：mkp.transport.online-pull。
- [ ] 4.22 统一下载、上传、拉取的 DTO、权限、审计、限额和幂等。依赖：4.19-4.21；主要文件/接口：PackageTransferPolicy、PackageTransferAuditService、IdempotencyRepository；失败测试：同字节不同入口产生不同 manifest 摘要/幂等键，或日志泄漏 URL 凭据时失败；验证：BE -Dtest=PackageTransferContractTest test；证据键：mkp.transport.unified-contract。
- [ ] 4.23 实现受管隔离预检。依赖：4.22、3.7、3.10；主要文件/接口：MedicalPackagePreflightService；失败测试：信任/吊销/防重放/许可/兼容/13 类正文/依赖/测试/撤回/无患者数据任一失败时阻断；验证：BE -Dtest=MedicalPackagePreflightServiceTest test；证据键：mkp.import.preflight-bound-digest。
- [ ] 4.24 删除 evidenceId 与 releaseId 文件旁路。依赖：4.23；主要文件/接口：ReleaseGovernanceController、现有离线导入 command/DTO；失败测试：只提交本地 evidenceId 或来源 releaseId 必须 400 且零业务写入；验证：BE -Dtest=ReleaseGovernanceControllerTest test 和 rg -n 'evidenceId.*import|releaseId.*offline' 前后端无旁路；证据键：mkp.import.no-evidence-bypass。
- [ ] 4.25 事务物化稳定身份映射。依赖：4.16、4.23；主要文件/接口：MedicalPackageMaterializationService、13 adapter materialize；失败测试：目标主键不同、任一适配器异常或预检后文件替换时全部回滚；验证：BE -Dtest=MedicalPackageMaterializationServiceTest test；证据键：mkp.import.atomic-materialization。
- [ ] 4.26 实现差异影响预览与 CAS 激活。依赖：4.25；主要文件/接口：PackageActivationService、PackageImpactPreviewResponse；失败测试：预览摘要漂移、期望 releaseId/revision 不匹配或空库未显式期望空时冲突；验证：BE -Dtest=PackageActivationServiceTest test；证据键：mkp.activation.cas-preview-bound。
- [ ] 4.27 迁移 ReleaseGovernance 到真实文件流和六态。依赖：4.19-4.26；主要文件/接口：ReleaseGovernanceController、frontend/src/pages/tenant/ReleaseGovernance.tsx 及 API client；失败测试：载入、空、正常、错误、无权限、降级任一缺失，或页面仍提交 evidenceId 时失败；验证：BE -Dtest=ReleaseGovernanceControllerTest test && FE test -- ReleaseGovernance.test.tsx；证据键：mkp.ui.release-governance-six-state。
- [ ] 4.28 实现精确差量链与全量回退。依赖：4.25；主要文件/接口：DeltaPackageChainService；失败测试：fromDigest/父链/累积撤回缺失时不得部分合并，断网无全量包返回 NOT_SYNCED/IMPORT_BLOCKED；验证：BE -Dtest=DeltaPackageChainServiceTest test；证据键：mkp.delta.full-fallback。
- [ ] 4.29 实现医院本地不可变回滚。依赖：4.26、4.28；主要文件/接口：LocalPackageRollbackService；失败测试：权限/责任确认缺失、CAS 冲突、目标含当前安全撤回项时失败；验证：BE -Dtest=LocalPackageRollbackServiceTest test；证据键：mkp.rollback.local-audited。

## 5. 建成覆盖 16 语义族与 S0-S40 的医疗资源工厂

- [ ] 5.1 建立唯一资源覆盖矩阵合同。依赖：4.29；主要文件/接口：创建 medkernel-backend/src/main/resources/catalog/medical-resource-coverage.v1.json、对应 schema 与 MedicalResourceCoverageCatalogTest；失败测试：缺 13 类、11 分类、16 语义族、专业领域、S0-S40、专病十阶段、消费者或交付形态时失败；验证：BE -Dtest=MedicalResourceCoverageCatalogTest test；证据键：resource.coverage.unique-matrix。
- [ ] 5.2 建立来源许可与原件账本。依赖：5.1；主要文件/接口：SourceLedgerService、ManagedSourceFilePort、/zoesoft/medkernel-data 适配器；失败测试：许可范围/证据/有效期/替代撤回/URL 或离线坐标/SHA-256/精确锚点任一缺失时失败；验证：BE -Dtest=SourceLedgerServiceTest test；证据键：resource.source.licensed-ledger。
- [ ] 5.3 打通无模型 B0 生产链。依赖：5.2；主要文件/接口：ResourceProductionService.manual/template/deterministicParse；失败测试：MODEL_DISABLED 时来源、编辑、校验、审核、发布任一步不可用则失败；验证：BE -Dtest=ResourceProductionB0Test test；证据键：resource.production.b0-complete。
- [ ] 5.4 约束模型只生成待审候选。依赖：5.3；主要文件/接口：ModelResourceCandidateService、AI 标识与模型/提示版本 DTO；失败测试：无来源、无 schema、无 AI 标识、自动审核或自动发布时失败；验证：BE -Dtest=ModelResourceCandidateServiceTest test；证据键：resource.production.model-candidate-only。
- [ ] 5.5 建立类型门、依赖、回归、高危与影响验证。依赖：5.3；主要文件/接口：AssetTechnicalValidationService、AssetDependencyService；失败测试：缺失、冲突、不兼容、循环、固定回归失败或高危未解决时禁止进入审核；验证：BE -Dtest=AssetTechnicalValidationServiceTest,AssetDependencyServiceTest test；证据键：resource.validation.technical-gates。
- [ ] 5.6 强制规则与路径自包含。依赖：5.5；主要文件/接口：DeclarativeAssetContentValidator、RulePackageAdapter、PathwayPackageAdapter；失败测试：任何条件片段库、共享片段、共享子路径、子路径引用、路径继承或循环嵌套必须失败；验证：BE -Dtest=DeclarativeAssetContentValidatorTest,RulePackageAdapterTest,PathwayPackageAdapterTest test；证据键：resource.structure.no-fragment-subpath-inheritance-cycle。
- [ ] 5.7 实现有资质责任审核。依赖：5.5；主要文件/接口：QualifiedResourceReviewService、任职/职责/组织范围端口；失败测试：权限、组织范围、资质、任职、结论、理由、许可摘要或 traceId 缺失时失败；验证：BE -Dtest=QualifiedResourceReviewServiceTest test；证据键：resource.review.qualified-audited。
- [ ] 5.8 实现版本、发布、更新、替换、撤回和回滚。依赖：5.7；主要文件/接口：ResourceLifecycleService、VersionReleaseService；失败测试：原地改写历史、撤回不传播或回滚降低修订号时失败；验证：BE -Dtest=ResourceLifecycleServiceTest test；证据键：resource.lifecycle.immutable-loop。
- [ ] 5.9 闭环语义族“疾病与诊断”。依赖：5.2-5.8；主要文件/接口：medical-resource-coverage.v1.json 的 DISEASE_DIAGNOSIS、资源生产 API；失败测试：亚型/分期/诊断标准/支持反驳证据/鉴别关系/诊疗指针任一缺失时未就绪；验证：MedicalSemanticFamilyIT 参数 DISEASE_DIAGNOSIS；证据键：resource.semantic.disease-diagnosis。
- [ ] 5.10 闭环语义族“症状体征与风险”。依赖：5.2-5.8；主要文件/接口：矩阵 SYMPTOM_SIGN_RISK；失败测试：主诉/体征/危险信号/风险因素/特殊人群/缺失策略任一缺失时未就绪；验证：MedicalSemanticFamilyIT 参数 SYMPTOM_SIGN_RISK；证据键：resource.semantic.symptom-sign-risk。
- [ ] 5.11 闭环语义族“检验检查与报告”。依赖：5.2-5.8；主要文件/接口：矩阵 TEST_EXAM_REPORT；失败测试：说明书、标本/部位/方法、参考范围、危急值、趋势、局限、解释边界不全时失败；验证：MedicalSemanticFamilyIT 参数 TEST_EXAM_REPORT；证据键：resource.semantic.test-exam-report。
- [ ] 5.12 闭环语义族“药品与药物治疗”。依赖：5.2-5.8；主要文件/接口：矩阵 DRUG_THERAPY；失败测试：成分/剂型/剂量/途径/适应证/禁忌/相互作用/监测/特殊人群不全时失败；验证：MedicalSemanticFamilyIT 参数 DRUG_THERAPY；证据键：resource.semantic.drug-therapy。
- [ ] 5.13 闭环语义族“手术操作与医疗技术”。依赖：5.2-5.8；主要文件/接口：矩阵 PROCEDURE_TECHNOLOGY；失败测试：手术/操作/麻醉/输血/介入/准入/适用条件/风险不全时失败；验证：MedicalSemanticFamilyIT 参数 PROCEDURE_TECHNOLOGY；证据键：resource.semantic.procedure-technology。
- [ ] 5.14 闭环语义族“器械与耗材”。依赖：5.2-5.8；主要文件/接口：矩阵 DEVICE_CONSUMABLE；失败测试：UDI/型号/适用范围/禁忌/召回/停用/关联技术不全时失败；验证：MedicalSemanticFamilyIT 参数 DEVICE_CONSUMABLE；证据键：resource.semantic.device-consumable。
- [ ] 5.15 闭环语义族“指南与证据”。依赖：5.2-5.8；主要文件/接口：矩阵 GUIDANCE_EVIDENCE；失败测试：指南/共识/文献/证据等级/推荐强度/条款引用/冲突/撤回不全时失败；验证：MedicalSemanticFamilyIT 参数 GUIDANCE_EVIDENCE；证据键：resource.semantic.guidance-evidence。
- [ ] 5.16 闭环语义族“量表、评分与公式”。依赖：5.2-5.8；主要文件/接口：矩阵 SCORE_FORMULA；失败测试：输入/单位/阈值/计算/解释/适用范围/测试向量不全时失败；验证：MedicalSemanticFamilyIT 参数 SCORE_FORMULA；证据键：resource.semantic.score-formula。
- [ ] 5.17 闭环语义族“护理”。依赖：5.2-5.8；主要文件/接口：矩阵 NURSING；失败测试：分级/自理/风险/问题/目标/措施/复评/交班/质控不全时失败；验证：MedicalSemanticFamilyIT 参数 NURSING；证据键：resource.semantic.nursing。
- [ ] 5.18 闭环语义族“路径与连续照护”。依赖：5.2-5.8；主要文件/接口：矩阵 PATHWAY_CONTINUITY；失败测试：准入/节点/时钟/变异/退出/康复/宣教/随访/转诊不全或引用子路径时失败；验证：MedicalSemanticFamilyIT 参数 PATHWAY_CONTINUITY；证据键：resource.semantic.pathway-continuity。
- [ ] 5.19 闭环语义族“病历、病案与医保”。依赖：5.2-5.8；主要文件/接口：矩阵 RECORD_CLAIM_INSURANCE；失败测试：文书/编码/首页/DRG-DIP/目录/限制/证据定位不全时失败；验证：MedicalSemanticFamilyIT 参数 RECORD_CLAIM_INSURANCE；证据键：resource.semantic.record-claim-insurance。
- [ ] 5.20 闭环语义族“感控与公共卫生”。依赖：5.2-5.8；主要文件/接口：矩阵 INFECTION_PUBLIC_HEALTH；失败测试：院感/传染病/暴露/接种/筛查/报告条件/干预不全时失败；验证：MedicalSemanticFamilyIT 参数 INFECTION_PUBLIC_HEALTH；证据键：resource.semantic.infection-public-health。
- [ ] 5.21 闭环语义族“康复及综合照护”。依赖：5.2-5.8；主要文件/接口：矩阵 REHABILITATION_HOLISTIC_CARE；失败测试：康复/营养/心理/疼痛/安宁/患者目标/偏好/结局不全时失败；验证：MedicalSemanticFamilyIT 参数 REHABILITATION_HOLISTIC_CARE；证据键：resource.semantic.rehabilitation-holistic-care。
- [ ] 5.22 闭环语义族“中医药”。依赖：5.2-5.8；主要文件/接口：矩阵 TCM；失败测试：病名/证候/四诊/治法/方药/适宜技术/中西药风险/协同边界不全时失败；验证：MedicalSemanticFamilyIT 参数 TCM；证据键：resource.semantic.tcm。
- [ ] 5.23 闭环语义族“制度、质量与评级”。依赖：5.2-5.8；主要文件/接口：矩阵 POLICY_QUALITY_RATING；失败测试：院内/核心制度、质量指标、整改、评级映射、监管约束不全时失败；验证：MedicalSemanticFamilyIT 参数 POLICY_QUALITY_RATING；证据键：resource.semantic.policy-quality-rating。
- [ ] 5.24 闭环语义族“来源与有效性”。依赖：5.2-5.8；主要文件/接口：矩阵 SOURCE_VALIDITY；失败测试：许可、发布日期、适用时点、版本、替代、冲突、撤回、锚点不全时失败；验证：MedicalSemanticFamilyIT 参数 SOURCE_VALIDITY；证据键：resource.semantic.source-validity。
- [ ] 5.25 生产 11 个知识分类第一批。依赖：5.9-5.24；主要文件/接口：矩阵 GUIDELINE/DRUG/PATHWAY_KNOWLEDGE/NURSING；失败测试：任一分类无来源、许可、审核、当前版本、消费者或审计时失败；验证：KnowledgeCategoryProductionIT 参数第一批；证据键：resource.knowledge-category.batch1。
- [ ] 5.26 生产 11 个知识分类第二批。依赖：5.25；主要文件/接口：矩阵 DIAGNOSTIC_ITEM/TCM/PROTOCOL/POLICY；失败测试：任一分类仅有文件或候选时失败；验证：KnowledgeCategoryProductionIT 参数第二批；证据键：resource.knowledge-category.batch2。
- [ ] 5.27 生产 11 个知识分类第三批。依赖：5.26；主要文件/接口：矩阵 LITERATURE/DIAGNOSIS/OTHER；失败测试：任一分类无真实运行消费者时失败；验证：KnowledgeCategoryProductionIT 参数第三批；证据键：resource.knowledge-category.batch3。
- [ ] 5.28 闭环专业领域第一批。依赖：5.9-5.27；主要文件/接口：临床各专科、护理、检验医技、药学、手术麻醉输血介入的矩阵记录；失败测试：任一领域未完成来源到审计回读全链时失败；验证：MedicalSpecialtyProductionIT 参数第一批；证据键：resource.specialty.batch1。
- [ ] 5.29 闭环专业领域第二批。依赖：5.28；主要文件/接口：急诊重症、妇产儿老年、肿瘤透析移植生殖、康复综合、感控公卫的矩阵记录；失败测试：任一领域缺有许可来源、结构化资产、责任审核、不可变发布、机构生效、真实运行或审计回读，或仅以代表页面代替运行时失败；验证：MedicalSpecialtyProductionIT 参数第二批；证据键：resource.specialty.batch2。
- [ ] 5.30 闭环专业领域第三批。依赖：5.29；主要文件/接口：中医药、口腔眼耳鼻喉皮肤、医保病案管理、科研真实世界、基层区域远程的矩阵记录；失败测试：任一领域缺有许可来源、结构化资产、责任审核、不可变发布、机构生效、真实消费者或审计回读时失败；验证：MedicalSpecialtyProductionIT 参数第三批；证据键：resource.specialty.batch3。
- [ ] 5.31 归零 S0-S4 资源依赖。依赖：5.24-5.30；主要文件/接口：矩阵场景 S0-S4；失败测试：每场景正常/异常/缺数/冲突/高危/降级任一路径缺精确版本闭包时失败；验证：ScenarioResourceCoverageIT 参数 S0,S1,S2,S3,S4；证据键：resource.scenario.s00-s04。
- [ ] 5.32 归零 S5-S9 资源依赖。依赖：5.31；主要文件/接口：矩阵场景 S5-S9；失败测试：规则/路径不自包含或消费者回读缺失时失败；验证：ScenarioResourceCoverageIT 参数 S5-S9；证据键：resource.scenario.s05-s09。
- [ ] 5.33 归零 S10-S14 资源依赖。依赖：5.32；主要文件/接口：矩阵场景 S10-S14；失败测试：质控/评价/随访/发布/权限任一闭环缺失时失败；验证：ScenarioResourceCoverageIT 参数 S10-S14；证据键：resource.scenario.s10-s14。
- [ ] 5.34 归零 S15-S19 资源依赖。依赖：5.33；主要文件/接口：矩阵场景 S15-S19；失败测试：全验收、诊断、检查检验、用药、急危重任一正式消费者缺失时失败；验证：ScenarioResourceCoverageIT 参数 S15-S19；证据键：resource.scenario.s15-s19。
- [ ] 5.35 归零 S20-S24 资源依赖。依赖：5.34；主要文件/接口：矩阵场景 S20-S24；失败测试：护理接续、公卫、MDT、评级、门急诊任一闭环缺失时失败；验证：ScenarioResourceCoverageIT 参数 S20-S24；证据键：resource.scenario.s20-s24。
- [ ] 5.36 归零 S25-S29 资源依赖。依赖：5.35；主要文件/接口：矩阵场景 S25-S29；失败测试：住院、围术期、重症、特殊人群、肿瘤任一闭环缺失时失败；验证：ScenarioResourceCoverageIT 参数 S25-S29；证据键：resource.scenario.s25-s29。
- [ ] 5.37 归零 S30-S34 资源依赖。依赖：5.36；主要文件/接口：矩阵场景 S30-S34；失败测试：慢病、药事、安全事件、器械技术、科研数据任一闭环缺失时失败；验证：ScenarioResourceCoverageIT 参数 S30-S34；证据键：resource.scenario.s30-s34。
- [ ] 5.38 归零 S35-S39 资源依赖。依赖：5.37；主要文件/接口：矩阵场景 S35-S39；失败测试：护理、报告、床旁知识、综合照护、中医健康任一闭环缺失时失败；验证：ScenarioResourceCoverageIT 参数 S35-S39；证据键：resource.scenario.s35-s39。
- [ ] 5.39 归零 S40 资源依赖。依赖：5.38；主要文件/接口：矩阵场景 S40；失败测试：跨机构报告质量、互认理由、重复提示、远程任务或来源证据缺失时失败；验证：ScenarioResourceCoverageIT 参数 S40；证据键：resource.scenario.s40。
- [ ] 5.40 闭环专病十阶段。依赖：5.31-5.39；主要文件/接口：矩阵 specialtyPathwayStages；失败测试：`PRODUCT_SCOPE §9.4` 从筛查与分诊至质量改进与资产迭代的十阶段任一无资源或无明确不适用原因时失败；验证：BE -Dtest=SpecialtyPathwayTenStageCoverageIT test；证据键：resource.specialty-pathway.ten-stages。
- [ ] 5.41 汇总资源工厂真实就绪。依赖：5.1-5.40；主要文件/接口：MedicalResourceCoverageService、只读 API/页面；失败测试：分类名、文件数、候选、页面存在或静态布尔不得生成 READY；验证：BE -Dtest=MedicalResourceCoverageServiceTest test && FE test -- MedicalResourceCoverage.test.tsx；证据键：resource.factory.zero-gap-summary。

## 6. 构建 openEuler 断网院内软件包与可复验支持矩阵

- [ ] 6.1 定义不可变软件 manifest。依赖：5.41；主要文件/接口：创建 deploy/onprem/manifest/onprem-software-manifest.schema.json、scripts/release/onprem-package-manifest.mjs；失败测试：JAR/dist/JRE21/V1/模板/脚本/SBOM/来源证明任一缺失或提交不一致时失败；验证：NT scripts/release/onprem-package-manifest.test.mjs；证据键：onprem.package.manifest-complete。
- [ ] 6.2 锁定首发 profile 和支持矩阵 schema。依赖：6.1；主要文件/接口：创建 deploy/onprem/support-matrix.v1.json 及测试；失败测试：非 openEuler 24.03 LTS x86_64、非 PostgreSQL 16 主版本、本机 Nginx/资料盘组合未经真实 smoke 却标 SUPPORTED 时失败；验证：NT deploy/onprem/tests/support-matrix.test.mjs；证据键：onprem.support-matrix.initial-profile。
- [ ] 6.3 生成精确离线依赖清单。依赖：6.2；主要文件/接口：创建 deploy/onprem/offline-repo/packages.lock.json、generate-offline-repo-lock.mjs；失败测试：PostgreSQL、Nginx 和必要 OS 包缺 name/epoch/version/release/arch（NEVRA）、来源仓库、RPM SHA-256 时失败；验证：NT deploy/onprem/tests/offline-repo-lock.test.mjs；证据键：onprem.repo.nevra-lock。
- [ ] 6.4 构建并签名 openEuler 离线仓库。依赖：6.3；主要文件/接口：deploy/onprem/offline-repo/repodata、build-offline-repo.sh、repo manifest/signature；失败测试：RPM 缺失、repomd 摘要漂移、仓库签名无受信链或包 NEVRA 不匹配时失败；验证：bash deploy/onprem/tests/validate-offline-repo.sh；证据键：onprem.repo.signed-complete。
- [ ] 6.5 阻断安装期间公网仓库。依赖：6.4；主要文件/接口：install-dependencies.sh、临时 dnf repo 配置；失败测试：网络断开仍需公网、dnf 配置出现 enabled 外部 repo 或抓包发现外连时失败；验证：bash deploy/onprem/tests/validate-no-network-install.sh；证据键：onprem.repo.no-public-network。
- [ ] 6.6 实现只读 preflight profile 检查。依赖：6.2-6.5；主要文件/接口：deploy/onprem/preflight.sh；失败测试：OS 版本、x86_64、CPU/内存/磁盘、PostgreSQL 16、端口、CJK、CA/TLS、资料盘、备份容量或离线仓库任一不符时零主机修改；验证：bash deploy/onprem/tests/validate-preflight.sh；证据键：onprem.action.preflight-readonly。
- [ ] 6.7 预置 authorityId 与根指纹并禁止 TOFU。依赖：3.6-3.7、6.1；主要文件/接口：软件 manifest trustAnchors、preflight.sh、trust-bootstrap.sh；失败测试：首次信任来自同一 .mkp 未知根、现场临时自签或仅 IP 匹配时安装失败；验证：bash deploy/onprem/tests/validate-trust-bootstrap.sh；证据键：onprem.trust.preprovisioned-no-tofu。
- [ ] 6.8 校验无凭据 site overlay。依赖：6.6；主要文件/接口：deploy/onprem/site-overlay.schema.json、validate-site-overlay.sh；失败测试：口令、JWT/加密/集成密钥、bootstrap token、SSH/TLS 私钥、占位符、不安全权限时失败且日志不泄密；验证：bash deploy/onprem/tests/validate-site-overlay.sh；证据键：onprem.overlay.no-secret。
- [ ] 6.9 安装离线依赖。依赖：6.4-6.8；主要文件/接口：install-dependencies.sh；失败测试：NEVRA、RPM 摘要或 repo 签名不匹配时不安装；验证：断网 openEuler 临时机执行两次并比较 rpm -qa 精确清单；证据键：onprem.action.install-dependencies。
- [ ] 6.10 初始化本机 PostgreSQL 16 空库。依赖：6.9；主要文件/接口：install-database.sh、medkernel-fresh-deploy.sh；失败测试：非空 medkernel 库、版本不符、多迁移或 V1 后 schema 清单漂移时失败；验证：bash deploy/onprem/tests/validate-database-init.sh；证据键：onprem.action.init-postgresql-v1。
- [ ] 6.11 安装同字节应用制品。依赖：6.10；主要文件/接口：install-application.sh、版本化 releases 目录和 current 原子链接；失败测试：JAR/dist/JRE/V1 任一 SHA-256 或 40 位提交不符时不切换 current；验证：bash deploy/onprem/tests/validate-application-install.sh；证据键：onprem.action.install-immutable-app。
- [ ] 6.12 安装 systemd 与 Nginx 严格 TLS 配置。依赖：6.8、6.11；主要文件/接口：templates/medkernel.service、medkernel.nginx.conf、configure-services.sh；失败测试：后端非回环、SAN 不匹配、私钥权限过宽或明文入口时失败；验证：bash deploy/onprem/tests/validate-service-config.sh；证据键：onprem.action.configure-services-tls。
- [ ] 6.13 启动并验证严格 readiness。依赖：6.12；主要文件/接口：start.sh、readiness.sh；失败测试：只回环健康而外部 HTTPS 失败、证书过期/SAN 错误或使用 --insecure 时失败；验证：bash deploy/onprem/tests/validate-readiness.sh；证据键：onprem.action.start-readiness。
- [ ] 6.14 验证 install 幂等和中途补偿。依赖：6.9-6.13；主要文件/接口：install.sh、install-state.json；失败测试：依赖/数据库/制品/服务各注入失败后出现半切换或旧程序连新库时失败；验证：bash deploy/onprem/tests/validate-install-idempotency-recovery.sh；证据键：onprem.action.install-idempotent-atomic。
- [ ] 6.15 实现 upgrade 动作。依赖：6.14；主要文件/接口：upgrade.sh、compatibility-check.sh；失败测试：目标 schema 不兼容、候选未签名、摘要漂移或 readiness 失败时不得保留新 current；验证：bash deploy/onprem/tests/validate-upgrade.sh；证据键：onprem.action.upgrade。
- [ ] 6.16 实现 rollback 动作。依赖：6.15；主要文件/接口：rollback.sh、版本兼容清单；失败测试：旧程序与新库不兼容、备份缺失或目标制品摘要错误时拒绝回滚；验证：bash deploy/onprem/tests/validate-rollback.sh；证据键：onprem.action.rollback。
- [ ] 6.17 实现 status 动作。依赖：6.13；主要文件/接口：status.sh、状态 JSON schema；失败测试：只读状态不得改服务，且提交/摘要/迁移/TLS/资料盘任一漂移必须非零退出；验证：bash deploy/onprem/tests/validate-status.sh；证据键：onprem.action.status-recomputed。
- [ ] 6.18 验证受管 file 资料盘。依赖：6.8；主要文件/接口：validate-managed-file-store.sh、ManagedSourceFileAdapter；失败测试：资料根在 release 内、非 file、未挂载、容量/权限/写读摘要失败或账本漂移时阻断；验证：bash deploy/onprem/tests/validate-managed-file-store.sh；证据键：onprem.storage.managed-file。
- [ ] 6.19 创建一致性加密备份。依赖：6.17-6.18；主要文件/接口：backup.sh、backup-manifest.schema.json；失败测试：数据库/资料/配置/overlay/制品 manifest/包注册/证书元数据任一缺失、私钥进入普通归档或异故障域复制失败时整包失败；验证：bash deploy/onprem/tests/validate-backup.sh；证据键：onprem.action.backup-complete。
- [ ] 6.20 执行隔离异机联合恢复。依赖：6.19；主要文件/接口：restore.sh、restore-verify.sh；失败测试：任一组成缺失、版本不一致、摘要错误、登录/B0/机构生效版本不一致时失败；验证：bash deploy/onprem/tests/validate-restore.sh；证据键：onprem.action.restore-joint-rpo-rto。
- [ ] 6.21 收敛网络暴露。依赖：6.12-6.13；主要文件/接口：network-acceptance.sh；失败测试：5432/18080/11434 或未批准端口从非授权网络可达、22 无来源限制时失败；验证：bash deploy/onprem/tests/validate-network-exposure.sh；证据键：onprem.network.minimal-exposure。
- [ ] 6.22 保持模型附件可选且隔离。依赖：6.13；主要文件/接口：ollama 模板、validate-model-addon.sh；失败测试：无许可/digest/Provider 配置/医学基准、非回环监听或自动发布模型结果时失败；验证：bash deploy/onprem/tests/validate-model-addon.sh；证据键：onprem.model.optional-loopback。
- [ ] 6.23 在真实断网 profile 完成安装动作矩阵。依赖：6.9-6.22；主要文件/接口：deploy/onprem/tests/openEuler-24.03-postgresql-16-smoke.sh、support-matrix.v1.json；失败测试：空机安装、重复安装、升级、回滚、备份、异机恢复、TLS、B0、核心 smoke 任一缺失时不登记 SUPPORTED；验证：该 smoke 全绿并记录 OS/内核/NEVRA/驱动/制品摘要；证据键：onprem.support-matrix.oe2403-pg16-smoke。
- [ ] 6.24 封装可重现签名院内软件包。依赖：6.1-6.23；主要文件/接口：mk-publish.sh、软件 manifest、SBOM、签名与来源证明；失败测试：发布阶段重建二进制、归档顺序/时间戳漂移或任一文件不可追溯到候选时失败；验证：两个干净宿主生成逐字节相同包且 DEP 全绿；证据键：onprem.package.reproducible-signed。

## 7. 在本地实现无中间门禁自动化与全部目标工具

- [ ] 7.1 建立提交级非破坏性编排。依赖：6.24；主要文件/接口：scripts/release/launch-orchestrator.mjs、.github/workflows/ci.yml；失败测试：格式、类型、真实性、受影响测试或 DB 生成任一失败时后续阶段不得运行；验证：NT scripts/release/launch-orchestrator.test.mjs；证据键：automation.commit-stage.orchestrated。
- [ ] 7.2 建立主线级 clean 编排。依赖：7.1；主要文件/接口：launch-orchestrator.mjs 的 mainline plan；失败测试：后端 clean、前端 verify/build、CLI/MCP、部署合同、真实性 inventory 任一未运行或复用旧证据时失败；验证：在全新检出完整执行；证据键：automation.mainline.clean-gates。
- [ ] 7.3 建立 RC 全量浏览器 E2E。依赖：7.2；主要文件/接口：frontend/playwright.config.ts、e2e 全套、RC plan；失败测试：任一项目 skipped/flaky/unexpected、workers/retries 不符、仅路由代表证据时失败；验证：E2E 并绑定完整测试计数、浏览器项目、提交和 run-id；证据键：automation.rc.browser-e2e-full。
- [ ] 7.4 建立独立临时双实例 RC 栈。依赖：7.3、4.29；主要文件/接口：deploy/acceptance/local-dual-instance.sh；失败测试：平台与医院共享数据库、目录、缓存、身份或医院绕过文件导入时失败；验证：bash deploy/acceptance/tests/validate-local-dual-instance.sh；证据键：automation.rc.dual-instance-isolated。
- [ ] 7.5 只构建一次并提升同字节候选。依赖：7.2-7.4；主要文件/接口：scripts/release/promote-immutable-artifacts.mjs、Release workflow；失败测试：标签/Release/部署重建或提交、摘要、签名、来源漂移时失败；验证：NT scripts/release/promote-immutable-artifacts.test.mjs；证据键：automation.artifact.promote-same-bytes。
- [ ] 7.6 本地实现单次破坏性确认状态机。依赖：7.1；主要文件/接口：创建 scripts/deploy/destructive-confirmation-state.mjs；失败测试：预检/恢复未绿、多次确认、缺 hostname/root/db/commit/delete-scope/window/backup/run-id 时失败；验证：NT scripts/deploy/destructive-confirmation-state.test.mjs；证据键：rollout.local.confirmation-state-machine。
- [ ] 7.7 本地实现确认后漂移失效。依赖：7.6；主要文件/接口：destructive-confirmation-state.mjs 的 revalidateBeforeMutation；失败测试：主机、数据库、候选、删除边界、备份或状态指纹任一变化必须使确认作废且零破坏动作；验证：同测试；证据键：rollout.local.confirmation-drift-invalidated。
- [ ] 7.8 本地实现十段顺序编排。依赖：7.4、7.7；主要文件/接口：创建 scripts/release/ten-stage-rehearsal.mjs；失败测试：顺序错、前置缺、FAILED/UNKNOWN/SKIPPED 或阶段十自证时停止依赖结论；验证：NT scripts/release/ten-stage-rehearsal.test.mjs；证据键：rollout.local.ten-stage-orchestrator。
- [ ] 7.9 本地实现独立证据重算。依赖：7.8；主要文件/接口：创建 scripts/release/target-evidence-recalculator.mjs；失败测试：部署器自报、历史 JSON、错误 hostname/commit/digest/run-id、目标文件缺失或摘要漂移时失败；验证：NT scripts/release/target-evidence-recalculator.test.mjs；证据键：rollout.local.independent-recalculation。
- [ ] 7.10 本地实现 48-72 小时观察器。依赖：7.9；主要文件/接口：创建 scripts/release/stability-observer.mjs；失败测试：窗口短于 48h/长于 72h、采样缺口、拼接窗口、漂移、非计划重启或可用性低于 99.9% 时失败；验证：NT scripts/release/stability-observer.test.mjs 使用可控时钟；证据键：rollout.local.stability-observer-contract。
- [ ] 7.11 在非目标环境验证全部目标工具。依赖：7.6-7.10；主要文件/接口：deploy/acceptance/local-rollout-tool-smoke.sh；失败测试：任何工具仅能在 134 调试、合同无失败注入或产生未脱敏输出时失败；验证：bash deploy/acceptance/tests/validate-local-rollout-tools.sh；证据键：rollout.local.tools-all-green-before-134。
- [ ] 7.12 运行一次无人工中间放行的本地完整验收。依赖：7.1-7.11；主要文件/接口：launch-orchestrator.mjs rc plan；失败测试：阶段间人工勾选/上传/改状态、UNKNOWN/SKIPPED 或医疗责任动作被模拟时失败；验证：单一 run-id 连续生成 LAUNCH-01 至 LAUNCH-14 就绪和零未知汇总；证据键：automation.local.full-acceptance。

## 8. 同步文档、评审并形成 134 可执行候选

- [ ] 8.1 同步当前权威产品与架构文档。依赖：7.12；主要文件/接口：docs/PRODUCT_SCOPE.md、ARCHITECTURE.md、DATABASE_SCHEMA.md、DEPLOYMENT_AND_REHEARSAL.md；失败测试：文档与 35 入口、权威/信任、.mkp、资源工厂、openEuler profile 代码事实漂移时文档合同失败；验证：文档链接检查、OSV、DIFF；证据键：docs.authority-and-deployment.synced。
- [ ] 8.2 同步质量、功能和职责真相源。依赖：8.1；主要文件/接口：docs/audit/质量基线.md、product-function-catalog.md、product-role-journeys.md；失败测试：并行入口集合、旧 34 口径、TOFU、共享片段/子路径或未经 smoke 的支持声明命中时失败；验证：对应合同测试与 rg 审计；证据键：docs.catalog-quality.synced。
- [ ] 8.3 完成逐小写集规格符合性评审。依赖：8.1-8.2；主要文件/接口：本变更 specs/tasks 与实现 diff；失败测试：任一 SHALL/MUST 无任务、测试或证据映射时阻断；验证：OSV 和独立评审结论零 blocker；证据键：review.spec-compliance.zero-blocker。
- [ ] 8.4 完成逐小写集代码质量与医疗安全评审。依赖：8.3；主要文件/接口：所有新增代码/测试/迁移/部署脚本；失败测试：秘密泄漏、患者数据、自动开嘱/发布、吞错成功、并发/事务/降级缺陷任一存在时阻断；验证：受影响测试、全量门禁、TG、DB、DEP；证据键：review.code-medical-safety.zero-blocker。
- [ ] 8.5 创建并验证新的可提升 RC。依赖：8.4；主要文件/接口：RC manifest、软件包、.mkp、离线仓库、SBOM、签名/来源证明；失败测试：任何原始证据不可重算、提交/摘要/来源漂移或 E2E/真实 profile smoke 缺失时失败；验证：全新检出重跑并由独立验证器 VERIFIED；证据键：release.rc.before-134.verified。
- [ ] 8.6 更新唯一接力入口。依赖：8.5；主要文件/接口：docs/\_HANDOFF.md；失败测试：缺当前 commit/run-id/命令/证据/未完成 checkbox/134 单次确认边界或包含历史并行计划时失败；验证：OSV、DIFF；证据键：handoff.ready-for-134。

## 9. 对 134 执行一次确认后的首次正式部署

- [ ] 9.1 只读核对 134 主机身份和范围。依赖：8.6；主要文件/接口：target-preflight.sh、期望 hostname/root/database 清单；失败测试：任何探测导致服务、数据库、目录或配置变化时失败；验证：前后状态摘要一致；证据键：target134.preflight.identity-readonly。
- [ ] 9.2 只读核对首发 profile。依赖：9.1；主要文件/接口：target-preflight.sh；失败测试：非 openEuler 24.03 LTS x86_64、PostgreSQL 16、CJK/容量/端口/离线仓库 NEVRA 不符时停止；验证：输出脱敏实际值与期望值；证据键：target134.preflight.profile-match。
- [ ] 9.3 只读核对候选、信任和 TLS。依赖：9.2；主要文件/接口：RC/software manifest、CA/SAN、root fingerprint；失败测试：40 位提交、制品/仓库摘要、签名链、预置根、外部 SAN 任一漂移时停止；验证：独立重算全匹配；证据键：target134.preflight.candidate-trust-tls。
- [ ] 9.4 备份 134 数据库。依赖：9.3；主要文件/接口：backup.sh database 阶段；失败测试：快照失败、摘要/加密/保留/恢复指令缺失或含患者明文证据时失败；验证：备份 manifest 可复验；证据键：target134.backup.database。
- [ ] 9.5 备份 134 程序、配置和受管资料。依赖：9.4；主要文件/接口：backup.sh files 阶段；失败测试：当前程序、外置配置、资料、包注册、证书元数据、审计任一缺失时整备份失败；验证：逐文件摘要与范围清单匹配；证据键：target134.backup.files-config-audit。
- [ ] 9.6 验证签名私钥专用边界。依赖：9.5；主要文件/接口：HSM/KMS 备份/围栏证明接口；失败测试：私钥或恢复码进入普通备份、日志、证据仓库时失败；验证：秘密扫描和密钥设施专用恢复演练；证据键：target134.backup.key-boundary。
- [ ] 9.7 完成加密异故障域复制。依赖：9.4-9.6；主要文件/接口：backup-replicate.sh；失败测试：复制缺项、远端摘要不一致或失败无告警时失败；验证：从异故障域重新读取并重算 manifest；证据键：target134.backup.offsite-copy。
- [ ] 9.8 完成清理前第一次隔离恢复。依赖：9.7；主要文件/接口：restore.sh、独立数据库/目录；失败测试：结构、关键主体/数据、资产、机构版本或可启动性任一失败则保持 134 原状；验证：联合恢复、登录和 B0 回读；证据键：target134.restore.before-cleanup。
- [ ] 9.9 创建一次原子破坏性确认。依赖：9.8；主要文件/接口：destructive-confirmation-state.mjs；失败测试：hostname/root/medkernel db/候选提交/删除范围/停机窗口/回滚备份/run-id 任一缺失或错误时不可确认；验证：只生成一个脱敏确认摘要；证据键：target134.confirmation.single-atomic。
- [ ] 9.10 在首个破坏动作前重验漂移。依赖：9.9；主要文件/接口：revalidateBeforeMutation；失败测试：预检、候选、备份、目标状态或删除边界任一漂移时确认立即失效；验证：真实目标指纹与确认摘要一致；证据键：target134.confirmation.no-drift。
- [ ] 9.11 停止确认范围内服务。依赖：9.10；主要文件/接口：target-rollout.sh stop 阶段；失败测试：停止范围外进程、服务未停或证据 run-id 不符时停止后续；验证：进程与端口独立检查；证据键：target134.deploy.stop-services。
- [ ] 9.12 只清理确认范围。依赖：9.11；主要文件/接口：target-rollout.sh cleanup 阶段、允许删除白名单；失败测试：路径符号链接、越界、数据库名不为 medkernel 或删除目标与确认不一致时零删除；验证：删除清单逐项匹配确认；证据键：target134.deploy.cleanup-scoped。
- [ ] 9.13 从签名离线仓库安装依赖。依赖：9.12；主要文件/接口：install-dependencies.sh；失败测试：任何公网请求、签名/NEVRA/SHA-256 漂移时失败并进入既定回滚；验证：断网安装精确 RPM 清单；证据键：target134.deploy.offline-dependencies。
- [ ] 9.14 安装同字节软件候选。依赖：9.13；主要文件/接口：install-application.sh；失败测试：发布阶段重建、文件摘要或候选提交不一致时失败；验证：实际 JAR/dist/JRE/template 摘要与 RC software manifest 相同；证据键：target134.deploy.same-candidate-bytes。
- [ ] 9.15 从空 medkernel 数据库执行唯一 V1。依赖：9.14；主要文件/接口：install-database.sh、medkernel.schema.json；失败测试：数据库非空、多迁移、表/约束/索引/中文注释任一漂移时失败；验证：目标库 schema 清单独立重算；证据键：target134.deploy.empty-v1。
- [ ] 9.16 激活预置信任而不接受 TOFU。依赖：9.14-9.15；主要文件/接口：trust-bootstrap.sh、HospitalTrustCheckpoint；失败测试：现场 .mkp 携带未知根、临时自签或仅主机匹配时失败；验证：authorityId/root fingerprint 与签名软件 manifest 一致；证据键：target134.deploy.trust-no-tofu。
- [ ] 9.17 启动服务并验收严格 TLS/readiness。依赖：9.15-9.16；主要文件/接口：systemd/Nginx/status/readiness；失败测试：旧程序连接新库、SAN/证书链错误、--insecure、内部健康替代外部 HTTPS 时失败；验证：外部 443、回环服务、迁移、首次接管均通过；证据键：target134.deploy.readiness-strict-tls。
- [ ] 9.18 从真实 134 状态独立重算部署证据。依赖：9.17；主要文件/接口：target-evidence-recalculator.mjs；失败测试：历史、本地替代、部署器自报、错误 hostname/commit/digest/run-id 或文件缺失时失败；验证：独立验收器生成可复算索引；证据键：target134.deploy.independent-evidence。

## 10. 在 134 生产全量资源、十段演练与稳定观察

- [ ] 10.1 初始化稳定权威和独立 134 issuer。依赖：9.18；主要文件/接口：AuthorityService、IssuerRegistrationService、HSM/KMS；失败测试：重建 authorityId、复用其它 issuer key 或非活动实例签发时失败；验证：权威只读 API 与审计回读；证据键：target134.authority.initialized。
- [ ] 10.2 导入并核验有许可来源原件。依赖：10.1；主要文件/接口：SourceLedgerService、/zoesoft/medkernel-data；失败测试：许可/原文摘要/引用锚点/适用范围缺失或文件账本不一致时未就绪；验证：来源覆盖矩阵与资料摘要；证据键：target134.resource.sources-licensed。
- [ ] 10.3 生产并审核 13 类资产。依赖：10.2；主要文件/接口：13 个 PackageAdapter、ResourceProductionService；失败测试：任一类型只有候选/坐标、无审核/发布/消费者时阻断完整包；验证：13 类逐类真实正文、依赖、测试、审核、发布、回读；证据键：target134.resource.asset-types13。
- [ ] 10.4 生产并审核 16 个语义族。依赖：10.3；主要文件/接口：medical-resource-coverage.v1.json；失败测试：任一语义族内容项、来源、范围、版本、关系或消费者缺失时未就绪；验证：MedicalSemanticFamilyIT 对 134 本次数据逐族通过；证据键：target134.resource.semantic-families16。
- [ ] 10.5 生产 11 个知识分类。依赖：10.4；主要文件/接口：KNOWLEDGE 分类资源；失败测试：任一分类仅有名称/文件数而无来源、审核、当前版本、消费者时失败；验证：分类覆盖 API 和运行审计；证据键：target134.resource.knowledge-categories11。
- [ ] 10.6 生产全专业代表资源。依赖：10.4-10.5；主要文件/接口：专业领域矩阵；失败测试：任一领域未完成来源、结构化、审核、发布、机构生效、运行、审计时保持缺口；验证：专业领域批次真实回读；证据键：target134.resource.specialties-representative-loop。
- [ ] 10.7 归零 S0-S40 和专病十阶段资源缺口。依赖：10.3-10.6；主要文件/接口：场景矩阵、ScenarioResourceCoverageIT；失败测试：正常/异常/缺数/冲突/高危/降级任一路径缺精确依赖时失败；验证：S0-S40 分批结果与十阶段结果零缺口；证据键：target134.resource.scenarios41-ten-stages。
- [ ] 10.8 签发并登记完整 .mkp。依赖：10.7；主要文件/接口：MedicalPackageExporter、PackageRegistrationService、HSM/KMS；失败测试：任一适配器、许可、自包含规则/路径、测试、撤回或签名链缺失时禁止签发；验证：真实文件、SM3/SM2、注册表和下载回读；证据键：target134.resource.full-mkp-published。
- [ ] 10.9 演练第一段：基础治理。依赖：10.8；主要文件/接口：ten-stage-rehearsal stage-01；失败测试：组织、任职、账号、四职责、配置、审计任一 UNKNOWN/SKIPPED 时停止；验证：真实服务与数据库回读；证据键：target134.rehearsal.01-governance。
- [ ] 10.10 演练第二段：数据与互操作。依赖：10.9；主要文件/接口：stage-02、13 标准患者资源、互操作适配器；失败测试：幂等/签名/重放/映射/质量/NOT_CONNECTED 补偿缺失时停止；验证：真实输入输出与审计；证据键：target134.rehearsal.02-interoperability。
- [ ] 10.11 演练第三段：11 类知识生产。依赖：10.10；主要文件/接口：stage-03；失败测试：任一分类生产、审核、发布、消费证据缺失时停止；验证：11 类本次证据；证据键：target134.rehearsal.03-knowledge11。
- [ ] 10.12 演练第四段：13 类资产、16 语义族与专业资源。依赖：10.11；主要文件/接口：stage-04；失败测试：代表资源冒充完整 13 类/16 族或规则路径不自包含时停止；验证：类型、语义、专业矩阵；证据键：target134.rehearsal.04-assets-semantics-specialties。
- [ ] 10.13 演练第五段：平台与两机构版本。依赖：10.12；主要文件/接口：stage-05、VersionRolloutService；失败测试：平台标准、两机构差异、唯一当前生效、CAS/回滚任一缺失时停止；验证：服务端版本解析与审计；证据键：target134.rehearsal.05-version-governance。
- [ ] 10.14 演练第六段：S0-S40 与专病十阶段。依赖：10.13；主要文件/接口：stage-06；失败测试：41 场景任一正常/异常/缺数/冲突/高危/降级缺失时停止；验证：分批回放机器结果；证据键：target134.rehearsal.06-scenarios41。
- [ ] 10.15 演练第七段：35 入口与交付/组合/第三方。依赖：10.14；主要文件/接口：stage-07、35 入口目录；失败测试：任一入口核心动作/权限/六态、五形态、七组合或第三方系统族无真实/诚实断连证据时停止；验证：入口总账与消费者回读；证据键：target134.rehearsal.07-product-coverage。
- [ ] 10.16 演练第八段：医疗安全与诚实降级。依赖：10.15；主要文件/接口：stage-08；失败测试：AI 无标识、自动开嘱/发布、高危无逐条确认、模型/图/平台/第三方断连 B0 失败时停止；验证：安全与回滚审计；证据键：target134.rehearsal.08-safety-degradation。
- [ ] 10.17 演练第九段：运维与证据。依赖：10.16；主要文件/接口：stage-09；失败测试：监控、健康、审计、备份、死信、容量、证据索引任一缺失或不可重算时停止；验证：独立目标观察；证据键：target134.rehearsal.09-operations-evidence。
- [ ] 10.18 演练第十段：独立完整范围审计。依赖：10.9-10.17；主要文件/接口：stage-10、launch-coverage-audit.mjs；失败测试：引用自身、历史证据或任一 FAILED/UNKNOWN/SKIPPED/缺证时拒绝通过；验证：前九段支撑 LAUNCH-01 至 LAUNCH-14；证据键：target134.rehearsal.10-independent-audit。
- [ ] 10.19 重启并核对权威状态不漂移。依赖：10.18；主要文件/接口：post-rehearsal-restart-verify.sh；失败测试：进程来源、提交、迁移、平台标准或两机构版本漂移时失败；验证：重启前后独立摘要一致；证据键：target134.post-rehearsal.restart-consistent。
- [ ] 10.20 创建演练后第二时点备份。依赖：10.19；主要文件/接口：backup.sh；失败测试：复用清理前备份、缺患者资源/执行证据或摘要不完整时失败；验证：新 backup-id 与时间点、完整 manifest；证据键：target134.backup.after-rehearsal。
- [ ] 10.21 完成第二次隔离联合恢复和事件重放。依赖：10.20；主要文件/接口：restore.sh、clinical-event-replay.sh；失败测试：关键主体/资产/机构版本/患者资源/执行证据或事件重放任一不一致时失败；验证：新隔离库和目录独立通过；证据键：target134.restore.after-rehearsal-second。
- [ ] 10.22 固定 48-72 小时观察窗口。依赖：10.21；主要文件/接口：stability-observer.mjs start；失败测试：窗口长度、hostname/commit/digest/run-id、采样计划或 99.9% 阈值未固定时不得开始；验证：签名观察计划；证据键：target134.stability.window-declared。
- [ ] 10.23 连续采样稳定性指标。依赖：10.22；主要文件/接口：stability-observer.mjs sample；失败测试：readiness/TLS/DB/版本/B0/任务死信/备份/审计/容量/错误率/延迟任一采样缺失时窗口失效；验证：每个采样绑定本次 run-id；证据键：target134.stability.samples-continuous。
- [ ] 10.24 完成或重开完整稳定窗口。依赖：10.23；主要文件/接口：stability-observer.mjs finalize；失败测试：故障、非计划重启、漂移、安全异常、证据中断或拼接时间片时失败并从零重开；验证：连续 48-72h、可用性不低于 99.9%；证据键：target134.stability.window-passed。
- [ ] 10.25 从 134 导出正式完整资源包。依赖：10.24；主要文件/接口：GET package file API、包注册表；失败测试：文件/摘要/签名/注册事实任一不可独立回读时失败；验证：在隔离介质复制后重新验签和 SM3；证据键：target134.migration.export-full-mkp。
- [ ] 10.26 在完全独立空医院导入、激活、运行和回滚。依赖：10.25；主要文件/接口：upload/pull、preflight/materialize/activate/rollback API；失败测试：共享 DB/目录/缓存/身份、部分可见、TOFU 或依赖 evidenceId 时失败；验证：真实消费者、断连 B0、CAS 回滚均由医院本地库完成；证据键：target134.migration.blank-hospital-roundtrip。
- [ ] 10.27 生成唯一完整上线结论。依赖：10.1-10.26；主要文件/接口：launch-coverage-audit.mjs；失败测试：LAUNCH-01 至 LAUNCH-15、35 入口、全部矩阵、两次恢复、稳定窗口、双实例迁移任一缺失时拒绝 PASSED；验证：零 FAILED/UNKNOWN/SKIPPED/缺证且索引不含秘密/患者明文；证据键：target134.launch.final-passed。

## 11. 复制医院实例并保证未来知识源迁移连续

- [ ] 11.1 生成医院最小交付集合。依赖：10.27；主要文件/接口：同一签名软件包、同一 .mkp、公开信任材料、site overlay；失败测试：包含 134 DB/患者/平台私钥/凭据/主机配置/运行证据或字节被改写时失败；验证：交付 inventory 与签名摘要；证据键：hospital.delivery.minimal-private-state-free。
- [ ] 11.2 从空 openEuler/PostgreSQL 主机安装独立医院。依赖：11.1；主要文件/接口：onprem install 动作；失败测试：实例/机构/密钥/审计身份复用 134 或非首发 profile 未在支持矩阵时失败；验证：空机断网安装、唯一 V1 和严格 readiness；证据键：hospital.install.independent-instance。
- [ ] 11.3 导入平台资源并验证医院职责与 B0。依赖：11.2；主要文件/接口：统一 .mkp 导入服务、35 入口目录；失败测试：四职责/组织范围、机构版本解析、B0、第三方 NOT_CONNECTED、回滚、备份恢复任一缺失时不宣称交付通过；验证：本地数据库和审计回读；证据键：hospital.acceptance.b0-role-scope。
- [ ] 11.4 保持断连只读消费与本地覆盖隔离。依赖：11.3；主要文件/接口：HospitalTrustCheckpointService、机构覆盖服务；失败测试：断连无法使用最后验证版本、本地覆盖反写 t-1 或冒用 authorityId 时失败；验证：平台离线运行与拒绝审计；证据键：hospital.offline.readonly-platform-local-overlay。
- [ ] 11.5 登记未来 issuer 的独立身份和 key。依赖：3.14、11.4；主要文件/接口：IssuerRegistrationService、HSM/KMS；失败测试：复用 134 issuerInstanceId/keyId、未处于 STANDBY 或缺信任链时失败；验证：只读权威状态；证据键：authority.future-issuer.independent-standby。
- [ ] 11.6 冻结 134 并核对全量摘要。依赖：11.5；主要文件/接口：AuthorityHandoverService.freeze/verify；失败测试：冻结后仍能签发，或 DB/原件/审计/注册表/信任链任一摘要不一致时失败并恢复旧状态；验证：交接预检证据；证据键：authority.migration.freeze-and-verify。
- [ ] 11.7 签署交接并原子切换 issuer/key。依赖：11.6；主要文件/接口：AuthorityHandoverService.activate、HSM/KMS fence；失败测试：未同时围栏旧 key、授权新 key、递增交接序号或 CAS 冲突时整体失败；验证：唯一活动 issuer 和 key 状态；证据键：authority.migration.atomic-key-switch。
- [ ] 11.8 验证旧 134 快照无法产生可接受新包。依赖：11.7；主要文件/接口：StaleIssuerSnapshotRejectionIT、医院验签服务；失败测试：旧快照以旧 issuer/key/交接序号签包若任一医院接受则失败；验证：平台注册拒绝与医院导入拒绝双证据；证据键：authority.migration.stale-134-rejected。
- [ ] 11.9 验证医院信任和发布序列连续。依赖：11.7-11.8；主要文件/接口：未来知识源导出、医院 pull/upload；失败测试：authorityId/root fingerprint/发布序号/父摘要变化或医院需重新 TOFU 时失败；验证：既有医院导入未来知识源新包并保持本地覆盖；证据键：authority.migration.hospital-trust-continuity。

## 12. 同步主规格并归档 OpenSpec

- [ ] 12.1 将已实施 delta 同步到主规格。依赖：11.9；主要文件/接口：openspec/specs 下 launch-convergence、platform-knowledge-authority、portable-medical-resource-package、medical-resource-factory、onprem-offline-delivery、target-environment-rollout 主规格；失败测试：change spec 与主规格 requirement/scenario 缺失或冲突时失败；验证：openspec validate --all --strict --no-interactive；证据键：openspec.main-specs.synced。
- [ ] 12.2 完成最终任务、代码和证据核对。依赖：12.1；主要文件/接口：本 tasks.md、docs/\_HANDOFF.md、最终 commit/run-id/证据索引；失败测试：任一 checkbox 未完成、证据不可重算、deferred issue 被冒领或秘密/患者明文命中时禁止归档；验证：全量门禁、OSV、DIFF；证据键：openspec.archive.preflight-complete。
- [ ] 12.3 归档变更并验证归档后主规格。依赖：12.2；主要文件/接口：openspec archive converge-full-launch-and-knowledge-platform、openspec/changes/archive；失败测试：归档命令报告未同步/未完成或归档后 strict 失败时不得删除当前变更；验证：openspec archive converge-full-launch-and-knowledge-platform --yes && openspec validate --all --strict --no-interactive；证据键：openspec.change.archived-strict。
- [ ] 12.4 更新最终接力与 Git 收尾。依赖：12.3；主要文件/接口：docs/\_HANDOFF.md、最终中文 PR/commit、origin/main；失败测试：主线不含归档提交、远端 CI 非全绿或残留临时分支/worktree 时不得宣称完成；验证：git fetch origin、merge-base、git worktree list、远端分支检查；证据键：project.launch-convergence.closed-on-main。
