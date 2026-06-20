# 全系统医疗产品体验与模型生产控制台实施计划

> 本计划实施设计：`docs/superpowers/specs/2026-06-20-full-system-medical-product-usability-design.md`。每项改动遵循 TDD，先看到目标测试失败，再写最小实现使其通过；每个逻辑单元独立提交，禁止把未验证的跨域改动堆入同一提交。

## 目标

把全系统从“功能目录”改造成“可完成任务的医疗产品”，并优先交付一个统一的模型生产控制台，使授权角色可在前台完成 Provider、Key、健康检查、医学评测、独立签署、九项闸门和正式模型知识生产。正式生产入口只允许大模型，未激活的非模型候选以保留审计的方式退出正式审核池。

## 完成边界

- P0 问题为零：无凭据泄漏、错误授权、错误生产者、错误修复入口或关键任务阻断。
- P1 问题全部关闭，或仅剩已登记且不影响当前任务的外部依赖。
- 14 个职责角色均能从工作台进入主任务，并完成真实操作或获得诚实、可行动的空态。
- 模型 Key 前台安全维护，明文不回显、不入日志、不入审计。
- 公共正式生产 API 只接受 `API_MODEL`，模型失败不回退为 B0 候选。
- 已确认的未激活非模型候选退出正式审核池，ACTIVE 知识不变。
- 前后端、五方言迁移、T-GATE、浏览器桌面/窄屏和 134 部署证据全部通过。

---

## Task 1：建立全系统功能唯一清单和问题关闭账本

**文件：**

- 新建：`docs/audit/full-system-functional-inventory.md`
- 新建：`docs/audit/full-system-usability-findings.md`
- 修改：`docs/audit/product-role-journeys.md`
- 修改：`frontend/src/shared/config/routes.ts`
- 修改：`medkernel-backend/src/main/java/com/medkernel/engine/security/MenuPermissionCatalog.java`
- 测试：`frontend/src/shared/config/routes.test.ts`
- 测试：`medkernel-backend/src/test/java/com/medkernel/engine/security/MenuPermissionCatalogTest.java`

**步骤：**

1. 新增契约测试，从授权路由和菜单目录提取产品面、主角色、主任务、权限与页面路径；缺少任一字段时失败。
2. 运行前后端目标测试，确认当前路由缺少任务元数据、菜单与路由归类漂移导致失败。
3. 为全部认证路由补齐 `productArea`、`primaryRole`、`primaryTask`，同步菜单目录顺序。
4. 生成功能清单，逐项记录前端、后端、主任务、六态、分页、证据和 P0/P1/P2 结论。
5. 建立问题账本，状态只允许 `OPEN`、`IN_PROGRESS`、`VERIFIED`、`DEFERRED_EXTERNAL`；每项必须有复现、改造范围和验证证据。
6. 运行目标测试及文档死链检查。
7. 提交：`docs: 建立全系统功能清单与问题关闭账本`。

## Task 2：把角色旅程从“可打开”升级为“可完成任务”

**文件：**

- 修改：`frontend/e2e/helpers/auth.ts`
- 修改：`frontend/e2e/helpers/roleCredentials.ts`
- 新建：`frontend/e2e/role-primary-task.spec.ts`
- 修改：`frontend/e2e/all-routes-by-role.spec.ts`
- 修改：`frontend/src/shared/config/productRoleJourneys.ts`
- 测试：`frontend/src/shared/config/productRoleJourneys.test.ts`
- 修改：`docs/audit/product-role-journeys.md`

**步骤：**

1. 为凭据加载器补测试，支持主账号文件中的 `username` 覆盖和只读会话模式；禁止自动改密码、自动绑定 MFA 或写回账号状态。
2. 运行测试，确认现有“用户名等于角色”假设在平台临时账号上失败。
3. 只解析唯一主账号格式，删除旧格式分支和兼容测试，日志仅输出角色和账号别名。
4. 为 14 个职责角色定义一个主任务：入口、准备条件、主动作、成功证据、诚实空态、无权限边界和接力角色。
5. 新增主任务 E2E；不得只断言标题，必须断言动作、HTTP 结果和页面反馈。
6. 对当前无法安全写入生产数据的角色使用只读证据路径，明确记录待部署后执行的写入验收。
7. 运行角色旅程测试、全路由测试和 390px 视口测试。
8. 提交：`test: 建立十四角色真实主任务验收`。

## Task 3：新增模型凭据五方言存储

**文件：**

- 新建：`medkernel-backend/src/main/resources/db/migration/h2/V158__llm_provider_credential_vault.sql`
- 新建：`medkernel-backend/src/main/resources/db/migration/postgres/V158__llm_provider_credential_vault.sql`
- 新建：`medkernel-backend/src/main/resources/db/migration/oracle/V158__llm_provider_credential_vault.sql`
- 新建：`medkernel-backend/src/main/resources/db/migration/dm/V158__llm_provider_credential_vault.sql`
- 新建：`medkernel-backend/src/main/resources/db/migration/kingbase/V158__llm_provider_credential_vault.sql`
- 修改：`medkernel-backend/src/test/java/com/medkernel/migration/FlywayMultiDialectSmokeTest.java`
- 修改：`medkernel-backend/src/test/java/com/medkernel/migration/H2BaselineMigrationTest.java`
- 修改：`medkernel-backend/src/test/java/com/medkernel/migration/MigrationBaselineContractTest.java`

**步骤：**

1. 先把最新迁移版本目标改为 158，并在合同测试中要求表、租户唯一约束、乐观锁、审计字段、中文注释和必要索引。
2. 运行迁移目标测试，确认五方言缺少 V158 而失败。
3. 在五方言新增 `mk_llm_provider_credential`，字段包括租户、Provider、密文、SHA-256 指纹、尾四位、锁版本、创建/更新人时间、traceId。
4. 添加 `(tenant_id, provider_code)` 唯一约束和租户查询索引；所有 COMMENT 使用简体中文。
5. 运行 H2 基线、五方言 smoke 和迁移合同。
6. 提交：`feat: 新增模型凭据加密存储基线`。

## Task 4：实现模型凭据独立用途加密与租户解析

**文件：**

- 新建：`medkernel-backend/src/main/java/com/medkernel/engine/llm/provider/ModelProviderCredential.java`
- 新建：`medkernel-backend/src/main/java/com/medkernel/engine/llm/provider/ModelProviderCredentialRepository.java`
- 新建：`medkernel-backend/src/main/java/com/medkernel/engine/llm/provider/ProviderCredentialCodec.java`
- 新建：`medkernel-backend/src/main/java/com/medkernel/engine/llm/provider/VaultProviderCredentialResolver.java`
- 修改：`medkernel-backend/src/main/java/com/medkernel/engine/llm/provider/ProviderCredentialResolver.java`
- 删除：`medkernel-backend/src/main/java/com/medkernel/engine/llm/provider/EnvProviderCredentialResolver.java`
- 修改：调用 `ProviderCredentialResolver` 的模型 Provider 适配器
- 测试：`medkernel-backend/src/test/java/com/medkernel/engine/llm/provider/ProviderCredentialCodecTest.java`
- 测试：`medkernel-backend/src/test/java/com/medkernel/engine/llm/provider/VaultProviderCredentialResolverTest.java`

**步骤：**

1. 写失败测试证明：相同明文在模型凭据上下文中可往返，但不能被字段级 D3/D4 上下文解密；API 对象和异常不得包含明文。
2. 写失败测试证明模型调用只读取租户凭据库，缺失时返回结构化未配置状态，不读取环境变量。
3. 使用 `MEDKERNEL_FIELD_ENCRYPTION_KEY` 派生 `medkernel:llm:provider-credential:` 独立 SM4 密钥，不复用 JWT 或 MFA 密钥。
4. 将 resolver 签名改为显式接收 `tenantId` 和 `providerCode`，逐个更新 Provider 适配器，禁止从线程外隐式猜租户。
5. 对解密和 HTTP 认证异常做脱敏；日志仅允许 providerCode、credentialLast4、traceId。
6. 运行 provider、加密和日志泄漏目标测试。
7. 提交：`feat: 实现租户级模型凭据安全解析`。

## Task 5：补齐 Provider 列表、Key 轮换和高风险治理 API

**文件：**

- 新建：`medkernel-backend/src/main/java/com/medkernel/engine/llm/provider/ModelProviderCredentialUpsertRequest.java`
- 新建：`medkernel-backend/src/main/java/com/medkernel/engine/llm/provider/ModelProviderCredentialView.java`
- 修改：`medkernel-backend/src/main/java/com/medkernel/engine/llm/provider/ModelProviderController.java`
- 修改：`medkernel-backend/src/main/java/com/medkernel/engine/llm/provider/ModelProviderGovernanceService.java`
- 修改：`medkernel-backend/src/main/java/com/medkernel/engine/llm/provider/ModelProviderConfigRepository.java`
- 修改：`medkernel-backend/src/main/java/com/medkernel/engine/llm/provider/ModelProviderGovernanceView.java`
- 测试：`medkernel-backend/src/test/java/com/medkernel/engine/llm/provider/ModelProviderControllerSecurityTest.java`
- 测试：`medkernel-backend/src/test/java/com/medkernel/engine/llm/provider/ModelProviderGovernanceServiceTest.java`
- 测试：`medkernel-backend/src/test/java/com/medkernel/engine/llm/provider/ModelProviderConfigRepositoryTest.java`

**步骤：**

1. 写失败测试覆盖：租户分页列表、Key 永不回显、无 MFA 拒绝、确认语句错误拒绝、原因过短拒绝、expectedVersion 冲突、轮换后强制停用并重置健康、删除后强制停用、跨租户不可见。
2. 运行测试确认现有 Controller 仅支持单条读取且无凭据 API。
3. 实现分页 `GET /api/v1/model-providers`。
4. 实现 `PUT /{providerCode}/credential` 和 `DELETE /{providerCode}/credential`；沿用 `llm.provider.manage`，并强制 MFA、高风险确认、实质原因、乐观锁和审计。
5. 响应只返回 `configured`、指纹尾标、更新时间、更新人和版本；序列化扫描不得出现 ciphertext 或 plaintext。
6. 凭据更新后 Provider 保持停用、健康状态改为待验证；启用仍要求真实健康、当前评测和部署形态。
7. 运行目标测试、Controller 安全测试和 API 合同测试。
8. 提交：`feat: 补齐模型服务与凭据治理接口`。

## Task 6：公共正式生产入口强制仅使用大模型

**文件：**

- 修改：`medkernel-backend/src/main/java/com/medkernel/engine/knowledge/production/KnowledgeProductionController.java`
- 新建：`medkernel-backend/src/main/java/com/medkernel/engine/knowledge/production/FormalKnowledgeProductionPolicy.java`
- 修改：`medkernel-backend/src/main/java/com/medkernel/engine/knowledge/production/ProductionJobRequest.java`
- 测试：`medkernel-backend/src/test/java/com/medkernel/engine/knowledge/production/KnowledgeProductionControllerSecurityTest.java`
- 测试：`medkernel-backend/src/test/java/com/medkernel/engine/knowledge/production/KnowledgeProductionOrchestrationServiceTest.java`
- 修改：`frontend/src/shared/api/knowledgeProduction.ts`
- 测试：`frontend/src/shared/api/knowledgeProduction.test.ts`

**步骤：**

1. 写失败测试：公共 `/jobs` 对 `AGENT_TOOL`、`LOCAL_MODEL`、`MANUAL` 和 B0 生产者返回明确 400；`API_MODEL` 保持可用；内部初始化服务不受公共接口策略误伤。
2. 写失败测试：模型调用失败时 job 失败且候选数为零，不能静默回退 B0。
3. 在 Controller 边界应用正式生产策略，服务内部 B0 初始化使用明确的内部方法和调用路径。
4. 将前端创建类型固定为 `API_MODEL`，移除调用方提供生产者的能力。
5. 运行生产编排、Controller 安全、前端 API 测试。
6. 提交：`fix: 正式知识生产仅允许大模型`。

## Task 7：构建统一模型生产控制台

**文件：**

- 重构：`frontend/src/pages/quality/KnowledgeProduction.tsx`
- 新建：`frontend/src/pages/knowledge-production/ModelProductionConsole.tsx`
- 新建：`frontend/src/pages/knowledge-production/ProviderSetupPanel.tsx`
- 新建：`frontend/src/pages/knowledge-production/MedicalEvaluationPanel.tsx`
- 新建：`frontend/src/pages/knowledge-production/ProductionReadinessPanel.tsx`
- 新建：`frontend/src/pages/knowledge-production/ProductionJobPanel.tsx`
- 新建：`frontend/src/shared/api/modelProviders.ts`
- 修改：`frontend/src/shared/api/modelEvaluation.ts`
- 修改：`frontend/src/shared/api/knowledgeProduction.ts`
- 新建：`frontend/src/pages/knowledge-production/IndependentMedicalReviewPanel.tsx`
- 修改：`frontend/src/shared/config/routes.ts`
- 修改：`frontend/src/shared/config/productRoleJourneys.ts`
- 修改：`frontend/src/pages/workbench/ReadinessValidation.tsx`
- 测试：对应 `*.test.tsx`、API hook 测试和路由测试

**步骤：**

1. 先写页面测试，要求同页按顺序显示“模型服务与 Key → 医学评测 → 独立复核 → 九项生产闸 → 开始生产”，并按权限隐藏动作但保留责任角色。
2. 写 Provider 面板测试：密码框禁止自动填充，保存成功清空；只显示尾标；健康、启停和错误六态完整。
3. 写评测面板测试：可创建当前制品评测，历史指纹明确不可放行，签署必须逐例确认且与运行人分离。
4. 写 readiness 测试：九闸按依赖顺序展示，修复链接统一指向 `/knowledge/production?step=provider|evaluation|readiness`，不再误指 `/system/providers` 或 `/advanced/ai-workflows`。
5. 写生产测试：页面无生产者选择；未全绿时主按钮禁用并展示阻断；提交固定 `API_MODEL`；失败不显示“已产生候选”。
6. 实现 Provider hooks、凭据轮换表单、高危确认、探活和启停。
7. 把医学回归复核收敛为控制台内置步骤，删除 `/qc/model-evaluations` 旧路由、旧菜单权限和独立页面包装，只保留一套状态逻辑。
8. 把现有任务、候选、11 门禁、8 态分流和影子证据移入生产任务区域；技术字段进入专家模式。
9. 更新工作台角色旅程：集成运维员、质量治理专家、平台知识治理员和系统超级管理员从同一控制台接力。
10. 运行组件、hooks、路由、角色旅程、可访问性和 390px 测试。
11. 提交：`feat: 上线统一模型生产控制台`。

## Task 8：受控退出未激活非模型候选

**文件：**

- 新建：`medkernel-backend/src/main/java/com/medkernel/engine/knowledge/production/NonModelCandidateRetirementService.java`
- 新建：`medkernel-backend/src/main/java/com/medkernel/engine/knowledge/production/NonModelCandidateRetirementRequest.java`
- 新建：`medkernel-backend/src/main/java/com/medkernel/engine/knowledge/production/NonModelCandidateRetirementResult.java`
- 修改：`medkernel-backend/src/main/java/com/medkernel/engine/knowledge/production/KnowledgeProductionController.java`
- 修改：`medkernel-backend/src/main/java/com/medkernel/engine/knowledge/production/KnowledgeProductionCandidateRepository.java`
- 修改：`medkernel-backend/src/main/java/com/medkernel/engine/knowledge/KnowledgeVersionService.java`
- 测试：`medkernel-backend/src/test/java/com/medkernel/engine/knowledge/production/NonModelCandidateRetirementServiceTest.java`
- 测试：`medkernel-backend/src/test/java/com/medkernel/engine/knowledge/production/KnowledgeProductionReleaseStateMachineIntegrationTest.java`
- 新建：`scripts/drill/retire-non-model-candidates.mjs`
- 新建：`scripts/drill/retire-non-model-candidates.test.mjs`

**步骤：**

1. 写失败测试精确定义候选范围：`generatedByModel=false` 或 job producer 非 `API_MODEL`，未激活，状态仅限待编著/待审核/待替换审核，并且 identity/version/job 在显式清单内。
2. 写失败测试证明 ACTIVE、已发布、模型候选和清单外候选绝不改变。
3. 实现 dry-run 返回版本、任务和审核项数量及稳定摘要；执行模式要求 `knowledge.withdraw`、MFA、确认语句、原因和 dry-run 摘要一致。
4. 在单事务中把候选版本置为 `WITHDRAWN` 或现有等价终态，关闭/驳回关联审核任务，保留原 job 与生产证据并追加审计原因。
5. 脚本默认 dry-run，只有传入显式确认和摘要才执行；禁止通配全库删除。
6. 运行服务、状态机、脚本和 ACTIVE 不变量测试。
7. 提交：`feat: 受控退出非模型候选审核池`。

## Task 9：重排信息架构和高频任务入口

**文件：**

- 修改：`frontend/src/shared/config/routes.ts`
- 修改：`frontend/src/shared/config/productRoleJourneys.ts`
- 修改：`medkernel-backend/src/main/java/com/medkernel/engine/security/MenuPermissionCatalog.java`
- 修改：`medkernel-backend/src/main/java/com/medkernel/engine/security/DefaultPermissionPolicy.java`
- 修改：工作台卡片配置与对应页面
- 修改：`docs/audit/product-role-journeys.md`
- 测试：路由、菜单、默认权限和角色旅程测试

**步骤：**

1. 先写排序和归类测试，固定八个产品面及其业务顺序；测试失败时输出漂移菜单。
2. 按设计重排机构人员、知识治理、知识生产、临床协同、质量管理、合规安全和系统运维。
3. 从医院质量日常面移除医学回归复核的主入口，把全真沙盘限制为实施/治理角色。
4. 工作台只保留可行动待办、阻断和证据；每张卡必须有真实对象 ID 或可行动空态。
5. 更新默认权限快照，确保菜单可见性与 API 权限一致。
6. 运行菜单、默认权限、工作台和 14 角色旅程测试。
7. 提交：`refactor: 按医疗任务重排全系统功能入口`。

## Task 10：逐产品面关闭 P0/P1/P2

按以下固定批次执行，每批独立提交；每个发现必须先进入问题账本，再由失败测试复现，修复后把状态改为 `VERIFIED`：

1. 机构与人员：机构开通 → 账号 → 身份来源。
2. 知识治理：来源 → 结构化维护 → 组包 → 审核发布 → 关系。
3. 临床协同：患者 → 路径 → 推荐 → 任务 → 随访。
4. 质量管理：发现 → 整改 → 医保 → 指标。
5. 合规安全：审计证据 → 安全配置。
6. 系统运维：实施验收 → 接入 → 运行保障 → 国产化 → 诊断。

**每批共同步骤：**

1. 对主角色执行桌面和 390px 真实旅程，记录页面、动作、HTTP、控制台、空/错/无权限态。
2. P0：立即修复并补安全/权限/状态机测试。
3. P1：合并分散入口、补修复链接、接力角色和服务端分页。
4. P2：统一中文术语、主按钮、默认筛选、信息密度、键盘和屏幕阅读器。
5. 运行该产品面的后端、前端、E2E、路由和权限回归。
6. 更新功能清单、问题账本、角色旅程和 `_HANDOFF.md`。
7. 提交格式：`fix(<产品面>): 关闭功能与体验问题`。

## Task 11：完整验证、评审、PR 和 134 发布

**文件：**

- 修改：`docs/_HANDOFF.md`
- 修改：`docs/audit/full-system-functional-inventory.md`
- 修改：`docs/audit/full-system-usability-findings.md`
- 修改：`docs/audit/product-role-journeys.md`
- 新建：`docs/release/evidence/model-production-console-20260620/`

**步骤：**

1. 后端：运行受影响模块测试，再运行全量 `clean verify`；统计 Surefire XML、tests、failures、errors、skipped。
2. 迁移：运行 H2 基线、五方言 Flyway smoke、迁移合同和中文注释门禁。
3. 前端：运行组件/API 测试、14 角色 E2E、全部授权路由、390px、`npm run verify` 和生产构建。
4. 安全：扫描 Key、ciphertext、Authorization、患者数据和日志；证明凭据 API 永不回显明文。
5. 产品：运行产品目录、菜单/权限、六态、服务端分页和 T-GATE。
6. 浏览器：使用真实角色验证模型控制台五步骤、错误恢复、无权限和跨角色接力；控制台与未预期 HTTP 错误为零。
7. 请求独立代码评审，逐条核实意见并修复 P0/P1。
8. 更新 `_HANDOFF.md`，明确本地、PR、合并、部署和生产数据各自状态，禁止把任一阶段提前写成完成。
9. 推送 `codex/model-production-console`，创建中文 PR；CI 全绿后 squash 合并，确认 `origin/main` 含合并提交。
10. 从最新 `origin/main` 构建并部署 134，核对 commit、JAR SHA、Flyway V159、服务健康和发布指纹。
11. 先对非模型候选执行 dry-run，核对 8 个基础 B0 候选和 WHO B0 样本；摘要一致后执行受控退出，复核 ACTIVE=0 未改变、模型候选未受影响。
12. 在前台录入真实 Provider Key，探活、运行当前制品医学评测、由独立专家签署、复核九闸，再创建第一条 `API_MODEL` 正式生产任务。
13. 保存脱敏证据，更新问题账本；只有全部完成判据满足后才能声明系统可正常使用。
