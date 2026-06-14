# 文档体系重构 · P0 基础骨架 实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 立起新文档体系的 P0 基础骨架——把宪法重写为"按 11 视角分章的极简核心"，转化体验规范为共享体验契约，建立施工卡/域简报模板、场景→卡索引、旧锚点→新卡覆盖矩阵，并改写 AGENTS.md 阅读链；为 D0–D6 逐域搬迁备好地基。

**Architecture:** 四层 IA（核心恒读 + 域简报 + 施工卡 + 索引）。本计划只做 P0（地基），不写任何一张业务卡内容（那是 Plan 2 起的逐域搬迁）。**4 个旧巨物（详规/落地规划/FOUNDATION/体验规范）本计划不删**——它们在各自域搬迁完成前物理保留但不再权威，全部域搬完后于 P8 删除。设计依据见 [设计规格](../specs/2026-05-30-doc-architecture-build-cards-design.md)。

**Tech Stack:** Markdown 文档；验证用 `grep`/`wc`/`git`，无运行时代码、无测试框架。"测试先行"在本计划里 = "先定验证命令与期望、再产出文档、再跑验证"。

**内容迁移类任务的说明（务必读）：** 核心重写、体验契约、术语表是**内容迁移**，无法把成稿全文塞进计划。这类任务给出：① 强制章节标题；② 每节必须覆盖的内容与**从哪份旧文档哪一节迁来**；③ 可执行的 `grep` 验证（断言关键不变量 token 出现）。执行者据此**整合旧源、撰写成稿**，再跑验证。这不是占位符——要求精确且可验证。模板/索引/AGENTS 等小件则**全文内联**，照抄即可。

---

## 文件结构

| 动作 | 文件 | 职责 |
|---|---|---|
| 新建 | `docs/cards/_template.md` | 施工卡模板（全 175 卡复制它）= 合同结构 |
| 新建 | `docs/cards/_brief-template.md` | 域简报模板 |
| 新建 | `docs/cards/_coverage-matrix.md` | 迁移神谕：旧文档全锚点 → 新卡/核心§ 覆盖追踪 |
| 新建 | `docs/cards/_index.md` | 场景 S0–S40 → 卡 索引 + 全卡目录（按域） |
| 新建 | `docs/EXPERIENCE_CONTRACT.md` | 共享体验与组件契约（页面卡读），由体验规范转化 |
| 新建 | `docs/glossary.md` | 术语表（客户面 vs 技术词） |
| 改写 | `docs/CONSTITUTION.md` | 重写为按 11 视角分章的极简核心 |
| 改写 | `AGENTS.md` | §文档权威顺序：7 文档链 → 核心+域简报+卡 |
| 改写 | `docs/README.md` | 文档中心导航更新到新结构 |
| 不动/不删 | 详规/落地规划/FOUNDATION/体验规范/backlog/质量基线 | 过渡期保留；删除在 P8（另计划） |

执行顺序：先模板与神谕（T1–T4）→ 转化件（T5–T6）→ 核心重写（T7–T9）→ 阅读链与导航（T10–T11）→ P0 一致性门（T12）。

---

## Task 1: 施工卡模板 `_template.md`

**Files:**
- Create: `docs/cards/_template.md`

- [ ] **Step 1: 定验证（先失败）**

Run: `test -f docs/cards/_template.md && echo EXIST || echo MISSING`
Expected: `MISSING`

- [ ] **Step 2: 写模板（全文照抄）**

```markdown
# 施工卡模板（_template）

> 用法：复制本模板为 `docs/cards/<域>/<TASK-ID>.md`。每段必填；不涉及的视角写 `N·A + 理由`，不得留空。
> 阅读前置：先读 `docs/CONSTITUTION.md`（核心）与本域 `_brief.md`；页面卡另读 `docs/EXPERIENCE_CONTRACT.md`。
> 本模板根源：详规 §10.1 场景实现卡模板 + 设计规格 §5③（加 11 视角清单 + 原子可测条目 + 与 backlog 对齐）。

## 身份
- 卡 ID：<= backlog 任务 ID，如 BASE-01>
- 域：<D0..D6 / wave2 / ga>
- 关联场景：<S-x；无则 N·A>
- 依赖卡：<上游卡 ID 列表>
- 工作量：<Nd>
- owner / reviewer：<owner ≠ reviewer>

## 目标
<一句话：本卡交付什么可验证的产品能力>

## 功能要求（原子可测条目）
- [ ] FR-1：<一个可验证断言：输入 → 期望输出/行为>
- [ ] FR-2：…
（每条能直接写成一条测试；禁散文式要求）

## 接口契约 / 页面契约
### 接口契约（引擎/API 卡）
- 端点：<METHOD /api/v1/...>
- DTO：<Record DTO + Bean Validation 字段>
- 响应信封：ApiResult / ProblemDetail
- 状态机：<选 4 套之一：配置 / 变更 / 待办 / 告警>
- 幂等 / 错误码 / traceId：…
### 页面契约（页面卡）
- 路由元数据：sectionKey / menuKey / menuLabel / requiredPermissions / requiredRoles
- 结构：PageShell + 六态（加载/空/错误/无权限/部分成功/正常）
- 主按钮 ≤1 / 默认筛选 ≤3 / 默认角色视图
- 五维 RBAC 点位：菜单 / 动作 / 数据 / 资产 / 环境
- 样式：仅引用核心 §设计token 与 EXPERIENCE_CONTRACT 组件契约（禁硬编码 hex/px）

## 数据与迁移
- 表族 / 主键 / 唯一约束 / 索引 / 组织字段 / 状态 / 版本 / 审计字段
- 5 方言迁移（h2/postgres/oracle/dm/kingbase）或"明确不落库原因"

## 视角清单（11 视角逐条：✓ 填要求 or N·A+理由；留空即不得 done）
1. 产品架构：
2. 产品体验：
3. 系统与数据架构（含性能规模 10万级/并发幂等/P95）：
4. 临床医疗安全：
5. 知识与数据治理：
6. 安全合规与监管：
7. 集团化与多租户治理：
8. 集成与互操作：
9. 运维 / SRE / 国产化交付：
10. 质量与真实性审计：
11. AI / 模型治理与可降级：

## 适用不变量
- 命中核心约束：<点编号，如 #6 #8 #10 / 铁律#1 #4>；本卡落点：<一句话如何满足>
（点编号，不抄原文）

## 验收 + 验证
- [ ] AC-1：<对应 FR-1 的可举证验收：命令 / 用例 / 期望>
- [ ] AC-2：…
- 关联 A1–A9 剧本：<若涉及>
- T-GATE：前后端真实性门禁全绿
- B0 验收：<关闭模型后该能力仍可用的判据；无 AI 则 N·A>

## 完工证据
- 代码 permalink ×N / 单测·契约·E2E / 迁移文件 / 运行截图 / traceId / 审计员签字（owner≠reviewer）

## 大卡工序（>~5d 或 后端+多层前端 才填；仍是一张卡一份合同）
- PR1：<迁移+门禁> → 验收
- PR2：<后端契约> → 验收
- PR3：<前端页面> → 验收
```

- [ ] **Step 3: 跑验证（应通过）**

Run: `grep -cE '^## (身份|目标|功能要求|视角清单|适用不变量|验收|完工证据)' docs/cards/_template.md`
Expected: `7`（七个必备段落标题命中）

Run: `grep -c '^[0-9]\{1,2\}\. ' docs/cards/_template.md`
Expected: ≥ `11`（视角清单 11 条都在）

- [ ] **Step 4: 提交**

```bash
git add docs/cards/_template.md
git commit -m "docs(cards): 施工卡模板（合同结构=详规§10.1+11视角清单+原子条目）"
```

---

## Task 2: 域简报模板 `_brief-template.md`

**Files:**
- Create: `docs/cards/_brief-template.md`

- [ ] **Step 1: 定验证（先失败）**

Run: `test -f docs/cards/_brief-template.md && echo EXIST || echo MISSING`
Expected: `MISSING`

- [ ] **Step 2: 写模板（全文照抄）**

```markdown
# 域简报模板（_brief-template）

> 用法：复制为 `docs/cards/<域>/_brief.md`。本域所有卡共享的上下文放这里，**不在卡间复制**。

## 域目标
<一句话>

## 登入 / 使用角色
<13 角色矩阵的本域子集 + 每角色在本域主要干什么>

## 共享数据模型 / 实体
<本域核心实体与关系>

## 依赖
- 上游引擎 / API：<…>
- 上游卡：<…>

## 本域涉及
- 租户层级：<平台/集团/医院/院区/社区/科室/专病 中本域涉及的层级>
- 外部系统：<HIS/EMR/LIS/PACS/医保/区域 等本域涉及的>

## 本域最烫的不变量
<点核心编号 + 为何在本域最关键。例 D3：#10 医师确认、低打扰、B0 CDSS>

## 域级验收（D-验收）
<本域全部卡 done 后的域级验收：登入角色 → 逐页六态可达 → 跑通本域 B0 主链路 E2E → T-GATE 全绿 → owner≠reviewer 签字>
```

- [ ] **Step 3: 跑验证（应通过）**

Run: `grep -cE '^## (域目标|登入|共享数据模型|依赖|本域涉及|本域最烫的不变量|域级验收)' docs/cards/_brief-template.md`
Expected: `7`

- [ ] **Step 4: 提交**

```bash
git add docs/cards/_brief-template.md
git commit -m "docs(cards): 域简报模板"
```

---

## Task 3: 覆盖矩阵 `_coverage-matrix.md`（迁移神谕）

迁移保真的核心工具：把 4 个旧源的**全部锚点**枚举成表，每行一个旧锚点，"迁入"列在逐域搬迁时填"哪张卡 / 核心§"。P0 只建表并填满"旧锚点"列，"迁入"列暂留 `待迁`。

**Files:**
- Create: `docs/cards/_coverage-matrix.md`

- [ ] **Step 1: 先采集旧锚点清单（这是"期望行数"的来源）**

Run:
```bash
{ echo "== 详规 =="; grep -nE '^#{2,4} ' docs/MEDKERNEL_BUSINESS_SCENARIO_DETAIL_SPEC.md; \
  echo "== 落地规划 =="; grep -nE '^#{2,3} ' docs/MEDKERNEL_IMPLEMENTATION_LANDING_PLAN.md; \
  echo "== FOUNDATION =="; grep -nE '^#{2,3} ' docs/MEDKERNEL_FOUNDATION_AND_SERVICES.md; \
  echo "== 体验规范 =="; grep -nE '^#{2,3} ' docs/MEDKERNEL_PRODUCT_EXPERIENCE_RULES.md; \
  echo "== 详规场景 S=="; grep -nE '^\| S[0-9]+ ' docs/MEDKERNEL_BUSINESS_SCENARIO_DETAIL_SPEC.md; \
} | tee /tmp/old-anchors.txt | grep -c .
```
Expected: 一个总行数 N（旧锚点总数，含 S0–S40 行）。记下 N。

- [ ] **Step 2: 写覆盖矩阵（结构照抄 + 用 Step1 输出逐行填"旧锚点"列）**

文件头与表头照抄，然后把 `/tmp/old-anchors.txt` 的每条锚点写成一行（"迁入"列填 `待迁`）：

```markdown
# 旧文档 → 新卡 覆盖矩阵（迁移神谕）

> 用途：保证拆解巨物时零要求丢失。每行 = 一个旧锚点；逐域搬迁时把"迁入"从 `待迁` 改为承接它的"卡ID / 核心§ / 域简报 / EXPERIENCE_CONTRACT§ / glossary"。
> 退役门（P8）：本表"迁入"列 0 个 `待迁`，且旧锚点在新文档零残留引用，才允许删除 4 巨物。

| 旧文档 | 旧锚点（章节/场景） | 迁入（卡ID / 核心§ / 其它） | 核对人 |
|---|---|---|---|
| 详规 | §1.x … | 待迁 | |
| 详规 | S0 … | 待迁 | |
| … | …（逐条来自 /tmp/old-anchors.txt） | 待迁 | |
| 落地规划 | §x … | 待迁 | |
| FOUNDATION | §x … | 待迁 | |
| 体验规范 | §x … | 待迁 | |

## 进度
- 旧锚点总数：N
- 已迁（非"待迁"）：0
- 退役就绪：否（待全部已迁）
```

- [ ] **Step 3: 跑验证（应通过）**

Run: `grep -c '待迁' docs/cards/_coverage-matrix.md`
Expected: 约等于 N（每个旧锚点一行 `待迁`）。与 Step1 的 N 比对，相差应仅为表头/说明里的固定文字。

- [ ] **Step 4: 提交**

```bash
git add docs/cards/_coverage-matrix.md
git commit -m "docs(cards): 旧文档→新卡 覆盖矩阵（迁移保真神谕，全锚点入表）"
```

---

## Task 4: 场景→卡 索引 `_index.md`

**Files:**
- Create: `docs/cards/_index.md`

- [ ] **Step 1: 采集 S 场景与 backlog 任务 ID（索引的两份来源）**

Run: `grep -oE '^\| S[0-9]+ \| [^|]+' docs/MEDKERNEL_BUSINESS_SCENARIO_DETAIL_SPEC.md | head -50`
Expected: 列出 S0…S40 及名称（用于"场景→卡"表左列）。

Run: `grep -oE '^\| [A-Z][A-Z0-9-]+ ' docs/backlog.md | sort -u`
Expected: 列出 backlog 任务 ID（BASE-01…、API-…、SVC-…、AIK-… 等，用于"全卡目录"）。

- [ ] **Step 2: 写索引（结构照抄；左列 S 来自 Step1，卡目录来自 backlog 任务 ID 按域归类）**

```markdown
# 卡索引（场景 → 卡 + 全卡目录）

> 用途：找卡，不通读。S0–S40 → 拥有其要求的卡；全卡目录按域列出所有卡（= backlog 可交付物）。
> 迁移中：未建的卡在"卡"列写 `待建`。

## 场景 → 卡
| 场景 | 名称 | 拥有要求的卡 |
|---|---|---|
| S0 | … | 待建 |
| …（S0–S40 全列，来自详规） | | 待建 |

## 全卡目录（按域）
### D0 登录域/平台脊柱
- BASE-01 … BASE-11 / OBS-01 / API-13 / SYS-01..05 / INFRA-01..08 / SUPERADMIN-01 / CONFIG-01 …（待建）
### D1 工作台 … D6 高级工具
…（按 backlog 各域任务 ID + 页面卡，待建）
### 第二波 wave2
- AIK-STD-01..12 / LLM-01..08 / KNOWGEN-01..15 / 领域门面 …（待建）
### GA 验收 ga
- QA-01..08 / DEGRADE-01 / SYS-07 / INFRA-07/10 …（待建）
```

- [ ] **Step 3: 跑验证（应通过）**

Run: `grep -c '^| S[0-9]' docs/cards/_index.md`
Expected: `41`（S0–S40 全部在场景表）

Run: `grep -cE '^### (D0|D1|D2|D3|D4|D5|D6|第二波|GA)' docs/cards/_index.md`
Expected: ≥ `9`（各域 + wave2 + ga 分节齐全）

- [ ] **Step 4: 提交**

```bash
git add docs/cards/_index.md
git commit -m "docs(cards): 场景→卡 索引 + 全卡目录骨架"
```

---

## Task 5: 共享体验契约 `EXPERIENCE_CONTRACT.md`（转化体验规范）

**内容迁移类。** 把 [体验规范](../../MEDKERNEL_PRODUCT_EXPERIENCE_RULES.md) 的**页面/组件级模式**迁入本契约（页面卡读）；其**全局硬规则**（一页一目标、≤3筛选、1主按钮、AI 标识等）由核心承接（Task 7/8），本契约只点"详见核心§"。

**Files:**
- Create: `docs/EXPERIENCE_CONTRACT.md`

- [ ] **Step 1: 定验证（先失败）**

Run: `test -f docs/EXPERIENCE_CONTRACT.md && echo EXIST || echo MISSING`
Expected: `MISSING`

- [ ] **Step 2: 写契约（强制章节 + 迁移源 + 内容要求）**

必备章节（`##` 级）与各自必须覆盖的内容、迁移来源：

1. `## 页面类型与固定结构` ← 迁自体验规范 §4.2：工作台/列表/配置/审核台/详情/嵌入面板/驾驶舱/专家工具 八类各自固定结构。
2. `## 页面交互固定规则` ← 迁自 §5：主按钮≤1、默认筛选≤3、表格≤8列、详情抽屉不刷新列表、**六态定义**、危险动作二次确认、草稿保护、可撤销。
3. `## 六态组件契约` ← 迁自 §5 + §13：加载/空/错误/无权限/部分成功/正常；错误态含中文原因+重试+traceId 复制；无权限不暴露敏感数据+申请入口；部分成功含成功数/失败数/失败明细/重试。
4. `## 大规模列表与性能门槛` ← 迁自 §6：服务端分页（默认20，50/100）、游标、保存视图、异步导出留审计；性能门槛 首屏 P95≤1s、筛选 P95≤2s、详情不重载列表。
5. `## 临床嵌入与低打扰` ← 迁自 §8：嵌入轻量卡/抽屉、打扰三级（信息/弱打断/红线强打断）、降级"智能建议暂不可用"不阻断主流程、反馈回传审计、最小数据。
6. `## 驾驶舱契约` ← 迁自 §9：每指标可下钻到对象/责任人/科室/待办；禁无动作装饰图；先异常后全量。
7. `## 表单/校验/发布契约` ← 迁自 §10：>7字段分组、>15字段步骤化、字段级实时校验、发布前影响分析、发布完成展示范围/同步/回滚点/证据入口。
8. `## 文案与视觉` ← 迁自 §11：按钮"动词+对象"、颜色只表状态、正文14px/老年≥16pt、可访问性（对比度/键盘可达/焦点态）、5 主题模式（default/elder/dark/eye/system）。
9. `## 与核心的边界` ← 一句话指针："一页一目标/≤3筛选/1主按钮/AI 标识/菜单 IA 锁/设计 token 为全局硬约束，详见核心 §产品体验 与 §设计token；本契约只定可复用的页面/组件模式。"

文首加：`> 定位：页面卡读本契约获取可复用页面/组件模式；全局硬约束在核心。不重复核心已述的规则原文。`

- [ ] **Step 3: 跑验证（应通过）**

Run: `grep -cE '^## (页面类型|页面交互|六态组件契约|大规模列表|临床嵌入|驾驶舱契约|表单|文案与视觉|与核心的边界)' docs/EXPERIENCE_CONTRACT.md`
Expected: `9`

Run: `grep -E 'traceId|六态|P95|老年|采纳' docs/EXPERIENCE_CONTRACT.md | grep -c .`
Expected: ≥ `5`（关键体验 token 已迁入）

- [ ] **Step 4: 提交**

```bash
git add docs/EXPERIENCE_CONTRACT.md
git commit -m "docs: 共享体验与组件契约（由体验规范转化，页面卡读；全局规则归核心）"
```

---

## Task 6: 术语表 `glossary.md`

**内容迁移类。** 合并 [宪法 §7 术语表](../../CONSTITUTION.md) 与详规中的术语条目，形成"客户面（推荐）/ 不要这么说 / 含义"三列表。

**Files:**
- Create: `docs/glossary.md`

- [ ] **Step 1: 采集现有术语**

Run: `grep -nE '试点准备|临床运行|质控改进|合规运维|配置包|提醒治理|灰度发布|院级驾驶舱|来源追溯|国密|国产化自检|MPI|DRG|DIP' docs/CONSTITUTION.md | head`
Expected: 命中宪法 §7 术语表区域（用作迁移源）。

- [ ] **Step 2: 写术语表（结构照抄；条目迁自宪法§7 + 详规术语）**

```markdown
# 术语表（医院听得懂的话 vs 技术名词）

> 定位：核心 §术语指针指向本表。客户面菜单/按钮/列名严禁技术缩写（除非已是医院术语，如 DRG/MPI/ICD）。

| 客户面（推荐） | 不要这么说 | 含义 |
|---|---|---|
| 试点准备 | 系统初始化 / 实施配置 | 医生用之前实施+信息科要做的全部配置 |
| 临床运行 | 临床嵌入器 / 临床应用层 | 医生日常用的提醒、待办、路径执行 |
| 质控改进 | 数据中台 / 分析平台 | 院长/医务处/质控办看效果 |
| 合规运维 | DevSecOps / SRE | 信息科主任+合规审计的工具 |
| 配置包 | Asset Bundle / 资产包 | 路径+规则+字典+适配器配置打包 |
| 提醒治理 | CDSS 疲劳治理 | 治理"医生收到太多无用提醒" |
| 灰度发布 | Canary Deploy | 先发 10% 床位/一个科室验证 |
| 院级驾驶舱 | Executive Dashboard | 院长/医务处看的全院总览 |
| 来源追溯 | Provenance | 提醒可追溯到指南/文献/知识库哪一条 |
| 国密 | SM2/SM3/SM4 | 国家商用密码标准算法 |
| 国产化自检 | Domestication Check | 检查 OS/JDK/DB/中间件国产化程度 |
| MPI | Master Patient Index | 患者主索引 |
| DRG / DIP | Diagnosis Related Groups / Diagnosis Intervention Packet | 医保付费分组 |
（如详规另有术语条目，一并补入，不自创同义词）
```

- [ ] **Step 3: 跑验证（应通过）**

Run: `grep -c '^| ' docs/glossary.md`
Expected: ≥ `14`（表头 + ≥13 术语行）

- [ ] **Step 4: 提交**

```bash
git add docs/glossary.md
git commit -m "docs: 术语表 glossary（迁自宪法§7+详规术语）"
```

---

## Task 7: 核心重写 Part A —— 结构性不变量（§0–§5）

**内容迁移类（最关键）。** 把 [CONSTITUTION.md](../../CONSTITUTION.md) 重写为按 11 视角分章的核心。Part A 覆盖结构性不变量。**保留宪法现有 #1–#18 全文要义并新增 #19/#20**（来自 [超管/配置设计](../specs/2026-05-30-plan-hardening-superadmin-config-design.md)）。

**Files:**
- Modify: `docs/CONSTITUTION.md`

- [ ] **Step 1: 基线快照（迁移前留底，便于核对不丢约束）**

Run: `awk '/^## 1\./{f=1;next} /^## 2\./{f=0} f' docs/CONSTITUTION.md | grep -cE '^\| ([0-9]|1[0-8]) \|'`
Expected: `18`（§1 段内现有 18 条硬约束；awk 限定 §1 区块，避开 §2 菜单表的序号行污染）

Run: `wc -m docs/CONSTITUTION.md`
Expected: 记下当前字数（约 23784）。

- [ ] **Step 2: 重写 §0–§5（强制章节 + 内容要求 + 迁移源）**

按序写以下章节，整合旧宪法对应处、去散文：

- `## §0 产品定位与命名` ← 旧宪法 §0：集团医疗智能中枢；中文产品名/英文代码名/内部能力名；面向客户四件事；当前节奏（按业务域纵向推进·两波）。
- `## §1 不可妥协的硬约束` ← 旧宪法 §1 的 #1–#18 **原义保留**，**新增**：
  - `#19 配置外置`：除启动必需（DB/端口/profile/迁移/密钥）外，业务与运营配置不得写死 yml，须经配置中心管理、可审计、高危项有护栏。
  - `#20 内置超级管理员`：强制内置、启动自动授满五维、不可降权/删除/移出超管组、不旁路（走 RBAC）、全程审计、强制 MFA。
- `## §2 菜单 IA 锁` ← 核心 §2.1+§2.2：现行七业务域 + 功能入口目录同源（合规安全下的"安全与配置"承载系统配置中心）。
- `## §3 4 套统一状态机` ← 旧宪法 §3：配置/变更/待办/告警 四套及默认动作。
- `## §4 7 步极简配置流` ← 旧宪法 §4。
- `## §5 设计 token` ← 旧宪法 §8：主色#1565c0 等全 token + "硬编码自动拒"。

- [ ] **Step 3: 跑验证（应通过）**

Run: `grep -cE '^\| (19|20) \|' docs/CONSTITUTION.md`
Expected: `2`（#19 #20 已加）

Run: `grep -E '27 项|27 二级|安全基线|Provider 状态' docs/CONSTITUTION.md | grep -c .`
Expected: ≥ `3`（菜单全树仍在、27 锁未变）

Run: `grep -cE '^## §[0-5] ' docs/CONSTITUTION.md`
Expected: `6`（§0–§5 六章齐全）

- [ ] **Step 4: 提交**

```bash
git add docs/CONSTITUTION.md
git commit -m "docs(core): 核心重写 PartA §0-§5 结构不变量（硬约束+#19配置外置/#20内置超管+菜单锁+状态机+7步流+token）"
```

---

## Task 8: 核心重写 Part B —— 七视角不变量章（§6–§12）

**内容迁移类（完整性骨干）。** 新增 7 个视角不变量章，把散落在旧宪法 §10/§11、详规、质量基线里的横切原则**按视角归位**。这是治"缺失事实"的结构核心。

**Files:**
- Modify: `docs/CONSTITUTION.md`

- [ ] **Step 1: 采集横切原则来源（确认有料可迁）**

Run: `grep -nE '医师确认|危急值|国密|等保|个保法|数据出境|继承|租户|FHIR|CDS Hooks|模型能力网关|B0|内外网|国产化|麒麟|达梦' docs/CONSTITUTION.md docs/MEDKERNEL_BUSINESS_SCENARIO_DETAIL_SPEC.md | grep -c .`
Expected: 大量命中（确认这些不变量在旧源中有出处，迁移有依据）。

- [ ] **Step 2: 写 §6–§12（强制章节 + 必覆盖要点 + 迁移源）**

- `## §6 临床医疗安全红线` ← 旧宪法 #9/#10/#13/#14 + 质量基线 #3 + 详规 OPT-04：医师确认才进病历、AI 明显标识、红线禁忌/DDI/危急值/剂量上限/抗菌限制/特殊人群、高危双签、旧版隔离、临床知情同意、不自动开医嘱。
- `## §7 知识与数据治理不变量` ← 旧宪法 #14/§10 知识唯一 + 质量基线 #5/#6 + 详规 §8：唯一权威知识（同一适用域唯一 ACTIVE）、待审新版只审不执行、原子替换、字典**语义**映射（反 LCS 红线，高危近似强制 HIGH+禁批量+禁自动确认）、来源锚点+hash、证据分级、关系库权威/图与 Dify 仅投影。
- `## §8 安全合规与监管基线` ← 旧宪法 #1/#8：等保 2.0 三级、国密 SM2/3/4、个保法、数据出境合规评估、脱敏+字段级加密、审计链、互联互通与电子病历评级、伦理与（数据）知情同意。
- `## §9 集团化与多租户治理` ← 旧宪法 §6（租户生命周期）+ §2.1 多维切片 + 详规：平台→集团→医院→院区→社区→科室→专病 继承覆盖与冲突仲裁、租户隔离、6 阶段生命周期及自动推进、灰度范围（默认10%床位）。
- `## §10 集成与互操作边界` ← 旧宪法 §10 外部系统统一对接 + 详规 §1.5：HIS/EMR/LIS/PACS/医保/区域/Provider 只经适配器+标准上下文+临床事件+嵌入+回调+包同步+审计；FHIR/CDS Hooks 风格；不绕引擎直写医嘱/病历/上报/支付/设备控制。
- `## §11 AI / 模型治理与可降级` ← 旧宪法 §10 大模型可插拔 + 详规 §10.2/§10.3：B0 先于模型（铁律#4）、统一经模型能力网关、只产候选不产事实、路由/脱敏外调、提示词与模型版本治理、幻觉拦截、模型/ Dify/ 图全关时主链路仍验收通过。
- `## §12 运维 / SRE / 国产化交付` ← 旧宪法 §10 内外网双形态 + FOUNDATION §2/§6 运行交付：内外网双形态、国产化栈（麒麟/统信/openEuler·达梦/人大金仓·KAE/BiSheng JDK·Ollama）、5 方言迁移、监控、备份 RPO-RTO、降级链、离线许可、SLA≥99.9%、灾备。

- [ ] **Step 3: 跑验证（应通过）**

Run: `grep -cE '^## §(6|7|8|9|10|11|12) ' docs/CONSTITUTION.md`
Expected: `7`（七视角章齐全）

Run: `grep -E '医师确认|ACTIVE|语义映射|LCS|等保|国密|继承|FHIR|模型能力网关|B0|内外网|麒麟' docs/CONSTITUTION.md | grep -c .`
Expected: ≥ `10`（七视角关键不变量 token 均落地）

- [ ] **Step 4: 提交**

```bash
git add docs/CONSTITUTION.md
git commit -m "docs(core): 核心重写 PartB §6-§12 七视角不变量章（临床安全/知识治理/安全合规/集团继承/集成边界/AI治理/运维国产化）"
```

---

## Task 9: 核心重写 Part C —— 门禁/语言/指针 + 修订 + 字数门

**Files:**
- Modify: `docs/CONSTITUTION.md`

- [ ] **Step 1: 确认铁律来源（10 + 新 #11）**

Run: `grep -cE '^### #[0-9]+ ' docs/audit/质量基线.md`
Expected: `10`（质量基线 §1 现有 10 条铁律，格式为 `### #N 名称`；迁来作核心 §13，另加 #11 配置外置）。

- [ ] **Step 2: 写 §13–§15 + 收尾**

- `## §13 验收铁律 + 门禁 + 真实性` ← 迁自质量基线 §1 的 10 条铁律 + **新增 #11 配置外置**（呼应 #19）；并入"通用拒绝项"要义（新增一级菜单/多主按钮/默认筛选>3/大列表前端全量/AI 无标识/绕 no-page-mock 等）。
- `## §14 中文优先与语言` ← 并入 [DOCUMENTATION_LANGUAGE_POLICY](../../DOCUMENTATION_LANGUAGE_POLICY.md) 要义 + 旧宪法 §9 文档规则：当前有效文档简体中文、技术对象默认隐藏专家模式、同 PR 合并远程 main。
- `## §15 指针` ← 术语表指向 `glossary.md`；13 角色矩阵指向 `audit/质量基线.md §9`；实现细节指向 `cards/`；验收方法论指向 `audit/质量基线.md`。
- `## 修订记录` ← 续表加一行：`3.0 | 2026-05-30 | 用户决策 + Claude | 收编为按 11 视角分章的核心；吸收体验规范全局/铁律/横切原则；加 #19/#20；启用自包含施工卡体系`。文首版本号升为 `3.0`。

- [ ] **Step 3: 跑验证（应通过）**

Run: `grep -cE '^## §(13|14|15) ' docs/CONSTITUTION.md`
Expected: `3`

Run: `grep -E '^> 版本：3\.0' docs/CONSTITUTION.md | grep -c .`
Expected: `1`（版本升到 3.0）

Run: `awk '/^## §1 /{f=1;next} /^## §2 /{f=0} f' docs/CONSTITUTION.md | grep -cE '^\| ([0-9]|1[0-9]|20) \|'`
Expected: `20`（§1 段内硬约束 20 条，未丢 #1–#18 且含 #19/#20；awk 限定 §1 避开菜单表）

Run: `grep -c 配置外置 docs/CONSTITUTION.md` ; `grep -cE '内置超级管理员|内置超管' docs/CONSTITUTION.md` ; `grep -c 引擎真实性 docs/CONSTITUTION.md`
Expected: 各 ≥ `1`（#19 配置外置 / #20 内置超管 已加、#18 引擎真实性 等旧约束未丢——内容抽检）

Run: `wc -m docs/CONSTITUTION.md`
Expected: ≤ `30000`（目标 2.5万字；超 3万说明没去够散文，回到 Task7/8 压缩）

- [ ] **Step 4: 提交**

```bash
git add docs/CONSTITUTION.md
git commit -m "docs(core): 核心重写 PartC §13-§15 铁律门禁/语言/指针+修订3.0+字数门"
```

---

## Task 10: 改写 `AGENTS.md` 阅读链

**Files:**
- Modify: `AGENTS.md`（仓库根；当前 §文档权威顺序 为 7 文档链，见第 20–30 行）

- [ ] **Step 1: 定位旧链**

Run: `grep -nA12 '## 文档权威顺序' AGENTS.md`
Expected: 显示旧 7 条阅读链（README→…→backlog）。

- [ ] **Step 2: 用下文整段替换 `## 文档权威顺序` 小节**

```markdown
## 文档权威顺序

构建任一任务，按下序读（读最少、拿最全）：

1. [docs/CONSTITUTION.md](docs/CONSTITUTION.md) —— 核心（恒读，11 视角不变量）
2. `docs/cards/<域>/_brief.md` —— 所领卡所在域的域简报
3. `docs/cards/<域>/<TASK-ID>.md` —— 所领的施工卡

页面卡额外读 [docs/EXPERIENCE_CONTRACT.md](docs/EXPERIENCE_CONTRACT.md)（共享体验与组件契约）。

辅助（按需查，不通读）：
- 找卡：[docs/cards/_index.md](docs/cards/_index.md)（场景 S0–S40 → 卡）
- 验收方法论：[docs/audit/质量基线.md](docs/audit/质量基线.md)
- 名词：[docs/glossary.md](docs/glossary.md)
- 任务状态 / 派单：[docs/backlog.md](docs/backlog.md)

冲突裁决：核心 > 域简报 > 卡。卡与核心冲突 → 核心赢；卡之间本不应重叠，若冲突＝分区错误，修分区而非裁决。

> 迁移过渡期（P0–P7）：旧巨物（详规/落地规划/FOUNDATION/体验规范）在对应域搬迁完成前物理保留但**不再作为权威**；以本序为准。全部域搬迁完成后（P8）删除。
```

- [ ] **Step 3: 跑验证（应通过）**

Run: `grep -E 'docs/cards/<域>/<TASK-ID>|核心（恒读' AGENTS.md | grep -c .`
Expected: ≥ `1`（新链已写入）

Run: `grep -E 'MEDKERNEL_IMPLEMENTATION_LANDING_PLAN|MEDKERNEL_BUSINESS_SCENARIO_DETAIL_SPEC' AGENTS.md | grep -c .`
Expected: `0`（旧链对两巨物的权威引用已移除）

- [ ] **Step 4: 提交**

```bash
git add AGENTS.md
git commit -m "docs: AGENTS.md 阅读链 7文档→核心+域简报+卡（含过渡期口径）"
```

---

## Task 11: 更新 `docs/README.md` 导航

**Files:**
- Modify: `docs/README.md`

- [ ] **Step 1: 看现状**

Run: `sed -n '1,40p' docs/README.md`
Expected: 现有文档中心导航（指向旧巨物）。

- [ ] **Step 2: 更新导航段**

把文档中心导航改为新结构指引：核心 `CONSTITUTION.md`、体验契约 `EXPERIENCE_CONTRACT.md`、卡目录 `cards/`（含 `_index`/`_template`/`_brief-template`/`_coverage-matrix`）、`glossary.md`、`backlog.md`、`audit/质量基线.md`；并加一句"巨物（详规/落地规划/FOUNDATION/体验规范）迁移过渡期保留，权威以核心+卡为准，P8 删除"。保留 handbook/adr/legal/release 链接不变。

- [ ] **Step 3: 跑验证（应通过）**

Run: `grep -E 'cards/|EXPERIENCE_CONTRACT|glossary' docs/README.md | grep -c .`
Expected: ≥ `3`

- [ ] **Step 4: 提交**

```bash
git add docs/README.md
git commit -m "docs: README 文档中心导航更新到自包含施工卡结构"
```

---

## Task 12: P0 一致性门（不产出新文件，只校验 + 收尾）

确认 P0 地基成立、且过渡期口径自洽，再交付。

- [ ] **Step 1: 跑全套一致性校验**

Run（逐条核对期望）:
```bash
echo "A 核心 11 视角章："; grep -cE '^## §([0-9]|1[0-5]) ' docs/CONSTITUTION.md   # 期望 16（§0-§15）
echo "B 硬约束 20 条（§1 限定）："; awk '/^## §1 /{f=1;next} /^## §2 /{f=0} f' docs/CONSTITUTION.md | grep -cE '^\| ([0-9]|1[0-9]|20) \|'   # 期望 20
echo "C 卡脚手架："; ls docs/cards/_template.md docs/cards/_brief-template.md docs/cards/_index.md docs/cards/_coverage-matrix.md
echo "D 体验契约/术语表："; ls docs/EXPERIENCE_CONTRACT.md docs/glossary.md
echo "E AGENTS 新链/无旧巨物权威："; grep -c 'docs/cards' AGENTS.md; grep -c 'MEDKERNEL_IMPLEMENTATION_LANDING_PLAN\|MEDKERNEL_BUSINESS_SCENARIO_DETAIL_SPEC' AGENTS.md  # 期望 ≥1 然后 0
echo "F 覆盖矩阵待迁数："; grep -c '待迁' docs/cards/_coverage-matrix.md          # 期望 = 旧锚点总数 N（P0 尚未开始迁）
echo "G 巨物仍在（P0 不删）："; ls docs/MEDKERNEL_BUSINESS_SCENARIO_DETAIL_SPEC.md docs/MEDKERNEL_IMPLEMENTATION_LANDING_PLAN.md docs/MEDKERNEL_FOUNDATION_AND_SERVICES.md docs/MEDKERNEL_PRODUCT_EXPERIENCE_RULES.md
```
Expected: A=`16`；B≥`20`；C/D/G 全部文件存在；E 先 ≥`1` 后 `0`；F = N（与 Task3 一致）。任一不符→回对应 Task 修。

- [ ] **Step 2: 死链检查（新文档内相对链接可达）**

Run: `grep -oE '\]\(([^)]+\.md)' docs/CONSTITUTION.md docs/cards/_index.md docs/README.md AGENTS.md | sed -E 's/.*\(//' | sort -u`
Expected: 逐个 `test -f` 应存在（cards/_template、glossary、EXPERIENCE_CONTRACT、audit/质量基线 等）。对仍指向旧巨物的链接——P0 允许（过渡期巨物在），但不得作为权威序。

- [ ] **Step 3: 收尾提交（若 Step1/2 有零碎修复）**

```bash
git add -A docs AGENTS.md
git commit -m "docs: P0 基础骨架一致性门通过（核心16章/硬约束≥20/卡脚手架/阅读链/覆盖矩阵就绪；巨物过渡保留）" || echo "无待提交修复"
```

---

## Self-Review（写计划者自检结论）

- **Spec 覆盖**：设计规格 §3 四层（核心 T7–9 / 域简报模板 T2 / 卡模板 T1 / 索引 T4）✓；§4 11 视角（核心 §6–§12 七视角章 T8 + 卡视角清单 T1）✓；§5 三模板（T1/T2/T7–9）✓；§6 去向地图与 AGENTS 改写（T5/T6/T10/T11）✓；§7 P0 + 覆盖矩阵（T3）✓。**§7 的 P1–P7 逐域搬迁、P8 退役删除不在本计划**——按约定属 Plan 2+（D0 起）与终末退役计划，本计划仅 P0。
- **占位符扫描**：模板/AGENTS/索引/术语/覆盖矩阵全文内联；核心/体验契约为内容迁移类，已用"强制章节+迁移源+grep 验证"替代散文占位，非 TODO。
- **类型/命名一致**：文件路径全计划一致（`docs/cards/_template.md`、`docs/EXPERIENCE_CONTRACT.md`、`docs/glossary.md`、`docs/cards/_coverage-matrix.md`、`docs/cards/_index.md`）；核心章节号 §0–§15 在 T7/T8/T9/T12 一致；视角数 11 在 T1 与设计规格一致；硬约束目标 ≥20 在 T7/T9/T12 一致。

---

**End of P0 基础骨架实施计划。**
