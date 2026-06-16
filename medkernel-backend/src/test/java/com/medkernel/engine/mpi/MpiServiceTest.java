package com.medkernel.engine.mpi;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import com.medkernel.engine.context.ContextSnapshotFilter;
import com.medkernel.engine.context.ContextSnapshotResponse;
import com.medkernel.engine.context.ContextSnapshotService;
import com.medkernel.engine.context.ContextSnapshotStatus;
import com.medkernel.engine.context.ContextSnapshotSummary;
import com.medkernel.engine.context.QualityStatus;
import com.medkernel.engine.pathway.PatientPathway;
import com.medkernel.engine.pathway.PatientPathwayRepository;
import com.medkernel.engine.pathway.PatientPathwayStatus;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import com.medkernel.shared.api.PageRequest;
import com.medkernel.shared.api.PageResponse;
import com.medkernel.shared.api.error.ApiException;
import com.medkernel.shared.api.error.ErrorCode;
import com.medkernel.shared.context.OrgScope;
import com.medkernel.shared.context.RequestContext;
import com.medkernel.shared.observability.StateTransitionRecorder;

/**
 * MpiService 单元测试。
 *
 * <p>全面覆盖列表查询、多维度指标驾驶舱统计、事务合并逻辑及其可观测性审计记录。
 */
class MpiServiceTest {

    private MpiPatientRepository repository;
    private MpiMergeReviewRepository reviewRepository;
    private StateTransitionRecorder recorder;
    private ContextSnapshotService contextSnapshots;
    private PatientPathwayRepository patientPathways;
    private MpiService service;

    private static final String TENANT_ID = "tenant-A";
    private static final String ACTOR = "tester";

    @BeforeEach
    void setUp() {
        repository = mock(MpiPatientRepository.class);
        reviewRepository = mock(MpiMergeReviewRepository.class);
        recorder = mock(StateTransitionRecorder.class);
        contextSnapshots = mock(ContextSnapshotService.class);
        patientPathways = mock(PatientPathwayRepository.class);
        service = new MpiService(repository, reviewRepository, recorder, contextSnapshots, patientPathways);

        // 设置 RequestContext
        RequestContext.restore(new RequestContext.Snapshot("trace-mpi-test", OrgScope.tenant(TENANT_ID), ACTOR));
    }

    @AfterEach
    void tearDown() {
        RequestContext.clear();
    }

    @Test
    void shouldThrowIfTenantMissingWhenQueryPatients() {
        RequestContext.clear();
        assertThatThrownBy(() -> service.getPatients(null, null, PageRequest.defaults()))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("租户 ID 缺失");
    }

    @Test
    void shouldReturnEmptyPageWhenNoPatientsFound() {
        when(repository.countPatients(eq(TENANT_ID), any(), any())).thenReturn(0L);

        PageResponse<MpiPatient> page = service.getPatients("keyword", "ACTIVE", PageRequest.defaults());

        assertThat(page.total()).isZero();
        assertThat(page.items()).isEmpty();
        verify(repository, never()).findPatients(anyString(), anyString(), anyString(), anyInt(), anyInt());
    }

    @Test
    void shouldReturnPatientsPageWhenFound() {
        MpiPatient patient = new MpiPatient(
            1L, "mpi-1", TENANT_ID, "张*三", "M", 35, "1234", 0, "ACTIVE",
            null, Instant.now(), ACTOR, Instant.now(), ACTOR
        );

        when(repository.countPatients(TENANT_ID, "张", "ACTIVE")).thenReturn(1L);
        when(repository.findPatients(TENANT_ID, "张", "ACTIVE", 20, 0))
            .thenReturn(List.of(patient));

        PageResponse<MpiPatient> page = service.getPatients("张", "ACTIVE", PageRequest.defaults());

        assertThat(page.total()).isEqualTo(1L);
        assertThat(page.items()).containsExactly(patient);
    }

    @Test
    void shouldReturnMergeReviewsPageWithoutMaterializingTenantStatusSnapshot() {
        MpiMergeReview review = MpiMergeReview.pending(
            "mrv-1", TENANT_ID, "mpi-source", "mpi-target", "HIGH", "身份证后四位不一致",
            ACTOR, Instant.now(), "trace-mpi-test"
        );
        PageRequest request = new PageRequest(2, 10, null);
        when(reviewRepository.countByTenantIdAndStatus(TENANT_ID, "PENDING")).thenReturn(21L);
        when(reviewRepository.pageByTenantIdAndStatus(TENANT_ID, "PENDING", 10, 10)).thenReturn(List.of(review));

        PageResponse<MpiMergeReview> page = service.getMergeReviews(" pending ", request);

        assertThat(page.items()).containsExactly(review);
        assertThat(page.page()).isEqualTo(2);
        assertThat(page.size()).isEqualTo(10);
        assertThat(page.total()).isEqualTo(21L);
        verify(reviewRepository, never()).findAllByTenantIdAndStatus(anyString(), anyString());
    }

    @Test
    void shouldGenerateActivePatientIdWithTenantScopeAndAudit() {
        when(repository.save(any(MpiPatient.class))).thenAnswer(invocation -> invocation.getArgument(0));

        MpiPatient patient = service.createPatient(new MpiPatientCreateRequest(
            " 李*四 ", " f ", 41, "9876"
        ));

        assertThat(patient.mpiId()).matches("mpi-[0-9A-HJKMNP-TV-Z]{26}");
        assertThat(patient.tenantId()).isEqualTo(TENANT_ID);
        assertThat(patient.maskedName()).isEqualTo("李*四");
        assertThat(patient.gender()).isEqualTo("F");
        assertThat(patient.age()).isEqualTo(41);
        assertThat(patient.idLast4()).isEqualTo("9876");
        assertThat(patient.mergedCount()).isZero();
        assertThat(patient.status()).isEqualTo("ACTIVE");
        assertThat(patient.createdBy()).isEqualTo(ACTOR);
        assertThat(patient.updatedBy()).isEqualTo(ACTOR);

        ArgumentCaptor<MpiPatient> patientCaptor = ArgumentCaptor.forClass(MpiPatient.class);
        verify(repository).save(patientCaptor.capture());
        assertThat(patientCaptor.getValue().id()).isNull();
        verify(recorder).record(
            eq("mpi_patient"),
            eq(patient.mpiId()),
            eq("NONE"),
            eq("ACTIVE"),
            eq("创建患者主索引"),
            isNull()
        );
    }

    @Test
    void shouldReturnStatsCorrectly() {
        when(repository.countActive(TENANT_ID)).thenReturn(10L);
        when(repository.countMerged(TENANT_ID)).thenReturn(2L);
        when(repository.averageAge(TENANT_ID)).thenReturn(42.5);
        when(patientPathways.countActiveByTenantId(TENANT_ID)).thenReturn(3L);

        MpiPatientRepository.GenderCount gcMale = mock(MpiPatientRepository.GenderCount.class);
        when(gcMale.getGender()).thenReturn("M");
        when(gcMale.getCnt()).thenReturn(6L);

        MpiPatientRepository.GenderCount gcFemale = mock(MpiPatientRepository.GenderCount.class);
        when(gcFemale.getGender()).thenReturn("F");
        when(gcFemale.getCnt()).thenReturn(4L);

        when(repository.countGender(TENANT_ID)).thenReturn(List.of(gcMale, gcFemale));

        MpiStatsResponse stats = service.getStats();

        assertThat(stats.activeCount()).isEqualTo(10L);
        assertThat(stats.mergedCount()).isEqualTo(2L);
        assertThat(stats.activePathwayCount()).isEqualTo(3L);
        assertThat(stats.averageAge()).isEqualTo(42.5);
        assertThat(stats.genderCounts()).containsEntry("M", 6L);
        assertThat(stats.genderCounts()).containsEntry("F", 4L);
        assertThat(stats.genderCounts()).containsEntry("UNKNOWN", 0L);
    }

    @Test
    void shouldReturnPatient360WithLatestContextSnapshotAndActivePathways() {
        MpiPatient patient = new MpiPatient(
            1L, "mpi-1", TENANT_ID, "张*三", "M", 35, "1234", 0, "ACTIVE",
            null, Instant.now(), ACTOR, Instant.now(), ACTOR
        );
        ContextSnapshotSummary summary = new ContextSnapshotSummary(
            "ctx-1", "mpi-1", "enc-1", ContextSnapshotStatus.ACTIVE, QualityStatus.PARTIAL, Instant.now());
        ContextSnapshotResponse snapshot = new ContextSnapshotResponse(
            "ctx-1", ContextSnapshotStatus.ACTIVE, null,
            "pkg-2026.06",
            QualityStatus.PARTIAL, List.of(), java.util.Map.of(), Instant.now(), "trace-context");
        PatientPathway activePathway = new PatientPathway(
            1L, "pp-1", TENANT_ID, "mpi-1", "enc-1", "pt-1", "ASSESS",
            PatientPathwayStatus.NODE_EXECUTING, Instant.now(), null, null, null, null,
            Instant.now(), ACTOR, Instant.now(), ACTOR, "trace-pathway");
        when(repository.findByTenantIdAndMpiId(TENANT_ID, "mpi-1")).thenReturn(Optional.of(patient));
        when(contextSnapshots.list(any(ContextSnapshotFilter.class), any(PageRequest.class)))
            .thenReturn(PageResponse.of(List.of(summary), new PageRequest(1, 1, "createdAt,desc"), 1));
        when(contextSnapshots.findById("ctx-1")).thenReturn(snapshot);
        when(patientPathways.countActiveByTenantIdAndPatientId(TENANT_ID, "mpi-1")).thenReturn(1L);
        when(patientPathways.findActiveByTenantIdAndPatientIdOrderByEnteredAtDesc(TENANT_ID, "mpi-1", 0, 5))
            .thenReturn(List.of(activePathway));

        MpiPatientDetailResponse response = service.patientDetail("mpi-1");

        assertThat(response.patient()).isEqualTo(patient);
        assertThat(response.latestContextSnapshot()).isEqualTo(summary);
        assertThat(response.contextSnapshot()).isEqualTo(snapshot);
        assertThat(response.activePathwayCount()).isEqualTo(1L);
        assertThat(response.activePathways()).containsExactly(activePathway);
        ArgumentCaptor<ContextSnapshotFilter> filterCaptor = ArgumentCaptor.forClass(ContextSnapshotFilter.class);
        ArgumentCaptor<PageRequest> pageCaptor = ArgumentCaptor.forClass(PageRequest.class);
        verify(contextSnapshots).list(filterCaptor.capture(), pageCaptor.capture());
        assertThat(filterCaptor.getValue().patientId()).isEqualTo("mpi-1");
        assertThat(pageCaptor.getValue().safePage()).isEqualTo(1);
        assertThat(pageCaptor.getValue().safeSize()).isEqualTo(1);
    }

    @Test
    void shouldThrowIfTenantMissingWhenGetStats() {
        RequestContext.clear();
        assertThatThrownBy(() -> service.getStats())
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("租户 ID 缺失");
    }

    @Test
    void shouldRejectMergeWhenIdsAreBlank() {
        assertThatThrownBy(() -> service.mergePatients("", "mpi-2"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("不能为空");

        assertThatThrownBy(() -> service.mergePatients("mpi-1", null))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("不能为空");
    }

    @Test
    void shouldRejectMergeWhenIdsAreEqual() {
        assertThatThrownBy(() -> service.mergePatients("mpi-1", "mpi-1"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("不能是同一个患者");
    }

    @Test
    void shouldRejectMergeWhenSourcePatientMissing() {
        when(repository.findByTenantIdAndMpiId(TENANT_ID, "mpi-source")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.mergePatients("mpi-source", "mpi-target"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("未找到源患者");
    }

    @Test
    void shouldRejectMergeWhenTargetPatientMissing() {
        MpiPatient source = new MpiPatient(
            1L, "mpi-source", TENANT_ID, "张*三", "M", 30, "1234", 0, "ACTIVE",
            null, Instant.now(), ACTOR, Instant.now(), ACTOR
        );
        when(repository.findByTenantIdAndMpiId(TENANT_ID, "mpi-source")).thenReturn(Optional.of(source));
        when(repository.findByTenantIdAndMpiId(TENANT_ID, "mpi-target")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.mergePatients("mpi-source", "mpi-target"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("未找到目标患者");
    }

    @Test
    void shouldRejectMergeWhenSourceIsNotActive() {
        MpiPatient source = new MpiPatient(
            1L, "mpi-source", TENANT_ID, "张*三", "M", 30, "1234", 0, "MERGED_INTO",
            "mpi-other", Instant.now(), ACTOR, Instant.now(), ACTOR
        );
        MpiPatient target = new MpiPatient(
            2L, "mpi-target", TENANT_ID, "张*四", "M", 32, "5678", 1, "ACTIVE",
            null, Instant.now(), ACTOR, Instant.now(), ACTOR
        );

        when(repository.findByTenantIdAndMpiId(TENANT_ID, "mpi-source")).thenReturn(Optional.of(source));
        when(repository.findByTenantIdAndMpiId(TENANT_ID, "mpi-target")).thenReturn(Optional.of(target));

        assertThatThrownBy(() -> service.mergePatients("mpi-source", "mpi-target"))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("源患者状态不是活跃状态");
    }

    @Test
    void shouldRejectMergeWhenTargetIsNotActive() {
        MpiPatient source = new MpiPatient(
            1L, "mpi-source", TENANT_ID, "张*三", "M", 30, "1234", 0, "ACTIVE",
            null, Instant.now(), ACTOR, Instant.now(), ACTOR
        );
        MpiPatient target = new MpiPatient(
            2L, "mpi-target", TENANT_ID, "张*四", "M", 32, "5678", 1, "MERGED_INTO",
            "mpi-other", Instant.now(), ACTOR, Instant.now(), ACTOR
        );

        when(repository.findByTenantIdAndMpiId(TENANT_ID, "mpi-source")).thenReturn(Optional.of(source));
        when(repository.findByTenantIdAndMpiId(TENANT_ID, "mpi-target")).thenReturn(Optional.of(target));

        assertThatThrownBy(() -> service.mergePatients("mpi-source", "mpi-target"))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("合并目标必须是活跃患者");
    }

    @Test
    void shouldMergeSuccessfullyAndRecordAudit() {
        MpiPatient source = new MpiPatient(
            1L, "mpi-source", TENANT_ID, "张*三", "M", 30, "1234", 2, "ACTIVE",
            null, Instant.now(), ACTOR, Instant.now(), ACTOR
        );
        MpiPatient target = new MpiPatient(
            2L, "mpi-target", TENANT_ID, "张*三", "M", 31, "1234", 1, "ACTIVE",
            null, Instant.now(), ACTOR, Instant.now(), ACTOR
        );

        when(repository.findByTenantIdAndMpiId(TENANT_ID, "mpi-source")).thenReturn(Optional.of(source));
        when(repository.findByTenantIdAndMpiId(TENANT_ID, "mpi-target")).thenReturn(Optional.of(target));

        MpiMergeResult result = service.mergePatients("mpi-source", "mpi-target");

        assertThat(result.status()).isEqualTo("MERGED");
        assertThat(result.reviewId()).isNull();
        // 验证源患者更新：状态变为 MERGED_INTO，指向目标 mpi-target
        ArgumentCaptor<MpiPatient> patientCaptor = ArgumentCaptor.forClass(MpiPatient.class);
        verify(repository, times(2)).save(patientCaptor.capture());

        List<MpiPatient> allSaved = patientCaptor.getAllValues();
        MpiPatient savedSource = allSaved.stream()
            .filter(p -> "mpi-source".equals(p.mpiId()))
            .findFirst()
            .orElseThrow(() -> new AssertionError("未捕获到 mpi-source 的保存操作"));
        MpiPatient savedTarget = allSaved.stream()
            .filter(p -> "mpi-target".equals(p.mpiId()))
            .findFirst()
            .orElseThrow(() -> new AssertionError("未捕获到 mpi-target 的保存操作"));

        assertThat(savedSource.status()).isEqualTo("MERGED_INTO");
        assertThat(savedSource.mergedIntoMpiId()).isEqualTo("mpi-target");
        // 目标患者 mergedCount = 目标原有 1 + 源原有 2 + 1 = 4
        assertThat(savedTarget.mergedCount()).isEqualTo(4);

        // 验证审计日志记录
        verify(recorder, times(1)).record(
            eq("mpi_patient"),
            eq("mpi-source"),
            eq("ACTIVE"),
            eq("MERGED_INTO"),
            eq("合并至目标患者主索引：mpi-target"),
            isNull()
        );
    }

    @Test
    void shouldRequireManualReviewBeforeHighRiskMergeAndPersistPendingReview() {
        MpiPatient source = new MpiPatient(
            1L, "mpi-source", TENANT_ID, "张*三", "M", 30, "1234", 0, "ACTIVE",
            null, Instant.now(), ACTOR, Instant.now(), ACTOR
        );
        MpiPatient target = new MpiPatient(
            2L, "mpi-target", TENANT_ID, "张*三", "M", 30, "5678", 0, "ACTIVE",
            null, Instant.now(), ACTOR, Instant.now(), ACTOR
        );
        when(repository.findByTenantIdAndMpiId(TENANT_ID, "mpi-source")).thenReturn(Optional.of(source));
        when(repository.findByTenantIdAndMpiId(TENANT_ID, "mpi-target")).thenReturn(Optional.of(target));
        when(reviewRepository.findByTenantIdAndSourceMpiIdAndTargetMpiId(TENANT_ID, "mpi-source", "mpi-target"))
            .thenReturn(Optional.empty());
        when(reviewRepository.save(any(MpiMergeReview.class))).thenAnswer(invocation -> invocation.getArgument(0));

        ApiException error = assertThrows(ApiException.class, () -> service.mergePatients("mpi-source", "mpi-target"));

        assertThat(error.errorCode()).isEqualTo(ErrorCode.MPI_MERGE_REQUIRES_REVIEW);
        ArgumentCaptor<MpiMergeReview> reviewCaptor = ArgumentCaptor.forClass(MpiMergeReview.class);
        verify(reviewRepository).save(reviewCaptor.capture());
        MpiMergeReview review = reviewCaptor.getValue();
        assertThat(review.status()).isEqualTo("PENDING");
        assertThat(review.riskLevel()).isEqualTo("HIGH");
        assertThat(review.sourceMpiId()).isEqualTo("mpi-source");
        assertThat(review.targetMpiId()).isEqualTo("mpi-target");
        assertThat(review.riskReason()).contains("身份证后四位不一致");
        verify(repository, never()).save(any(MpiPatient.class));
        verify(recorder, never()).record(anyString(), anyString(), anyString(), anyString(), anyString(), any());
    }

    @Test
    void shouldReusePendingReviewForRepeatedHighRiskMergeRequest() {
        MpiPatient source = new MpiPatient(
            1L, "mpi-source", TENANT_ID, "张*三", "M", 30, "1234", 0, "ACTIVE",
            null, Instant.now(), ACTOR, Instant.now(), ACTOR
        );
        MpiPatient target = new MpiPatient(
            2L, "mpi-target", TENANT_ID, "张*三", "M", 30, "5678", 0, "ACTIVE",
            null, Instant.now(), ACTOR, Instant.now(), ACTOR
        );
        MpiMergeReview existing = MpiMergeReview.pending(
            "mrv-existing", TENANT_ID, "mpi-source", "mpi-target", "HIGH", "身份证后四位不一致", ACTOR, Instant.now(), "trace-mpi-test"
        );
        when(repository.findByTenantIdAndMpiId(TENANT_ID, "mpi-source")).thenReturn(Optional.of(source));
        when(repository.findByTenantIdAndMpiId(TENANT_ID, "mpi-target")).thenReturn(Optional.of(target));
        when(reviewRepository.findByTenantIdAndSourceMpiIdAndTargetMpiId(TENANT_ID, "mpi-source", "mpi-target"))
            .thenReturn(Optional.of(existing));

        ApiException error = assertThrows(ApiException.class, () -> service.mergePatients("mpi-source", "mpi-target"));

        assertThat(error.errorCode()).isEqualTo(ErrorCode.MPI_MERGE_REQUIRES_REVIEW);
        verify(reviewRepository, never()).save(any(MpiMergeReview.class));
        verify(repository, never()).save(any(MpiPatient.class));
    }

    @Test
    void shouldConfirmPendingReviewThenMergeAndRecordAudit() {
        MpiMergeReview pending = MpiMergeReview.pending(
            "mrv-1", TENANT_ID, "mpi-source", "mpi-target", "HIGH", "身份证后四位不一致", ACTOR, Instant.now(), "trace-mpi-test"
        );
        MpiPatient source = new MpiPatient(
            1L, "mpi-source", TENANT_ID, "张*三", "M", 30, "1234", 2, "ACTIVE",
            null, Instant.now(), ACTOR, Instant.now(), ACTOR
        );
        MpiPatient target = new MpiPatient(
            2L, "mpi-target", TENANT_ID, "张*三", "M", 30, "5678", 1, "ACTIVE",
            null, Instant.now(), ACTOR, Instant.now(), ACTOR
        );
        when(reviewRepository.findByTenantIdAndReviewId(TENANT_ID, "mrv-1")).thenReturn(Optional.of(pending));
        when(repository.findByTenantIdAndMpiId(TENANT_ID, "mpi-source")).thenReturn(Optional.of(source));
        when(repository.findByTenantIdAndMpiId(TENANT_ID, "mpi-target")).thenReturn(Optional.of(target));
        when(reviewRepository.save(any(MpiMergeReview.class))).thenAnswer(invocation -> invocation.getArgument(0));

        MpiMergeResult result = service.confirmMergeReview(
            "mrv-1",
            new MpiMergeReviewConfirmRequest("人工核验 HIS 与身份证原件一致")
        );

        assertThat(result.status()).isEqualTo("MERGED");
        assertThat(result.reviewId()).isEqualTo("mrv-1");
        ArgumentCaptor<MpiMergeReview> reviewCaptor = ArgumentCaptor.forClass(MpiMergeReview.class);
        verify(reviewRepository).save(reviewCaptor.capture());
        assertThat(reviewCaptor.getValue().status()).isEqualTo("CONFIRMED");
        assertThat(reviewCaptor.getValue().reviewReason()).isEqualTo("人工核验 HIS 与身份证原件一致");
        verify(repository, times(2)).save(any(MpiPatient.class));
        verify(recorder).record(
            eq("mpi_patient"),
            eq("mpi-source"),
            eq("ACTIVE"),
            eq("MERGED_INTO"),
            eq("合并至目标患者主索引：mpi-target"),
            isNull()
        );
    }

    @Test
    void shouldSplitMergedPatientAndRecordAudit() {
        MpiPatient source = new MpiPatient(
            1L, "mpi-source", TENANT_ID, "张*三", "M", 30, "1234", 2, "MERGED_INTO",
            "mpi-target", Instant.now(), ACTOR, Instant.now(), ACTOR
        );
        MpiPatient target = new MpiPatient(
            2L, "mpi-target", TENANT_ID, "张*三", "M", 31, "1234", 5, "ACTIVE",
            null, Instant.now(), ACTOR, Instant.now(), ACTOR
        );
        when(repository.findByTenantIdAndMpiId(TENANT_ID, "mpi-source")).thenReturn(Optional.of(source));
        when(repository.findByTenantIdAndMpiId(TENANT_ID, "mpi-target")).thenReturn(Optional.of(target));

        MpiSplitResult result = service.splitMergedPatient(
            "mpi-source",
            new MpiSplitRequest("人工核查后确认不是同一患者")
        );

        assertThat(result.status()).isEqualTo("SPLIT");
        assertThat(result.sourceMpiId()).isEqualTo("mpi-source");
        assertThat(result.targetMpiId()).isEqualTo("mpi-target");
        ArgumentCaptor<MpiPatient> patientCaptor = ArgumentCaptor.forClass(MpiPatient.class);
        verify(repository, times(2)).save(patientCaptor.capture());
        List<MpiPatient> saved = patientCaptor.getAllValues();
        MpiPatient savedSource = saved.stream()
            .filter(p -> "mpi-source".equals(p.mpiId()))
            .findFirst()
            .orElseThrow();
        MpiPatient savedTarget = saved.stream()
            .filter(p -> "mpi-target".equals(p.mpiId()))
            .findFirst()
            .orElseThrow();
        assertThat(savedSource.status()).isEqualTo("ACTIVE");
        assertThat(savedSource.mergedIntoMpiId()).isNull();
        assertThat(savedTarget.mergedCount()).isEqualTo(2);
        verify(recorder).record(
            eq("mpi_patient"),
            eq("mpi-source"),
            eq("MERGED_INTO"),
            eq("ACTIVE"),
            eq("拆分患者主索引合并关系：人工核查后确认不是同一患者"),
            isNull()
        );
    }

    @Test
    void shouldRejectSplitWhenPatientIsNotMergedInto() {
        MpiPatient source = new MpiPatient(
            1L, "mpi-source", TENANT_ID, "张*三", "M", 30, "1234", 0, "ACTIVE",
            null, Instant.now(), ACTOR, Instant.now(), ACTOR
        );
        when(repository.findByTenantIdAndMpiId(TENANT_ID, "mpi-source")).thenReturn(Optional.of(source));

        assertThatThrownBy(() -> service.splitMergedPatient(
            "mpi-source",
            new MpiSplitRequest("人工核查")
        ))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("不是已归并状态");

        verify(repository, never()).save(any(MpiPatient.class));
    }
}
