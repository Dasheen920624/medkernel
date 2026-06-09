package com.medkernel.engine.versioning;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

import com.medkernel.shared.api.error.ApiException;

class ApplicableScopeMatcherTest {

    @Test
    void candidateDimensionsMatchWhenTheyAreSubsetOfQueryDimensions() {
        String query = ApplicableScopeMatcher.canonicalQuery(
            "AF", "S16", "ED", "RENAL_IMPAIR", "DOCTOR");

        assertThat(query)
            .isEqualTo("specialty=AF;scenario=S16;setting=ED;cohort=RENAL_IMPAIR;role=DOCTOR");
        assertThat(ApplicableScopeMatcher.matches("specialty=AF;setting=ED", query)).isTrue();
        assertThat(ApplicableScopeMatcher.matches("specialty=AF;setting=IPD", query)).isFalse();
        assertThat(ApplicableScopeMatcher.matches("ALL", query)).isTrue();
    }

    @Test
    void moreDimensionsAndNarrowerValuesHaveHigherSpecificity() {
        assertThat(ApplicableScopeMatcher.specificityOf("specialty=AF;setting=ED"))
            .isGreaterThan(ApplicableScopeMatcher.specificityOf("specialty=AF"));
        assertThat(ApplicableScopeMatcher.specificityOf("setting=ED"))
            .isGreaterThan(ApplicableScopeMatcher.specificityOf("setting=ED,IPD"));
    }

    @Test
    void malformedOrUnknownDimensionsAreRejected() {
        assertThatThrownBy(() -> ApplicableScopeMatcher.matches("specialty", "specialty=AF"))
            .isInstanceOf(ApiException.class)
            .hasMessageContaining("applicableScope");
        assertThatThrownBy(() -> ApplicableScopeMatcher.matches("unknown=X", "specialty=AF"))
            .isInstanceOf(ApiException.class)
            .hasMessageContaining("未知作用域维度");
    }

    @Test
    void dimensionValuesCannotInjectAdditionalScopeSegments() {
        assertThatThrownBy(() -> ApplicableScopeMatcher.canonicalQuery(
            "AF;role=ADMIN", "S16", null, null, null))
            .isInstanceOf(ApiException.class)
            .hasMessageContaining("作用域维度值");
        assertThatThrownBy(() -> ApplicableScopeMatcher.canonicalQuery(
            "AF,ONCOLOGY", "S16", null, null, null))
            .isInstanceOf(ApiException.class)
            .hasMessageContaining("作用域维度值");
    }

    @Test
    void unstructuredScopesMatchOnlyByExactValue() {
        assertThat(ApplicableScopeMatcher.matches("adult|inpatient", "adult|inpatient")).isTrue();
        assertThat(ApplicableScopeMatcher.matches("adult|inpatient", "adult|outpatient")).isFalse();
    }
}
