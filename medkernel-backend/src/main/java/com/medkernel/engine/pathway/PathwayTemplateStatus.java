package com.medkernel.engine.pathway;

/**
 * 临床路径生命周期状态。
 *
 * <p>草稿可编辑，{@code PUBLISHED} 表示已发布并写保护；只有统一资产版本
 * {@code ACTIVE} 时才允许新患者入径。下线和归档状态仅保留查询与追溯。
 */
public enum PathwayTemplateStatus {
    DRAFT,
    PUBLISHED,
    OFFLINE,
    ARCHIVED
}
