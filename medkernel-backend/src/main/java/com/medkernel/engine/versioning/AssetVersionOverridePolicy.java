package com.medkernel.engine.versioning;

/**
 * 平台版本约束下游可覆盖程度的策略护栏（权威与临床安全）。
 *
 * <p>LOCKED 的安全单调判定（禁止关闭、只能收紧不能放宽）下沉到各域的
 * {@code SafetyMonotonicityCheck}，由解析层统一调用，本阶段仅登记策略本身。
 */
public enum AssetVersionOverridePolicy {
    /** 可自由 REPLACE / DISABLE 覆盖。 */
    FREE,
    /** 覆盖需走评审方可生效。 */
    REVIEW,
    /** 安全单调下限：禁止 DISABLE，且只能收紧不能放宽。 */
    LOCKED
}
