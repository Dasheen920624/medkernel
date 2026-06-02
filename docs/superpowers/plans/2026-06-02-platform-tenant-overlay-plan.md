# 平台主源与租户覆盖层 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 将知识、规则、路径核心医疗资产从“当前租户硬隔离读取”调整为“平台主源 + 租户覆盖层”有效读取模型，并限制平台主源只能同步主源本身。

**Architecture:** 新增小而集中的解析能力：规则和路径在服务层通过平台主租户回退读取候选资产；知识在身份/版本服务中按业务编码解析平台回退。所有写入仍落当前租户，运行事实也落当前租户，平台 `t-1` 不接收客户反写。平台主源只同步到主源发布账本或只读发布快照；客户导入平台包不得生成客户主源资产。

**Tech Stack:** Java 21、Spring Boot、Spring Data JDBC、JUnit 5、Mockito、AssertJ、React/TypeScript（后续来源标签扩展）。

---

### Task 1: 规则有效读取

**Files:**
- Modify: `medkernel-backend/src/test/java/com/medkernel/engine/rule/RuleEngineServiceTest.java`
- Modify: `medkernel-backend/src/main/java/com/medkernel/engine/rule/RuleDefinitionRepository.java`
- Modify: `medkernel-backend/src/main/java/com/medkernel/engine/rule/RuleEngineService.java`

- [ ] **Step 1: Write failing tests**

新增两个测试：客户租户未指定规则时会评估平台已发布规则；客户存在同 `rule_code` 本地规则时，本地规则覆盖平台规则。

- [ ] **Step 2: Verify RED**

Run: `mvn -B -q "-Dtest=RuleEngineServiceTest" test`

Expected: 新测试失败，因为当前服务只查当前租户。

- [ ] **Step 3: Implement resolver**

在仓库增加平台回退查询，在服务中合并当前租户和平台候选，按 `rule_code` 去重并保持本地优先。

- [ ] **Step 4: Verify GREEN**

Run: `mvn -B -q "-Dtest=RuleEngineServiceTest" test`

Expected: `RuleEngineServiceTest` 通过。

### Task 2: 路径模板有效读取

**Files:**
- Modify: `medkernel-backend/src/test/java/com/medkernel/engine/pathway/PathwayEngineServiceTest.java`
- Modify: `medkernel-backend/src/main/java/com/medkernel/engine/pathway/PathwayTemplateRepository.java`
- Modify: `medkernel-backend/src/main/java/com/medkernel/engine/pathway/PathwayNodeRepository.java`
- Modify: `medkernel-backend/src/main/java/com/medkernel/engine/pathway/PathwayEdgeRepository.java`
- Modify: `medkernel-backend/src/main/java/com/medkernel/engine/pathway/PathwayEngineService.java`

- [ ] **Step 1: Write failing tests**

新增测试：客户租户可基于平台已发布模板入径；客户本地同编码同版本模板优先。

- [ ] **Step 2: Verify RED**

Run: `mvn -B -q "-Dtest=PathwayEngineServiceTest" test`

Expected: 新测试失败，因为当前服务只按客户租户查模板和图。

- [ ] **Step 3: Implement resolver**

详情和入径先查当前租户模板，未找到回退平台；节点和边按解析出的模板来源租户加载；患者路径事实仍写当前租户。

- [ ] **Step 4: Verify GREEN**

Run: `mvn -B -q "-Dtest=PathwayEngineServiceTest" test`

Expected: `PathwayEngineServiceTest` 通过。

### Task 3: 知识身份有效读取

**Files:**
- Modify: `medkernel-backend/src/test/java/com/medkernel/engine/knowledge/KnowledgeVersionServiceTest.java`
- Modify: `medkernel-backend/src/main/java/com/medkernel/engine/knowledge/KnowledgeIdentityRepository.java`
- Modify: `medkernel-backend/src/main/java/com/medkernel/engine/knowledge/KnowledgeVersionService.java`

- [ ] **Step 1: Write failing tests**

新增测试：客户租户按平台身份 ID 读取版本时可回退平台；客户有同 `identity_code` 本地身份时本地优先。

- [ ] **Step 2: Verify RED**

Run: `mvn -B -q "-Dtest=KnowledgeVersionServiceTest" test`

Expected: 新测试失败，因为当前服务找不到客户租户内的平台代理身份。

- [ ] **Step 3: Implement resolver**

新增身份解析方法：本租户直接命中则返回；否则非平台租户按平台身份查到 canonical key，再查本租户同 key，本地存在则返回本地，否则返回平台。

- [ ] **Step 4: Verify GREEN**

Run: `mvn -B -q "-Dtest=KnowledgeVersionServiceTest" test`

Expected: `KnowledgeVersionServiceTest` 通过。

### Task 4: 总体验证与接力

**Files:**
- Modify: `docs/_HANDOFF.md`

- [ ] **Step 1: Run focused backend verification**

Run: `mvn -B -q "-Dtest=KnowledgeVersionServiceTest,RuleEngineServiceTest,PathwayEngineServiceTest" test`

- [ ] **Step 2: Run backend package or project gate as time allows**

Run: `mvn -B -q test`

- [ ] **Step 3: Run diff check**

Run: `git diff --check`

- [ ] **Step 4: Update handoff**

记录本轮变更范围、验证命令、未进入本阶段的后续覆盖关系表和合并任务。

### Task 5: 离线包主源边界复核

**Files:**
- Modify: `medkernel-backend/src/test/java/com/medkernel/engine/pkg/PackageEngineServiceTest.java`
- Modify: `medkernel-backend/src/main/java/com/medkernel/engine/pkg/PackageEngineService.java`

- [ ] **Step 1: Write failing tests**

新增测试：平台主源离线包导入客户租户时，不得创建客户主源资产归属；只能作为平台发布快照 / 引用被读取。客户覆盖资产不得被平台包覆盖。

- [ ] **Step 2: Verify RED**

Run: `mvn -B -q "-Dtest=PackageEngineServiceTest" test`

Expected: 如果当前实现仍把平台源同步为客户源，测试失败。

- [ ] **Step 3: Implement boundary**

收紧导入逻辑：平台包面向客户只落只读发布快照或引用关系；客户资产行只由本地新增或明确覆盖生成。

- [ ] **Step 4: Verify GREEN**

Run: `mvn -B -q "-Dtest=PackageEngineServiceTest" test`

Expected: `PackageEngineServiceTest` 通过。
