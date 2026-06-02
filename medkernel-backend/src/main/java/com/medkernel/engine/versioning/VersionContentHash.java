package com.medkernel.engine.versioning;

import com.medkernel.shared.hash.Sha256ContentHash;

/**
 * 配置资产内容指纹工具。
 */
final class VersionContentHash {

    private VersionContentHash() {
    }

    static String resolve(String content, String externalHash) {
        return Sha256ContentHash.resolve(
            content,
            externalHash,
            "资产内容与外部内容哈希不一致，禁止登记不可自证的版本",
            "内容原文不能为空，禁止为空内容生成版本指纹"
        );
    }
}
