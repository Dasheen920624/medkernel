package com.medkernel.engine.domainfacade;

/**
 * 单个共享引擎能力的 B0 主链路证据。
 *
 * @param engine 共享引擎能力
 * @param sharedHandlerClass 已存在的共享处理器类
 * @param b0Route 可运行的确定性 B0 入口
 * @param b0Assertion B0 断言摘要
 * @param deterministic 是否为确定性路径
 * @param handlerPresent 处理器类是否存在
 * @param clinicalContentSeeded 是否预填真实医学内容；固定为 false
 */
public record DomainFacadeEngineEvidence(
    DomainFacadeEngine engine,
    String sharedHandlerClass,
    String b0Route,
    String b0Assertion,
    boolean deterministic,
    boolean handlerPresent,
    boolean clinicalContentSeeded
) {
}
