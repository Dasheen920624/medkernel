# SYS-03 Projection Sync PR1 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 交付 SYS-03 PR1：关系库标准临床对象可重放生成图投影事实，投影快照可清空重建，一致性校验可检测缺失、额外和内容漂移，收口 AC-1 / AC-5。

**Architecture:** 关系库仍是唯一权威源；新增 `engine.projection` 作为派生投影层，只保存可重建的投影事实和同步任务状态。当前 PR 不接真实 Neo4j/Dify，不伪造连接成功；后续 PR2 再接投影关闭、Dify 执行器解耦和同步审计。

**Tech Stack:** Java 21, Spring Boot, Spring Data JDBC, JUnit 5, AssertJ, Mockito, Flyway, PostgreSQL/Oracle/H2 迁移 smoke。

---

## Scope

本 PR 只做 SYS-03 大卡工序 PR1：

- FR-1：关系库唯一权威，投影事实从 `mk_clinical_*` 权威表生成。
- FR-2：图投影可重建，清空投影快照后可由关系库重放。
- FR-6：一致性校验，能报告 missing / extra / changed。
- AC-1 / AC-5：通过服务测试和迁移测试证明。

不在本 PR 冒领：

- FR-3 / AC-3 Dify 仅执行器。
- FR-4 / AC-2 投影关闭与诚实降级端到端。
- FR-5 / AC-4 统一审计留痕。

## File Map

- Create: `medkernel-backend/src/main/java/com/medkernel/engine/projection/ProjectionTargetType.java`
- Create: `medkernel-backend/src/main/java/com/medkernel/engine/projection/ProjectionFactKind.java`
- Create: `medkernel-backend/src/main/java/com/medkernel/engine/projection/ProjectionSyncStatus.java`
- Create: `medkernel-backend/src/main/java/com/medkernel/engine/projection/ProjectionFact.java`
- Create: `medkernel-backend/src/main/java/com/medkernel/engine/projection/ProjectionDiffItem.java`
- Create: `medkernel-backend/src/main/java/com/medkernel/engine/projection/ProjectionConsistencyReport.java`
- Create: `medkernel-backend/src/main/java/com/medkernel/engine/projection/ProjectionRebuildResponse.java`
- Create: `medkernel-backend/src/main/java/com/medkernel/engine/projection/ClinicalGraphProjectionSource.java`
- Create: `medkernel-backend/src/main/java/com/medkernel/engine/projection/ProjectionSnapshot.java`
- Create: `medkernel-backend/src/main/java/com/medkernel/engine/projection/ProjectionSnapshotRepository.java`
- Create: `medkernel-backend/src/main/java/com/medkernel/engine/projection/ProjectionSync.java`
- Create: `medkernel-backend/src/main/java/com/medkernel/engine/projection/ProjectionSyncRepository.java`
- Create: `medkernel-backend/src/main/java/com/medkernel/engine/projection/ProjectionSyncService.java`
- Create: `medkernel-backend/src/main/java/com/medkernel/engine/projection/ProjectionController.java`
- Modify: `medkernel-backend/src/main/java/com/medkernel/engine/clinical/model/*Repository.java`
- Modify: `medkernel-backend/src/main/java/com/medkernel/engine/security/PermissionCode.java`
- Modify: `medkernel-backend/src/main/java/com/medkernel/engine/security/DefaultPermissionPolicy.java`
- Modify: `medkernel-backend/src/main/java/com/medkernel/engine/contract/ServiceContractCatalog.java`
- Create: `medkernel-backend/src/main/resources/db/migration/{h2,postgres,oracle,dm,kingbase}/V40__projection_sync_baseline.sql`
- Create: `medkernel-backend/src/test/java/com/medkernel/engine/projection/ClinicalGraphProjectionSourceTest.java`
- Create: `medkernel-backend/src/test/java/com/medkernel/engine/projection/ProjectionSyncServiceTest.java`
- Modify: `docs/cards/D0/SYS-03.md`
- Modify: `docs/_HANDOFF.md`

## Task 1: Baseline And Red Tests

- [x] **Step 1: Verify related baseline**

Run:

```bash
cd medkernel-backend
mvn -B -q -Dtest=StandardClinicalAuthorityServiceTest,PackageEngineServiceTest,LenientPackageSyncAdapterTest test
```

Expected: PASS. Existing package sync tests may log intentional `NOT_SYNCED` / failure messages while still passing.

- [x] **Step 2: Add source red test**

Create `ClinicalGraphProjectionSourceTest` with two behaviors:

```java
@Test
void createsGraphFactsFromRelationalClinicalAuthorityWithoutSensitiveFields() {
    seedRelationalClinicalObjects();

    List<ProjectionFact> facts = source.factsForTenant("tenant-A");

    assertThat(facts).extracting(ProjectionFact::factKey)
        .contains("NODE:PATIENT:pat-1", "NODE:OBSERVATION:obs-1", "EDGE:PATIENT:pat-1:HAS_RESOURCE:OBSERVATION:obs-1");
    assertThat(facts).extracting(ProjectionFact::canonicalPayload)
        .noneMatch(payload -> payload.contains("cipher-name"))
        .noneMatch(payload -> payload.contains("cipher-id"))
        .noneMatch(payload -> payload.contains("cipher-phone"));
}
```

Expected compile failure: `ClinicalGraphProjectionSource` / `ProjectionFact` 不存在。

- [x] **Step 3: Add rebuild/diff red test**

Create `ProjectionSyncServiceTest` with two behaviors:

```java
@Test
void rebuildClearsProjectionAndStoresRelationalFactsWithMatchingHashes() {
    InMemorySnapshotRepository snapshots = new InMemorySnapshotRepository();
    snapshots.save(staleSnapshot("tenant-A"));
    ProjectionSyncService service = serviceWith(sourceFacts("tenant-A", "NODE:PATIENT:pat-1", "NODE:OBSERVATION:obs-1"), snapshots);

    ProjectionRebuildResponse response = service.rebuildClinicalGraph("tenant-A", "tester", "trace-1");

    assertThat(response.status()).isEqualTo(ProjectionSyncStatus.SUCCESS);
    assertThat(response.sourceCount()).isEqualTo(2);
    assertThat(response.projectionCount()).isEqualTo(2);
    assertThat(response.sourceHash()).isEqualTo(response.projectionHash());
    assertThat(snapshots.findByTenantIdAndTargetType("tenant-A", ProjectionTargetType.CLINICAL_GRAPH))
        .extracting(ProjectionSnapshot::factKey)
        .containsExactly("NODE:PATIENT:pat-1", "NODE:OBSERVATION:obs-1");
}

@Test
void consistencyReportDetectsMissingExtraAndChangedProjectionFacts() {
    ProjectionSyncService service = serviceWith(
        sourceFacts("tenant-A", "NODE:PATIENT:pat-1", "NODE:OBSERVATION:obs-1"),
        snapshotFacts("tenant-A", changed("NODE:PATIENT:pat-1"), extra("NODE:CLAIM:claim-9")));

    ProjectionConsistencyReport report = service.checkClinicalGraphConsistency("tenant-A");

    assertThat(report.consistent()).isFalse();
    assertThat(report.missing()).extracting(ProjectionDiffItem::factKey).containsExactly("NODE:OBSERVATION:obs-1");
    assertThat(report.extra()).extracting(ProjectionDiffItem::factKey).containsExactly("NODE:CLAIM:claim-9");
    assertThat(report.changed()).extracting(ProjectionDiffItem::factKey).containsExactly("NODE:PATIENT:pat-1");
}
```

Expected compile failure: projection service/domain types 不存在。

## Task 2: Implement Projection Domain And Source

- [x] **Step 1: Implement immutable projection facts**

Create `ProjectionFact` as a record with:

- `ProjectionTargetType targetType`
- `ProjectionFactKind kind`
- `String objectType`
- `String objectId`
- `String subjectKey`
- `String predicate`
- `String objectKey`
- `String canonicalPayload`
- `String contentHash`
- `Instant sourceUpdatedAt`

The compact constructor must compute `contentHash` with SHA-256 from `canonicalPayload`, never with UUID or timestamp.

- [x] **Step 2: Implement relational source**

`ClinicalGraphProjectionSource.factsForTenant(String tenantId)` must:

- Load all 12 standard clinical object lists by tenant from existing repositories.
- Emit one `NODE:<TYPE>:<id>` fact per object.
- Emit `EDGE:PATIENT:<patientId>:HAS_RESOURCE:<TYPE>:<id>` for patient-linked objects.
- Emit `EDGE:ENCOUNTER:<encounterId>:HAS_RESOURCE:<TYPE>:<id>` when `encounterId` is present.
- Canonical payload must contain only non-sensitive structural fields: tenant, type, id, patientId, encounterId, fhirResourceId, code/system/status where applicable, updatedAt.
- Exclude patient `nameCipher`, `identityNoCipher`, `phoneCipher` and any ciphertext field.

- [x] **Step 3: Add repository tenant queries**

Add `findByTenantId(String tenantId)` to each `Clinical*Repository` used by the source. Keep existing patient-scoped methods.

- [x] **Step 4: Run source test**

Run:

```bash
cd medkernel-backend
mvn -B -q -Dtest=ClinicalGraphProjectionSourceTest test
```

Expected: PASS.

## Task 3: Implement Snapshot Store, Rebuild, And Diff

- [x] **Step 1: Implement persistence records**

Create `ProjectionSnapshot` mapped to `mk_projection_snapshot` and `ProjectionSync` mapped to `mk_projection_sync`.

`ProjectionSnapshot` fields:

- `Long id`
- `String tenantId`
- `ProjectionTargetType targetType`
- `String factKey`
- `ProjectionFactKind factKind`
- `String objectType`
- `String objectId`
- `String subjectKey`
- `String predicate`
- `String objectKey`
- `String contentHash`
- `String canonicalPayload`
- `Instant sourceUpdatedAt`
- `Instant syncedAt`
- `String traceId`

`ProjectionSync` fields:

- `Long id`
- `String syncId`
- `String tenantId`
- `ProjectionTargetType targetType`
- `ProjectionSyncStatus status`
- `Integer sourceCount`
- `Integer projectionCount`
- `String sourceHash`
- `String projectionHash`
- `String message`
- `Instant startedAt`
- `Instant finishedAt`
- `String requestedBy`
- `String traceId`

- [x] **Step 2: Implement repositories**

`ProjectionSnapshotRepository`:

- `List<ProjectionSnapshot> findByTenantIdAndTargetType(String tenantId, ProjectionTargetType targetType)`
- `@Modifying @Query DELETE FROM mk_projection_snapshot WHERE tenant_id = :tenantId AND target_type = :targetType`
- `ListCrudRepository<ProjectionSnapshot, Long>`

`ProjectionSyncRepository`:

- `Optional<ProjectionSync> findByTenantIdAndSyncId(String tenantId, String syncId)`
- `List<ProjectionSync> findByTenantIdAndTargetTypeOrderByStartedAtDesc(String tenantId, ProjectionTargetType targetType)`

- [x] **Step 3: Implement service**

`ProjectionSyncService.rebuildClinicalGraph(tenantId, requestedBy, traceId)` must:

- Save RUNNING sync row.
- Read source facts from `ClinicalGraphProjectionSource`.
- Clear existing `CLINICAL_GRAPH` snapshots for tenant.
- Save source facts as snapshots.
- Reload projection snapshots.
- Compute aggregate hashes by sorting `factKey=contentHash` pairs.
- Save SUCCESS sync row with counts and hashes.
- On exception, save FAILED sync row and rethrow an `ApiException`.

`ProjectionSyncService.checkClinicalGraphConsistency(tenantId)` must:

- Compare source fact map and projection snapshot map by `factKey`.
- Return missing, extra, changed lists and source/projection counts/hashes.

- [x] **Step 4: Run service test**

Run:

```bash
cd medkernel-backend
mvn -B -q -Dtest=ProjectionSyncServiceTest test
```

Expected: PASS.

## Task 4: Add API Contract And Permissions

- [x] **Step 1: Add controller**

Create `/api/v1/projections/clinical-graph/rebuild` and `/api/v1/projections/clinical-graph/consistency`.

- POST rebuild permission: `projection.rebuild`
- GET consistency permission: `projection.read`
- Controller must use `@DataScope(requireTenant = true)` and current `RequestContext` for tenant/user/trace.

- [x] **Step 2: Register permissions**

Add `PROJECTION_READ` and `PROJECTION_REBUILD` to `PermissionCode` and grant to platform admin / hospital admin / it-ops / audit-compliance / architect-equivalent roles that already receive integration or package governance permissions.

- [x] **Step 3: Register service contract**

Add `ProjectionController` to `ServiceContractCatalog` with permissions and audit points. Ensure SYS-02 governance test covers it.

- [x] **Step 4: Run contract tests**

Run:

```bash
cd medkernel-backend
mvn -B -q -Dtest=ServiceContractGovernanceTest,OpenApiContractConfigurationTest test
```

Expected: PASS.

## Task 5: Add Migrations

- [x] **Step 1: Create V40 migration for all dialect directories**

Create:

- `mk_projection_sync`
- `mk_projection_snapshot`

Required indexes:

- `idx_mk_projection_sync_tenant_target_ts`
- `idx_mk_projection_sync_tenant_status`
- `idx_mk_projection_snapshot_tenant_target`
- `uk_mk_projection_snapshot_fact`

Production dialect comments must be Chinese. Even though current runtime scope only guarantees PostgreSQL + Oracle, keep all five migration files structurally aligned to satisfy repository convention;真实国产化环境仍按 `DEFER-001` 后续验收。

- [x] **Step 2: Run migration guard**

Run:

```bash
node scripts/migration-convention-guard.mjs --mode=changed --base=origin/main
```

Expected: PASS.

## Task 6: Documentation, Full Verification, Commit

- [x] **Step 1: Update SYS-03 card**

Mark PR1 evidence only:

- FR-1, FR-2, FR-6 checked.
- AC-1, AC-5 checked.
- Leave FR-3, FR-4, FR-5 and AC-2/3/4 unchecked for PR2.
- Note table names use `mk_projection_sync` / `mk_projection_snapshot` to comply with current migration convention while implementing the projection sync table family.

- [x] **Step 2: Update handoff**

Create/replace active line:

- `SYS-03 投影同步 PR1`
- Branch `codex/sys-03-projection-sync`
- Status with local tests and remaining PR/CI/merge steps.
- Mention external Neo4j/Dify true adapter work is PR2/D6/GA if no real environment exists; do not block PR1.

- [x] **Step 3: Run focused verification**

Run:

```bash
cd medkernel-backend
mvn -B -q -Dtest=ClinicalGraphProjectionSourceTest,ProjectionSyncServiceTest,ServiceContractGovernanceTest,OpenApiContractConfigurationTest test
```

Expected: PASS.

- [x] **Step 4: Run backend full verification**

Run:

```bash
cd medkernel-backend
mvn -B -q test
```

Expected: PASS, including PostgreSQL + Oracle Testcontainers when Docker is available. 达梦 / 人大金仓 real runtime evidence remains `DEFER-001`.

Evidence: `mvn -B -q test` exited 0 after 744 backend tests; Docker Testcontainers ran PostgreSQL 15 and Oracle 21 migrations through V40.

- [x] **Step 5: Run T-GATE**

Run:

```bash
node scripts/authenticity-guard.mjs --mode=changed --base=origin/main
node scripts/config-boundary-guard.mjs --mode=changed --base=origin/main
node scripts/migration-convention-guard.mjs --mode=changed --base=origin/main
scripts/check-comment-zh.sh
git diff --check origin/main...HEAD
```

Expected: all PASS.

Evidence: official changed T-GATE passed after commit: authenticity guard scanned 30 files, config-boundary guard scanned 30 files, migration guard scanned 5 V40 files, Chinese comment gate reported 0 fail / 0 warn, and `git diff --check origin/main...HEAD` exited 0.

- [ ] **Step 6: Commit, push, PR**

Commit message:

```bash
git commit -m "完成 SYS-03 投影同步 PR1"
```

Then push, create PR, wait for CI, squash merge, pull main, clean worktree, and only then领取 SYS-03 PR2.
