# AIK-STD-02 文档解析、引用锚点与版本存证 — 设计

> 卡片：[`docs/cards/wave2/AIK-STD-02.md`](../../cards/wave2/AIK-STD-02.md) · 6d · X-AIK / engine-knowledge
> 阶段：第二阶段 P2-C 工厂流水线**入口**（源→安全候选内容管线的第一刀）
> 前置：[CONSTITUTION](../../CONSTITUTION.md) · [wave2 _brief](../../cards/wave2/_brief.md) · 依赖 [AIK-STD-01](../../cards/wave2/AIK-STD-01.md)（资产信封）· [KNOW-01](../../cards/D2/KNOW-01.md)（来源/hash）

## 1. 目标与边界

**目标**：文档解析 → 引用锚点 → 版本存证。PDF/Word 解析到章节树 + 表格理解，每条抽取产物可回溯到原文页/章/段锚点 + 原文 hash，杜绝凭空生成。

**这是 P2-C 流水线的入口**：本卡产出「带真实锚点的受控来源片段」，下游 AIK-STD-03（术语）/04（候选生成）/05（11 项门禁）/10（去重分流）消费这些片段。

**P6 阻断分寸（恒守）**：文献资料库受管根地址未配置 = 正式知识生产仍阻断。本卡只建**解析机制 + B0 确定性路径**，以提交进仓库的**测试夹具文档**验证；不连接真实文献库、不进入 P6。生产环境缺源时诚实降级（无文档可解析 → 诚实 EMPTY/拒绝，不伪造结构，铁律 #1）。

## 2. 核查结论（关键：别重建已成熟地基）

核查 `medkernel-backend`（2026-06-15）：

| 卡片预设 | 实际现状 | 裁决 |
|---|---|---|
| `doc_anchor`（条目→页/章/段）新表 | **`source_fragment` 已承载**：`anchor_path`(256)+`anchor_label`(256)+`text_excerpt`(2048)+`content_hash`(V32 SHA-256)，UNIQUE(version, anchor_path) / UNIQUE(version, content_hash)；`citation` 带 `start_offset/end_offset`(V49) | **不建 `doc_anchor`**，解析片段物化进 `source_fragment` |
| 原文 + 解析 hash 存证 | **`source_version` 已承载**：`content_hash`(SHA-256)+`file_uri`(原文引用)，UNIQUE(doc, content_hash)(V49) | 复用 `source_version` 存证 |
| 来源登记 | `source_document`（code/type{指南/说明书/政策/院内/中医}/authority A–E/publisher/license） | 复用 |
| PDF/Word 章节解析 + 表格理解 | **不存在**（无 PDFBox/POI/Tika 依赖、无解析代码） | **本卡新建（唯一真实缺口）** |
| 解析 job 跟踪 | 不存在（`knowledge_export_job`/`mk_knowledge_production_job`/`mk_knowledge_discovery_run` 均非解析 job） | **新建 `mk_doc_parse_job`（唯一新表）** |

Java 侧已就绪：`SourceDocument`/`SourceVersion`/`SourceFragment`/`Citation` 实体 + 仓储 + `SourceVersionRegisterRequest`/`KnowledgeSourceVersionCreateRequest` + 写入服务（`KnowledgeIdentityService`/`KnowledgeCustomizationService`），均在 `com.medkernel.engine.knowledge`。最新迁移 = V132。

**结论**：本卡 = 在成熟受控源注册表之上补**解析层**。新增物只有：① 解析 job 表；② 解析端口 + 适配器；③ 物化器（解析产物 → source_version/fragment）。

## 3. 架构（端口-适配器）

仿既有 `ModelProvider` 抽象 + 适配器、`KnowledgeCandidateIntake` 端口的成熟范式。

```
上传文档(bytes + 文件名 + source_document)
   → DocumentParseOrchestrationService.submit() → mk_doc_parse_job(PENDING)
   → (异步) DocumentParser 端口按格式分派
        ├─ StructuredTextDocumentParser (B0, 确定性, PR1)
        ├─ PdfDocumentParser (Apache PDFBox, PR2)
        └─ WordDocumentParser (Apache POI, PR3, + 表格)
   → ParsedDocument(章节树 + 表格 + 段落, 每节点带锚点 + 片段 hash)
   → ParsedDocumentMaterializer
        ├─ 注册/复用 source_version(原文 SHA-256 + file_uri)   ← FR-4 存证
        └─ 逐条 upsert source_fragment(anchor_path/label/excerpt/hash)  ← FR-3 锚点
   → job(SUCCEEDED, result_ref=source_version_id, parsed_hash) ;
      解析失败 → job(FAILED, error) 不产半真片段   ← FR-5 诚实
```

### 3.1 单元与职责（可独立测试）

- **`DocumentParser`（端口）**：`ParsedDocument parse(ParseInput)` — 入参原始字节 + 文件名 + 声明格式；不支持/损坏 → 抛 `DocumentParseException`（诚实失败，FR-5）。`supports(format)` 用于分派。
- **`DocumentFormat`（枚举）**：`STRUCTURED_TEXT`/`PDF`/`WORD`（OFD/版式 = 视角#9「尽力」，**诚实 UNSUPPORTED 不实现**）。
- **`ParsedDocument`（record）**：`List<ParsedSection>`（章节树，含层级 + 标题 + 段落）+ `List<ParsedTable>`（PR3）；每单元携**确定性锚点**（见 §3.3）+ 正文 + SHA-256 片段 hash。纯内存结构，无副作用，易测。
- **`StructuredTextDocumentParser`（B0 适配器，PR1）**：纯规则解析分级标题文本（Markdown 风格 `#`/`##` 或编号标题 `一、`/`1.`/`1.1`）→ 章节树 + 段落；零外部依赖、确定性、可重放。
- **`ParsedDocumentMaterializer`**：把 `ParsedDocument` 落入 `source_version`（存证）+ `source_fragment`（锚点）；强租户隔离；幂等（同 content_hash 复用，不重复插入）。
- **`DocumentParseOrchestrationService`**：job 生命周期（submit→PENDING→解析→SUCCEEDED/FAILED）+ 审计 + 上游不可用诚实降级。
- **`DocumentParseController`**：提交解析 / 查询 job 状态 / 列表（`knowledge.write`/`knowledge.read`，**复用不新增权限码**）。

### 3.2 数据契约（唯一新表）

`mk_doc_parse_job`（mutable-audited，五方言）：

| 列 | 类型 | 说明 |
|---|---|---|
| id | BIGINT PK | |
| tenant_id | VARCHAR(64) NOT NULL | 强租户隔离 |
| job_code | VARCHAR(64) NOT NULL | 业务编码，UNIQUE(tenant, job_code) |
| source_document_id | BIGINT NOT NULL | 关联受控来源（authority 由其继承） |
| source_file_name | VARCHAR(512) NOT NULL | 原文文件名 |
| document_format | VARCHAR(24) NOT NULL | STRUCTURED_TEXT/PDF/WORD，CHECK |
| source_hash | VARCHAR(128) NOT NULL | **原文字节 SHA-256**（存证锚 + 幂等） |
| status | VARCHAR(24) NOT NULL DEFAULT 'PENDING' | PENDING/RUNNING/SUCCEEDED/FAILED，CHECK |
| result_source_version_id | BIGINT NULL | 成功后物化的 source_version |
| parsed_section_count | INTEGER NULL | 解析出章节数（诚实计数） |
| parsed_fragment_count | INTEGER NULL | 物化片段数 |
| error_message | VARCHAR(1024) NULL | FR-5 诚实失败原因 |
| created_at/by, updated_at/by | 审计 | |

约束：`uk_doc_parse_job_tenant_code`、`ck_doc_parse_job_format`、`ck_doc_parse_job_status`、索引 `(tenant_id, source_document_id)`/`(tenant_id, status)`。中文 `COMMENT ON TABLE/COLUMN`（生产方言）。`LATEST_MIGRATION_VERSION` 132→133。

### 3.3 锚点编码（FR-3，落 source_fragment.anchor_path ≤256）

确定性、层级、可人读：
- 章节：`§<编号路径>`，如 `§2.1.3`；`anchor_label` = 标题原文。
- 段落：`§2.1.3/¶<段序>`，如 `§2.1.3/¶4`。
- PDF 追加页：`p<页>/§2.1/¶4`（PR2）。
- 表格单元（PR3）：`§2.1/tbl<n>/r<行>c<列>`。

`citation.start_offset/end_offset` 用于把下游 citation 精确定位到片段文本范围（已有，本卡不改 citation 写入，留下游 04）。

## 4. PR 切片（依赖隔离 · PR1 零新依赖）

| PR | 范围 | 新依赖 | FR |
|---|---|---|---|
| **PR1** | 管线核心：`mk_doc_parse_job`(V133 五方言) + `DocumentParser` 端口 + `StructuredTextDocumentParser`(B0) + 章节树/段落锚点 + job 生命周期 + `ParsedDocumentMaterializer`(物化进 source_version/fragment) + 诚实失败 + 控制器 | **无** | FR-1(文本章节)/FR-3/FR-4/FR-5 + B0 |
| **PR2** | `PdfDocumentParser`（Apache PDFBox）：PDF→章节 + 页锚点；测试夹具 PDF | PDFBox | FR-1(PDF) |
| **PR3** | `WordDocumentParser`（Apache POI）+ **表格理解**（FR-2，表→行/单元格片段，两格式通用）；测试夹具 DOCX | POI | FR-1(Word)/FR-2 |

依赖选型理由：PDFBox + POI 为 JVM 事实标准、纯 Java、Apache-2.0、离线可用、确定性（无模型）= 契合 B0 + 国产化/SRE 视角；OFD/版式无可靠 JVM 库 → 诚实 UNSUPPORTED（视角#9「尽力」）。依赖各自隔离在引入它的 PR，便于评审与回滚。

## 5. 不变量与红线

- **铁律 #1 真实性**：每片段必带真实锚点 + 真实 SHA-256；解析失败诚实 FAILED，**禁无锚点抽取、禁伪造结构**。
- **核心 §6 来源可溯**：`source_version.file_uri` + `content_hash` = 原文存证可验未篡改。
- **B0 先于模型**：纯规则解析路径（StructuredText / PDFBox / POI 均确定性）可用，不依赖模型；模型增强（版面理解等）为可选、受 LLM-01/08 网关 + P6 闸，**本卡不实现**（YAGNI + P6）。
- **多租户隔离**：job 与片段强租户隔离；按 org 隔离来源。
- **域归属 SYS-02**：新代码归 `engine-knowledge`（写 source_* = engine-knowledge 表），不依赖 compliance。
- **AIK-STD-01 信封无关**：本卡产「受控来源片段」非「资产候选」；候选生成（消费片段 → KnowledgeAssetEnvelope）属 AIK-STD-04，本卡不越界。

## 6. 验收映射

- **AC-1（FR-1~3）**：PDF/Word 解析 + 表格 + 锚点正确 → PR1(文本章节锚点) + PR2(PDF) + PR3(Word+表格)。
- **AC-2（FR-4/5）**：hash 存证可验（`source_version.content_hash` + job `source_hash`/`parsed` 一致性可复算）；失败诚实（FAILED + error，无半真片段）→ PR1 起。
- **T-GATE**：后端真实性门禁全绿（Javadoc 禁占位/模拟/仿真/演示/placeholder）。
- **B0 验收**：StructuredText 纯规则解析路径可用，不依赖模型 → PR1。

## 7. 测试策略（TDD 红绿）

- `StructuredTextDocumentParserTest`：分级标题→章节树、段落锚点、编号标题、空/损坏输入诚实抛错。
- `ParsedDocumentMaterializerTest`：物化进 source_version/fragment、幂等（同 hash 复用）、租户隔离、锚点唯一冲突处理。
- `DocumentParseOrchestrationServiceTest`：job 生命周期、SUCCEEDED 计数、FAILED 诚实降级、跨租户拒绝。
- `DocumentParseControllerTest`：权限（`knowledge.write` 提交 / `knowledge.read` 查询）、`@Valid`、未知 job 结构化 404。
- 真实 H2 集成：上传结构化文本夹具 → 解析 → 物化 → 查 source_fragment 锚点/hash 端到端。
- 迁移：`MigrationBaselineContractTest`（V133/表/索引/约束/mutable-audited/lifecycle/tenant）+ 两 `LATEST_MIGRATION_VERSION` 132→133 + 五方言 Flyway smoke 真实容器。
- 门禁：四门禁 changed（真实性/配置/迁移/中文注释）+ `git diff --check` + 前端 `productCatalog.test.ts`（新控制器→重生成产品目录）。

## 8. 非目标（YAGNI / 本卡不做）

- 模型增强解析（版面理解 / OCR）：受 P6 + LLM 闸，留后续。
- OFD/版式国产格式解析：无可靠 JVM 库，诚实 UNSUPPORTED。
- 候选生成 / 术语映射 / 11 项门禁 / 去重：分属 AIK-STD-04/03/05/10。
- citation 偏移写入：留下游 04（本卡只产 source_fragment，citation 由消费方建链）。
- 前端解析工作台 UI：视角#2「N·A」，本卡纯后端机制；如需触发界面留 AIK-STD-12 生产者工作台后续。
