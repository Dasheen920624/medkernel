package com.medkernel.engine.workflow;

import com.medkernel.engine.pathway.PathwayNodeWorklistCommand;
import com.medkernel.engine.pathway.PathwayNodeWorklistCompletionCommand;
import com.medkernel.engine.pathway.PathwayWorklistPort;
import org.springframework.stereotype.Component;

/**
 * 路径节点工作清单适配器。
 *
 * <p>路径域通过端口声明节点待办事实，协同域统一负责待办、通知与审计。
 */
@Component
public class PathwayWorkflowWorklistAdapter implements PathwayWorklistPort {

    private final WorkflowCollaborationService workflow;

    public PathwayWorkflowWorklistAdapter(WorkflowCollaborationService workflow) {
        this.workflow = workflow;
    }

    @Override
    public void openNodeTodo(PathwayNodeWorklistCommand command) {
        workflow.openPathwayNodeTodo(command);
    }

    @Override
    public void completeNodeTodo(PathwayNodeWorklistCompletionCommand command) {
        workflow.completePathwayNodeTodo(command);
    }
}
