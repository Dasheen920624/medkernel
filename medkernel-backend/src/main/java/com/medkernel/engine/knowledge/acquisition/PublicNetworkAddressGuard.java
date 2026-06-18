package com.medkernel.engine.knowledge.acquisition;

import java.net.InetAddress;
import java.net.URI;

/**
 * 公域抓取目的地址解析器：只把已验证的可路由公网地址交给实际连接。
 */
@FunctionalInterface
public interface PublicNetworkAddressGuard {

    InetAddress[] resolvePublic(String host);

    default void assertPublic(URI uri) {
        String host = uri == null ? null : uri.getHost();
        resolvePublic(host);
    }
}
