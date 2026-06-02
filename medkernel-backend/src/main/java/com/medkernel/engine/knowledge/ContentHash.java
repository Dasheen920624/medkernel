package com.medkernel.engine.knowledge;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.regex.Pattern;

import com.medkernel.shared.api.error.ApiException;
import com.medkernel.shared.api.error.ErrorCode;

/**
 * 知识来源与资产内容指纹工具。统一生成和校验 SHA-256，避免服务内重复实现漂移。
 */
final class ContentHash {

    private static final Pattern SHA256_HEX = Pattern.compile("^[0-9a-fA-F]{64}$");

    private ContentHash() {
    }

    static String resolve(String content, String externalHash) {
        if (content != null && !content.isBlank()) {
            String computed = sha256(content);
            if (externalHash != null && !externalHash.isBlank()) {
                String normalizedExternal = normalizeExternalSha256(externalHash);
                if (!computed.equals(normalizedExternal)) {
                    throw new ApiException(ErrorCode.VALIDATION_FAILED, "来源正文与外部内容哈希不一致，禁止登记不可自证的来源版本");
                }
            }
            return computed;
        }
        return normalizeExternalSha256(externalHash);
    }

    static String normalizeExternalSha256(String hash) {
        if (hash == null || hash.isBlank()) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, "内容哈希必须为真实 SHA-256，禁止使用版本号或时间戳合成");
        }
        String normalized = hash.trim();
        if (!SHA256_HEX.matcher(normalized).matches()) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, "内容哈希必须为 64 位 SHA-256 十六进制字符串");
        }
        return normalized.toLowerCase();
    }

    static String sha256(String content) {
        if (content == null || content.isBlank()) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, "内容原文不能为空，禁止为空内容生成知识指纹");
        }
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(content.getBytes(StandardCharsets.UTF_8));
            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) {
                    hexString.append('0');
                }
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (Exception ex) {
            throw new IllegalStateException("无法计算 SHA-256 内容指纹", ex);
        }
    }
}
