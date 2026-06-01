# SYS-02 服务边界 PR1 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 交付 SYS-02 PR1：用可执行测试锁住引擎核心 / 业务包单向依赖、包级无环、领域实体单一 owner、跨模块直写他域表拒绝。

**Architecture:** 新增 test scope 的 ArchUnit 依赖，所有架构规则落在后端测试内，CI 跑后端测试即可阻断退化。领域所有权用一个小型生产契约目录 `DomainOwnershipCatalog` 表达，测试扫描 `@Table` 与源码 SQL 写操作，确保每张表只有一个 owner，且直写只能发生在 owner 包内。

**Tech Stack:** Java 21、Spring Boot 3.3、JUnit 5、ArchUnit、Spring Data JDBC `@Table`。

---

### Task 1: ArchUnit 模块边界与包级无环

**Files:**
- Modify: `medkernel-backend/pom.xml`
- Create: `medkernel-backend/src/test/java/com/medkernel/architecture/ModuleBoundaryArchTest.java`

- [x] **Step 1: Write the failing test**

Create `ModuleBoundaryArchTest` with these rules:

```java
package com.medkernel.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.library.dependencies.SlicesRuleDefinition;
import org.junit.jupiter.api.Test;

class ModuleBoundaryArchTest {
    private static final String ROOT_PACKAGE = "com.medkernel..";
    private static final String ENGINE_PACKAGE = "com.medkernel.engine..";
    private static final String SHARED_PACKAGE = "com.medkernel.shared..";
    private static final String BUSINESS_PACKAGE = "com.medkernel.compliance..";

    private final com.tngtech.archunit.core.domain.JavaClasses classes =
        new ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
            .importPackages(ROOT_PACKAGE);

    @Test
    void engineAndSharedMustNotDependOnBusinessPackages() {
        noClasses()
            .that().resideInAnyPackage(ENGINE_PACKAGE, SHARED_PACKAGE)
            .should().dependOnClassesThat().resideInAnyPackage(BUSINESS_PACKAGE)
            .because("SYS-02 要求依赖方向只能是业务包 -> 引擎核心 / shared")
            .check(classes);
    }

    @Test
    void sharedKernelMustNotDependOnEnginePackages() {
        noClasses()
            .that().resideInAPackage(SHARED_PACKAGE)
            .should().dependOnClassesThat().resideInAPackage(ENGINE_PACKAGE)
            .because("shared 是最底层公共契约，不能反向依赖 engine")
            .check(classes);
    }

    @Test
    void topLevelMedkernelPackagesMustBeFreeOfCycles() {
        SlicesRuleDefinition.slices()
            .matching("com.medkernel.(*)..")
            .should().beFreeOfCycles()
            .check(classes);
    }
}
```

- [x] **Step 2: Run test to verify it fails**

Run: `mvn -B -q -Dtest=ModuleBoundaryArchTest test`

Expected: FAIL at compilation because `com.tngtech.archunit` is not on the test classpath.

- [x] **Step 3: Add minimal ArchUnit dependency**

Add to `medkernel-backend/pom.xml` properties and test dependencies:

```xml
<archunit.version>1.3.0</archunit.version>
```

```xml
<dependency>
    <groupId>com.tngtech.archunit</groupId>
    <artifactId>archunit-junit5</artifactId>
    <version>${archunit.version}</version>
    <scope>test</scope>
</dependency>
```

- [x] **Step 4: Run test to verify it passes**

Run: `mvn -B -q -Dtest=ModuleBoundaryArchTest test`

Expected: PASS. If a real cycle or forbidden dependency is found, remove the dependency only inside the current SYS-02 PR1 boundary; do not suppress the rule.

---

### Task 2: 领域所有权目录与 `@Table` 单一 owner

**Files:**
- Create: `medkernel-backend/src/main/java/com/medkernel/shared/architecture/DomainModule.java`
- Create: `medkernel-backend/src/main/java/com/medkernel/shared/architecture/DomainOwnershipCatalog.java`
- Create: `medkernel-backend/src/test/java/com/medkernel/architecture/DomainOwnershipContractTest.java`

- [x] **Step 1: Write the failing tests**

Create `DomainOwnershipContractTest` that imports production classes, finds each Spring Data JDBC `@Table`, resolves it through `DomainOwnershipCatalog.ownerOfTable(tableName)`, and asserts:

```java
assertThat(owner).as("table owner for " + tableName).isPresent();
assertThat(owner.get().ownsPackage(clazz.getPackageName()))
    .as(clazz.getName() + " must live under owner package " + owner.get().id())
    .isTrue();
```

Also add a catalog uniqueness assertion:

```java
assertThat(DomainOwnershipCatalog.tablePrefixes())
    .doesNotHaveDuplicates();
```

- [x] **Step 2: Run test to verify it fails**

Run: `mvn -B -q -Dtest=DomainOwnershipContractTest test`

Expected: FAIL at compilation because `DomainModule` and `DomainOwnershipCatalog` do not exist.

- [x] **Step 3: Add minimal ownership catalog**

Implement `DomainModule` as a Java record with `id`, `ownedPackages`, `tablePrefixes`, `tableNames` and methods `ownsPackage(String)` / `ownsTable(String)`.

Implement `DomainOwnershipCatalog` with explicit modules:

```java
shared-audit -> com.medkernel.shared.audit.. -> audit_event, audit_chain_head
shared-config -> com.medkernel.shared.config.. -> mk_config_
shared-idempotency -> com.medkernel.shared.idempotency.. -> sys_idempotency
shared-observability -> com.medkernel.shared.observability.. -> mk_obs_
engine-security -> com.medkernel.engine.security.. -> sys_, role_permission, user_role_assignment, platform_credential, emergency_permission_grant, mk_security_
engine-org -> com.medkernel.engine.org.. -> org_
engine-context -> com.medkernel.engine.context.. -> context_, clinical_event, canonical_resource
engine-clinical -> com.medkernel.engine.clinical.. -> mk_clinical_
engine-rule -> com.medkernel.engine.rule.. -> rule_
engine-pathway -> com.medkernel.engine.pathway.. -> pathway_, specialty_, patient_pathway, clinical_clock
engine-knowledge -> com.medkernel.engine.knowledge.. -> knowledge_, source_, citation
engine-package -> com.medkernel.engine.pkg.. -> knowledge_package, package_item, release_plan, sync_target, sync_log
engine-evaluation -> com.medkernel.engine.evaluation.. -> evaluation_, quality_finding, rectification_
engine-terminology -> com.medkernel.engine.terminology.. -> term_, standard_term, local_term, mapping_
engine-experience -> com.medkernel.engine.experience.. + com.medkernel.engine.list.. -> mk_experience_
engine-followup -> com.medkernel.engine.followup.. -> followup_
engine-integration -> com.medkernel.engine.integration.. -> integration_
engine-mpi -> com.medkernel.engine.mpi.. -> mpi_
engine-recommendation -> com.medkernel.engine.recommendation.. -> recommendation_
engine-llm -> com.medkernel.engine.llm.. -> model_capability_
engine-embed -> com.medkernel.engine.embed.. -> embed_
engine-tenant -> com.medkernel.engine.tenant.. -> tenant_
compliance-evidence -> com.medkernel.compliance.evidence.. -> evidence_
```

- [x] **Step 4: Run test to verify it passes**

Run: `mvn -B -q -Dtest=DomainOwnershipContractTest test`

Expected: PASS. If any `@Table` is unmapped, add it to the owner that actually owns the table; do not broaden ownership with vague catch-all prefixes.

---

### Task 3: Cross-module direct write guard

**Files:**
- Modify: `medkernel-backend/src/test/java/com/medkernel/architecture/DomainOwnershipContractTest.java`

- [x] **Step 1: Write the failing source-scan test**

Add a test that scans `src/main/java` files, detects SQL write statements matching:

```java
Pattern.compile("\\b(?:INSERT\\s+INTO|UPDATE|DELETE\\s+FROM)\\s+([a-zA-Z_][a-zA-Z0-9_]*)", Pattern.CASE_INSENSITIVE)
```

For every detected table, resolve the owner and assert the Java file package is under the owner package. Also allow no owner only for SQL keywords inside comments? No: strip Java block comments and line comments before matching.

- [x] **Step 2: Run test to verify it fails**

Run: `mvn -B -q -Dtest=DomainOwnershipContractTest#directSqlWritesMustStayInsideOwningModule test`

Expected: FAIL if catalog is not yet wired for every write table, otherwise PASS after confirming the test inspects at least one SQL write. The test must assert `checkedWrites > 0` so it cannot become vacuous.

- [x] **Step 3: Fix catalog or code for real violations**

If a write is in the wrong module, move it behind the owning repository/service contract. If only the owner catalog is missing a table that is truly owned by that package, add the explicit table name/prefix.

- [x] **Step 4: Run focused architecture tests**

Run: `mvn -B -q -Dtest=ModuleBoundaryArchTest,DomainOwnershipContractTest test`

Expected: PASS.

---

### Task 4: Docs, handoff, and verification

**Files:**
- Modify: `docs/cards/D0/SYS-02.md`
- Modify: `docs/_HANDOFF.md`

- [x] **Step 1: Update SYS-02 card**

Check only PR1-covered items:

```markdown
- [x] FR-1 ...
- [x] FR-5 ...
- [x] AC-1 ...
- [x] AC-5 ...
```

Add evidence section with links to `ModuleBoundaryArchTest`, `DomainOwnershipCatalog`, and `DomainOwnershipContractTest`. Leave FR-2/3/4/6 and AC-2/3/4 unchecked for PR2.

- [x] **Step 2: Update handoff**

Move SYS-01 PR3 to archived lines with PR #221 / merge `ebea3f0`, and add SYS-02 PR1 as current in-flight line.

- [x] **Step 3: Run final local verification**

Run:

```bash
mvn -B -q -Dtest=ModuleBoundaryArchTest,DomainOwnershipContractTest test
mvn -B -q test
node scripts/authenticity-guard.mjs --mode=changed --base=origin/main
node scripts/config-boundary-guard.mjs --mode=changed --base=origin/main
node scripts/migration-convention-guard.mjs --mode=changed --base=origin/main
scripts/check-comment-zh.sh
git diff --check origin/main...HEAD
```

Expected: all pass. If a guard reports an external/non-current-stage issue, register it in `docs/audit/deferred-issues.md` with a new `DEFER-XXX` and continue only if it does not touch current card main path, login usability, permission isolation, authenticity gate, or medical safety.

Result: PASS. Focused architecture tests, related regression tests, full backend `mvn -B -q test`, pre-commit T-GATE, and uncommitted whitespace check completed successfully. Changed-mode guards must be rerun after commit so they scan the committed diff against `origin/main`.

- [ ] **Step 4: Commit, PR, CI, merge**

Commit message: `完成 SYS-02 模块边界与领域所有权 PR1`

Create PR, wait for remote CI 8/8 green, merge, confirm `origin/main` contains merge commit, delete remote/local branch and worktree, then continue to SYS-02 PR2.
