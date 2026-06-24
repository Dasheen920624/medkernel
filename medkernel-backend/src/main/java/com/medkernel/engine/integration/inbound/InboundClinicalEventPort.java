package com.medkernel.engine.integration.inbound;

/**
 * 集成接入与临床运行之间的唯一窄端口。
 *
 * <p>接入域负责验签、幂等、字段和术语归一；临床事件域负责持久化、上下文快照与引擎派发。
 */
public interface InboundClinicalEventPort {

    InboundClinicalEventAccepted accept(String tenantId, InboundClinicalEventCommand command);
}
