package com.medkernel.engine.knowledge.delivery;

import java.nio.file.Path;

/**
 * 未信任上传字节已完整写入隔离区后的文件事实。
 *
 * @param path 当前宿主内受管绝对路径，不返回客户端且不进入 manifest
 * @param quarantineCoordinate 隔离根下由整包 SM3 唯一派生的相对坐标
 * @param packageFileDigest 整个上传文件真实字节的 SM3 摘要
 * @param packageFileSize 整个上传文件真实字节数
 */
public record QuarantinedFullPackage(
    Path path,
    String quarantineCoordinate,
    String packageFileDigest,
    long packageFileSize
) {
}
