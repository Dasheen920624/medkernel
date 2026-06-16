package com.medkernel.engine.knowledge.diagnosis;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import com.medkernel.engine.knowledge.Citation;
import com.medkernel.engine.knowledge.CitationRelation;
import com.medkernel.engine.knowledge.CitationRepository;
import com.medkernel.engine.knowledge.KnowledgeAssetVersion;
import com.medkernel.engine.knowledge.KnowledgeDomain;
import com.medkernel.engine.knowledge.KnowledgeIdentity;
import com.medkernel.engine.knowledge.KnowledgeIdentityService;
import com.medkernel.engine.knowledge.KnowledgeIdentityStatus;
import com.medkernel.engine.knowledge.KnowledgeRiskLevel;
import com.medkernel.engine.knowledge.KnowledgeVersionStatus;
import com.medkernel.engine.knowledge.SourceAuthorityLevel;
import com.medkernel.engine.terminology.StandardTerm;
import com.medkernel.engine.terminology.StandardTermRepository;
import com.medkernel.engine.versioning.AssetVersion;
import com.medkernel.engine.versioning.AssetVersionRepository;
import com.medkernel.engine.versioning.AssetVersionStatus;
import com.medkernel.engine.versioning.VersionedAssetType;
import com.medkernel.shared.api.error.ApiException;
import com.medkernel.shared.api.error.ErrorCode;
import com.medkernel.shared.context.OrgScope;
import com.medkernel.shared.context.RequestContext;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class DiagnosisReferenceValidatorTest {

    private KnowledgeIdentityService identities;
    private AssetVersionRepository assetVersions;
    private StandardTermRepository standardTerms;
    private CitationRepository citations;
    private DiagnosisReferenceValidator validator;

    @BeforeEach
    void setUp() {
        identities = mock(KnowledgeIdentityService.class);
        assetVersions = mock(AssetVersionRepository.class);
        standardTerms = mock(StandardTermRepository.class);
        citations = mock(CitationRepository.class);
        validator = new DiagnosisReferenceValidator(identities, assetVersions, standardTerms, citations);
        RequestContext.restore(new RequestContext.Snapshot(
            "trace-ref", OrgScope.tenant("t-dept"), "medical-1"));
    }

    @AfterEach
    void clear() {
        RequestContext.clear();
    }

    @Test
    void differentialRequiresAnotherActiveDiagnosisIdentity() {
        KnowledgeIdentity target = identity(9L, "DX.ACS", KnowledgeDomain.DIAGNOSIS);
        when(identities.get(9L)).thenReturn(target);
        when(identities.getActiveVersion(9L)).thenReturn(mock(KnowledgeAssetVersion.class));

        assertThatCode(() -> validator.validateDifferential(7L, 9L)).doesNotThrowAnyException();
        assertThatThrownBy(() -> validator.validateDifferential(7L, 7L))
            .isInstanceOf(ApiException.class)
            .extracting(error -> ((ApiException) error).errorCode())
            .isEqualTo(ErrorCode.VALIDATION_FAILED);
    }

    @Test
    void differentialRejectsNonDiagnosisKnowledge() {
        when(identities.get(9L)).thenReturn(identity(9L, "GUIDE.ACS", KnowledgeDomain.GUIDELINE));

        assertThatThrownBy(() -> validator.validateDifferential(7L, 9L))
            .isInstanceOf(ApiException.class)
            .extracting(error -> ((ApiException) error).errorCode())
            .isEqualTo(ErrorCode.VALIDATION_FAILED);
    }

    @Test
    void careRuleAndPathwayTargetsRequireAPublishedUnifiedVersion() {
        AssetVersion active = mock(AssetVersion.class);
        when(assetVersions.findByTenantIdAndAssetTypeAndAssetIdentityAndStatus(
            "t-dept", VersionedAssetType.RULE, "RULE.CKD", AssetVersionStatus.PUBLISHED))
            .thenReturn(List.of(active));
        when(assetVersions.findByTenantIdAndAssetTypeAndAssetIdentityAndStatus(
            "t-dept", VersionedAssetType.PATHWAY, "PATH.CKD", AssetVersionStatus.PUBLISHED))
            .thenReturn(List.of(active));

        assertThatCode(() -> validator.validateCareTarget(
            DiagnosisCareTargetType.RULE, "RULE.CKD")).doesNotThrowAnyException();
        assertThatCode(() -> validator.validateCareTarget(
            DiagnosisCareTargetType.PATHWAY, "PATH.CKD")).doesNotThrowAnyException();

        assertThatThrownBy(() -> validator.validateCareTarget(
            DiagnosisCareTargetType.RULE, "RULE.MISSING"))
            .isInstanceOf(ApiException.class)
            .extracting(error -> ((ApiException) error).errorCode())
            .isEqualTo(ErrorCode.VALIDATION_FAILED);
    }

    @Test
    void careKnowledgeTargetRequiresAnActiveKnowledgeVersion() {
        KnowledgeIdentity target = identity(9L, "KNOW.CKD", KnowledgeDomain.GUIDELINE);
        when(identities.getByCode("KNOW.CKD")).thenReturn(target);
        when(identities.getActiveVersion(9L)).thenReturn(mock(KnowledgeAssetVersion.class));

        assertThatCode(() -> validator.validateCareTarget(
            DiagnosisCareTargetType.KNOWLEDGE, "KNOW.CKD")).doesNotThrowAnyException();
    }

    @Test
    void criterionRequiresActiveStandardFindingTermFromRuntimeDictionaries() {
        KnowledgeAssetVersion version = version(10L, 7L);
        when(standardTerms.findFirstActiveByTenantIdsAndStandardSystemAndTermCode(
            List.of("t-1", "t-dept"), "t-dept", "TERM.LAB", "LOINC-EGFR"))
            .thenReturn(Optional.of(mock(StandardTerm.class)));

        assertThatCode(() -> validator.validateCriterion(version, "LOINC-EGFR", null))
            .doesNotThrowAnyException();

        assertThatThrownBy(() -> validator.validateCriterion(version, "UNKNOWN-FINDING", null))
            .isInstanceOf(ApiException.class)
            .extracting(error -> ((ApiException) error).errorCode())
            .isEqualTo(ErrorCode.VALIDATION_FAILED);
    }

    @Test
    void criterionCitationMustBelongToCurrentDiagnosisVersion() {
        KnowledgeAssetVersion version = version(10L, 7L);
        when(standardTerms.findFirstActiveByTenantIdsAndStandardSystemAndTermCode(
            List.of("t-1", "t-dept"), "t-dept", "TERM.DIAGNOSIS", "DX.CKD"))
            .thenReturn(Optional.of(mock(StandardTerm.class)));
        when(citations.findByTenantIdAndId("t-dept", 33L))
            .thenReturn(Optional.of(citation(33L, 10L)));
        when(citations.findByTenantIdAndId("t-dept", 34L))
            .thenReturn(Optional.of(citation(34L, 11L)));

        assertThatCode(() -> validator.validateCriterion(version, "DX.CKD", 33L))
            .doesNotThrowAnyException();

        assertThatThrownBy(() -> validator.validateCriterion(version, "DX.CKD", 34L))
            .isInstanceOf(ApiException.class)
            .extracting(error -> ((ApiException) error).errorCode())
            .isEqualTo(ErrorCode.VALIDATION_FAILED);
    }

    private KnowledgeIdentity identity(Long id, String code, KnowledgeDomain domain) {
        Instant now = Instant.now();
        return new KnowledgeIdentity(id, "t-dept", code, domain, code, null, null,
            KnowledgeIdentityStatus.ACTIVE, null, now, "medical-1", now, "medical-1");
    }

    private KnowledgeAssetVersion version(Long id, Long identityId) {
        Instant now = Instant.now();
        return new KnowledgeAssetVersion(
            id, "t-dept", identityId, "2026", "2026 版", 30L, 31L, "hash", "section-1",
            KnowledgeVersionStatus.PENDING_REPLACEMENT_REVIEW, KnowledgeRiskLevel.HIGH,
            SourceAuthorityLevel.B_GUIDELINE, null, null, null,
            "tenant:t-dept", "DEFAULT", "version:" + id, null, null, null, null,
            null, null, null, null, now, "medical-1", now, "medical-1", 12, null);
    }

    private Citation citation(Long id, Long assetVersionId) {
        return new Citation(id, "t-dept", assetVersionId, 88L,
            CitationRelation.DERIVED_FROM, 100, null, null, Instant.now(), "medical-1");
    }
}
