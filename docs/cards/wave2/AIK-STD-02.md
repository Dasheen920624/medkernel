# AIK-STD-02 · 文档解析、引用锚点与版本存证

> 读卡前置：[核心 CONSTITUTION](../../CONSTITUTION.md) + [wave2 域简报](_brief.md)。
> 迁移来源：详规 §8.2 文档解析 · backlog 第二波 X-AIK · 铁律 #1 真实性。

## 身份
- 卡 ID：AIK-STD-02（= backlog `AIK-STD-02`）
- 域：wave2（X-AIK）
- 关联场景：S3、S15
- 依赖卡：[AIK-STD-01](AIK-STD-01.md)（资产 schema）· [KNOW-01](../D2/KNOW-01.md)（来源/hash）
- 工作量：6d
- owner / reviewer：待派单（owner ≠ reviewer）

## 目标
**文档解析 → 引用锚点 → 版本存证**：PDF/Word 解析到章节 + 表格理解，每条产出可回溯到原文页/章/条锚点 + 原文 hash，杜绝凭空生成。

## 现状（核查 2026-05-31，不深调研）
承载＝[KNOW-01](../D2/KNOW-01.md) 来源登记 + hash 雏形。本卡＝**新建文档解析流水线 + 锚点 + 存证**（当前无 PDF/Word 章节解析与表格理解）。

## 功能要求（原子可测条目）
- [~] FR-1 解析到章节：解析为结构化章节树（标题层级）。**PR1 ✅ 结构化文本（Markdown/编号标题）**；PDF（PR2 PDFBox）/ Word（PR3 POI）待接入。
- [ ] FR-2 表格理解：表格解析为结构化行列（不丢语义）。**PR3 待做**（表→行/单元格片段）。
- [x] FR-3 引用锚点：每抽取条目带原文锚点（§章节/¶段，PR2 追加页）。**PR1 ✅**（物化进 `source_fragment.anchor_path`）。
- [x] FR-4 版本存证：原文 + 解析结果 hash 存证，可验未篡改。**PR1 ✅**（`source_version.content_hash` 原文 SHA-256 + `file_uri`；job `source_hash`）。
- [x] FR-5 解析失败诚实：无法解析诚实标记，不产伪结构。**PR1 ✅**（空/不支持格式 → job FAILED + error，不物化）。

## 实现进度
- **PR1（管线核心，本卡第一刀）**：端口-适配器架构 `DocumentParser` + B0 `StructuredTextDocumentParser`（确定性章节解析，零外部依赖）+ `ParsedDocumentMaterializer`（物化进既有 `source_version`/`source_fragment`，**复用 KNOW-01 不建 `doc_anchor` 重复表**）+ `DocumentParseOrchestrationService`（job 生命周期 + 诚实失败降级）+ `DocumentParseController`（`knowledge.write/read` 复用不新增权限码）+ 唯一新表 `mk_doc_parse_job`（V133 五方言）。归 `engine-knowledge` 域新包 `engine.knowledge.parsing`。
- **PR2 待做**：`PdfDocumentParser`（Apache PDFBox，FR-1 PDF + 页锚点）。
- **PR3 待做**：`WordDocumentParser`（Apache POI）+ 表格理解（FR-2）。
- **P6 分寸**：仅建机制 + B0 + 测试夹具验证，不连真实文献库、不进 P6；模型增强解析受 LLM/P6 闸不实现。

## 接口 / 数据契约
- `doc_parse_job`（源文件/状态/结果 ref/hash）+ `doc_anchor`（条目→页/章/段），五方言；大列表 [API-13](../D0/API-13.md)。

## 视角清单（11 视角）
1. 产品架构：知识入厂的解析层。 2. 产品体验：N·A。 3. 系统与数据架构：大文档解析异步、可重试。 4. 临床医疗安全：解析产物经审核才临床用。 5. 知识与数据治理：★锚点 + hash = 可追溯治理底座。 6. 安全合规与监管：原文存证可审计。 7. 集团化与多租户治理：按 org 隔离来源。 8. 集成与互操作：支持常见 PDF/Word 格式。 9. 运维/SRE/国产化：国产格式（WPS/版式）兼容尽力。 10. 质量与真实性审计：★每条带真实锚点，禁无锚点抽取。 11. AI/模型治理与可降级：解析可纯规则（B0），模型增强可选。

## 适用不变量
- 命中核心约束：**铁律 #1 真实性**（锚点+hash）· **核心 §6 来源可溯**。
- 本卡落点：解析到章节/表格 + 引用锚点 + 版本存证，抽取条目可回原文。

## 验收 + 验证
- [~] AC-1（FR-1~3）：解析 + 表格 + 锚点正确。**PR1 ✅ 文本章节 + 锚点**；PDF/Word/表格 待 PR2/PR3。
- [x] AC-2（FR-4/5）：hash 存证可验；失败诚实。**PR1 ✅**。
- [x] T-GATE：后端真实性门禁全绿（PR1：authenticity/config/migration/comment-zh changed 全过）。
- [x] B0 验收：★纯规则解析路径可用（不依赖模型）。**PR1 ✅** `StructuredTextDocumentParser` 确定性解析。

## 完工证据
- 代码 permalink：解析流水线 + 锚点 + 存证。
- 测试：章节/表格/锚点/hash/失败诚实。
- 审计员签字：@<reviewer>（owner ≠ reviewer）。
