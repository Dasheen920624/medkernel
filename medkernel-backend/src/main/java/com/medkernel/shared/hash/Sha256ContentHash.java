package com.medkernel.shared.hash;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.regex.Pattern;

import com.medkernel.shared.api.error.ApiException;
import com.medkernel.shared.api.error.ErrorCode;

/**
 * SHA-256 内容指纹工具。
 *
 * <p>用于知识、规则、路径、包等配置资产的真实内容指纹，禁止使用版本号、
 * 时间戳、UUID 等不可自证值伪装哈希。
 */
public final class Sha256ContentHash {

    private static final Pattern SHA256_HEX = Pattern.compile("^[0-9a-fA-F]{64}$");

    private Sha256ContentHash() {
    }

    /**
     * 根据内容生成指纹，或校验外部指纹；同时提供内容与外部指纹时必须一致。
     */
    public static String resolve(
            String content,
            String externalHash,
            String mismatchMessage,
            String emptyContentMessage) {
        if (content != null && !content.isBlank()) {
            String computed = sha256(content, emptyContentMessage);
            if (externalHash != null && !externalHash.isBlank()) {
                String normalizedExternal = normalizeExternalSha256(externalHash);
                if (!computed.equals(normalizedExternal)) {
                    throw new ApiException(ErrorCode.VALIDATION_FAILED, mismatchMessage);
                }
            }
            return computed;
        }
        return normalizeExternalSha256(externalHash);
    }

    /**
     * 校验并标准化外部 SHA-256 十六进制字符串。
     */
    public static String normalizeExternalSha256(String hash) {
        if (hash == null || hash.isBlank()) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, "内容哈希必须为真实 SHA-256，禁止使用版本号或时间戳合成");
        }
        String normalized = hash.trim();
        if (!SHA256_HEX.matcher(normalized).matches()) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, "内容哈希必须为 64 位 SHA-256 十六进制字符串");
        }
        return normalized.toLowerCase();
    }

    /**
     * 基于 UTF-8 原文计算小写 SHA-256 十六进制字符串。
     */
    public static String sha256(String content, String emptyContentMessage) {
        if (content == null || content.isBlank()) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, emptyContentMessage);
        }
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(content.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(hash.length * 2);
            for (byte b : hash) {
                String value = Integer.toHexString(0xff & b);
                if (value.length() == 1) {
                    hex.append('0');
                }
                hex.append(value);
            }
            return hex.toString();
        } catch (Exception ex) {
            throw new IllegalStateException("无法计算 SHA-256 内容指纹", ex);
        }
    }
}
