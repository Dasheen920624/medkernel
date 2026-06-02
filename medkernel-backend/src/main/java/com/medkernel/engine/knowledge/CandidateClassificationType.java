package com.medkernel.engine.knowledge;

/**
 * 知识候选新旧识别分类。
 */
public enum CandidateClassificationType {
    /** 全新知识身份下的首个候选 */
    NEW_ASSET,
    /** 同一知识身份下的新版候选 */
    SAME_IDENTITY_NEW_VERSION,
    /** 内容指纹重复，不新增审核待办 */
    DUPLICATE,
    /** 同主题但内容或来源分级冲突，需对照审核 */
    CONFLICT
}
