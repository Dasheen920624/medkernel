package com.medkernel.engine.rule;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.medkernel.shared.api.error.ApiException;
import com.medkernel.shared.api.error.ErrorCode;
import com.medkernel.shared.context.OrgScope;

class RuleApplicabilityEvaluatorTest {

    private ObjectMapper json;
    private RuleApplicabilityEvaluator evaluator;

    @BeforeEach
    void setUp() {
        json = new ObjectMapper();
        evaluator = new RuleApplicabilityEvaluator(
            json,
            Clock.fixed(Instant.parse("2026-06-07T08:00:00Z"), ZoneOffset.UTC));
    }

    @Test
    void excludesMatchedPopulationBeforeRuleEvaluation() {
        RuleApplicabilityDecision decision = evaluator.evaluate(
            read("""
                {
                  "population": {
                    "exclude": {
                      "all": [
                        {
                          "fact": "patient.specialPopulations",
                          "operator": "contains",
                          "value": "PREGNANT"
                        }
                      ]
                    }
                  },
                  "orgScope": {},
                  "settings": ["INPATIENT"],
                  "effective": {"rolloutPercent": 100}
                }
                """),
            read("""
                {
                  "patient": {
                    "mpi": "MPI-1",
                    "specialPopulations": ["PREGNANT"]
                  },
                  "encounters": [{"encounterType": "INPATIENT"}]
                }
                """),
            OrgScope.tenant("tenant-A"),
            "version-1"
        );

        assertThat(decision.applicable()).isFalse();
        assertThat(decision.reasonCode()).isEqualTo("POPULATION_EXCLUDED");
    }

    @Test
    void requiresPopulationInclusionWhenConfigured() {
        RuleApplicabilityDecision decision = evaluator.evaluate(
            read("""
                {
                  "population": {
                    "include": {
                      "all": [
                        {"fact": "patient.age", "operator": "gte", "value": 65}
                      ]
                    }
                  },
                  "orgScope": {},
                  "settings": ["OUTPATIENT"],
                  "effective": {"rolloutPercent": 100}
                }
                """),
            read("""
                {
                  "patient": {"mpi": "MPI-1", "age": 42},
                  "encounters": [{"encounterType": "OUTPATIENT"}]
                }
                """),
            OrgScope.tenant("tenant-A"),
            "version-1"
        );

        assertThat(decision.applicable()).isFalse();
        assertThat(decision.reasonCode()).isEqualTo("POPULATION_NOT_INCLUDED");
    }

    @Test
    void requiresEveryConfiguredOrganizationDimensionToMatch() {
        RuleApplicabilityDecision decision = evaluator.evaluate(
            read("""
                {
                  "population": {},
                  "orgScope": {
                    "groupIds": ["group-1"],
                    "hospitalIds": ["hospital-1"],
                    "deptIds": ["dept-1"]
                  },
                  "settings": ["INPATIENT"],
                  "effective": {"rolloutPercent": 100}
                }
                """),
            context("MPI-1", "INPATIENT"),
            new OrgScope(
                "tenant-A", "group-1", "hospital-1", null, null, "dept-2", null, null),
            "version-1"
        );

        assertThat(decision.applicable()).isFalse();
        assertThat(decision.reasonCode()).isEqualTo("ORG_SCOPE_MISMATCH");
    }

    @Test
    void rejectsContextOutsideConfiguredClinicalSetting() {
        RuleApplicabilityDecision decision = evaluator.evaluate(
            read("""
                {
                  "population": {},
                  "orgScope": {},
                  "settings": ["ED"],
                  "effective": {"rolloutPercent": 100}
                }
                """),
            context("MPI-1", "INPATIENT"),
            OrgScope.tenant("tenant-A"),
            "version-1"
        );

        assertThat(decision.applicable()).isFalse();
        assertThat(decision.reasonCode()).isEqualTo("SETTING_MISMATCH");
    }

    @Test
    void appliesInclusiveEffectiveDateBoundaries() {
        RuleApplicabilityDecision decision = evaluator.evaluate(
            read("""
                {
                  "population": {},
                  "orgScope": {},
                  "settings": ["INPATIENT"],
                  "effective": {
                    "from": "2026-06-07",
                    "to": "2026-06-07",
                    "rolloutPercent": 100
                  }
                }
                """),
            context("MPI-1", "INPATIENT"),
            OrgScope.tenant("tenant-A"),
            "version-1"
        );

        assertThat(decision.applicable()).isTrue();
        assertThat(decision.reasonCode()).isEqualTo("APPLICABLE");
    }

    @Test
    void rolloutBucketIsStableForSamePatientAndVersion() {
        JsonNode applicability = read("""
            {
              "population": {},
              "orgScope": {},
              "settings": ["INPATIENT"],
              "effective": {"rolloutPercent": 50}
            }
            """);

        RuleApplicabilityDecision first = evaluator.evaluate(
            applicability, context("MPI-STABLE", "INPATIENT"), OrgScope.tenant("tenant-A"), "version-1");
        RuleApplicabilityDecision second = evaluator.evaluate(
            applicability, context("MPI-STABLE", "INPATIENT"), OrgScope.tenant("tenant-A"), "version-1");

        assertThat(second.applicable()).isEqualTo(first.applicable());
        assertThat(second.details().path("rolloutBucket").asInt())
            .isEqualTo(first.details().path("rolloutBucket").asInt());
    }

    @Test
    void partialRolloutWithoutStablePatientOrEncounterIdentityIsNotApplicable() {
        RuleApplicabilityDecision decision = evaluator.evaluate(
            read("""
                {
                  "population": {},
                  "orgScope": {},
                  "settings": ["INPATIENT"],
                  "effective": {"rolloutPercent": 50}
                }
                """),
            read("""
                {"patient": {"age": 72}, "encounters": [{"encounterType": "INPATIENT"}]}
                """),
            OrgScope.tenant("tenant-A"),
            "version-1"
        );

        assertThat(decision.applicable()).isFalse();
        assertThat(decision.reasonCode()).isEqualTo("ROLLOUT_IDENTITY_MISSING");
    }

    @Test
    void partialRolloutUsesClinicalEventPatientIdAsStableIdentity() {
        RuleApplicabilityDecision decision = evaluator.evaluate(
            read("""
                {
                  "population": {},
                  "orgScope": {},
                  "settings": ["INPATIENT"],
                  "effective": {"rolloutPercent": 50}
                }
                """),
            read("""
                {
                  "patient": {"patientId": "MPI-EVENT-1"},
                  "encounters": [{"encounterType": "INPATIENT"}]
                }
                """),
            OrgScope.tenant("tenant-A"),
            "version-1"
        );

        assertThat(decision.reasonCode()).isNotEqualTo("ROLLOUT_IDENTITY_MISSING");
        assertThat(decision.details().path("rolloutBucket").isIntegralNumber()).isTrue();
    }

    @Test
    void validatesRequiredApplicabilityShapeAndDateOrder() {
        assertThatThrownBy(() -> evaluator.validate(read("""
            {
              "population": {},
              "orgScope": {},
              "settings": [],
              "effective": {
                "from": "2026-06-08",
                "to": "2026-06-07",
                "rolloutPercent": 101
              }
            }
            """)))
            .isInstanceOf(ApiException.class)
            .extracting("errorCode")
            .isEqualTo(ErrorCode.ENG_RULE_001);
    }

    private JsonNode context(String patientId, String setting) {
        return read("""
            {
              "patient": {"mpi": "%s", "age": 72},
              "encounters": [{"encounterId": "ENC-1", "encounterType": "%s"}]
            }
            """.formatted(patientId, setting));
    }

    private JsonNode read(String source) {
        try {
            return json.readTree(source);
        } catch (Exception exception) {
            throw new IllegalStateException(exception);
        }
    }
}
