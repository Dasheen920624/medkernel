package com.medkernel.engine.llm.eval;

import java.util.List;

/**
 * 负责人可核查的医学回归单用例证据。
 */
public record ModelEvalCaseEvidenceResponse(
    Long evidenceId,
    Long regressionCaseId,
    String caseVersion,
    String caseInput,
    String expectedPhrase,
    String redLineType,
    String sourceReference,
    String outputContent,
    String sourceCitations,
    boolean expectedPhraseHit,
    boolean citationRequired,
    boolean citationVerified,
    boolean redLineCase,
    boolean redLineBreach,
    boolean passed,
    List<String> failureReasons
) {
    public ModelEvalCaseEvidenceResponse {
        failureReasons = failureReasons == null ? List.of() : List.copyOf(failureReasons);
    }
}
