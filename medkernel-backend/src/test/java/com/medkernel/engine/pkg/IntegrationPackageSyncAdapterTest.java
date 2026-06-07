package com.medkernel.engine.pkg;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.medkernel.engine.integration.domain.IntegrationAdapter;
import com.medkernel.engine.integration.service.HttpIntegrationConnector;
import com.medkernel.engine.versioning.SourceTier;
import com.medkernel.engine.versioning.VersionedAssetType;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

class IntegrationPackageSyncAdapterTest {

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
    void deliversEffectivePackageSnapshotThroughUnifiedIntegrationAdapter() throws Exception {
        AtomicReference<String> deliveredBody = new AtomicReference<>();
        server.createContext("/packages", exchange -> {
            deliveredBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            exchange.sendResponseHeaders(202, -1);
            exchange.close();
        });
        ObjectMapper mapper = new ObjectMapper();
        IntegrationPackageSyncAdapter adapter = new IntegrationPackageSyncAdapter(
            mapper,
            List.of(new HttpIntegrationConnector(mapper, new MockEnvironment())));

        String evidence = adapter.sync(
            "tenant-A",
            releasePlan(),
            integrationAdapter("REST", """
                {"baseUrl":"%s","outboundPath":"/packages"}
                """.formatted(baseUrl)),
            snapshot());

        assertThat(deliveredBody.get())
            .contains("\"eventType\":\"MEDKERNEL_PACKAGE_RELEASE\"")
            .contains("\"packageCode\":\"PKG.COPD\"")
            .contains("\"contentSha256\"");
        assertThat(evidence)
            .contains("\"adapterId\":\"adapter-package\"")
            .contains("\"deliveryStatus\":\"ACCEPTED\"")
            .contains(snapshot().contentSha256());
    }

    @Test
    void reportsNotSyncedWhenAdapterProtocolHasNoRealConnector() {
        IntegrationPackageSyncAdapter adapter = new IntegrationPackageSyncAdapter(
            new ObjectMapper(),
            List.of());

        assertThatThrownBy(() -> adapter.sync(
            "tenant-A",
            releasePlan(),
            integrationAdapter("HL7", "{}"),
            snapshot()))
            .isInstanceOf(PackageSyncNotConnectedException.class)
            .hasMessageContaining("NOT_SYNCED")
            .hasMessageContaining("没有可用连接器");
    }

    private IntegrationAdapter integrationAdapter(String protocol, String configJson) {
        Instant now = Instant.parse("2026-06-07T00:00:00Z");
        return new IntegrationAdapter(
            1L,
            "adapter-package",
            "tenant-A",
            "院内配置投递",
            protocol,
            "ACTIVE",
            configJson,
            "HEALTHY",
            8L,
            now,
            now,
            "tester",
            now,
            "tester");
    }

    private ReleasePlan releasePlan() {
        Instant now = Instant.parse("2026-06-07T00:00:00Z");
        return new ReleasePlan(
            1L,
            "plan-1",
            "tenant-A",
            "pkg-1",
            "hospital-1",
            ReleaseStrategy.GRAYSCALE,
            ReleaseScopeType.ALL,
            null,
            ReleasePlanStatus.EXECUTING,
            now,
            "tester",
            now,
            "tester",
            "trace-1");
    }

    private EffectivePackageSnapshot snapshot() {
        return EffectivePackageSnapshot.from(new EffectiveKnowledgePackageResponse(
            "tenant-A",
            "hospital-1",
            "pkg-1",
            "PKG.COPD",
            "1.0.0",
            List.of(new EffectivePackageItem(
                VersionedAssetType.RULE,
                "RULE.COPD",
                "1",
                "1",
                "tenant-A",
                "/TENANT-A/HOSPITAL-1",
                SourceTier.ORG,
                false,
                false,
                true,
                "asset-version-1",
                "a".repeat(64))),
            List.of(),
            List.of()));
    }
}
