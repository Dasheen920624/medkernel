package com.medkernel.engine.llm.provider;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;

import org.springframework.stereotype.Component;

/**
 * 基于 JDK {@link HttpClient} 的模型服务出站调用实现（LLM-08）。
 *
 * <p>带连接/读取超时；非 2xx 与传输错误抛运行时异常，由适配器转 NOT_CONNECTED 或上抛降级。
 * 单测用 {@link ModelProviderHttpClient} 假实现，本类不在单测连真实外网。
 */
@Component
public class RestClientModelProviderHttpClient implements ModelProviderHttpClient {

    @Override
    public String post(String url, Map<String, String> headers, String body, int timeoutMs) {
        HttpRequest.Builder builder = requestBuilder(url, headers, timeoutMs)
            .POST(HttpRequest.BodyPublishers.ofString(body == null ? "" : body, StandardCharsets.UTF_8));
        return send(builder.build(), timeoutMs);
    }

    @Override
    public String get(String url, Map<String, String> headers, int timeoutMs) {
        HttpRequest.Builder builder = requestBuilder(url, headers, timeoutMs).GET();
        return send(builder.build(), timeoutMs);
    }

    private HttpRequest.Builder requestBuilder(String url, Map<String, String> headers, int timeoutMs) {
        int boundedTimeoutMs = Math.max(1_000, Math.min(timeoutMs, 120_000));
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(url))
            .timeout(Duration.ofMillis(boundedTimeoutMs));
        headers.forEach(builder::header);
        return builder;
    }

    private String send(HttpRequest request, int timeoutMs) {
        int boundedTimeoutMs = Math.max(1_000, Math.min(timeoutMs, 120_000));
        HttpClient client = HttpClient.newBuilder()
            .connectTimeout(Duration.ofMillis(Math.min(5_000, boundedTimeoutMs)))
            .build();
        try {
            HttpResponse<byte[]> response = client.send(request, HttpResponse.BodyHandlers.ofByteArray());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new IllegalStateException("模型服务 HTTP " + response.statusCode());
            }
            return decode(response.body());
        } catch (IOException transportError) {
            throw new IllegalStateException(transportError.getMessage(), transportError);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("模型服务调用被中断", interrupted);
        }
    }

    private String decode(byte[] response) {
        return response == null ? "" : new String(response, StandardCharsets.UTF_8);
    }
}
