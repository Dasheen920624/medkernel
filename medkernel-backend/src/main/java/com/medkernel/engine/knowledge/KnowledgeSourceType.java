package com.medkernel.engine.knowledge;

/**
 * 客户视角的知识来源类型。
 */
public enum KnowledgeSourceType {
    /** 平台当前发布的权威标准。 */
    PLATFORM_STANDARD,
    /** 客户从平台标准明确派生的本地定制。 */
    LOCAL_CUSTOMIZATION,
    /** 客户独立创建、没有平台上游的知识。 */
    LOCAL_ORIGINAL
}
