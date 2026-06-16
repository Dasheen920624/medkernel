package com.medkernel.engine.knowledge.parsing;

import java.time.Instant;
import java.util.List;

import org.springframework.stereotype.Service;

import com.medkernel.engine.knowledge.SourceFragment;
import com.medkernel.engine.knowledge.SourceFragmentRepository;
import com.medkernel.engine.knowledge.SourceVersion;
import com.medkernel.engine.knowledge.SourceVersionRepository;
import com.medkernel.shared.hash.Sha256ContentHash;

/**
 * 解析产物物化器（AIK-STD-02 FR-3/4）。把 {@link ParsedDocument} 落入既有受控来源：
 * 注册/复用 {@code source_version}（原文 SHA-256 + file_uri = 存证）+ 逐段 upsert {@code source_fragment}
 * （anchor_path 编码 {@code [p<页>/]§<章节>/¶<段>} = 锚点，PDF 等版式来源附真实页号前缀）。
 * 强租户隔离 + 幂等（同 hash 复用版本、跳过重复片段不重复插入）。
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
            for (ParsedParagraph paragraph : section.paragraphs()) {
                paraIndex++;
                String pagePrefix = paragraph.page() == null ? "" : "p" + paragraph.page() + "/";
                String anchorPath = pagePrefix + "§" + section.numberPath() + "/¶" + paraIndex;
                fragmentCount += upsertFragment(tenantId, version.id(), anchorPath,
                    section.title(), paragraph.text());
            }
        }
        for (ParsedTable table : doc.tables()) {
            String pagePrefix = table.page() == null ? "" : "p" + table.page() + "/";
            String tableBase = pagePrefix + "§" + table.sectionNumberPath() + "/tbl" + table.indexInSection();
            int rowNo = 0;
            for (List<String> row : table.rows()) {
                rowNo++;
                int colNo = 0;
                for (String cell : row) {
                    colNo++;
                    String text = cell == null ? "" : cell.strip();
                    if (text.isEmpty()) {
                        continue;
                    }
                    String anchorPath = tableBase + "/r" + rowNo + "c" + colNo;
                    fragmentCount += upsertFragment(tenantId, version.id(), anchorPath,
                        table.sectionTitle(), text);
                }
            }
        }
        return new MaterializationResult(version.id(), doc.sections().size(), fragmentCount);
    }

    /** upsert 单条来源片段：真实 SHA-256 + 幂等去重（同版本同 hash 已存在则跳过）；返回新增数（0 或 1）。 */
    private int upsertFragment(String tenantId, Long sourceVersionId, String anchorPath,
                               String anchorLabel, String text) {
        String contentHash = Sha256ContentHash.sha256(text, EMPTY_MSG);
        if (fragmentRepository.findBySourceVersionIdAndContentHash(sourceVersionId, contentHash).isPresent()) {
            return 0;
        }
        fragmentRepository.save(new SourceFragment(
            null, tenantId, sourceVersionId, anchorPath, anchorLabel, text, contentHash, Instant.now()));
        return 1;
    }
}
