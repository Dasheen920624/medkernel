package com.medkernel.engine.knowledge.production;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

import com.medkernel.engine.versioning.VersionedAssetType;
import com.medkernel.shared.api.error.ApiException;
import com.medkernel.shared.api.error.ErrorCode;

class FormalKnowledgeProductionPolicyTest {

    private final FormalKnowledgeProductionPolicy policy = new FormalKnowledgeProductionPolicy();

    @Test
    void acceptsApiModelForFormalProduction() {
        assertThatCode(() -> policy.requireApiModel(request(KnowledgeProducer.API_MODEL)))
            .doesNotThrowAnyException();
    }

    @Test
    void acceptsKnowledgeRuleAndPathwayForFormalModelProduction() {
        for (VersionedAssetType type : java.util.List.of(
            VersionedAssetType.KNOWLEDGE,
            VersionedAssetType.RULE,
            VersionedAssetType.PATHWAY)) {
            assertThatCode(() -> policy.requireApiModel(request(KnowledgeProducer.API_MODEL, type)))
                .doesNotThrowAnyException();
        }
    }

    @Test
    void rejectsOtherAssetTypesFromFormalModelProduction() {
        ProductionJobRequest unsupported = request(KnowledgeProducer.API_MODEL, VersionedAssetType.FORMULA);

        assertThatThrownBy(() -> policy.requireApiModel(unsupported))
            .isInstanceOf(ApiException.class)
            .hasMessageContaining("知识、规则或路径")
            .extracting("errorCode")
            .isEqualTo(ErrorCode.BAD_REQUEST);
    }

    @Test
    void rejectsEveryNonApiModelProducerForFormalProduction() {
        for (KnowledgeProducer producer : KnowledgeProducer.values()) {
            if (producer == KnowledgeProducer.API_MODEL) {
                continue;
            }
            assertThatThrownBy(() -> policy.requireApiModel(request(producer)))
                .isInstanceOf(ApiException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.BAD_REQUEST);
        }
    }

    @Test
    void rejectsLegacyB0GenerationFromFormalApi() {
        assertThatThrownBy(policy::rejectB0Generation)
            .isInstanceOf(ApiException.class)
            .hasMessage("正式知识生产不再接受 B0 候选生成，请使用统一 Provider API 模型生产任务");
    }

    private ProductionJobRequest request(KnowledgeProducer producer) {
        return request(producer, VersionedAssetType.KNOWLEDGE);
    }

    private ProductionJobRequest request(KnowledgeProducer producer, VersionedAssetType assetType) {
        return new ProductionJobRequest(
            "source-version:1",
            assetType,
            producer,
            TargetPipeline.TENANT_OVERLAY,
            KnowledgeDomain.GENERAL,
            "model-strategy"
        );
    }
}
