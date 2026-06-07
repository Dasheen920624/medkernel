package com.medkernel.engine.integration.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

import com.medkernel.engine.integration.domain.IntegrationAdapter;

class HttpIntegrationConnectorTest {

    private HttpServer server;
    private String baseUrl;

    @BeforeEach
    void startServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.start();
        baseUrl = "http://127.0.0.1:" + server.getAddress().getPort();
    }

    @AfterEach
    void stopServer() {
        server.stop(0);
    }

    @Test
    void probesAndDeliversJsonThroughConfiguredHttpConnector() {
        AtomicReference<String> deliveredBody = new AtomicReference<>();
        AtomicReference<String> messageId = new AtomicReference<>();
        server.createContext("/health", exchange -> {
            exchange.sendResponseHeaders(204, -1);
            exchange.close();
        });
        server.createContext("/messages", exchange -> {
            deliveredBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            messageId.set(exchange.getRequestHeaders().getFirst("X-MedKernel-Message-Id"));
            exchange.sendResponseHeaders(202, -1);
            exchange.close();
        });

        HttpIntegrationConnector connector = new HttpIntegrationConnector(
            new ObjectMapper(),
            new MockEnvironment());
        IntegrationAdapter adapter = adapter("""
            {
              "baseUrl": "%s",
              "healthPath": "/health",
              "outboundPath": "/messages",
              "connectTimeoutMs": 1000,
              "requestTimeoutMs": 1000
            }
            """.formatted(baseUrl));

        IntegrationConnectorHealth health = connector.checkHealth(adapter);
        IntegrationDeliveryResult delivery = connector.deliver(
            adapter,
            new ObjectMapper().createObjectNode().put("patientId", "MPI-1"),
            "msg-1",
            "trace-1",
            Map.of());

        assertThat(health.status()).isEqualTo("HEALTHY");
        assertThat(health.rttMs()).isPositive();
        assertThat(delivery.delivered()).isTrue();
        assertThat(delivery.connected()).isTrue();
        assertThat(deliveredBody.get()).contains("\"patientId\":\"MPI-1\"");
        assertThat(messageId.get()).isEqualTo("msg-1");
    }

    @Test
    void rejectsPlaintextSensitiveHeadersButResolvesEnvironmentReferences() {
        MockEnvironment environment = new MockEnvironment()
            .withProperty("HIS_AUTH_HEADER", "Bearer runtime-secret");
        HttpIntegrationConnector connector = new HttpIntegrationConnector(new ObjectMapper(), environment);

        assertThat(connector.validate(adapter("""
            {
              "baseUrl": "%s",
              "headers": {"Authorization": "plain-secret"}
            }
            """.formatted(baseUrl))).valid()).isFalse();
        assertThat(connector.validate(adapter("""
            {
              "baseUrl": "%s",
              "headers": {"Authorization": "${HIS_AUTH_HEADER}"}
            }
            """.formatted(baseUrl))).valid()).isTrue();
    }

    private IntegrationAdapter adapter(String configJson) {
        Instant now = Instant.parse("2026-06-07T00:00:00Z");
        return new IntegrationAdapter(
            1L,
            "adapter-1",
            "tenant-A",
            "HIS",
            "REST",
            "ACTIVE",
            configJson,
            "NOT_CONNECTED",
            0L,
            null,
            now,
            "tester",
            now,
            "tester");
    }
}
