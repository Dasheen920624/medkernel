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
                String contentHash = Sha256ContentHash.sha256(paragraph.text(), EMPTY_MSG);
                if (fragmentRepository.findBySourceVersionIdAndContentHash(version.id(), contentHash).isPresent()) {
                    continue;
                }
                fragmentRepository.save(new SourceFragment(
                    null, tenantId, version.id(), anchorPath, section.title(), paragraph.text(), contentHash, Instant.now()));
                fragmentCount++;
            }
        }
        return new MaterializationResult(version.id(), doc.sections().size(), fragmentCount);
    }
}
