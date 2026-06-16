package com.medkernel.engine.knowledge.production.gate;

/**
 * 单项门禁判定结果（AIK-STD-05）。
 *
 * <p>门禁码 + 是否通过 + 不过原因（通过时为空）。诚实报因，不静默放行（FR-4）。
 */
public record GateItemResult(String code, boolean passed, String reason) {

    /** 通过：无原因。 */
    public static GateItemResult pass(String code) {
        return new GateItemResult(code, true, null);
    }

    /** 不过：带真实原因。 */
    public static GateItemResult fail(String code, String reason) {
        return new GateItemResult(code, false, reason);
    }
}
