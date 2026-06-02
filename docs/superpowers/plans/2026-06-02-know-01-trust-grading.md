# KNOW-01 PR2 可信分级与冲突仲裁实施计划

> 给后续 AI：本计划按 `executing-plans` 执行。每一步必须保留红灯 / 绿灯证据，不得把 PR3 图/搜索投影或 OPT-07 展示优先级提前算入本 PR。

**目标**：完成 KNOW-01 PR2 的可信分级与冲突仲裁基座：来源 A/B/C/D/E 标准化、分级依据必填、资产版本 GRADE 快照、低阶覆盖高阶门禁、V50 五方言迁移与证据同步。

**架构**：关系库仍是唯一权威。来源文献登记时写入可信分级与分级依据；创建知识资产版本时从来源文献快照分级，并保存 GRADE 字段；版本激活替换时按 A-E rank 做 B0 确定性仲裁，裁决摘要进入资产版本与替代链。

**技术栈**：Java 21、Spring Boot、Spring Data JDBC、Flyway、JUnit 5、Mockito、AssertJ。真实运行范围为 PostgreSQL + Oracle；H2 用于本地基线和测试；达梦 / 人大金仓仅做静态方言合同，真实环境归 `DEFER-001`。

---

### 任务 1：为来源分级与分级依据补红灯测试

**文件**
- 修改：`medkernel-backend/src/test/java/com/medkernel/engine/knowledge/KnowledgeIdentityServiceTest.java`
- 修改：`medkernel-backend/src/test/java/com/medkernel/engine/knowledge/KnowledgeEngineTest.java`

- [x] **步骤 1：新增失败测试**

测试使用 `SourceAuthorityLevel.A_REGULATION`、`B_GUIDELINE`、`C_CONSENSUS_LITERATURE`、`D_HOSPITAL`、`E_FEEDBACK`，断言 rank 顺序，并断言来源登记必须持久化非空 `authorityBasis`。

- [x] **步骤 2：运行红灯**

命令：`mvn -q -Dtest=KnowledgeIdentityServiceTest,KnowledgeEngineTest test`

预期：编译失败或测试失败，因为新枚举与 `authorityBasis` 字段尚不存在。

### 任务 2：实现 A-E 来源可信分级

**文件**
- 修改：`medkernel-backend/src/main/java/com/medkernel/engine/knowledge/SourceAuthorityLevel.java`
- 修改：`medkernel-backend/src/main/java/com/medkernel/engine/knowledge/SourceDocument.java`
- 修改：`medkernel-backend/src/main/java/com/medkernel/engine/knowledge/SourceRegisterRequest.java`
- 修改：`medkernel-backend/src/main/java/com/medkernel/engine/knowledge/KnowledgeSourceCreateRequest.java`
- 修改：`medkernel-backend/src/main/java/com/medkernel/engine/knowledge/KnowledgeIdentityService.java`
- 修改：构造 `SourceDocument` 或来源请求的相关测试

- [x] **步骤 1：替换旧来源权威枚举**

`SourceAuthorityLevel` 只保留 A-E 分级，并提供 `rank()`、`label()`、`isHighAuthority()`、`isLowAuthority()`；rank 越小可信度越高。

- [x] **步骤 2：持久化分级依据**

`SourceDocument` 增加 `@Column("authority_basis") String authorityBasis`；来源请求记录增加 `authorityBasis`；`KnowledgeIdentityService.registerSource` 拒绝空白分级依据。

- [x] **步骤 3：跑绿任务 1**

命令：`mvn -q -Dtest=KnowledgeIdentityServiceTest,KnowledgeEngineTest test`

预期：通过。

### 任务 3：为资产版本 GRADE 快照补红灯测试

**文件**
- 修改：`medkernel-backend/src/test/java/com/medkernel/engine/knowledge/KnowledgeVersionServiceTest.java`
- 修改：`medkernel-backend/src/test/java/com/medkernel/engine/knowledge/KnowledgeAssetApiContractTest.java`

- [x] **步骤 1：新增失败测试**

测试 `createDraftVersion` 保存 `SourceAuthorityLevel.B_GUIDELINE`、`GradeEvidenceQuality.HIGH`、`GradeRecommendationStrength.STRONG` 到 `KnowledgeAssetVersion`，并验证 API 请求接受 `grade_quality` / `grade_strength`。

- [x] **步骤 2：运行红灯**

命令：`mvn -q -Dtest=KnowledgeVersionServiceTest,KnowledgeAssetApiContractTest test`

预期：编译失败或测试失败，因为 GRADE 枚举和资产版本字段尚不存在。

### 任务 4：实现资产版本 GRADE 快照

**文件**
- 新增：`medkernel-backend/src/main/java/com/medkernel/engine/knowledge/GradeEvidenceQuality.java`
- 新增：`medkernel-backend/src/main/java/com/medkernel/engine/knowledge/GradeRecommendationStrength.java`
- 修改：`medkernel-backend/src/main/java/com/medkernel/engine/knowledge/KnowledgeAssetVersion.java`
- 修改：`medkernel-backend/src/main/java/com/medkernel/engine/knowledge/DraftVersionCreateRequest.java`
- 修改：`medkernel-backend/src/main/java/com/medkernel/engine/knowledge/KnowledgeVersionCreateRequest.java`
- 修改：`medkernel-backend/src/main/java/com/medkernel/engine/knowledge/KnowledgeVersionService.java`
- 修改：受影响测试和 fixture

- [x] **步骤 1：新增枚举与记录字段**

DTO 与实体新增可空 GRADE 字段。保持可空是为了不伪造历史行，但调用方传入的值必须原样持久化。

- [x] **步骤 2：创建版本时读取来源文献**

`KnowledgeVersionService` 注入 `SourceDocumentRepository`；`createDraftVersion` 必须按当前租户和 `sourceDocumentId` 读取来源文献，并把来源 `authorityLevel` 快照到资产版本。

- [x] **步骤 3：复制状态时保留新字段**

`submit`、`activate`、`withdraw`、历史重放等所有 `new KnowledgeAssetVersion(...)` 复制点必须保留 `authorityLevel`、`gradeQuality`、`gradeStrength`、`conflictArbitration`。

- [x] **步骤 4：跑绿任务 3**

命令：`mvn -q -Dtest=KnowledgeVersionServiceTest,KnowledgeAssetApiContractTest test`

预期：通过。

### 任务 5：为冲突仲裁与低阶覆盖门禁补红灯测试

**文件**
- 修改：`medkernel-backend/src/test/java/com/medkernel/engine/knowledge/KnowledgeVersionServiceTest.java`

- [x] **步骤 1：新增失败测试**

覆盖三类场景：A 级候选替换 D 级 active 不需要人工理由；D 级候选替换 A 级 active 且理由为空时抛 `AUTHORITY_OVERRIDE_DENIED`；D 级候选带理由替换 A 级 active 时成功，并记录显式理由与裁决摘要。

- [x] **步骤 2：运行红灯**

命令：`mvn -q -Dtest=KnowledgeVersionServiceTest test`

预期：编译失败或测试失败，因为仲裁对象与错误码尚不存在。

### 任务 6：实现冲突仲裁

**文件**
- 新增：`medkernel-backend/src/main/java/com/medkernel/engine/knowledge/ConflictArbitration.java`
- 修改：`medkernel-backend/src/main/java/com/medkernel/shared/api/error/ErrorCode.java`
- 修改：`medkernel-backend/src/main/java/com/medkernel/engine/knowledge/KnowledgeVersionService.java`

- [x] **步骤 1：新增错误码**

新增 `AUTHORITY_OVERRIDE_DENIED("ENG-KNOW-004", 409, "低阶来源覆盖高阶来源需要显式理由和审核", ErrorClass.DATA, false)`。

- [x] **步骤 2：新增确定性仲裁值对象**

`ConflictArbitration.between(oldActive, target)` 先比较 A-E rank，并输出中文摘要；任一侧缺少分级时不阻断，但摘要必须写明可信分级缺失，需要审核人按来源引用确认。

- [x] **步骤 3：激活时执行低阶覆盖门禁**

替换旧 active 前计算仲裁。若 target 为 D/E 且 old active 为 A/B，理由为空则抛 `AUTHORITY_OVERRIDE_DENIED`；否则把裁决摘要写入激活版本，并写入替代链理由。

- [x] **步骤 4：跑绿任务 5**

命令：`mvn -q -Dtest=KnowledgeVersionServiceTest test`

预期：通过。

### 任务 7：新增 V50 五方言迁移与迁移测试

**文件**
- 新增：`medkernel-backend/src/main/resources/db/migration/h2/V50__knowledge_trust_grading.sql`
- 新增：`medkernel-backend/src/main/resources/db/migration/postgres/V50__knowledge_trust_grading.sql`
- 新增：`medkernel-backend/src/main/resources/db/migration/oracle/V50__knowledge_trust_grading.sql`
- 新增：`medkernel-backend/src/main/resources/db/migration/dm/V50__knowledge_trust_grading.sql`
- 新增：`medkernel-backend/src/main/resources/db/migration/kingbase/V50__knowledge_trust_grading.sql`
- 修改：`medkernel-backend/src/test/java/com/medkernel/migration/MigrationBaselineContractTest.java`
- 修改：`medkernel-backend/src/test/java/com/medkernel/migration/FlywayMultiDialectSmokeTest.java`
- 修改：`medkernel-backend/src/test/java/com/medkernel/migration/H2BaselineMigrationTest.java`

- [x] **步骤 1：新增失败迁移合同**

把最新迁移期望更新到 V50，断言 `source_document.authority_basis`、`knowledge_asset_version.authority_level`、`grade_quality`、`grade_strength`、`conflict_arbitration`、相关约束、索引和中文 COMMENT。

- [x] **步骤 2：运行红灯迁移测试**

命令：`mvn -q -Dtest=MigrationBaselineContractTest,H2BaselineMigrationTest test`

预期：失败，因为 V50 尚不存在。

- [x] **步骤 3：新增 V50 迁移**

各方言迁移必须把旧来源权威值转换为 A-E，替换 `ck_source_document_authority`，新增资产版本字段、CHECK 约束、`idx_knowledge_av_authority` 和中文 COMMENT。

- [x] **步骤 4：运行迁移烟测**

命令：`mvn -q -Dtest=MigrationBaselineContractTest,H2BaselineMigrationTest,FlywayMultiDialectSmokeTest test`

预期：H2、PostgreSQL、Oracle 通过；DM / Kingbase 只做静态合同，不伪造真实连接证据。

### 任务 8：文档、接力与验证

**文件**
- 修改：`docs/cards/D2/KNOW-01.md`
- 修改：`docs/cards/D2/OPT-07.md`
- 修改：`docs/_HANDOFF.md`
- 复核：`docs/audit/deferred-issues.md`

- [x] **步骤 1：更新任务卡证据**

`KNOW-01` 勾选 FR-5 / AC-3，只代表 PR2 引擎基座完成；FR-6 / AC-4 保持待 PR3。`OPT-07` 只写阶段证据，不整体标 done。

- [x] **步骤 2：更新接力文档**

归档 PR1，新增 PR2 当前状态、分支、验证命令和下一步；保持待处理清单策略可见。

- [x] **步骤 3：运行全量验证**

命令：

```bash
mvn -q test
node scripts/authenticity-guard.mjs --mode=changed --base=origin/main
node scripts/config-boundary-guard.mjs --mode=changed --base=origin/main
node scripts/migration-convention-guard.mjs --mode=changed --base=origin/main
scripts/check-comment-zh.sh
git diff --check
```

预期：全部通过。提交前 `changed` 可能扫描 0 文件，不能作为最终证据；提交后必须重跑 changed 门禁并确认扫描到本 PR 文件。
