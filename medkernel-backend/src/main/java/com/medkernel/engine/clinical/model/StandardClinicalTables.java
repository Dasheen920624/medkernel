package com.medkernel.engine.clinical.model;

import java.util.List;

/**
 * SYS-01 标准临床对象关系库权威表目录。
 */
public final class StandardClinicalTables {

    private static final List<String> NAMES = List.of(
        "mk_clinical_patient",
        "mk_clinical_encounter",
        "mk_clinical_condition",
        "mk_clinical_observation",
        "mk_clinical_medication",
        "mk_clinical_procedure",
        "mk_clinical_diagnostic_report",
        "mk_clinical_document",
        "mk_clinical_nursing_assessment",
        "mk_clinical_care_plan",
        "mk_clinical_follow_up",
        "mk_clinical_claim"
    );

    private StandardClinicalTables() {}

    public static List<String> names() {
        return NAMES;
    }
}
