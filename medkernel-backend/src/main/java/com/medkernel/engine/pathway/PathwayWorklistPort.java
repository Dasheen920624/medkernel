package com.medkernel.engine.pathway;

/**
 * 路径节点工作清单端口。
 *
 * <p>路径域只声明节点待办事实，实际落库和通知由临床协同域承接，避免路径引擎直接依赖待办表结构。
 */
public interface PathwayWorklistPort {

    void openNodeTodo(PathwayNodeWorklistCommand command);

    void completeNodeTodo(PathwayNodeWorklistCompletionCommand command);

    static PathwayWorklistPort noop() {
        return new PathwayWorklistPort() {
            @Override
            public void openNodeTodo(PathwayNodeWorklistCommand command) {
                // 空实现用于单元测试或未启用协同域时保持路径主链路可运行。
            }

            @Override
            public void completeNodeTodo(PathwayNodeWorklistCompletionCommand command) {
                // 空实现用于单元测试或未启用协同域时保持路径主链路可运行。
            }
        };
    }
}
