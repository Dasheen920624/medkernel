# 模型 Provider 受控启停 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 将模型 provider 的连接配置与高危启停分离，补齐脱敏快照、真实健康门、医学评测门、MFA、二次确认、原因和乐观锁，为 T9.8 后续受控执行提供安全入口。

**Architecture:** `ModelProviderConfig` 以 `@Version lock_version` 作为关系库并发事实源；配置 PUT 始终保存为停用，独立 enable/disable 只改变状态。`ModelProviderGovernanceView` 隐藏凭据引用，`ModelProviderGovernanceService` 统一校验环境变量引用、纯净 HTTP(S) 端点、MFA、健康、评测和部署形态。

**Tech Stack:** Java 21、Spring Boot、Spring Data JDBC、MockMvc、JUnit 5、Mockito、Flyway 五方言、Maven。

---

### Task 1: V152 乐观锁基线

**Files:**
- Create: `medkernel-backend/src/main/resources/db/migration/h2/V152__model_provider_lock_version.sql`
- Create: `medkernel-backend/src/main/resources/db/migration/postgres/V152__model_provider_lock_version.sql`
- Create: `medkernel-backend/src/main/resources/db/migration/kingbase/V152__model_provider_lock_version.sql`
- Create: `medkernel-backend/src/main/resources/db/migration/oracle/V152__model_provider_lock_version.sql`
- Create: `medkernel-backend/src/main/resources/db/migration/dm/V152__model_provider_lock_version.sql`
- Modify: `medkernel-backend/src/main/java/com/medkernel/engine/llm/provider/ModelProviderConfig.java`
- Modify: `medkernel-backend/src/test/java/com/medkernel/migration/MigrationBaselineContractTest.java`
- Modify: `medkernel-backend/src/test/java/com/medkernel/migration/H2BaselineMigrationTest.java`
- Modify: `medkernel-backend/src/test/java/com/medkernel/migration/FlywayMultiDialectSmokeTest.java`
- Modify: `medkernel-backend/src/test/java/com/medkernel/engine/llm/provider/ModelProviderConfigRepositoryTest.java`
- Modify: all 12 `new ModelProviderConfig(...)` call sites found by `rg -n "new ModelProviderConfig\\(" medkernel-backend/src`

- [x] **Step 1: 写迁移红测**

在 `MigrationBaselineContractTest` 的权威迁移序列追加：

```java
"V152__model_provider_lock_version.sql"
```

新增五方言合同测试：

```java
@Test
void modelProviderLockVersionMigrationIsAlignedAcrossDialects() {
    for (String dialect : DIALECTS) {
        String ddl = readMigration(dialect, "V152__model_provider_lock_version.sql");
        assertThat(ddl).containsIgnoringCase("mk_llm_provider");
        assertThat(ddl).containsIgnoringCase("lock_version");
        assertThat(ddl).contains("模型 provider 治理并发版本号");
    }
}
```

把 `H2BaselineMigrationTest` 与 `FlywayMultiDialectSmokeTest` 的 `LATEST_MIGRATION_VERSION` 从 `151` 改为 `152`，并在 H2 smoke 增加：

```java
Integer lockVersionColumns = jdbc.queryForObject("""
    SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
    WHERE TABLE_NAME = 'MK_LLM_PROVIDER'
      AND COLUMN_NAME = 'LOCK_VERSION'
      AND IS_NULLABLE = 'NO'
    """, Integer.class);
assertThat(lockVersionColumns).as("模型 provider 乐观锁列").isEqualTo(1);
```

- [x] **Step 2: 运行红测**

Run:

```bash
cd medkernel-backend
mvn -q -Dtest=MigrationBaselineContractTest,H2BaselineMigrationTest test
```

Expected: FAIL，原因是 V152 尚不存在且 H2 只能迁移至 V151。

- [x] **Step 3: 增加五方言迁移**

H2/PostgreSQL/Kingbase：

```sql
ALTER TABLE mk_llm_provider
    ADD COLUMN lock_version BIGINT DEFAULT 0 NOT NULL;

COMMENT ON COLUMN mk_llm_provider.lock_version IS '模型 provider 治理并发版本号，防止配置、探活与启停相互覆盖';
```

Oracle/DM：

```sql
ALTER TABLE mk_llm_provider ADD (
    lock_version NUMBER(19) DEFAULT 0 NOT NULL
);

COMMENT ON COLUMN mk_llm_provider.lock_version IS '模型 provider 治理并发版本号，防止配置、探活与启停相互覆盖';
```

- [x] **Step 4: 实体增加 `@Version`**

在 `ModelProviderConfig` 导入：

```java
import org.springframework.data.annotation.Version;
```

记录末尾增加：

```java
@Version @Column("lock_version") Long version
```

更新全部构造调用：生产代码保存已有行时传 `current.version()`，新建传 `null`；测试夹具使用 `0L`。

- [x] **Step 5: 增加真实仓储并发测试**

在 `ModelProviderConfigRepositoryTest` 用真实 H2 仓储读取同一行两次，先保存第一份，再保存第二份陈旧快照：

```java
ModelProviderConfig first = repository.findByTenantIdAndProviderCode(
    "tenant-1", "ollama-local").orElseThrow();
ModelProviderConfig stale = repository.findByTenantIdAndProviderCode(
    "tenant-1", "ollama-local").orElseThrow();

repository.save(copyWithStatus(first, "HEALTHY"));

assertThatThrownBy(() -> repository.save(copyWithStatus(stale, "NOT_CONNECTED")))
    .isInstanceOf(OptimisticLockingFailureException.class);
```

同时断言第一次保存后的 `version` 大于原版本。

- [x] **Step 6: 运行迁移与 provider 现有测试**

Run:

```bash
cd medkernel-backend
mvn -q -Dtest=MigrationBaselineContractTest,H2BaselineMigrationTest,ModelProviderConfigRepositoryTest,ModelProviderRegistryTest,OllamaProviderTest,ExternalProviderTest test
```

Expected: PASS，H2 从空库迁移至 V152。

### Task 2: 配置接口只保存停用且拒绝危险输入

**Files:**
- Modify: `medkernel-backend/src/main/java/com/medkernel/engine/llm/provider/ModelProviderUpsertRequest.java`
- Modify: `medkernel-backend/src/main/java/com/medkernel/engine/llm/provider/ModelProviderGovernanceService.java`
- Modify: `medkernel-backend/src/test/java/com/medkernel/engine/llm/provider/ModelProviderGovernanceServiceTest.java`
- Modify: `medkernel-backend/src/test/java/com/medkernel/engine/llm/provider/ModelProviderControllerSecurityTest.java`

- [x] **Step 1: 写配置语义红测**

把旧“PUT 可直接启用”测试替换为：

```java
@Test
void newProviderIsAlwaysSavedDisabledAndDoesNotConsultEvaluationGate() {
    when(repo.findByTenantIdAndProviderCode("tenant-1", "ollama-local"))
        .thenReturn(Optional.empty());

    ModelProviderConfig saved = service.upsertProvider(
        "ollama-local",
        new ModelProviderUpsertRequest(
            "OLLAMA",
            "http://127.0.0.1:11434",
            null,
            "qwen2.5:7b",
            null));

    assertThat(saved.enabled()).isFalse();
    assertThat(saved.status()).isEqualTo("NOT_CONNECTED");
    verify(evalService, never()).isClearedForGoLive(any(), any(), any(), any());
}

@Test
void existingProviderUpdateRequiresMatchingVersionAndForcesDisabled() {
    ModelProviderConfig current = provider("Y", "HEALTHY", 4L);
    when(repo.findByTenantIdAndProviderCode("tenant-1", "ollama-local"))
        .thenReturn(Optional.of(current));

    ModelProviderConfig saved = service.upsertProvider(
        "ollama-local",
        new ModelProviderUpsertRequest(
            "OLLAMA",
            "http://127.0.0.1:11434",
            null,
            "qwen2.5:7b",
            4L));

    assertThat(saved.enabled()).isFalse();
    assertThat(saved.status()).isEqualTo("HEALTHY");
    assertThat(saved.version()).isEqualTo(4L);
}
```

补充以下拒绝测试：

- 更新已有 provider 缺 `expectedVersion`；
- `expectedVersion` 与当前版本不一致；
- 新建携带 `expectedVersion`；
- endpoint 含用户名/密码、查询串或片段；
- endpoint 非 HTTP(S)；
- 外部 provider 缺 `credentialRef`；
- 外部 provider 的 `credentialRef` 不匹配 `[A-Z][A-Z0-9_]{2,127}`；
- Ollama 允许空 `credentialRef`。

- [x] **Step 2: 运行红测**

Run:

```bash
cd medkernel-backend
mvn -q -Dtest=ModelProviderGovernanceServiceTest test
```

Expected: FAIL，原因是请求仍含 `enabled` 且服务仍允许 PUT 直接启用。

- [x] **Step 3: 收紧请求 DTO**

将请求改为：

```java
public record ModelProviderUpsertRequest(
    @NotBlank String providerType,
    @NotBlank String endpointUri,
    String credentialRef,
    @NotBlank String modelVersion,
    @PositiveOrZero Long expectedVersion
) {}
```

删除 `enabled`。控制器测试 BODY 改为：

```json
{
  "providerType": "OLLAMA",
  "endpointUri": "http://127.0.0.1:11434",
  "modelVersion": "qwen2.5:7b"
}
```

- [x] **Step 4: 实现配置校验与停用保存**

`upsertProvider` 按以下结构实现：

```java
ModelProviderConfig current = repository
    .findByTenantIdAndProviderCode(tenantId, code)
    .orElse(null);
assertExpectedVersion(current, request.expectedVersion());

ProviderType type = parseType(request.providerType());
String endpointUri = normalizeEndpoint(request.endpointUri());
String credentialRef = normalizeCredentialRef(type, request.credentialRef());
String modelVersion = requireText(request.modelVersion(), "模型版本");
boolean changed = current == null || connectionMaterialChanged(
    current, type, endpointUri, credentialRef, modelVersion);

ModelProviderConfig saved = saveWithConflictTranslation(new ModelProviderConfig(
    current == null ? null : current.id(),
    tenantId,
    code,
    type.name(),
    endpointUri,
    credentialRef,
    modelVersion,
    "N",
    changed ? "NOT_CONNECTED" : current.status(),
    current == null ? now : current.createdAt(),
    current == null ? actor : current.createdBy(),
    now,
    actor,
    current == null ? null : current.version()));
```

`normalizeEndpoint` 必须：

```java
URI uri = URI.create(raw.trim());
if (!Set.of("http", "https").contains(uri.getScheme())
        || uri.getHost() == null
        || uri.getUserInfo() != null
        || uri.getQuery() != null
        || uri.getFragment() != null) {
    throw new ApiException(ErrorCode.BAD_REQUEST, "provider 端点必须是纯净 HTTP(S) 绝对 URL");
}
return uri.toString().replaceAll("/+$", "");
```

`normalizeCredentialRef` 对外部 provider 要求环境变量键名，对 Ollama 允许空值：

```java
private static final Pattern ENV_KEY = Pattern.compile("[A-Z][A-Z0-9_]{2,127}");
```

`assertExpectedVersion`：

```java
if (current == null && expectedVersion != null) {
    throw ApiException.conflict("新建 provider 不能携带 expectedVersion");
}
if (current != null && !Objects.equals(current.version(), expectedVersion)) {
    throw ApiException.conflict("provider 配置版本已变化，请刷新后重试");
}
```

捕获 `OptimisticLockingFailureException` 并转换为相同冲突。

- [x] **Step 5: 运行配置测试转绿**

Run:

```bash
cd medkernel-backend
mvn -q -Dtest=ModelProviderGovernanceServiceTest,ModelProviderControllerSecurityTest test
```

Expected: PASS。

### Task 3: 脱敏只读快照

**Files:**
- Create: `medkernel-backend/src/main/java/com/medkernel/engine/llm/provider/ModelProviderGovernanceView.java`
- Modify: `medkernel-backend/src/main/java/com/medkernel/engine/llm/provider/ModelProviderGovernanceService.java`
- Modify: `medkernel-backend/src/main/java/com/medkernel/engine/llm/provider/ModelProviderController.java`
- Modify: `medkernel-backend/src/test/java/com/medkernel/engine/llm/provider/ModelProviderGovernanceServiceTest.java`
- Modify: `medkernel-backend/src/test/java/com/medkernel/engine/llm/provider/ModelProviderControllerSecurityTest.java`

- [x] **Step 1: 写快照红测**

服务测试：

```java
@Test
void getProviderReturnsCredentialPresenceWithoutCredentialReference() {
    when(repo.findByTenantIdAndProviderCode("tenant-1", "external"))
        .thenReturn(Optional.of(providerWithCredential("MODEL_API_KEY", 7L)));

    ModelProviderGovernanceView view = service.getProvider("external");

    assertThat(view.credentialConfigured()).isTrue();
    assertThat(view.version()).isEqualTo(7L);
    assertThat(view.toString()).doesNotContain("MODEL_API_KEY");
}
```

控制器测试增加临床用户 403、集成运维员 200：

```java
mockMvc.perform(get("/api/v1/model-providers/ollama-local")
    .with(integrationOperatorJwt()))
    .andExpect(status().isOk());
```

- [x] **Step 2: 运行红测**

Run:

```bash
cd medkernel-backend
mvn -q -Dtest=ModelProviderGovernanceServiceTest,ModelProviderControllerSecurityTest test
```

Expected: FAIL，原因是 view、service 方法与 GET 端点尚不存在。

- [x] **Step 3: 实现脱敏 view**

```java
public record ModelProviderGovernanceView(
    String providerCode,
    String providerType,
    String endpointUri,
    boolean credentialConfigured,
    String modelVersion,
    boolean enabled,
    String status,
    Long version,
    Instant updatedAt,
    String updatedBy
) {
    static ModelProviderGovernanceView from(ModelProviderConfig config) {
        return new ModelProviderGovernanceView(
            config.providerCode(),
            config.providerType(),
            config.endpointUri(),
            config.credentialRef() != null && !config.credentialRef().isBlank(),
            config.modelVersion(),
            config.enabled(),
            config.status(),
            config.version(),
            config.updatedAt(),
            config.updatedBy());
    }
}
```

`ModelProviderGovernanceService.getProvider` 按当前 tenant + provider code 精确读取，未找到返回 404。

- [x] **Step 4: 增加 GET 控制器**

```java
@GetMapping("/{providerCode}")
@PreAuthorize("@perm.has('llm.provider.manage')")
public ApiResult<ModelProviderGovernanceView> getProvider(
        @PathVariable String providerCode) {
    return ApiResult.ok(service.getProvider(providerCode));
}
```

- [x] **Step 5: 运行快照测试转绿**

Run:

```bash
cd medkernel-backend
mvn -q -Dtest=ModelProviderGovernanceServiceTest,ModelProviderControllerSecurityTest test
```

Expected: PASS，响应 JSON 不出现 `credentialRef`。

### Task 4: 高危启用与停用状态机

**Files:**
- Create: `medkernel-backend/src/main/java/com/medkernel/engine/llm/provider/ModelProviderActivationRequest.java`
- Modify: `medkernel-backend/src/main/java/com/medkernel/engine/llm/provider/ModelProviderGovernanceService.java`
- Modify: `medkernel-backend/src/main/java/com/medkernel/engine/llm/provider/ModelProviderController.java`
- Modify: `medkernel-backend/src/test/java/com/medkernel/engine/llm/provider/ModelProviderGovernanceServiceTest.java`
- Modify: `medkernel-backend/src/test/java/com/medkernel/engine/llm/provider/ModelProviderControllerSecurityTest.java`

- [x] **Step 1: 写启停红测**

给 service 注入 `HighRiskChangeGuard`，覆盖：

```java
@Test
void enableRequiresHealthyCurrentVersionPassedEvaluationAndMfa() {
    ModelProviderConfig current = provider("N", "HEALTHY", 5L);
    when(repo.findByTenantIdAndProviderCode("tenant-1", "external"))
        .thenReturn(Optional.of(current));
    when(deploymentForm.allowsExternalProvider()).thenReturn(true);
    when(evalService.isClearedForGoLive(
        "tenant-1", "external", current.modelVersion(), "rule.draft")).thenReturn(true);

    ModelProviderGovernanceView enabled = service.enableProvider(
        "external",
        new ModelProviderActivationRequest(
            "rule.draft",
            "独立专家评测已签署，按 T9.8 受控启用",
            5L,
            true));

    assertThat(enabled.enabled()).isTrue();
    verify(highRiskGuard).assertHighRiskAllowed("model_provider", "external");
    verify(auditRecorder).record(
        AuditAction.UPDATE,
        "mk_llm_provider",
        "external",
        "启用模型 provider external（capability=rule.draft）：独立专家评测已签署，按 T9.8 受控启用");
}
```

再覆盖：

- `confirmedHighRisk != true`；
- reason 空；
- MFA guard 抛错；
- expectedVersion 缺失/漂移；
- status 非 `HEALTHY`；
- 外部 provider 在运行侧内网；
- 医学评测未通过；
- 已启用且版本匹配幂等；
- 停用只改 `enabled_flag=N`；
- 已停用且版本匹配幂等；
- 已停用但版本漂移仍冲突；
- 健康检查保存时递增版本且不改启停。

- [x] **Step 2: 运行红测**

Run:

```bash
cd medkernel-backend
mvn -q -Dtest=ModelProviderGovernanceServiceTest test
```

Expected: FAIL，原因是 activation DTO、MFA 依赖和启停方法尚不存在。

- [x] **Step 3: 实现 activation DTO**

```java
public record ModelProviderActivationRequest(
    @Size(max = 64) String capabilityCode,
    @NotBlank @Size(max = 500) String reason,
    @NotNull @PositiveOrZero Long expectedVersion,
    @NotNull Boolean confirmedHighRisk
) {}
```

- [x] **Step 4: 实现启停核心**

服务构造器新增：

```java
private final HighRiskChangeGuard highRiskGuard;
```

公共方法：

```java
@Transactional
public ModelProviderGovernanceView enableProvider(
        String providerCode,
        ModelProviderActivationRequest request) {
    return changeEnabled(providerCode, request, true);
}

@Transactional
public ModelProviderGovernanceView disableProvider(
        String providerCode,
        ModelProviderActivationRequest request) {
    return changeEnabled(providerCode, request, false);
}
```

`changeEnabled` 固定顺序：

```java
assertActivationConfirmed(request);
String tenantId = requireCurrentTenant();
String code = normalizeProviderCode(providerCode);
highRiskGuard.assertHighRiskAllowed("model_provider", code);
ModelProviderConfig current = requireProvider(tenantId, code);
assertExpectedVersion(current, request.expectedVersion());

if (current.enabled() == enabled) {
    return ModelProviderGovernanceView.from(current);
}
if (enabled) {
    String capabilityCode = requireActivationCapability(request);
    if (!ProviderHealth.HEALTHY.name().equals(current.status())) {
        throw ApiException.conflict("provider 未通过当前真实健康检查，禁止启用");
    }
    ProviderType type = parseType(current.providerType());
    if (type.external() && !deploymentForm.allowsExternalProvider()) {
        throw new ApiException(ErrorCode.ENG_LLM_009);
    }
    if (!evalService.isClearedForGoLive(
            tenantId, code, current.modelVersion(), capabilityCode)) {
        throw new ApiException(ErrorCode.ENG_LLM_008);
    }
}

ModelProviderConfig saved = saveWithConflictTranslation(copyEnabled(
    current, enabled, currentActor(), Instant.now()));
auditRecorder.record(
    AuditAction.UPDATE,
    "mk_llm_provider",
    code,
    (enabled ? "启用" : "停用") + "模型 provider " + code
        + "：" + request.reason().trim());
return ModelProviderGovernanceView.from(saved);
```

`assertActivationConfirmed` 在访问仓储前拒绝未确认或空原因。

- [x] **Step 5: 增加 enable/disable 控制器**

```java
@PostMapping("/{providerCode}/enable")
@PreAuthorize("@perm.has('llm.provider.manage')")
public ApiResult<ModelProviderGovernanceView> enableProvider(
        @PathVariable String providerCode,
        @Valid @RequestBody ModelProviderActivationRequest request) {
    return ApiResult.ok(service.enableProvider(providerCode, request));
}

@PostMapping("/{providerCode}/disable")
@PreAuthorize("@perm.has('llm.provider.manage')")
public ApiResult<ModelProviderGovernanceView> disableProvider(
        @PathVariable String providerCode,
        @Valid @RequestBody ModelProviderActivationRequest request) {
    return ApiResult.ok(service.disableProvider(providerCode, request));
}
```

控制器测试验证临床用户 403、集成运维员 200、缺 request 字段 400。

- [x] **Step 6: 运行启停测试转绿**

Run:

```bash
cd medkernel-backend
mvn -q -Dtest=ModelProviderGovernanceServiceTest,ModelProviderControllerSecurityTest test
```

Expected: PASS。

### Task 5: 合同、文档与完整验证

**Files:**
- Modify: `docs/audit/product-function-catalog.md`（通过导出脚本重生成）
- Modify: `docs/cards/wave2/LLM-08.md`
- Modify: `docs/superpowers/plans/2026-06-16-autonomous-knowledge-production-golive-master-plan.md`
- Modify: `docs/_HANDOFF.md`
- Modify: `docs/superpowers/plans/2026-06-18-model-provider-controlled-activation.md`

- [x] **Step 1: 跑 provider + 迁移 + 合同目标套件**

Run:

```bash
cd medkernel-backend
mvn -q -Dtest=ModelProviderGovernanceServiceTest,ModelProviderControllerSecurityTest,ModelProviderConfigRepositoryTest,ModelProviderRegistryTest,KnowledgeProductionReadinessServiceTest,MigrationBaselineContractTest,H2BaselineMigrationTest,FlywayMultiDialectSmokeTest,ServiceContractGovernanceTest,OpenApiContractConfigurationTest,DomainOwnershipContractTest test
```

Expected: PASS；Docker 不可用时 PostgreSQL/Oracle smoke 只能按测试既有 Assumption 跳过，不得写成通过。

- [x] **Step 2: 同步产品与卡片**

Run:

```bash
node scripts/audit/export-product-capabilities.mjs
```

更新 `LLM-08.md`：

- provider 配置 PUT 只保存停用；
- GET 返回脱敏治理快照；
- health-check 不启用；
- enable/disable 要求 MFA、二次确认、原因和乐观锁；
- 启用要求 HEALTHY、PASSED 评测与部署形态允许。

主计划与 `_HANDOFF.md` 记录该切片只完成安全入口，不代表 134 provider 已启用。

- [x] **Step 3: 后端全量验证**

Run:

```bash
cd medkernel-backend
MEDKERNEL_EVENTS_WORKER_ENABLED=false mvn -q clean test
mvn -q -DskipTests package
```

Expected: 全量测试 0 failures / 0 errors；H2 空库迁移至 V152；生产 JAR 构建成功。

- [x] **Step 4: T-GATE 与差异验证**

Run:

```bash
node --test scripts/authenticity-guard.test.mjs
node --test scripts/config-boundary-guard.test.mjs
node --test scripts/migration-convention-guard.test.mjs
node scripts/authenticity-guard.mjs --mode=changed
node scripts/config-boundary-guard.mjs --mode=changed
node scripts/migration-convention-guard.mjs --mode=changed
node scripts/b0-perfect-check.mjs
bash scripts/check-comment-zh.sh
node scripts/audit/export-product-capabilities.mjs --check
git diff --check
```

Expected: 全部退出 0。

- [x] **Step 5: 自审**

确认：

- 无任何接口返回 `credentialRef` 或真实凭据；
- PUT 无法直接启用 provider；
- enable/disable 不重传或修改连接材料；
- 未关闭 TLS 校验；
- 未自动签署医学评测、翻 P6、创建或激活候选；
- V152 五方言一致且中文 COMMENT 完整；
- 134 状态未被修改。

- [x] **Step 6: 本地提交**

```bash
git add medkernel-backend docs
git commit -m "feat: 增加模型Provider受控启停"
```

Expected: 本地提交成功，不推送远程。
