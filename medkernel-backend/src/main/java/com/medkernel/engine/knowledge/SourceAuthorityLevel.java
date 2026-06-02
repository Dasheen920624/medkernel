package com.medkernel.engine.knowledge;

/**
 * 来源证据可信分级。对应 {@code source_document.authority_level} CHECK 约束。
 *
 * <p>A-E 分级用于 KNOW-01 / OPT-07 的 B0 可信仲裁：数字 rank 越小可信度越高。
 */
public enum SourceAuthorityLevel {
    /** A 法规 / 强制性监管文件。 */
    A_REGULATION(1, "A 法规"),
    /** B 国家或国际指南。 */
    B_GUIDELINE(2, "B 指南"),
    /** C 共识 / 文献。 */
    C_CONSENSUS_LITERATURE(3, "C 共识文献"),
    /** D 院内制度 / SOP。 */
    D_HOSPITAL(4, "D 院内"),
    /** E 反馈 / 其他低阶来源。 */
    E_FEEDBACK(5, "E 反馈");

    private final int rank;
    private final String label;

    SourceAuthorityLevel(int rank, String label) {
        this.rank = rank;
        this.label = label;
    }

    public int rank() {
        return rank;
    }

    public String label() {
        return label;
    }

    public boolean isHighAuthority() {
        return this == A_REGULATION || this == B_GUIDELINE;
    }

    public boolean isLowAuthority() {
        return this == D_HOSPITAL || this == E_FEEDBACK;
    }
}
