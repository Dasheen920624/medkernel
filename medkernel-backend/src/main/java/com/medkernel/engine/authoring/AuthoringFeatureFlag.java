package com.medkernel.engine.authoring;

/**
 * 规则与路径创作增强能力的运行开关清单。
 */
public enum AuthoringFeatureFlag {
    RECURSIVE_CONDITION_TREE("authoring-recursive-condition-tree", "递归条件树"),
    CLINICAL_OPERATORS("authoring-clinical-operators", "临床算子"),
    BATCH_AUTHORING("authoring-batch-authoring", "批量创作"),
    PATHWAY_RICH_NODES("authoring-pathway-rich-nodes", "路径富节点");

    private final String key;
    private final String displayName;

    AuthoringFeatureFlag(String key, String displayName) {
        this.key = key;
        this.displayName = displayName;
    }

    public String key() {
        return key;
    }

    public String displayName() {
        return displayName;
    }
}
