package com.medkernel.engine.pkg;

/**
 * 同步目标未接入真实物理通道时的诚实降级异常。
 */
public class PackageSyncNotConnectedException extends RuntimeException {

    public static final String CODE = "NOT_SYNCED";

    public PackageSyncNotConnectedException(String message) {
        super(message);
    }
}
