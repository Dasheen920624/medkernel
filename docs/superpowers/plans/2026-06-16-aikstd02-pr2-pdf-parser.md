# AIK-STD-02 PR2 PdfDocumentParser（PDFBox）+ 页锚点 Implementation Plan

> REQUIRED SUB-SKILL: superpowers:test-driven-development（红绿驱动，逐单元）。

**Goal:** 在 PR1 解析管线之上接入 PDF 解析：Apache PDFBox 确定性提取每页文本 → 复用分章逻辑得章节树 → 每段携真实页号 → 物化为带页锚点 `p<页>/§<节>/¶<段>` 的受控来源片段。缺文本（扫描件）/损坏 PDF 诚实 FAILED，不产伪结构（铁律 #1）。

**设计依据：** [`docs/superpowers/specs/2026-06-15-aikstd02-doc-parse-pipeline-design.md`](../specs/2026-06-15-aikstd02-doc-parse-pipeline-design.md) §3.3 / §4（PR2 行）。

## 关键设计抉择（写给评审/接力）

1. **二进制传输＝复用 `content` 承载 Base64**（非新增字段）。`DocumentParseRequest.content` 保持 `@NotBlank`：STRUCTURED_TEXT 时为原文文本；PDF/WORD 时为**原文字节的 Base64**。理由：不破 PR1 构造签名/控制器契约/`parseRejectsBlankContentWith400`（bean 层 400 仍生效），以最简实现满足 §3.2「扩展 base64 字段」意图。编排 `resolveRawBytes(format, content)`：文本走 UTF-8 字节；二进制走 `Base64.decode`，非法 Base64 → `ErrorCode.BAD_REQUEST`（400）。
2. **页号是逐段属性（非逐节）**。PDF 单节可跨页，节级页号会误标 → 违铁律 #1。故 `ParsedSection.paragraphs` 由 `List<String>` 重构为 `List<ParsedParagraph>`（`text` + 可空 `page`）。文本/Word 正文 `page=null`（锚点无 `p` 前缀，PR1 锚点不变）；PDF `page=` 1 基页号。
3. **抽共享分章器 `DocumentSectionizer`**（rule of three：文本 / PDF / Word〔PR3〕）。输入有序 `TextLine(text, page)` 流，输出 `ParsedDocument`（标题检测 + 编号路径 + 前言 §0 + 超长按句界切分 + 空输入诚实抛 `DocumentParseException`「空文档」）。`StructuredTextDocumentParser` 与 `PdfDocumentParser` 均喂行给它，去重解析骨架。
4. **页文本提取**：PDFBox `PDFTextStripper` 逐页 `setStartPage/EndPage` 取文本，按行 strip → `TextLine(line, p)`。全 PDF 无可提取文本（扫描件）→ 诚实 `DocumentParseException`（不做 OCR，OCR 受 P6 + LLM 闸，YAGNI）。损坏字节 `IOException` → `DocumentParseException`。
5. **无新表 / 无新端点 / 无新权限 / 无新迁移**：`mk_doc_parse_job` 的 `ck_*_format` 已含 `'PDF'`；走既有 `documents:parse`（knowledge.write）。`PdfDocumentParser` 为 `@Component` 自动并入 `List<DocumentParser>` 分派，整链零控制器改动 → 产品目录不漂移。

## 依赖

pom 加 `pdfbox.version=3.0.3` + `org.apache.pdfbox:pdfbox`（纯 Java、Apache-2.0、离线确定性、JVM 事实标准；已验证可下载）。OFD/版式无可靠 JVM 库 → 诚实 UNSUPPORTED（不实现）。

## Tasks（TDD 逐单元红绿）

- **T1 页锚点物化（重构 + 新行为）**
  - 新 `ParsedParagraph(text, Integer page)`；`ParsedSection.paragraphs` → `List<ParsedParagraph>`。
  - 重构 `StructuredTextDocumentParser` 产 `ParsedParagraph(text, null)`；同步既有 `StructuredTextDocumentParserTest`/`ParsedDocumentMaterializerTest` 至新类型（page=null，锚点不变）→ 绿（重构保形）。
  - RED：`ParsedDocumentMaterializerTest` 加用例——段带 `page=3` → 锚点 `p3/§2/¶1`。GREEN：物化器锚点加 `(page==null?"":"p"+page+"/")` 前缀。
- **T2 共享分章器 `DocumentSectionizer`**
  - 抽 `DocumentSectionizer.sectionize(List<TextLine>)`（含 `TextLine(text, page)`）；`StructuredTextDocumentParser` 改用之 → 既有解析测试保绿（refactor）。
- **T3 `PdfDocumentParser`（PDFBox）**
  - pom 加 PDFBox。RED：`PdfDocumentParserTest`——`PDFBox` 确定性构造 2 页夹具 PDF（ASCII 编号标题 + 段落，避免 CJK 字体内嵌），解析断言章节 `1`/`1.1`/`2` + 段落 page=1/1/2；`supports`；空文本/损坏诚实抛。GREEN：实现 `@Component PdfDocumentParser` 逐页提取 → `DocumentSectionizer`。
- **T4 编排 Base64 传输**
  - RED：`DocumentParseOrchestrationServiceTest`——改 `unsupportedFormatFailsHonestly` 为 WORD + 合法 Base64 → FAILED「暂不支持」；加 `rejectsInvalidBase64ForBinaryFormat`（PDF + 非法 Base64 → ApiException 400）。GREEN：`resolveRawBytes(format, content)`。
- **T5 端到端集成**
  - `DocumentParseIntegrationTest` 加：建 SourceDocument → 构造 2 页 PDF → Base64 → `submit(format=PDF)` → SUCCEEDED + `source_fragment` 锚点含 `p1/…`、`p2/…` + 真实 SHA-256。
- **T6 收口**：全量 `mvn test` + 四门禁 changed + 五方言 Flyway smoke（无迁移变更，验证不回归）+ `git diff --check` + 前端 `productCatalog.test.ts`（应无漂移）。

## 红线

铁律 #1（锚点/hash 真实，扫描件/损坏诚实 FAILED 禁伪结构）· B0 先于模型（PDFBox 确定性，无模型，OCR 受 P6 不做）· 域归属 engine-knowledge 不依赖 compliance · 合并 main 逐 PR 授权（不自动合）· 真实性门禁禁 Javadoc 占位/模拟/仿真/演示/placeholder。
