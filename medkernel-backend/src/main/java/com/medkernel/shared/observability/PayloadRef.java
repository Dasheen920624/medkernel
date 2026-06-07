package com.medkernel.shared.observability;

/**
 * 持久化 payload 的引用信息。
 *
 * <p>当前实现使用 storageType=INLINE，并把 payload 以 Base64 持久化到
 * {@code mk_obs_payload_store}。
 *
 * @param storageType  "INLINE" 或 "URI"
 * @param digest       SHA-256 摘要
 * @param uri          INLINE 时填 "db://table/id"；URI 时填外部存储 URI（mc://、oss://）
 * @param sizeBytes    字节数
 * @param contentType  payload 内容类型
 */
public record PayloadRef(
    String storageType,
    String digest,
    String uri,
    long sizeBytes,
    String contentType
) {

    public static final String STORAGE_INLINE = "INLINE";
    public static final String STORAGE_URI = "URI";

    public PayloadRef(String storageType, String digest, String uri, long sizeBytes) {
        this(storageType, digest, uri, sizeBytes, null);
    }
}
