package com.medkernel.engine.knowledge.production.initialization;

/** 初始化依赖拓扑阶段，禁止越过未完成前置层。 */
public enum InitializationPhase {
    F0(0),
    F1(1),
    F2(2),
    F3(3),
    F4(4),
    F5(5),
    F6(6),
    F7(7),
    F8(8);

    private final int order;

    InitializationPhase(int order) {
        this.order = order;
    }

    public int order() {
        return order;
    }
}
