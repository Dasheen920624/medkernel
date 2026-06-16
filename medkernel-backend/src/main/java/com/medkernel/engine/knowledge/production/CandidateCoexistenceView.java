package com.medkernel.engine.knowledge.production;

import java.time.Instant;

import com.medkernel.engine.knowledge.CandidateClassification;
import com.medkernel.engine.knowledge.CandidateClassificationType;
import com.medkernel.engine.knowledge.CandidateReviewStatus;
import com.medkernel.engine.knowledge.GradeEvidenceQuality;
import com.medkernel.engine.knowledge.GradeRecommendationStrength;
import com.medkernel.engine.knowledge.KnowledgeAssetVersion;
import com.medkernel.engine.knowledge.KnowledgeRiskLevel;
import com.medkernel.engine.knowledge.KnowledgeVersionStatus;
import com.medkernel.engine.knowledge.SourceAuthorityLevel;

/**
 * 待审候选与现行权威版本的共存视图（AIK-STD-09/11）。
 *
 * <p>只读呈现审核前的安全边界：候选版本仅供对照，不参与临床执行；现行 {@code ACTIVE} 仍是唯一可执行版本。
 */
public record CandidateCoexistenceView(
    String candidateRef,
    Long identityId,
    VersionSnapshot candidateVersion,
    VersionSnapshot activeVersion,
    CandidateClassificationType classification,
    CandidateReviewStatus reviewStatus,
    String diffSummary,
    ProductionLineage productionLineage,
    boolean candidateExecutable,
    boolean activeExecutable,
    String approvalOutcome,
    String replacementReminder,
    String safetyNotice
) {

    public CandidateCoexistenceView {
        candidateExecutable = false;
        safetyNotice = safetyNotice == null || safetyNotice.isBlank()
            ? "候选处于待审共存态，仅供人工对照审核，不参与临床执行"
            : safetyNotice;
    }

    /** 版本摘要，避免审核台为展示共存关系直接暴露完整资产正文。 */
    public record VersionSnapshot(
        Long versionId,
        String versionNo,
        KnowledgeVersionStatus status,
        KnowledgeRiskLevel riskLevel,
        SourceAuthorityLevel authorityLevel,
        GradeEvidenceQuality gradeQuality,
        GradeRecommendationStrength gradeStrength,
        String contentHash,
        String organizationScope,
        String applicableScope,
        Instant activatedAt,
        Instant updatedAt
    ) {
        public static VersionSnapshot from(KnowledgeAssetVersion version) {
            if (version == null) {
                return null;
            }
            return new VersionSnapshot(
                version.id(),
                version.versionNo(),
                version.status(),
                version.riskLevel(),
                version.authorityLevel(),
                version.gradeQuality(),
                version.gradeStrength(),
                version.contentHash(),
                version.effectiveOrganizationScope(),
                version.effectiveApplicableScope(),
                version.activatedAt(),
                version.updatedAt());
        }
    }

    /** 生产血缘摘要；缺失时返回 null，不臆造候选来源。 */
    public record ProductionLineage(
        String jobCode,
        String assetIdentity,
        KnowledgeProducer producer,
        TargetPipeline targetPipeline,
        KnowledgeDomain domain,
        String modelStrategy,
        KnowledgeRiskLevel riskLevel,
        Instant createdAt
    ) {
        public static ProductionLineage from(KnowledgeProductionCandidate row, KnowledgeProductionJob job) {
            if (row == null || job == null) {
                return null;
            }
            return new ProductionLineage(
                row.jobCode(),
                row.assetIdentity(),
                job.producer(),
                job.targetPipeline(),
                job.domain(),
                job.modelStrategy(),
                row.riskLevel(),
                row.createdAt());
        }
    }

    public static CandidateCoexistenceView of(String candidateRef, KnowledgeAssetVersion candidate,
                                              KnowledgeAssetVersion active,
                                              CandidateClassification classification,
                                              ProductionLineage lineage) {
        boolean hasActive = active != null && active.status() == KnowledgeVersionStatus.ACTIVE;
        String approvalOutcome = hasActive ? "APPROVE_REPLACE_ACTIVE" : "APPROVE_ACTIVATE_FIRST_VERSION";
        return new CandidateCoexistenceView(
            candidateRef,
            candidate.identityId(),
            VersionSnapshot.from(candidate),
            VersionSnapshot.from(active),
            classification == null ? null : classification.classification(),
            classification == null ? null : classification.reviewStatus(),
            classification == null ? null : classification.diffSummary(),
            lineage,
            false,
            hasActive,
            approvalOutcome,
            replacementReminder(candidate, active, hasActive),
            "候选处于 PENDING_REPLACEMENT_REVIEW，仅供审核台对照，不参与临床执行；审核通过才会进入 SYS-08 原子激活/替换流程");
    }

    private static String replacementReminder(KnowledgeAssetVersion candidate, KnowledgeAssetVersion active,
                                              boolean hasActive) {
        if (!hasActive) {
            return "审核通过后将触发 SYS-08 首次激活；审核前无可执行权威版本，候选 "
                + candidate.versionNo() + " 不得直接用于临床执行";
        }
        return "审核通过后将触发 SYS-08 原子替换：候选 " + candidate.versionNo()
            + " 变为 ACTIVE，现行 " + active.versionNo()
            + " 变为 SUPERSEDED，并刷新投影与同步任务；审核前仍由现行 ACTIVE="
            + active.versionNo() + " 执行";
    }
}
