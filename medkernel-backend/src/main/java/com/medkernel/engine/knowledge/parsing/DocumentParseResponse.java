package com.medkernel.engine.knowledge.parsing;

import com.medkernel.engine.knowledge.production.generation.GenerationSummary;

/**
 * 文档解析响应；院内上传可在解析成功后追加候选生成摘要。
 *
 * @param parseJob 解析 job 结果
 * @param generationSummary 候选生成摘要；未请求生成或解析未成功时为空
 */
public record DocumentParseResponse(
    DocParseJob parseJob,
    GenerationSummary generationSummary
) {
}
