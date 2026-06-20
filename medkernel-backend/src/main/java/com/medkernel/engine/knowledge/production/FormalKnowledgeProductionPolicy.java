package com.medkernel.engine.knowledge.production;

import org.springframework.stereotype.Component;

import com.medkernel.shared.api.error.ApiException;
import com.medkernel.shared.api.error.ErrorCode;

/**
 * 正式知识生产入口策略。
 *
 * <p>公共生产控制台只允许 API 大模型创建正式生产任务。B0、人工维护和内部初始化继续保留各自的内部服务边界，
 * 不得从公共正式入口混入模型生产审核池。
 */
@Component
public class FormalKnowledgeProductionPolicy {

    public void requireApiModel(ProductionJobRequest request) {
        if (request.producer() != KnowledgeProducer.API_MODEL) {
            throw new ApiException(ErrorCode.BAD_REQUEST, "正式知识生产仅允许 API_MODEL 大模型生产器");
        }
    }

    public void rejectB0Generation() {
        throw new ApiException(
            ErrorCode.BAD_REQUEST,
            "正式知识生产不再接受 B0 候选生成，请使用 API_MODEL 模型生产任务"
        );
    }
}
