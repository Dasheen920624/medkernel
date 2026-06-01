package com.medkernel.engine.context;

/**
 * 下游引擎接收统一临床事件上下文的适配端口。
 */
public interface ClinicalEventEngineAdapter {

    ClinicalEventEngine engine();

    ClinicalEventEngineDispatchResult dispatch(ClinicalEventContext context);
}
