# SYS-02 服务契约 PR2 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 交付 SYS-02 PR2：OpenAPI 覆盖每个已暴露服务，领域事件 schema 版本化并由契约测试守护，每个服务声明五维权限与审计点，缺声明或裸接口会让 CI 变红。

**Architecture:** 以 `ServiceContractCatalog` 作为服务契约单一目录，OpenAPI 配置、权限声明、审计点声明和测试均消费同一目录。事件契约采用 `DomainEventSchemaCatalog` + `docs/contracts/events/*.json` 双轨：生产目录声明版本与责任域，测试用 JSON schema 锁住 Java record 字段，字段破坏性变更必须显式改 schema。权限治理测试扫描所有 `/api/v1` 控制器，要求除登录、首次部署引导、心跳等公开端点外，每个方法都有可解析的 `@PreAuthorize`。

**Tech Stack:** Java 21、Spring Boot 3.3、Springdoc OpenAPI、JUnit 5、ArchUnit、Jackson JSON Schema-style 文档。

---

### Task 1: 服务契约目录与治理红灯测试

**Files:**
- Create: `medkernel-backend/src/test/java/com/medkernel/architecture/ServiceContractGovernanceTest.java`
- Create: `medkernel-backend/src/test/java/com/medkernel/architecture/OpenApiContractConfigurationTest.java`

- [x] **Step 1: Write the failing service governance tests**

`ServiceContractGovernanceTest` must import all production controllers under `com.medkernel`, scan class-level `@RequestMapping` that starts with `/api/v1`, and assert these rules:

```java
Set<String> actualControllers = apiControllers().stream()
    .map(Class::getName)
    .collect(Collectors.toCollection(TreeSet::new));

Set<String> declaredControllers = ServiceContractCatalog.contracts().stream()
    .map(ServiceContract::controllerClassName)
    .collect(Collectors.toCollection(TreeSet::new));

assertThat(declaredControllers).containsExactlyElementsOf(actualControllers);
```

It must also iterate every mapped method and assert:

```java
String endpointKey = httpMethod + " " + fullPath;
boolean publicEndpoint = contract.isPublicEndpoint(endpointKey);
PreAuthorize auth = AnnotatedElementUtils.findMergedAnnotation(method, PreAuthorize.class);

assertThat(publicEndpoint || auth != null)
    .as(endpointKey + " must declare @PreAuthorize or be explicit public contract")
    .isTrue();
```

For every `@PreAuthorize("@perm.has('code')")`, extract `code` and assert:

```java
assertThat(PermissionCode.fromCode(code))
    .as(endpointKey + " permission code must exist: " + code)
    .isPresent();
assertThat(contract.declaresPermission(code))
    .as(endpointKey + " permission must be declared in ServiceContractCatalog")
    .isTrue();
```

For every non-GET endpoint not marked public, assert the service contract has at least one audit point:

```java
assertThat(contract.auditPoints())
    .as(endpointKey + " mutating service must declare audit points")
    .isNotEmpty();
```

- [x] **Step 2: Write the failing OpenAPI configuration test**

`OpenApiContractConfigurationTest` must assert the OpenAPI configuration consumes catalog paths:

```java
List<String> expected = ServiceContractCatalog.openApiPaths();
GroupedOpenApi api = new OpenApiContractConfiguration().medkernelServiceContractsOpenApi();
assertThat(api).isNotNull();
assertThat(expected).contains("/api/v1/engine/events/**");
assertThat(expected).contains("/api/v1/engine/rules/**");
assertThat(expected).contains("/api/v1/engine/pathways/**");
```

- [x] **Step 3: Run red tests**

Run:

```bash
mvn -B -q -Dtest=ServiceContractGovernanceTest,OpenApiContractConfigurationTest test
```

Expected: FAIL at compilation because `ServiceContractCatalog`, `ServiceContract`, and `OpenApiContractConfiguration` do not exist.

---

### Task 2: 实现服务契约目录、OpenAPI 配置和真实权限缺口

**Files:**
- Create: `medkernel-backend/src/main/java/com/medkernel/engine/contract/ServiceContract.java`
- Create: `medkernel-backend/src/main/java/com/medkernel/engine/contract/ServicePermissionDeclaration.java`
- Create: `medkernel-backend/src/main/java/com/medkernel/engine/contract/ServiceAuditDeclaration.java`
- Create: `medkernel-backend/src/main/java/com/medkernel/engine/contract/ServiceContractCatalog.java`
- Create: `medkernel-backend/src/main/java/com/medkernel/engine/contract/OpenApiContractConfiguration.java`
- Modify: `medkernel-backend/src/main/java/com/medkernel/engine/security/PermissionCode.java`
- Modify: `medkernel-backend/src/main/java/com/medkernel/engine/security/DefaultPermissionPolicy.java`
- Modify: `medkernel-backend/src/main/java/com/medkernel/engine/integration/controller/IntegrationController.java`
- Modify: `medkernel-backend/src/main/java/com/medkernel/engine/security/auth/AuthController.java`
- Modify: `medkernel-backend/src/main/java/com/medkernel/engine/security/bootstrap/BootstrapController.java`

- [x] **Step 1: Add contract records**

Create records with immutable lists and exact endpoint key matching:

```java
public record ServiceContract(
    String id,
    String title,
    String controllerClassName,
    String basePath,
    List<String> openApiPaths,
    List<ServicePermissionDeclaration> permissions,
    List<ServiceAuditDeclaration> auditPoints,
    List<String> publicEndpoints
) {
    public boolean declaresPermission(String code) {
        return permissions.stream().anyMatch(p -> p.code().equalsIgnoreCase(code));
    }

    public boolean isPublicEndpoint(String endpointKey) {
        return publicEndpoints.stream().anyMatch(p -> p.equalsIgnoreCase(endpointKey));
    }
}
```

```java
public record ServicePermissionDeclaration(String code, PermissionDimension dimension, String purpose) {}
public record ServiceAuditDeclaration(AuditAction action, String targetType, String purpose) {}
```

- [x] **Step 2: Add catalog entries**

`ServiceContractCatalog.contracts()` must declare these controllers exactly once:

`AuditController`, `EvidenceController`, `ClinicalEventController`, `ContextSnapshotController`, `EmbedEngineController`, `EvaluationEngineController`, `SavedViewController`, `ThemePreferenceController`, `FollowupEngineController`, `IntegrationController`, `KnowledgeExportController`, `KnowledgeIdentityController`, `KnowledgeVersionController`, `LargeListController`, `ModelGatewayController`, `MpiController`, `OrgUnitController`, `PathwayEngineController`, `PackageEngineController`, `RecommendationEngineController`, `RuleEngineController`, `SecurityMeController`, `UserRoleAssignmentController`, `AuthController`, `CredentialAdminController`, `TenantProvisioningController`, `BootstrapController`, `BrandingController`, `SuccessController`, `TerminologyController`, `SystemConfigController`, `ObservabilityDiagnoseController`, `RuntimeOperationsController`, `HealthController`, `RuntimeProbeController`.

Public endpoints must be explicit:

```text
POST /api/v1/auth/login
POST /api/v1/auth/logout
POST /api/v1/bootstrap/init-token
POST /api/v1/bootstrap/password
GET /api/v1/system/ping
```

- [x] **Step 3: Add OpenAPI group**

`OpenApiContractConfiguration` must expose one grouped OpenAPI document named `medkernel-service-contracts` using all catalog paths:

```java
@Configuration
public class OpenApiContractConfiguration {
    @Bean
    public GroupedOpenApi medkernelServiceContractsOpenApi() {
        List<String> paths = ServiceContractCatalog.openApiPaths();
        return GroupedOpenApi.builder()
            .group("medkernel-service-contracts")
            .pathsToMatch(paths.toArray(String[]::new))
            .build();
    }
}
```

- [x] **Step 4: Fix real permission gaps**

Add permission codes:

```java
INTEGRATION_READ("integration.read", Risk.LOW, "查看第三方适配器、Webhook 和集成日志"),
INTEGRATION_WRITE("integration.write", Risk.MEDIUM, "创建或修改第三方适配器与 Webhook"),
INTEGRATION_EXECUTE("integration.execute", Risk.MEDIUM, "执行适配器连通性自检、Webhook 测试和死信重试"),
MPI_READ("mpi.read", Risk.LOW, "查看患者主索引列表与统计"),
MPI_WRITE("mpi.write", Risk.HIGH, "合并患者主索引"),
```

Grant integration permissions to platform, group, hospital admin, IT ops, implementation engineer, and audit read-only where appropriate through `DefaultPermissionPolicy`. Grant MPI read/write according to clinical roles: doctors/nurses/dept heads read, hospital admin and medical affairs write.

Add `@PreAuthorize` to every `IntegrationController` endpoint:

```java
@PreAuthorize("@perm.has('integration.read')")
@PreAuthorize("@perm.has('integration.write')")
@PreAuthorize("@perm.has('integration.execute')")
```

Add authenticated declarations to `AuthController.changePassword` and `BootstrapController.bindMfa`:

```java
@PreAuthorize("isAuthenticated()")
```

- [x] **Step 5: Run green service contract tests**

Run:

```bash
mvn -B -q -Dtest=ServiceContractGovernanceTest,OpenApiContractConfigurationTest test
```

Expected: PASS.

---

### Task 3: 领域事件 schema 契约与破坏性变更门禁

**Files:**
- Create: `medkernel-backend/src/main/java/com/medkernel/engine/contract/DomainEventSchema.java`
- Create: `medkernel-backend/src/main/java/com/medkernel/engine/contract/DomainEventSchemaCatalog.java`
- Create: `medkernel-backend/src/test/java/com/medkernel/architecture/DomainEventSchemaContractTest.java`
- Create: `docs/contracts/events/clinical-event.v1.json`
- Create: `docs/contracts/events/clinical-event-processed.v1.json`
- Create: `docs/contracts/events/followup-event.v1.json`
- Create: `docs/contracts/events/shared-audit-event.v1.json`
- Create: `docs/contracts/events/compliance-audit-event.v1.json`

- [x] **Step 1: Write failing event schema tests**

`DomainEventSchemaContractTest` must scan all production records whose simple name ends with `Event`:

```java
Set<String> actual = productionRecordsEndingWithEvent();
Set<String> declared = DomainEventSchemaCatalog.schemas().stream()
    .map(DomainEventSchema::recordClassName)
    .collect(Collectors.toCollection(TreeSet::new));
assertThat(declared).containsExactlyElementsOf(actual);
```

It must parse each JSON file and compare required fields with record components:

```java
List<String> recordFields = Arrays.stream(eventClass.getRecordComponents())
    .map(RecordComponent::getName)
    .toList();
assertThat(jsonRequiredFields(schema.contractFile()))
    .containsExactlyElementsOf(recordFields);
assertThat(schema.version()).isGreaterThanOrEqualTo(1);
assertThat(schema.schemaId()).endsWith(".v" + schema.version());
```

- [x] **Step 2: Run red test**

Run:

```bash
mvn -B -q -Dtest=DomainEventSchemaContractTest test
```

Expected: FAIL at compilation because `DomainEventSchema` and `DomainEventSchemaCatalog` do not exist.

- [x] **Step 3: Implement schema catalog and JSON contracts**

Add five schema entries:

```java
schema("clinical-event.v1", 1, "com.medkernel.engine.context.ClinicalEvent", "docs/contracts/events/clinical-event.v1.json")
schema("clinical-event-processed.v1", 1, "com.medkernel.engine.context.ClinicalEventProcessedEvent", "docs/contracts/events/clinical-event-processed.v1.json")
schema("followup-event.v1", 1, "com.medkernel.engine.followup.FollowupEvent", "docs/contracts/events/followup-event.v1.json")
schema("shared-audit-event.v1", 1, "com.medkernel.shared.audit.AuditEvent", "docs/contracts/events/shared-audit-event.v1.json")
schema("compliance-audit-event.v1", 1, "com.medkernel.compliance.audit.AuditEvent", "docs/contracts/events/compliance-audit-event.v1.json")
```

Each JSON contract must include `$schema`, `$id`, `title`, `type: object`, `x-medkernel-version`, `required`, and `properties`. The `required` array must match the Java record component order exactly.

- [x] **Step 4: Run green event contract tests**

Run:

```bash
mvn -B -q -Dtest=DomainEventSchemaContractTest test
```

Expected: PASS.

---

### Task 4: 文档、验收、提交和合并

**Files:**
- Modify: `docs/cards/D0/SYS-02.md`
- Modify: `docs/backlog.md`
- Modify: `docs/_HANDOFF.md`

- [x] **Step 1: Update SYS-02 card**

Check the remaining items:

```markdown
- [x] FR-2 OpenAPI 契约
- [x] FR-3 事件契约
- [x] FR-4 权限审计要求
- [x] FR-6 契约测试
- [x] AC-2（FR-2）
- [x] AC-3（FR-3/6）
- [x] AC-4（FR-4）
```

Add PR2 evidence for `ServiceContractCatalog`, `OpenApiContractConfiguration`, `DomainEventSchemaCatalog`, event JSON contracts, and governance tests.

- [x] **Step 2: Mark SYS-02 done after verification**

In `docs/backlog.md`, mark `SYS-02` as `done` only after all tests and T-GATE pass.

- [x] **Step 3: Update handoff**

Archive SYS-02 PR1, set SYS-02 PR2 as current while PR is open, and after merge replace it with the next pending D0 card line.

- [x] **Step 4: Run final verification**

Run:

```bash
mvn -B -q -Dtest=ModuleBoundaryArchTest,DomainOwnershipContractTest,ServiceContractGovernanceTest,OpenApiContractConfigurationTest,DomainEventSchemaContractTest test
mvn -B -q test
node scripts/authenticity-guard.mjs --mode=changed --base=origin/main
node scripts/config-boundary-guard.mjs --mode=changed --base=origin/main
node scripts/migration-convention-guard.mjs --mode=changed --base=origin/main
scripts/check-comment-zh.sh
git diff --check
```

Expected: all pass. If a guard reports a non-current-stage external issue, register it in `docs/audit/deferred-issues.md` and continue only when it does not touch current card main path, login usability, permission isolation, authenticity gate, or medical safety.

Result: PASS. 聚焦架构测试、后端全量 `mvn -B -q test`、真实性门禁、配置边界门禁、迁移规约门禁、中文注释门禁和空白检查均已通过。

- [ ] **Step 5: Commit, PR, CI, merge**

Commit message:

```bash
git commit -m "完成 SYS-02 服务契约与事件 schema PR2"
```

Create PR, wait for remote CI 8/8 green, squash merge, confirm `origin/main` contains the merge commit, delete branch/worktree, then continue to the next pending D0 card.
