package com.medkernel.engine.versioning;

import java.time.Instant;

/**
 * 版本发布电子签名证据。
 */
public record VersionElectronicSignature(
    String signatureId,
    String signerId,
    String signerName,
    Instant signedAt,
    String signatureHash
) {}
