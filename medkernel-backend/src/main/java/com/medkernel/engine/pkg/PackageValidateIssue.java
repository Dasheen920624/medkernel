package com.medkernel.engine.pkg;

/**
 * 包发布校验问题。
 *
 * @param field 问题归属字段或资源
 * @param severity 严重级别，`BLOCKING` 表示阻断发布
 * @param message 面向用户的中文说明
 */
public record PackageValidateIssue(
    String field,
    String severity,
    String message
) {}
