# D2 API-03 Standard Knowledge Asset API Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 收口 D2 `API-03` 标准知识资产 API，让来源、资产版本、引用、审核契约、历史重放、分页和异步导出可被真实调用与验证。

**Architecture:** 保留现有 `engine/knowledge` 表族和服务作为单一事实源，不为 API-03 抢做 KNOW-02/SYS-08 的候选表、紧急失效表或完整替换事务。新增少量 DTO/响应对象和服务方法，把现有来源、版本、lineage、citation、export 能力组合成统一 REST 合同；候选审核在 KNOW-02 未实施前返回真实空态/未找到，不伪造候选。

**Tech Stack:** Spring Boot 3、Spring Data JDBC、Bean Validation、MockMvc、JUnit 5、Mockito、H2/PostgreSQL/Oracle Flyway smoke、MedKernel `ApiResult`/`ProblemDetail`/`DataScope`/`RequestContext`。

---

## Scope Guard

- 本卡只实现 `API-03` API 合同与可验证主链路。
- 不新建 `candidate_classification` / `review_assignment` / `knowledge_invalidation` / `affected_case_task`，这些归 `KNOW-02` / `SYS-08`。
- 写入类端点必须带标准上下文字段；GET 查询继续由 JWT/`RequestContext` + `DataScope` 保证租户与组织范围。
- 非当前阶段阻塞登记到 `docs/audit/deferred-issues.md` 后继续，不把长期目标标记 blocked。

## File Map

- Modify: `medkernel-backend/src/main/java/com/medkernel/engine/knowledge/KnowledgeIdentityController.java`
  增加 `POST /identities`、`GET /identities/{id}/citations`、来源嵌套路由 `/sources/{id}/versions`。
- Modify: `medkernel-backend/src/main/java/com/medkernel/engine/knowledge/KnowledgeVersionController.java`
  增加 `POST /identities/{id}/versions`、`POST /.../submit`、`GET /.../replay`、候选/审核/diff 路由。
- Modify: `medkernel-backend/src/main/java/com/medkernel/engine/knowledge/KnowledgeExportController.java`
  导出提交请求对齐标准上下文。
- Modify: `medkernel-backend/src/main/java/com/medkernel/engine/knowledge/KnowledgeIdentityService.java`
  增加身份创建、citation 查询、来源版本嵌套路由校验、标准上下文校验。
- Modify: `medkernel-backend/src/main/java/com/medkernel/engine/knowledge/KnowledgeVersionService.java`
  增加身份下创建版本、submit、replay、候选空态、审核未找到、diff 未找到，以及无来源引用禁止激活门禁。
- Use existing: `medkernel-backend/src/main/java/com/medkernel/engine/knowledge/CitationRepository.java`
  复用当前版本 citation 查询，不为 API-03 新造引用数据。
- Modify: `medkernel-backend/src/main/java/com/medkernel/shared/api/error/ErrorCode.java`
  新增 `KNOWLEDGE_CITATION_REQUIRED / ENG-KNOW-003`。
- Create: `medkernel-backend/src/main/java/com/medkernel/engine/knowledge/KnowledgeApiContext.java`
  API-03 写入类请求共享标准上下文字段。
- Create: `medkernel-backend/src/main/java/com/medkernel/engine/knowledge/KnowledgeIdentityCreateRequest.java`
  身份创建请求。
- Create: `medkernel-backend/src/main/java/com/medkernel/engine/knowledge/KnowledgeSourceCreateRequest.java`
  来源登记客户面请求。
- Create: `medkernel-backend/src/main/java/com/medkernel/engine/knowledge/KnowledgeSourceVersionCreateRequest.java`
  来源版本登记客户面请求。
- Create: `medkernel-backend/src/main/java/com/medkernel/engine/knowledge/KnowledgeVersionCreateRequest.java`
  身份下创建版本客户面请求。
- Create: `medkernel-backend/src/main/java/com/medkernel/engine/knowledge/KnowledgeActionRequest.java`
  提交审核等动作请求。
- Create: `medkernel-backend/src/main/java/com/medkernel/engine/knowledge/KnowledgeReplayResponse.java`
  历史重放响应，明确 `historicalVersion=true` 与绑定包/快照。
- Create: `medkernel-backend/src/main/java/com/medkernel/engine/knowledge/KnowledgeCandidateResponse.java`
  KNOW-02 未接入前的候选空态响应。
- Create: `medkernel-backend/src/main/java/com/medkernel/engine/knowledge/KnowledgeCandidateReviewDecision.java`
  候选审核决策枚举。
- Create: `medkernel-backend/src/main/java/com/medkernel/engine/knowledge/KnowledgeCandidateReviewRequest.java`
  候选审核请求，标准上下文字段 + 审核结论。
- Create: `medkernel-backend/src/test/java/com/medkernel/engine/knowledge/KnowledgeAssetApiContractTest.java`
  Controller/API 合同测试。
- Modify: `medkernel-backend/src/test/java/com/medkernel/engine/knowledge/KnowledgeIdentityServiceTest.java`
- Modify: `medkernel-backend/src/test/java/com/medkernel/engine/knowledge/KnowledgeVersionServiceTest.java`
- Modify: `medkernel-backend/src/test/java/com/medkernel/engine/knowledge/KnowledgeExportServiceTest.java`
- Modify: `docs/cards/D2/API-03.md`
- Modify: `docs/backlog.md`
- Modify: `docs/_HANDOFF.md`

---

### Task 1: Baseline And API Contract Red Tests

**Files:**
- Test: `medkernel-backend/src/test/java/com/medkernel/engine/knowledge/KnowledgeAssetApiContractTest.java`

- [x] **Step 1: Run existing knowledge baseline**

Run:

```bash
mvn -q -Dtest=KnowledgeIdentityServiceTest,KnowledgeVersionServiceTest,KnowledgeExportServiceTest,KnowledgeIdentityControllerSecurityTest,KnowledgeEngineTest,KnowledgeIdentityRepositoryTest test
```

Expected: 当前知识域基线通过。若失败，先按失败原因修当前基线，不进入新功能实现。

- [x] **Step 2: Write failing API contract tests**

Add tests for:

```java
// 1. POST /api/v1/engine/knowledge/identities rejects missing standard context.
// 2. POST /api/v1/engine/knowledge/identities accepts snake_case standard context and creates identity.
// 3. POST /api/v1/engine/knowledge/identities/{id}/versions creates a version under the path identity.
// 4. POST /api/v1/engine/knowledge/identities/{id}/versions/{vid}/submit transitions to UNDER_REVIEW.
// 5. GET /api/v1/engine/knowledge/identities/{id}/citations returns current-version citations.
// 6. GET /api/v1/engine/knowledge/identities/{id}/versions/{vid}/replay returns historicalVersion=true.
// 7. GET /api/v1/engine/knowledge/identities/{id}/candidates returns empty B0 response while KNOW-02 storage is absent.
```

- [x] **Step 3: Verify red**

Run:

```bash
mvn -q -Dtest=KnowledgeAssetApiContractTest test
```

Expected: FAIL because routes/DTOs are missing.

---

### Task 2: Standard Context And Identity/Source API

**Files:**
- Create: `medkernel-backend/src/main/java/com/medkernel/engine/knowledge/KnowledgeApiContext.java`
- Create: `medkernel-backend/src/main/java/com/medkernel/engine/knowledge/KnowledgeIdentityCreateRequest.java`
- Modify: `KnowledgeIdentityController.java`
- Modify: `KnowledgeIdentityService.java`
- Test: `KnowledgeIdentityServiceTest.java`

- [x] **Step 1: Write failing service tests**

Add tests showing:

```java
// createIdentity rejects request tenant_id that differs from RequestContext tenant.
// createIdentity trims identityCode and defaults status to ACTIVE.
// registerSourceVersionAtSource rejects sourceDocumentId mismatch in nested route.
```

- [x] **Step 2: Verify red**

Run:

```bash
mvn -q -Dtest=KnowledgeIdentityServiceTest test
```

Expected: FAIL because service methods and DTOs are missing.

- [x] **Step 3: Implement minimal code**

Implement:

```java
public record KnowledgeApiContext(
    @JsonAlias("request_id") @NotBlank String requestId,
    @JsonAlias("trace_id") @NotBlank String traceId,
    @JsonAlias("tenant_id") @NotBlank String tenantId,
    @JsonAlias("group_id") String groupId,
    @JsonAlias("hospital_id") String hospitalId,
    @JsonAlias("campus_id") String campusId,
    @JsonAlias("site_id") String siteId,
    @JsonAlias("department_id") String departmentId,
    @JsonAlias("specialty_id") String specialtyId,
    @JsonAlias("user_id") @NotBlank String userId,
    @JsonAlias("role_codes") List<String> roleCodes,
    @JsonAlias("package_version") @NotBlank String packageVersion
) { ... }
```

Use `validateContext(KnowledgeApiContext context)` in service:

```java
if (!tenantId.equals(context.tenantId())) {
    throw new ApiException(ErrorCode.ORG_SCOPE_DENIED, "请求租户与当前会话租户不一致");
}
```

Add:

```java
POST /api/v1/engine/knowledge/identities
POST /api/v1/engine/knowledge/sources
POST /api/v1/engine/knowledge/sources/{sourceId}/versions
```

旧 HTTP 兼容入口不再保留；底层服务方法可继续供知识引擎内部测试和后续 KNOW-01 复用。

- [x] **Step 4: Verify green**

Run:

```bash
mvn -q -Dtest=KnowledgeIdentityServiceTest,KnowledgeAssetApiContractTest test
```

Expected: service tests pass; contract tests for identity/source pass.

---

### Task 3: Version Submit, Citation Readback, And Replay

**Files:**
- Create: `KnowledgeReplayResponse.java`
- Modify: `KnowledgeVersionController.java`
- Modify: `KnowledgeVersionService.java`
- Modify: `CitationRepository.java`
- Modify: `KnowledgeIdentityService.java`
- Test: `KnowledgeVersionServiceTest.java`
- Test: `KnowledgeAssetApiContractTest.java`

- [x] **Step 1: Write failing service tests**

Add tests showing:

```java
// createDraftVersionForIdentity rejects path identity mismatch.
// submitVersion changes DRAFT/CANDIDATE to UNDER_REVIEW and keeps other fields.
// replayVersion rejects version not belonging to identity.
// replayVersion returns historicalVersion=true and does not require ACTIVE status.
```

- [x] **Step 2: Verify red**

Run:

```bash
mvn -q -Dtest=KnowledgeVersionServiceTest test
```

Expected: FAIL because methods are missing.

- [x] **Step 3: Implement minimal code**

Add routes:

```java
POST /api/v1/engine/knowledge/identities/{identityId}/versions
POST /api/v1/engine/knowledge/identities/{identityId}/versions/{versionId}/submit
GET  /api/v1/engine/knowledge/identities/{identityId}/versions/{versionId}/replay
GET  /api/v1/engine/knowledge/identities/{identityId}/citations
```

Replay response:

```java
public record KnowledgeReplayResponse(
    Long identityId,
    Long versionId,
    String versionNo,
    KnowledgeVersionStatus status,
    boolean historicalVersion,
    String packageVersion,
    String snapshotId,
    String contentHash,
    String anchors,
    Instant effectiveFrom,
    Instant effectiveTo
) {}
```

For citation readback, return current active version citations; if no active version, return empty list instead of fabricating source evidence.

- [x] **Step 4: Verify green**

Run:

```bash
mvn -q -Dtest=KnowledgeVersionServiceTest,KnowledgeIdentityServiceTest,KnowledgeAssetApiContractTest test
```

Expected: version/citation/replay tests pass.

---

### Task 4: Candidate/Review Honest B0 Contract

**Files:**
- Create: `KnowledgeCandidateResponse.java`
- Create: `KnowledgeCandidateReviewRequest.java`
- Modify: `KnowledgeVersionController.java`
- Modify: `KnowledgeVersionService.java`
- Test: `KnowledgeVersionServiceTest.java`
- Test: `KnowledgeAssetApiContractTest.java`
- Modify: `docs/audit/deferred-issues.md` only if a new non-current blocker is discovered.

- [x] **Step 1: Write failing tests**

Add tests showing:

```java
// listCandidates(identityId) returns honest empty response with available=false / reason=KNOW_02_PENDING.
// reviewCandidate(candidateId, request) returns NOT_FOUND while KNOW-02 storage is absent.
// diffCandidate(candidateId) returns NOT_FOUND while KNOW-02 storage is absent.
```

- [x] **Step 2: Verify red**

Run:

```bash
mvn -q -Dtest=KnowledgeVersionServiceTest,KnowledgeAssetApiContractTest test
```

Expected: FAIL because candidate contract is missing.

- [x] **Step 3: Implement honest contract**

Add routes:

```java
GET  /api/v1/engine/knowledge/identities/{identityId}/candidates
POST /api/v1/engine/knowledge/candidates/{candidateId}/review
GET  /api/v1/engine/knowledge/candidates/{candidateId}/diff
```

Do not create candidate records. Return:

```java
new KnowledgeCandidateResponse(identityId, List.of(), false, "KNOW_02_PENDING", "知识候选审核引擎尚未实施，当前无可审核候选")
```

For review/diff, throw `ApiException.notFound("知识候选尚未接入 KNOW-02 candidate_classification")`.

- [x] **Step 4: Verify green**

Run:

```bash
mvn -q -Dtest=KnowledgeVersionServiceTest,KnowledgeAssetApiContractTest test
```

Expected: candidate contract tests pass without fake data.

---

### Task 5: Export Standard Context And Documentation Closeout

**Files:**
- Modify: `KnowledgeExportController.java`
- Modify: `KnowledgeExportServiceTest.java`
- Modify: `docs/cards/D2/API-03.md`
- Modify: `docs/backlog.md`
- Modify: `docs/_HANDOFF.md`

- [x] **Step 1: Write failing export context test**

Add a controller test showing `POST /api/v1/engine/knowledge/exports` without `request_id` or `package_version` returns validation ProblemDetail.

- [x] **Step 2: Verify red**

Run:

```bash
mvn -q -Dtest=KnowledgeAssetApiContractTest,KnowledgeExportServiceTest test
```

Expected: FAIL because export request does not contain `KnowledgeApiContext`.

- [x] **Step 3: Implement minimal export request change**

Change `SubmitExportRequest` to include flat standard context fields:

```java
@JsonAlias("request_id") String requestId
...
@JsonAlias("package_version") String packageVersion
```

and call a shared `KnowledgeApiContext` validator before `exportService.submit`.

- [x] **Step 4: Update docs**

Update:

```text
docs/cards/D2/API-03.md  FR/AC checked with evidence summary
docs/backlog.md          API-03 pending -> done after verification
docs/_HANDOFF.md         archive API-03 after PR/CI/merge; keep next API-04 pointer
```

If verification is not complete yet, do not mark `done`; leave exact next action in `_HANDOFF`.

---

### Task 6: Verification, PR, CI, Merge, Cleanup

**Files:** all changed files.

- [x] **Step 1: Focused backend tests**

Run:

```bash
mvn -q -Dtest=KnowledgeAssetApiContractTest,KnowledgeIdentityServiceTest,KnowledgeVersionServiceTest,KnowledgeExportServiceTest,KnowledgeIdentityControllerSecurityTest,KnowledgeEngineTest,KnowledgeIdentityRepositoryTest test
```

Expected: PASS.

- [x] **Step 2: Migration smoke**

Run:

```bash
mvn -q -Dtest=FlywayMultiDialectSmokeTest test
```

Expected: PASS for H2/PostgreSQL/Oracle. Do not require 达梦/人大金仓 in current stage; keep `DEFER-001` open.

- [x] **Step 3: Backend full test**

Run:

```bash
mvn -q test
```

Expected: PASS with zero failures/errors.

- [x] **Step 4: T-GATE**

Run:

```bash
node --test scripts/authenticity-guard.test.mjs scripts/migration-convention-guard.test.mjs scripts/config-boundary-guard.test.mjs
node scripts/authenticity-guard.mjs --mode=all
scripts/check-comment-zh.sh --mode=full
git diff --check
```

Expected: JS guard tests pass, authenticity scan passes, changed comments pass. Historical comment GAP remains `DEFER-006` unless explicitly closed with evidence.

- [ ] **Step 5: Commit and PR**

Run non-interactively:

```bash
git status --short
git add medkernel-backend/src/main/java/com/medkernel/engine/knowledge medkernel-backend/src/test/java/com/medkernel/engine/knowledge docs/cards/D2/API-03.md docs/backlog.md docs/_HANDOFF.md docs/superpowers/plans/2026-06-02-api-03-knowledge-asset-api.md
git commit -m "D2 API-03 标准知识资产 API 收口"
git push -u origin codex/d2-api-03-knowledge-asset-api
gh pr create --title "D2 API-03 标准知识资产 API 收口" --body-file <generated-pr-body>
```

- [ ] **Step 6: Remote CI and merge**

Watch CI until all required checks pass. After CI passes, squash merge PR, confirm `origin/main` contains merge commit, delete remote branch, remove local worktree/branch, return main worktree to latest `origin/main`, then continue to `API-04`.

---

## Self-Review

- API-03 FR-1: Task 2 and Task 3 cover source, identity, version, citations.
- API-03 FR-2: Task 3 covers submit/activate/withdraw existing route and lineage remains existing.
- API-03 FR-3: Task 4 exposes honest candidate/review/diff contract without fake candidate data.
- API-03 FR-4: Task 3 covers replay with historical marker.
- API-03 FR-5: Task 1/5/6 cover server pagination and async export.
- API-03 FR-6: Task 2/5 enforce standard context on write/export endpoints.
- No production code may be changed before the matching red test is observed.
