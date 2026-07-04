package com.medkernel.engine.event;

/**
 * 引擎领域事件出口。
 *
 * <p>规则与路径域只声明真实业务事实，具体待办、通知、质量风险概览由协同域适配器统一承接。
 */
public interface EngineDomainEventPort {

    void ruleFired(RuleFiredEvent event);

    void overrideCaptured(OverrideCapturedEvent event);

    void pathwayVarianceRecorded(PathwayVarianceRecordedEvent event);

    void clockSlaBreached(ClockSlaBreachedEvent event);

    static EngineDomainEventPort noop() {
        return new EngineDomainEventPort() {
            @Override
            public void ruleFired(RuleFiredEvent event) {
            }

            @Override
            public void overrideCaptured(OverrideCapturedEvent event) {
            }

            @Override
            public void pathwayVarianceRecorded(PathwayVarianceRecordedEvent event) {
            }

            @Override
            public void clockSlaBreached(ClockSlaBreachedEvent event) {
            }
        };
    }
}
