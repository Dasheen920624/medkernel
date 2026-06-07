package com.medkernel.engine.integration;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.LockSupport;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.medkernel.engine.integration.domain.IntegrationAdapter;
import com.medkernel.engine.integration.domain.IntegrationMessageLog;
import com.medkernel.engine.integration.dto.AdapterCreateDto;
import com.medkernel.engine.integration.dto.IntegrationOutboundRequestDto;
import com.medkernel.engine.integration.dto.IntegrationOutboundResultDto;
import com.medkernel.engine.integration.dto.WebhookCreateDto;
import com.medkernel.engine.integration.dto.WebhookCreateResponse;
import com.medkernel.engine.integration.repository.IntegrationAdapterRepository;
import com.medkernel.engine.integration.repository.IntegrationMessageLogRepository;
import com.medkernel.engine.integration.service.IntegrationService;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class IntegrationHttpDeliveryTest {

    @Autowired
    private IntegrationService service;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private IntegrationMessageLogRepository logRepository;

    @Autowired
    private IntegrationAdapterRepository adapterRepository;

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
        logRepository.findByMessageIdAndTenantId("msg-async-1", "tenant-async")
            .ifPresent(logRepository::delete);
        adapterRepository.findByAdapterIdAndTenantId("his-async", "tenant-async")
            .ifPresent(adapterRepository::delete);
    }

    @Test
    void probesAndDispatchesConfiguredAdapterThroughRealHttpConnector() throws Exception {
        AtomicReference<String> deliveredBody = new AtomicReference<>();
        server.createContext("/health", exchange -> {
            exchange.sendResponseHeaders(204, -1);
            exchange.close();
        });
        server.createContext("/messages", exchange -> {
            deliveredBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            exchange.sendResponseHeaders(202, -1);
            exchange.close();
        });
        service.createAdapter("tenant-http", new AdapterCreateDto(
            "his-http",
            "真实 HIS",
            "REST",
            """
                {
                  "baseUrl": "%s",
                  "healthPath": "/health",
                  "outboundPath": "/messages"
                }
                """.formatted(baseUrl)
        ));

        IntegrationAdapter health = service.checkAdapterHealth("tenant-http", "his-http");
        IntegrationOutboundResultDto queued = service.enqueueOutboundMessage(
            "tenant-http",
            outbound("msg-http-1", "his-http")
        );
        IntegrationMessageLog delivered = service.dispatchQueuedMessage("tenant-http", "msg-http-1");

        assertThat(health.healthStatus()).isEqualTo("HEALTHY");
        assertThat(health.rttMs()).isPositive();
        assertThat(queued.status()).isEqualTo("RETRYING");
        assertThat(queued.compensationRequired()).isFalse();
        assertThat(delivered.status()).isEqualTo("SUCCESS");
        assertThat(deliveredBody.get()).contains("\"patientId\":\"MPI-1\"");
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void dispatchesAutomaticallyAfterQueueTransactionCommits() throws Exception {
        CountDownLatch received = new CountDownLatch(1);
        server.createContext("/messages", exchange -> {
            exchange.getRequestBody().readAllBytes();
            exchange.sendResponseHeaders(202, -1);
            exchange.close();
            received.countDown();
        });
        service.createAdapter("tenant-async", new AdapterCreateDto(
            "his-async",
            "异步 HIS",
            "REST",
            """
                {"baseUrl":"%s","outboundPath":"/messages"}
                """.formatted(baseUrl)
        ));

        IntegrationOutboundResultDto queued = service.enqueueOutboundMessage(
            "tenant-async",
            outbound("msg-async-1", "his-async")
        );

        assertThat(queued.status()).isEqualTo("RETRYING");
        assertThat(received.await(3, TimeUnit.SECONDS)).isTrue();
        assertThat(awaitMessageStatus("tenant-async", "msg-async-1", Duration.ofSeconds(3)))
            .isEqualTo("SUCCESS");
    }

    @Test
    void failedDeliveryCanBeRetriedAgainstTheSameConfiguredConnector() throws Exception {
        AtomicInteger status = new AtomicInteger(503);
        server.createContext("/messages", exchange -> {
            exchange.getRequestBody().readAllBytes();
            exchange.sendResponseHeaders(status.get(), -1);
            exchange.close();
        });
        service.createAdapter("tenant-retry", new AdapterCreateDto(
            "his-retry",
            "可重试 HIS",
            "REST",
            """
                {"baseUrl":"%s","outboundPath":"/messages"}
                """.formatted(baseUrl)
        ));
        service.enqueueOutboundMessage("tenant-retry", outbound("msg-retry-1", "his-retry"));

        IntegrationMessageLog failed = service.dispatchQueuedMessage("tenant-retry", "msg-retry-1");
        status.set(202);
        IntegrationMessageLog retried = service.retryMessage("tenant-retry", "msg-retry-1");

        assertThat(failed.status()).isEqualTo("FAILED");
        assertThat(failed.retryCount()).isZero();
        assertThat(retried.status()).isEqualTo("SUCCESS");
        assertThat(retried.retryCount()).isEqualTo(1);
    }

    @Test
    void dispatchesWebhookWithTimestampAndHmacSignature() throws Exception {
        AtomicReference<String> deliveredBody = new AtomicReference<>();
        AtomicReference<String> timestamp = new AtomicReference<>();
        AtomicReference<String> signature = new AtomicReference<>();
        server.createContext("/callback", exchange -> {
            deliveredBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            timestamp.set(exchange.getRequestHeaders().getFirst("X-MedKernel-Timestamp"));
            signature.set(exchange.getRequestHeaders().getFirst("X-MedKernel-Signature"));
            exchange.sendResponseHeaders(204, -1);
            exchange.close();
        });
        WebhookCreateResponse webhook = service.createWebhook(
            "tenant-webhook",
            new WebhookCreateDto(
                "callback-main",
                "业务回调",
                baseUrl + "/callback",
                "CLINICAL_EVENT_PROCESSED"
            )
        );

        IntegrationOutboundResultDto queued = service.enqueueOutboundMessage(
            "tenant-webhook",
            outbound("msg-webhook-1", webhook.webhookId())
        );
        IntegrationMessageLog delivered = service.dispatchQueuedMessage("tenant-webhook", "msg-webhook-1");

        assertThat(queued.status()).isEqualTo("RETRYING");
        assertThat(delivered.status()).isEqualTo("SUCCESS");
        assertThat(deliveredBody.get()).contains("\"patientId\":\"MPI-1\"");
        assertThat(timestamp.get()).isNotBlank();
        assertThat(signature.get()).startsWith("sha256=");
    }

    private IntegrationOutboundRequestDto outbound(String messageId, String adapterId) throws Exception {
        return new IntegrationOutboundRequestDto(
            messageId,
            "trace-" + messageId,
            adapterId,
            "HIS",
            "REST",
            "同步患者事件",
            objectMapper.readTree("{\"patientId\":\"MPI-1\",\"event\":\"UPDATED\"}"),
            3
        );
    }

    private String awaitMessageStatus(String tenantId, String messageId, Duration timeout) {
        long deadline = System.nanoTime() + timeout.toNanos();
        String status = "";
        while (System.nanoTime() < deadline) {
            status = logRepository.findByMessageIdAndTenantId(messageId, tenantId)
                .map(IntegrationMessageLog::status)
                .orElse("");
            if ("SUCCESS".equals(status)) {
                return status;
            }
            LockSupport.parkNanos(Duration.ofMillis(10).toNanos());
        }
        return status;
    }
}
