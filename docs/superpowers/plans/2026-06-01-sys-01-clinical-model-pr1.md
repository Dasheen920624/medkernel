# SYS-01 Clinical Model PR1 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 交付 SYS-01 PR1：12 类标准临床对象、关系库权威表、组织/审计/敏感字段基础，并清理旧 `SYMPTOM` 口径。

**Architecture:** 保留现有 `context_snapshot` / `canonical_resource` 作为事件快照与诊断追踪视角；新增 `com.medkernel.engine.clinical.model` 作为 12 类标准对象的关系库权威模型。新表统一使用 BASE-05 门禁要求的 `mk_clinical_*` 命名；旧 SYS-01 卡中的裸表名同步改为门禁合规表名，避免后续 AI 继续沿用旧命名。

**Tech Stack:** Java 21、Spring Data JDBC、Flyway 五方言迁移、JUnit 5、AssertJ、H2 Flyway smoke、现有 T-GATE 脚本。

---

## Scope

本计划只做 SYS-01 大卡工序 PR1：

- 12 类对象：Patient / Encounter / Condition / Observation / Medication / Procedure / DiagnosticReport / Document / NursingAssessment / CarePlan / FollowUp / Claim。
- 新增 12 张关系库权威表，全部带 `tenant_id`、`org_path`、`source_system`、`source_id`、`fhir_resource_id`、`created_at/by`、`updated_at/by`、`trace_id`。
- Patient 敏感字段只落密文与掩码：`name_cipher` / `name_mask`、`identity_no_cipher` / `identity_no_mask`、`phone_cipher` / `phone_mask`，不新增明文姓名 / 证件 / 手机列。
- 清理旧 `CanonicalSymptom` / `SYMPTOM` 口径，改为 `CanonicalNursingAssessment` / `NURSING_ASSESSMENT`。
- 暂不实现 PR2 的 `ClinicalEventContext` 三引擎入口，也不实现 PR3 的 FHIR 门面和图投影解耦。

## Files

- Create: `medkernel-backend/src/main/java/com/medkernel/engine/clinical/model/ClinicalPatient.java`
- Create: `medkernel-backend/src/main/java/com/medkernel/engine/clinical/model/ClinicalEncounter.java`
- Create: `medkernel-backend/src/main/java/com/medkernel/engine/clinical/model/ClinicalCondition.java`
- Create: `medkernel-backend/src/main/java/com/medkernel/engine/clinical/model/ClinicalObservation.java`
- Create: `medkernel-backend/src/main/java/com/medkernel/engine/clinical/model/ClinicalMedication.java`
- Create: `medkernel-backend/src/main/java/com/medkernel/engine/clinical/model/ClinicalProcedure.java`
- Create: `medkernel-backend/src/main/java/com/medkernel/engine/clinical/model/ClinicalDiagnosticReport.java`
- Create: `medkernel-backend/src/main/java/com/medkernel/engine/clinical/model/ClinicalDocument.java`
- Create: `medkernel-backend/src/main/java/com/medkernel/engine/clinical/model/ClinicalNursingAssessment.java`
- Create: `medkernel-backend/src/main/java/com/medkernel/engine/clinical/model/ClinicalCarePlan.java`
- Create: `medkernel-backend/src/main/java/com/medkernel/engine/clinical/model/ClinicalFollowUp.java`
- Create: `medkernel-backend/src/main/java/com/medkernel/engine/clinical/model/ClinicalClaim.java`
- Create: `medkernel-backend/src/main/java/com/medkernel/engine/clinical/model/*Repository.java`
- Create: `medkernel-backend/src/test/java/com/medkernel/engine/clinical/model/StandardClinicalModelRepositoryTest.java`
- Create: `medkernel-backend/src/test/java/com/medkernel/engine/clinical/model/StandardClinicalModelContractTest.java`
- Create: `medkernel-backend/src/main/resources/db/migration/{h2,postgres,oracle,dm,kingbase}/V38__standard_clinical_model.sql`
- Modify: `medkernel-backend/src/main/java/com/medkernel/engine/context/canonical/CanonicalSymptom.java` -> move/replace with `CanonicalNursingAssessment.java`
- Modify: `medkernel-backend/src/main/java/com/medkernel/engine/context/ContextSnapshotResources.java`
- Modify: `medkernel-backend/src/main/java/com/medkernel/engine/context/CanonicalResourceType.java`
- Modify: `medkernel-backend/src/main/java/com/medkernel/engine/context/ContextSnapshotService.java`
- Modify: `medkernel-backend/src/main/java/com/medkernel/engine/context/ContextValidator.java`
- Modify: `medkernel-backend/src/test/java/com/medkernel/engine/context/*`
- Modify: `medkernel-backend/src/test/java/com/medkernel/migration/MigrationBaselineContractTest.java`
- Modify: `medkernel-backend/src/test/java/com/medkernel/migration/H2BaselineMigrationTest.java`
- Modify: `docs/cards/D0/SYS-01.md`
- Modify: `docs/_HANDOFF.md`

---

### Task 1: Red Tests For 12 Object Contract

**Files:**
- Create: `medkernel-backend/src/test/java/com/medkernel/engine/clinical/model/StandardClinicalModelContractTest.java`
- Modify: `medkernel-backend/src/test/java/com/medkernel/engine/context/canonical/CanonicalDtoValidationTest.java`

- [x] **Step 1: Write failing contract tests**

```java
class StandardClinicalModelContractTest {
    private static final Set<String> EXPECTED_TABLES = Set.of(
        "mk_clinical_patient", "mk_clinical_encounter", "mk_clinical_condition",
        "mk_clinical_observation", "mk_clinical_medication", "mk_clinical_procedure",
        "mk_clinical_diagnostic_report", "mk_clinical_document",
        "mk_clinical_nursing_assessment", "mk_clinical_care_plan",
        "mk_clinical_follow_up", "mk_clinical_claim"
    );

    @Test
    void clinicalModelHasExactlySys01TwelveObjectTables() {
        assertThat(StandardClinicalTables.names()).containsExactlyInAnyOrderElementsOf(EXPECTED_TABLES);
    }

    @Test
    void patientModelDoesNotExposePlainSensitiveFields() {
        assertThat(ClinicalPatient.class.getRecordComponents())
            .extracting(RecordComponent::getName)
            .doesNotContain("name", "identityNo", "phone")
            .contains("nameCipher", "nameMask", "identityNoCipher", "identityNoMask", "phoneCipher", "phoneMask");
    }

    @Test
    void oldSymptomCanonicalResourceIsNotPartOfSys01() {
        assertThat(CanonicalResourceType.values())
            .extracting(Enum::name)
            .doesNotContain("SYMPTOM")
            .contains("NURSING_ASSESSMENT");
    }
}
```

- [x] **Step 2: Run red tests**

Run from `medkernel-backend`:

```bash
mvn -B -q -Dtest=StandardClinicalModelContractTest,CanonicalDtoValidationTest test
```

Expected: FAIL because `com.medkernel.engine.clinical.model` does not exist and `CanonicalResourceType` still contains `SYMPTOM`.

---

### Task 2: Red Tests For Persistence And Tenant Isolation

**Files:**
- Create: `medkernel-backend/src/test/java/com/medkernel/engine/clinical/model/StandardClinicalModelRepositoryTest.java`

- [x] **Step 1: Write failing repository tests**

```java
@DataJdbcTest
@ImportAutoConfiguration(FlywayAutoConfiguration.class)
@AutoConfigureTestDatabase(replace = Replace.NONE)
@TestPropertySource(properties = {
    "spring.datasource.url=jdbc:h2:mem:standard-clinical-${random.uuid};MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;CASE_INSENSITIVE_IDENTIFIERS=TRUE;DB_CLOSE_DELAY=-1",
    "spring.datasource.driver-class-name=org.h2.Driver",
    "spring.datasource.username=sa",
    "spring.datasource.password=",
    "spring.flyway.enabled=true",
    "spring.flyway.locations=classpath:db/migration/h2"
})
class StandardClinicalModelRepositoryTest {
    @Autowired ClinicalPatientRepository patients;
    @Autowired ClinicalEncounterRepository encounters;
    @Autowired ClinicalConditionRepository conditions;
    @Autowired ClinicalObservationRepository observations;
    @Autowired ClinicalMedicationRepository medications;
    @Autowired ClinicalProcedureRepository procedures;
    @Autowired ClinicalDiagnosticReportRepository diagnosticReports;
    @Autowired ClinicalDocumentRepository documents;
    @Autowired ClinicalNursingAssessmentRepository nursingAssessments;
    @Autowired ClinicalCarePlanRepository carePlans;
    @Autowired ClinicalFollowUpRepository followUps;
    @Autowired ClinicalClaimRepository claims;

    @Test
    void persistsAndReadsTwelveObjectsWithinTenant() {
        ClinicalPatient patient = patients.save(Fixtures.patient("tenant-A", "cp-1", "SRC-1"));
        encounters.save(Fixtures.encounter("tenant-A", "ce-1", patient.patientId()));
        conditions.save(Fixtures.condition("tenant-A", "cc-1", patient.patientId()));
        observations.save(Fixtures.observation("tenant-A", "co-1", patient.patientId()));
        medications.save(Fixtures.medication("tenant-A", "cm-1", patient.patientId()));
        procedures.save(Fixtures.procedure("tenant-A", "cpr-1", patient.patientId()));
        diagnosticReports.save(Fixtures.report("tenant-A", "cdr-1", patient.patientId()));
        documents.save(Fixtures.document("tenant-A", "cdoc-1", patient.patientId()));
        nursingAssessments.save(Fixtures.nursingAssessment("tenant-A", "cna-1", patient.patientId()));
        carePlans.save(Fixtures.carePlan("tenant-A", "ccp-1", patient.patientId()));
        followUps.save(Fixtures.followUp("tenant-A", "cfu-1", patient.patientId()));
        claims.save(Fixtures.claim("tenant-A", "ccl-1", patient.patientId()));

        assertThat(patients.findByTenantIdAndPatientId("tenant-A", "cp-1")).contains(patient);
        assertThat(encounters.findByTenantIdAndPatientId("tenant-A", patient.patientId())).hasSize(1);
        assertThat(claims.findByTenantIdAndPatientId("tenant-A", patient.patientId())).hasSize(1);
    }

    @Test
    void patientLookupNeverCrossesTenant() {
        patients.save(Fixtures.patient("tenant-A", "cp-1", "SRC-1"));
        patients.save(Fixtures.patient("tenant-B", "cp-2", "SRC-1"));

        assertThat(patients.findByTenantIdAndSourceSystemAndSourceId("tenant-A", "HIS", "SRC-1"))
            .extracting(ClinicalPatient::tenantId)
            .contains("tenant-A");
        assertThat(patients.findByTenantIdAndPatientId("tenant-B", "cp-1")).isEmpty();
    }
}
```

- [x] **Step 2: Run red tests**

```bash
mvn -B -q -Dtest=StandardClinicalModelRepositoryTest test
```

Expected: FAIL because repositories, entities, and V38 migration are missing.

---

### Task 3: Implement 12 Standard Object Records And Repositories

**Files:**
- Create: `medkernel-backend/src/main/java/com/medkernel/engine/clinical/model/*.java`

- [x] **Step 1: Add model records**

Use this common field policy in every record:

```java
@NotBlank String tenantId,
@NotBlank String orgPath,
@NotBlank String sourceSystem,
@NotBlank String sourceId,
String fhirResourceId,
Instant createdAt,
@NotBlank String createdBy,
Instant updatedAt,
@NotBlank String updatedBy,
String traceId
```

Use these required object IDs and code fields:

```text
ClinicalPatient.patientId
ClinicalEncounter.encounterId + patientId + encounterClass/status
ClinicalCondition.conditionId + patientId + code/codeSystem/displayName
ClinicalObservation.observationId + patientId + code/codeSystem/displayName
ClinicalMedication.medicationId + patientId + code/codeSystem/displayName
ClinicalProcedure.procedureId + patientId + code/codeSystem/displayName
ClinicalDiagnosticReport.reportId + patientId + reportType/status
ClinicalDocument.documentId + patientId + documentType/status
ClinicalNursingAssessment.assessmentId + patientId + assessmentType/status
ClinicalCarePlan.carePlanId + patientId + status
ClinicalFollowUp.followUpId + patientId + planType/status
ClinicalClaim.claimId + patientId + claimType/status
```

- [x] **Step 2: Add repositories**

Each repository extends `ListCrudRepository<对应实体, String>` and must expose tenant-scoped methods only, for example:

```java
Optional<ClinicalPatient> findByTenantIdAndPatientId(String tenantId, String patientId);
Optional<ClinicalPatient> findByTenantIdAndSourceSystemAndSourceId(String tenantId, String sourceSystem, String sourceId);
List<ClinicalEncounter> findByTenantIdAndPatientId(String tenantId, String patientId);
```

Do not add unscoped `findByPatientId` or `findBySourceId` methods.

- [x] **Step 3: Run green tests for Task 1 records**

```bash
mvn -B -q -Dtest=StandardClinicalModelContractTest test
```

Expected: PASS after records and repositories compile, except persistence tests still fail until V38 exists.

---

### Task 4: Add V38 Five-Dialect Migrations

**Files:**
- Create: `medkernel-backend/src/main/resources/db/migration/h2/V38__standard_clinical_model.sql`
- Create: `medkernel-backend/src/main/resources/db/migration/postgres/V38__standard_clinical_model.sql`
- Create: `medkernel-backend/src/main/resources/db/migration/oracle/V38__standard_clinical_model.sql`
- Create: `medkernel-backend/src/main/resources/db/migration/dm/V38__standard_clinical_model.sql`
- Create: `medkernel-backend/src/main/resources/db/migration/kingbase/V38__standard_clinical_model.sql`

- [x] **Step 1: Add 12 authority tables**

Every table must have:

```sql
tenant_id, org_path, source_system, source_id, fhir_resource_id,
created_at, created_by, updated_at, updated_by, trace_id
```

Every table must define:

```sql
CONSTRAINT uk_<table>_source UNIQUE (tenant_id, source_system, source_id)
CREATE INDEX idx_<table>_org_path ON <table> (tenant_id, org_path)
```

Patient table additionally defines:

```sql
name_cipher, name_mask, identity_no_cipher, identity_no_mask, phone_cipher, phone_mask
```

and must not define cleartext `name`, `identity_no`, or `phone`. Non-Patient tables additionally define:

```sql
CREATE INDEX idx_<table>_patient ON <table> (tenant_id, patient_id)
```

Do not add a duplicate patient source index on `(tenant_id, source_system, source_id)`; the unique constraint already provides that access path and Oracle rejects the duplicate column-list index.

- [x] **Step 2: Update migration contract tests**

Update:

```java
EXPECTED_MIGRATIONS.add("V38__standard_clinical_model.sql")
REQUIRED_TABLES.addAll(Set.of(
    "mk_clinical_patient", "mk_clinical_encounter", "mk_clinical_condition",
    "mk_clinical_observation", "mk_clinical_medication", "mk_clinical_procedure",
    "mk_clinical_diagnostic_report", "mk_clinical_document",
    "mk_clinical_nursing_assessment", "mk_clinical_care_plan",
    "mk_clinical_follow_up", "mk_clinical_claim"))
REQUIRED_INDEXES.addAll(Set.of(
    "idx_mk_clinical_patient_org_path",
    "idx_mk_clinical_encounter_patient", "idx_mk_clinical_condition_patient",
    "idx_mk_clinical_observation_patient", "idx_mk_clinical_medication_patient",
    "idx_mk_clinical_procedure_patient", "idx_mk_clinical_diagnostic_report_patient",
    "idx_mk_clinical_document_patient", "idx_mk_clinical_nursing_assessment_patient",
    "idx_mk_clinical_care_plan_patient", "idx_mk_clinical_follow_up_patient",
    "idx_mk_clinical_claim_patient"))
COMMON_CONSTRAINTS.addAll(Set.of(
    "uk_mk_clinical_patient_source", "uk_mk_clinical_encounter_source",
    "uk_mk_clinical_condition_source", "uk_mk_clinical_observation_source",
    "uk_mk_clinical_medication_source", "uk_mk_clinical_procedure_source",
    "uk_mk_clinical_diagnostic_report_source", "uk_mk_clinical_document_source",
    "uk_mk_clinical_nursing_assessment_source", "uk_mk_clinical_care_plan_source",
    "uk_mk_clinical_follow_up_source", "uk_mk_clinical_claim_source"))
```

Update H2 expected migration count and versions from 37 to 38.

- [x] **Step 3: Run migration red/green**

```bash
mvn -B -q -Dtest=MigrationBaselineContractTest,H2BaselineMigrationTest test
node scripts/migration-convention-guard.mjs --mode=changed --base=origin/main
```

Expected: PASS. If guard fails, fix naming/comments/indexes rather than weakening the guard.

---

### Task 5: Replace Old Symptom Snapshot Type With NursingAssessment

**Files:**
- Delete: `medkernel-backend/src/main/java/com/medkernel/engine/context/canonical/CanonicalSymptom.java`
- Create: `medkernel-backend/src/main/java/com/medkernel/engine/context/canonical/CanonicalNursingAssessment.java`
- Modify: `ContextSnapshotResources`, `CanonicalResourceType`, `ContextSnapshotService`, `ContextValidator`, context tests.

- [x] **Step 1: Add failing references**

Update `CanonicalDtoValidationTest` to validate:

```java
var invalid = new CanonicalNursingAssessment(null, null, null, null,
    "NIS", "REC-NA", "v1", Instant.now(), Instant.now(), QualityStatus.VALID);
assertThat(validator.validate(invalid)).extracting(v -> v.getPropertyPath().toString())
    .contains("assessmentId", "assessmentType");
```

- [x] **Step 2: Implement replacement**

`CanonicalResourceType` must contain:

```java
NURSING_ASSESSMENT
```

`ContextSnapshotResources` must expose:

```java
@Valid List<CanonicalNursingAssessment> nursingAssessments
```

`ContextSnapshotService.persistResources` must persist that list as `CanonicalResourceType.NURSING_ASSESSMENT`.

- [x] **Step 3: Clean old code references**

Run:

```bash
rg -n "CanonicalSymptom|SYMPTOM|symptoms\\(" medkernel-backend/src/main/java medkernel-backend/src/test/java
```

Expected: no results in production/test Java.

---

### Task 6: Sync Docs And Current Task State

**Files:**
- Modify: `docs/cards/D0/SYS-01.md`
- Modify: `docs/backlog.md`
- Modify: `docs/_HANDOFF.md`

- [x] **Step 1: Update SYS-01 table names**

Change the table family section to the gate-compliant names:

```text
mk_clinical_patient / mk_clinical_encounter / mk_clinical_condition /
mk_clinical_observation / mk_clinical_medication / mk_clinical_procedure /
mk_clinical_diagnostic_report / mk_clinical_document /
mk_clinical_nursing_assessment / mk_clinical_care_plan /
mk_clinical_follow_up / mk_clinical_claim
```

Add a short note: old bare table names in the card were superseded by BASE-05 migration naming guard.

- [x] **Step 2: Mark PR1 evidence only**

Do not mark all SYS-01 FR/AC done. Mark PR1 progress as covering AC-1 and AC-5 foundation only; PR2/PR3 remain pending.

---

### Task 7: Full Verification Before PR

**Files:** no code edits unless verification exposes defects.

- [x] **Step 1: Run focused backend tests**

```bash
mvn -B -q -Dtest=StandardClinicalModelContractTest,StandardClinicalModelRepositoryTest,CanonicalDtoValidationTest,ContextSnapshotServiceTest,ContextValidatorTest,CanonicalResourceRepositoryTest,MigrationBaselineContractTest,H2BaselineMigrationTest test
```

- [x] **Step 2: Run backend full tests**

```bash
mvn -B -q test
```

- [x] **Step 3: Run changed T-GATE**

From repo root:

```bash
node scripts/authenticity-guard.mjs --mode=changed --base=origin/main
node scripts/config-boundary-guard.mjs --mode=changed --base=origin/main
node scripts/migration-convention-guard.mjs --mode=changed --base=origin/main
scripts/check-comment-zh.sh
git diff --check origin/main...HEAD
```

Note: before commit, `changed` mode has no committed diff to inspect, so PR1 also ran explicit worktree/file scans for untracked files: authenticity/config scanned 47 changed-or-new candidates and 33 production Java files; migration guard scanned the 5 V38 files; `git diff --check` ran after intent-to-add so new files were included.

- [ ] **Step 4: Commit, PR, CI, merge**

Only after fresh verification passes:

```bash
git add docs/_HANDOFF.md docs/backlog.md docs/cards/D0/SYS-01.md \
  docs/superpowers/plans/2026-06-01-sys-01-clinical-model-pr1.md \
  medkernel-backend/src/main/java/com/medkernel/engine/clinical/model \
  medkernel-backend/src/main/java/com/medkernel/engine/context \
  medkernel-backend/src/main/resources/db/migration \
  medkernel-backend/src/test/java/com/medkernel/engine/clinical/model \
  medkernel-backend/src/test/java/com/medkernel/engine/context \
  medkernel-backend/src/test/java/com/medkernel/migration
git commit -m "完成 SYS-01 标准临床模型 PR1"
git push -u origin codex/sys-01-clinical-model
```

Create PR with Chinese summary, local verification evidence, migration impact, deferred issues, and clear note that PR2/PR3 are not claimed complete. Merge only after CI is green; then confirm `origin/main` contains the merge before continuing SYS-01 PR2.
