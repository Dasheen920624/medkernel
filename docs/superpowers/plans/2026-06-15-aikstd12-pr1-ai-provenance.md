# AIK-STD-12 PR1 实施计划 · AI 生产来源溯源接入审核台读模型

> 设计：[2026-06-15-aikstd12-aireview-ai-provenance-design.md](../specs/2026-06-15-aikstd12-aireview-ai-provenance-design.md) §3 PR1。纯后端，TDD 红绿。分支 `claude/wave2-p2b-aikstd12-aireview-ai-provenance`。

## 目标
审核台候选可反查 **AI 工厂生产来源**（producer/job/管道/模型策略/领域/时点），用于前端 AI 标识 + 来源溯源。旁挂只读查询，**不改既有候选响应**（零前端破坏 / 零现有契约漂移）。

## 步骤（每步红→绿）

1. **反查仓储**（`KnowledgeProductionCandidateRepository`）
   - 加 `findByTenantIdAndCandidateRefIn(String tenantId, Collection<String> refs)`（`@Query` 强租户 + `IN`）。
   - 红：repo 集成测试存两租户血缘行，按 refs 反查仅命中本租户。

2. **provenance 视图 DTO**（`CandidateProvenanceView` record）
   - 字段：`candidateRef` / `aiGenerated`(boolean) / `producer`(KnowledgeProducer) / `jobCode` / `targetPipeline` / `modelStrategy`(nullable) / `domain` / `riskLevel` / `producedAt` / `producedBy`。
   - 静态 `from(candidateRow, job)`：`aiGenerated = job.producer() != KnowledgeProducer.MANUAL`。

3. **只读服务**（`CandidateProvenanceService` @Service，`knowledge.production`）
   - `resolve(Collection<String> refs) -> List<CandidateProvenanceView>`：requireCurrentTenant → repo 反查 → 按 candidate_ref 关联 job（`findByTenantIdAndJobCode` 已有，或批量取 job）→ 映射；无血缘行的 ref 不返回。
   - 红：服务测试——AI job（API_MODEL）候选 aiGenerated=true、MANUAL job 候选 aiGenerated=false、未知 ref 不返回、跨租户 ref 不命中。

4. **只读端点**（`KnowledgeProductionController`）
   - `POST /api/v1/engine/knowledge-production/candidates/provenance`，body `CandidateProvenanceRequest{ List<String> candidateRefs }`（`@Valid` + `@NotEmpty`），`knowledge.read`。
   - 红：控制器安全测试——`knowledge.read` 可访、无权 403、空 body 400。

5. **契约 + 产品目录 + 域归属**
   - 契约 `knowledge-production` 补新端点声明（若契约测试要求）。
   - 重生成 `product-function-catalog`（新增端点）→ 跑前端 `productCatalog.test.ts`。
   - `mk_knowledge_production_candidate` 反查无新表/迁移。

6. **收口验证**（全绿才提交）
   - 全量 `mvn test`（基线 2519，+ 新增 provenance repo/service/controller 测试）。
   - 四门禁 changed（authenticity/migration/config/comment-zh）+ `git diff --check`。
   - 前端 `productCatalog.test.ts` 5/5。
   - 五方言 Flyway smoke（无新迁移，跑全量已含 `FlywayMultiDialectSmokeTest`）。

## 不做（PR1 边界）
- 前端 AI 标识/署名/退修（PR2）。
- 全专业资产模板（PR3）。
- 门禁/评测结果展示（AIK-STD-01 校验结论若未落库则不臆造，按真实落库字段后续补）。

## 恒守
B0（无生产血缘不阻断人工审）· P6 阻断（不开生产）· 铁律 #1（来源真实不臆造）· TDD 红绿 · 合并 main 逐 PR 授权。
