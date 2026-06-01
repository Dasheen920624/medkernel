package com.medkernel.engine.experience;

import java.time.Instant;

/**
 * 页面保存视图响应。
 */
public record SavedViewResponse(
    String savedViewId,
    String pageKey,
    String viewName,
    String definitionJson,
    boolean defaultView,
    long version,
    Instant updatedAt,
    String updatedBy
) {

    static SavedViewResponse from(SavedView view) {
        return new SavedViewResponse(
            view.savedViewId(),
            view.pageKey(),
            view.viewName(),
            view.definitionJson(),
            view.isDefaultView(),
            view.version(),
            view.updatedAt(),
            view.updatedBy()
        );
    }
}
