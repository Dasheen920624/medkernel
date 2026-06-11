# 全系统功能与产品信息架构重构实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 在第一阶段提交后、134 演练前，完成全系统功能盘点、产品裁决、菜单与路由重构、页面任务化和 14 角色验收。

**Architecture:** 以功能目录为权威输入，先做能力到客户任务的裁决，再改菜单、路由、权限和页面，最后用自动化矩阵与真实浏览器角色旅程锁定结果。五个客户主域保持稳定，高级技术能力默认隐藏；不保留旧名称和错误归属的兼容入口。

**Tech Stack:** Java 21、Spring Boot、Spring Security、Flyway、React、TypeScript、Ant Design、Vitest、Testing Library、Playwright、Markdown 证据。

---

### Task 1: 生成全系统功能目录

**Files:**
- Create: `docs/audit/product-function-catalog.md`
- Create: `scripts/audit/export-product-capabilities.mjs`
- Test: `frontend/src/shared/config/productCatalog.test.ts`

- [ ] **Step 1: 写失败测试**

测试要求每个认证路由都出现在功能目录，每个后端菜单键都与前端路由唯一对应，每项能力都有 `KEEP/RENAME/MOVE/MERGE/SPLIT/EXPERT/API_ONLY/REMOVE` 裁决。

- [ ] **Step 2: 运行测试并确认失败**

Run: `npm test -- --run src/shared/config/productCatalog.test.ts`

Expected: FAIL，报告目录文件缺失或路由未登记。

- [ ] **Step 3: 实现目录导出脚本**

脚本读取 `routes.ts`、`MenuPermissionCatalog.java`、页面组件和控制器清单，输出路径、菜单、角色、权限、页面类型、API 来源和裁决占位结构；人工裁决后不得保留空值。

- [ ] **Step 4: 完成裁决矩阵**

逐项填写客户任务、主角色、现有问题、目标名称、目标归属、目标入口、处理方式和验收路径。无明确客户任务的能力选择专家化、接口化或移除。

- [ ] **Step 5: 运行目录测试**

Expected: PASS，且无重复菜单、孤儿认证路由或空裁决。

### Task 2: 锁定目标五域信息架构

**Files:**
- Modify: `docs/CONSTITUTION.md`
- Modify: `docs/EXPERIENCE_CONTRACT.md`
- Modify: `docs/glossary.md`
- Modify: `docs/BUSINESS_IMPLEMENTATION_SCOPE_AUDIT.md`
- Create: `docs/audit/product-ia-matrix.md`
- Test: `frontend/src/shared/config/routes.test.ts`
- Test: `medkernel-backend/src/test/java/com/medkernel/engine/security/MenuPermissionControllerTest.java`

- [ ] **Step 1: 写五域名称、顺序和归属失败测试**

断言一级域依次为“工作台、机构治理、知识配置、临床协同、质量与运营”，高级工具保持隐藏；同一菜单键只出现一次。

- [ ] **Step 2: 运行前后端测试并确认失败**

Run: `npm test -- --run src/shared/config/routes.test.ts`

Run: `mvn -q -Dtest=MenuPermissionControllerTest test`

- [ ] **Step 3: 更新产品权威文档**

同步五域定位、功能归属原则、专家工具边界和新术语；删除“试点准备”“合规运维”等不再适合作为长期产品域的旧权威表述。

- [ ] **Step 4: 完成菜单裁决矩阵**

矩阵必须给出每个菜单的原名称、目标名称、原域、目标域、顺序、主角色、次角色、页面和权限。

### Task 3: 重构菜单目录、路由和权限同源

**Files:**
- Modify: `medkernel-backend/src/main/java/com/medkernel/engine/security/MenuPermissionCatalog.java`
- Modify: `medkernel-backend/src/main/java/com/medkernel/engine/security/DefaultPermissionPolicy.java`
- Modify: `frontend/src/shared/config/routes.ts`
- Modify: `frontend/src/widgets/AppLayout.tsx`
- Test: `medkernel-backend/src/test/java/com/medkernel/engine/security/DefaultPermissionPolicyTest.java`
- Test: `frontend/src/shared/config/routes.test.ts`
- Test: `frontend/src/widgets/AppLayout.test.tsx`

- [ ] **Step 1: 写角色菜单快照失败测试**

为 14 个客户职责角色建立完整菜单快照，断言只出现目标名称和目标顺序。

- [ ] **Step 2: 确认旧名称和旧顺序导致失败**

重点捕获 `pilot-setup`、`quality-improve` 中的知识治理、`compliance-ops` 中的人员入口和客户可见 `Provider`。

- [ ] **Step 3: 修改后端菜单目录**

按目标五域改 `sectionKey`、展示名和顺序；稳定权限码仅在仍表达同一业务责任时保留，错误权限语义直接重建，不做别名。

- [ ] **Step 4: 修改前端路由与侧栏**

同步路由元数据、面包屑、页签、可见角色、菜单顺序和隐藏策略。移位页面不保留双入口。

- [ ] **Step 5: 校正默认权限**

保证人员访问、平台知识、机构知识、临床、质量、审计、集成和实施职责各自获得最小菜单集。

- [ ] **Step 6: 运行菜单、路由和布局测试**

Expected: 14 角色菜单快照全部通过，前后端目录完全一致。

### Task 4: 重构机构治理功能

**Files:**
- Modify: `frontend/src/pages/tenant/TenantOnboarding.tsx`
- Modify: `frontend/src/pages/tenant/ImplementationGuide.tsx`
- Modify: `frontend/src/pages/compliance/AdminUsers.tsx`
- Modify: `frontend/src/pages/compliance/IdentityBinding.tsx`
- Modify: `frontend/src/pages/compliance/NotificationSettings.tsx`
- Test: 对应 `*.test.tsx`

- [ ] **Step 1: 为机构管理员、人员与访问管理员、实施运维员写主任务失败测试**

覆盖服务机构维护、批量人员导入、任职与账号维护、身份来源批量匹配、冲突处置和实施验收。

- [ ] **Step 2: 合并重复组织与开通概念**

页面默认使用“服务机构、组织、人员、任职、账号、身份来源”，技术编码进入专家信息。

- [ ] **Step 3: 把批量流程设为高频主流程**

批量导入、批量注册、批量绑定采用可恢复步骤流；单个新增保持次级入口。

- [ ] **Step 4: 补齐六态和移动端**

每页只有一个主动作，默认筛选不超过三个，详情不暴露原始技术对象。

### Task 5: 重构知识配置功能

**Files:**
- Modify: `frontend/src/pages/quality/KnowledgeGovernance.tsx`
- Modify: `frontend/src/pages/tenant/ConfigPackages.tsx`
- Modify: `frontend/src/pages/tenant/ReleaseGovernance.tsx`
- Modify: `frontend/src/pages/tenant/AuthoringAssets.tsx`
- Modify: `frontend/src/pages/tenant/TerminologyMapping.tsx`
- Modify: `frontend/src/pages/tenant/RuleDefinitions.tsx`
- Modify: `frontend/src/pages/tenant/PathwayTemplates.tsx`
- Test: 对应 `*.test.tsx`

- [ ] **Step 1: 写平台知识和机构知识两条任务旅程失败测试**

覆盖平台主源发布、机构派生、差异审阅、换基线、机构发布、恢复平台标准和来源追溯。

- [ ] **Step 2: 合并配置包、发布和统一资产库重复入口**

客户只从“知识资产”或“配置包与发布”进入完整步骤，隐藏路由仅作页内子步骤。

- [ ] **Step 3: 统一规则、路径、字典和知识术语**

页面提供自然语言预览和影响分析，DSL、JSON、原始图谱和技术版本键进入专家模式。

- [ ] **Step 4: 验证平台主源不可被机构污染**

所有派生和恢复操作显示来源、基线、目标组织、审核状态和审计证据。

### Task 6: 重构临床协同功能

**Files:**
- Modify: `frontend/src/pages/clinical/Mpi.tsx`
- Modify: `frontend/src/pages/clinical/PatientPathways.tsx`
- Modify: `frontend/src/pages/clinical/CdssFatigue.tsx`
- Modify: `frontend/src/pages/clinical/RuleValidate.tsx`
- Modify: `frontend/src/pages/clinical/WorkflowTodos.tsx`
- Modify: `frontend/src/pages/clinical/Notifications.tsx`
- Modify: `frontend/src/pages/clinical/Followup.tsx`
- Test: 对应 `*.test.tsx`

- [ ] **Step 1: 写临床、护理、药事、医技任务旅程失败测试**

断言各角色从工作台一跳进入职责任务，推荐和路径可解释，状态能闭环，页面不要求配置权限。

- [ ] **Step 2: 统一“提醒与推荐”因果链**

同页可追溯事件、规则、知识、路径、任务、反馈和审核，不依赖用户跨页猜测。

- [ ] **Step 3: 收敛待办、通知和随访状态**

明确任务与消息的区别，处理结果同步，空态提供下一步，不制造虚假统计。

- [ ] **Step 4: 验证医疗安全**

智能内容明显标识来源和限制，高风险必须人工确认，不自动开嘱，模型不可用时主链路诚实降级。

### Task 7: 重构质量与运营功能

**Files:**
- Modify: `frontend/src/pages/quality/QcDashboard.tsx`
- Modify: `frontend/src/pages/quality/QcAlerts.tsx`
- Modify: `frontend/src/pages/quality/InsuranceAudit.tsx`
- Modify: `frontend/src/pages/quality/QcEvalSets.tsx`
- Modify: `frontend/src/pages/quality/QcEvalResults.tsx`
- Modify: `frontend/src/pages/compliance/AdminAudit.tsx`
- Modify: `frontend/src/pages/compliance/SecurityBaseline.tsx`
- Modify: `frontend/src/pages/compliance/SystemProviders.tsx`
- Modify: `frontend/src/pages/tenant/AdapterHub.tsx`
- Test: 对应 `*.test.tsx`

- [ ] **Step 1: 写质量、审计、集成和运维任务旅程失败测试**

质量指标必须下钻到问题与整改；审计必须按人员、对象、动作和时间追溯；运行状态必须区分正常、降级、未连接和停服。

- [ ] **Step 2: 清除装饰性驾驶舱和技术状态堆叠**

所有指标有对象、有责任范围、有动作；外部依赖显示中文业务名和可处置建议。

- [ ] **Step 3: 保持角色视图隔离**

质量角色不看到系统配置操作，审计角色只读，集成角色不获得知识发布，机构管理员只在授权范围治理。

### Task 8: 清理客户英文和技术对象

**Files:**
- Modify: `frontend/src/shared/config/customerLabels.ts`
- Create: `frontend/src/shared/config/customerLanguageGate.test.ts`
- Modify: 所有客户可见页面

- [ ] **Step 1: 写静态与渲染失败测试**

扫描菜单、页头、列名、筛选、按钮、状态、错误和空态，阻止已知英文枚举、`Provider`、`TENANT`、`ASSET`、`MEDIUM`、原始 JSON/DSL/trace 进入默认客户界面。

- [ ] **Step 2: 建立分类词典**

状态、风险、组织、权限、知识、任务、连接、发布和导入分别维护稳定中文词典，未知值显示“未识别状态”并进入监测。

- [ ] **Step 3: 迁移页面并运行全量静态门禁**

允许的行业缩写必须在 `docs/glossary.md` 有中文释义。

### Task 9: 建立全角色工作台与导航验收

**Files:**
- Modify: `frontend/src/widgets/WorkbenchPanel.tsx`
- Modify: `frontend/src/widgets/WorkbenchPanel.test.tsx`
- Create: `frontend/e2e/product-role-journeys.spec.ts`
- Create: `docs/audit/product-role-journeys.md`

- [ ] **Step 1: 写 14 角色默认工作台和主任务失败测试**

每个角色有职责标题、职责摘要、唯一主动作和不超过三个高频入口。

- [ ] **Step 2: 写完整导航 E2E**

每个角色登录后遍历可见菜单，断言页面可打开、无权限页不泄露数据、无 404、无 Hook 错误、无横向溢出。

- [ ] **Step 3: 完成桌面和移动端浏览器验收**

视口至少覆盖 1440×1100、1366×768、768×1024 和 390×844；保存关键截图和角色菜单快照。

### Task 10: 全角色评审和演练放行

**Files:**
- Create: `docs/audit/global-product-ia-acceptance.md`
- Modify: `docs/_HANDOFF.md`
- Modify: `docs/audit/deferred-issues.md`

- [ ] **Step 1: 运行全部自动化门禁**

Run: `mvn test`

Run: `npm test -- --run`

Run: `npm run typecheck`

Run: `npm run lint`

Run: `npm run stylelint`

Run: `npm run build`

Run: 项目 T-GATE 命令。

- [ ] **Step 2: 完成独立八视角评审**

产品体验、临床医疗、机构管理、人员访问、安全合规、数据治理、架构运维和测试质量分别给出结论。P0/P1 和阻断主任务的 P2 必须修复后重审。

- [ ] **Step 3: 写验收报告**

报告包含功能裁决统计、菜单前后对照、14 角色旅程、英文清理结果、桌面/移动证据、测试命令、残余风险和演练放行结论。

- [ ] **Step 4: 放行或阻止 134 演练**

只有所有硬门禁通过时，`docs/_HANDOFF.md` 才能写“允许进入 134 清库演练”；否则继续修复，不得绕过。

