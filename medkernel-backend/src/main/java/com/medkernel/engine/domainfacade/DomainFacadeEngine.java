package com.medkernel.engine.domainfacade;

/**
 * 领域门面可复用的既有引擎能力。
 *
 * <p>X-DOMAIN 只组合这些共享能力，不新增护理、药事、报告等专属业务引擎。
 */
public enum DomainFacadeEngine {
    RULE,
    PATHWAY,
    KNOWLEDGE,
    CDSS,
    EMBED,
    EVALUATION,
    FOLLOWUP,
    PACKAGE,
    INTEGRATION,
    DATA_SERVICE,
    SAFETY,
    ORGANIZATION,
    DOSAGE_CALCULATION,
    AUTHORING_TEMPLATE
}
