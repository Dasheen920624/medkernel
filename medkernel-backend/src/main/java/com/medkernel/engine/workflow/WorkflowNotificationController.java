package com.medkernel.engine.workflow;

import com.medkernel.shared.api.ApiResult;
import com.medkernel.shared.api.PageRequest;
import com.medkernel.shared.api.PageResponse;
import com.medkernel.shared.datascope.DataScope;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 临床通知中心控制器。
 */
@RestController
@RequestMapping("/api/v1/engine/notifications")
@DataScope(requireTenant = true)
public class WorkflowNotificationController {

    private final WorkflowCollaborationService service;

    public WorkflowNotificationController(WorkflowCollaborationService service) {
        this.service = service;
    }

    /**
     * 分页查询统一通知。
     */
    @GetMapping
    @PreAuthorize("@perm.has('notification.read')")
    public ApiResult<PageResponse<WorkflowNotificationResponse>> notifications(
            @RequestParam(required = false) WorkflowNotificationStatus status,
            @RequestParam(required = false) WorkflowNotificationLevel level,
            @RequestParam(required = false) String recipientId,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String sort) {
        return ApiResult.ok(service.listNotifications(
            new WorkflowNotificationFilter(status, level, recipientId),
            new PageRequest(page, size, sort)));
    }

    /**
     * 标记统一通知已读。
     */
    @PostMapping("/{notificationId}/read")
    @PreAuthorize("@perm.has('notification.write')")
    public ApiResult<WorkflowNotificationResponse> readNotification(@PathVariable String notificationId) {
        return ApiResult.ok(service.markNotificationRead(notificationId));
    }
}
