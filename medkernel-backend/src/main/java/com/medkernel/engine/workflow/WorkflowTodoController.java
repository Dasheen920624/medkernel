package com.medkernel.engine.workflow;

import com.medkernel.shared.api.ApiResult;
import com.medkernel.shared.api.PageRequest;
import com.medkernel.shared.api.PageResponse;
import com.medkernel.shared.datascope.DataScope;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 临床协同待办控制器。
 */
@RestController
@RequestMapping("/api/v1/engine/workflow/todos")
@DataScope(requireTenant = true)
public class WorkflowTodoController {

    private final WorkflowCollaborationService service;

    public WorkflowTodoController(WorkflowCollaborationService service) {
        this.service = service;
    }

    /**
     * 分页查询统一协同待办。
     */
    @GetMapping
    @PreAuthorize("@perm.has('workflow.read')")
    public ApiResult<PageResponse<WorkflowTodoResponse>> todos(
            @RequestParam(required = false) WorkflowTodoStatus status,
            @RequestParam(required = false) WorkflowPriority priority,
            @RequestParam(required = false) WorkflowTodoSourceType sourceType,
            @RequestParam(required = false) String assigneeId,
            @RequestParam(required = false) String patientId,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String sort) {
        return ApiResult.ok(service.listTodos(
            new WorkflowTodoFilter(status, priority, sourceType, assigneeId, patientId),
            new PageRequest(page, size, sort)));
    }

    /**
     * 完成统一协同待办。
     */
    @PostMapping("/{todoId}/complete")
    @PreAuthorize("@perm.has('workflow.write')")
    public ApiResult<WorkflowTodoResponse> completeTodo(
            @PathVariable String todoId,
            @Valid @RequestBody WorkflowTodoCompleteRequest request) {
        return ApiResult.ok(service.completeTodo(todoId, request));
    }

    /**
     * 转交统一协同待办。
     */
    @PostMapping("/{todoId}/transfer")
    @PreAuthorize("@perm.has('workflow.write')")
    public ApiResult<WorkflowTodoResponse> transferTodo(
            @PathVariable String todoId,
            @Valid @RequestBody WorkflowTodoTransferRequest request) {
        return ApiResult.ok(service.transferTodo(todoId, request));
    }
}
