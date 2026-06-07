package com.medkernel.engine.knowledge.diagnosis;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;

import com.medkernel.engine.knowledge.KnowledgeAssetVersion;
import com.medkernel.engine.knowledge.KnowledgeDomain;
import com.medkernel.engine.knowledge.KnowledgeIdentity;
import com.medkernel.engine.knowledge.KnowledgeIdentityService;
import com.medkernel.engine.knowledge.KnowledgeIdentityStatus;
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
    private DiagnosisReferenceValidator validator;

    @BeforeEach
    void setUp() {
        identities = mock(KnowledgeIdentityService.class);
        assetVersions = mock(AssetVersionRepository.class);
        validator = new DiagnosisReferenceValidator(identities, assetVersions);
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
    void careRuleAndPathwayTargetsRequireAnActiveUnifiedVersion() {
        AssetVersion active = mock(AssetVersion.class);
        when(assetVersions.findByTenantIdAndAssetTypeAndAssetIdentityAndStatus(
            "t-dept", VersionedAssetType.RULE, "RULE.CKD", AssetVersionStatus.ACTIVE))
            .thenReturn(List.of(active));
        when(assetVersions.findByTenantIdAndAssetTypeAndAssetIdentityAndStatus(
            "t-dept", VersionedAssetType.PATHWAY, "PATH.CKD", AssetVersionStatus.ACTIVE))
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

    private KnowledgeIdentity identity(Long id, String code, KnowledgeDomain domain) {
        Instant now = Instant.now();
        return new KnowledgeIdentity(id, "t-dept", code, domain, code, null, null,
            KnowledgeIdentityStatus.ACTIVE, null, now, "medical-1", now, "medical-1");
    }
}
