package com.medkernel.engine.llm.provider;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Map;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class RestClientModelProviderHttpClientTest {

    private HttpServer server;

    @AfterEach
    void tearDown() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    void postDecodesOctetStreamJsonBodyAsUtf8Text() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/api/generate", exchange -> {
            byte[] body = "{\"model\":\"qwen2.5:7b\",\"response\":\"候选内容\",\"done\":true}"
                .getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/octet-stream");
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.start();

        String raw = new RestClientModelProviderHttpClient().post(
            "http://127.0.0.1:" + server.getAddress().getPort() + "/api/generate",
            Map.of("Content-Type", "application/json"),
            "{\"model\":\"qwen2.5:7b\"}",
            5_000);

        assertThat(raw).contains("\"response\":\"候选内容\"");
    }
}
