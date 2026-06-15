package com.medkernel.engine.knowledge.production;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import com.medkernel.engine.knowledge.KnowledgeDomain;

/**
 * 新建知识身份壳声明（AIK-STD-13 PR4）：生产方显式声明内容域 + 主题 + 身份编码。
 *
 * <p>身份壳＝有效主题容器（{@code KnowledgeIdentityStatus.ACTIVE}）；权威性在版本层把关（候选版本恒待审，核心 §6）。
 */
public record NewIdentitySpec(
    @NotNull KnowledgeDomain domain,
    @NotBlank String subject,
    @NotBlank String identityCode
) {
}
