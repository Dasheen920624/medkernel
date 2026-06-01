# SYS-05 Runtime Modes PR1 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 交付 SYS-05 PR1：在线、异步、批量三类运行模式的统一任务框架，支持状态轮询与批量部分成功，不包含离线许可、重试、死信、回放。

**Architecture:** 新增 `com.medkernel.shared.runtime.task` 作为共享运行任务底座，`sys_task` 作为关系库权威任务表，payload 只保存 `PayloadStoragePort` 引用与真实摘要。业务执行通过 `RuntimeTaskExecutorPort` 插件化，默认执行器只接受 `RUNTIME_SELF_CHECK` 自检任务，其他业务能力必须显式接入，避免假闭环。

**Tech Stack:** Spring Boot 3、Spring Data JDBC、Record DTO + Bean Validation、`ApiResult`、`RequestContext`、`AuditRecorder`、`StateTransitionRecorder`、Flyway 五方言迁移、JUnit 5 + Mockito + AssertJ。

---

## 执行约束

- 当前 PR 只关闭 SYS-05 的 FR-1、FR-2、FR-3、AC-1、AC-2；FR-4/5/6 与 AC-3/4/5 在 PR2 承接。
- PostgreSQL 与 Oracle 是当前真实运行保障范围；达梦 / 人大金仓运行环境只做迁移文件静态一致性，不伪造真实连接证据。
- 外部环境、闭源驱动、客户现场资源等非当前卡主链路问题写入 `docs/audit/deferred-issues.md` 后继续推进；登录可用、权限隔离、真实性门禁、医疗安全、当前卡主链路缺陷不得延期。
- 先写红测并确认失败，再实现；每次声称完成必须附新跑的命令证据。

## File Map

- Create: `medkernel-backend/src/main/java/com/medkernel/shared/runtime/task/RuntimeTaskMode.java` — 三类 PR1 运行模式枚举。
- Create: `medkernel-backend/src/main/java/com/medkernel/shared/runtime/task/RuntimeTaskStatus.java` — 待办状态机枚举。
- Create: `medkernel-backend/src/main/java/com/medkernel/shared/runtime/task/RuntimeTaskItemStatus.java` — 批量明细状态枚举。
- Create: `medkernel-backend/src/main/java/com/medkernel/shared/runtime/task/RuntimeTaskBatchItem.java` — 批量输入项 DTO。
- Create: `medkernel-backend/src/main/java/com/medkernel/shared/runtime/task/RuntimeTaskFailureItem.java` — 部分成功失败明细 DTO。
- Create: `medkernel-backend/src/main/java/com/medkernel/shared/runtime/task/RuntimeTaskSubmitRequest.java` — 任务提交 Record DTO。
- Create: `medkernel-backend/src/main/java/com/medkernel/shared/runtime/task/RuntimeTaskResponse.java` — 任务状态响应 DTO。
- Create: `medkernel-backend/src/main/java/com/medkernel/shared/runtime/task/RuntimeTaskExecutionCommand.java` — 执行器命令，携带任务元数据与 payload 引用。
- Create: `medkernel-backend/src/main/java/com/medkernel/shared/runtime/task/RuntimeTaskExecutionResult.java` — 执行结果。
- Create: `medkernel-backend/src/main/java/com/medkernel/shared/runtime/task/RuntimeTaskExecutorPort.java` — 执行器端口。
- Create: `medkernel-backend/src/main/java/com/medkernel/shared/runtime/task/DefaultRuntimeTaskExecutor.java` — 默认自检执行器。
- Create: `medkernel-backend/src/main/java/com/medkernel/shared/runtime/task/RuntimeTaskRecord.java` — `sys_task` 实体。
- Create: `medkernel-backend/src/main/java/com/medkernel/shared/runtime/task/RuntimeTaskRepository.java` — Spring Data JDBC 仓储。
- Create: `medkernel-backend/src/main/java/com/medkernel/shared/runtime/task/RuntimeTaskService.java` — 在线/异步/批量编排服务。
- Create: `medkernel-backend/src/main/java/com/medkernel/shared/runtime/task/RuntimeTaskController.java` — `/api/v1/system/tasks` 提交与状态查询。
- Create: `medkernel-backend/src/test/java/com/medkernel/shared/runtime/task/RuntimeTaskServiceTest.java` — 红绿单测。
- Create: `medkernel-backend/src/test/java/com/medkernel/shared/runtime/task/RuntimeTaskMigrationContractTest.java` — V41 五方言合同。
- Modify: `medkernel-backend/src/main/java/com/medkernel/shared/architecture/DomainOwnershipCatalog.java` — 新增 `shared-runtime-task` owner。
- Modify: `medkernel-backend/src/main/java/com/medkernel/engine/contract/ServiceContractCatalog.java` — 登记任务服务契约。
- Modify: `medkernel-backend/src/test/java/com/medkernel/migration/MigrationBaselineContractTest.java` — 纳入 V41、`sys_task`、索引、约束和生命周期字段。
- Modify: `docs/cards/D0/SYS-05.md` — 勾选 PR1 覆盖项并写证据。
- Modify: `docs/backlog.md` — 将 SYS-03 更新为 done，SYS-05 保持 pending 至 PR2 完整完成。
- Modify: `docs/_HANDOFF.md` — 归档 SYS-03 PR2，登记 SYS-05 PR1 当前状态与下一步。

## Task 1: Baseline And RED Tests

**Files:**
- Create: `medkernel-backend/src/test/java/com/medkernel/shared/runtime/task/RuntimeTaskServiceTest.java`
- Create: `medkernel-backend/src/test/java/com/medkernel/shared/runtime/task/RuntimeTaskMigrationContractTest.java`

- [x] **Step 1: Run baseline before touching code**

Run from `medkernel-backend`:

```bash
mvn -B -q -Dtest=MigrationBaselineContractTest,DomainOwnershipContractTest,ServiceContractGovernanceTest,OpenApiContractConfigurationTest test
```

Expected: exit `0`; this baseline was already green on branch `codex/sys-05-runtime-modes-pr1`.

- [x] **Step 2: Write service behavior red tests**

Create `RuntimeTaskServiceTest` with these test methods:

```java
@Test
void onlineTimeoutReturnsEscalatedWithoutThrowingAndAudits() {
    RuntimeTaskExecutorPort executor = command -> RuntimeTaskExecutionResult.timeout("ONLINE_TIMEOUT", "同步任务超时，主流程未阻断");
    RuntimeTaskService service = serviceWith(executor);

    RuntimeTaskResponse response = service.submit(new RuntimeTaskSubmitRequest(
        RuntimeTaskMode.ONLINE,
        "RUNTIME_SELF_CHECK",
        "{\"source\":\"unit\"}",
        List.of()
    ));

    assertThat(response.status()).isEqualTo(RuntimeTaskStatus.ESCALATED);
    assertThat(response.message()).contains("主流程未阻断");
    assertThat(response.totalCount()).isEqualTo(1);
    assertThat(response.failureCount()).isEqualTo(1);
    verify(auditRecorder).record(argThat(command ->
        command.action() == AuditAction.EXECUTE
            && command.targetType().equals("sys_task")
            && String.valueOf(command.after()).contains("ESCALATED")));
}

@Test
void asyncSubmitPersistsUnreadTaskAndStatusCanBePolled() {
    RuntimeTaskService service = serviceWith(command -> RuntimeTaskExecutionResult.completed("不应执行"));

    RuntimeTaskResponse submitted = service.submit(new RuntimeTaskSubmitRequest(
        RuntimeTaskMode.ASYNC,
        "RUNTIME_SELF_CHECK",
        "{\"source\":\"unit\"}",
        List.of()
    ));
    RuntimeTaskResponse polled = service.getTask(submitted.taskId());

    assertThat(submitted.status()).isEqualTo(RuntimeTaskStatus.UNREAD);
    assertThat(polled.taskId()).isEqualTo(submitted.taskId());
    assertThat(polled.status()).isEqualTo(RuntimeTaskStatus.UNREAD);
    verifyNoInteractions(executorShouldNotRunForAsync);
}

@Test
void batchPartialSuccessPersistsCountsAndRetryableFailures() {
    RuntimeTaskExecutorPort executor = command -> RuntimeTaskExecutionResult.partialSuccess(
        "批量任务部分成功",
        3,
        2,
        1,
        List.of(new RuntimeTaskFailureItem("item-2", "VALIDATION_FAILED", "数据缺失", true))
    );
    RuntimeTaskService service = serviceWith(executor);

    RuntimeTaskResponse response = service.submit(new RuntimeTaskSubmitRequest(
        RuntimeTaskMode.BATCH,
        "RUNTIME_SELF_CHECK",
        "{\"source\":\"unit\"}",
        List.of(
            new RuntimeTaskBatchItem("item-1", "{\"ok\":true}"),
            new RuntimeTaskBatchItem("item-2", "{\"ok\":false}"),
            new RuntimeTaskBatchItem("item-3", "{\"ok\":true}")
        )
    ));

    assertThat(response.status()).isEqualTo(RuntimeTaskStatus.PARTIAL_SUCCESS);
    assertThat(response.totalCount()).isEqualTo(3);
    assertThat(response.successCount()).isEqualTo(2);
    assertThat(response.failureCount()).isEqualTo(1);
    assertThat(response.retryableCount()).isEqualTo(1);
    assertThat(response.failures()).extracting(RuntimeTaskFailureItem::itemId).containsExactly("item-2");
}
```

The helper `serviceWith` must use an in-memory fake repository implementing the exact `RuntimeTaskRepository` methods used by the service, and mocks for `PayloadStoragePort`, `AuditRecorder`, and `StateTransitionRecorder`.

- [x] **Step 3: Write migration red test**

Create `RuntimeTaskMigrationContractTest`:

```java
class RuntimeTaskMigrationContractTest {
    private final Path migrationRoot = Path.of("src/main/resources/db/migration");

    @Test
    void runtimeTaskMigrationExistsInEveryDialectWithChineseComments() throws IOException {
        for (String dialect : List.of("h2", "postgres", "oracle", "dm", "kingbase")) {
            Path migration = migrationRoot.resolve(dialect).resolve("V41__runtime_task_framework.sql");
            assertThat(migration).as(dialect + " runtime task migration").exists();
            String sql = Files.readString(migration);
            assertThat(sql)
                .contains("sys_task")
                .contains("uk_sys_task_tenant_task")
                .contains("idx_sys_task_status_ts")
                .contains("idx_sys_task_mode_ts")
                .contains("idx_sys_task_org_ts")
                .contains("任务运行框架");
            if (List.of("postgres", "oracle", "dm", "kingbase").contains(dialect)) {
                assertThat(sql)
                    .contains("COMMENT ON TABLE sys_task")
                    .contains("COMMENT ON COLUMN sys_task.task_id")
                    .contains("COMMENT ON COLUMN sys_task.status");
            }
        }
    }
}
```

- [x] **Step 4: Run RED tests**

Run:

```bash
mvn -B -q -Dtest=RuntimeTaskServiceTest,RuntimeTaskMigrationContractTest test
```

Expected: fails because runtime task classes and V41 migrations do not exist.

## Task 2: Runtime Task Model And Migrations

**Files:**
- Create all production files listed in File Map under `com.medkernel.shared.runtime.task`
- Create five `V41__runtime_task_framework.sql` files
- Modify `DomainOwnershipCatalog`
- Modify `MigrationBaselineContractTest`

- [x] **Step 1: Add enums and DTO records**

Implement:

```java
public enum RuntimeTaskMode {
    ONLINE,
    ASYNC,
    BATCH
}

public enum RuntimeTaskStatus {
    UNREAD,
    PROCESSING,
    COMPLETED,
    PARTIAL_SUCCESS,
    FAILED,
    ESCALATED
}

public record RuntimeTaskFailureItem(
    String itemId,
    String errorCode,
    String message,
    boolean retryable
) {
}
```

`RuntimeTaskSubmitRequest` must validate non-null `mode`, non-blank `taskType`, max payload length `1048576`, and for `BATCH` require non-empty `items`.

- [x] **Step 2: Add `RuntimeTaskRecord` and repository**

`RuntimeTaskRecord` maps to `sys_task`; `taskId` is a ULID-like deterministic prefix from time plus random-free monotonic counter in service tests is acceptable only in test fake. Production task id must use `SecureRandom` or `UUID` as identifier, never as hash or proof.

- [x] **Step 3: Add V41 five dialect migrations**

Each dialect creates `sys_task` with:

```sql
task_id, tenant_id, org_path, task_mode, status, task_type,
payload_storage_type, payload_uri, payload_digest, payload_size_bytes,
total_count, success_count, failure_count, retryable_count,
failure_details_json, message, error_code, trace_id,
started_at, finished_at, created_at, created_by, updated_at, updated_by
```

Constraints and indexes:

```sql
uk_sys_task_tenant_task
ck_sys_task_mode
ck_sys_task_status
idx_sys_task_status_ts
idx_sys_task_mode_ts
idx_sys_task_org_ts
```

Use Chinese `COMMENT ON TABLE` / `COMMENT ON COLUMN` where the dialect supports it; H2 may use inline comments plus parser-friendly SQL.

- [x] **Step 4: Update architecture and migration catalogs**

Add `shared-runtime-task` owner:

```java
module("shared-runtime-task", packages("com.medkernel.shared.runtime.task"), prefixes(), tables("sys_task"))
```

Add V41, `sys_task`, indexes, constraints, tenant/lifecycle/audit fields to `MigrationBaselineContractTest`.

## Task 3: Runtime Task Service And Controller

**Files:**
- Create: `RuntimeTaskExecutionCommand.java`
- Create: `RuntimeTaskExecutionResult.java`
- Create: `RuntimeTaskExecutorPort.java`
- Create: `DefaultRuntimeTaskExecutor.java`
- Create: `RuntimeTaskService.java`
- Create: `RuntimeTaskController.java`

- [x] **Step 1: Implement executor port**

The port shape:

```java
public interface RuntimeTaskExecutorPort {
    RuntimeTaskExecutionResult execute(RuntimeTaskExecutionCommand command);
}
```

`RuntimeTaskExecutionCommand` must include only `taskId`, `tenantId`, `orgPath`, `mode`, `taskType`, `payloadRef`, `batchItemCount`, `traceId`; no patient data or raw clinical payload fields.

- [x] **Step 2: Implement default executor**

`DefaultRuntimeTaskExecutor`:
- accepts `taskType="RUNTIME_SELF_CHECK"` and returns `completed("运行任务框架自检完成")`;
- returns `failed("UNSUPPORTED_TASK_TYPE", "未接入真实执行器，任务未执行")` for unknown task types;
- never pretends business work completed.

- [x] **Step 3: Implement service modes**

`RuntimeTaskService.submit` behavior:
- `ONLINE`: persist `PROCESSING`, execute immediately, transition to terminal status; timeout result maps to `ESCALATED` and returns success envelope with honest message.
- `ASYNC`: persist `UNREAD` and return without executing.
- `BATCH`: require items, persist `PROCESSING`, execute once with batch metadata, terminal status may be `COMPLETED`, `PARTIAL_SUCCESS`, or `FAILED`.

Every terminal transition records:
- `StateTransitionRecorder.record("sys_task", taskId, fromStatus, toStatus, reason, error)`
- `AuditRecorder.record(new AuditRecordCommand(AuditAction.EXECUTE, "sys_task", taskId, summary, before, after, environmentKey))`

- [x] **Step 4: Implement controller**

Controller contract:

```java
@RestController
@RequestMapping("/api/v1/system/tasks")
public class RuntimeTaskController {
    @PostMapping
    @PreAuthorize("@perm.has('system.manage')")
    public ApiResult<RuntimeTaskResponse> submit(@Valid @RequestBody RuntimeTaskSubmitRequest request) { ... }

    @GetMapping("/{taskId}")
    @PreAuthorize("@perm.has('system.read')")
    public ApiResult<RuntimeTaskResponse> get(@PathVariable String taskId) { ... }
}
```

## Task 4: Contracts, Docs, And Handoff

**Files:**
- Modify: `ServiceContractCatalog`
- Modify: `docs/cards/D0/SYS-05.md`
- Modify: `docs/backlog.md`
- Modify: `docs/_HANDOFF.md`

- [x] **Step 1: Register service contract**

Add:

```java
contract("runtime-task", "运行任务框架服务",
    "com.medkernel.shared.runtime.task.RuntimeTaskController", "/api/v1/system/tasks",
    permissions("system.read", "system.manage"),
    audits(audit(AuditAction.EXECUTE, "sys_task", "提交、轮询和执行运行任务")))
```

- [x] **Step 2: Update SYS-05 card**

Check only:
- FR-1
- FR-2
- FR-3
- AC-1
- AC-2

Add PR1 evidence with exact commands run. Keep FR-4/5/6 and AC-3/4/5 unchecked.

- [x] **Step 3: Update backlog and handoff**

Set SYS-03 to `done` in `docs/backlog.md`; keep SYS-05 `pending` until PR2. In `_HANDOFF`, archive SYS-03 PR2 with PR #225 / merge `8e6063a`, and set active line to SYS-05 PR1.

## Task 5: Verification And PR

**Files:**
- All changed files

- [x] **Step 1: Focused tests**

Run:

```bash
mvn -B -q -Dtest=RuntimeTaskServiceTest,RuntimeTaskMigrationContractTest,MigrationBaselineContractTest,DomainOwnershipContractTest,ServiceContractGovernanceTest,OpenApiContractConfigurationTest test
```

Expected: exit `0`.

- [x] **Step 2: Full backend**

Run:

```bash
mvn -B -q test
```

Result: exit `0`; Surefire totals `tests=756 failures=0 errors=0 skipped=0`.

Additional TDD cleanup discovered during self-review: added red test
`RuntimeTaskServiceTest.batchCompletedResultUsesSubmittedItemCount` after finding batch `COMPLETED`
results could keep single-task counts; fixed batch result normalization and re-ran focused + full backend green.

- [x] **Step 3: T-GATE**

Run from repo root:

```bash
node scripts/authenticity-guard.mjs --mode=changed --base=origin/main
node scripts/config-boundary-guard.mjs --mode=changed --base=origin/main
node scripts/migration-convention-guard.mjs --mode=changed --base=origin/main
scripts/check-comment-zh.sh
git diff --check origin/main...HEAD
```

Result: all exit `0`; `node --test scripts/migration-convention-guard.test.mjs` also passed `5/5`.

- [ ] **Step 4: Commit, PR, CI, merge**

Commit message:

```bash
git commit -m "完成 SYS-05 运行模式框架 PR1"
```

Open PR, wait for remote CI all green, squash merge, pull `origin/main`, remove worktree and merged branch, then continue to SYS-05 PR2.

## Self Review

- Spec coverage: PR1 covers SYS-05 FR-1/2/3 and AC-1/2. PR2 explicitly owns FR-4/5/6 and AC-3/4/5, so this plan does not claim them.
- Placeholder scan: No `TBD` / vague “handle later” instructions are used; PR2 scope is named and bounded by the card’s own split.
- Type consistency: All referenced runtime task types live under `com.medkernel.shared.runtime.task`; service and controller use the same DTO names; contract path is `/api/v1/system/tasks`.
