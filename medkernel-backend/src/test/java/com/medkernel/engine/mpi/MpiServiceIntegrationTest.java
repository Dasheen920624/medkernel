package com.medkernel.engine.mpi;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Instant;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import com.medkernel.shared.api.error.ApiException;
import com.medkernel.shared.api.error.ErrorCode;
import com.medkernel.shared.context.OrgScope;
import com.medkernel.shared.context.RequestContext;

/**
 * MPI 服务事务边界集成测试。
 */
@SpringBootTest
@ActiveProfiles("test")
class MpiServiceIntegrationTest {

    private static final String TENANT_ID = "tenant-mpi-it";

    @Autowired
    MpiService service;

    @Autowired
    MpiPatientRepository patientRepository;

    @Autowired
    MpiMergeReviewRepository reviewRepository;

    @AfterEach
    void clearContext() {
        reviewRepository.deleteAll();
        patientRepository.deleteAll();
        RequestContext.clear();
    }

    @Test
    void statsHandlesNewlyCreatedUnknownGenderPatient() {
        RequestContext.restore(new RequestContext.Snapshot("trace-mpi-stats-it", OrgScope.tenant(TENANT_ID), "clinical-user"));
        patientRepository.save(new MpiPatient(
            null, "mpi-it-stats", TENANT_ID, "赵*君", "UNKNOWN", 67, "4568", 0, "ACTIVE",
            null, Instant.now(), "test", Instant.now(), "test"
        ));

        MpiStatsResponse stats = service.getStats();

        assertThat(stats.activeCount()).isEqualTo(1L);
        assertThat(stats.mergedCount()).isZero();
        assertThat(stats.activePathwayCount()).isZero();
        assertThat(stats.averageAge()).isEqualTo(67.0);
        assertThat(stats.genderCounts()).containsEntry("UNKNOWN", 1L);
    }

    @Test
    void highRiskMergePersistsPendingReviewWithoutChangingPatientsWhenProblemDetailReturned() {
        RequestContext.restore(new RequestContext.Snapshot("trace-mpi-it", OrgScope.tenant(TENANT_ID), "reviewer-a"));
        patientRepository.save(new MpiPatient(
            null, "mpi-it-source", TENANT_ID, "李*一", "M", 41, "1234", 0, "ACTIVE",
            null, Instant.now(), "test", Instant.now(), "test"
        ));
        patientRepository.save(new MpiPatient(
            null, "mpi-it-target", TENANT_ID, "李*一", "M", 41, "5678", 0, "ACTIVE",
            null, Instant.now(), "test", Instant.now(), "test"
        ));

        ApiException error = assertThrows(ApiException.class,
            () -> service.mergePatients("mpi-it-source", "mpi-it-target"));

        assertThat(error.errorCode()).isEqualTo(ErrorCode.MPI_MERGE_REQUIRES_REVIEW);
        MpiMergeReview review = reviewRepository
            .findByTenantIdAndSourceMpiIdAndTargetMpiId(TENANT_ID, "mpi-it-source", "mpi-it-target")
            .orElseThrow();
        assertThat(review.status()).isEqualTo("PENDING");
        assertThat(review.riskReason()).contains("身份证后四位不一致");
        assertThat(patientRepository.findByTenantIdAndMpiId(TENANT_ID, "mpi-it-source").orElseThrow().status())
            .isEqualTo("ACTIVE");
    }
}
