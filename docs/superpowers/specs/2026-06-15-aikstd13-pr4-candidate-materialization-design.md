# AIK-STD-13 PR4 候选真实物化（信封 → 版本/审核链 + 路由分派）· 设计

> 卡片：[docs/cards/wave2/AIK-STD-13.md](../../cards/wave2/AIK-STD-13.md)。批次 P2-B。日期 2026-06-15。
> 权威读序：核心 §6（审核后生效 / 高危会签）· §7（唯一权威 / 来源可溯）· 铁律 #1（真实性，不伪造）· #4（B0 先于模型）· #5（AI 只产候选）。
> 上游：PR1（#619）编排核心 + PR2（#620）生命周期/血缘 + PR3（#621）会签路由决策，均已合并入 main。

## 1. 关键核查（写给下个 AI）

| 假设 | 既有现实 | 裁决 |
|---|---|---|
| 候选物化需另起表 | **审核链已成熟**：`KnowledgeVersionService.classifyCandidate(identityId, KnowledgeVersionCreateRequest)` 已建 `KnowledgeAssetVersion`(PENDING_REPLACEMENT_REVIEW) + `mk_knowledge_candidate_classification`(4 态去重 NEW_ASSET/SAME_IDENTITY_NEW_VERSION/DUPLICATE/CONFLICT) + `mk_knowledge_review_assignment`（**已在 classifyCandidate 内创建**，当前 `assignedTo=提交人`） | **复用既有链，无新表** |
| 物化是纯机械解析 | **源 FK 机械、身份语义**：信封串 `sourceRef("code:版本:锚")`→`source_document_id/source_version_id` 可回查（机械 B0）；但 `classifyCandidate` 要先有 `identityId`，「候选属哪个知识主题」是**语义分流**（AIK-STD-04/10 解析管道的活，非纯 B0） | **生产方显式声明目标身份**，语义决定交人，物化纯机械 |
| 现审核台需新建 | **真实审核台 = `frontend/src/pages/quality/KnowledgeGovernance.tsx`**（卡片「现状」写的 `AiReview.tsx` 已在一期菜单整治改名/并入）：候选审核页签已含身份台账→候选列表(判定+审核状态)→对照抽屉(ACTIVE vs 候选 diff + 来源锚 + contentHash)→通过并发布/驳回（12 字段 context + 高危电子签名 + 平台质量门 + 幂等），走 `KnowledgeVersionController` `/api/v1/engine/knowledge` | **物化产物自动进现审核台，本卡不动前端** |
| 一个 KnowledgeDomain | **两条正交轴同名**：`engine.knowledge.KnowledgeDomain`＝内容域（GUIDELINE/DRUG/NURSING/REPORT/TCM/PROTOCOL/POLICY/LITERATURE/OTHER/DIAGNOSIS/PATHWAY_KNOWLEDGE，11 值，`knowledge_identity.domain`）；`engine.knowledge.production.KnowledgeDomain`＝路由域（CLINICAL/PHARMACY/TERMINOLOGY_REPORT/EVALUATION_INSURANCE/GENERAL，5 值，PR3 会签路由） | 建身份用**内容域**，路由用**路由域**，本设计全程显式区分 |

**当前桩**：`StagingCandidateIntake.intake(job, envelope)` 返回 `"staged:<assetIdentity>"`，**不物化**（PR1 诚实占位「不伪装已物化」）。本卡以正式物化实现替换之。

## 2. 范围（用户裁决）

- **端到端最小闭环 · 仅 discovery-origin**：把已产出候选物化进版本/审核链 + 据 PR3 路由建 ReviewAssignment + 自动进现审核台可审/发。MANUAL/无受控源 FK 候选留下一刀。
- **身份解析**：生产方显式声明 `MaterializationTarget`——`targetIdentityId`（现有→走 SAME_IDENTITY_NEW_VERSION/DUPLICATE/CONFLICT）**异或** `newIdentity{内容域 + 主题 + identityCode}`（不存→find-or-create）。新建身份 `status=ACTIVE`（**身份壳＝有效主题容器**；`KnowledgeIdentityStatus` 无 DRAFT 态，ACTIVE 仅表「主题存在」）；**权威性在版本层把关**——版本恒 `PENDING_REPLACEMENT_REVIEW`、无 ACTIVE 版本即不参与临床（核心 §6 审过才发），故新身份壳零权威风险。
- **源 FK**：`SourceReferenceResolver` 回查解析；解析不出**诚实结构化拒收**（铁律 #1，不伪造 FK、不半物化）。
- **双签**：据 PR3 路由建 `{归口}∪{领域(异于归口时)}` 条 `ReviewAssignment`（谁该审）；**不做「必须两签才发」强制**（留下刀，不动现有 `reviewCandidate` 单决流）。
- **接口形状**：改 `submitCandidate` 加 `target` 入参、删 `StagingCandidateIntake` 桩（greenfield 无兼容包袱，端口契约可改）。
- **恒守**：不碰 P6（外部模型/源接入）；仅物化已产出的确定性候选。

## 3. 组件设计（全在 `com.medkernel.engine.knowledge` 域内，无跨模块边界）

### 3.1 `SourceReferenceResolver`（@Service，B0 纯确定性）
- 输入：`AssetSourceRef.sourceRef`（串 `"sourceCode:versionNo:anchorPath"`，与 `DiscoveryOrchestrationService` 产出格式对齐）+ 当前租户。
- 解析：`source_document`(by sourceCode + tenant) → `source_version`(by document + versionNo) → 返回 `ResolvedSource(sourceDocumentId, sourceVersionId, anchorPath)`。
- 失败：源不存在/版本不存在 → 抛结构化 `ApiException`，复用既有 `ENG_KNOW_001`（来源文献不存在）族错误码，**拒收候选，不伪造 FK**。
- 强租户隔离（仅解析当前租户受控源）。

### 3.2 `MaterializationTarget`（record）
```
targetIdentityId : Long      // 现有身份（二选一）
newIdentity      : NewIdentitySpec{ domain: engine.knowledge.KnowledgeDomain, subject, identityCode }  // 新身份壳（二选一）
```
- 校验：恰好其一非空（both null / both set → 结构化 400）。

### 3.3 `MaterializingCandidateIntake`（@Component，替换 `StagingCandidateIntake`，实现 `KnowledgeCandidateIntake`）
- 端口签名升级：`intake(KnowledgeProductionJob job, KnowledgeAssetEnvelope candidate, MaterializationTarget target) → MaterializationResult`。
- 编排：
  1. 解析身份：`targetIdentityId` 校验存在（租户内）；或 `newIdentity` 按 `(tenant, identityCode)` find，不存则 create 非权威身份壳（`KnowledgeIdentityStatus` 初始非 ACTIVE 权威态；用内容域+主题+identityCode）。
  2. 解析源 FK：对信封 `sources`（≥1，AIK-STD-01 已保证）取首条经 `SourceReferenceResolver`。
  3. 构造 `KnowledgeVersionCreateRequest`：content=`payload`、`sourceDocumentId/sourceVersionId`=解析值、`versionNo`=`versionLabel`（空则生成）、`riskLevel`=信封值、`gradeQuality`=信封值**或 `VERY_LOW`**（信封无 GRADE 时取最保守值，诚实不夸大证据质量、不伪造高质量；铁律 #1）、`gradeStrength`=信封值（可空）、`anchors`=解析出的 anchorPath、`reviewCycleMonths`=默认 12、context（tenant/traceId/userId 从 `RequestContext`，余 null；`validateContext` 仅校租户一致）。
  4. 调 `KnowledgeVersionService.classifyCandidate(identityId, request, 路由分派计划)`（见 3.4）。
  5. 返回**真实物化版本引用串**（如 `kv:<versionId>`）作 candidateRef。

### 3.4 `KnowledgeVersionService.classifyCandidate` 聚焦扩展
- 加可选「路由分派计划」入参 `ReviewAssignmentPlan`（来自 PR3 `ReviewRoutingDecision`：归口角色 + 领域角色 + 是否双签）。
- 传计划：建 `{归口}∪{领域(异于归口时)}` 条 `ReviewAssignment`，`assignedTo`=路由角色码（非提交人）。
- 传 null（既有调用方＝现审核台 `createVersion` 端点）：**保持原行为**（单行 `assignedTo=提交人`），**零回归**。
- DUPLICATE 路径不变（去重不产审核待办）。

### 3.5 `submitCandidate` 升级（`KnowledgeProductionOrchestrationService` + 控制器）
- signature：`submitCandidate(jobCode, KnowledgeAssetEnvelope candidate, MaterializationTarget target)`。
- 校验隔离（PR1）+ 路由 resolve（PR3）不变；resolve 出 `ReviewRoutingDecision` 传入 `intake(job, candidate, target, routing)`；`intake` 改调物化实现；血缘行 `candidate_ref` 落**真实物化版本引用**（不再 `"staged:"`）。
- 返回 `CandidateSubmissionResponse(candidateRef, routing)` **形状不变**（YAGNI，不扩展 DTO）——`candidateRef` 由 `"staged:"` 变为真实物化版本引用；version/classification/assignment 由现审核台既有端点查询，无需塞进本响应。
- 控制器 `POST /jobs/{jobCode}/candidates` body 由 `KnowledgeAssetEnvelope` 改为 `CandidateSubmissionRequest(@Valid 信封, @Valid target)`。
- `KnowledgeCandidateIntake` 端口签名升级为 `intake(job, candidate, target, routing) → String candidateRef`。

## 4. 数据流（端到端）
```
submitCandidate(job, 信封, target)
  → [PR1] 校验闸 + 双形态隔离 + 资产类型/租户一致
  → [PR4] SourceReferenceResolver: sourceRef → source_document_id/source_version_id（拒收 if 解析不出）
  → [PR4] 身份: target.identityId 校验 | target.newIdentity find-or-create 非权威身份壳
  → [PR4] KnowledgeVersionCreateRequest 构造（payload/FK/versionNo/risk/grade/锚/context）
  → [PR3] reviewRouter.resolve(pipeline, domain, risk) → ReviewRoutingDecision
  → [PR4] classifyCandidate(identityId, request, 路由计划)
        → KnowledgeAssetVersion(PENDING_REPLACEMENT_REVIEW) + CandidateClassification(4 态) + ReviewAssignment×{归口∪领域}
  → [PR2] 血缘行落库（candidate_ref=真实版本引用）+ 计数 RUNNING + 审计
→ 自动进现审核台 KnowledgeGovernance.tsx 可审/发（走既有 reviewCandidate → SYS-08 权威替换）
```

## 5. 诚实降级 + 边界
- 源/身份解析不出 → 结构化拒收，不半物化、不伪造（铁律 #1）；事务回滚不留脏数据。
- DUPLICATE（contentHash 重复）→ 既有去重，不产审核待办（如实返回去重依据）。
- 仍 **不碰 P6**（外部模型/源接入）；仅物化已产出的确定性候选。

## 6. FR/AC 映射（本卡）
- FR-3 统一候选池（物化部分）✅：四生产器产物经统一信封 → 真实物化入既有版本/审核链（本卡覆盖 discovery；MANUAL/外部留后）。
- FR-5 血缘 ✅延伸：血缘行 `candidate_ref` 落真实版本引用，回溯 job→版本。
- FR-6 路由→分派 ✅：PR3 路由决策落成 `ReviewAssignment`（归口+领域），消费 PR3。
- 仍 pending：双签强制、MANUAL/无 FK 源、自动语义分流（AIK-STD-04/10）、外部模型（P6）。

## 7. 验证清单
- TDD 红绿：
  - `SourceReferenceResolverTest`（解析成功 / 源不存在拒收 / 版本不存在拒收 / 租户隔离）。
  - `MaterializingCandidateIntakeTest`（端到端物化 / targetIdentityId 现有 / newIdentity find-or-create / 路由→两行分派 / GENERAL 单行 / 源解析失败拒收 / target 二选一校验）。
  - `KnowledgeVersionServiceTest` 扩展（传路由计划建路由分派 + 传 null 既有单行零回归）。
  - 服务集成（H2 真实落 version+classification+assignment + 强租户隔离）。
  - `KnowledgeProductionOrchestrationServiceTest`/控制器（submitCandidate 加 target + 物化结果 + 权限）。
  - **既有审核台/版本链测试不回归**（`KnowledgeVersionService` createVersion 端点路径）。
- 全量 `mvn test` 不回归 + 四门禁 changed + 五方言 Flyway smoke（若无迁移则验既有基线不破）+ `git diff --check` + 前端 `productCatalog.test.ts`（本卡若不增控制器端点应无漂移）。
- 合并 main 逐 PR 授权（用户手动合）。

## 8. 显式不做（YAGNI / 边界）
- 不做双签**强制**（建两行分派记录，「必须两签才发」留下刀）。
- 不覆盖 MANUAL / 无受控源 FK 候选（自由文本来源 FK 缺口）。
- 不做自动语义分流（候选→主题身份的语义推断＝ AIK-STD-04/10）。
- 不接外部模型生产器（P6 闸）。
- 不动前端审核台（物化产物自动进现页）；不另起资产/候选表。
