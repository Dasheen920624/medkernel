package com.medkernel.engine.projection;

/**
 * 可选外部投影执行器端口，Dify 只能实现该端口执行任务，不能保存权威业务数据。
 */
@FunctionalInterface
public interface ProjectionExecutionPort {

    ProjectionExecutionResult execute(ProjectionExecutionCommand command);
}
