package com.medkernel.engine.knowledge.acquisition;

import java.io.IOException;
import java.io.InputStream;
import java.net.InetAddress;
import java.net.URI;
import java.time.Duration;
import java.time.Instant;

import org.apache.hc.client5.http.DnsResolver;
import org.apache.hc.client5.http.classic.methods.HttpGet;
import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.CloseableHttpResponse;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManagerBuilder;
import org.apache.hc.core5.http.HttpEntity;
import org.apache.hc.core5.util.Timeout;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import jakarta.annotation.PreDestroy;

/**
 * 公域资料抓取实现。连接直接使用安全解析器返回的地址，不在校验后再次解析域名。
 */
@Component
public class RestWebContentFetcher implements WebContentFetcher {

    private static final Duration TIMEOUT = Duration.ofSeconds(20);
    static final int MAX_RESPONSE_BYTES = 32 * 1024 * 1024;

    private final CloseableHttpClient client;

    public RestWebContentFetcher() {
        this(new DnsPublicNetworkAddressGuard());
    }

    @Autowired
    public RestWebContentFetcher(PublicNetworkAddressGuard addressGuard) {
        this(buildClient(addressGuard));
    }

    private static CloseableHttpClient buildClient(PublicNetworkAddressGuard addressGuard) {
        DnsResolver resolver = new DnsResolver() {
            @Override
            public InetAddress[] resolve(String host) {
                return addressGuard.resolvePublic(host);
            }

            @Override
            public String resolveCanonicalHostname(String host) {
                return host;
            }
        };
        return HttpClients.custom()
            // 连接管理器直接消费同一次校验返回的地址，避免“先校验、连接时再解析”的 DNS 时序窗口。
            .setConnectionManager(PoolingHttpClientConnectionManagerBuilder.create()
                .setDnsResolver(resolver)
                .build())
            .setDefaultRequestConfig(RequestConfig.custom()
                .setConnectTimeout(Timeout.of(TIMEOUT))
                .setResponseTimeout(Timeout.of(TIMEOUT))
                .build())
            // 重定向目的地必须重新走来源治理；当前抓取契约直接拒绝 3xx。
            .disableRedirectHandling()
            .build();
    }

    RestWebContentFetcher(CloseableHttpClient client) {
        this.client = client;
    }

    @PreDestroy
    void close() throws IOException {
        client.close();
    }

    @Override
    public FetchedWebContent fetch(URI uri) {
        if (uri == null || uri.getHost() == null || uri.getHost().isBlank()) {
            throw new WebContentFetchException("公域资料 URL 缺少合法域名");
        }
        HttpGet request = new HttpGet(uri);
        request.setHeader("Accept",
            "text/plain, application/pdf, application/vnd.openxmlformats-officedocument.wordprocessingml.document");
        request.setHeader("User-Agent", "MedKernel-Knowledge-Acquisition/1.0");
        try (CloseableHttpResponse response = client.execute(request)) {
            int statusCode = response.getCode();
            if (statusCode < 200 || statusCode >= 300) {
                throw new WebContentFetchException("HTTP " + statusCode);
            }
            HttpEntity entity = response.getEntity();
            if (entity == null) {
                throw new WebContentFetchException("公域资料响应体为空");
            }
            String contentType = entity.getContentType() == null
                ? "application/octet-stream"
                : entity.getContentType();
            long declaredLength = entity.getContentLength();
            if (declaredLength > MAX_RESPONSE_BYTES) {
                throw new WebContentFetchException("公域资料响应体超过 32 MiB 上限");
            }
            try (InputStream body = entity.getContent()) {
                byte[] bytes = body.readNBytes(MAX_RESPONSE_BYTES + 1);
                if (bytes.length > MAX_RESPONSE_BYTES) {
                    throw new WebContentFetchException("公域资料响应体超过 32 MiB 上限");
                }
                return new FetchedWebContent(uri, contentType, bytes, Instant.now());
            }
        } catch (IOException exception) {
            throw new WebContentFetchException("HTTP 抓取失败：" + exception.getMessage(), exception);
        }
    }
}
