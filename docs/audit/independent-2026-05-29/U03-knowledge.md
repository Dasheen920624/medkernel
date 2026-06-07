# U03 知识资产 / 版本状态机 / 知识身份 / 来源存证 — 独立深度真实性审计

> 审计日期：2026-05-29 ｜ 审计人：资深医疗系统审计专家（独立复核，未参考 `docs/audit/` 既有报告）
> 取证范围：`medkernel-backend/src/main/java/com/medkernel/engine/knowledge/`（全读 38 个源文件）、`db/migration/{postgres,oracle,dm,kingbase,h2}/V3__knowledge_asset_baseline.sql`、`medkernel-backend/src/test/java/com/medkernel/engine/knowledge/`（6 个测试）、`frontend/src/pages/quality/AiReview.tsx`、`frontend/src/app/router.tsx`、`frontend/src/shared/config/routes.ts`、`docs/backlog.md`。
> 权威对照：`docs/CONSTITUTION.md` 第 14 条 / §281、`docs/MEDKERNEL_BUSINESS_SCENARIO_DETAIL_SPEC.md` §8（§8.2.3 / §8.4 / §8.5 / §8.6 / §8.7 / §8.9 / §8.12 / §8.13）、S3「AI 知识工厂」、行 1417 / 1451。

---

## 一、单元概览

### 已落地（真实）
- **知识身份 / 版本 / 替代链的数据模型与只读查询**：`KnowledgeIdentity` / `KnowledgeAssetVersion` / `KnowledgeSupersession` / `Citation` / `SourceDocument` / `SourceVersion` / `SourceFragment` 七张表（PG `V3` 行 4-180），实体、Repository、Controller（列表 / 详情 / by-code / active / lineage）齐全。
- **版本状态机核心动作**：`KnowledgeVersionService.activate` / `withdraw` / `createDraftVersion`（`KnowledgeVersionService.java:73 / 167 / 243`），用悲观锁 `SELECT ... FOR UPDATE` 串行化同一 identity（`KnowledgeIdentityRepository.java:54`）。
- **真 SHA-256**：版本内容指纹（`KnowledgeVersionService.java:295-312`）、片段摘要（`KnowledgeIdentityService.java:235-252`）均为标准 `MessageDigest("SHA-256")` 十六进制，非 UUID / 时间戳合成。版本去重按真实 `content_hash` 比对（`KnowledgeVersionService.java:268-273`）。
- **多租户隔离 + RBAC**：所有 Repository 查询带 `tenantId`；Controller 类级 `@DataScope(requireTenant=true)` + 方法级 `@PreAuthorize("@perm.has('knowledge.read|write|publish|withdraw')")`（`KnowledgeVersionController.java:37/55/67/81`），权限矩阵有端到端测试（`KnowledgeIdentityControllerSecurityTest.java`）。

### 严重缺失 / 造假（详见维度①②⑥⑨⑩）
- **§8 AI 知识工厂的「探索 → 候选生成 → 新旧识别 → 分流 → 审核台」管线 100% 不存在**：无 `candidate_disposition`、`discovery_job`、8 种分流类型、`PENDING_REPLACEMENT_REVIEW`、影响处置任务、冲突仲裁、灰度发布。
- **片段去重防线运行时必崩**：`source_fragment` 表 5 方言全部缺 `content_hash` 列，但实体 / Repository / Service 均引用它 → 任何真实 `createFragment` 调用必抛 SQL 异常；被全 mock 测试掩盖。
- **来源版本「哈希」可被时间戳合成**（`KnowledgeIdentityService.java:170`）。
- **导出为假实现**：所有导出类型只返回 identity 计数 + `memory://` 占位 URI（`KnowledgeExportService.java:143-154`）。
- **前端零落地**：`AiReview.tsx` 为永久 disabled 占位页；无任何前端消费知识 API。

---

## 二、10 维度逐条 findings

### ① 业务正确性（对照 §8 工厂规范）

- **[Critical] C1 — §8 生成管线（探索/候选/分流/审核台）整体缺失。** 规范 §8.2.3 定义 8 种 `candidate_disposition`（`NEW_TOPIC` / `NEW_VERSION` / `SAFETY_WITHDRAWAL` / `SOURCE_CONFLICT` / `LOCAL_OVERRIDE_IMPACT` / `EXACT_DUPLICATE` / `OLDER_THAN_ACTIVE` / `UNRESOLVED_IDENTITY`），§8.6 生成流水线 11 段，§8.7 生成任务状态机（来源/探索/解析/身份比对/生成/校验/审核/验证/发布 9 阶段）。全仓后端 `grep` `candidate_disposition|discovery_job|exploration|NEW_VERSION|SAFETY_WITHDRAWAL|knowledge_as_of|EXACT_DUPLICATE|OLDER_THAN_ACTIVE|PENDING_REPLACEMENT` **零命中**（`medkernel-backend/src/main/java/com/medkernel/engine/knowledge/` 全目录）。当前仅有"人工建草稿版本 + 同 identity 内 hash 去重"，**不是 AI 知识工厂，是一个手工版本登记表**。
- **[High] H1 — 生成期新旧识别与分流缺失。** §8.2.3 要求"大模型每次生成前先查权威库解析 `knowledge_identity` 并比对当前/历史版本"，未识别不得进审核队列。代码中 `createDraftVersion`（`KnowledgeVersionService.java:243-293`）只做"同 identity 内版本号唯一 + 同 identity 内 hash 碰撞"两项校验，**没有跨来源版本/生效日期先后判定、没有 `comparison_target_version_id`、没有 disposition 落库**。
- **[High] H2 — 去重粒度错误：仅同 identity 内去重，无法满足 §8.9「生成去重分流」。** `KnowledgeVersionService.java:268` `findByTenantIdAndIdentityIdOrderByCreatedAtDesc` 只在传入的 `identityId` 范围内扫 hash。§8.2.3 `EXACT_DUPLICATE` 要求"同来源版本 / hash / 结构化事实与现行或已处理版本一致即去重"，与 identity 无关；且"重复导入、重复探索、模型重试均落到同一比对结果，不重复生成待办"（幂等去重）完全没有实现。
- **[Medium] M1 — `createDraftVersion` 初始态为 `UNDER_REVIEW` 而非 `CANDIDATE`/`DRAFT`。** `KnowledgeVersionService.java:286` 注释与赋值均为 `UNDER_REVIEW`，但 §8.13.1 新版应为 `PENDING_REPLACEMENT_REVIEW`，且状态机枚举注释（`KnowledgeVersionStatus.java:8`）画的是 `DRAFT→CANDIDATE→UNDER_REVIEW`。一个手工"草稿"接口直接产出"审核中"，跳过了 DRAFT/CANDIDATE，与自身声明的状态机不一致。

### ② 医疗安全合规（旧版隔离 / 版本替换 / AI 标识）

- **[Critical] C2 — 旧版隔离红线「无消费方可隔离」=端到端落空。** 宪法第 14 条 / §281 / §8.13.3 要求"运行时只查询当前适用域 `ACTIVE_AUTHORITATIVE`；缓存、搜索、图谱、Dify 投影随替换失效或重建"。但全仓 `grep`：knowledge 包**之外没有任何代码**消费 `findActiveByIdentity` / `getActiveVersion` / `KnowledgeAssetVersion`（在 `medkernel-backend/src/main/java` 非 `engine/knowledge/` 路径零命中）。即"权威版本"从未被任何 CDSS / 规则 / 推荐 / 质控引擎引用，状态机是孤岛；"旧版退出新临床判断"这条医疗安全红线**没有任何运行时执行点**，无从隔离。
- **[High] H3 — 安全撤回 / 受影响病例处置缺失。** §8.2.3 `SAFETY_WITHDRAWAL`、§8.13.2「后置处置」、§8.13.3「已确认旧版重大风险按授权立即限制/撤回 + 触发人工干预 + 加急审核」要求撤回后派发受影响在径患者 / 运行任务 / 离线站点的复核、补同步、风险告知任务。`withdraw`（`KnowledgeVersionService.java:167-220`）只把版本置 `WITHDRAWN` + `current_version_id=null` + 写一条 supersession，**无任何影响处置任务生成**。撤回后 identity 直接进入"无权威版本"空窗，未保留可回退的上一 ACTIVE，临床侧无降级提示。
- **[High] H4 — 待审新版与现行版「共存 + 替换提醒」机制缺失（GA-AIK-STD-11）。** §8.13.1「待替代新版常规更新时可与现行旧版共存用于审核比较，绝不参与临床引擎执行」+ 替换提醒。代码无 `PENDING_REPLACEMENT_REVIEW` 态、无替换提醒、无"未审新版不参与执行"的运行时门禁（因无运行时消费方，见 C2）。
- **[Medium] M2 — AI 标识 / 模型溯源元数据缺失。** §8.4 要求 `generation_job_id` / `model_mode` / `model_version` / `prompt_version`（无模型记 `DISABLED`/`BASELINE`）。`knowledge_asset_version` 表（PG `V3` 行 83-111）**无任何模型溯源列**，版本无法标识"AI 候选 vs 人工"，违反 §1.4「AI 输出必须标识为候选」与 §8.12「审核台必须显示 AI 标识」。

### ③ 多租户隔离

- **[通过] 隔离设计扎实。** 所有 Repository finder 强制 `tenantId`（`KnowledgeAssetVersionRepository.java:22-37`、`KnowledgeIdentityRepository.java:18-55`）；Service 统一 `requireCurrentTenant()` 从 `RequestContext.currentOrgScope()` 取（`KnowledgeVersionService.java:222-228`）；Controller `@DataScope(requireTenant=true)`。`KnowledgeIdentityRepositoryTest.isolatesByTenant`（行 94-100）真实 H2 验证跨租户不可见。
- **[Low] L1 — 跨租户写入校验依赖 finder 隐式过滤，无显式断言。** `activate` 取目标版本用 `findByTenantIdAndId`（`KnowledgeVersionService.java:86`）已带租户，安全；但 `source_version_id` / `source_document_id` 等外键在写版本时未校验属于同租户（理论上可引用他租户来源 id），属低风险数据一致性瑕疵。

### ④ 审计证据链

- **[High] H5 — 失败审计与"一键追溯"缺失。** §8.12「一键追溯：任意发布资产都可回到来源、解析、生成、审核、仿真、发布记录」。当前 lineage（`KnowledgeIdentityService.java:93-101`）只聚合 identity + 版本 + supersession 三者，**无解析/生成/审核/仿真/发布事件链**（因这些阶段不存在）。导出失败仅 `log.error`（`KnowledgeExportService.java:109`），无失败审计落库 → 失败证据丢失。
- **[Medium] M3 — supersession 链记录真实但不完整。** `activate`/`withdraw` 写 `knowledge_supersession`（`KnowledgeVersionService.java:149 / 213`）含 `transition_type` / `reason` / `actor` / `at`，这部分是真实证据；但缺 §8.13.2 要求的"受影响规则/路径/图谱/推荐清单"与影响处置闭环 id。

### ⑤ 五方言一致性

- **[Critical] C3 — 5 方言 `source_fragment` 表全部缺 `content_hash` 列（与代码契约不一致）。** 实体 `SourceFragment.java:23` 声明 `@Column("content_hash")`，Repository `SourceFragmentRepository.java:24` `findBySourceVersionIdAndContentHash`，Service `KnowledgeIdentityService.java:209/211/216-225` 写入并查询该列。但：
  - PG：`source_fragment`（行 45-54）字段仅 `id/tenant_id/source_version_id/anchor_path/anchor_label/text_excerpt/created_at`，**无 content_hash**。
  - Oracle（行 46-54）、DM（行 44-52）、Kingbase（行 44-52）、H2（行 56-65）**同样缺失**。
  `grep content_hash .../V3__*.sql | grep fragment` 五方言零命中。这既是五方言一致性问题（5 方言一致地错），更是 ⑥/⑩ 的运行时崩溃根因。
- **[通过] 其余六表五方言语义一致。** `knowledge_asset_version` 状态 CHECK、`risk_level` CHECK、`knowledge_supersession` transition CHECK、各 UNIQUE/INDEX 在五方言一致。

### ⑥ 代码净化（判伪铁律）

- **[Critical] C4 — 来源版本「哈希」用时间戳合成（UUID/时间戳充哈希）。** `KnowledgeIdentityService.java:168-171`：当 `request.contentHash()` 为空时 `hash = sha256(request.versionNo() + "_" + Instant.now().toEpochMilli())`。这不是来源文件内容的指纹，而是"版本号+当前毫秒"的摘要——每次导入必不同，`content_hash` 列声明用途为"真实性核验 + 重复检测"（`SourceVersion.java:10`）彻底落空，来源版本去重永远不命中。命中判伪铁律"时间戳充哈希"。
- **[High] H6 — 导出为 stub 假实现。** `KnowledgeExportService.java:37`（"当前实现是 stub：不输出真实文件 URI"）、行 143-148（VERSIONS/LINEAGE/CITATIONS/FULL_TENANT 全部 `count = identityRepository.countByTenantId` 即 identity 数量做 placeholder）、行 153-154（`result_uri = "memory://knowledge-export/" + jobCode + ".jsonl"` 占位）。导出"成功"但无任何真实内容，作业终态 SUCCEEDED 是假闭环。
- **[Medium] M4 — `createDraftVersion` 注释自述"计算内容哈希"但缺 §8.4 元数据落库。** 非造假，但版本仅存 `content_hash` 一个指纹字段，§8.4 约 30 个强制字段（`canonical_code` / `applicable_population` / `organization_scope` / `evidence_level` / `knowledge_as_of` / `valid_from-to` / `supersedes_version_id` / `superseded_by_version_id` 等）大量缺失。

### ⑦ 错误处理与降级

- **[High] H7 — 无模型可运行（B0/BASELINE）路径不存在。** §8.9「无模型可运行：关闭模型后来源导入、人工配置、审核、包发布和同步核心流程验收全通过」、行 1451「人工发布同样执行唯一有效版本约束和旧版失效隔离」。当前根本没有模型集成，也没有"探索/生成"业务，谈不上降级；但更关键的是连"人工完整走通来源→审核→发布→隔离"的闭环都缺审核台与发布环节，B0 路径同样不成立。
- **[Low] L2 — `sha256` catch 包 RuntimeException。** `KnowledgeVersionService.java:309-311` / `KnowledgeIdentityService.java:249-251` 把 `NoSuchAlgorithmException` 包成 `RuntimeException`。SHA-256 必然存在，实务无害，属可接受但非最佳（应 `throw new IllegalStateException`）。

### ⑧ 可观测性

- **[Medium] M5 — 状态机关键动作无审计日志/指标。** `activate`/`withdraw`（医疗高风险动作）无 `log.info` 记录 actor/identity/old→new/reason，无 metrics。仅导出服务有日志（`KnowledgeExportService.java:166`）。高风险版本替换缺可观测性，事故追溯困难。

### ⑨ 测试覆盖与有效性（"测试全绿 ≠ 真"）

- **[Critical] C5 — 核心业务测试全 mock，掩盖 C3/C4 运行时崩溃。** `KnowledgeVersionServiceTest`、`KnowledgeIdentityServiceTest`、`KnowledgeEngineTest` 三个测试的所有 Repository 均为 `Mockito.mock`，`save` 直接返回入参（`KnowledgeEngineTest.java:52-74`、`KnowledgeVersionServiceTest.java:38-47`）。后果：
  - `createFragmentSavesNewFragment`（`KnowledgeEngineTest.java:158-176`）从不写真实 DB → `content_hash` 缺列（C3）无法暴露；且该用例**未断言** `saved.contentHash()`，hash 去重分支（Service 行 211-214 `findBySourceVersionIdAndContentHash`）在 mock 下恒返回 empty，**该去重防线从未被任何测试真正执行**。
  - 悲观锁 `FOR UPDATE` 串行化在 mock 下是空操作；`KnowledgeIdentityRepositoryTest.forUpdateLockReturnsExisting`（行 102-108）自述"不验证 lock 行为本身"，仅验 SQL 可解析。"同 identity ACTIVE≤1"不变量在真实并发下从未被证明。
- **[High] H8 — DB 无唯一约束兜底"ACTIVE≤1"，纯靠应用层锁且未集成验证。** `knowledge_asset_version`（PG `V3` 行 107-110）唯一约束仅 `uk(identity_id, version_no)`，**无 partial unique index `WHERE status='ACTIVE'`**。`KnowledgeAssetVersion.java:12` 与 service 注释均承认"由 Service 层事务保证"。一旦悲观锁失效（如读已提交隔离 + 锁 SQL 在某方言降级、或绕过 service 直写）即可产生双 ACTIVE，且无测试覆盖并发竞态。属高风险（医疗权威唯一性红线无 DB 兜底）。
- **[High] H9 — 关键正路径零集成测试。** 唯一真实 DB 测试 `KnowledgeIdentityRepositoryTest`（`@DataJdbcTest`+H2+Flyway）只覆盖 identity 表的增查/筛选/租户隔离，**从不触达 version/fragment/supersession/citation/export 任何一张表的真实读写**。`activate`/`withdraw`/`createFragment`/`registerSourceVersion` 无端到端 DB 验证。

### ⑩ 前后端契约一致

- **[Critical] C6 — 前端「AI 知识审核台」为永久 disabled 占位页 + 引用错误 backlog。** `AiReview.tsx`（行 5-18）整页 `<PageState state="disabled">`，且依赖声明为 `GA-ENG-CDSS-01`、`GA-ENG-EVAL-01`（行 13-14）——与本单元知识工厂（GA-ENG-KNOW / GA-AIK-STD）**完全无关**。路由 `/aik/review` 已注册（`router.tsx:88`）但渲染空壳。§8.12 规定的 9 个工厂页面（来源资料库 / 探索策略与新知队列 / 同步任务 / 解析身份比对 / AI 知识审核台 / 冲突仲裁 / 仿真报告 / 知识包发布 / 反馈优化）**全部不存在**。
- **[High] H10 — 后端知识 API 零前端消费。** 全仓 `grep "v1/engine/knowledge" frontend/src` 零命中。identity 列表/详情/active/lineage/版本激活撤回/导出/来源登记等所有端点**没有任何前端页面调用**，后端能力无法被任何角色通过 UI 使用，无法真实验收。

---

## 三、7 角色硬指标评估

| 角色 | 硬指标 | 本单元结论 | 取证 |
|---|---|---|---|
| 院长 | 首屏看懂系统状态 | 不适用（无知识工厂面板） | — |
| 医务处 | 不打开 JSON/DSL 跑剧本 | **不通过**：无 AI 知识审核台，无法审核/激活/替换任何知识 | `AiReview.tsx:11`（disabled） |
| 临床医生 | 0 培训用 CDSS / 床旁查阅 | **不通过**：知识 API 无前端消费，且无运行时引擎引用权威版本 | C2 / H10 |
| 信息科主任 | 接入向导 | 不适用 | — |
| 路径专家 | 编辑图形节点 | 不适用 | — |
| 实施工程师 | 一键导入专病配置包 | **不通过**：知识包发布/同步页面不存在，导出为假 | H6 / §8.12 缺失 |
| **合规审计** | **任意页面导出审计快照 + 来源一键追溯** | **不通过（重点）**：导出 stub 仅返回 identity 计数 + `memory://` 占位（`KnowledgeExportService.java:143-154`）；"一键追溯到来源/解析/生成/审核/发布"因这些阶段不存在而无法导出 | H5 / H6 |

> **合规审计「来源追溯一键导出」专项结论**：当前 `KnowledgeExportController` + `KnowledgeExportService` 提供了作业框架（PENDING→RUNNING→SUCCEEDED、TTL、权限），但导出**内容是假的**——`VERSIONS/LINEAGE/CITATIONS/FULL_TENANT` 全部用 identity 数量充数，`result_uri` 是内存占位字符串，不产出任何可下载的 JSONL/快照文件。合规审计拿到的"成功"导出无任何实际证据价值，属假闭环。

---

## 四、Findings 汇总表

| ID | 维度 | 严重度 | 一句话 | file:line |
|---|---|---|---|---|
| C1 | ① | Critical | §8 探索/候选/分流/审核台管线 100% 缺失 | `knowledge/` 全目录（disposition/discovery 零命中） |
| C2 | ② | Critical | 旧版隔离红线无运行时消费方，端到端落空 | `KnowledgeAssetVersionRepository.java:26`（仅 knowledge 包内使用） |
| C3 | ⑤ | Critical | 5 方言 source_fragment 全缺 content_hash 列，代码引用必崩 | `postgres/V3:45-54` vs `SourceFragment.java:23` |
| C4 | ⑥ | Critical | 来源版本哈希用 versionNo+时间戳合成（时间戳充哈希） | `KnowledgeIdentityService.java:170` |
| C5 | ⑨ | Critical | 核心业务测试全 mock，掩盖 C3/C4 崩溃 + 去重分支从未执行 | `KnowledgeEngineTest.java:52-74,170-175` |
| C6 | ⑩ | Critical | AI 知识审核台为 disabled 占位页且引用错误 backlog | `AiReview.tsx:11-14` |
| H1 | ① | High | 生成期新旧识别/分流缺失 | `KnowledgeVersionService.java:243-293` |
| H2 | ① | High | 去重仅同 identity 内，不满足 §8.9 hash/幂等去重 | `KnowledgeVersionService.java:268` |
| H3 | ② | High | 安全撤回后无受影响病例/任务处置 | `KnowledgeVersionService.java:167-220` |
| H4 | ② | High | 待审新版共存+替换提醒（GA-AIK-STD-11）缺失 | `KnowledgeVersionStatus.java:15-29`（无 PENDING_REPLACEMENT） |
| H5 | ④ | High | 一键追溯链不完整 + 失败审计丢失 | `KnowledgeIdentityService.java:93-101`、`KnowledgeExportService.java:109` |
| H6 | ⑥ | High | 导出 stub 假实现（identity 计数 + memory:// 占位） | `KnowledgeExportService.java:143-154` |
| H7 | ⑦ | High | 无模型可运行（B0）人工闭环不成立（缺审核台/发布） | §8.9 对照，全单元 |
| H8 | ⑨ | High | DB 无 ACTIVE≤1 唯一约束兜底，纯应用锁且未测并发 | `postgres/V3:107-110` |
| H9 | ⑨ | High | version/fragment/supersession/export 零集成测试 | `KnowledgeIdentityRepositoryTest.java`（仅 identity） |
| H10 | ⑩ | High | 后端知识 API 零前端消费 | `frontend/src`（v1/engine/knowledge 零命中） |
| M1 | ① | Medium | 草稿初始态直跳 UNDER_REVIEW，违自身状态机 | `KnowledgeVersionService.java:286` |
| M2 | ② | Medium | 版本表无模型溯源/AI 标识元数据 | `postgres/V3:83-111` |
| M3 | ④ | Medium | supersession 链缺受影响清单与处置 id | `KnowledgeVersionService.java:149` |
| M4 | ⑥ | Medium | §8.4 约 30 元数据字段大量缺失 | `postgres/V3:83-111` vs §8.4 |
| M5 | ⑧ | Medium | activate/withdraw 高风险动作无审计日志/指标 | `KnowledgeVersionService.java:73-220` |
| L1 | ③ | Low | 来源外键未显式校验同租户 | `KnowledgeVersionService.java:124-135` |
| L2 | ⑦ | Low | sha256 catch 包 RuntimeException | `KnowledgeVersionService.java:309` |

**计数：Critical 6 ｜ High 10 ｜ Medium 5 ｜ Low 2 ｜ 合计 23**

---

## 五、改造建议（每条 C / H）

### Critical

- **C1（管线缺失）**：按 §8.6/§8.7 新建探索调度（`discovery_job` / `discovery_policy`）、候选生成与分流（`candidate_disposition` 枚举 8 值 + `comparison_target_version_id`）、审核台后端门面。这是 GA-AIK-STD-04/08/10/11 的实质交付，工作量为新建子系统级别，不可在现有"手工版本表"上打补丁。
- **C2（旧版隔离落空）**：先确立运行时消费契约——CDSS/规则/推荐/质控引擎统一通过 `findActiveByIdentity` 取权威版本，并在替换事务内刷新/失效相关缓存与投影（§8.13.3）。在缺消费方前，任何"旧版已隔离"的验收主张都不成立。
- **C3（fragment 缺列）**：在 5 方言 `source_fragment` 增 `content_hash VARCHAR(128) NOT NULL` + `UNIQUE(source_version_id, content_hash)`（新迁移版本，禁止改 V3 已发布脚本）；补真实 `@DataJdbcTest` 覆盖 `createFragment` 写入与 hash 去重命中两条路径。
- **C4（时间戳充哈希）**：来源版本必须以真实来源文件字节流（`file_uri` 指向内容）计算 SHA-256；`contentHash` 缺失时应拒绝注册或要求上传文件后端算，严禁 `versionNo+epochMilli` 合成。
- **C5（全 mock 测试）**：将状态机与去重的关键路径改为 H2/Testcontainers 真实 DB 集成测试，断言落库字段与唯一性冲突；mock 测试保留做分支逻辑补充但不得作为"功能已落地"的依据。
- **C6（前端占位）**：按 §8.12 实现 AI 知识审核台真实页面（左原文 / 右候选 / 中影响、disposition 过滤、高风险逐条、来源对照），并修正其 backlog 依赖引用。

### High

- **H1/H2**：实现 §8.2.3 确定性优先比对（官方编号/批准文号/指南名+版本/生效日期/canonical code/来源 hash/适用域）+ 跨来源 hash 幂等去重 + disposition 落库。
- **H3**：`withdraw`/紧急停用后按 §8.13.2「后置处置」生成受影响在径患者、运行任务、离线站点的复核/补同步/风险告知任务，并保留可回退上一 ACTIVE。
- **H4**：引入 `PENDING_REPLACEMENT_REVIEW` 态与"待审新版与现行版共存 + 替换提醒"，运行时门禁确保未审新版不参与执行。
- **H5**：扩展 lineage 为来源→解析→生成→审核→仿真→发布全事件链；导出失败落审计表（不丢失败证据）。
- **H6**：导出接对象存储，产出真实 JSONL/快照（含 §8.4 元数据 + 来源锚点），`result_uri` 为可下载签名 URL。
- **H7**：补齐人工（无模型）完整闭环：来源登记→解析→人工建候选→审核台→质量门禁→发布→旧版隔离，作为 B0 验收基线。
- **H8**：在应用锁之外补 DB 兜底（PG/Kingbase partial unique index `WHERE status='ACTIVE'`；Oracle/DM 用基于函数的唯一索引或触发器），并补并发竞态测试。
- **H9**：补 version/fragment/supersession/export 的真实 DB 集成测试。
- **H10**：知识 API 接入前端（身份列表、版本时间轴、审核台、来源库），实现端到端可用与可验收。

---

## 六、总评

### done 是否名副其实？
**否，严重不副实。** backlog 将 `GA-ENG-API-03`（行 58）、`GA-ENG-KNOW-01`（行 76）、`GA-ENG-KNOW-02`（行 77）标记为 **done**，且变更记录 4.41（行 147）宣称"知识资产 SHA-256 哈希指纹去重物理阻断防线落地、全量 CI 跑绿、合规收官"。**实际**：
- 片段去重防线的 `content_hash` 列在 5 方言迁移中至今缺失（C3），所谓"物理阻断"是只改 Java 不改 SQL 的纸面防线，真实调用必 500。
- 来源版本指纹可用时间戳合成（C4）。
- 全 CI 跑绿是因为核心测试全 mock（C5），从未触达真实 DB，绿色不代表功能成立。
- 变更记录 4.39（行 149）本已诚实承认"KNOW-01 片段 hash 字段缺失"并回退 in_progress，但随后 4.38/4.41 在未真正补迁移列的情况下又标回 done——**这是一次未兑现的修复**。

`GA-ENG-KNOW-02`「新旧识别、去重、冲突、待审新版、原子替换、旧版隔离」六项中，仅"原子替换"（activate 悲观锁）部分成立；"新旧识别、冲突、待审新版、旧版隔离"四项缺失或落空，"去重"为纸面+错粒度。

### 可否真实验收？
**不可**。无 AI 知识审核台（C6）、无任何前端消费（H10）、导出为假（H6）、旧版隔离无消费方（C2）、关键路径无真实 DB 测试（C5/C9）——任一项都构成验收阻断，合规审计"来源追溯一键导出"硬指标直接不通过。

### §8 AI 知识工厂落地度估计
**约 12%–15%。** 真实落地的仅：知识身份/版本/替代链数据模型 + 只读查询、版本原子激活/撤回的悲观锁状态机骨架、版本内容真 SHA-256 与同 identity 内去重、多租户+RBAC。§8 的核心（探索 §8.2.1、新旧识别与分流 §8.2.3、元数据 §8.4 全量、生成流水线 §8.6、生成任务状态机 §8.7、质量门禁 §8.9、工厂页面 §8.12、待审共存与影响处置 §8.13.2/8.13.3 运行时）**全部缺失**。

### 应回退的 backlog 任务（done → in_progress / partial）
| backlog ID | 现状 | 建议 | 依据 |
|---|---|---|---|
| `GA-ENG-KNOW-01` | done | **回退 in_progress** | C3（fragment hash 列缺失）、C4（来源哈希造假）、C5（全 mock） |
| `GA-ENG-KNOW-02` | done | **回退 in_progress** | H1/H2/H3/H4/C2（新旧识别/去重粒度/旧版隔离/待审共存缺失） |
| `GA-ENG-API-03` | done | **回退 partial** | H6（导出假）、H10（无前端）、H5（追溯链不全） |
| `GA-AIK-STD-04/08/09/10/11/12` | （应为 P0 未起） | **确认 not-started / 不得标 done** | C1/H1/H4 全管线缺失 |

> 备注：本单元真实部分（版本数据模型、悲观锁原子替换骨架、多租户+RBAC、权限矩阵端到端测试、真 SHA-256 算法本身）应予保留，回退非全盘推倒。
