package com.medkernel.engine.factory;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.medkernel.engine.knowledge.GradeEvidenceQuality;
import com.medkernel.engine.knowledge.GradeRecommendationStrength;
import com.medkernel.engine.knowledge.KnowledgeRiskLevel;
import com.medkernel.engine.knowledge.SourceAuthorityLevel;
import com.medkernel.engine.versioning.AssetVersionStatus;
import com.medkernel.engine.versioning.VersionedAssetType;
import com.medkernel.shared.api.error.ApiException;
import com.medkernel.shared.api.error.ErrorCode;
import com.medkernel.shared.hash.Sha256ContentHash;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 统一资产信封 schema 校验闸测试（AIK-STD-01，FR-1/2/3/4）。
 *
 * <p>覆盖合法通过、逐字段拒收（无源/缺元数据/越级状态/伪造 hash）、多违规一次抛出、类型无关扩展性。
 */
class KnowledgeAssetSchemaValidatorTest {

    private static final String PAYLOAD = "{\"dose\":\"500mg bid\"}";

    private final KnowledgeAssetSchemaValidator validator = new KnowledgeAssetSchemaValidator();

    @Test
    void acceptsWellFormedCandidateEnvelope() {
        assertThatCode(() -> validator.validate(valid().build())).doesNotThrowAnyException();
    }

    @Test
    void rejectsBlankAssetIdentity() {
        expectReject(valid().assetIdentity("  ").build(), "资产身份");
    }

    @Test
    void rejectsBlankSubject() {
        expectReject(valid().subject("").build(), "主题");
    }

    @Test
    void rejectsEmptySourcesAsUnsourced() {
        expectReject(valid().sources(List.of()).build(), "来源");
    }

    @Test
    void rejectsSourceWithBlankRef() {
        expectReject(valid().sources(List.of(new AssetSourceRef("  ", SourceAuthorityLevel.B_GUIDELINE))).build(), "来源");
    }

    @Test
    void rejectsSourceWithMissingAuthorityLevel() {
        expectReject(valid().sources(List.of(new AssetSourceRef("src-1", null))).build(), "来源");
    }

    @Test
    void rejectsMissingTrustLevel() {
        expectReject(valid().trustLevel(null).build(), "可信分级");
    }

    @Test
    void rejectsBlankOrgScope() {
        expectReject(valid().orgScope("").build(), "组织");
    }

    @Test
    void rejectsBlankPayload() {
        expectReject(valid().payload("").contentHash("not-checked").build(), "内容");
    }

    @Test
    void rejectsMissingRiskLevel() {
        expectReject(valid().riskLevel(null).build(), "风险");
    }

    @Test
    void rejectsNonCandidateLifecycleStatus() {
        // 铁律 #5：AI/生产器只产候选，禁直接产 PUBLISHED/ACTIVE。
        expectReject(valid().lifecycleStatus(AssetVersionStatus.PUBLISHED).build(), "状态");
    }

    @Test
    void rejectsMalformedContentHash() {
        expectReject(valid().contentHash("ZZZ").build(), "指纹");
    }

    @Test
    void rejectsContentHashNotMatchingPayload() {
        String wrong = Sha256ContentHash.sha256("不一样的内容", "payload 不能为空");
        expectReject(valid().contentHash(wrong).build(), "指纹");
    }

    @Test
    void collectsMultipleViolationsInOneRejection() {
        KnowledgeAssetEnvelope bad = valid().assetIdentity("").subject("").sources(List.of()).build();
        assertThatThrownBy(() -> validator.validate(bad))
            .isInstanceOf(ApiException.class)
            .hasMessageContaining("资产身份")
            .hasMessageContaining("主题")
            .hasMessageContaining("来源");
    }

    @Test
    void validatesAcrossAssetTypesWithoutTypeSpecificCode() {
        for (VersionedAssetType type : List.of(
                VersionedAssetType.KNOWLEDGE, VersionedAssetType.RULE,
                VersionedAssetType.PATHWAY, VersionedAssetType.ACTION_CARD)) {
            assertThatCode(() -> validator.validate(valid().assetType(type).build()))
                .as("类型 %s 应类型无关地通过", type)
                .doesNotThrowAnyException();
        }
    }

    private void expectReject(KnowledgeAssetEnvelope envelope, String hint) {
        assertThatThrownBy(() -> validator.validate(envelope))
            .isInstanceOf(ApiException.class)
            .hasMessageContaining(hint)
            .extracting("errorCode").isEqualTo(ErrorCode.BAD_REQUEST);
    }

    private Builder valid() {
        return new Builder();
    }

    /** 默认构造合法候选信封，单测覆盖单字段。 */
    private static final class Builder {
        private VersionedAssetType assetType = VersionedAssetType.KNOWLEDGE;
        private String assetIdentity = "ka-001";
        private String subject = "二甲双胍用药指导";
        private String versionLabel = "v1";
        private List<AssetSourceRef> sources = List.of(new AssetSourceRef("src-doc-1#frag-2", SourceAuthorityLevel.B_GUIDELINE));
        private SourceAuthorityLevel trustLevel = SourceAuthorityLevel.B_GUIDELINE;
        private KnowledgeRiskLevel riskLevel = KnowledgeRiskLevel.MEDIUM;
        private String orgScope = "tenant:t-1";
        private String payload = PAYLOAD;
        private String contentHash = Sha256ContentHash.sha256(PAYLOAD, "payload 不能为空");
        private AssetVersionStatus lifecycleStatus = AssetVersionStatus.DRAFT;

        Builder assetType(VersionedAssetType v) { this.assetType = v; return this; }
        Builder assetIdentity(String v) { this.assetIdentity = v; return this; }
        Builder subject(String v) { this.subject = v; return this; }
        Builder sources(List<AssetSourceRef> v) { this.sources = v; return this; }
        Builder trustLevel(SourceAuthorityLevel v) { this.trustLevel = v; return this; }
        Builder riskLevel(KnowledgeRiskLevel v) { this.riskLevel = v; return this; }
        Builder orgScope(String v) { this.orgScope = v; return this; }
        Builder payload(String v) { this.payload = v; return this; }
        Builder contentHash(String v) { this.contentHash = v; return this; }
        Builder lifecycleStatus(AssetVersionStatus v) { this.lifecycleStatus = v; return this; }

        KnowledgeAssetEnvelope build() {
            return new KnowledgeAssetEnvelope(assetType, assetIdentity, subject, versionLabel, sources,
                trustLevel, GradeEvidenceQuality.HIGH, GradeRecommendationStrength.STRONG,
                riskLevel, orgScope, contentHash, payload, lifecycleStatus);
        }
    }
}
