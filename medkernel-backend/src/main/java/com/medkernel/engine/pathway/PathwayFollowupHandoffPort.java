package com.medkernel.engine.pathway;

import com.medkernel.shared.context.RequestContext;

/**
 * 路径域到随访域的结径交接端口。
 */
public interface PathwayFollowupHandoffPort {

    PathwayFollowupHandoffResult handoff(PathwayFollowupHandoffCommand command);

    static PathwayFollowupHandoffPort noop() {
        return command -> PathwayFollowupHandoffResult.skipped(
            "NOT_CONNECTED", RequestContext.currentTraceId());
    }
}
