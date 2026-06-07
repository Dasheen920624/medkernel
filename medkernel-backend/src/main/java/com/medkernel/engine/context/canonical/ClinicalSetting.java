package com.medkernel.engine.context.canonical;

/**
 * MedKernel 唯一临床场景值集。
 */
public enum ClinicalSetting {
    INPATIENT("IMP"),
    OUTPATIENT("AMB"),
    ED("EMER"),
    FOLLOWUP("AMB");

    private final String fhirClassCode;

    ClinicalSetting(String fhirClassCode) {
        this.fhirClassCode = fhirClassCode;
    }

    public String fhirClassCode() {
        return fhirClassCode;
    }

    /**
     * 校验标准上下文只使用产品四值闭集。
     */
    public static void requireCanonical(String value) {
        if (value == null) {
            return;
        }
        try {
            ClinicalSetting.valueOf(value);
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException(
                "标准就诊场景仅允许 INPATIENT/OUTPATIENT/ED/FOLLOWUP", exception);
        }
    }

    /**
     * 把 FHIR Encounter.class 与随访类型映射为标准场景。
     */
    public static ClinicalSetting fromFhir(String classCode, String typeText) {
        if ("FOLLOWUP".equals(typeText)) {
            return FOLLOWUP;
        }
        return switch (classCode) {
            case "IMP" -> INPATIENT;
            case "AMB" -> OUTPATIENT;
            case "EMER" -> ED;
            default -> throw new IllegalArgumentException(
                "FHIR Encounter.class.code 仅支持 IMP/AMB/EMER");
        };
    }
}
