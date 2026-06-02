package com.medkernel.engine.knowledge;

import com.medkernel.shared.hash.Sha256ContentHash;

/**
 * 知识来源与资产内容指纹工具。统一生成和校验 SHA-256，避免服务内重复实现漂移。
 */
final class ContentHash {

    private ContentHash() {
    }

    static String resolve(String content, String externalHash) {
        return Sha256ContentHash.resolve(
            content,
            externalHash,
            "来源正文与外部内容哈希不一致，禁止登记不可自证的来源版本",
            "内容原文不能为空，禁止为空内容生成知识指纹"
        );
    }

    static String sha256(String content) {
        return Sha256ContentHash.sha256(content, "内容原文不能为空，禁止为空内容生成知识指纹");
    }
}
