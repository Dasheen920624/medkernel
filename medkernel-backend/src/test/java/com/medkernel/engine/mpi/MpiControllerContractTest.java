package com.medkernel.engine.mpi;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import com.medkernel.shared.api.error.ApiException;
import com.medkernel.shared.api.error.ErrorCode;
import com.medkernel.shared.context.RequestContext;

/**
 * SVC-PILOT-02 MPI 服务包 HTTP 契约测试。
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("dev")
class MpiControllerContractTest {

    @Autowired
    MockMvc mvc;

    @MockBean
    MpiService service;

    @AfterEach
    void clearContext() {
        RequestContext.clear();
    }

    @Test
    void engineMpiRouteExposesStatsMergeReviewsAndManualConfirm() throws Exception {
        when(service.getStats()).thenReturn(new MpiStatsResponse(2, 1, 4, 36.5, Map.of("M", 1L, "F", 1L)));
        when(service.createPatient(any(MpiPatientCreateRequest.class))).thenReturn(
            new MpiPatient(1L, "mpi-new", "tenant-A", "李*四", "F", 41, "9876", 0, "ACTIVE",
                null, Instant.now(), "doctor-a", Instant.now(), "doctor-a")
        );
        when(service.patientDetail("mpi-1")).thenReturn(new MpiPatientDetailResponse(
            new MpiPatient(1L, "mpi-1", "tenant-A", "张*三", "M", 36, "1234", 0, "ACTIVE",
                null, Instant.now(), "test", Instant.now(), "test"),
            null, null, 2, List.of(), "trace-mpi"));
        when(service.mergePatients("mpi-source", "mpi-target"))
            .thenReturn(new MpiMergeResult("MERGED", "mpi-source", "mpi-target", null, null, "患者主索引已合并"));
        when(service.splitMergedPatient(eq("mpi-source"), any(MpiSplitRequest.class)))
            .thenReturn(new MpiSplitResult("SPLIT", "mpi-source", "mpi-target", "患者主索引合并关系已拆分"));
        when(service.getMergeReviews("PENDING")).thenReturn(List.of(MpiMergeReview.pending(
            "mrv-1", "tenant-A", "mpi-source", "mpi-target", "HIGH", "身份证后四位不一致",
            "doctor-a", Instant.now(), "trace-mpi"
        )));
        when(service.confirmMergeReview(eq("mrv-1"), any(MpiMergeReviewConfirmRequest.class)))
            .thenReturn(new MpiMergeResult("MERGED", "mpi-source", "mpi-target", "mrv-1", "HIGH", "患者主索引已合并"));

        mvc.perform(get("/api/v1/engine/mpi/stats").with(readJwt()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.activeCount").value(2))
            .andExpect(jsonPath("$.data.activePathwayCount").value(4));

        mvc.perform(post("/api/v1/engine/mpi/patients")
                .with(writeJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"mpiId\":\"mpi-new\",\"maskedName\":\"李*四\",\"gender\":\"F\",\"age\":41,\"idLast4\":\"9876\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.mpiId").value("mpi-new"))
            .andExpect(jsonPath("$.data.status").value("ACTIVE"));

        mvc.perform(get("/api/v1/engine/mpi/patients/mpi-1").with(readJwt()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.patient.mpiId").value("mpi-1"))
            .andExpect(jsonPath("$.data.activePathwayCount").value(2))
            .andExpect(jsonPath("$.data.traceId").value("trace-mpi"));

        mvc.perform(post("/api/v1/engine/mpi/patients:merge")
                .with(writeJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"sourceMpiId\":\"mpi-source\",\"targetMpiId\":\"mpi-target\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.status").value("MERGED"));

        mvc.perform(post("/api/v1/engine/mpi/patients/mpi-source:split")
                .with(writeJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"reviewReason\":\"人工核查后确认不是同一患者\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.status").value("SPLIT"))
            .andExpect(jsonPath("$.data.sourceMpiId").value("mpi-source"))
            .andExpect(jsonPath("$.data.targetMpiId").value("mpi-target"));

        mvc.perform(get("/api/v1/engine/mpi/merge-reviews")
                .queryParam("status", "PENDING")
                .with(readJwt()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data[0].reviewId").value("mrv-1"))
            .andExpect(jsonPath("$.data[0].status").value("PENDING"));

        mvc.perform(post("/api/v1/engine/mpi/merge-reviews/mrv-1/confirm")
                .with(writeJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"reviewReason\":\"人工核验身份证原件一致\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.reviewId").value("mrv-1"))
            .andExpect(jsonPath("$.data.riskLevel").value("HIGH"));
    }

    @Test
    void highRiskAutoMergeReturnsExplicitReviewRequiredProblemDetail() throws Exception {
        doThrow(new ApiException(
            ErrorCode.MPI_MERGE_REQUIRES_REVIEW,
            "高危患者主索引合并需要人工确认，审核单：mrv-1；原因：身份证后四位不一致"
        )).when(service).mergePatients("mpi-source", "mpi-target");

        mvc.perform(post("/api/v1/engine/mpi/patients/merge")
                .with(writeJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"sourceMpiId\":\"mpi-source\",\"targetMpiId\":\"mpi-target\"}"))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.code").value("MPI_MERGE_REQUIRES_REVIEW"))
            .andExpect(jsonPath("$.detail").value("高危患者主索引合并需要人工确认，审核单：mrv-1；原因：身份证后四位不一致"));
    }

    private static RequestPostProcessor readJwt() {
        return jwt().jwt(token -> token
                .subject("it-ops")
                .claim("tenant_id", "tenant-A")
                .claim("roles", List.of("it-ops")))
            .authorities(new SimpleGrantedAuthority("ROLE_IT_OPS"));
    }

    private static RequestPostProcessor writeJwt() {
        return jwt().jwt(token -> token
                .subject("hospital-admin")
                .claim("tenant_id", "tenant-A")
                .claim("roles", List.of("hospital-admin")))
            .authorities(new SimpleGrantedAuthority("ROLE_HOSPITAL_ADMIN"));
    }
}
