# SYS-03 Projection Degrade Audit PR2 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 交付 SYS-03 PR2：投影关闭时诚实 `NOT_SYNCED` 且关系库主链路可用，Dify 只作为可选执行器不保存业务权威字段，投影同步动作具备真实审计证据，收口 FR-3/4/5 与 AC-2/3/4。

**Architecture:** 关系库继续是唯一业务权威源；`engine.projection` 增加运行策略层读取配置中心运行 Feature Flag，图投影关闭时不清空、不写入派生快照。Dify 通过只携带同步元数据的执行器端口接入，默认无真实适配器时返回 `NOT_SYNCED`，不传输患者或临床事实载荷。投影同步服务用 `AuditRecorder` 记录 `mk_projection_sync` 的真实执行结果和快照摘要。

**Tech Stack:** Java 21, Spring Boot, Spring Data JDBC, JUnit 5, AssertJ, Mockito, Flyway, MedKernel `SystemConfigService` / `RuntimeProperties` / `AuditRecorder`。

---

## Scope

本 PR 只做 SYS-03 大卡工序 PR2：

- FR-3 / AC-3：Dify 仅执行器，执行命令只允许 syncId、tenantId、targetType、sourceCount、sourceHash、traceId 等元数据，不包含临床事实载荷、患者标识密文字段或权威数据。
- FR-4 / AC-2：`graph-projection` 关闭时，重建端点返回 `NOT_SYNCED`，不改写 `mk_projection_snapshot`；标准临床对象主链路继续从关系库读取并返回投影状态 `NOT_SYNCED`。
- FR-5 / AC-4：重建成功、图投影关闭、Dify 未接入等同步动作都写真实审计，不能用 UUID / 时间戳伪造同步证据。

不在本 PR 冒领：

- 真实 Neo4j 写入适配器。
- 真实 Dify HTTP 客户端、密钥管理、网络探活。
- D6 图谱查询页面和 GA 国产化最终实跑证据。

## File Map

- Create: `medkernel-backend/src/main/java/com/medkernel/engine/projection/ProjectionRuntimePolicy.java`
- Create: `medkernel-backend/src/main/java/com/medkernel/engine/projection/ProjectionRuntimeStatusResponse.java`
- Create: `medkernel-backend/src/main/java/com/medkernel/engine/projection/ProjectionExecutionCommand.java`
- Create: `medkernel-backend/src/main/java/com/medkernel/engine/projection/ProjectionExecutionResult.java`
- Create: `medkernel-backend/src/main/java/com/medkernel/engine/projection/ProjectionExecutionPort.java`
- Create: `medkernel-backend/src/main/java/com/medkernel/engine/projection/NoopProjectionExecutionPort.java`
- Create: `medkernel-backend/src/main/java/com/medkernel/engine/projection/ProjectionClinicalStatusPort.java`
- Modify: `medkernel-backend/src/main/java/com/medkernel/engine/projection/ProjectionSyncService.java`
- Modify: `medkernel-backend/src/main/java/com/medkernel/engine/projection/ProjectionController.java`
- Modify: `medkernel-backend/src/main/java/com/medkernel/engine/projection/ProjectionRebuildResponse.java`
- Modify: `medkernel-backend/src/main/java/com/medkernel/engine/projection/ProjectionConsistencyReport.java`
- Modify: `medkernel-backend/src/main/java/com/medkernel/engine/projection/ProjectionSnapshotRepository.java`
- Modify: `medkernel-backend/src/main/java/com/medkernel/engine/clinical/model/NoopClinicalProjectionStatusPort.java`
- Modify: `medkernel-backend/src/test/java/com/medkernel/engine/projection/ProjectionSyncServiceTest.java`
- Create: `medkernel-backend/src/test/java/com/medkernel/engine/projection/ProjectionRuntimeDegradeTest.java`
- Create: `medkernel-backend/src/test/java/com/medkernel/engine/projection/ProjectionDifyExecutorBoundaryTest.java`
- Create: `medkernel-backend/src/test/java/com/medkernel/engine/projection/ProjectionAuditTest.java`
- Modify: `docs/cards/D0/SYS-03.md`
- Modify: `docs/_HANDOFF.md`

## Task 1: Baseline And Red Tests

- [x] **Step 1: Verify related baseline**

Run:

```bash
cd medkernel-backend
mvn -B -q -Dtest=ProjectionSyncServiceTest,ModelGatewayServiceTest,StandardClinicalAuthorityServiceTest test
```

Expected: PASS. Actual baseline passed on 2026-06-01.

- [x] **Step 2: Add projection disabled red test**

Create `ProjectionRuntimeDegradeTest`:

```java
@Test
void disabledGraphProjectionReturnsNotSyncedWithoutMutatingSnapshots() {
    when(policy.graphProjectionEnabled()).thenReturn(false);
    when(policy.difyWorkflowEnabled()).thenReturn(false);
    when(syncs.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

    ProjectionRebuildResponse response = service.rebuildClinicalGraph("tenant-A", "tester", "trace-1");

    assertThat(response.status()).isEqualTo(ProjectionSyncStatus.NOT_SYNCED);
    assertThat(response.message()).contains("graph-projection");
    verify(source, never()).factsForTenant(anyString());
    verify(snapshots, never()).deleteByTenantIdAndTargetType(anyString(), any());
    verify(snapshots, never()).saveAll(anyIterable());
}
```

Expected failure before implementation: `ProjectionSyncService` lacks runtime policy constructor and `ProjectionRebuildResponse.message()`.

- [x] **Step 3: Add status port red test**

In `ProjectionRuntimeDegradeTest` add:

```java
@Test
void clinicalProjectionStatusFallsBackToNotSyncedWhenGraphDisabled() {
    when(policy.graphProjectionEnabled()).thenReturn(false);
    ProjectionClinicalStatusPort port = new ProjectionClinicalStatusPort(policy, snapshots);

    assertThat(port.status("tenant-A")).isEqualTo(ClinicalProjectionStatus.NOT_SYNCED);
}
```

Expected failure: `ProjectionClinicalStatusPort` does not exist.

- [x] **Step 4: Add Dify executor boundary red test**

Create `ProjectionDifyExecutorBoundaryTest`:

```java
@Test
void difyExecutorCommandCarriesOnlySyncMetadataNeverAuthorityFacts() {
    List<String> componentNames = Arrays.stream(ProjectionExecutionCommand.class.getRecordComponents())
        .map(RecordComponent::getName)
        .toList();

    assertThat(componentNames)
        .containsExactly("tenantId", "syncId", "targetType", "sourceCount", "sourceHash", "traceId");
    assertThat(componentNames)
        .doesNotContain("facts", "canonicalPayload", "patientId", "nameCipher", "identityNoCipher", "phoneCipher");
}
```

Expected failure: `ProjectionExecutionCommand` does not exist.

- [x] **Step 5: Add audit red test**

Create `ProjectionAuditTest`:

```java
@Test
void rebuildRecordsRealAuditWithSyncHashesAndStatus() {
    when(policy.graphProjectionEnabled()).thenReturn(true);
    when(policy.difyWorkflowEnabled()).thenReturn(false);
    when(syncs.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
    when(source.factsForTenant("tenant-A")).thenReturn(List.of(ProjectionFact.node(
        "PATIENT", "pat-1", "payload=NODE:PATIENT:pat-1", Instant.parse("2026-06-01T00:00:00Z"))));
    wireSnapshotStore();

    ProjectionRebuildResponse response = service.rebuildClinicalGraph("tenant-A", "tester", "trace-1");

    assertThat(response.status()).isEqualTo(ProjectionSyncStatus.SUCCESS);
    verify(auditRecorder).record(argThat(command ->
        command.action() == AuditAction.EXECUTE
            && command.targetType().equals("mk_projection_sync")
            && command.targetId().equals(response.syncId())
            && String.valueOf(command.after()).contains(response.sourceHash())
            && String.valueOf(command.after()).contains("SUCCESS")));
}
```

Expected failure: `ProjectionSyncService` does not inject or call `AuditRecorder`.

Evidence: `mvn -B -q -Dtest=ProjectionRuntimeDegradeTest,ProjectionDifyExecutorBoundaryTest,ProjectionAuditTest test` first failed at test compilation for missing PR2 production types and response fields, then passed after implementation.

## Task 2: Runtime Policy And Honest Degrade

- [x] **Step 1: Implement `ProjectionRuntimePolicy`**

Create:

```java
@Component
public class ProjectionRuntimePolicy {
    static final String GRAPH_PROJECTION = "graph-projection";
    static final String DIFY_WORKFLOW = "dify-workflow";

    private final RuntimeProperties properties;
    private final SystemConfigService configService;

    public boolean graphProjectionEnabled() {
        return configService.runtimeFeatureFlagEnabled(properties, GRAPH_PROJECTION);
    }

    public boolean difyWorkflowEnabled() {
        return configService.runtimeFeatureFlagEnabled(properties, DIFY_WORKFLOW);
    }
}
```

- [x] **Step 2: Add status DTO and endpoint**

Add `ProjectionRuntimeStatusResponse` and `GET /api/v1/projections/clinical-graph/status`.

Expected fields:

```java
ProjectionTargetType targetType;
String tenantId;
boolean graphProjectionEnabled;
boolean difyWorkflowEnabled;
ClinicalProjectionStatus clinicalProjectionStatus;
ProjectionSyncStatus difyExecutionStatus;
long snapshotCount;
String message;
```

- [x] **Step 3: Add repository count**

Add to `ProjectionSnapshotRepository`:

```java
long countByTenantIdAndTargetType(String tenantId, ProjectionTargetType targetType);
```

- [x] **Step 4: Implement clinical projection status port**

Create `ProjectionClinicalStatusPort`:

```java
@Component
public class ProjectionClinicalStatusPort implements ClinicalProjectionStatusPort {
    public ClinicalProjectionStatus status(String tenantId) {
        if (!policy.graphProjectionEnabled()) return ClinicalProjectionStatus.NOT_SYNCED;
        return snapshots.countByTenantIdAndTargetType(tenantId, ProjectionTargetType.CLINICAL_GRAPH) > 0
            ? ClinicalProjectionStatus.UP
            : ClinicalProjectionStatus.NOT_SYNCED;
    }
}
```

Modify `NoopClinicalProjectionStatusPort` with `@ConditionalOnMissingBean(ClinicalProjectionStatusPort.class)`.

## Task 3: Dify Executor Boundary

- [x] **Step 1: Implement execution command/result records**

Create:

```java
public record ProjectionExecutionCommand(
    String tenantId,
    String syncId,
    ProjectionTargetType targetType,
    int sourceCount,
    String sourceHash,
    String traceId
) {}
```

Create:

```java
public record ProjectionExecutionResult(
    ProjectionSyncStatus status,
    String message
) {
    public static ProjectionExecutionResult notSynced(String message) {
        return new ProjectionExecutionResult(ProjectionSyncStatus.NOT_SYNCED, message);
    }
}
```

- [x] **Step 2: Implement execution port**

Create:

```java
public interface ProjectionExecutionPort {
    ProjectionExecutionResult execute(ProjectionExecutionCommand command);
}
```

Create default implementation:

```java
@Component
public class NoopProjectionExecutionPort implements ProjectionExecutionPort {
    public ProjectionExecutionResult execute(ProjectionExecutionCommand command) {
        return ProjectionExecutionResult.notSynced("NOT_SYNCED：未配置真实 Dify 执行器，未执行外部工作流");
    }
}
```

## Task 4: Rebuild Service Audit And Responses

- [x] **Step 1: Extend response/report records**

Add to `ProjectionRebuildResponse`:

```java
ProjectionSyncStatus difyExecutionStatus;
String message;
```

Add to `ProjectionConsistencyReport`:

```java
ProjectionSyncStatus status;
String message;
```

- [x] **Step 2: Update `ProjectionSyncService` constructor**

Inject:

```java
ProjectionRuntimePolicy policy;
ProjectionExecutionPort executor;
AuditRecorder auditRecorder;
```

Update existing tests to construct service with mocks.

- [x] **Step 3: Implement disabled graph behavior**

At the start of `rebuildClinicalGraph`:

```java
if (!policy.graphProjectionEnabled()) {
    ProjectionSync finished = syncs.save(running.finish(
        ProjectionSyncStatus.NOT_SYNCED, 0, 0, null, null,
        "graph-projection Feature Flag 关闭，未执行图投影重建", Instant.now()));
    recordSyncAudit(finished, ProjectionSyncStatus.NOT_SYNCED, "graph-projection Feature Flag 关闭");
    return responseFrom(finished, ProjectionSyncStatus.NOT_SYNCED, "graph-projection Feature Flag 关闭，关系库权威主链路保持可用");
}
```

Do not call `source`, `snapshots.delete...`, or `snapshots.saveAll(...)` in this branch.

- [x] **Step 4: Implement Dify optional executor call**

After local projection hashes match:

```java
ProjectionExecutionResult difyResult = policy.difyWorkflowEnabled()
    ? executor.execute(new ProjectionExecutionCommand(tenantId, finished.syncId(), ProjectionTargetType.CLINICAL_GRAPH,
        sourceFacts.size(), sourceHash, traceId))
    : ProjectionExecutionResult.notSynced("dify-workflow Feature Flag 关闭，未执行外部工作流");
```

Keep local rebuild `SUCCESS` when graph projection succeeded; expose Dify result separately as `difyExecutionStatus`.

- [x] **Step 5: Record audit for all terminal rebuild paths**

Use `AuditRecorder.record(new AuditRecordCommand(...))`.

Target:

```java
AuditAction.EXECUTE
targetType = "mk_projection_sync"
targetId = sync.syncId()
summary = "投影同步 " + sync.status()
after = Map.of(
  "tenantId", sync.tenantId(),
  "targetType", sync.targetType(),
  "status", sync.status(),
  "sourceCount", sync.sourceCount(),
  "projectionCount", sync.projectionCount(),
  "sourceHash", sync.sourceHash(),
  "projectionHash", sync.projectionHash(),
  "difyExecutionStatus", difyStatus,
  "traceId", sync.traceId()
)
```

## Task 5: Documentation And Verification

- [x] **Step 1: Run focused red/green tests**

Run:

```bash
cd medkernel-backend
mvn -B -q -Dtest=ProjectionRuntimeDegradeTest,ProjectionDifyExecutorBoundaryTest,ProjectionAuditTest,ProjectionSyncServiceTest,StandardClinicalAuthorityServiceTest,ServiceContractGovernanceTest,OpenApiContractConfigurationTest test
```

Expected: PASS.

Evidence: `mvn -B -q -Dtest=ProjectionRuntimeDegradeTest,ProjectionDifyExecutorBoundaryTest,ProjectionAuditTest,ProjectionSyncServiceTest,ClinicalGraphProjectionSourceTest,StandardClinicalAuthorityServiceTest,ServiceContractGovernanceTest,OpenApiContractConfigurationTest test` passed.

- [x] **Step 2: Update SYS-03 card**

Mark:

- FR-3, FR-4, FR-5 checked.
- AC-2, AC-3, AC-4 checked.
- Add PR2 evidence without changing PR1 evidence.

- [x] **Step 3: Update handoff**

Move SYS-03 PR1 to archived line with #224 / merge `409eda5`. Active line becomes SYS-03 PR2, branch `codex/sys-03-degrade-audit-pr2`, with current status and remaining PR/CI/merge steps.

- [x] **Step 4: Run backend full verification**

Run:

```bash
cd medkernel-backend
mvn -B -q test
```

Expected: PASS, including PostgreSQL + Oracle Testcontainers when Docker is available. 达梦 / 人大金仓 real runtime remains `DEFER-001`.

Evidence: `mvn -B -q test` passed on 2026-06-01 with 148 Surefire XML reports and no `failures>0` / `errors>0` matches; PostgreSQL 15 and Oracle 21 Testcontainers migrated to V40.

- [x] **Step 5: Run T-GATE**

Run after commit as official changed check, and before commit with explicit worktree scan if new files are uncommitted:

```bash
node scripts/authenticity-guard.mjs --mode=changed --base=origin/main
node scripts/config-boundary-guard.mjs --mode=changed --base=origin/main
node scripts/migration-convention-guard.mjs --mode=changed --base=origin/main
scripts/check-comment-zh.sh
git diff --check origin/main...HEAD
```

Expected: all PASS.

Evidence: pre-commit worktree scan covered 20 changed/untracked files with authenticity/config blocking violations 0; `node scripts/migration-convention-guard.mjs --mode=changed --base=origin/main` scanned 0 migration files and passed; `scripts/check-comment-zh.sh --mode=full` showed `engine/projection` 21/21 class-level Chinese Javadoc and exited 0; `git diff --cached --check` exited 0.

- [ ] **Step 6: Commit, push, PR**

Commit message:

```bash
git commit -m "完成 SYS-03 投影降级与审计 PR2"
```

Then push, create PR, wait for CI, squash merge, pull main, clean worktree, and only then领取下一张卡。

## Self-Review

- Spec coverage: FR-3/AC-3 covered by Dify command boundary and no-op executor; FR-4/AC-2 covered by runtime policy, status endpoint, clinical status port and disabled rebuild test; FR-5/AC-4 covered by AuditRecorder assertions.
- Placeholder scan: no TODO/TBD/open-ended implementation steps.
- Type consistency: all new DTO/port names are under `com.medkernel.engine.projection`; existing `ProjectionSyncStatus.NOT_SYNCED` is reused for disabled / not connected states.
