package com.medkernel.engine.projection;

import org.springframework.stereotype.Component;

/**
 * 默认外部投影执行器：未配置真实 Dify 执行器时诚实返回 NOT_SYNCED。
 */
@Component
public class NoopProjectionExecutionPort implements ProjectionExecutionPort {

    @Override
    public ProjectionExecutionResult execute(ProjectionExecutionCommand command) {
        return ProjectionExecutionResult.notSynced("NOT_SYNCED：未配置真实 Dify 执行器，未执行外部工作流");
    }
}
