package com.medkernel.engine.mpi;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.medkernel.shared.api.PageRequest;
import com.medkernel.shared.api.PageResponse;
import com.medkernel.shared.api.error.ApiException;
import com.medkernel.shared.api.error.ErrorCode;
import com.medkernel.shared.context.RequestContext;
import com.medkernel.shared.observability.StateTransitionRecorder;

/**
 * 患者主索引（MPI）业务服务逻辑层。
 *
 * <p>提供多租户强隔离下的患者检索、指标汇总以及跨院区患者身份合并。
 * 合并操作在同一事务中进行状态变迁，并触发底座状态机历史记录器。
 */
@Service
public class MpiService {

    private final MpiPatientRepository repository;
    private final MpiMergeReviewRepository reviewRepository;
    private final StateTransitionRecorder stateTransitionRecorder;

    public MpiService(MpiPatientRepository repository,
                      MpiMergeReviewRepository reviewRepository,
                      StateTransitionRecorder stateTransitionRecorder) {
        this.repository = repository;
        this.reviewRepository = reviewRepository;
        this.stateTransitionRecorder = stateTransitionRecorder;
    }

    /**
     * 分页查询当前租户下的患者主索引列表。
     *
     * @param keyword 姓名或主索引ID检索关键字（模糊查询）
     * @param status  主索引状态（ACTIVE / MERGED_INTO）
     * @param pageReq 分页请求参数
     * @return 分页包装的患者列表
     */
    public PageResponse<MpiPatient> getPatients(String keyword, String status, PageRequest pageReq) {
        String tenantId = RequestContext.currentOrgScope().tenantId();
        if (tenantId == null || tenantId.isBlank()) {
            throw new IllegalStateException("当前请求上下文中租户 ID 缺失，拒绝查询");
        }

        PageRequest req = pageReq != null ? pageReq : PageRequest.defaults();
        long total = repository.countPatients(tenantId, keyword, status);
        if (total == 0) {
            return PageResponse.empty(req);
        }

        List<MpiPatient> items = repository.findPatients(tenantId, keyword, status, req.safeSize(), req.offset());
        return PageResponse.of(items, req, total);
    }

    /**
     * 获取当前租户下的患者主索引驾驶舱指标统计。
     *
     * @return MPI 统计指标详情
     */
    public MpiStatsResponse getStats() {
        String tenantId = RequestContext.currentOrgScope().tenantId();
        if (tenantId == null || tenantId.isBlank()) {
            throw new IllegalStateException("当前请求上下文中租户 ID 缺失，拒绝查询");
        }

        long activeCount = repository.countActive(tenantId);
        long mergedCount = repository.countMerged(tenantId);
        Double avgAgeVal = repository.averageAge(tenantId);
        double averageAge = avgAgeVal != null ? avgAgeVal : 0.0;

        List<MpiPatientRepository.GenderCount> genderCounts = repository.countGender(tenantId);
        Map<String, Long> genderMap = new HashMap<>();
        // 初始填充，保证前端能拿到完整性别分类
        genderMap.put("M", 0L);
        genderMap.put("F", 0L);
        genderMap.put("UNKNOWN", 0L);

        if (genderCounts != null) {
            for (MpiPatientRepository.GenderCount gc : genderCounts) {
                String gender = gc.getGender();
                if (gender == null || gender.isBlank()) {
                    genderMap.put("UNKNOWN", genderMap.getOrDefault("UNKNOWN", 0L) + gc.getCnt());
                } else {
                    genderMap.put(gender.toUpperCase(), gc.getCnt());
                }
            }
        }

        return new MpiStatsResponse(activeCount, mergedCount, averageAge, genderMap);
    }

    /**
     * 合并重复患者主索引。
     *
     * <p>在同一个事务中，将源 MPI 患者的状态变迁为 MERGED_INTO，设置 mergedIntoMpiId，
     * 并累加目标患者的 mergedCount。同时触发 StateTransitionRecorder 记录审计日志。
     *
     * @param sourceMpiId 被合并的源主索引 ID
     * @param targetMpiId 合并入的目标主索引 ID
     */
    @Transactional(noRollbackFor = ApiException.class)
    public MpiMergeResult mergePatients(String sourceMpiId, String targetMpiId) {
        String tenantId = requireTenantId();
        validateMergeIds(sourceMpiId, targetMpiId);
        MpiPatient sourcePatient = requirePatient(tenantId, sourceMpiId, "源患者");
        MpiPatient targetPatient = requirePatient(tenantId, targetMpiId, "目标患者");
        requireActive(sourcePatient, "源患者状态不是活跃状态（ACTIVE），不能进行合并操作");
        requireActive(targetPatient, "目标患者状态不是活跃状态（ACTIVE），合并目标必须是活跃患者");

        String riskReason = highRiskReason(sourcePatient, targetPatient);
        if (riskReason != null) {
            MpiMergeReview review = requireManualReview(tenantId, sourceMpiId, targetMpiId, riskReason);
            throw new ApiException(
                ErrorCode.MPI_MERGE_REQUIRES_REVIEW,
                "高危患者主索引合并需要人工确认，审核单：" + review.reviewId() + "；原因：" + review.riskReason()
            );
        }

        return mergeValidatedPatients(sourcePatient, targetPatient, null, null);
    }

    /**
     * 人工确认高危合并审核单后执行真实合并。
     */
    @Transactional
    public MpiMergeResult confirmMergeReview(String reviewId, MpiMergeReviewConfirmRequest request) {
        String tenantId = requireTenantId();
        if (reviewId == null || reviewId.isBlank()) {
            throw new IllegalArgumentException("MPI 合并审核单 ID 不能为空");
        }
        String reviewReason = request == null ? null : request.reviewReason();
        if (reviewReason == null || reviewReason.isBlank()) {
            throw new IllegalArgumentException("人工确认理由不能为空");
        }

        MpiMergeReview review = reviewRepository.findByTenantIdAndReviewId(tenantId, reviewId)
            .orElseThrow(() -> ApiException.notFound("MPI 合并审核单"));
        if (!"PENDING".equals(review.status())) {
            throw new ApiException(ErrorCode.CONFLICT, "只有 PENDING 状态的 MPI 合并审核单可以确认");
        }

        MpiPatient sourcePatient = requirePatient(tenantId, review.sourceMpiId(), "源患者");
        MpiPatient targetPatient = requirePatient(tenantId, review.targetMpiId(), "目标患者");
        requireActive(sourcePatient, "源患者状态不是活跃状态（ACTIVE），不能进行合并操作");
        requireActive(targetPatient, "目标患者状态不是活跃状态（ACTIVE），合并目标必须是活跃患者");

        String actor = RequestContext.currentUserId().orElse("system");
        Instant now = Instant.now();
        MpiMergeResult result = mergeValidatedPatients(sourcePatient, targetPatient, review.reviewId(), review.riskLevel());
        reviewRepository.save(review.confirmed(actor, reviewReason.trim(), now));
        return result;
    }

    @Transactional(readOnly = true)
    public List<MpiMergeReview> getMergeReviews(String status) {
        String tenantId = requireTenantId();
        if (status == null || status.isBlank()) {
            return reviewRepository.findAllByTenantIdAndStatus(tenantId, "PENDING");
        }
        return reviewRepository.findAllByTenantIdAndStatus(tenantId, status.trim().toUpperCase());
    }

    private MpiMergeResult mergeValidatedPatients(MpiPatient sourcePatient,
                                                  MpiPatient targetPatient,
                                                  String reviewId,
                                                  String riskLevel) {
        String targetMpiId = targetPatient.mpiId();
        String sourceMpiId = sourcePatient.mpiId();

        String actor = RequestContext.currentUserId().orElse("system");
        Instant now = Instant.now();

        // 1. 更新源患者状态为 MERGED_INTO，并指向目标患者
        MpiPatient updatedSource = new MpiPatient(
            sourcePatient.id(),
            sourcePatient.mpiId(),
            sourcePatient.tenantId(),
            sourcePatient.maskedName(),
            sourcePatient.gender(),
            sourcePatient.age(),
            sourcePatient.idLast4(),
            sourcePatient.mergedCount(),
            "MERGED_INTO",
            targetMpiId,
            sourcePatient.createdAt(),
            sourcePatient.createdBy(),
            now,
            actor
        );

        // 2. 更新目标患者的被合并数（累加源患者的被合并数以及源患者本身）
        int newMergedCount = targetPatient.mergedCount() + sourcePatient.mergedCount() + 1;
        MpiPatient updatedTarget = new MpiPatient(
            targetPatient.id(),
            targetPatient.mpiId(),
            targetPatient.tenantId(),
            targetPatient.maskedName(),
            targetPatient.gender(),
            targetPatient.age(),
            targetPatient.idLast4(),
            newMergedCount,
            targetPatient.status(),
            targetPatient.mergedIntoMpiId(),
            targetPatient.createdAt(),
            targetPatient.createdBy(),
            now,
            actor
        );

        repository.save(updatedSource);
        repository.save(updatedTarget);

        // 3. 同事务触发可观测性审计
        stateTransitionRecorder.record(
            "mpi_patient",
            sourceMpiId,
            "ACTIVE",
            "MERGED_INTO",
            "合并至目标患者主索引：" + targetMpiId,
            null
        );

        return new MpiMergeResult("MERGED", sourceMpiId, targetMpiId, reviewId, riskLevel, "患者主索引已合并");
    }

    private String requireTenantId() {
        String tenantId = RequestContext.currentOrgScope().tenantId();
        if (tenantId == null || tenantId.isBlank()) {
            throw new IllegalStateException("当前请求上下文中租户 ID 缺失，拒绝操作");
        }
        return tenantId;
    }

    private void validateMergeIds(String sourceMpiId, String targetMpiId) {
        if (sourceMpiId == null || sourceMpiId.isBlank() || targetMpiId == null || targetMpiId.isBlank()) {
            throw new IllegalArgumentException("源患者主索引 ID 或目标患者主索引 ID 不能为空");
        }

        if (sourceMpiId.equals(targetMpiId)) {
            throw new IllegalArgumentException("源患者与目标患者不能是同一个患者，无法合并");
        }
    }

    private MpiPatient requirePatient(String tenantId, String mpiId, String label) {
        return repository.findByTenantIdAndMpiId(tenantId, mpiId)
            .orElseThrow(() -> new IllegalArgumentException("未找到" + label + "主索引记录，ID: " + mpiId));
    }

    private void requireActive(MpiPatient patient, String message) {
        if (!"ACTIVE".equals(patient.status())) {
            throw new IllegalStateException(message);
        }
    }

    private String highRiskReason(MpiPatient sourcePatient, MpiPatient targetPatient) {
        List<String> reasons = new ArrayList<>();
        if (!sameText(sourcePatient.idLast4(), targetPatient.idLast4())) {
            reasons.add("身份证后四位不一致");
        }
        if (!sameText(sourcePatient.gender(), targetPatient.gender())) {
            reasons.add("性别不一致");
        }
        if (sourcePatient.age() == null || targetPatient.age() == null
                || Math.abs(sourcePatient.age() - targetPatient.age()) > 1) {
            reasons.add("年龄差异超过安全阈值");
        }
        return reasons.isEmpty() ? null : String.join("；", reasons);
    }

    private boolean sameText(String first, String second) {
        String left = first == null ? "" : first.trim();
        String right = second == null ? "" : second.trim();
        return !left.isBlank() && left.equalsIgnoreCase(right);
    }

    private MpiMergeReview requireManualReview(String tenantId,
                                               String sourceMpiId,
                                               String targetMpiId,
                                               String riskReason) {
        return reviewRepository.findByTenantIdAndSourceMpiIdAndTargetMpiId(tenantId, sourceMpiId, targetMpiId)
            .orElseGet(() -> reviewRepository.save(MpiMergeReview.pending(
                null,
                tenantId,
                sourceMpiId,
                targetMpiId,
                "HIGH",
                riskReason,
                RequestContext.currentUserId().orElse("system"),
                Instant.now(),
                RequestContext.currentTraceId()
            )));
    }
}
