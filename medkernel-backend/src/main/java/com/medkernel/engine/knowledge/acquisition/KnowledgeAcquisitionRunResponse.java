package com.medkernel.engine.knowledge.acquisition;

import com.medkernel.engine.knowledge.production.generation.GenerationSummary;

/**
 * 公域资料获取响应。失败或阻断时只给真实原因，不伪造资料 URI、来源版本或候选生成结果。
 */
public record KnowledgeAcquisitionRunResponse(
    String runCode,
    KnowledgeAcquisitionRunStatus status,
    String sourceCode,
    String url,
    String domain,
    String sourceHash,
    Long byteSize,
    String contentType,
    String materialFileUri,
    Long sourceDocumentId,
    Long sourceVersionId,
    String parseJobCode,
    String failureReason,
    GenerationSummary generationSummary
) {
    static KnowledgeAcquisitionRunResponse from(KnowledgeAcquisitionRun run) {
        return from(run, null);
    }

    static KnowledgeAcquisitionRunResponse from(KnowledgeAcquisitionRun run, GenerationSummary generationSummary) {
        return new KnowledgeAcquisitionRunResponse(
            run.runCode(),
            run.status(),
            run.sourceCode(),
            run.url(),
            run.domain(),
            run.sourceHash(),
            run.byteSize(),
            run.contentType(),
            run.materialFileUri(),
            run.sourceDocumentId(),
            run.sourceVersionId(),
            run.parseJobCode(),
            run.failureReason(),
            generationSummary);
    }
}
