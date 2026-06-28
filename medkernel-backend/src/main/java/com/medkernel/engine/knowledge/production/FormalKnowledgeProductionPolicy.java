package com.medkernel.engine.knowledge.production;

import org.springframework.stereotype.Component;

import com.medkernel.engine.versioning.VersionedAssetType;
import com.medkernel.shared.api.error.ApiException;
import com.medkernel.shared.api.error.ErrorCode;

/**
 * 正式知识生产入口策略。
 *
 * <p>公共知识生产入口只允许经受控模型服务创建正式生产任务；模型服务可为本地或外部。
 * B0、人工维护和内部初始化继续保留各自的内部服务边界，
 * 不得从公共正式入口混入模型生产审核池。
 */
@Component
public class FormalKnowledgeProductionPolicy {

    public void requireApiModel(ProductionJobRequest request) {
        if (request.producer() != KnowledgeProducer.API_MODEL) {
            throw new ApiException(ErrorCode.BAD_REQUEST, "正式知识生产仅允许通过受控模型服务生产");
        }
        if (request.assetType() != VersionedAssetType.KNOWLEDGE
                && request.assetType() != VersionedAssetType.RULE
                && request.assetType() != VersionedAssetType.PATHWAY) {
            throw new ApiException(
                ErrorCode.BAD_REQUEST,
                "正式模型生产仅允许生成知识、规则或路径草稿"
            );
        }
    }

    public void rejectB0Generation() {
        throw new ApiException(
            ErrorCode.BAD_REQUEST,
            "正式知识生产不再接受无模型候选生成，请使用受控模型服务生产任务"
        );
    }
}
