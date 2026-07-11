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
    void acceptsAllSupportedProducersForUnifiedFormalProduction() {
        for (KnowledgeProducer producer : KnowledgeProducer.values()) {
            assertThatCode(() -> policy.requireSupportedFormalJob(request(producer)))
                .doesNotThrowAnyException();
        }
    }

    @Test
    void acceptsAllRuntimeAssetTypesForUnifiedFormalProduction() {
        for (VersionedAssetType type : VersionedAssetType.values()) {
            assertThatCode(() -> policy.requireSupportedFormalJob(request(KnowledgeProducer.MANUAL, type)))
                .doesNotThrowAnyException();
        }
    }

    @Test
    void rejectsMissingRequestFromFormalProduction() {
        assertThatThrownBy(() -> policy.requireSupportedFormalJob(null))
            .isInstanceOf(ApiException.class)
            .extracting("errorCode")
            .isEqualTo(ErrorCode.BAD_REQUEST);
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
