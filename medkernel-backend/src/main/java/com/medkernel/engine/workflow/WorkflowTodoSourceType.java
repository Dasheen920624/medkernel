package com.medkernel.engine.workflow;

/**
 * 统一待办的真实来源类型。
 */
public enum WorkflowTodoSourceType {
    FOLLOWUP_TASK,
    SAFETY_REVIEW,
    RECOMMENDATION_CARD,
    PATHWAY_NODE,
    RULE_EVENT,
    PATHWAY_EVENT,
    NURSING_TASK,
    REPORT_INTERPRETATION,
    BEDSIDE_KNOWLEDGE
}
