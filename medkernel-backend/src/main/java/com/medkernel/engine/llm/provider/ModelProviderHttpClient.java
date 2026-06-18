package com.medkernel.engine.llm.provider;

import java.util.Map;

/**
 * provider HTTP 出站抽象（LLM-08）。
 *
 * <p>把真实 HTTP 调用从适配器逻辑解耦，便于单测注入假实现、杜绝单测连真实外网；
 * 非 2xx 或传输失败一律抛异常，由适配器转 {@link ProviderHealth#NOT_CONNECTED} 或上抛降级。
 */
public interface ModelProviderHttpClient {

    String post(String url, Map<String, String> headers, String body, int timeoutMs);

    String get(String url, Map<String, String> headers, int timeoutMs);
}
