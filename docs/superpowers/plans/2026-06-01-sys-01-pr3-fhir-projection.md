# SYS-01 PR3 FHIR 映射与投影解耦实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 完成 SYS-01 PR3：证明 12 类标准临床对象以关系库为权威，图投影关闭不影响标准对象读取，并提供可追踪的 FHIR R4 映射锚点。

**Architecture:** 不提前实现 OPT-01 完整 FHIR 门面端点，也不冒领 SYS-03 投影重建任务。本 PR 在 `com.medkernel.engine.clinical.model` 内新增权威读取服务、投影状态端口与 FHIR 映射注册表；服务只读关系库仓库，投影状态只作为诚实状态返回，不参与权威读取。

**Tech Stack:** Spring Boot 3.3、Spring Data JDBC、JUnit 5、AssertJ、Mockito、Flyway H2/PostgreSQL/Oracle smoke。

---

### Task 1: 红测锁定 FHIR 映射锚点

**Files:**
- Create: `medkernel-backend/src/test/java/com/medkernel/engine/clinical/model/StandardClinicalFhirMappingRegistryTest.java`
- Create: `medkernel-backend/src/main/java/com/medkernel/engine/clinical/model/StandardClinicalFhirResourceType.java`
- Create: `medkernel-backend/src/main/java/com/medkernel/engine/clinical/model/StandardClinicalFhirReference.java`
- Create: `medkernel-backend/src/main/java/com/medkernel/engine/clinical/model/StandardClinicalFhirMappingRegistry.java`

- [x] **Step 1: 写失败测试**

覆盖：
- 12 类 SYS-01 标准对象都有 FHIR R4 资源类型。
- `ClinicalPatient.fhirResourceId = "Patient/pat-1"` 时解析为 `resourceType=Patient, resourceId=pat-1`。
- `fhirResourceId` 缺失时 fallback 到本地权威主键，且 `mappingStatus=LOCAL_AUTHORITY_FALLBACK`，不得伪造外部 FHIR id。

- [x] **Step 2: 运行红测**

Run: `mvn -B -q -Dtest=StandardClinicalFhirMappingRegistryTest test`
Expected: 编译失败，缺少 `StandardClinicalFhirMappingRegistry` / `StandardClinicalFhirReference`。

- [x] **Step 3: 最小实现**

新增：
- `StandardClinicalFhirResourceType`：枚举 Patient / Encounter / Condition / Observation / MedicationRequest / Procedure / DiagnosticReport / DocumentReference / CarePlan / Task / Claim。
- `StandardClinicalFhirReference`：包含 `CanonicalResourceType canonicalType`、`String localId`、`String fhirVersion`、`String resourceType`、`String resourceId`、`String mappingStatus`。
- `StandardClinicalFhirMappingRegistry`：显式注册 12 类对象到 FHIR R4 资源类型；按 `fhirResourceId` 解析 `ResourceType/id`，否则用本地 ID fallback。

- [x] **Step 4: 运行绿测**

Run: `mvn -B -q -Dtest=StandardClinicalFhirMappingRegistryTest test`
Expected: PASS。

### Task 2: 红测锁定关系库权威读取不依赖图投影

**Files:**
- Create: `medkernel-backend/src/test/java/com/medkernel/engine/clinical/model/StandardClinicalAuthorityServiceTest.java`
- Create: `medkernel-backend/src/main/java/com/medkernel/engine/clinical/model/ClinicalProjectionStatus.java`
- Create: `medkernel-backend/src/main/java/com/medkernel/engine/clinical/model/ClinicalProjectionStatusPort.java`
- Create: `medkernel-backend/src/main/java/com/medkernel/engine/clinical/model/StandardClinicalAuthorityBundle.java`
- Create: `medkernel-backend/src/main/java/com/medkernel/engine/clinical/model/StandardClinicalAuthorityService.java`

- [x] **Step 1: 写失败测试**

覆盖：
- 投影端口返回 `NOT_SYNCED` 时，按 `tenantId + patientId` 仍从关系库读取 Patient + 11 类患者相关对象。
- 返回结果 `authoritySource=RELATIONAL_DATABASE`，`projectionStatus=NOT_SYNCED`，并包含 12 个 FHIR 引用。
- 不存在患者时抛出 `ENG_CONTEXT_001`，不得返回空成功。

- [x] **Step 2: 运行红测**

Run: `mvn -B -q -Dtest=StandardClinicalAuthorityServiceTest test`
Expected: 编译失败，缺少权威读取服务与投影状态端口。

- [x] **Step 3: 最小实现**

新增：
- `ClinicalProjectionStatus`：`UP` / `NOT_SYNCED`。
- `ClinicalProjectionStatusPort`：`status(String tenantId)`；默认由测试 stub 注入，生产默认实现返回 `NOT_SYNCED`。
- `StandardClinicalAuthorityBundle`：聚合租户、患者、权威来源、投影状态与 FHIR 引用。
- `StandardClinicalAuthorityService`：只依赖 12 个关系库仓库与 FHIR 映射注册表；图投影状态只进入响应，不影响读取。

- [x] **Step 4: 运行绿测**

Run: `mvn -B -q -Dtest=StandardClinicalAuthorityServiceTest,StandardClinicalFhirMappingRegistryTest,StandardClinicalModelRepositoryTest,StandardClinicalModelContractTest test`
Expected: PASS。

### Task 3: 文档接力与验收口径

**Files:**
- Modify: `docs/_HANDOFF.md`
- Modify: `docs/cards/D0/SYS-01.md`
- Modify: `docs/backlog.md`

- [x] **Step 1: 更新 SYS-01 PR3 证据**

在 SYS-01 卡追加 PR3 进度证据：关系库权威读取服务、投影关闭诚实状态、FHIR R4 映射锚点；仅勾选 SYS-01 可由本 PR 完成的 FR/AC，不冒领 OPT-01 完整门面或 SYS-03 投影重建。

- [x] **Step 2: 更新接力**

把 PR2 工作线移入归档，新增/替换为 PR3 工作线；写明当前分支、计划路径、验证命令、`DEFER-001` 仍不阻塞主线。

- [x] **Step 3: 完整验证**

Run:
- `mvn -B -q -Dtest=StandardClinicalAuthorityServiceTest,StandardClinicalFhirMappingRegistryTest,StandardClinicalModelRepositoryTest,StandardClinicalModelContractTest test`
- `mvn -B -q test`
- `node scripts/authenticity-guard.mjs --mode=changed --base=origin/main`
- `node scripts/config-boundary-guard.mjs --mode=changed --base=origin/main`
- `node scripts/migration-convention-guard.mjs --mode=changed --base=origin/main`
- `scripts/check-comment-zh.sh`
- `git diff --check origin/main...HEAD`

Expected: 全部 exit 0；若遇到外部环境问题，登记 `docs/audit/deferred-issues.md` 并继续主线，不把未验证项写成已通过。
