package com.medkernel.engine.knowledge.delivery;

import java.nio.file.Path;

/**
 * 已完成落盘和重读校验的真实完整包文件事实。
 *
 * @param path 当前宿主内受管绝对路径，不进入 manifest 或注册表
 * @param storageCoordinate 与宿主无关的受管相对坐标
 * @param packageFileDigest 整个 .mkp 文件真实字节的 SM3 摘要
 * @param packageFileSize 整个 .mkp 文件真实字节数
 */
public record StoredFullPackage(
    Path path,
    String storageCoordinate,
    String packageFileDigest,
    long packageFileSize
) {
}
