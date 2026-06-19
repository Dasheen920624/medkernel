package com.medkernel.engine.knowledge.production.initialization;

import java.util.List;
import java.util.Set;

/** 已从关系库事实解析完成、等待纯确定性校验的初始化发行清单。 */
public record InitializationManifestDraft(
    InitializationReleaseType releaseType,
    String releaseVersion,
    String foundationReleaseVersion,
    InitializationPhase phase,
    String templateVersion,
    String modelVersion,
    String summary,
    int declaredSourceFileCount,
    int declaredEntryCount,
    Set<FoundationCoverageDimension> coverage,
    Set<String> availableCanonicalIds,
    boolean foundationReleaseComplete,
    List<InitializationManifestDraftItem> items
) {
    public InitializationManifestDraft {
        coverage = coverage == null ? Set.of() : Set.copyOf(coverage);
        availableCanonicalIds = availableCanonicalIds == null ? Set.of() : Set.copyOf(availableCanonicalIds);
        items = items == null ? List.of() : List.copyOf(items);
    }
}
