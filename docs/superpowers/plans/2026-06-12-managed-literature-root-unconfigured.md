# 平台知识文献资料库未配置态 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 移除代码中的 COS 正式地址默认值，让资料库根地址只能由系统配置页维护。

**Architecture:** 保留现有配置中心键和 `PLATFORM_SEED` 元数据，将安全默认改为空字符串；前端把空值渲染为明确的未配置警告。合法 URI 校验和高风险更新流程沿用现有 `SystemConfigService`。

**Tech Stack:** Java 21、Spring Boot、Spring JDBC、JUnit 5、React、TypeScript、Ant Design、Vitest。

---

### Task 1: 后端未配置安全默认

**Files:**
- Modify: `medkernel-backend/src/test/java/com/medkernel/shared/config/SystemConfigControllerTest.java`
- Modify: `medkernel-backend/src/main/java/com/medkernel/shared/config/SystemConfigService.java`
- Modify: `medkernel-backend/src/main/java/com/medkernel/shared/config/SystemConfigSeeder.java`

- [ ] **Step 1: 写失败测试**

将聚焦测试改为断言初始配置项值为空、来源为 `PLATFORM_SEED`，非法 `file:///tmp/...` 更新后仍为空，合法 S3 URI 可保存。

- [ ] **Step 2: 验证红灯**

Run: `mvn -q -f medkernel-backend/pom.xml -Dtest=SystemConfigControllerTest#knowledgeLiteratureMaterialRootUriRequiresManagedStorageConfiguration test`

Expected: FAIL，现有 COS 默认值与空值断言不符。

- [ ] **Step 3: 最小实现**

把 `DEFAULT_KNOWLEDGE_LITERATURE_MATERIAL_ROOT_URI` 改为空字符串；种子描述明确正式知识生产前必须通过系统配置页维护，不增加任何本机或厂商回退。

- [ ] **Step 4: 验证绿灯**

Run: `mvn -q -f medkernel-backend/pom.xml -Dtest=SystemConfigControllerTest#knowledgeLiteratureMaterialRootUriRequiresManagedStorageConfiguration test`

Expected: PASS。

### Task 2: 前端未配置态

**Files:**
- Modify: `frontend/src/pages/compliance/SecurityBaseline.test.tsx`
- Modify: `frontend/src/pages/compliance/SecurityBaselinePanels.tsx`

- [ ] **Step 1: 写失败测试**

把系统配置模拟值改为空，断言资料库摘要和表格显示“未配置”，仍可编辑并保存合法 S3 URI。

- [ ] **Step 2: 验证红灯**

Run: `npm --prefix frontend test -- src/pages/compliance/SecurityBaseline.test.tsx`

Expected: FAIL，现有页面把空字符串直接渲染为空白。

- [ ] **Step 3: 最小实现**

为空值提供统一的“未配置”文本；资料库摘要使用 warning 状态，并提示正式知识生产前必须通过配置页维护受管 URI。

- [ ] **Step 4: 验证绿灯**

Run: `npm --prefix frontend test -- src/pages/compliance/SecurityBaseline.test.tsx`

Expected: PASS。

### Task 3: 全新空库与发布证据

**Files:**
- Modify: `docs/audit/global-product-ia-acceptance.md`
- Modify: `docs/audit/p3-release-prep-acceptance.md`
- Modify: `docs/_HANDOFF.md`

- [ ] **Step 1: 构建新候选**

Run: `mvn -f medkernel-backend/pom.xml -DskipTests clean package && npm --prefix frontend run build`

Expected: 后端 `BUILD SUCCESS`，前端构建成功。

- [ ] **Step 2: 远端空库预检**

在独立 PostgreSQL 临时库启动候选，验证 Flyway V1-V116、178 张 public 基表、配置项值为空且来源为 `PLATFORM_SEED`、readiness 200，随后删除临时库和进程。

- [ ] **Step 3: 清库前最终备份与恢复**

备份正式演练库、配置和当前制品，生成 SHA-256，并恢复到隔离临时库验证后删除临时库。

- [ ] **Step 4: 清库与受控发布**

停止 `medkernel`，重建空白 `medkernel` 数据库，通过 `/usr/local/bin/medkernel-deploy` 发布新候选并等待健康检查。

- [ ] **Step 5: 记录验收**

记录 manifest、候选摘要、Flyway V116、表数量、未配置资料库根地址、服务健康和回退路径；不得把未配置状态写成正式知识生产已放行。

