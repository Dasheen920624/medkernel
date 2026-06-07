package com.medkernel.engine.pkg;

/**
 * 发布适配器未接入真实连接器时的诚实降级异常。
 */
public class PackageSyncNotConnectedException extends RuntimeException {

    public static final String CODE = "NOT_SYNCED";

    public PackageSyncNotConnectedException(String message) {
        super(message);
    }
}
