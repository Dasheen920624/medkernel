# AIK-STD-02 PR1 文档解析管线核心 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 建文档解析管线核心：结构化文本经确定性 B0 解析为章节树 + 段落锚点，物化进既有 KNOW-01 `source_version`/`source_fragment`，由 `mk_doc_parse_job` 跟踪生命周期，解析失败诚实标记不产伪结构。

**Architecture:** 端口-适配器（`DocumentParser` 端口 → `ParsedDocument`）+ 物化器（`ParsedDocumentMaterializer` 落 source_*）+ 编排服务（`DocumentParseOrchestrationService` job 生命周期）。复用 KNOW-01 受控源三表承载锚点/存证（**不建 doc_anchor 重复表**），唯一新表 `mk_doc_parse_job`。归 `engine-knowledge` 域新包 `com.medkernel.engine.knowledge.parsing`，复用 `knowledge.write/read` 权限不新增码。

**Tech Stack:** Java 21 / Spring Boot / Spring Data JDBC / Flyway 五方言（h2/postgres/oracle/dm/kingbase）/ JUnit5 + AssertJ + Mockito。PR1 **零新第三方依赖**（StructuredText 纯规则）。

**设计依据：** [`docs/superpowers/specs/2026-06-15-aikstd02-doc-parse-pipeline-design.md`](../specs/2026-06-15-aikstd02-doc-parse-pipeline-design.md)

---

## File Structure（PR1 新增/修改）

新包 `medkernel-backend/src/main/java/com/medkernel/engine/knowledge/parsing/`：
- `DocumentFormat.java` — 枚举 STRUCTURED_TEXT/PDF/WORD（PR1 仅 STRUCTURED_TEXT 有解析器）
- `ParseJobStatus.java` — 枚举 PENDING/RUNNING/SUCCEEDED/FAILED
- `DocParseJob.java` — `@Table("mk_doc_parse_job")` record 实体
- `DocParseJobRepository.java` — 强租户隔离仓储
- `DocumentParseException.java` — 解析失败诚实异常（FR-5）
- `ParseInput.java` — record（sourceDocumentId/versionNo/fileName/format/rawBytes/createdBy）
- `ParsedSection.java` — record（numberPath/level/title/List<String> paragraphs）
- `ParsedDocument.java` — record（List<ParsedSection> sections）
- `DocumentParser.java` — 端口接口（supports + parse）
- `StructuredTextDocumentParser.java` — `@Component` B0 适配器
- `MaterializationResult.java` — record（sourceVersionId/sectionCount/fragmentCount）
- `ParsedDocumentMaterializer.java` — `@Service` 物化器
- `DocumentParseRequest.java` — record 请求 DTO（带校验）
- `DocumentParseOrchestrationService.java` — `@Service` 编排
- `DocumentParseController.java` — `@RestController`

修改：
- `medkernel-backend/src/main/java/com/medkernel/shared/hash/Sha256ContentHash.java` — 加 `sha256Bytes(byte[], emptyMsg)`（FR-4 原文字节存证）
- `medkernel-backend/src/main/resources/db/migration/{h2,postgres,oracle,dm,kingbase}/V133__doc_parse_job.sql` — 新建表
- `medkernel-backend/.../DomainOwnershipCatalog`（engine-knowledge 加 `mk_doc_parse_job`）
- `medkernel-backend/src/test/java/com/medkernel/migration/MigrationBaselineContractTest.java`（多 Set + V133）
- `FlywayMultiDialectSmokeTest.java` + `H2BaselineMigrationTest.java`（LATEST_MIGRATION_VERSION 132→133）
- 产品功能目录（`scripts/audit/export-product-capabilities.mjs` 重生成）

测试：上列各单元 `*Test.java` + `DocumentParseIntegrationTest.java`（真实 H2 端到端）。

---

## Task 1: `mk_doc_parse_job` 迁移（五方言）+ 实体 + 仓储

**Files:**
- Create: `medkernel-backend/src/main/resources/db/migration/h2/V133__doc_parse_job.sql`
- Create: 同名于 `postgres/`、`oracle/`、`dm/`、`kingbase/`
- Create: `medkernel-backend/src/main/java/com/medkernel/engine/knowledge/parsing/ParseJobStatus.java`
- Create: `.../parsing/DocumentFormat.java`
- Create: `.../parsing/DocParseJob.java`
- Create: `.../parsing/DocParseJobRepository.java`
- Modify: `medkernel-backend/src/test/java/com/medkernel/migration/MigrationBaselineContractTest.java`
- Modify: `medkernel-backend/src/test/java/com/medkernel/migration/FlywayMultiDialectSmokeTest.java:33`
- Modify: `medkernel-backend/src/test/java/com/medkernel/migration/H2BaselineMigrationTest.java:25`

- [ ] **Step 1: 写迁移契约失败测试（更新基线 Set）**

在 `MigrationBaselineContractTest.java` 更新以下静态集合（保持既有元素，新增本卡项）：
- `EXPECTED_MIGRATIONS`：在 `"V132__knowledge_review_return.sql"` 之后追加 `"V133__doc_parse_job.sql"`。
- `REQUIRED_TABLES`：追加 `"mk_doc_parse_job"`。
- `REQUIRED_INDEXES`：追加 `"idx_mk_doc_parse_job_lookup"`。
- 约束名集合（约 line 564 区块）：追加 `"uk_mk_doc_parse_job_code"`、`"ck_mk_doc_parse_job_format"`、`"ck_mk_doc_parse_job_status"`。
- `TENANT_TABLES`：追加 `"mk_doc_parse_job"`。
- `MUTABLE_AUDITED_TABLES`：追加 `"mk_doc_parse_job"`。
- `LIFECYCLE_FIELDS`：追加 `Map.entry("mk_doc_parse_job", Set.of("status"))`。

在 `FlywayMultiDialectSmokeTest.java:33` 与 `H2BaselineMigrationTest.java:25` 将 `LATEST_MIGRATION_VERSION = 132` 改为 `133`。

- [ ] **Step 2: 运行迁移契约测试，确认失败**

Run: `cd medkernel-backend && mvn -q test -Dtest=MigrationBaselineContractTest,H2BaselineMigrationTest`
Expected: FAIL（缺 V133 文件 / 表 / 索引 / 约束）。

- [ ] **Step 3: 写五方言迁移文件**

`h2/V133__doc_parse_job.sql`：
```sql
-- MedKernel 第二阶段 P2-C · AIK-STD-02 文档解析 job（H2）
-- 文档解析管线生命周期跟踪：源文件 + 格式 + 原文 SHA-256 + 状态 + 解析产物计数；成功后物化进 source_version/source_fragment。
-- ROLLBACK：确认无引用后 DROP TABLE mk_doc_parse_job。

CREATE TABLE IF NOT EXISTS mk_doc_parse_job (
    id                       BIGINT        GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    tenant_id                VARCHAR(64)   NOT NULL,
    job_code                 VARCHAR(64)   NOT NULL,
    source_document_id       BIGINT        NOT NULL,
    source_file_name         VARCHAR(512)  NOT NULL,
    document_format          VARCHAR(24)   NOT NULL,
    source_hash              VARCHAR(64)   NOT NULL,
    status                   VARCHAR(24)   NOT NULL DEFAULT 'PENDING',
    result_source_version_id BIGINT        NULL,
    parsed_section_count     INTEGER       NULL,
    parsed_fragment_count    INTEGER       NULL,
    error_message            VARCHAR(1024) NULL,
    created_at               TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by               VARCHAR(64)   NULL,
    updated_at               TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by               VARCHAR(64)   NULL,
    CONSTRAINT uk_mk_doc_parse_job_code UNIQUE (job_code),
    CONSTRAINT ck_mk_doc_parse_job_format CHECK (document_format IN ('STRUCTURED_TEXT', 'PDF', 'WORD')),
    CONSTRAINT ck_mk_doc_parse_job_status CHECK (status IN ('PENDING', 'RUNNING', 'SUCCEEDED', 'FAILED'))
);

CREATE INDEX idx_mk_doc_parse_job_lookup ON mk_doc_parse_job (tenant_id, source_document_id, status);
```

`postgres/V133__doc_parse_job.sql`：同上，但 `id BIGSERIAL PRIMARY KEY`、`TIMESTAMP`→`TIMESTAMPTZ`、`DEFAULT CURRENT_TIMESTAMP`→`DEFAULT NOW()`，并在末尾追加：
```sql
COMMENT ON TABLE mk_doc_parse_job IS '文档解析 job：解析管线生命周期跟踪，记录源文件与原文指纹及解析产物计数，成功后物化进受控来源版本与片段';
COMMENT ON COLUMN mk_doc_parse_job.source_hash IS '原文字节 SHA-256 指纹，用于版本存证与幂等去重';
COMMENT ON COLUMN mk_doc_parse_job.error_message IS '解析失败诚实原因，禁止失败时产半真片段';
```

`oracle/V133__doc_parse_job.sql`：`id NUMBER GENERATED ALWAYS AS IDENTITY PRIMARY KEY`、`VARCHAR(n)`→`VARCHAR2(n)`、`TIMESTAMP`、`DEFAULT SYSTIMESTAMP`、`BOOLEAN` 无（本表无）；CHECK/UNIQUE/INDEX 同 h2；中文 `COMMENT ON TABLE/COLUMN` 同 postgres 文案。参照同目录既有 `V130__knowledge_production_job.sql` 的 Oracle 方言写法逐列对齐。

`dm/V133__doc_parse_job.sql` 与 `kingbase/V133__doc_parse_job.sql`：分别对照同目录 `V130__knowledge_production_job.sql` 的方言写法（DM 近 Oracle、Kingbase 近 PostgreSQL），列/约束/索引/中文 COMMENT 一致。

- [ ] **Step 4: 写实体与枚举**

`ParseJobStatus.java`：
```java
package com.medkernel.engine.knowledge.parsing;

/** 文档解析 job 状态（AIK-STD-02）。对应 mk_doc_parse_job.status CHECK 约束。 */
public enum ParseJobStatus {
    PENDING, RUNNING, SUCCEEDED, FAILED
}
```

`DocumentFormat.java`：
```java
package com.medkernel.engine.knowledge.parsing;

/** 文档格式（AIK-STD-02）。PR1 仅 STRUCTURED_TEXT 有确定性解析器；PDF/WORD 由 PR2/PR3 适配器接入。 */
public enum DocumentFormat {
    STRUCTURED_TEXT, PDF, WORD
}
```

`DocParseJob.java`：
```java
package com.medkernel.engine.knowledge.parsing;

import java.time.Instant;

import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

/**
 * 文档解析 job（AIK-STD-02）。跟踪「源文件 → 解析 → 物化进受控来源」的生命周期。
 * 成功后 {@code resultSourceVersionId} 指向物化的 source_version；失败 {@code errorMessage} 诚实记原因。
 */
@Table("mk_doc_parse_job")
public record DocParseJob(
    @Id Long id,
    @Column("tenant_id") String tenantId,
    @Column("job_code") String jobCode,
    @Column("source_document_id") Long sourceDocumentId,
    @Column("source_file_name") String sourceFileName,
    @Column("document_format") DocumentFormat documentFormat,
    @Column("source_hash") String sourceHash,
    @Column("status") ParseJobStatus status,
    @Column("result_source_version_id") Long resultSourceVersionId,
    @Column("parsed_section_count") Integer parsedSectionCount,
    @Column("parsed_fragment_count") Integer parsedFragmentCount,
    @Column("error_message") String errorMessage,
    @Column("created_at") Instant createdAt,
    @Column("created_by") String createdBy,
    @Column("updated_at") Instant updatedAt,
    @Column("updated_by") String updatedBy
) {
}
```

`DocParseJobRepository.java`：
```java
package com.medkernel.engine.knowledge.parsing;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.ListCrudRepository;
import org.springframework.stereotype.Repository;

/** 文档解析 job 仓储（AIK-STD-02）。所有查询强制带 tenantId。 */
@Repository
public interface DocParseJobRepository extends ListCrudRepository<DocParseJob, Long> {

    Optional<DocParseJob> findByTenantIdAndJobCode(String tenantId, String jobCode);

    @Query("""
        SELECT * FROM mk_doc_parse_job
        WHERE tenant_id = :tenantId
        ORDER BY created_at DESC, id DESC
        OFFSET :offset ROWS FETCH NEXT :limit ROWS ONLY
        """)
    List<DocParseJob> pageByTenantId(String tenantId, int offset, int limit);
}
```

- [ ] **Step 5: 运行迁移契约测试，确认通过**

Run: `cd medkernel-backend && mvn -q test -Dtest=MigrationBaselineContractTest,H2BaselineMigrationTest`
Expected: PASS。

- [ ] **Step 6: 五方言 Flyway 冒烟（真实容器）**

Run: `cd medkernel-backend && mvn -q test -Dtest=FlywayMultiDialectSmokeTest`
Expected: PASS（V133 在 h2/postgres/oracle/dm/kingbase 干净建表 + 索引 + 两 CHECK + UNIQUE）。

- [ ] **Step 7: 提交**

```bash
git add medkernel-backend/src/main/resources/db/migration medkernel-backend/src/main/java/com/medkernel/engine/knowledge/parsing medkernel-backend/src/test/java/com/medkernel/migration
git commit -m "feat(aikstd02/PR1): mk_doc_parse_job 五方言迁移 + 实体 + 仓储"
```

---

## Task 2: 解析端口 + 值对象 + 诚实失败异常

**Files:**
- Create: `.../parsing/DocumentParseException.java`
- Create: `.../parsing/ParseInput.java`
- Create: `.../parsing/ParsedSection.java`
- Create: `.../parsing/ParsedDocument.java`
- Create: `.../parsing/DocumentParser.java`

- [ ] **Step 1: 写值对象与端口（无行为，无需先写测试——由 Task 3 解析器测试驱动）**

`DocumentParseException.java`：
```java
package com.medkernel.engine.knowledge.parsing;

/** 解析失败诚实异常（AIK-STD-02 FR-5）：无法解析时抛出，编排层据此记 FAILED，绝不产伪结构。 */
public class DocumentParseException extends RuntimeException {
    public DocumentParseException(String message) {
        super(message);
    }
}
```

`ParseInput.java`：
```java
package com.medkernel.engine.knowledge.parsing;

/**
 * 解析输入（AIK-STD-02）。稳定端口入参，覆盖全格式：原始字节 + 文件名 + 声明格式。
 * PR1 STRUCTURED_TEXT 解析器按 UTF-8 解码 rawBytes；PR2/PR3 二进制格式直接读字节。
 */
public record ParseInput(
    Long sourceDocumentId,
    String versionNo,
    String fileName,
    DocumentFormat format,
    byte[] rawBytes,
    String createdBy
) {
}
```

`ParsedSection.java`：
```java
package com.medkernel.engine.knowledge.parsing;

import java.util.List;

/**
 * 解析出的章节（AIK-STD-02 FR-1）。{@code numberPath} 编码层级（如 "2.1.3"，根序为 "0"=前言），
 * {@code level} 为层级深度，章节树以 numberPath 前缀关系隐式表达。{@code paragraphs} 为章节正文段落。
 */
public record ParsedSection(
    String numberPath,
    int level,
    String title,
    List<String> paragraphs
) {
}
```

`ParsedDocument.java`：
```java
package com.medkernel.engine.knowledge.parsing;

import java.util.List;

/** 解析产物（AIK-STD-02 FR-1）：扁平章节列表（numberPath 编码树位置），物化器据此落锚点片段。 */
public record ParsedDocument(
    List<ParsedSection> sections
) {
}
```

`DocumentParser.java`：
```java
package com.medkernel.engine.knowledge.parsing;

/**
 * 文档解析端口（AIK-STD-02）。按格式分派：{@link #supports} 声明可解析格式，
 * {@link #parse} 解析为章节树；无法解析（损坏/空/不支持）抛 {@link DocumentParseException}（FR-5 诚实失败）。
 */
public interface DocumentParser {

    boolean supports(DocumentFormat format);

    ParsedDocument parse(ParseInput input);
}
```

- [ ] **Step 2: 编译确认无误**

Run: `cd medkernel-backend && mvn -q compile`
Expected: BUILD SUCCESS。

- [ ] **Step 3: 提交**

```bash
git add medkernel-backend/src/main/java/com/medkernel/engine/knowledge/parsing
git commit -m "feat(aikstd02/PR1): 解析端口 DocumentParser + 值对象 + 诚实失败异常"
```

---

## Task 3: `StructuredTextDocumentParser`（B0 确定性解析）

**Files:**
- Create: `.../parsing/StructuredTextDocumentParser.java`
- Test: `medkernel-backend/src/test/java/com/medkernel/engine/knowledge/parsing/StructuredTextDocumentParserTest.java`

- [ ] **Step 1: 写失败测试**

```java
package com.medkernel.engine.knowledge.parsing;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;

import org.junit.jupiter.api.Test;

class StructuredTextDocumentParserTest {

    private final StructuredTextDocumentParser parser = new StructuredTextDocumentParser();

    private ParseInput input(String text) {
        return new ParseInput(1L, "v1", "f.txt", DocumentFormat.STRUCTURED_TEXT, text.getBytes(UTF_8), "tester");
    }

    @Test
    void supportsOnlyStructuredText() {
        assertThat(parser.supports(DocumentFormat.STRUCTURED_TEXT)).isTrue();
        assertThat(parser.supports(DocumentFormat.PDF)).isFalse();
        assertThat(parser.supports(DocumentFormat.WORD)).isFalse();
    }

    @Test
    void parsesMarkdownHeadingsIntoChapterTree() {
        ParsedDocument doc = parser.parse(input("""
            # 总则
            本指南适用于成人。

            ## 适应证
            用于确诊患者。
            """));
        assertThat(doc.sections()).hasSize(2);
        ParsedSection top = doc.sections().get(0);
        assertThat(top.numberPath()).isEqualTo("1");
        assertThat(top.level()).isEqualTo(1);
        assertThat(top.title()).isEqualTo("总则");
        assertThat(top.paragraphs()).containsExactly("本指南适用于成人。");
        ParsedSection sub = doc.sections().get(1);
        assertThat(sub.numberPath()).isEqualTo("1.1");
        assertThat(sub.level()).isEqualTo(2);
        assertThat(sub.title()).isEqualTo("适应证");
        assertThat(sub.paragraphs()).containsExactly("用于确诊患者。");
    }

    @Test
    void parsesNumberedHeadings() {
        ParsedDocument doc = parser.parse(input("""
            1 适用范围
            适用于二级以上医院。
            1.1 人群
            成人患者。
            """));
        assertThat(doc.sections()).extracting(ParsedSection::numberPath)
            .containsExactly("1", "1.1");
        assertThat(doc.sections().get(1).title()).isEqualTo("人群");
    }

    @Test
    void leadingTextBeforeFirstHeadingGoesToPreambleSectionZero() {
        ParsedDocument doc = parser.parse(input("""
            前置说明无标题。
            # 第一章
            正文。
            """));
        assertThat(doc.sections().get(0).numberPath()).isEqualTo("0");
        assertThat(doc.sections().get(0).title()).isEqualTo("前言");
        assertThat(doc.sections().get(0).paragraphs()).containsExactly("前置说明无标题。");
    }

    @Test
    void splitsParagraphExceedingExcerptLimitWithoutLosingContent() {
        String longPara = "句一。" + "字".repeat(2100) + "。";
        ParsedDocument doc = parser.parse(input("# 标题\n" + longPara + "\n"));
        List<String> paras = doc.sections().get(0).paragraphs();
        assertThat(paras).hasSizeGreaterThan(1);
        assertThat(paras).allMatch(p -> p.length() <= 2048);
        assertThat(String.join("", paras)).isEqualTo(longPara);
    }

    @Test
    void blankInputFailsHonestly() {
        assertThatThrownBy(() -> parser.parse(input("   \n  \n")))
            .isInstanceOf(DocumentParseException.class)
            .hasMessageContaining("空文档");
    }
}
```

- [ ] **Step 2: 运行确认失败**

Run: `cd medkernel-backend && mvn -q test -Dtest=StructuredTextDocumentParserTest`
Expected: FAIL（`StructuredTextDocumentParser` 未实现）。

- [ ] **Step 3: 实现解析器**

```java
package com.medkernel.engine.knowledge.parsing;

import static java.nio.charset.StandardCharsets.UTF_8;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.stereotype.Component;

/**
 * 结构化文本解析器（AIK-STD-02 B0）。纯规则、确定性、零外部依赖：
 * 识别 Markdown 标题（{@code #}/{@code ##}…）与点分编号标题（{@code 1}/{@code 1.1}），
 * 解析为章节树；标题前正文归入「前言」§0；超出片段长度上限的段落按句界切分不丢语义。
 */
@Component
public class StructuredTextDocumentParser implements DocumentParser {

    private static final int MAX_EXCERPT = 2048;
    private static final Pattern MARKDOWN = Pattern.compile("^(#{1,6})\\s+(.+?)\\s*$");
    private static final Pattern NUMBERED = Pattern.compile("^(\\d+(?:\\.\\d+)*)[\\s、.]+(.+?)\\s*$");

    @Override
    public boolean supports(DocumentFormat format) {
        return format == DocumentFormat.STRUCTURED_TEXT;
    }

    @Override
    public ParsedDocument parse(ParseInput input) {
        String text = new String(input.rawBytes(), UTF_8);
        if (text.isBlank()) {
            throw new DocumentParseException("空文档无法解析，禁止产伪结构");
        }
        List<ParsedSection> sections = new ArrayList<>();
        // 自动编号计数器（Markdown 用，下标 = 层级）
        int[] counters = new int[7];
        SectionBuilder current = null;
        List<String> preamble = new ArrayList<>();

        for (String raw : text.split("\\R", -1)) {
            String line = raw.strip();
            if (line.isEmpty()) {
                continue;
            }
            Heading heading = detectHeading(line, counters);
            if (heading != null) {
                if (current != null) {
                    sections.add(current.build());
                }
                current = new SectionBuilder(heading.numberPath(), heading.level(), heading.title());
            } else if (current == null) {
                preamble.add(line);
            } else {
                current.addParagraph(line);
            }
        }
        if (current != null) {
            sections.add(current.build());
        }
        if (!preamble.isEmpty()) {
            SectionBuilder pre = new SectionBuilder("0", 1, "前言");
            preamble.forEach(pre::addParagraph);
            sections.add(0, pre.build());
        }
        if (sections.isEmpty()) {
            throw new DocumentParseException("空文档无法解析，禁止产伪结构");
        }
        return new ParsedDocument(sections);
    }

    private Heading detectHeading(String line, int[] counters) {
        Matcher md = MARKDOWN.matcher(line);
        if (md.matches()) {
            int level = md.group(1).length();
            counters[level]++;
            for (int i = level + 1; i < counters.length; i++) {
                counters[i] = 0;
            }
            StringBuilder path = new StringBuilder();
            for (int i = 1; i <= level; i++) {
                if (counters[i] == 0) {
                    counters[i] = 1;
                }
                if (i > 1) {
                    path.append('.');
                }
                path.append(counters[i]);
            }
            return new Heading(path.toString(), level, md.group(2).strip());
        }
        Matcher num = NUMBERED.matcher(line);
        if (num.matches()) {
            String numberPath = num.group(1);
            int level = numberPath.split("\\.").length;
            return new Heading(numberPath, level, num.group(2).strip());
        }
        return null;
    }

    private record Heading(String numberPath, int level, String title) {
    }

    /** 章节累积器，段落超长按句界切分（。！？.!?）不丢语义。 */
    private static final class SectionBuilder {
        private final String numberPath;
        private final int level;
        private final String title;
        private final List<String> paragraphs = new ArrayList<>();

        SectionBuilder(String numberPath, int level, String title) {
            this.numberPath = numberPath;
            this.level = level;
            this.title = title;
        }

        void addParagraph(String para) {
            if (para.length() <= MAX_EXCERPT) {
                paragraphs.add(para);
                return;
            }
            int start = 0;
            while (start < para.length()) {
                int end = Math.min(start + MAX_EXCERPT, para.length());
                if (end < para.length()) {
                    int cut = lastSentenceBoundary(para, start, end);
                    if (cut > start) {
                        end = cut;
                    }
                }
                paragraphs.add(para.substring(start, end));
                start = end;
            }
        }

        private int lastSentenceBoundary(String s, int from, int to) {
            for (int i = to - 1; i > from; i--) {
                char c = s.charAt(i);
                if (c == '。' || c == '！' || c == '？' || c == '.' || c == '!' || c == '?') {
                    return i + 1;
                }
            }
            return to;
        }

        ParsedSection build() {
            return new ParsedSection(numberPath, level, title, List.copyOf(paragraphs));
        }
    }
}
```

- [ ] **Step 4: 运行确认通过**

Run: `cd medkernel-backend && mvn -q test -Dtest=StructuredTextDocumentParserTest`
Expected: PASS（6 测试）。

- [ ] **Step 5: 提交**

```bash
git add medkernel-backend/src/main/java/com/medkernel/engine/knowledge/parsing/StructuredTextDocumentParser.java medkernel-backend/src/test/java/com/medkernel/engine/knowledge/parsing/StructuredTextDocumentParserTest.java
git commit -m "feat(aikstd02/PR1): StructuredTextDocumentParser B0 确定性章节解析"
```

---

## Task 4: `Sha256ContentHash.sha256Bytes` + `ParsedDocumentMaterializer`

**Files:**
- Modify: `medkernel-backend/src/main/java/com/medkernel/shared/hash/Sha256ContentHash.java`
- Create: `.../parsing/MaterializationResult.java`
- Create: `.../parsing/ParsedDocumentMaterializer.java`
- Test: `medkernel-backend/src/test/java/com/medkernel/shared/hash/Sha256ContentHashTest.java`（若已存在则追加用例）
- Test: `medkernel-backend/src/test/java/com/medkernel/engine/knowledge/parsing/ParsedDocumentMaterializerTest.java`

- [ ] **Step 1: 写 `sha256Bytes` 失败测试**

在 `Sha256ContentHashTest.java`（不存在则新建）加：
```java
package com.medkernel.shared.hash;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

import com.medkernel.shared.api.error.ApiException;

class Sha256ContentHashBytesTest {

    @Test
    void sha256BytesMatchesStringHashForUtf8() {
        byte[] bytes = "临床指南正文".getBytes(UTF_8);
        assertThat(Sha256ContentHash.sha256Bytes(bytes, "空"))
            .isEqualTo(Sha256ContentHash.sha256("临床指南正文", "空"))
            .hasSize(64);
    }

    @Test
    void sha256BytesRejectsEmpty() {
        assertThatThrownBy(() -> Sha256ContentHash.sha256Bytes(new byte[0], "原文字节不能为空"))
            .isInstanceOf(ApiException.class)
            .hasMessageContaining("原文字节不能为空");
    }
}
```

- [ ] **Step 2: 运行确认失败**

Run: `cd medkernel-backend && mvn -q test -Dtest=Sha256ContentHashBytesTest`
Expected: FAIL（`sha256Bytes` 未定义）。

- [ ] **Step 3: 实现 `sha256Bytes`**

在 `Sha256ContentHash.java` 加（紧邻既有 `sha256(String, ...)`）：
```java
    /** 基于原始字节计算小写 SHA-256 十六进制字符串（FR-4 原文存证）。 */
    public static String sha256Bytes(byte[] content, String emptyContentMessage) {
        if (content == null || content.length == 0) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, emptyContentMessage);
        }
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(content);
            StringBuilder hex = new StringBuilder(hash.length * 2);
            for (byte b : hash) {
                hex.append(Character.forDigit((b >> 4) & 0xF, 16));
                hex.append(Character.forDigit(b & 0xF, 16));
            }
            return hex.toString();
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 不可用", e);
        }
    }
```
（注：若既有 `sha256(String,...)` 已含等价逐字节转十六进制逻辑，复用其内部实现；以仓库实际为准，避免重复。）

- [ ] **Step 4: 运行确认通过**

Run: `cd medkernel-backend && mvn -q test -Dtest=Sha256ContentHashBytesTest`
Expected: PASS。

- [ ] **Step 5: 写物化器失败测试**

`ParsedDocumentMaterializerTest.java`（Mockito 模拟仓储，验证物化逻辑——锚点编码 / hash / 幂等 / 计数）：
```java
package com.medkernel.engine.knowledge.parsing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.medkernel.engine.knowledge.SourceFragment;
import com.medkernel.engine.knowledge.SourceFragmentRepository;
import com.medkernel.engine.knowledge.SourceVersion;
import com.medkernel.engine.knowledge.SourceVersionRepository;

@ExtendWith(MockitoExtension.class)
class ParsedDocumentMaterializerTest {

    @Mock SourceVersionRepository versionRepository;
    @Mock SourceFragmentRepository fragmentRepository;
    @InjectMocks ParsedDocumentMaterializer materializer;

    private ParsedDocument doc() {
        return new ParsedDocument(List.of(
            new ParsedSection("1", 1, "总则", List.of("成人适用。", "禁用于孕妇。")),
            new ParsedSection("1.1", 2, "适应证", List.of("确诊后使用。"))));
    }

    @Test
    void materializesVersionAndFragmentsWithHierarchicalAnchors() {
        when(versionRepository.findBySourceDocumentIdAndContentHash(anyLong(), anyString()))
            .thenReturn(Optional.empty());
        when(versionRepository.save(any())).thenAnswer(i -> {
            SourceVersion v = i.getArgument(0);
            return new SourceVersion(99L, v.tenantId(), v.sourceDocumentId(), v.versionNo(),
                v.publishedAt(), v.contentHash(), v.fileUri(), v.language(), v.createdAt(), v.createdBy());
        });
        when(fragmentRepository.findBySourceVersionIdAndContentHash(anyLong(), anyString()))
            .thenReturn(Optional.empty());
        when(fragmentRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        MaterializationResult result = materializer.materialize(
            "t-1", 5L, "v1", "file:/g.txt", "a".repeat(64), doc(), "tester");

        assertThat(result.sourceVersionId()).isEqualTo(99L);
        assertThat(result.sectionCount()).isEqualTo(2);
        assertThat(result.fragmentCount()).isEqualTo(3);

        ArgumentCaptor<SourceFragment> cap = ArgumentCaptor.forClass(SourceFragment.class);
        org.mockito.Mockito.verify(fragmentRepository, org.mockito.Mockito.times(3)).save(cap.capture());
        assertThat(cap.getAllValues()).extracting(SourceFragment::anchorPath)
            .containsExactly("§1/¶1", "§1/¶2", "§1.1/¶1");
        assertThat(cap.getAllValues()).allSatisfy(f -> {
            assertThat(f.tenantId()).isEqualTo("t-1");
            assertThat(f.sourceVersionId()).isEqualTo(99L);
            assertThat(f.contentHash()).hasSize(64);
        });
        assertThat(cap.getAllValues().get(0).anchorLabel()).isEqualTo("总则");
        assertThat(cap.getAllValues().get(0).textExcerpt()).isEqualTo("成人适用。");
    }

    @Test
    void reusesExistingVersionAndSkipsDuplicateFragmentsIdempotently() {
        SourceVersion existing = new SourceVersion(99L, "t-1", 5L, "v1", Instant.now(),
            "a".repeat(64), "file:/g.txt", "zh-CN", Instant.now(), "tester");
        when(versionRepository.findBySourceDocumentIdAndContentHash(5L, "a".repeat(64)))
            .thenReturn(Optional.of(existing));
        when(fragmentRepository.findBySourceVersionIdAndContentHash(anyLong(), anyString()))
            .thenReturn(Optional.of(new SourceFragment(1L, "t-1", 99L, "§1/¶1", "总则", "成人适用。", "x", Instant.now())));

        MaterializationResult result = materializer.materialize(
            "t-1", 5L, "v1", "file:/g.txt", "a".repeat(64), doc(), "tester");

        assertThat(result.sourceVersionId()).isEqualTo(99L);
        assertThat(result.fragmentCount()).isZero();
        org.mockito.Mockito.verify(versionRepository, org.mockito.Mockito.never()).save(any());
        org.mockito.Mockito.verify(fragmentRepository, org.mockito.Mockito.never()).save(any());
    }
}
```

- [ ] **Step 6: 运行确认失败**

Run: `cd medkernel-backend && mvn -q test -Dtest=ParsedDocumentMaterializerTest`
Expected: FAIL（`ParsedDocumentMaterializer`/`MaterializationResult` 未实现）。

- [ ] **Step 7: 实现 `MaterializationResult` 与 `ParsedDocumentMaterializer`**

`MaterializationResult.java`：
```java
package com.medkernel.engine.knowledge.parsing;

/** 物化结果（AIK-STD-02）：物化的 source_version + 章节数 + 新增片段数（诚实计数）。 */
public record MaterializationResult(
    Long sourceVersionId,
    int sectionCount,
    int fragmentCount
) {
}
```

`ParsedDocumentMaterializer.java`：
```java
package com.medkernel.engine.knowledge.parsing;

import java.time.Instant;

import org.springframework.stereotype.Service;

import com.medkernel.engine.knowledge.SourceFragment;
import com.medkernel.engine.knowledge.SourceFragmentRepository;
import com.medkernel.engine.knowledge.SourceVersion;
import com.medkernel.engine.knowledge.SourceVersionRepository;
import com.medkernel.shared.hash.Sha256ContentHash;

/**
 * 解析产物物化器（AIK-STD-02 FR-3/4）。把 {@link ParsedDocument} 落入既有受控来源：
 * 注册/复用 {@code source_version}（原文 SHA-256 + file_uri = 存证）+ 逐段 upsert {@code source_fragment}
 * （anchor_path 编码 §章节/¶段 = 锚点）。强租户隔离 + 幂等（同 hash 复用不重复插入）。
 */
@Service
public class ParsedDocumentMaterializer {

    private static final String EMPTY_MSG = "片段正文不能为空，禁止为空内容生成片段指纹";

    private final SourceVersionRepository versionRepository;
    private final SourceFragmentRepository fragmentRepository;

    public ParsedDocumentMaterializer(SourceVersionRepository versionRepository,
                                      SourceFragmentRepository fragmentRepository) {
        this.versionRepository = versionRepository;
        this.fragmentRepository = fragmentRepository;
    }

    public MaterializationResult materialize(String tenantId, Long sourceDocumentId, String versionNo,
                                             String fileUri, String sourceHash, ParsedDocument doc, String actor) {
        SourceVersion version = versionRepository
            .findBySourceDocumentIdAndContentHash(sourceDocumentId, sourceHash)
            .orElseGet(() -> versionRepository.save(new SourceVersion(
                null, tenantId, sourceDocumentId, versionNo, Instant.now(),
                sourceHash, fileUri, "zh-CN", Instant.now(), actor)));

        int fragmentCount = 0;
        for (ParsedSection section : doc.sections()) {
            int paraIndex = 0;
            for (String paragraph : section.paragraphs()) {
                paraIndex++;
                String anchorPath = "§" + section.numberPath() + "/¶" + paraIndex;
                String contentHash = Sha256ContentHash.sha256(paragraph, EMPTY_MSG);
                if (fragmentRepository.findBySourceVersionIdAndContentHash(version.id(), contentHash).isPresent()) {
                    continue;
                }
                fragmentRepository.save(new SourceFragment(
                    null, tenantId, version.id(), anchorPath, section.title(), paragraph, contentHash, Instant.now()));
                fragmentCount++;
            }
        }
        return new MaterializationResult(version.id(), doc.sections().size(), fragmentCount);
    }
}
```

- [ ] **Step 8: 运行确认通过**

Run: `cd medkernel-backend && mvn -q test -Dtest=ParsedDocumentMaterializerTest,Sha256ContentHashBytesTest`
Expected: PASS。

- [ ] **Step 9: 提交**

```bash
git add medkernel-backend/src/main/java/com/medkernel/shared/hash/Sha256ContentHash.java medkernel-backend/src/main/java/com/medkernel/engine/knowledge/parsing medkernel-backend/src/test/java/com/medkernel
git commit -m "feat(aikstd02/PR1): 解析产物物化进 source_version/fragment + sha256Bytes 存证"
```

---

## Task 5: `DocumentParseOrchestrationService`（job 生命周期 + 诚实降级）

**Files:**
- Create: `.../parsing/DocumentParseOrchestrationService.java`
- Test: `medkernel-backend/src/test/java/com/medkernel/engine/knowledge/parsing/DocumentParseOrchestrationServiceTest.java`

- [ ] **Step 1: 写失败测试**

```java
package com.medkernel.engine.knowledge.parsing;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.medkernel.engine.knowledge.SourceDocument;
import com.medkernel.engine.knowledge.SourceDocumentRepository;
import com.medkernel.shared.api.error.ApiException;
import com.medkernel.shared.audit.AuditRecorder;
import com.medkernel.shared.context.OrgScope;
import com.medkernel.shared.context.RequestContext;

@ExtendWith(MockitoExtension.class)
class DocumentParseOrchestrationServiceTest {

    @Mock DocParseJobRepository jobRepository;
    @Mock SourceDocumentRepository sourceDocumentRepository;
    @Mock ParsedDocumentMaterializer materializer;
    @Mock AuditRecorder auditRecorder;

    private DocumentParseOrchestrationService service;

    @BeforeEach
    void setUp() {
        service = new DocumentParseOrchestrationService(
            jobRepository, sourceDocumentRepository,
            List.of(new StructuredTextDocumentParser()), materializer, auditRecorder);
        RequestContext.bindOrgScope(OrgScope.ofTenant("t-1"));
        when(jobRepository.save(any())).thenAnswer(i -> {
            DocParseJob j = i.getArgument(0);
            return j.id() == null
                ? new DocParseJob(1L, j.tenantId(), j.jobCode(), j.sourceDocumentId(), j.sourceFileName(),
                    j.documentFormat(), j.sourceHash(), j.status(), j.resultSourceVersionId(),
                    j.parsedSectionCount(), j.parsedFragmentCount(), j.errorMessage(),
                    j.createdAt(), j.createdBy(), j.updatedAt(), j.updatedBy())
                : j;
        });
        when(sourceDocumentRepository.findByTenantIdAndId("t-1", 5L))
            .thenReturn(Optional.of(any(SourceDocument.class) == null ? null : null));
    }

    @AfterEach
    void tearDown() {
        RequestContext.clear();
    }

    private DocumentParseRequest req(String content, DocumentFormat fmt) {
        return new DocumentParseRequest(5L, "v1", "g.txt", fmt, content);
    }

    @Test
    void parsesAndMaterializesToSucceeded() {
        when(sourceDocumentRepository.findByTenantIdAndId("t-1", 5L))
            .thenReturn(Optional.of(stubDoc()));
        when(materializer.materialize(any(), any(), any(), any(), any(), any(), any()))
            .thenReturn(new MaterializationResult(99L, 1, 2));

        DocParseJob job = service.submit(req("# 总则\n成人适用。\n禁用于孕妇。\n", DocumentFormat.STRUCTURED_TEXT));

        assertThat(job.status()).isEqualTo(ParseJobStatus.SUCCEEDED);
        assertThat(job.resultSourceVersionId()).isEqualTo(99L);
        assertThat(job.parsedFragmentCount()).isEqualTo(2);
        assertThat(job.sourceHash()).hasSize(64);
    }

    @Test
    void unparseableContentFailsHonestlyWithoutFakeStructure() {
        when(sourceDocumentRepository.findByTenantIdAndId("t-1", 5L))
            .thenReturn(Optional.of(stubDoc()));

        DocParseJob job = service.submit(req("   \n  \n", DocumentFormat.STRUCTURED_TEXT));

        assertThat(job.status()).isEqualTo(ParseJobStatus.FAILED);
        assertThat(job.errorMessage()).contains("空文档");
        assertThat(job.resultSourceVersionId()).isNull();
        org.mockito.Mockito.verify(materializer, org.mockito.Mockito.never())
            .materialize(any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void unsupportedFormatFailsHonestly() {
        when(sourceDocumentRepository.findByTenantIdAndId("t-1", 5L))
            .thenReturn(Optional.of(stubDoc()));

        DocParseJob job = service.submit(req("%PDF-1.7", DocumentFormat.PDF));

        assertThat(job.status()).isEqualTo(ParseJobStatus.FAILED);
        assertThat(job.errorMessage()).contains("暂不支持");
    }

    @Test
    void unknownSourceDocumentRejected() {
        when(sourceDocumentRepository.findByTenantIdAndId("t-1", 5L)).thenReturn(Optional.empty());
        org.assertj.core.api.Assertions.assertThatThrownBy(
                () -> service.submit(req("# x\ny\n", DocumentFormat.STRUCTURED_TEXT)))
            .isInstanceOf(ApiException.class);
    }

    private SourceDocument stubDoc() {
        return new SourceDocument(5L, "t-1", "SRC-1", "GUIDELINE", "A", "高血压指南",
            null, null, "zh-CN", null, null, null, null);
    }
}
```
> 注：`SourceDocument` 构造参数以仓库实际字段为准（执行时 `Read` 该 record 校正 stub 参数顺序/个数）；`OrgScope.ofTenant`/`RequestContext.bindOrgScope` 以仓库实际 API 为准（执行时 grep 现有测试用法对齐，如 `DiscoveryOrchestrationServiceTest`）。

- [ ] **Step 2: 运行确认失败**

Run: `cd medkernel-backend && mvn -q test -Dtest=DocumentParseOrchestrationServiceTest`
Expected: FAIL。

- [ ] **Step 3: 实现编排服务**

```java
package com.medkernel.engine.knowledge.parsing;

import static java.nio.charset.StandardCharsets.UTF_8;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.medkernel.engine.knowledge.SourceDocument;
import com.medkernel.engine.knowledge.SourceDocumentRepository;
import com.medkernel.shared.api.error.ApiException;
import com.medkernel.shared.api.error.ErrorCode;
import com.medkernel.shared.audit.AuditAction;
import com.medkernel.shared.audit.AuditRecorder;
import com.medkernel.shared.context.OrgScope;
import com.medkernel.shared.context.RequestContext;
import com.medkernel.shared.hash.Sha256ContentHash;

/**
 * 文档解析编排服务（AIK-STD-02）。job 生命周期：建 PENDING → 按格式分派解析器 →
 * 成功物化进 source_version/fragment 记 SUCCEEDED；解析失败/不支持格式诚实记 FAILED（FR-5，绝不产伪结构）。
 * 强租户隔离 + 审计。同步执行（管线可后续异步化，骨架已具状态机）。
 */
@Service
public class DocumentParseOrchestrationService {

    private static final int DEFAULT_PAGE_SIZE = 20;
    private static final int MAX_PAGE_SIZE = 200;
    private static final String EMPTY_MSG = "原文内容不能为空";

    private final DocParseJobRepository jobRepository;
    private final SourceDocumentRepository sourceDocumentRepository;
    private final List<DocumentParser> parsers;
    private final ParsedDocumentMaterializer materializer;
    private final AuditRecorder auditRecorder;

    public DocumentParseOrchestrationService(DocParseJobRepository jobRepository,
                                             SourceDocumentRepository sourceDocumentRepository,
                                             List<DocumentParser> parsers,
                                             ParsedDocumentMaterializer materializer,
                                             AuditRecorder auditRecorder) {
        this.jobRepository = jobRepository;
        this.sourceDocumentRepository = sourceDocumentRepository;
        this.parsers = parsers;
        this.materializer = materializer;
        this.auditRecorder = auditRecorder;
    }

    @Transactional
    public DocParseJob submit(DocumentParseRequest request) {
        String tenantId = requireCurrentTenant();
        SourceDocument sourceDoc = sourceDocumentRepository
            .findByTenantIdAndId(tenantId, request.sourceDocumentId())
            .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "受控来源不存在或不属当前租户"));

        byte[] rawBytes = request.content().getBytes(UTF_8);
        String sourceHash = Sha256ContentHash.sha256Bytes(rawBytes, EMPTY_MSG);
        String jobCode = "dpj:" + UUID.randomUUID();
        String actor = RequestContext.currentUserId();
        Instant now = Instant.now();

        DocParseJob pending = jobRepository.save(new DocParseJob(
            null, tenantId, jobCode, sourceDoc.id(), request.fileName(), request.format(),
            sourceHash, ParseJobStatus.PENDING, null, null, null, null, now, actor, now, actor));

        DocumentParser parser = parsers.stream()
            .filter(p -> p.supports(request.format()))
            .findFirst()
            .orElse(null);
        if (parser == null) {
            return fail(pending, "暂不支持解析格式 " + request.format() + "，待对应适配器接入", actor);
        }

        ParsedDocument parsed;
        try {
            parsed = parser.parse(new ParseInput(sourceDoc.id(), request.versionNo(),
                request.fileName(), request.format(), rawBytes, actor));
        } catch (DocumentParseException e) {
            return fail(pending, e.getMessage(), actor);
        }

        MaterializationResult result = materializer.materialize(tenantId, sourceDoc.id(),
            request.versionNo(), "doc-parse:" + jobCode, sourceHash, parsed, actor);

        DocParseJob done = jobRepository.save(new DocParseJob(
            pending.id(), tenantId, jobCode, sourceDoc.id(), request.fileName(), request.format(),
            sourceHash, ParseJobStatus.SUCCEEDED, result.sourceVersionId(),
            result.sectionCount(), result.fragmentCount(), null, pending.createdAt(), actor, Instant.now(), actor));
        auditRecorder.record(AuditAction.EXECUTE, "mk_doc_parse_job", jobCode,
            "文档解析成功：章节 " + result.sectionCount() + " 片段 " + result.fragmentCount());
        return done;
    }

    public DocParseJob getJob(String jobCode) {
        String tenantId = requireCurrentTenant();
        return jobRepository.findByTenantIdAndJobCode(tenantId, jobCode)
            .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "解析 job 不存在"));
    }

    public List<DocParseJob> listJobs(int page, int size) {
        String tenantId = requireCurrentTenant();
        int safeSize = size <= 0 ? DEFAULT_PAGE_SIZE : Math.min(size, MAX_PAGE_SIZE);
        int safePage = Math.max(page, 0);
        return jobRepository.pageByTenantId(tenantId, safePage * safeSize, safeSize);
    }

    private DocParseJob fail(DocParseJob pending, String error, String actor) {
        DocParseJob failed = jobRepository.save(new DocParseJob(
            pending.id(), pending.tenantId(), pending.jobCode(), pending.sourceDocumentId(),
            pending.sourceFileName(), pending.documentFormat(), pending.sourceHash(),
            ParseJobStatus.FAILED, null, null, null, error,
            pending.createdAt(), actor, Instant.now(), actor));
        auditRecorder.record(AuditAction.EXECUTE, "mk_doc_parse_job", pending.jobCode(),
            "文档解析失败：" + error);
        return failed;
    }

    private String requireCurrentTenant() {
        OrgScope scope = RequestContext.currentOrgScope();
        if (scope == null || !scope.hasTenant()) {
            throw ApiException.tenantMissing();
        }
        return scope.tenantId();
    }
}
```
> 注：`RequestContext.currentUserId()` / `OrgScope` / `ApiException.tenantMissing()` / `ErrorCode.NOT_FOUND` 以仓库实际 API 为准（执行时对照 `DiscoveryOrchestrationService` 与 `KnowledgeProductionOrchestrationService` 校正方法名与导入）。

- [ ] **Step 4: 运行确认通过**

Run: `cd medkernel-backend && mvn -q test -Dtest=DocumentParseOrchestrationServiceTest`
Expected: PASS。

- [ ] **Step 5: 提交**

```bash
git add medkernel-backend/src/main/java/com/medkernel/engine/knowledge/parsing/DocumentParseOrchestrationService.java medkernel-backend/src/test/java/com/medkernel/engine/knowledge/parsing/DocumentParseOrchestrationServiceTest.java
git commit -m "feat(aikstd02/PR1): 解析编排服务 job 生命周期 + 诚实失败降级"
```

---

## Task 6: `DocumentParseController` + 请求 DTO + 安全测试 + 产品目录

**Files:**
- Create: `.../parsing/DocumentParseRequest.java`
- Create: `.../parsing/DocumentParseController.java`
- Test: `medkernel-backend/src/test/java/com/medkernel/engine/knowledge/parsing/DocumentParseControllerTest.java`
- Modify: 产品功能目录（重生成）

- [ ] **Step 1: 写请求 DTO**

```java
package com.medkernel.engine.knowledge.parsing;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * 文档解析请求（AIK-STD-02）。PR1 经 {@code content}（结构化文本）提交；
 * PR2/PR3 二进制格式将扩展 base64 字段。{@code format} 决定分派的解析适配器。
 */
public record DocumentParseRequest(
    @NotNull Long sourceDocumentId,
    @NotBlank String versionNo,
    @NotBlank String fileName,
    @NotNull DocumentFormat format,
    @NotBlank String content
) {
}
```

- [ ] **Step 2: 写控制器安全失败测试**

参照 `medkernel-backend/src/test/java/com/medkernel/engine/knowledge/discovery/DiscoveryControllerTest.java`（执行时 `Read` 对齐 `@WebMvcTest`/MockMvc/权限断言写法）。覆盖：
- `POST /api/v1/engine/knowledge/documents:parse` 需 `knowledge.write`（无权限 403）。
- `GET /api/v1/engine/knowledge/documents/parse-jobs/{jobCode}` 与 `.../parse-jobs` 需 `knowledge.read`。
- 请求体缺 `sourceDocumentId`/`content` → 400（`@Valid`）。
- 有权限时正常返回 `ApiResult`（service mock）。

```java
package com.medkernel.engine.knowledge.parsing;

// import 与 mock 安装方式对齐 DiscoveryControllerTest（@WebMvcTest(DocumentParseController.class) + @MockBean DocumentParseOrchestrationService）
// 测试方法：
//  - parseRequiresKnowledgeWrite_forbiddenWithoutPermission()
//  - getJobRequiresKnowledgeRead()
//  - listJobsRequiresKnowledgeRead()
//  - parseRejectsBlankContentWith400()
//  - parseSucceedsWithPermissionAndValidBody()
// 断言遵循既有 ApiResult JSON 结构（code/data）。
```
> 执行时按 DiscoveryControllerTest 的精确骨架填充上述 5 个方法的完整代码（含 `@WithMockUser`/权限注入方式、MockMvc 调用、jsonPath 断言）。

- [ ] **Step 3: 运行确认失败**

Run: `cd medkernel-backend && mvn -q test -Dtest=DocumentParseControllerTest`
Expected: FAIL（控制器未实现）。

- [ ] **Step 4: 实现控制器**

```java
package com.medkernel.engine.knowledge.parsing;

import java.util.List;

import jakarta.validation.Valid;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.medkernel.shared.api.ApiResult;
import com.medkernel.shared.datascope.DataScope;

/**
 * 文档解析 API（AIK-STD-02）。解析 = 把受控来源文档解析为带锚点的来源片段（走 {@code knowledge.write}，
 * 等同新增受控来源内容）；解析 job 台账供查询（走 {@code knowledge.read}）。
 * 类级 {@link DataScope}：所有方法需租户上下文。
 */
@RestController
@RequestMapping("/api/v1/engine/knowledge")
@DataScope(requireTenant = true)
public class DocumentParseController {

    private final DocumentParseOrchestrationService service;

    public DocumentParseController(DocumentParseOrchestrationService service) {
        this.service = service;
    }

    @PostMapping("/documents:parse")
    @PreAuthorize("@perm.has('knowledge.write')")
    public ApiResult<DocParseJob> parse(@Valid @RequestBody DocumentParseRequest request) {
        return ApiResult.ok(service.submit(request));
    }

    @GetMapping("/documents/parse-jobs/{jobCode}")
    @PreAuthorize("@perm.has('knowledge.read')")
    public ApiResult<DocParseJob> getJob(@PathVariable String jobCode) {
        return ApiResult.ok(service.getJob(jobCode));
    }

    @GetMapping("/documents/parse-jobs")
    @PreAuthorize("@perm.has('knowledge.read')")
    public ApiResult<List<DocParseJob>> listJobs(
            @RequestParam(required = false, defaultValue = "0") int page,
            @RequestParam(required = false, defaultValue = "20") int size) {
        return ApiResult.ok(service.listJobs(page, size));
    }
}
```

- [ ] **Step 5: 运行确认通过**

Run: `cd medkernel-backend && mvn -q test -Dtest=DocumentParseControllerTest`
Expected: PASS。

- [ ] **Step 6: 重生成产品功能目录 + 前端校验**

Run: `node scripts/audit/export-product-capabilities.mjs`
然后：`cd frontend && npx vitest run src/shared/config/productCatalog.test.ts`
Expected: PASS 5/5（新增 `DocumentParseController` 端点纳入，无漂移）。
> 若 `export-product-capabilities.mjs` 的控制器归类正则未覆盖 `DocumentParse`，按 LLM-05 教训在脚本正则补一族（与 knowledge 控制器同类），再重生成。

- [ ] **Step 7: 提交**

```bash
git add medkernel-backend/src/main/java/com/medkernel/engine/knowledge/parsing medkernel-backend/src/test/java/com/medkernel/engine/knowledge/parsing scripts/audit/export-product-capabilities.mjs frontend/src/shared/config
git commit -m "feat(aikstd02/PR1): 文档解析控制器 + 请求 DTO + 产品目录纳入"
```

---

## Task 7: 端到端集成测试 + 域归属 + 全量验证 + 门禁

**Files:**
- Test: `medkernel-backend/src/test/java/com/medkernel/engine/knowledge/parsing/DocumentParseIntegrationTest.java`
- Modify: `DomainOwnershipCatalog`（engine-knowledge 加 `mk_doc_parse_job`）
- Modify: 契约声明（`mk_doc_parse_job` 审计点 + 读写 `source_*`），对照 `DiscoveryController`/`discovery` 契约写法

- [ ] **Step 1: 写真实 H2 端到端集成测试**

`@SpringBootTest` + 真实 H2，注入 `DocumentParseOrchestrationService`，绑定租户上下文，提交结构化文本 → 断言 job SUCCEEDED + 查 `SourceFragmentRepository.findByTenantIdAndSourceVersionIdOrderByAnchorPathAsc` 得到锚点片段（`§1/¶1` 等）+ `text_excerpt`/`content_hash` 真实。参照 `medkernel-backend/src/test/java/.../CandidateMaterializationIntegrationTest.java` 的容器/租户绑定骨架。
```java
// 关键断言：
//  - job.status() == SUCCEEDED, job.resultSourceVersionId() != null
//  - fragments 非空，anchorPath 形如 "§1/¶1"，contentHash 长度 64
//  - 再次提交同内容 → 复用 source_version，fragmentCount == 0（幂等）
```

- [ ] **Step 2: 运行确认通过**

Run: `cd medkernel-backend && mvn -q test -Dtest=DocumentParseIntegrationTest`
Expected: PASS。

- [ ] **Step 3: 域归属 + 契约登记**

- 在 `DomainOwnershipCatalog`（grep 定位文件）engine-knowledge 域表清单加 `mk_doc_parse_job`。
- 契约：在 knowledge 相关契约声明 `mk_doc_parse_job` 审计点（EXECUTE）+ 读 `source_document`/写 `source_version`/`source_fragment` 访问声明，对照 `discovery` 契约条目写法。
Run: `cd medkernel-backend && mvn -q test -Dtest=ApiContractGovernanceTest,DomainOwnershipContractTest`
Expected: PASS（具体测试类名以仓库实际为准，grep `DomainOwnership`/`ApiContract`）。

- [ ] **Step 4: 全量后端测试**

Run: `cd medkernel-backend && mvn -q test`
Expected: PASS（基线 2534 + 本 PR 新增约 18：解析器 6 + 物化 2 + sha256 2 + 编排 4 + 控制器 5 + 集成 1 + 迁移契约增量）。

- [ ] **Step 5: 四门禁（changed）+ diff check**

Run:
```bash
node scripts/authenticity-guard.mjs --mode=changed --base=origin/main
node scripts/config-boundary-guard.mjs --mode=changed --base=origin/main
node scripts/migration-guard.mjs --mode=changed --base=origin/main
node scripts/comment-language-check.mjs --mode=changed --base=origin/main
git diff --check
```
Expected: 全过（真实性门禁注意 Javadoc 禁「占位/模拟/仿真/演示/placeholder」；本卡注释已规避，复核 `error_message` 相关注释用「诚实」非「占位」）。
> 门禁脚本确切路径/参数以仓库 `scripts/` 实际为准（grep 既有 CI `guard-rules` 步骤对齐）。

- [ ] **Step 6: 最终提交 + 更新卡片实现进度 + 推送开 PR**

更新 `docs/cards/wave2/AIK-STD-02.md`：勾 FR-1（文本章节）/FR-3/FR-4/FR-5 + B0「✅（PR1）」，加「实现进度（PR1）」段（PR2 PDF / PR3 Word+表格 仍 pending，**FR-1 PDF/Word 与 FR-2 不虚勾**）。更新 `docs/_HANDOFF.md` AIK-STD-02 段状态为「PR1 已实现待合」+ 下一步。
```bash
git add docs/cards/wave2/AIK-STD-02.md docs/_HANDOFF.md medkernel-backend
git commit -m "feat(aikstd02/PR1): 端到端集成 + 域归属/契约登记 + 卡片实现进度"
git push -u origin claude/wave2-p2c-aikstd02-doc-parse-pipeline
```
然后 `gh pr create`（合并 main 逐 PR 授权，**不自动合并**）。

---

## 自审清单（Spec 覆盖）

- FR-1 章节树：Task 3（文本）✅；PDF/Word 留 PR2/PR3（本 PR 不虚勾）。
- FR-2 表格理解：留 PR3（本 PR 不涉及）。
- FR-3 引用锚点：Task 4 物化 `§章节/¶段` 锚点入 source_fragment ✅。
- FR-4 版本存证：Task 4 `source_version.content_hash`(原文 sha256Bytes) + file_uri ✅。
- FR-5 解析失败诚实：Task 3（空文档抛异常）+ Task 5（FAILED + error，不物化）✅。
- B0：Task 3 纯规则确定性解析 ✅。
- AC-2（hash 存证可验 + 失败诚实）：Task 4 + Task 5 ✅。
- T-GATE：Task 7 Step 5 真实性门禁 ✅。
- 不建 doc_anchor / 复用 source_*：Task 4 物化进既有表 ✅。
- 唯一新表 mk_doc_parse_job：Task 1 ✅。

**类型一致性核对**：`ParsedSection(numberPath, level, title, paragraphs)`、`ParsedDocument(sections)`、`MaterializationResult(sourceVersionId, sectionCount, fragmentCount)`、`DocParseJob` 字段、`ParseJobStatus`/`DocumentFormat` 枚举值在各 Task 间一致；`materialize(tenantId, sourceDocumentId, versionNo, fileUri, sourceHash, doc, actor)` 签名 Task 4 定义、Task 5 调用一致；`submit/getJob/listJobs` 控制器与服务一致。
