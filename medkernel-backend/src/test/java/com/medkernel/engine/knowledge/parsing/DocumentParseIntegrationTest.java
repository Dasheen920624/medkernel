package com.medkernel.engine.knowledge.parsing;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import com.medkernel.engine.knowledge.SourceAuthorityLevel;
import com.medkernel.engine.knowledge.SourceDocument;
import com.medkernel.engine.knowledge.SourceDocumentRepository;
import com.medkernel.engine.knowledge.SourceFragment;
import com.medkernel.engine.knowledge.SourceFragmentRepository;
import com.medkernel.engine.knowledge.SourceType;
import com.medkernel.shared.context.OrgScope;
import com.medkernel.shared.context.RequestContext;

/**
 * 文档解析管线端到端集成测试（AIK-STD-02 PR1，真实 H2）。
 *
 * <p>验证：提交结构化文本 → 编排服务确定性解析章节 → 物化进 {@code source_version} + {@code source_fragment}
 * 带真实层级锚点（§章节/¶段）与真实 SHA-256；job 记 SUCCEEDED；再次提交同内容幂等（复用版本、不重复片段）。
 */
@SpringBootTest
@ActiveProfiles("dev")
class DocumentParseIntegrationTest {

    private static final String TENANT = "tenant-parse-it";

    @Autowired
    private DocumentParseOrchestrationService service;
    @Autowired
    private SourceDocumentRepository sourceDocuments;
    @Autowired
    private SourceFragmentRepository sourceFragments;

    @AfterEach
    void tearDown() {
        RequestContext.clear();
    }

    private static final String CONTENT = """
        # 总则
        本指南适用于成人高血压患者。

        ## 适应证
        用于确诊的原发性高血压。
        """;

    @Test
    void parsesStructuredTextAndMaterializesAnchoredFragments() {
        RequestContext.restore(new RequestContext.Snapshot("trace-it", OrgScope.tenant(TENANT), "user-it"));
        Instant now = Instant.now();
        SourceDocument doc = sourceDocuments.save(new SourceDocument(null, TENANT, "SRC-PARSE-IT",
            SourceType.GUIDELINE, SourceAuthorityLevel.B_GUIDELINE, "国家卫健委发布", "高血压指南",
            "卫健委", "lic", "zh-CN", now, "u", now, "u"));

        DocParseJob job = service.submit(new DocumentParseRequest(
            doc.id(), "v1", "hypertension.md", DocumentFormat.STRUCTURED_TEXT, CONTENT));

        assertThat(job.status()).isEqualTo(ParseJobStatus.SUCCEEDED);
        assertThat(job.resultSourceVersionId()).isNotNull();
        assertThat(job.sourceHash()).hasSize(64);
        assertThat(job.parsedSectionCount()).isEqualTo(2);
        assertThat(job.parsedFragmentCount()).isEqualTo(2);

        List<SourceFragment> fragments = sourceFragments
            .findByTenantIdAndSourceVersionIdOrderByAnchorPathAsc(TENANT, job.resultSourceVersionId());
        assertThat(fragments).extracting(SourceFragment::anchorPath)
            .containsExactly("§1.1/¶1", "§1/¶1");
        assertThat(fragments).allSatisfy(f -> assertThat(f.contentHash()).hasSize(64));
        assertThat(fragments).anySatisfy(f ->
            assertThat(f.textExcerpt()).isEqualTo("本指南适用于成人高血压患者。"));
    }

    @Test
    void reSubmittingSameContentIsIdempotent() {
        RequestContext.restore(new RequestContext.Snapshot("trace-it2", OrgScope.tenant(TENANT), "user-it"));
        Instant now = Instant.now();
        SourceDocument doc = sourceDocuments.save(new SourceDocument(null, TENANT, "SRC-PARSE-IDEM",
            SourceType.GUIDELINE, SourceAuthorityLevel.B_GUIDELINE, "依据", "指南", "卫健委", "lic", "zh-CN",
            now, "u", now, "u"));
        DocumentParseRequest request = new DocumentParseRequest(
            doc.id(), "v1", "g.md", DocumentFormat.STRUCTURED_TEXT, CONTENT);

        DocParseJob first = service.submit(request);
        DocParseJob second = service.submit(request);

        assertThat(second.status()).isEqualTo(ParseJobStatus.SUCCEEDED);
        assertThat(second.resultSourceVersionId()).isEqualTo(first.resultSourceVersionId());
        assertThat(second.parsedFragmentCount()).isZero();
    }
}
