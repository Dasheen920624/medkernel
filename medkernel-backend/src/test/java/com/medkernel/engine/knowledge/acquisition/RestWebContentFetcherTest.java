package com.medkernel.engine.knowledge.acquisition;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.net.InetSocketAddress;
import java.net.InetAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicInteger;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/** 真实本机 HTTP 服务验证公域抓取器不跨域跟随重定向且有响应体硬上限。 */
class RestWebContentFetcherTest {

    private HttpServer sourceServer;
    private HttpServer redirectTarget;

    @AfterEach
    void stopServers() {
        if (sourceServer != null) {
            sourceServer.stop(0);
        }
        if (redirectTarget != null) {
            redirectTarget.stop(0);
        }
    }

    @Test
    void redirectIsRejectedBeforeTargetReceivesRequest() throws Exception {
        AtomicInteger targetHits = new AtomicInteger();
        redirectTarget = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        redirectTarget.createContext("/private", exchange -> {
            targetHits.incrementAndGet();
            byte[] body = "should-not-be-fetched".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        redirectTarget.start();

        sourceServer = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        String target = "http://127.0.0.1:" + redirectTarget.getAddress().getPort() + "/private";
        sourceServer.createContext("/redirect", exchange -> {
            exchange.getResponseHeaders().add("Location", target);
            exchange.sendResponseHeaders(302, -1);
            exchange.close();
        });
        sourceServer.start();

        RestWebContentFetcher fetcher = new RestWebContentFetcher(
            ignored -> new InetAddress[] { InetAddress.getLoopbackAddress() });
        URI source = URI.create("http://127.0.0.1:" + sourceServer.getAddress().getPort() + "/redirect");

        assertThatThrownBy(() -> fetcher.fetch(source))
            .isInstanceOf(WebContentFetchException.class)
            .hasMessageContaining("HTTP 302");
        assertThat(targetHits).hasValue(0);
    }

    @Test
    void responseLargerThanConfiguredLimitIsRejectedBeforeReadingBody() throws Exception {
        sourceServer = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        sourceServer.createContext("/oversized", exchange -> {
            exchange.getResponseHeaders().add("Content-Type", "text/plain");
            exchange.sendResponseHeaders(200, RestWebContentFetcher.MAX_RESPONSE_BYTES + 1L);
            exchange.close();
        });
        sourceServer.start();

        RestWebContentFetcher fetcher = new RestWebContentFetcher(
            ignored -> new InetAddress[] { InetAddress.getLoopbackAddress() });
        URI source = URI.create("http://127.0.0.1:" + sourceServer.getAddress().getPort() + "/oversized");

        assertThatThrownBy(() -> fetcher.fetch(source))
            .isInstanceOf(WebContentFetchException.class)
            .hasMessageContaining("超过");
    }

    @Test
    void connectionUsesTheExactAddressReturnedByTheValidatedResolver() throws Exception {
        byte[] expected = "pinned-address-response".getBytes(StandardCharsets.UTF_8);
        sourceServer = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        sourceServer.createContext("/direct", exchange -> {
            exchange.getResponseHeaders().add("Content-Type", "text/plain");
            exchange.sendResponseHeaders(200, expected.length);
            exchange.getResponseBody().write(expected);
            exchange.close();
        });
        sourceServer.start();

        AtomicInteger resolutions = new AtomicInteger();
        RestWebContentFetcher fetcher = new RestWebContentFetcher(uri -> {
            resolutions.incrementAndGet();
            return new InetAddress[] { InetAddress.getLoopbackAddress() };
        });
        URI source = URI.create(
            "http://public-source.invalid:" + sourceServer.getAddress().getPort() + "/direct");

        FetchedWebContent content = fetcher.fetch(source);

        assertThat(content.bytes()).isEqualTo(expected);
        assertThat(resolutions).hasValue(1);
    }
}
