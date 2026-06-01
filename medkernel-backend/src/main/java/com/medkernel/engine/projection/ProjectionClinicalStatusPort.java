package com.medkernel.engine.projection;

import org.springframework.stereotype.Component;

import com.medkernel.engine.clinical.model.ClinicalProjectionStatus;
import com.medkernel.engine.clinical.model.ClinicalProjectionStatusPort;

/**
 * 基于投影运行开关与快照数量给出标准临床对象的图投影诚实状态。
 */
@Component
public class ProjectionClinicalStatusPort implements ClinicalProjectionStatusPort {

    private final ProjectionRuntimePolicy policy;
    private final ProjectionSnapshotRepository snapshots;

    public ProjectionClinicalStatusPort(ProjectionRuntimePolicy policy, ProjectionSnapshotRepository snapshots) {
        this.policy = policy;
        this.snapshots = snapshots;
    }

    @Override
    public ClinicalProjectionStatus status(String tenantId) {
        if (!policy.graphProjectionEnabled()) {
            return ClinicalProjectionStatus.NOT_SYNCED;
        }
        long snapshotCount = snapshots.countByTenantIdAndTargetType(tenantId, ProjectionTargetType.CLINICAL_GRAPH);
        return snapshotCount > 0 ? ClinicalProjectionStatus.UP : ClinicalProjectionStatus.NOT_SYNCED;
    }
}
