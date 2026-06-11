package com.medkernel.engine.knowledge;

/**
 * 平台知识本地派生生命周期。
 */
public enum KnowledgeCustomizationStatus {
    /** 已创建本地草稿，尚未接管运行时。 */
    DRAFT,
    /** 本地版本已发布并在目标组织优先生效。 */
    ACTIVE,
    /** 本地覆盖已停用，目标组织恢复平台标准。 */
    RESTORED
}
