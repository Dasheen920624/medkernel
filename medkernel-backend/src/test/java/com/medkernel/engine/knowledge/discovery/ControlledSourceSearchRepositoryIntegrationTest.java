package com.medkernel.engine.knowledge.discovery;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;

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
import com.medkernel.engine.knowledge.SourceVersion;
import com.medkernel.engine.knowledge.SourceVersionRepository;

/**
 * 受控来源检索仓储集成测试（LLM-06，FR-1 受控源 / FR-3 来源核验）。
 *
 * <p>对真实 H2 播种 {@code source_document → source_version → source_fragment}，验证：仅检索受控注册源、
 * 强租户隔离、按关键词匹配片段正文、按权威分级（A→E）排序、投影携带引用锚点与源版本快照字段。
 */
@SpringBootTest
@ActiveProfiles("dev")
class ControlledSourceSearchRepositoryIntegrationTest {

    @Autowired
    private ControlledSourceSearchRepository searchRepository;
    @Autowired
    private SourceDocumentRepository documentRepository;
    @Autowired
    private SourceVersionRepository versionRepository;
    @Autowired
    private SourceFragmentRepository fragmentRepository;

    private static final String TENANT = "tenant-discovery-it";
    private static final String OTHER_TENANT = "tenant-discovery-other";

    private Long seedFragment(String tenant, String sourceCode, SourceAuthorityLevel authority,
            String anchorPath, String excerpt) {
        Instant now = Instant.now();
        SourceDocument doc = documentRepository.save(new SourceDocument(null, tenant, sourceCode,
            SourceType.GUIDELINE, authority, "依据-" + sourceCode, "标题-" + sourceCode, "发布方", "CC",
            "zh", now, "system", now, "system"));
        SourceVersion version = versionRepository.save(new SourceVersion(null, tenant, doc.id(),
            "v1", now, "vh-" + sourceCode, "uri", "zh", now, "system"));
        SourceFragment fragment = fragmentRepository.save(new SourceFragment(null, tenant, version.id(),
            anchorPath, "锚点-" + anchorPath, excerpt, "fh-" + anchorPath, now));
        return fragment.id();
    }

    @Test
    void searchReturnsControlledHitsOrderedByAuthorityWithProvenance() {
        seedFragment(TENANT, "SRC-C", SourceAuthorityLevel.C_CONSENSUS_LITERATURE,
            "section-c", "阿司匹林抗血小板共识摘录");
        seedFragment(TENANT, "SRC-A", SourceAuthorityLevel.A_REGULATION,
            "section-a", "阿司匹林用药说明书条款");
        // 不含关键词的受控片段须排除
        seedFragment(TENANT, "SRC-N", SourceAuthorityLevel.B_GUIDELINE,
            "section-n", "二甲双胍剂量指南条款");
        // 跨租户片段须排除
        seedFragment(OTHER_TENANT, "SRC-X", SourceAuthorityLevel.A_REGULATION,
            "section-x", "阿司匹林越租户条款");

        List<DiscoveryFragmentHit> hits =
            searchRepository.searchControlledFragments(TENANT, "%阿司匹林%", 10);

        assertThat(hits).hasSize(2);
        // 高权威（A）优先于低权威（C）
        assertThat(hits.get(0).sourceCode()).isEqualTo("SRC-A");
        assertThat(hits.get(0).authorityLevel()).isEqualTo(SourceAuthorityLevel.A_REGULATION);
        assertThat(hits.get(0).anchorPath()).isEqualTo("section-a");
        assertThat(hits.get(0).versionNo()).isEqualTo("v1");
        assertThat(hits.get(0).versionContentHash()).isEqualTo("vh-SRC-A");
        assertThat(hits.get(0).textExcerpt()).contains("阿司匹林");
        assertThat(hits.get(0).sourceType()).isEqualTo(SourceType.GUIDELINE);
        assertThat(hits.get(1).sourceCode()).isEqualTo("SRC-C");
    }

    @Test
    void searchHonoursLimit() {
        seedFragment(TENANT, "SRC-L1", SourceAuthorityLevel.A_REGULATION, "s1", "布洛芬条款一");
        seedFragment(TENANT, "SRC-L2", SourceAuthorityLevel.A_REGULATION, "s2", "布洛芬条款二");

        List<DiscoveryFragmentHit> hits =
            searchRepository.searchControlledFragments(TENANT, "%布洛芬%", 1);

        assertThat(hits).hasSize(1);
    }
}
