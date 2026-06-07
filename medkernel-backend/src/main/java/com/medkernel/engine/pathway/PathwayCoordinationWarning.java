package com.medkernel.engine.pathway;

/**
 * 多路径并行协调提示。
 *
 * <p>用于展示同一患者多条活跃路径之间的医嘱集或时窗冲突，系统只提示协调，不自动改医嘱。
 */
public record PathwayCoordinationWarning(
    PathwayCoordinationWarningType warningType,
    String severity,
    String patientPathwayId,
    String templateId,
    String nodeCode,
    String conflictWithPatientPathwayId,
    String conflictWithTemplateId,
    String conflictWithNodeCode,
    String sharedRef,
    String message
) {}
