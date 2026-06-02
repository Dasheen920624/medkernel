package com.medkernel.engine.followup;

import com.medkernel.engine.pathway.PathwayFollowupHandoffCommand;
import com.medkernel.engine.pathway.PathwayFollowupHandoffPort;
import com.medkernel.engine.pathway.PathwayFollowupHandoffResult;
import com.medkernel.shared.context.RequestContext;
import org.springframework.stereotype.Component;

/**
 * 路径结径到随访计划的域适配器。
 */
@Component
public class FollowupPathwayHandoffAdapter implements PathwayFollowupHandoffPort {

    private final FollowupEngineService followupService;

    public FollowupPathwayHandoffAdapter(FollowupEngineService followupService) {
        this.followupService = followupService;
    }

    @Override
    public PathwayFollowupHandoffResult handoff(PathwayFollowupHandoffCommand command) {
        FollowupPlanDetailResponse response = followupService.generatePlan(new FollowupPlanGenerateRequest(
            command.patientId(),
            command.encounterId(),
            command.patientPathwayId(),
            command.diseaseCode(),
            command.riskLevel(),
            command.taskTypes()));
        return new PathwayFollowupHandoffResult(
            response.planId(), response.tasks().size(), response.status().name(),
            RequestContext.currentTraceId());
    }
}
