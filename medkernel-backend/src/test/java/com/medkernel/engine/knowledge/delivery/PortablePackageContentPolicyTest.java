package com.medkernel.engine.knowledge.delivery;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.medkernel.engine.versioning.AssetVersionOverridePolicy;
import com.medkernel.engine.versioning.AssetVersionSafetyPolicy;
import com.medkernel.engine.versioning.VersionedAssetType;
import com.medkernel.shared.api.error.ApiException;
import com.medkernel.shared.api.error.ErrorCode;
import org.junit.jupiter.api.Test;

/** 完整医疗资源包内容白名单不得把临床实例伪装成平台配置资产。 */
class PortablePackageContentPolicyTest {

    private final ObjectMapper json = new ObjectMapper();
    private final PortablePackageContentPolicy policy = new PortablePackageContentPolicy();

    @Test
    void rejectsNestedPatientResourceEvenWithoutDirectIdentifierKey() throws Exception {
        PortableAssetDocument document = new PortableAssetDocument(
            "1.0",
            VersionedAssetType.KNOWLEDGE,
            "ASSET.KNOWLEDGE",
            "version-knowledge-1",
            "1.0.0",
            "/PLATFORM",
            "ALL",
            AssetVersionSafetyPolicy.NORMAL,
            AssetVersionOverridePolicy.FREE,
            "a".repeat(64),
            "sm3:" + "b".repeat(64),
            json.readTree("{\"payload\":{\"resourceType\":\"Patient\","
                + "\"name\":[{\"text\":\"真实姓名\"}]}}"),
            List.of(),
            List.of(),
            List.of(),
            null,
            List.of());

        assertThatThrownBy(() -> policy.validateDocument(document))
            .isInstanceOfSatisfying(ApiException.class, exception -> {
                assertThat(exception.errorCode()).isEqualTo(ErrorCode.VALIDATION_FAILED);
                assertThat(exception).hasMessageContaining("患者资源");
            });
    }
}
