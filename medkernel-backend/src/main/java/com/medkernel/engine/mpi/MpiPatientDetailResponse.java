package com.medkernel.engine.mpi;

import java.util.List;

import com.medkernel.engine.context.ContextSnapshotResponse;
import com.medkernel.engine.context.ContextSnapshotSummary;
import com.medkernel.engine.pathway.PatientPathway;

/**
 * 患者 360 详情响应。
 *
 * <p>聚合当前租户下的患者主索引、最新标准上下文快照和活跃患者路径实例，
 * 供临床页面查看患者全景，不在前端构造假患者或假在径状态。
 *
 * @param patient 患者主索引事实
 * @param latestContextSnapshot 最新标准上下文快照摘要；无快照时为 {@code null}
 * @param contextSnapshot 最新标准上下文快照详情；无快照时为 {@code null}
 * @param activePathwayCount 当前患者活跃路径实例数
 * @param activePathways 当前患者最近活跃路径实例，按入径时间倒序返回
 * @param traceId 当前请求 traceId
 */
public record MpiPatientDetailResponse(
    MpiPatient patient,
    ContextSnapshotSummary latestContextSnapshot,
    ContextSnapshotResponse contextSnapshot,
    long activePathwayCount,
    List<PatientPathway> activePathways,
    String traceId
) {

    public MpiPatientDetailResponse {
        activePathways = activePathways == null ? List.of() : List.copyOf(activePathways);
    }
}
