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
            .hasMessage("正式知识生产不再接受 B0 候选生成，请使用 API_MODEL 模型生产任务");
    }

    private ProductionJobRequest request(KnowledgeProducer producer) {
        return new ProductionJobRequest(
            "source-version:1",
            VersionedAssetType.KNOWLEDGE,
            producer,
            TargetPipeline.TENANT_OVERLAY,
            KnowledgeDomain.GENERAL,
            "model-strategy"
        );
    }
}
