package com.medkernel.engine.pkg;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.medkernel.engine.integration.domain.IntegrationAdapter;
import com.medkernel.engine.integration.service.IntegrationConnector;
import com.medkernel.engine.integration.service.IntegrationConnectorValidation;
import com.medkernel.engine.integration.service.IntegrationDeliveryResult;
import com.medkernel.shared.api.error.ApiException;
import com.medkernel.shared.api.error.ErrorCode;
import org.springframework.stereotype.Component;

/**
 * 复用统一集成连接器执行配置包快照投递。
 */
@Component
public class IntegrationPackageSyncAdapter implements PackageSyncPort {

    private static final String STATUS_ACTIVE = "ACTIVE";
    private static final String EVENT_TYPE = "MEDKERNEL_PACKAGE_RELEASE";

    private final ObjectMapper objectMapper;
    private final List<IntegrationConnector> connectors;

    public IntegrationPackageSyncAdapter(ObjectMapper objectMapper, List<IntegrationConnector> connectors) {
        this.objectMapper = objectMapper;
        this.connectors = List.copyOf(connectors);
    }

    @Override
    public boolean supports(IntegrationAdapter adapter) {
        return adapter != null && connectorFor(adapter).isPresent();
    }

    @Override
    public String sync(
            String tenantId,
            ReleasePlan plan,
            IntegrationAdapter adapter,
            EffectivePackageSnapshot snapshot) {
        requireAdapter(tenantId, adapter);
        IntegrationConnector connector = connectorFor(adapter)
            .orElseThrow(() -> new PackageSyncNotConnectedException(
                "NOT_SYNCED：适配器 " + adapter.adapterId() + " 的协议没有可用连接器"));
        IntegrationConnectorValidation validation = connector.validate(adapter);
        if (!validation.valid()) {
            throw new ApiException(
                ErrorCode.ENG_PACKAGE_005,
                "同步适配器连接配置无效: " + validation.reason());
        }

        ObjectNode payload = packageReleasePayload(plan, adapter, snapshot);
        String messageId = "package-release:" + plan.planId() + ":" + adapter.adapterId();
        IntegrationDeliveryResult result = connector.deliver(
            adapter,
            payload,
            messageId,
            plan.traceId(),
            Map.of("X-MedKernel-Event-Type", EVENT_TYPE));
        if (!result.delivered()) {
            if (!result.connected()) {
                throw new PackageSyncNotConnectedException(
                    "NOT_SYNCED：适配器 " + adapter.adapterId() + " 未连通，" + result.errorMessage());
            }
            throw new ApiException(
                ErrorCode.ENG_PACKAGE_005,
                "同步适配器投递失败: " + result.errorMessage());
        }
        return deliveryEvidence(messageId, adapter, snapshot, payload);
    }

    private void requireAdapter(String tenantId, IntegrationAdapter adapter) {
        if (adapter == null || !tenantId.equals(adapter.tenantId())) {
            throw new ApiException(ErrorCode.ENG_PACKAGE_001, "同步适配器不存在或不属于当前租户");
        }
        if (!STATUS_ACTIVE.equalsIgnoreCase(adapter.status())) {
            throw new ApiException(ErrorCode.ENG_PACKAGE_002, "同步适配器未启用: " + adapter.adapterId());
        }
    }

    private Optional<IntegrationConnector> connectorFor(IntegrationAdapter adapter) {
        return connectors.stream()
            .filter(connector -> connector.supports(adapter))
            .findFirst();
    }

    private ObjectNode packageReleasePayload(
            ReleasePlan plan,
            IntegrationAdapter adapter,
            EffectivePackageSnapshot snapshot) {
        ObjectNode root = objectMapper.createObjectNode();
        root.put("schemaVersion", "1.0");
        root.put("eventType", EVENT_TYPE);
        root.put("messageId", "package-release:" + plan.planId() + ":" + adapter.adapterId());
        root.put("traceId", plan.traceId());
        root.set("releasePlan", objectMapper.createObjectNode()
            .put("planId", plan.planId())
            .put("packageId", plan.packageId())
            .put("targetOrgUnitId", plan.targetOrgUnitId())
            .put("strategy", plan.strategy().name())
            .put("scopeType", plan.scopeType().name())
            .put("scopeValue", plan.scopeValue()));
        root.set("effectiveSnapshot", objectMapper.valueToTree(snapshot));
        return root;
    }

    private String deliveryEvidence(
            String messageId,
            IntegrationAdapter adapter,
            EffectivePackageSnapshot snapshot,
            ObjectNode payload) {
        ObjectNode evidence = objectMapper.createObjectNode();
        evidence.put("messageId", messageId);
        evidence.put("adapterId", adapter.adapterId());
        evidence.put("protocolType", adapter.protocolType());
        evidence.put("deliveryStatus", "ACCEPTED");
        evidence.put("snapshotSha256", snapshot.contentSha256());
        evidence.put("payloadSha256", sha256(payload));
        evidence.put("acceptedAt", Instant.now().toString());
        try {
            return objectMapper.writeValueAsString(evidence);
        } catch (JsonProcessingException exception) {
            throw new ApiException(ErrorCode.INTERNAL_ERROR, "配置包同步证据序列化失败", exception);
        }
    }

    private String sha256(ObjectNode payload) {
        try {
            byte[] bytes = objectMapper.writeValueAsString(payload).getBytes(StandardCharsets.UTF_8);
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (JsonProcessingException | NoSuchAlgorithmException exception) {
            throw new ApiException(ErrorCode.INTERNAL_ERROR, "配置包投递摘要生成失败", exception);
        }
    }
}
