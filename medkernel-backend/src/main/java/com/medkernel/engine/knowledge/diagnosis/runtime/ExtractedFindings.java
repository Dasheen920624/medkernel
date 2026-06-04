package com.medkernel.engine.knowledge.diagnosis.runtime;

import java.util.List;
import java.util.Set;

/**
 * 发现提取结果：标准化发现编码集 + 未能标准化的本地编码清单（部分可用，不阻断）。
 *
 * <p>{@code unmappedFindings} 进运行时响应字段如实告知"哪些发现未纳入命中"，是正常降级而非错误。
 */
public record ExtractedFindings(Set<String> normalizedCodes, List<String> unmappedFindings) {}
