package com.medkernel.engine.versioning;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import com.medkernel.shared.api.error.ApiException;
import com.medkernel.shared.api.error.ErrorCode;
import com.medkernel.shared.context.OrgScope;
import com.medkernel.shared.context.RequestContext;

class AssetVersionServiceTest {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-06-03T08:00:00Z"), ZoneOffset.UTC);

    private AssetVersionRepository repository;
    private AssetVersionContentRepository contentRepository;
    private AssetIdentityService identities;
    private AssetScopeResolver scopes;
    private AssetVersionService service;

    @BeforeEach
    void setUp() {
        repository = mock(AssetVersionRepository.class);
        contentRepository = mock(AssetVersionContentRepository.class);
        identities = mock(AssetIdentityService.class);
        scopes = mock(AssetScopeResolver.class);
        service = new AssetVersionService(
            repository, null, contentRepository, identities, scopes, CLOCK);
        when(identities.allocateNextVersion(
            anyString(), any(), anyString(), anyString(), any()))
            .thenReturn(new AssetVersionAllocation(1L, "V1"));
        when(scopes.resolveOrganizationPath(anyString(), anyString()))
            .thenAnswer(invocation -> new AssetOwnershipScope(
                com.medkernel.engine.release.ReleaseSourceLayer.HOSPITAL,
                invocation.getArgument(1)));
    }

    @Test
    void registerDraftAllocatesCanonicalVersionInsteadOfAcceptingCallerVersion() {
        when(repository.save(any(AssetVersion.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(identities.allocateNextVersion(
            "tenant-A",
            VersionedAssetType.RULE,
            "RULE.VTE.RISK",
            "reviewer-1",
            "trace-sys04"
        )).thenReturn(new AssetVersionAllocation(1L, "V1"));

        AssetVersion saved = service.registerDraft(new AssetVersionRegisterCommand(
            "tenant-A",
            VersionedAssetType.RULE,
            "RULE.VTE.RISK",
            "/GROUP/g-1/HOSPITAL/h-1",
            "adult|inpatient",
            "when observation.d_dimer > 0.5 then alert",
            null,
            "rule/RULE.VTE.RISK",
            "reviewer-1",
            "trace-sys04"
        ));

        assertThat(saved.versionNo()).isEqualTo("V1");
    }

    @Test
    void registerDraftPersistsOnlyTheCanonicalRealOrganizationOwnerPath() {
        when(repository.save(any(AssetVersion.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(scopes.resolveOrganizationPath(
            "tenant-A", "/tenant-A/group-A/hospital-A/department-A"))
            .thenReturn(new AssetOwnershipScope(
                com.medkernel.engine.release.ReleaseSourceLayer.HOSPITAL,
                "/tenant-A/group-A/hospital-A"));

        AssetVersion saved = service.registerDraft(new AssetVersionRegisterCommand(
            "tenant-A",
            VersionedAssetType.RULE,
            "RULE.VTE.RISK",
            "/tenant-A/group-A/hospital-A/department-A",
            "specialty=cardiology",
            "when observation.d_dimer > 0.5 then alert",
            null,
            "rule/RULE.VTE.RISK",
            "reviewer-1",
            "trace-sys04"
        ));

        assertThat(saved.organizationScope()).isEqualTo("/tenant-A/group-A/hospital-A");
        verify(scopes).resolveOrganizationPath(
            "tenant-A", "/tenant-A/group-A/hospital-A/department-A");
    }

    @Test
    void registerDraftDerivesOwnershipFromTheAuthenticatedOrganizationContext() throws Exception {
        when(repository.save(any(AssetVersion.class))).thenAnswer(invocation -> invocation.getArgument(0));
        OrgScope requestScope = new OrgScope(
            "tenant-A", "group-A", "hospital-A", null, null,
            "department-A", null, "cardiology");
        when(scopes.resolve("tenant-A", requestScope))
            .thenReturn(new AssetOwnershipScope(
                com.medkernel.engine.release.ReleaseSourceLayer.HOSPITAL,
                "/tenant-A/group-A/hospital-A"));

        AssetVersion saved = RequestContext.callWith(
            new RequestContext.Snapshot("trace-sys04", requestScope, "reviewer-1"),
            () -> service.registerDraft(new AssetVersionRegisterCommand(
                "tenant-A",
                VersionedAssetType.RULE,
                "RULE.VTE.RISK",
                null,
                "specialty=cardiology",
                "when observation.d_dimer > 0.5 then alert",
                null,
                "rule/RULE.VTE.RISK",
                "reviewer-1",
                "trace-sys04"
            ))
        );

        assertThat(saved.organizationScope()).isEqualTo("/tenant-A/group-A/hospital-A");
        verify(scopes).resolve("tenant-A", requestScope);
    }

    @Test
    void registerDraftComputesStableContentHashAndRejectsMismatch() {
        when(repository.save(any(AssetVersion.class))).thenAnswer(invocation -> invocation.getArgument(0));

        AssetVersion saved = service.registerDraft(new AssetVersionRegisterCommand(
            "tenant-A",
            VersionedAssetType.RULE,
            "RULE.VTE.RISK",
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
    void registerDraftPersistsDeclarativeAssetContentInsteadOfOnlyKeepingItsHash() {
        when(repository.save(any(AssetVersion.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(contentRepository.save(any(AssetVersionContent.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));
        String content = """
            {
              "schemaVersion": "1.0",
              "fields": [
                {
                  "fieldPath": "observations[].valueNumeric",
                  "dataType": "number"
                }
              ]
            }
            """;

        AssetVersion saved = service.registerDraft(new AssetVersionRegisterCommand(
            "tenant-A",
            VersionedAssetType.FIELD_CATALOG,
            "FIELD.CANONICAL",
            "/GROUP/g-1/HOSPITAL/h-1",
            "ALL",
            content,
            null,
            "canonical-field-catalog",
            "operator-1",
            "trace-field-catalog"
        ));

        ArgumentCaptor<AssetVersionContent> body = ArgumentCaptor.forClass(AssetVersionContent.class);
        verify(contentRepository).save(body.capture());
        assertThat(body.getValue().versionId()).isEqualTo(saved.versionId());
        assertThat(body.getValue().tenantId()).isEqualTo("tenant-A");
        assertThat(body.getValue().contentJson()).isEqualTo(content);
        assertThat(body.getValue().contentHash()).isEqualTo(saved.contentHash());
        assertThat(body.getValue().createdBy()).isEqualTo("operator-1");
    }

    @Test
    void declarativeAssetRejectsRegistrationWithoutARecoverableContentBody() {
        assertThatThrownBy(() -> service.registerDraft(new AssetVersionRegisterCommand(
            "tenant-A",
            VersionedAssetType.FORMULA,
            "FORMULA.EGFR",
            "/GROUP/g-1/HOSPITAL/h-1",
            "ALL",
            null,
            "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef",
            "formula-egfr",
            "operator-1",
            "trace-formula"
        )))
            .isInstanceOf(ApiException.class)
            .hasMessageContaining("资产正文")
            .extracting("errorCode")
            .isEqualTo(ErrorCode.VALIDATION_FAILED);

        verify(repository, never()).save(any(AssetVersion.class));
        verify(contentRepository, never()).save(any(AssetVersionContent.class));
    }

    @Test
    void registerDraftCarriesExplicitOverridePolicy() {
        when(repository.save(any(AssetVersion.class))).thenAnswer(invocation -> invocation.getArgument(0));

        AssetVersion saved = service.registerDraft(new AssetVersionRegisterCommand(
            "tenant-A",
            VersionedAssetType.RULE,
            "RULE.VTE.RISK",
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
    void rejectsMalformedStructuredScopeBeforePersistingVersion() {
        assertThatThrownBy(() -> service.registerDraft(new AssetVersionRegisterCommand(
            "tenant-A",
            VersionedAssetType.RULE,
            "RULE.VTE.RISK",
            "/GROUP/g-1/HOSPITAL/h-1",
            "unknown=ICU",
            "when allergy.penicillin then block order",
            null,
            "rule/RULE.VTE.RISK",
            "reviewer-1",
            "trace-sys04"
        )))
            .isInstanceOf(ApiException.class)
            .hasMessageContaining("未知作用域维度")
            .extracting("errorCode")
            .isEqualTo(ErrorCode.VALIDATION_FAILED);

        verify(repository, never()).save(any(AssetVersion.class));
    }

    @Test
    void editingPublishedVersionAutomaticallyCreatesTheNextDraft() {
        AssetVersion published = sample(AssetVersionStatus.PUBLISHED, "hash-a", "version:av-1");
        when(repository.findByVersionIdAndTenantId("av-1", "tenant-A")).thenReturn(Optional.of(published));
        when(identities.allocateNextVersion(
            "tenant-A",
            VersionedAssetType.RULE,
            "RULE.VTE.RISK",
            "reviewer-1",
            null
        )).thenReturn(new AssetVersionAllocation(2L, "V2"));
        when(repository.save(any(AssetVersion.class))).thenAnswer(invocation -> invocation.getArgument(0));

        AssetVersion nextDraft = service.updateDraft(new AssetVersionDraftUpdateCommand(
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
        ));

        assertThat(nextDraft.versionId()).isNotEqualTo(published.versionId());
        assertThat(nextDraft.versionNo()).isEqualTo("V2");
        assertThat(nextDraft.status()).isEqualTo(AssetVersionStatus.DRAFT);
        assertThat(nextDraft.contentHash()).isEqualTo(sha256("changed content"));
        assertThat(published.status()).isEqualTo(AssetVersionStatus.PUBLISHED);
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
    void draftUpdateRevalidatesAndCanonicalizesTheOrganizationOwnerPath() {
        AssetVersion draft = sample(AssetVersionStatus.DRAFT, "hash-a", "version:av-1");
        when(repository.findByVersionIdAndTenantId("av-1", "tenant-A")).thenReturn(Optional.of(draft));
        when(repository.save(any(AssetVersion.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(scopes.resolveOrganizationPath(
            "tenant-A", "/tenant-A/group-A/hospital-B/department-B"))
            .thenReturn(new AssetOwnershipScope(
                com.medkernel.engine.release.ReleaseSourceLayer.HOSPITAL,
                "/tenant-A/group-A/hospital-B"));

        AssetVersion updated = service.updateDraft(new AssetVersionDraftUpdateCommand(
            "tenant-A",
            "av-1",
            "RULE.VTE.RISK",
            "/tenant-A/group-A/hospital-B/department-B",
            "specialty=cardiology",
            "updated rule content",
            null,
            "rule/RULE.VTE.RISK",
            AssetVersionSafetyPolicy.NORMAL,
            AssetVersionOverridePolicy.FREE,
            "reviewer-2"
        ));

        assertThat(updated.organizationScope()).isEqualTo("/tenant-A/group-A/hospital-B");
        verify(scopes).resolveOrganizationPath(
            "tenant-A", "/tenant-A/group-A/hospital-B/department-B");
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
