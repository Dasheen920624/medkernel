package com.medkernel.shared.web;

/**
 * 后端运行制品身份。
 *
 * @param bound           是否已绑定可验证候选制品
 * @param candidateCommit 制品内嵌的完整候选提交；未绑定时为空
 * @param reason          绑定结果原因
 */
public record BuildIdentity(
    boolean bound,
    String candidateCommit,
    String reason
) {

    public static BuildIdentity bound(String candidateCommit) {
        return new BuildIdentity(true, candidateCommit, "BOUND");
    }

    public static BuildIdentity unbound(String reason) {
        return new BuildIdentity(false, null, reason);
    }
}
