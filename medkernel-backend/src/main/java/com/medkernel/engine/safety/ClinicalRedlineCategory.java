package com.medkernel.engine.safety;

import java.util.List;

/**
 * OPT-04 临床安全红线受控类目。
 */
public enum ClinicalRedlineCategory {
    DRUG_INTERACTION,
    CRITICAL_VALUE,
    DOSE_LIMIT,
    ANTIMICROBIAL_RESTRICTION,
    SPECIAL_POPULATION_CONTRAINDICATION,
    SURGERY_ANESTHESIA_TRANSFUSION;

    public static List<ClinicalRedlineCategory> requiredSafetyCategories() {
        return List.of(
            DRUG_INTERACTION,
            CRITICAL_VALUE,
            DOSE_LIMIT,
            ANTIMICROBIAL_RESTRICTION,
            SPECIAL_POPULATION_CONTRAINDICATION,
            SURGERY_ANESTHESIA_TRANSFUSION);
    }
}
