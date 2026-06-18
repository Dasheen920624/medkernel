package com.medkernel.engine.knowledge.acquisition;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.net.URI;

import org.junit.jupiter.api.Test;

/** 公域抓取 DNS 边界测试：回环、私网和链路本地地址一律不得成为出网目的地。 */
class DnsPublicNetworkAddressGuardTest {

    private final DnsPublicNetworkAddressGuard guard = new DnsPublicNetworkAddressGuard();

    @Test
    void loopbackAndPrivateIpv4AreRejected() {
        assertThatThrownBy(() -> guard.assertPublic(URI.create("https://127.0.0.1/source")))
            .isInstanceOf(WebContentFetchException.class)
            .hasMessageContaining("非公网");
        assertThatThrownBy(() -> guard.assertPublic(URI.create("https://192.168.1.10/source")))
            .isInstanceOf(WebContentFetchException.class)
            .hasMessageContaining("非公网");
    }

    @Test
    void loopbackHostnameIsRejectedAfterDnsResolution() {
        assertThatThrownBy(() -> guard.assertPublic(URI.create("https://localhost/source")))
            .isInstanceOf(WebContentFetchException.class)
            .hasMessageContaining("非公网");
    }
}
