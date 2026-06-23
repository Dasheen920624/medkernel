package com.medkernel.engine.authoring;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 自动生成资产候选服务。
 *
 * <p>对控制器、模型生产器和批量任务暴露统一入口，内部委托 {@link AssetAuthoringRegistry}
 * 物化为正式资产草稿。
 */
@Service
public class GeneratedAssetCandidateService {

    private final AssetAuthoringRegistry registry;

    public GeneratedAssetCandidateService(AssetAuthoringRegistry registry) {
        this.registry = registry;
    }

    @Transactional
    public GeneratedAssetDraftResponse materializeDraft(GeneratedAssetCandidateRequest request) {
        return registry.materializeDraft(request);
    }
}
