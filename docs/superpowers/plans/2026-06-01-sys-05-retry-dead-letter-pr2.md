# SYS-05 Retry Dead Letter PR2 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 交付 SYS-05 PR2：离线运行模式、故障重试、死信入库、人工回放、断连诚实降级，收口 SYS-05 FR-4/5/6 与 AC-3/4/5。

**Architecture:** 在 PR1 的 `com.medkernel.shared.runtime.task` 框架上扩展，不新造队列。`sys_task` 继续作为任务权威表，V42 增补重试与离线字段；新增 `sys_task_dead_letter` 作为死信权威表。执行器仍只拿 payload 引用；没有真实执行器或外部依赖断开时写 `NOT_CONNECTED` / `DEAD_LETTER`，禁止伪成功。

**Tech Stack:** Spring Boot 3、Spring Data JDBC、Record DTO + Bean Validation、`ApiResult`、`RequestContext`、`AuditRecorder`、`StateTransitionRecorder`、Flyway 五方言迁移、JUnit 5 + Mockito + AssertJ。

---

## 执行约束

- 当前运行环境只保障 PostgreSQL + Oracle；达梦 / 人大金仓真实连接保持 `DEFER-001`，本 PR 只提交五方言迁移文件与静态契约。
- 本 PR 必须关闭 SYS-05 FR-4/5/6 与 AC-3/4/5；PR1 的 FR-1/2/3 与 AC-1/2 不回退。
- `OFFLINE` 表示使用本地 payload 与本地执行器运行，不依赖外网；不能把未接入业务执行器写成完成。
- `NOT_CONNECTED` 是诚实终态，不是异常吞掉后的成功；外部依赖未接入或断连时必须可见。
- 死信入库必须可重放、可审计、可追踪；重放创建新任务，原死信记录写 `replay_task_id`，不覆盖原失败证据。
- 先写红测并确认失败，再实现；完成声明必须附本地测试、T-GATE、远端 CI 证据。

## File Map

- Modify: `medkernel-backend/src/main/java/com/medkernel/shared/runtime/task/RuntimeTaskMode.java` — 增加 `OFFLINE`。
- Modify: `medkernel-backend/src/main/java/com/medkernel/shared/runtime/task/RuntimeTaskStatus.java` — 增加 `NOT_CONNECTED`、`DEAD_LETTER`。
- Modify: `medkernel-backend/src/main/java/com/medkernel/shared/runtime/task/RuntimeTaskSubmitRequest.java` — 增加可选 `maxRetries`，保留旧四参构造兼容 PR1 测试。
- Modify: `medkernel-backend/src/main/java/com/medkernel/shared/runtime/task/RuntimeTaskExecutionResult.java` — 增加 `notConnected` 工厂方法。
- Modify: `medkernel-backend/src/main/java/com/medkernel/shared/runtime/task/RuntimeTaskRecord.java` — 增加重试、死信、回放字段与状态变更 helper。
- Modify: `medkernel-backend/src/main/java/com/medkernel/shared/runtime/task/RuntimeTaskResponse.java` — 返回 `retryCount`、`maxRetries`、`nextAttemptAt`、`deadLetterId`、`replayedFromTaskId`。
- Create: `medkernel-backend/src/main/java/com/medkernel/shared/runtime/task/RuntimeTaskDeadLetterRecord.java` — `sys_task_dead_letter` 实体。
- Create: `medkernel-backend/src/main/java/com/medkernel/shared/runtime/task/RuntimeTaskDeadLetterRepository.java` — 死信仓储。
- Modify: `medkernel-backend/src/main/java/com/medkernel/shared/runtime/task/RuntimeTaskRepository.java` — 保留按租户查询；如需只通过 `save` 更新，不增加原生 SQL。
- Modify: `medkernel-backend/src/main/java/com/medkernel/shared/runtime/task/RuntimeTaskService.java` — 抽取执行方法，新增 `retryTask` / `replayDeadLetter`，统一处理 `NOT_CONNECTED` 与耗尽入死信。
- Modify: `medkernel-backend/src/main/java/com/medkernel/shared/runtime/task/RuntimeTaskController.java` — 新增 `POST /{taskId}/retry` 与 `POST /dead-letters/{deadLetterId}/replay`。
- Modify: `medkernel-backend/src/main/java/com/medkernel/engine/contract/ServiceContractCatalog.java` — runtime-task 审计描述补重试 / 回放。
- Modify: `medkernel-backend/src/main/java/com/medkernel/shared/architecture/DomainOwnershipCatalog.java` — `shared-runtime-task` owner 增加 `sys_task_dead_letter`。
- Create: `medkernel-backend/src/main/resources/db/migration/{h2,postgres,oracle,dm,kingbase}/V42__runtime_task_retry_dead_letter.sql` — 增加列、约束、死信表、索引和中文注释。
- Modify: `medkernel-backend/src/test/java/com/medkernel/shared/runtime/task/RuntimeTaskServiceTest.java` — 新增 PR2 红绿行为测试。
- Modify: `medkernel-backend/src/test/java/com/medkernel/shared/runtime/task/RuntimeTaskMigrationContractTest.java` — 覆盖 V42 五方言、死信表、`OFFLINE`、`NOT_CONNECTED`、`DEAD_LETTER`。
- Modify: `medkernel-backend/src/test/java/com/medkernel/migration/MigrationBaselineContractTest.java` — 纳入 V42、`sys_task_dead_letter`、新增字段、索引和约束。
- Modify: `medkernel-backend/src/test/java/com/medkernel/migration/H2BaselineMigrationTest.java` — 最新迁移版本从 41 调到 42。
- Modify: `medkernel-backend/src/test/java/com/medkernel/migration/FlywayMultiDialectSmokeTest.java` — 最新迁移版本从 41 调到 42。
- Modify: `docs/cards/D0/SYS-05.md` — 勾选 FR-4/5/6 与 AC-3/4/5，补 PR2 证据。
- Modify: `docs/backlog.md` — SYS-05 完成后标 `done`。
- Modify: `docs/_HANDOFF.md` — 归档 PR1，登记/推进 PR2 状态。

## Task 1: Baseline And RED Tests

**Files:**
- Modify: `medkernel-backend/src/test/java/com/medkernel/shared/runtime/task/RuntimeTaskServiceTest.java`
- Modify: `medkernel-backend/src/test/java/com/medkernel/shared/runtime/task/RuntimeTaskMigrationContractTest.java`

- [x] **Step 1: Run baseline before PR2 code**

Run from `medkernel-backend`:

```bash
mvn -B -q -Dtest=RuntimeTaskServiceTest,RuntimeTaskMigrationContractTest,MigrationBaselineContractTest,DomainOwnershipContractTest,ServiceContractGovernanceTest,OpenApiContractConfigurationTest test
```

Result: exit `0` on branch `codex/sys-05-retry-dead-letter-pr2`.

- [x] **Step 2: Add service RED tests**

Add these tests to `RuntimeTaskServiceTest`:

```java
@Test
void offlineModeRunsWithLocalExecutorAndNoExternalDependency() {
    RuntimeTaskService service = serviceWith(command -> RuntimeTaskExecutionResult.completed("离线任务本地完成"));

    RuntimeTaskResponse response = service.submit(new RuntimeTaskSubmitRequest(
        RuntimeTaskMode.OFFLINE,
        "RUNTIME_SELF_CHECK",
        "{\"source\":\"offline\"}",
        List.of()
    ));

    assertThat(response.mode()).isEqualTo(RuntimeTaskMode.OFFLINE);
    assertThat(response.status()).isEqualTo(RuntimeTaskStatus.COMPLETED);
    assertThat(response.message()).contains("离线任务本地完成");
    verify(auditRecorder).record(argThat(command ->
        command.action() == AuditAction.EXECUTE
            && command.targetType().equals("sys_task")
            && String.valueOf(command.after()).contains("OFFLINE")));
}

@Test
void notConnectedResultIsPersistedHonestlyWithoutSuccess() {
    RuntimeTaskService service = serviceWith(command ->
        RuntimeTaskExecutionResult.notConnected("NOT_CONNECTED", "外部执行器未连接，任务未执行"));

    RuntimeTaskResponse response = service.submit(new RuntimeTaskSubmitRequest(
        RuntimeTaskMode.ONLINE,
        "EXTERNAL_SYNC",
        "{\"source\":\"unit\"}",
        List.of()
    ));

    assertThat(response.status()).isEqualTo(RuntimeTaskStatus.NOT_CONNECTED);
    assertThat(response.successCount()).isZero();
    assertThat(response.failureCount()).isEqualTo(1);
    assertThat(response.errorCode()).isEqualTo("NOT_CONNECTED");
    assertThat(response.message()).contains("未连接");
}

@Test
void retryExhaustionMovesTaskToDeadLetterAndReplayCreatesNewCompletedTask() {
    AtomicInteger attempts = new AtomicInteger();
    RuntimeTaskService service = serviceWith(command -> attempts.incrementAndGet() < 3
        ? RuntimeTaskExecutionResult.failed("DOWNSTREAM_FAILED", "下游失败")
        : RuntimeTaskExecutionResult.completed("人工回放成功"));

    RuntimeTaskResponse failed = service.submit(new RuntimeTaskSubmitRequest(
        RuntimeTaskMode.ONLINE,
        "EXTERNAL_SYNC",
        "{\"source\":\"unit\"}",
        List.of(),
        1
    ));
    RuntimeTaskResponse dead = service.retryTask(failed.taskId());
    RuntimeTaskResponse replayed = service.replayDeadLetter(dead.deadLetterId());

    assertThat(failed.status()).isEqualTo(RuntimeTaskStatus.FAILED);
    assertThat(dead.status()).isEqualTo(RuntimeTaskStatus.DEAD_LETTER);
    assertThat(dead.retryCount()).isEqualTo(1);
    assertThat(dead.deadLetterId()).isNotBlank();
    assertThat(replayed.status()).isEqualTo(RuntimeTaskStatus.COMPLETED);
    assertThat(replayed.replayedFromTaskId()).isEqualTo(failed.taskId());
    verify(deadLetterRepository).save(argThat(record ->
        record.deadLetterId().equals(dead.deadLetterId())
            && replayed.taskId().equals(record.replayTaskId())));
}
```

Update `serviceWith` helper to pass a mocked `RuntimeTaskDeadLetterRepository`. The first run must fail because `OFFLINE` / `NOT_CONNECTED` / `DEAD_LETTER` / `retryTask` / `replayDeadLetter` do not exist.

- [x] **Step 3: Add migration RED test**

Extend `RuntimeTaskMigrationContractTest` to require `V42__runtime_task_retry_dead_letter.sql` in all five dialects, containing:

```java
assertThat(sql)
    .contains("sys_task_dead_letter")
    .contains("retry_count")
    .contains("max_retries")
    .contains("dead_letter_id")
    .contains("OFFLINE")
    .contains("NOT_CONNECTED")
    .contains("DEAD_LETTER")
    .contains("任务死信");
```

For postgres / oracle / dm / kingbase also assert:

```java
assertThat(sql)
    .contains("COMMENT ON TABLE sys_task_dead_letter")
    .contains("COMMENT ON COLUMN sys_task_dead_letter.dead_letter_id")
    .contains("COMMENT ON COLUMN sys_task.retry_count");
```

- [x] **Step 4: Run RED tests**

Run:

```bash
mvn -B -q -Dtest=RuntimeTaskServiceTest,RuntimeTaskMigrationContractTest test
```

Result: exit `1`，按预期失败在缺少 `RuntimeTaskDeadLetterRepository` / `RuntimeTaskDeadLetterRecord` / `OFFLINE` / `NOT_CONNECTED` / `DEAD_LETTER` / `retryTask` / `replayDeadLetter` / V42 迁移。

## Task 2: Runtime Retry And Dead Letter Model

**Files:**
- Modify: production runtime task files in File Map
- Create: `RuntimeTaskDeadLetterRecord.java`
- Create: `RuntimeTaskDeadLetterRepository.java`

- [x] **Step 1: Extend enums and DTOs**

Implement:

```java
public enum RuntimeTaskMode {
    ONLINE,
    ASYNC,
    BATCH,
    OFFLINE
}

public enum RuntimeTaskStatus {
    UNREAD,
    PROCESSING,
    COMPLETED,
    PARTIAL_SUCCESS,
    FAILED,
    ESCALATED,
    NOT_CONNECTED,
    DEAD_LETTER
}
```

Add `Integer maxRetries` to `RuntimeTaskSubmitRequest`, keep a four-argument constructor:

```java
public RuntimeTaskSubmitRequest(RuntimeTaskMode mode, String taskType, String payloadJson,
                                List<RuntimeTaskBatchItem> items) {
    this(mode, taskType, payloadJson, items, null);
}
```

In the compact constructor, normalize `maxRetries` to `null` or `0..10`; invalid values should fail Bean Validation and service validation.

- [x] **Step 2: Add honest execution result factory**

Add:

```java
public static RuntimeTaskExecutionResult notConnected(String errorCode, String message) {
    return new RuntimeTaskExecutionResult(RuntimeTaskStatus.NOT_CONNECTED, message, errorCode, 1, 0, 1, 1, List.of());
}
```

Update normalization to treat `NOT_CONNECTED` as a terminal failure-like status and never as success.

- [x] **Step 3: Extend `RuntimeTaskRecord`**

Add columns:

```java
@Column("retry_count") Integer retryCount,
@Column("max_retries") Integer maxRetries,
@Column("next_attempt_at") Instant nextAttemptAt,
@Column("last_error_code") String lastErrorCode,
@Column("dead_letter_id") String deadLetterId,
@Column("replayed_from_task_id") String replayedFromTaskId
```

Add helpers:

```java
withProcessingForRetry(int retryCount, Instant now, String actor)
withRetryTerminal(RuntimeTaskExecutionResult result, String failureDetails, Instant now, String actor)
withDeadLetter(String deadLetterId, int retryCount, Instant now, String actor)
```

Each helper must preserve tenant, org, payload ref and traceId.

- [x] **Step 4: Add dead letter record/repository**

`RuntimeTaskDeadLetterRecord` fields:

```java
@Id Long id;
String deadLetterId;
String tenantId;
String orgPath;
String taskId;
String taskMode;
String taskType;
String payloadStorageType;
String payloadUri;
String payloadDigest;
Long payloadSizeBytes;
Integer retryCount;
String failureDetailsJson;
String errorCode;
String message;
String traceId;
Instant createdAt;
String createdBy;
Instant replayedAt;
String replayedBy;
String replayTaskId;
```

Repository methods:

```java
Optional<RuntimeTaskDeadLetterRecord> findByTenantIdAndDeadLetterId(String tenantId, String deadLetterId);
Optional<RuntimeTaskDeadLetterRecord> findByTenantIdAndTaskId(String tenantId, String taskId);
```

## Task 3: Service And Controller Behavior

**Files:**
- Modify: `RuntimeTaskService.java`
- Modify: `RuntimeTaskController.java`
- Modify: `ServiceContractCatalog.java`
- Modify: `DomainOwnershipCatalog.java`

- [x] **Step 1: Inject dead letter repository and default max retries**

Constructor adds `RuntimeTaskDeadLetterRepository deadLetters`.

Constants:

```java
private static final int DEFAULT_MAX_RETRIES = 2;
private static final int MAX_RETRIES_LIMIT = 10;
private static final String NOT_CONNECTED = "NOT_CONNECTED";
```

`validate` rejects `maxRetries < 0 || maxRetries > 10`.

- [x] **Step 2: Factor execution**

Create a private method:

```java
private RuntimeTaskRecord executeAndPersist(RuntimeTaskRecord processing,
                                            int batchItemCount,
                                            String actor,
                                            RuntimeTaskRecord beforeForAudit)
```

It builds `RuntimeTaskExecutionCommand` from the record, calls executor, normalizes by record `mode` and `batchItemCount`, saves terminal result, records transition and audit.

- [x] **Step 3: Implement retry**

`retryTask(String taskId)`:
- requires tenant context;
- only allows `FAILED`, `ESCALATED`, `NOT_CONNECTED`;
- increments retry count;
- saves `PROCESSING`;
- executes once;
- if terminal result is `FAILED`, `ESCALATED`, or `NOT_CONNECTED` and retry count reaches `maxRetries`, create dead letter and save task as `DEAD_LETTER`;
- returns updated `RuntimeTaskResponse`.

Dead letter creation must use `dead-" + UUID.randomUUID()` as identifier only, not as proof/hash.

- [x] **Step 4: Implement replay**

`replayDeadLetter(String deadLetterId)`:
- requires tenant context;
- loads unreplayed dead letter by tenant;
- creates new `RuntimeTaskRecord` with same payload ref, mode, task type, total count and `replayed_from_task_id`;
- executes immediately;
- updates dead letter `replayedAt/replayedBy/replayTaskId`;
- returns new task response.

- [x] **Step 5: Add controller endpoints**

```java
@PostMapping("/{taskId}/retry")
@PreAuthorize("@perm.has('system.manage')")
public ApiResult<RuntimeTaskResponse> retry(@PathVariable String taskId) { ... }

@PostMapping("/dead-letters/{deadLetterId}/replay")
@PreAuthorize("@perm.has('system.manage')")
public ApiResult<RuntimeTaskResponse> replay(@PathVariable String deadLetterId) { ... }
```

Service contract audit description must mention submit / retry / replay.

## Task 4: V42 Five-Dialect Migration And Contracts

**Files:**
- Create V42 in five dialect folders
- Modify migration tests listed in File Map

- [x] **Step 1: Add V42 migrations**

Each V42 must:
- add `sys_task.retry_count`, `max_retries`, `next_attempt_at`, `last_error_code`, `dead_letter_id`, `replayed_from_task_id`;
- update `ck_sys_task_mode` to include `OFFLINE`;
- update `ck_sys_task_status` to include `NOT_CONNECTED` and `DEAD_LETTER`;
- create `sys_task_dead_letter`;
- create indexes `idx_sys_task_retry_ts`, `idx_sys_task_dead_letter`, `idx_sys_task_dead_tenant_ts`, `idx_sys_task_dead_task`;
- add Chinese comments for production dialects.

- [x] **Step 2: Update migration baselines**

Change latest version constants/lists from 41 to 42 in:

```text
H2BaselineMigrationTest
FlywayMultiDialectSmokeTest
MigrationBaselineContractTest
```

`MigrationBaselineContractTest` must assert `sys_task_dead_letter` owner fields, indexes and constraints.

- [x] **Step 3: Update owner catalog**

Change:

```java
module("shared-runtime-task", packages("com.medkernel.shared.runtime.task"), prefixes(),
    tables("sys_task", "sys_task_dead_letter"))
```

## Task 5: Docs, Handoff, Verification, PR

**Files:**
- Modify: `docs/cards/D0/SYS-05.md`
- Modify: `docs/backlog.md`
- Modify: `docs/_HANDOFF.md`

- [x] **Step 1: Update docs**

Only after local tests pass:
- check SYS-05 FR-4/5/6 and AC-3/4/5;
- add PR2 evidence with exact commands;
- mark SYS-05 `done` in backlog;
- move PR1 active line into archived section and set current active line to PR2 until merged.

- [x] **Step 2: Focused tests**

Run:

```bash
mvn -B -q -Dtest=RuntimeTaskServiceTest,RuntimeTaskMigrationContractTest,MigrationBaselineContractTest,DomainOwnershipContractTest,ServiceContractGovernanceTest,OpenApiContractConfigurationTest test
```

Expected: exit `0`.

- [x] **Step 3: Full backend**

Run:

```bash
mvn -B -q test
```

Expected: exit `0`, record Surefire totals and PostgreSQL + Oracle V42 migration evidence.

Result:
- 首次全量发现 Oracle V42 迁移失败：`ORA-01408`，根因为 `sys_task_dead_letter` 已有 `(tenant_id, task_id)` 唯一约束，Oracle 自动建索引后又创建同列同序 `idx_sys_task_dead_task`。
- 已按根因把五方言 `idx_sys_task_dead_task` 调整为 `(task_id, tenant_id)`，避免冗余索引并保留按任务 ID 查死信的查询价值。
- 聚焦回归：`mvn -B -q -Dtest=FlywayMultiDialectSmokeTest,MigrationBaselineContractTest,RuntimeTaskMigrationContractTest test` exit `0`，PostgreSQL / H2 / Oracle 均迁移至 V42 且重复 migrate 为 0。
- 完整后端：`mvn -B -q test` exit `0`，Surefire `tests=760 failures=0 errors=0 skipped=0`，含 PostgreSQL 15 + Oracle 21 Testcontainers 迁移至 V42。

- [x] **Step 4: T-GATE**

Run from repo root after commit:

```bash
node --test scripts/migration-convention-guard.test.mjs
node scripts/authenticity-guard.mjs --mode=changed --base=origin/main
node scripts/config-boundary-guard.mjs --mode=changed --base=origin/main
node scripts/migration-convention-guard.mjs --mode=changed --base=origin/main
scripts/check-comment-zh.sh
git diff --check origin/main...HEAD
```

Expected: all exit `0`.

Result:
- `node --test scripts/authenticity-guard.test.mjs` exit `0`，20/20 pass。
- `node scripts/authenticity-guard.mjs --mode=changed --base=origin/main` exit `0`，扫描 12 个触碰生产文件，无阻断项。
- `node --test scripts/config-boundary-guard.test.mjs` exit `0`，2/2 pass。
- `node scripts/config-boundary-guard.mjs --mode=changed --base=origin/main` exit `0`，扫描 12 个触碰 Java 文件，无阻断项。
- `scripts/check-comment-zh.sh` exit `0`，0 fail / 0 warn。
- `git diff --check origin/main...HEAD` exit `0`。
- `node scripts/migration-convention-guard.mjs --mode=changed --base=origin/main` 首次发现门禁误判：`DROP CONSTRAINT IF EXISTS` 被识别为约束名 `IF`；已新增 `DROP CONSTRAINT IF EXISTS 不会被误判为约束名 IF` 用例并修复解析。
- 修复后 `node --test scripts/migration-convention-guard.test.mjs` exit `0`，6/6 pass；`node scripts/migration-convention-guard.mjs --mode=changed --base=origin/main` exit `0`，扫描 5 个 V42 迁移，无阻断项。

- [ ] **Step 5: PR, CI, merge**

Commit:

```bash
git commit -m "完成 SYS-05 重试死信与离线运行 PR2"
```

Push, create PR, wait for remote CI 8/8, squash merge, pull main, remove worktree and local branch, then领取下一张 backlog 当前阶段任务。

## Self Review

- Spec coverage: FR-4 maps to `OFFLINE`; FR-5 maps to retry/dead-letter/replay; FR-6 maps to `NOT_CONNECTED` terminal behavior. AC-3/4/5 each has a named red/green test.
- Placeholder scan: No TBD/TODO/“后续实现” placeholders;国产化真实环境 remains registered as `DEFER-001` by scope decision.
- Type consistency: All new fields appear in DTO, record, migrations and tests with the same names; DB column uses `task_mode`, Java response still exposes `mode`.
