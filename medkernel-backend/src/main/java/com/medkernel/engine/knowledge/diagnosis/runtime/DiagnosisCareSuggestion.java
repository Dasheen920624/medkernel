package com.medkernel.engine.knowledge.diagnosis.runtime;

import com.medkernel.engine.knowledge.diagnosis.DiagnosisCarePointerType;
import com.medkernel.engine.knowledge.diagnosis.DiagnosisCareTargetType;

/**
 * 诊断候选关联的诊疗建议：仅供医师确认后查看和执行，不自动开嘱或入径。
 */
public record DiagnosisCareSuggestion(
    DiagnosisCarePointerType pointerType,
    DiagnosisCareTargetType targetType,
    String targetRef,
    String description,
    boolean requiresPhysicianConfirmation
) {}
