package com.medkernel.engine.versioning;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import com.medkernel.shared.api.error.ApiException;
import com.medkernel.shared.api.error.ErrorCode;

class AssetTechnicalValidationServiceTest {

    private static final Instant NOW = Instant.parse("2026-06-23T12:00:00Z");
    private final AssetIdentityRepository identities = mock(AssetIdentityRepository.class);
    private final AssetVersionContentRepository contents =
        mock(AssetVersionContentRepository.class);
    private final AssetValidationRecordRepository records =
        mock(AssetValidationRecordRepository.class);
    private AssetTechnicalValidationService service;

    @BeforeEach
    void setUp() {
        service = new AssetTechnicalValidationService(
            identities,
            contents,
            records,
            Clock.fixed(NOW, ZoneOffset.UTC)
        );
        when(records.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void rerunsTechnicalValidationAndPersistsEvidenceBoundToExactContentHash() {
        AssetVersion version = version(VersionedAssetType.FORMULA, "FORMULA.EGFR");
        when(identities.findByTenantIdAndAssetTypeAndAssetIdentity(
            "tenant-A", VersionedAssetType.FORMULA, "FORMULA.EGFR"))
            .thenReturn(Optional.of(identity(
                VersionedAssetType.FORMULA,
                "FORMULA.EGFR",
                AssetIdentityStatus.ACTIVE)));
        when(contents.findByTenantIdAndVersionId("tenant-A", "formula-v1"))
            .thenReturn(Optional.of(content(version.contentHash())));

        AssetValidationRecord result = service.validateForPublish(
            version, "operator-A", "trace-A");

        assertThat(result.passed()).isTrue();
        assertThat(result.versionId()).isEqualTo("formula-v1");
        assertThat(result.contentHash()).isEqualTo(version.contentHash());
        assertThat(result.validatedAt()).isEqualTo(NOW);
        assertThat(result.summary()).contains("稳定身份", "正文哈希");
    }

    @Test
    void rejectsBodyHashMismatchWithoutPersistingFalseSuccessEvidence() {
        AssetVersion version = version(VersionedAssetType.FORMULA, "FORMULA.EGFR");
        when(identities.findByTenantIdAndAssetTypeAndAssetIdentity(
            "tenant-A", VersionedAssetType.FORMULA, "FORMULA.EGFR"))
            .thenReturn(Optional.of(identity(
                VersionedAssetType.FORMULA,
                "FORMULA.EGFR",
                AssetIdentityStatus.ACTIVE)));
        when(contents.findByTenantIdAndVersionId("tenant-A", "formula-v1"))
            .thenReturn(Optional.of(content("6".repeat(64))));

        assertThatThrownBy(() -> service.validateForPublish(
            version, "operator-A", "trace-A"))
            .isInstanceOf(ApiException.class)
            .hasMessageContaining("哈希")
            .extracting("errorCode")
            .isEqualTo(ErrorCode.CONFLICT);

        verify(records, never()).save(any());
    }

    @Test
    void rejectsAReleaseForRetiredStableIdentity() {
        AssetVersion version = version(VersionedAssetType.RULE, "RULE.CKD");
        when(identities.findByTenantIdAndAssetTypeAndAssetIdentity(
            "tenant-A", VersionedAssetType.RULE, "RULE.CKD"))
            .thenReturn(Optional.of(identity(
                VersionedAssetType.RULE,
                "RULE.CKD",
                AssetIdentityStatus.RETIRED)));

        assertThatThrownBy(() -> service.validateForPublish(
            version, "operator-A", "trace-A"))
            .isInstanceOf(ApiException.class)
            .hasMessageContaining("退役")
            .extracting("errorCode")
            .isEqualTo(ErrorCode.CONFLICT);
    }

    private AssetIdentity identity(
            VersionedAssetType type,
            String code,
            AssetIdentityStatus status) {
        return new AssetIdentity(
            1L, "tenant-A", type, code, status, 1L,
            NOW.minusSeconds(3600), "operator-old",
            NOW.minusSeconds(60), "operator-old", "trace-old");
    }

    private AssetVersion version(VersionedAssetType type, String code) {
        String contentHash = type.usesUnifiedContentStore()
            ? VersionContentHash.resolve("{\"expression\":\"x\"}", null)
            : "5".repeat(64);
        return new AssetVersion(
            1L, "formula-v1", "tenant-A", type, code, "V1",
            "/tenant-A/hospital-A", "ALL", contentHash,
            AssetVersionSafetyPolicy.NORMAL, AssetVersionOverridePolicy.FREE,
            AssetVersionStatus.DRAFT, "version:formula-v1", "source-A",
            null, null,
            NOW.minusSeconds(60), "operator-A",
            NOW.minusSeconds(60), "operator-A", "trace-A");
    }

    private AssetVersionContent content(String hash) {
        return new AssetVersionContent(
            1L, "formula-v1", "tenant-A", "{\"expression\":\"x\"}", hash,
            NOW.minusSeconds(60), "operator-A",
            NOW.minusSeconds(60), "operator-A", "trace-A");
    }
}
