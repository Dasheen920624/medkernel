package com.medkernel.engine.rule;

/**
 * 规则版本生命周期状态枚举（GA-ENG-API-05）。
 *
 * <p>取值含义：{@code DRAFT} 草稿版本（编辑中）、{@code PUBLISHED} 已发布版本（只读）、
 * {@code OFFLINE} 已下线版本、{@code ARCHIVED} 归档版本（仅追溯）。
 * 是否参与运行只由统一资产版本的 {@code ACTIVE} 状态决定。
 */
public enum RuleVersionStatus {
    DRAFT,
    PUBLISHED,
    OFFLINE,
    ARCHIVED
}
