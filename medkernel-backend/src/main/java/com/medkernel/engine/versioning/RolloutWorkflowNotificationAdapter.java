package com.medkernel.engine.versioning;

import java.time.Clock;
import java.time.Instant;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.medkernel.engine.workflow.WorkflowNotification;
import com.medkernel.engine.workflow.WorkflowNotificationLevel;
import com.medkernel.engine.workflow.WorkflowNotificationRepository;
import com.medkernel.engine.workflow.WorkflowNotificationSourceType;
import com.medkernel.engine.workflow.WorkflowNotificationStatus;
import com.medkernel.shared.ids.Ulid;

/**
 * 灰度暂停到统一通知中心的适配器。
 */
@Component
public class RolloutWorkflowNotificationAdapter implements RolloutPauseNotifier {

    private static final String SYSTEM_ACTOR = "release-rollout";

    private final WorkflowNotificationRepository notifications;
    private final Clock clock;

    @Autowired
    public RolloutWorkflowNotificationAdapter(WorkflowNotificationRepository notifications) {
        this(notifications, Clock.systemUTC());
    }

    RolloutWorkflowNotificationAdapter(
            WorkflowNotificationRepository notifications,
            Clock clock) {
        this.notifications = notifications;
        this.clock = clock;
    }

    @Override
    public void notifyPaused(VersionReleasePlan plan, String reason) {
        String dedupeKey = "rollout-paused:" + plan.planId() + ":" + plan.rolloutStageIndex();
        if (notifications.findByTenantIdAndDedupeKey(plan.tenantId(), dedupeKey).isPresent()) {
            return;
        }
        Instant now = clock.instant();
        notifications.save(new WorkflowNotification(
            null,
            "ntf-" + Ulid.newUlid(),
            plan.tenantId(),
            WorkflowNotificationSourceType.RELEASE_ROLLOUT,
            plan.planId(),
            dedupeKey,
            "灰度放量已暂停",
            plan.assetIdentity() + " 在第 " + (plan.rolloutStageIndex() + 1)
                + " 批观测中触发自动护栏：" + reason,
            WorkflowNotificationLevel.HIGH,
            WorkflowNotificationStatus.UNREAD,
            null,
            "ORGANIZATION_ADMIN",
            null,
            null,
            "/tenant/packages?releasePlanId=" + plan.planId(),
            null,
            null,
            plan.traceId(),
            now,
            SYSTEM_ACTOR,
            now,
            SYSTEM_ACTOR
        ));
    }
}
