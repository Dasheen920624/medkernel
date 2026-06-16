# AIK-STD-02 PR3 WordDocumentParser（POI）+ 表格理解 Implementation Plan

> REQUIRED SUB-SKILL: superpowers:test-driven-development（红绿驱动，逐单元）。

**Goal:** AIK-STD-02 最后一刀，补齐 AC-1。在 PR1/PR2 解析管线之上接入 Word：Apache POI 确定性读取 `.docx` 正文段落 + 表格 → 复用分章逻辑得章节树 → **表格理解（FR-2）：表 → 行/单元格片段**，物化为带单元锚点 `[p<页>/]§<节>/tbl<n>/r<行>c<列>` 的受控来源片段。空 / 损坏 docx 诚实 FAILED，不产伪结构（铁律 #1）。表格数据模型 + 物化 + 锚点方案两格式通用。

**设计依据：** [`docs/superpowers/specs/2026-06-15-aikstd02-doc-parse-pipeline-design.md`](../specs/2026-06-15-aikstd02-doc-parse-pipeline-design.md) §3.1/§3.3/§4（PR3 行）。

## 关键设计抉择（写给评审/接力）

1. **零迁移 / 零端点 / 零权限 / 零新表**。`ck_mk_doc_parse_job_format` 的 CHECK 已含 `'WORD'`（PR1 建表即纳入）；编排 `resolveRawBytes` 对一切非 STRUCTURED_TEXT 格式走 Base64 解码——故 WORD job 无需任何编排/迁移改动。`WordDocumentParser` 为 `@Component` 自动并入 `List<DocumentParser>` 分派。整链零控制器改动 → 产品目录不漂移。`LATEST_MIGRATION_VERSION` 保持 133。
2. **表格理解「两格式通用」落在模型 + 分章器 + 物化器（格式无关），非落在每个适配器**。PDFBox 文本层无可靠表格边界，强行抽表＝伪造结构（违铁律 #1）→ PDF 诚实不产表（保留 `page` 维度供未来可靠 PDF 表抽取器接入）。Word `.docx` 表格为结构化 `XWPFTable`（行/单元格确定），POI 真实抽取。故表能力**通用承载、Word 真实填充**——`page` 字段使锚点对 PDF 来源（`p<页>/…`）与 Word 来源（无页前缀）统一可表达。
3. **分章器升级为「元素流」分章**。`DocumentSectionizer.sectionize` 由 `List<TextLine>` 改吃密封 `List<Element>`（`TextLine` | `TableBlock`）：段落走既有标题检测/编号路径/前言 §0；遇 `TableBlock` 归属「当前章节」（无标题前归 §0 前言），记录该节内 1 基表序 `tbl<n>` + 节号 + 节标题 + 可空页号 → 产 `ParsedTable`。`ParsedDocument` 由 `(sections)` 扩为 `(sections, tables)`。文本 / PDF 适配器仅喂 `TextLine`（tables 为空），Word 喂 `TextLine` + `TableBlock`。
4. **表格单元锚点 = `[p<页>/]§<节>/tbl<n>/r<行>c<列>`**（§3.3）。物化器在段落物化后追加表格物化：逐单元格，空单元格跳过（不为空内容产指纹，守既有 `片段正文不能为空` 红线），真实 SHA-256 + 幂等去重；`anchor_label` = 所属节标题，`text_excerpt` = 单元格正文。表格单元片段计入 job `parsed_fragment_count`（诚实计数，含表格）。
5. **Word 无版式页维度** → 段落 / 表格 `page=null`（锚点无 `p` 前缀），同 PR1 文本。`.docx`（OOXML）支持；旧二进制 `.doc` / 非 OOXML 字节诚实 FAILED。OCR / 版面理解受 P6 + LLM 闸不做（YAGNI）。

## 依赖

pom 加 `poi.version=5.3.0` + `org.apache.poi:poi-ooxml`（纯 Java、Apache-2.0、离线确定性、JVM 事实标准的 OOXML 读写库）。仅用其 `XWPFDocument` 结构化读 `.docx` 段落 + 表格，无模型。

## Tasks（TDD 逐单元红绿）

- **T1 数据模型 + 分章器元素流（重构保形 + 新表格行为）**
  - 新 `ParsedTable(sectionNumberPath, sectionTitle, indexInSection, Integer page, List<List<String>> rows)`；`ParsedDocument` 扩为 `(sections, tables)`。
  - `DocumentSectionizer`：定义密封 `Element`（`TextLine` | `TableBlock(Integer page, List<List<String>> rows)`），`sectionize(List<Element>)` 段落逻辑不变 + 表格归属当前节并编 `tbl<n>`。
  - 同步 `StructuredTextDocumentParser` / `PdfDocumentParser` 喂 `Element` 流（tables 空）；同步 3 处 `new ParsedDocument(...)`（分章器 + 2 物化测试）。既有解析 / 物化 / 编排 / 集成测试保绿（重构保形）。
- **T2 表格单元物化（RED→GREEN）**
  - RED：`ParsedDocumentMaterializerTest` 加用例——含 1 表（2 行 ×2 列，1 空单元格）+ Word（page=null）→ 锚点 `§1/tbl1/r1c1`…且空单元格不产片段；再加 page 维度表 → `p2/§2/tbl1/r1c1`（证两格式通用）。GREEN：物化器加表格循环，空单元格跳过 + 幂等。
- **T3 `WordDocumentParser`（POI，RED→GREEN）**
  - pom 加 POI。RED：`WordDocumentParserTest`——POI 确定性构造夹具 `.docx`（编号标题 + 段落 + 1 张表），断言 `supports` 仅 WORD；解析得章节树（page=null）+ `ParsedTable`（节号 / 节内表序 / 行列单元格）；空 docx / 损坏字节诚实抛 `DocumentParseException`。GREEN：实现 `@Component WordDocumentParser` 遍历 `getBodyElements()` 段落→`TextLine`、表→`TableBlock` → `DocumentSectionizer`。
- **T4 端到端集成**
  - `DocumentParseIntegrationTest` 加：建 SourceDocument → POI 构造含表 `.docx` → Base64 → `submit(format=WORD)` → SUCCEEDED + `source_fragment` 含段落锚点（`§…/¶…`）与表格单元锚点（`§…/tbl1/r1c1`）+ 真实 SHA-256。
- **T5 收口**：全量 `mvn test` + 四门禁 changed（真实性 / 配置 / 迁移 / 中文注释）+ 五方言 Flyway smoke（无迁移变更，验证不回归）+ `git diff --check` + 前端 `productCatalog.test.ts`（应无漂移，无控制器改动）。

## 红线

铁律 #1（锚点/hash 真实，空 / 损坏 docx 诚实 FAILED 禁伪结构；PDF 无可靠表则不产表不伪造）· B0 先于模型（POI 确定性，无模型，OCR / 版面理解受 P6 不做）· 域归属 engine-knowledge 不依赖 compliance · 合并 main 逐 PR 授权（不自动合）· 真实性门禁禁 Javadoc 占位/模拟/仿真/演示/placeholder · 中文注释门禁要多行 Javadoc。
