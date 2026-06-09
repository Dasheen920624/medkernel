package com.medkernel.engine.versioning;

import org.junit.jupiter.api.Test;

import com.medkernel.shared.api.error.ApiException;
import com.medkernel.shared.api.error.ErrorCode;
import com.medkernel.shared.context.PlatformTenant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AssetIdentityAllocatorTest {

    private final AssetIdentityAllocator allocator = new AssetIdentityAllocator();

    @Test
    void allocatesPlatformIdentityFromCanonicalDomainAndSlug() {
        assertThat(allocator.allocate(PlatformTenant.ID, "DRUG", "rosuvastatin-guide"))
            .isEqualTo("plat:drug:rosuvastatin-guide");
    }

    @Test
    void allocatesTenantIdentityWithoutSharingPlatformNamespace() {
        assertThat(allocator.allocate("hospital-a", "DIAGNOSIS", "chronic-kidney-disease"))
            .isEqualTo("t:hospital-a:diagnosis:chronic-kidney-disease");
    }

    @Test
    void rejectsFullLegacyCodeInsteadOfMaintainingCompatibilityAlias() {
        assertThatThrownBy(() -> allocator.allocate(PlatformTenant.ID, "DRUG", "DRUG.ROSUVA"))
            .isInstanceOf(ApiException.class)
            .extracting("errorCode")
            .isEqualTo(ErrorCode.VALIDATION_FAILED);
    }

    @Test
    void rejectsTenantIdentifierThatCouldBreakNamespaceStructure() {
        assertThatThrownBy(() -> allocator.allocate("hospital:a", "DRUG", "rosuvastatin"))
            .isInstanceOf(ApiException.class)
            .extracting("errorCode")
            .isEqualTo(ErrorCode.VALIDATION_FAILED);
    }

    @Test
    void rejectsIdentityThatWouldExceedPersistenceBoundary() {
        assertThatThrownBy(() -> allocator.allocate(
                "t-" + "a".repeat(62),
                "PATHWAY_KNOWLEDGE",
                "b".repeat(44)))
            .isInstanceOf(ApiException.class)
            .extracting("errorCode")
            .isEqualTo(ErrorCode.VALIDATION_FAILED);
    }
}
