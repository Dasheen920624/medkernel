package com.medkernel.engine.knowledge.acquisition;

import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.UnknownHostException;

import org.springframework.stereotype.Component;

/**
 * DNS 公网地址闸。域名任一解析结果落入回环、私网、链路本地、共享地址、基准测试或文档保留网段即拒绝，
 * 避免混合 DNS 结果和 DNS 重绑定把公域抓取变成内网探测。
 */
@Component
public class DnsPublicNetworkAddressGuard implements PublicNetworkAddressGuard {

    @Override
    public InetAddress[] resolvePublic(String host) {
        if (host == null || host.isBlank()) {
            throw new WebContentFetchException("公域资料 URL 缺少合法域名");
        }
        final InetAddress[] addresses;
        try {
            addresses = InetAddress.getAllByName(host);
        } catch (UnknownHostException exception) {
            throw new WebContentFetchException("公域资料域名无法解析", exception);
        }
        if (addresses.length == 0) {
            throw new WebContentFetchException("公域资料域名无可用解析地址");
        }
        for (InetAddress address : addresses) {
            if (isNonPublic(address)) {
                throw new WebContentFetchException("公域资料域名解析到非公网地址，禁止抓取");
            }
        }
        return addresses.clone();
    }

    private boolean isNonPublic(InetAddress address) {
        if (address.isAnyLocalAddress()
            || address.isLoopbackAddress()
            || address.isLinkLocalAddress()
            || address.isSiteLocalAddress()
            || address.isMulticastAddress()) {
            return true;
        }
        byte[] bytes = address.getAddress();
        if (address instanceof Inet4Address) {
            int first = unsigned(bytes[0]);
            int second = unsigned(bytes[1]);
            int third = unsigned(bytes[2]);
            return first == 0
                || (first == 100 && second >= 64 && second <= 127)
                || (first == 192 && second == 0 && third == 0)
                || (first == 192 && second == 0 && third == 2)
                || (first == 198 && (second == 18 || second == 19))
                || (first == 198 && second == 51 && third == 100)
                || (first == 203 && second == 0 && third == 113)
                || first >= 224;
        }
        if (address instanceof Inet6Address) {
            int first = unsigned(bytes[0]);
            int second = unsigned(bytes[1]);
            return (first & 0xfe) == 0xfc
                || (first == 0x20 && second == 0x01
                    && unsigned(bytes[2]) == 0x0d && unsigned(bytes[3]) == 0xb8);
        }
        return true;
    }

    private static int unsigned(byte value) {
        return value & 0xff;
    }
}
