package com.medkernel.engine.versioning;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.medkernel.shared.api.error.ApiException;
import com.medkernel.shared.api.error.ErrorCode;

class AssetVersionServiceTest {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-06-03T08:00:00Z"), ZoneOffset.UTC);

    private AssetVersionRepository repository;
    private AssetVersionService service;

    @BeforeEach
    void setUp() {
        repository = mock(AssetVersionRepository.class);
        service = new AssetVersionService(repository, CLOCK);
    }

    @Test
    void registerDraftComputesStableContentHashAndRejectsMismatch() {
        when(repository.save(any(AssetVersion.class))).thenAnswer(invocation -> invocation.getArgument(0));

        AssetVersion saved = service.registerDraft(new AssetVersionRegisterCommand(
            "tenant-A",
            VersionedAssetType.RULE,
            "RULE.VTE.RISK",
            "1.0.0",
            "/GROUP/g-1/HOSPITAL/h-1",
            "adult|inpatient",
            "when observation.d_dimer > 0.5 then alert",
            null,
            "rule/RULE.VTE.RISK",
            "reviewer-1",
            "trace-sys04"
        ));

        assertThat(saved.contentHash()).isEqualTo(sha256("when observation.d_dimer > 0.5 then alert"));
        assertThat(saved.versionId()).matches("av-[0-9A-HJKMNP-TV-Z]{26}");
        assertThat(saved.safetyPolicy()).isEqualTo(AssetVersionSafetyPolicy.NORMAL);
        assertThat(saved.overridePolicy()).isEqualTo(AssetVersionOverridePolicy.FREE);
        assertThat(saved.status()).isEqualTo(AssetVersionStatus.DRAFT);
        assertThat(saved.activeScopeKey()).startsWith("version:");
        assertThat(saved.createdAt()).isEqualTo(Instant.parse("2026-06-03T08:00:00Z"));

        assertThatThrownBy(() -> service.registerDraft(new AssetVersionRegisterCommand(
            "tenant-A",
            VersionedAssetType.RULE,
            "RULE.VTE.RISK",
            "1.0.1",
            "/GROUP/g-1/HOSPITAL/h-1",
            "adult|inpatient",
            "changed content",
            "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef",
            "rule/RULE.VTE.RISK",
            "reviewer-1",
            "trace-sys04"
        )))
            .isInstanceOf(ApiException.class)
            .extracting("errorCode")
            .isEqualTo(ErrorCode.VALIDATION_FAILED);
    }

    @Test
    void registerDraftCarriesExplicitOverridePolicy() {
        when(repository.save(any(AssetVersion.class))).thenAnswer(invocation -> invocation.getArgument(0));

        AssetVersion saved = service.registerDraft(new AssetVersionRegisterCommand(
            "tenant-A",
            VersionedAssetType.RULE,
            "RULE.VTE.RISK",
            "2.0.0",
            "/GROUP/g-1/HOSPITAL/h-1",
            "adult|inpatient",
            "when allergy.penicillin then block order",
            null,
            "rule/RULE.VTE.RISK",
            "reviewer-1",
            "trace-sys04",
            AssetVersionSafetyPolicy.SAFETY_REDLINE,
            AssetVersionOverridePolicy.LOCKED
        ));

        assertThat(saved.overridePolicy()).isEqualTo(AssetVersionOverridePolicy.LOCKED);
        assertThat(saved.safetyPolicy()).isEqualTo(AssetVersionSafetyPolicy.SAFETY_REDLINE);
    }

    @Test
    void publishedVersionCannotBeMutatedInPlace() {
        AssetVersion published = sample(AssetVersionStatus.PUBLISHED, "hash-a", "version:av-1");
        when(repository.findByVersionIdAndTenantId("av-1", "tenant-A")).thenReturn(Optional.of(published));

        assertThatThrownBy(() -> service.updateDraft(new AssetVersionDraftUpdateCommand(
            "tenant-A",
            "av-1",
            "RULE.VTE.RISK",
            "/GROUP/g-1/HOSPITAL/h-1",
            "adult|inpatient",
            "changed content",
            null,
            "rule/RULE.VTE.RISK",
            AssetVersionSafetyPolicy.NORMAL,
            AssetVersionOverridePolicy.FREE,
            "reviewer-1"
        )))
            .isInstanceOf(ApiException.class)
            .extracting("errorCode")
            .isEqualTo(ErrorCode.CONFLICT);

        verify(repository, never()).save(any(AssetVersion.class));
    }

    @Test
    void draftUpdateReplacesTheCompleteRegistrationAtomically() {
        AssetVersion draft = sample(AssetVersionStatus.DRAFT, "hash-a", "version:av-1");
        when(repository.findByVersionIdAndTenantId("av-1", "tenant-A")).thenReturn(Optional.of(draft));
        when(repository.save(any(AssetVersion.class))).thenAnswer(invocation -> invocation.getArgument(0));

        AssetVersion updated = service.updateDraft(new AssetVersionDraftUpdateCommand(
            "tenant-A",
            "av-1",
            "RULE.VTE.RISK.V2",
            "/GROUP/g-1/HOSPITAL/h-2",
            "adult|outpatient",
            "updated rule content",
            null,
            "rule/RULE.VTE.RISK.V2",
            AssetVersionSafetyPolicy.SAFETY_REDLINE,
            AssetVersionOverridePolicy.LOCKED,
            "reviewer-2"
        ));

        assertThat(updated.assetIdentity()).isEqualTo("RULE.VTE.RISK.V2");
        assertThat(updated.organizationScope()).isEqualTo("/GROUP/g-1/HOSPITAL/h-2");
        assertThat(updated.applicableScope()).isEqualTo("adult|outpatient");
        assertThat(updated.contentHash()).isEqualTo(sha256("updated rule content"));
        assertThat(updated.sourceRef()).isEqualTo("rule/RULE.VTE.RISK.V2");
        assertThat(updated.safetyPolicy()).isEqualTo(AssetVersionSafetyPolicy.SAFETY_REDLINE);
        assertThat(updated.overridePolicy()).isEqualTo(AssetVersionOverridePolicy.LOCKED);
        assertThat(updated.updatedBy()).isEqualTo("reviewer-2");
    }

    @Test
    void activateRejectsSecondActiveVersionInSameEffectiveDomain() {
        AssetVersion target = sample(AssetVersionStatus.PUBLISHED, "hash-b", "version:av-2");
        AssetVersion existingActive = sample(AssetVersionStatus.ACTIVE, "hash-a",
            "RULE.VTE.RISK|/GROUP/g-1/HOSPITAL/h-1|adult|inpatient").withVersionId("av-1");
        when(repository.findByVersionIdAndTenantId("av-2", "tenant-A")).thenReturn(Optional.of(target.withVersionId("av-2")));
        when(repository.findByTenantIdAndAssetTypeAndActiveScopeKeyAndStatus(
            "tenant-A",
            VersionedAssetType.RULE,
            "RULE.VTE.RISK|/GROUP/g-1/HOSPITAL/h-1|adult|inpatient",
            AssetVersionStatus.ACTIVE
        )).thenReturn(List.of(existingActive));

        assertThatThrownBy(() -> service.activate("tenant-A", "av-2", "publisher-1"))
            .isInstanceOf(ApiException.class)
            .extracting("errorCode")
            .isEqualTo(ErrorCode.CONFLICT);

        verify(repository, never()).save(any(AssetVersion.class));
    }

    private AssetVersion sample(AssetVersionStatus status, String hash, String activeScopeKey) {
        Instant now = CLOCK.instant();
        return new AssetVersion(
            1L,
            "av-1",
            "tenant-A",
            VersionedAssetType.RULE,
            "RULE.VTE.RISK",
            "1.0.0",
            "/GROUP/g-1/HOSPITAL/h-1",
            "adult|inpatient",
            hash,
            AssetVersionSafetyPolicy.NORMAL,
            AssetVersionOverridePolicy.FREE,
            status,
            activeScopeKey,
            "rule/RULE.VTE.RISK",
            null,
            null,
            now,
            "reviewer-1",
            now,
            "reviewer-1",
            "trace-sys04"
        );
    }

    private String sha256(String content) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(content.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(hash.length * 2);
            for (byte b : hash) {
                hex.append(String.format("%02x", b));
            }
            return hex.toString();
        } catch (Exception ex) {
            throw new AssertionError(ex);
        }
    }
}
