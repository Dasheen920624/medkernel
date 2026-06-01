package com.medkernel.engine.contract;

/**
 * 领域事件 schema 目录项。
 *
 * @param schemaId 版本化 schema ID，格式为 {@code <event>.v<version>}
 * @param version schema 主版本
 * @param recordClassName 事件 record 类全名
 * @param contractFile 仓库根目录下的 JSON 契约文件
 */
public record DomainEventSchema(
    String schemaId,
    int version,
    String recordClassName,
    String contractFile
) {}
