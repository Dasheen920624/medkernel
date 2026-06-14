# GA-ENG-BASE-09 · 代码基线净化设计

> 版本：1.0 · 日期：2026-05-26
> 任务台账：[docs/backlog.md](../../backlog.md) · `GA-ENG-BASE-09`
> 关联：[宪法 #16](../../CONSTITUTION.md)、[实施落地方案](../../MEDKERNEL_IMPLEMENTATION_LANDING_PLAN.md)、[业务场景详细规范](../../MEDKERNEL_BUSINESS_SCENARIO_DETAIL_SPEC.md)、[产品体验固定规范](../../MEDKERNEL_PRODUCT_EXPERIENCE_RULES.md)
> 上游审计：本次架构师介入产出的全栈核查报告（见本文 §2 摘录）

---

## 1 · 目的与一句话定义

把 W3-W7 阶段遗留的 6 大旧业务模块（advanced / quality / clinical / tenant / platform / compliance 非 audit 部分）从主代码中彻底净化，前端 19 处显式 mock/硬编码常量 + 数处"真接到 mock 后端"的 hook 页面同步下线，并补全 CI 门禁；让 E1 基础底座完整收尾，为 BASE-08 产品体验底座与 E2 余下 11 项引擎 API 提供干净地基。

**一句话**：把代码、UI 与门禁同时净化到"无 mock、无假闭环、无裸 Map、无 SEED 数据、无视觉债回潮"的状态。

---

## 2 · 上游审计摘录

| 维度 | 现状 |
|---|---|
| 新引擎质量 | `engine/{org, security, terminology, knowledge}` 边界清晰、契约规范、测试覆盖完整、**对旧模块零反向依赖** |
| 旧业务残留 | 25 个旧 Controller 全部为 `Map<String, Object>` 裸返 + 静态 SEED + 无 `@DataScope` + 无 `@PreAuthorize` + 无 ApiResult 包络；旧模块**无任何测试** |
| 前端假闭环 | 19 处显式 mock/硬编码常量散布 19 个业务页文件；另有数处已接 hook 的页面（CdssFatigue / PathwayTemplates / Mpi）**真接到 mock controller** |
| 门禁缺口 | CI 未跑 ESLint，BASE-10 视觉债门禁存在回潮风险 |
| Backlog 漂移 | `GA-ENG-API-03` 与 `GA-ENG-API-04` 已实质上线但 backlog 仍标 pending；引擎执行 `KNOW-01/02`、`TERM-01` 已 partial done |
| OpenSpec 残留 | `openspec/changes/{audit-event-persistence, containerized-development-platform}` 两份历史变更已落地，待归档 |

---

## 3 · 设计决策

### 3.1 后端净化策略 — **C 提取设计精华 + 删除实现**

> 把旧 Controller 中的**三类设计要素**迁移到 [业务场景详细规范](../../MEDKERNEL_BUSINESS_SCENARIO_DETAIL_SPEC.md) 的 E6 附录，作为未来业务包装的设计锚点；然后**删除全部 Java 代码**。

**迁入文档的三类要素**：
1. **URL 路径表**：每个旧 controller 的 `@RequestMapping` 路径与方法（如 `GET /api/v1/clinical/cdss/alerts`），作为 E6 业务服务包装的 URL 起点
2. **DTO 字段表**：每个旧 DTO 的字段名、类型、业务含义（如 `CdssAlert.adoptionRate`、`PathwayTemplate.status`），作为 E6 接口契约设计起点
3. **状态机迁移**：每个旧状态枚举（如 `pending → adopting → closed/remediating`），作为 E6 状态机设计起点

**不迁入**：实现代码、SEED 数据、mock 逻辑、`Map<String, Object>` 返回结构。

**理由**：
- W3-W7 业务规划是历史沉淀的设计精华，但 Java 实现是 mock，价值不在代码而在设计意图
- 全删可让工作树彻底干净；保留精华到文档可让 E6 阶段可参考
- 暂封方案会让 ESLint/grep 门禁需要 ignore，长期维护负担大

### 3.2 前端处理策略 — **方案 ① 升级版：占位卡 + 任务 ID + 路线图**

> 不动现行业务域菜单与路由，每个被净化的业务页统一改造为：
> ```tsx
> <PageState
>   state="disabled"
>   title="等待引擎能力上线"
>   description="本页依赖 [GA-ENG-XXX]，引擎完成后激活"
>   actions={<RoadmapLink taskIds={["GA-ENG-XXX"]} />}
> />
> ```

**理由**：
- 现行业务域菜单以核心宪法与代码目录为准，不与施工域做 1:1 映射
- "真接到 mock 后端"是最大欺骗源（CdssFatigue / Mpi / PathwayTemplates），必须一并下线占位
- 用占位卡 + Backlog 任务 ID + 路线图链接，**比有 mock 数据更专业、更诚实、更便于集成商理解**
- 引擎上线后激活仅需替换 PageState 为 hook + 真实组件，零路由变更

### 3.3 PR 切片策略 — **3 个顺序 PR**

> PR-1 门禁先行 → PR-2 后端净化 → PR-3 前端净化。

**理由**：
- PR-1 改动轻、风险低，先锁门禁；阻止后续 PR 引入视觉债
- PR-2 与 PR-3 解耦：后端删 controller 不影响前端 typecheck（前端 hook 走 axios string path，不依赖后端类型）
- 顺序 PR 便于独立 revert，回滚成本最小

---

## 4 · 净化清单

### 4.1 后端删除清单（共 47 个 Java 文件）

> 注：以下路径相对 `medkernel-backend/src/main/java/com/medkernel/`。

| 模块 | 文件清单 |
|---|---|
| `advanced/llm/*` | `LlmRequest`、`LlmExplain`、`LlmExplainController`、`LlmResponse`、`LlmProvider`、`LlmGateway`、`LlmVersionController`、`SaasFallbackProvider`、`OllamaMockProvider` (9) |
| `advanced/chatbot/*` | `ComplianceChatbotController` (1) |
| `advanced/academic/*` | `AcademicExportController` (1) |
| `advanced/domestic/*` | `DomesticCheckController` (1) |
| `quality/variance/*` | `PathwayVarianceController` (1) |
| `quality/ncis/*` | `NcisReportController` (1) |
| `quality/medicalrecord/*` | `MedicalRecordHomePageController` (1) |
| `quality/insurance/*` | `DrgRulesetController`、`DrgRuleset` (2) |
| `clinical/mpi/*` | `MpiController`、`MpiPatient`、`FederationController` (3) |
| `clinical/cdss/*` | `CdssController`、`CdssAlert` (2) |
| `clinical/udi/*` | `UdiCheckController` (1) |
| `clinical/publichealth/*` | `PublicHealthReportController` (1) |
| `tenant/rule/*` | `RuleValidateRequest`、`RuleValidateResult`、`Rule`、`RuleController` (4) |
| `tenant/hrp/*` | `HrpInteropController` (1) |
| `tenant/pathway/*` | `PathwayController`、`PathwayTemplate` (2) |
| `platform/branding/*` | `BrandingController` (1) |
| `platform/success/*` | `CustomerSuccessController` (1) |
| `platform/license/*` | `OfflineLicenseController` (1) |
| `platform/emergency/*` | `EmergencyPlanController` (1) |
| `compliance/tenant/*` | `TenantWallController` (1) |
| `compliance/dr/*` | `DisasterRecoveryController` (1) |
| `compliance/signature/*` | `PhysicianSignatureController` (1) |
| `compliance/dataexport/*` | `DataExportAssessmentController` (1) |
| `compliance/dlm/*` | `DataLifecycleController` (1) |
| `compliance/waf/*` | `WafController` (1) |
| `compliance/tsa/*` | `TsaService` (1) |
| `compliance/masking/*` | `MaskingProfile`、`MaskingService`、`MaskingController` (3) |

**配套删除**：上述模块对应的测试文件（保留 `compliance/audit/*` 测试不动）。

**保留**：
- `engine/*`（新引擎核心）
- `shared/*`（基础设施）
- `compliance/audit/*`（BASE-04 审计链合法产物）

### 4.2 前端净化清单

**显式 `MOCK = [` 删除（15 文件）**：

| 路径 | 任务 ID 锚点 |
|---|---|
| `pages/clinical/PatientPathways.tsx` | GA-ENG-PATH-01 + GA-ENG-API-06 |
| `pages/clinical/WorkflowTodos.tsx` | GA-SVC-CLINICAL-03（待 E6） |
| `pages/clinical/Notifications.tsx` | GA-SVC-CLINICAL-03（待 E6） |
| `pages/quality/AiReview.tsx` | GA-ENG-CDSS-01 + GA-ENG-EVAL-01 |
| `pages/quality/InsuranceAudit.tsx` | GA-SVC-QUALITY-02（待 E6） |
| `pages/quality/QcAlerts.tsx` | GA-ENG-EVAL-01 |
| `pages/quality/QcEvalResults.tsx` | GA-ENG-EVAL-01 |
| `pages/quality/QcEvalSets.tsx` | GA-ENG-EVAL-01 |
| `pages/tenant/AdapterHub.tsx` | GA-SVC-PILOT-02（待 E6） |
| `pages/tenant/ConfigPackages.tsx` | GA-ENG-PKG-01 |
| `pages/tenant/TerminologyMapping.tsx` | **改连接** engine/terminology 真接口（已上线） |
| `pages/tenant/RuleDefinitions.tsx` | GA-ENG-RULE-01 + GA-ENG-API-05 |
| `pages/advanced/AiWorkflows.tsx` | GA-ENG-LLM-01 |
| `pages/compliance/AdminUsers.tsx` | GA-SVC-COMPLIANCE-01（待 E6） |
| `features/tenant-lifecycle/TenantLifecyclePanel.tsx` | GA-SVC-PILOT-01（待 E6） |

**隐藏硬编码常量删除（4 文件）**：

| 路径 | 常量 | 处置 |
|---|---|---|
| `pages/quality/QcDashboard.tsx` | `DEPTS` | 占位（GA-ENG-EVAL-01） |
| `pages/compliance/SecurityBaseline.tsx` | `ITEMS` | 占位（GA-SVC-COMPLIANCE-01） |
| `pages/advanced/DevConsole.tsx` | `LINKS` | 占位（待评估是否保留为开发工具页） |
| `pages/compliance/IdentityBinding.tsx` | `PROVIDERS` | 占位（GA-SVC-COMPLIANCE-01） |

**已接 mock 后端的 hook 页面占位**（额外清单，PR-3 时全量扫描）：

| 路径 | 已接 hook | 处置 | 任务 ID 锚点 |
|---|---|---|---|
| `pages/clinical/CdssFatigue.tsx` | `useCdssAlerts` / `useCdssDecide` | 改占位 | GA-ENG-CDSS-01 |
| `pages/tenant/PathwayTemplates.tsx` | `usePathwayTemplates` / `usePublishPathway` | 改占位 | GA-ENG-PATH-01 + GA-ENG-API-06 |
| `pages/clinical/Mpi.tsx` | `useMpiPatients` / `useMpiStats` | 改占位 | GA-SVC-CLINICAL-01 |
| `pages/clinical/RuleValidate.tsx` | （PR-3 时核实） | 改占位 | GA-ENG-RULE-01 |

**已接 mock 后端的 hook 删除**：`shared/api/hooks.ts` 内：
- `useMpiPatients`、`useMpiStats`、`useMpiMerge` 等 MPI hooks
- `useCdssAlerts`、`useCdssDecide` 等 CDSS hooks
- `usePathwayTemplates`、`usePublishPathway` 等路径 hooks
- 其他指向旧 endpoint 的 hook（按 PR-3 时点全量梳理）

**保留**：
- `useSecurityProfile`（指向 `engine/security/me` 真接口）
- `useAuditSnapshot`（指向 `compliance/audit` 真接口）
- 所有指向 `engine/*` 的 hooks

**特殊处理 — TerminologyMapping**：
- `engine/terminology` 引擎已完整上线（commit cb39796），前端 `TerminologyMapping.tsx` 的 MOCK 应改为真接 engine/terminology API，**不走占位**
- 这是 PR-3 中**唯一一处真接入**示例，演示后续 E2/E3 上线后业务页激活的标准模式

---

## 5 · PR-1 · 门禁先行

### 5.1 范围
1. 追加业务场景详细规范的 E6 附录（按 §3.1 的三类要素）：
   - **URL 路径表**：25 个旧 controller 的 `@RequestMapping` 路径与方法签名
   - **DTO 字段表**：旧 DTO 的字段名、类型、业务含义
   - **状态机迁移**：旧状态枚举（如 `pending → adopting → closed/remediating`）
   - 不复制 SEED 数据、mock 逻辑、`Map<String,Object>` 结构
2. 修订 `docs/backlog.md` 至 4.10：
   - `GA-ENG-API-03 知识资产 API` → done（commit ddfb950 + engine/knowledge 完整模块）
   - `GA-ENG-API-04 字典映射 API` → done（commit cb39796 + engine/terminology 完整模块）
   - `GA-ENG-KNOW-01`、`KNOW-02` → partial done（标注余下范围：版本仲裁、原子替换、旧版隔离）
   - `GA-ENG-TERM-01` → partial done（标注余下范围：候选推荐算法、冲突仲裁界面）
   - 新增 `GA-ENG-BASE-09` 状态 in_progress
3. `.github/workflows/ci.yml` 新增 `frontend-lint` job：
   - `npm install --no-audit --no-fund --no-package-lock`
   - `npm run lint`
   - 加入必需 check 列表（与 `backend-build-test`、`guard-rules` 并列）
4. `openspec/changes/{audit-event-persistence, containerized-development-platform}` 整目录移动到 `openspec/archive/`
   - 在 `openspec/archive/README.md` 补一句说明：这两份历史变更已实质落地，归档留痕

### 5.2 验收
- CI 全绿
- `docs/backlog.md` 修订记录有 4.10 条目
- `openspec/changes/` 只剩 `README.md`
- 6 张权威文档 check 通过（已有的 guard-rules 已保证）
- PR 描述中文、符合 AGENTS.md 模板

### 5.3 回滚
独立 revert PR-1 即可，不影响 PR-2/3。

---

## 6 · PR-2 · 后端净化（核心删除）

### 6.1 范围
1. 删除 §4.1 列出的 47 个 Java 文件
2. 删除对应测试（grep `test/java` 下与旧 controller 配对的测试文件）
3. 净化 `shared/observability/BusinessMetrics`：
   - 删除只被旧 controller 引用的方法（如 `incCdssAlerts`、`incPathwayPublish`）
   - 保留通用 traceId / 五方言可观察方法
4. 五方言迁移核查：
   - grep `db/migration/{h2,postgres,oracle,dm,kingbase}/*.sql` 是否有旧业务表
   - 有则补 `V7__drop_legacy_business_tables.sql`（5 方言并列）
   - 没有则在 PR 描述中确认"无旧业务表残留"
5. 删除 application.yml / properties 中只服务于旧模块的配置项（如 `medkernel.llm.ollama.*`、`medkernel.dify.*`，逐项核查再决定）

### 6.2 验收
- `mvn -B -q test` 全绿
- JaCoCo 覆盖率不降（删测试 + 删代码同时进行）
- `grep -rn "Map<String, Object>" src/main/java` 归零（裸 Map 归零）
- `grep -rnE "private static final List<[A-Za-z]+> SEED\s*=" src/main/java` 归零（SEED 硬编码数组归零）
- `grep -rnE "new [A-Za-z]+\([\"']\w+[\"'].*\)," src/main/java/com/medkernel/{advanced,quality,clinical,tenant,platform}` 归零（旧模块的内联硬编码示例数据归零）
- 五方言 smoke 通过
- 启动后端 `mvn spring-boot:run` → `/medkernel/actuator/health` 健康；五菜单对应的旧 endpoint 全部返回 404（如 `/api/v1/clinical/cdss/alerts` 不存在）

### 6.3 回滚
独立 revert PR-2，前端 PR-3 不依赖后端代码层，受影响仅为已删 hook 的复活（PR-3 顺序未到则未删）。

---

## 7 · PR-3 · 前端净化（占位卡 + 数据下线）

### 7.1 范围
1. 新增 `shared/ui/RoadmapLink.tsx`：
   - props：`taskIds: string[]`
   - 渲染为可点击链接，跳转到 `/docs/backlog.md#{kebab(taskId)}` 锚点；或弹出 modal 展示该任务的标题、所属阶段、owner、status
   - 已带统一样式与图标
2. 改造 §4.2 列出的 16 + 5 = 21 处文件，统一使用占位卡模板：
   ```tsx
   <PageShell title="..." description="...">
     <PageState
       state="disabled"
       title="等待引擎能力上线"
       description={`本页依赖 ${taskIds.join(' / ')}，引擎完成后激活`}
       actions={<RoadmapLink taskIds={taskIds} />}
     />
   </PageShell>
   ```
3. `TerminologyMapping.tsx` 特殊处理：改为真接 engine/terminology API（已上线），作为后续业务页激活的样板
4. 删除 `shared/api/hooks.ts` 中所有指向旧 endpoint 的 hook
5. 新增 ESLint 自定义规则 `medkernel/no-page-mock`：
   - 禁止 `src/pages/**/*.tsx` 与 `src/features/**/*.tsx` 出现 `const [A-Z_]+\s*=\s*\[` 形式
   - error 级别
   - 单测覆盖 + 文档说明

### 7.2 验收
- `npm run verify` 全绿（lint + format + typecheck + test）
- `grep -rn "const MOCK\|const DEPTS\|const ITEMS\|const LINKS\|const PROVIDERS" src/pages src/features` 归零
- `grep -rn "/api/v1/clinical/cdss\|/api/v1/clinical/mpi\|/api/v1/tenant/pathways\|/api/v1/quality/insurance\|/api/v1/advanced/llm\|/api/v1/clinical/udi\|/api/v1/quality/ncis" src` 归零
- 手动 smoke：登录 → 五菜单全部点开 → 每个被占位页正确显示 `state=disabled` + 路线图链接可点击
- TerminologyMapping 页可看到真实的标准/本地术语数据
- E2E 测试通过（如 `playwright`）

### 7.3 回滚
独立 revert PR-3 即可。

---

## 8 · 整体验收门槛

**Hard gate**（任一失败回滚对应 PR）：
- CI 全绿（含新 lint job）
- 6 张权威文档存在性 check 通过
- 后端 `mvn test` + 前端 `npm run verify` 通过
- 五方言 smoke 通过
- 手动 smoke：登录 → 五菜单 + 1 高级工具菜单全部点开 → 占位卡正常显示 + 路线图链接可达；TerminologyMapping 真接口正常
- 全 PR 链合并到远程 main 后，backlog 中 `GA-ENG-BASE-09` 标 done

**Soft gate**（不阻塞合并但记录跟踪）：
- 后续 BASE-08 设计时审视占位卡视觉细节，必要时统一升级

---

## 9 · 后续衔接

BASE-09 全部合并后立即进入 **BASE-08 产品体验底座** 的 brainstorming，处理：
- 一页一目标
- 角色默认视图
- 专家模式
- 服务端分页
- 详情抽屉
- 异步导出
- 保存视图

BASE-08 完成后才进入 E2 余下 11 项 API（API-01/02/05/06/07/08/09/10/11/12/13；API-03、API-04 已 done）。

---

## 10 · 设计取舍备忘

- **保留 menu 不动**：节省宪法变更成本，未来 E6 业务包装上线时占位卡平滑替换
- **不归档旧 Java 代码**：git history 保留即可，不在工作树留死代码
- **业务场景详细规范追加 E6 附录**：把旧 W3-W7 设计精华作为参考资料而非"待复活"代码
- **TerminologyMapping 真接入示范**：演示后续业务页激活范式，减小未来 PR review 摩擦
- **CI 补 lint 在 PR-1**：先锁门禁，再删代码，反向操作会让删除中途有视觉债残留

---

**End of GA-ENG-BASE-09 设计文档。**
