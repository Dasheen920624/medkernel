package com.medkernel.engine.knowledge.production;

import org.springframework.stereotype.Component;

import com.medkernel.engine.versioning.VersionedAssetType;
import com.medkernel.shared.api.error.ApiException;
import com.medkernel.shared.api.error.ErrorCode;

/**
 * 正式知识生产入口策略。
 *
 * <p>公共知识生产入口只负责校验生产任务是否进入统一候选/草稿治理链；人工维护、来源解析、受控模型
 * 和本地模型都不能绕过后续来源、结构、影子评测、责任审核和发布门禁。
 */
@Component
public class FormalKnowledgeProductionPolicy {

    public void requireSupportedFormalJob(ProductionJobRequest request) {
        if (request == null) {
            throw new ApiException(ErrorCode.BAD_REQUEST, "知识生产任务请求不能为空");
        }
        if (request.producer() == null) {
            throw new ApiException(ErrorCode.BAD_REQUEST, "知识生产器不能为空");
        }
        VersionedAssetType assetType = request.assetType();
        if (assetType == null) {
            throw new ApiException(ErrorCode.BAD_REQUEST, "资产类型不能为空");
        }
        if (!assetType.isRuntimeConfiguration()) {
            throw new ApiException(ErrorCode.BAD_REQUEST, "知识生产仅允许进入可发布运行配置资产");
        }
    }
}
