package com.medkernel.shared.runtime.task;

import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.medkernel.shared.api.ApiResult;

/**
 * SYS-05 运行任务控制器。
 */
@RestController
@RequestMapping("/api/v1/system/tasks")
public class RuntimeTaskController {

    private final RuntimeTaskService service;

    public RuntimeTaskController(RuntimeTaskService service) {
        this.service = service;
    }

    /**
     * 提交在线、异步、批量或离线运行任务。
     *
     * @param request 任务请求
     * @return 当前任务状态
     */
    @PostMapping
    @PreAuthorize("@perm.has('system.manage')")
    public ApiResult<RuntimeTaskResponse> submit(@Valid @RequestBody RuntimeTaskSubmitRequest request) {
        return ApiResult.ok(service.submit(request));
    }

    /**
     * 查询任务状态。
     *
     * @param taskId 任务 ID
     * @return 当前任务状态
     */
    @GetMapping("/{taskId}")
    @PreAuthorize("@perm.has('system.read')")
    public ApiResult<RuntimeTaskResponse> get(@PathVariable String taskId) {
        return ApiResult.ok(service.getTask(taskId));
    }

    /**
     * 人工重试失败任务。
     *
     * @param taskId 任务 ID
     * @return 重试后的任务状态
     */
    @PostMapping("/{taskId}/retry")
    @PreAuthorize("@perm.has('system.manage')")
    public ApiResult<RuntimeTaskResponse> retry(@PathVariable String taskId) {
        return ApiResult.ok(service.retryTask(taskId));
    }

    /**
     * 人工回放死信任务。
     *
     * @param deadLetterId 死信 ID
     * @return 新回放任务状态
     */
    @PostMapping("/dead-letters/{deadLetterId}/replay")
    @PreAuthorize("@perm.has('system.manage')")
    public ApiResult<RuntimeTaskResponse> replayDeadLetter(@PathVariable String deadLetterId) {
        return ApiResult.ok(service.replayDeadLetter(deadLetterId));
    }
}
