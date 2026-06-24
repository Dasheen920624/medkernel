package com.medkernel.engine.release;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Collection;

/**
 * 不可变版本明细的完整性校验码。
 */
public final class ReleaseManifestHash {

    private ReleaseManifestHash() {
    }

    public static String sha256(Collection<String> canonicalLines) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            canonicalLines.stream()
                .sorted()
                .forEach(line -> {
                    digest.update(line.getBytes(StandardCharsets.UTF_8));
                    digest.update((byte) '\n');
                });
            return java.util.HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("运行环境不支持 SHA-256", exception);
        }
    }
}
