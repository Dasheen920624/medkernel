package com.medkernel.engine.knowledge.acquisition;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;

import org.springframework.stereotype.Component;

/**
 * 基于 JDK HttpClient 的公域资料抓取实现。只负责真实 HTTP 获取，不内嵌白名单策略。
 */
@Component
public class RestWebContentFetcher implements WebContentFetcher {

    private static final Duration TIMEOUT = Duration.ofSeconds(20);

    private final HttpClient client;

    public RestWebContentFetcher() {
        this(HttpClient.newBuilder()
            .connectTimeout(TIMEOUT)
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build());
    }

    RestWebContentFetcher(HttpClient client) {
        this.client = client;
    }

    @Override
    public FetchedWebContent fetch(URI uri) {
        HttpRequest request = HttpRequest.newBuilder(uri)
            .timeout(TIMEOUT)
            .header("Accept", "text/plain, application/pdf, application/vnd.openxmlformats-officedocument.wordprocessingml.document")
            .header("User-Agent", "MedKernel-Knowledge-Acquisition/1.0")
            .GET()
            .build();
        try {
            HttpResponse<byte[]> response = client.send(request, HttpResponse.BodyHandlers.ofByteArray());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new WebContentFetchException("HTTP " + response.statusCode());
            }
            String contentType = response.headers().firstValue("Content-Type").orElse("application/octet-stream");
            return new FetchedWebContent(response.uri(), contentType, response.body(), Instant.now());
        } catch (IOException exception) {
            throw new WebContentFetchException("HTTP 抓取失败：" + exception.getMessage(), exception);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new WebContentFetchException("HTTP 抓取被中断", exception);
        }
    }
}
