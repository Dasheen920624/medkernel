package com.medkernel.engine.llm.provider;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;

import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * 基于 Spring {@link RestClient} 的模型服务出站调用实现（LLM-08）。
 *
 * <p>带连接/读取超时；非 2xx 与传输错误抛 {@code RestClient} 运行时异常，由适配器转 NOT_CONNECTED 或上抛降级。
 * 单测用 {@link ModelProviderHttpClient} 假实现，本类不在单测连真实外网。
 */
@Component
public class RestClientModelProviderHttpClient implements ModelProviderHttpClient {

    @Override
    public String post(String url, Map<String, String> headers, String body, int timeoutMs) {
        byte[] response = restClient(timeoutMs).post()
            .uri(url)
            .headers(h -> headers.forEach(h::set))
            .body(body)
            .retrieve()
            .body(byte[].class);
        return decode(response);
    }

    @Override
    public String get(String url, Map<String, String> headers, int timeoutMs) {
        byte[] response = restClient(timeoutMs).get()
            .uri(url)
            .headers(h -> headers.forEach(h::set))
            .retrieve()
            .body(byte[].class);
        return decode(response);
    }

    private RestClient restClient(int timeoutMs) {
        int boundedTimeoutMs = Math.max(1_000, Math.min(timeoutMs, 120_000));
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofMillis(Math.min(5_000, boundedTimeoutMs)));
        factory.setReadTimeout(Duration.ofMillis(boundedTimeoutMs));
        return RestClient.builder().requestFactory(factory).build();
    }

    private String decode(byte[] response) {
        return response == null ? "" : new String(response, StandardCharsets.UTF_8);
    }
}
