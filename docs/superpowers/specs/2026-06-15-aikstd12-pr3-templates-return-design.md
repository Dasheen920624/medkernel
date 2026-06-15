# AIK-STD-12 设计 · PR3 收尾 = 全专业资产模板（FR-1）+ 候选退修（FR-3）

> 卡：[AIK-STD-12](../../cards/wave2/AIK-STD-12.md)（7d 大卡）。前置：[核心 CONSTITUTION](../../CONSTITUTION.md) + [PR1/PR2 设计](2026-06-15-aikstd12-aireview-ai-provenance-design.md)。
> 续接从最新 `origin/main` `b95a9388` 起。**本切片为本卡最后一刀**：补齐 FR-1（全专业模板）+ FR-3（退修），使全部 FR 真实勾完闭卡。

## 1. 范围与现状

PR1（#623）落 AI 来源溯源读模型，PR2（#624）前端审核台接 AI 标识/溯源。本卡剩两件未竟：

- **FR-1 全专业模板**：术语/规则/路径/推荐/指标/随访/护理/报告/中医/医保等各专业标准资产模板——**未建**。
- **FR-3 退修**：PR2 明确未做——后端 `KnowledgeCandidateReviewDecision` 仅 `APPROVE|REJECT`，无 RETURN 退修态。

其余 FR（FR-2 AI 候选审核 / FR-4 AI 标识署名 / FR-5 六态 RBAC）已在审核台成熟（AIREVIEW-01 + PR1/PR2）。

## 2. 关键核查结论（写给实现者）

- **VersionedAssetType**（17 类，结构维）与 **`engine.knowledge.KnowledgeDomain`**（11 值医学领域维：GUIDELINE/DRUG/NURSING/REPORT/TCM/POLICY/DIAGNOSIS…，对应 `knowledge_identity.domain`）正交。FR-1 列举的「专业」＝二者组合。**不新建资产类型**（守 spec）。
- 知识审核台候选经 `KnowledgeIdentity.domain` 暴露医学领域；前端 `KnowledgeGovernance.tsx` 已有 `selectedIdentity.domain` + `knowledgeDomainLabel()`，详情抽屉可直接按领域匹配模板。
- **退修迁移边界**：`mk_knowledge_candidate_classification.review_status`、`mk_knowledge_review_assignment.review_status` 与 `.decision` 三处 CHECK 约束建于 **V52（早已发布）**，**不可原地改**；须新建 **V132 五方言 ALTER**（金标先例 = V88 资产类型归一：`DROP CONSTRAINT` + `ADD CONSTRAINT … CHECK`，五方言同写法且过真实容器 smoke）。`LATEST_MIGRATION_VERSION` 131→132。
- **零新治理面**：模板端点挂既有 `KnowledgeProductionController`（已在产品目录 + `knowledge-production` 契约 + engine-knowledge 域），免新建控制器/契约/域登记；DomainOwnership 为表级（factory 无表不需登记），ApiContractGovernance 仅校验 `@RequestBody @Valid`（GET 无 body 不涉及）。

## 3. 工作线 A —— FR-1 全专业资产模板

**形态裁决**：代码态确定性注册表，**不建表、不做租户自定义**（无消费者需要，YAGNI + 守「非新表优先」）。模板逻辑落 `com.medkernel.engine.factory`（与 AIK-STD-01 信封/校验闸同域，构成「工厂资产标准」三件套）。

### 3.1 后端
- `TemplateSection`（record，factory）：`key` / `label` / `required`(boolean) / `hint`。**只编结构骨架（章节名），绝不预填医学内容**——守铁律 #1：模板是「待按真实来源填充的结构」非医学事实。章节取自既有专业文书结构标准（药品说明书法定项、护理程序五步等），Javadoc 注明结构出处，**不用「占位/演示/模拟/仿真」字样**（守真实性门禁禁词）。
- `ProfessionalAssetTemplate`（record，factory）：`professionCode`（稳定码）/ `displayName` / `assetType`(`VersionedAssetType`) / `knowledgeDomain`(`engine.knowledge.KnowledgeDomain`，结构型模板可空) / `List<TemplateSection> sections`。字段精到审核台真实消费，不多挂。
- `ProfessionalAssetTemplateRegistry`（@Service，factory）：返回不可变全专业目录（13 模板）。
  - **医学领域型（assetType=KNOWLEDGE × domain）——知识审核台可匹配**：指南(GUIDELINE)/药品说明书(DRUG)/护理(NURSING)/报告解读(REPORT)/中医(TCM)/医保政策(POLICY)/诊断(DIAGNOSIS)。
  - **结构型（assetType=TERMINOLOGY/RULE/PATHWAY/RECOMMENDATION/EVALUATION/FOLLOWUP，domain 空）——目录完整性，供编著/生产工作台**：术语/规则/路径/推荐/指标/随访。
  - 暴露 `findByAssetTypeAndDomain` 供匹配。
- `KnowledgeProductionController` 加 `GET /api/v1/engine/knowledge-production/asset-templates`（`@perm.has('knowledge.read')`，列全目录）。**无迁移、无新权限**；重生成 product-function-catalog（既有控制器端点 +1，MERGE）。

### 3.2 前端
- `useAssetTemplates()` hook（拉全目录，类型 `ProfessionalAssetTemplate`/`TemplateSection`）。
- `KnowledgeGovernance.tsx` 详情抽屉新增「专业标准模板」区：按 `selectedIdentity.domain` 匹配 KNOWLEDGE 型模板 → 渲染**结构清单（章节 label + 必填标记 + hint）**，供审核人对照核查完整性。无匹配诚实标「该领域暂无标准模板」。BASE-10 token，禁硬编码。
- 知识审核台只审 KNOWLEDGE 型资产，故仅医学领域型模板在此可见；结构型模板诚实留待对应工作台（不在本台臆造匹配）。

## 4. 工作线 B —— FR-3 退修（RETURN）

**语义**：退修 ≠ 驳回。驳回＝永久拒绝留档（终态）；**退修＝可修订，退回生产者并给修订意见，期待修订重提**——临床治理真实区分。

### 4.1 后端
- `KnowledgeCandidateReviewDecision` 加 `RETURN`；`CandidateReviewStatus` 加 `RETURNED`。
- `KnowledgeVersionService.reviewCandidate` 处理 RETURN（沿用 reject 分支同模式）：
  - **强制 `reason` 非空**（退修须说明修订要求，医疗安全；service 层条件校验，blank → `BAD_REQUEST`）。
  - 候选版本 status → `DRAFT`（退回草稿待修订、退出审核台队列，契合现 `REJECTED` 注释「可改回 DRAFT 继续完善」语义）。
  - classification.review_status → `RETURNED`（基础依据追加退修原因）。
  - `ReviewAssignment`(decision=`RETURN`, status=`RETURNED`, reason, actor) 留痕（署名留痕，守铁律 #3）。
  - 返回 `KnowledgeCandidateResponse(... "RETURNED", "候选已退修，退回生产者修订重提")`。
- **V132 五方言**：ALTER 三处 CHECK——`ck_review_assignment_decision` 加 `'RETURN'`、`ck_knowledge_candidate_review_status` 与 `ck_review_assignment_review_status` 各加 `'RETURNED'`，更新两处中文 COMMENT（注明退修态）。`MigrationBaselineContractTest` 加 `v132` 断言 + `EXPECTED_MIGRATIONS` 追加 V132；`H2BaselineMigrationTest` / `FlywayMultiDialectSmokeTest` 的 `LATEST_MIGRATION_VERSION` 131→132。

### 4.2 前端
- `KnowledgeCandidateReviewDecision` 类型 +`"RETURN"`；`CandidateReviewStatus` +`"RETURNED"`。
- 审核台审核动作区加**「退修」按钮**（弹必填修订意见 → 复用现 review 提交 decision=RETURN）；候选状态展示支持 RETURNED（标签）。主按钮仍 ≤1（发布/批准）。

## 5. 恒守红线
- **B0**：无 AI / 无模板不阻断人工审；模板与退修均为审核台增强，缺失不影响纯人工审/发。
- **P6 阻断**：本卡不触发真实知识生产；消费已落审核链候选。
- **铁律 #1**（模板只结构不预填内容、AI 标识来源真实）/ **#3**（退修留痕署名）/ **#6**（审过才发走替换链）。
- TDD 红绿 + 四门禁 changed + 五方言 Flyway smoke（V132 真实容器）+ 前端 `productCatalog.test.ts`（端点 +1）+ `vitest`/`tsc`/`eslint`/`prettier` + 合并 main 逐 PR 授权。

## 6. 测试计划（TDD 红先）
- 后端：`ProfessionalAssetTemplateRegistryTest`（目录完整性/13 模板/匹配/不可变/结构无空）；`KnowledgeVersionServiceTest` 增退修用例（RETURN 落 RETURNED + 版本回 DRAFT + assignment RETURN；reason 空拒收；非待审态重复审核拒）；`KnowledgeProductionControllerSecurityTest` 增 asset-templates 安全（knowledge.read 可读 / 越权 403）；`MigrationBaselineContractTest` V132 断言。
- 前端：`KnowledgeGovernance.test.tsx` 增「专业模板区按领域渲染」+「退修按钮提交 RETURN（必填意见）」。
