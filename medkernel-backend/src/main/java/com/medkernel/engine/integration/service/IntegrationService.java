package com.medkernel.engine.integration.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.medkernel.engine.integration.domain.*;
import com.medkernel.engine.integration.dto.*;
import com.medkernel.engine.integration.repository.*;
import com.medkernel.shared.api.error.ErrorCode;
import com.medkernel.shared.api.error.ApiException;

/**
 * 外部第三方系统对接及集成核心业务逻辑服务。
 *
 * <p>实现适配器生命周期管理（健康检查、配置预检）、外部 Webhook 签名测试、
 * 接口集成消息队列的多租户分页查询、以及死信队列（Dead Letter）的一键手动重试及业务补偿。
 */
@Service
public class IntegrationService {

    private static final String STATUS_ACTIVE = "ACTIVE";
    private static final String STATUS_SUSPENDED = "SUSPENDED";
    private static final String HEALTH_HEALTHY = "HEALTHY";
    private static final String HEALTH_NOT_CONNECTED = "NOT_CONNECTED";
    private static final String HEALTH_MISCONFIGURED = "MISCONFIGURED";

    private final IntegrationAdapterRepository adapterRepository;
    private final IntegrationWebhookConfigRepository webhookRepository;
    private final IntegrationMessageLogRepository logRepository;
    private final ObjectMapper objectMapper;

    /**
     * 构造器注入适配器、Webhook 订阅及流日志的持久化存储库。
     */
    public IntegrationService(IntegrationAdapterRepository adapterRepository,
                              IntegrationWebhookConfigRepository webhookRepository,
                              IntegrationMessageLogRepository logRepository,
                              ObjectMapper objectMapper) {
        this.adapterRepository = adapterRepository;
        this.webhookRepository = webhookRepository;
        this.logRepository = logRepository;
        this.objectMapper = objectMapper;
    }

    // ==========================================
    // 1. 适配器生命周期服务 (Adapter Lifecycle)
    // ==========================================

    /**
     * 根据租户 ID 检索其名下所有异构适配器列表（物理多租户隔离）。
     *
     * @param tenantId 租户标识
     * @return 适配器实体列表
     */
    @Transactional(readOnly = true)
    public List<IntegrationAdapter> getAdapters(String tenantId) {
        return adapterRepository.findAllByTenantId(tenantId);
    }

    /**
     * 为当前租户注册创建一条新的外部第三方集成适配器。
     *
     * @param tenantId 租户标识
     * @param dto      新建适配器参数 DTO，含 JSR-380 输入校验
     * @return 创建成功的适配器实体
     * @throws ApiException 若 adapterId 冲突则抛出 CONFLICT 异常
     */
    @Transactional
    public IntegrationAdapter createAdapter(String tenantId, AdapterCreateDto dto) {
        Optional<IntegrationAdapter> existing = adapterRepository.findByAdapterIdAndTenantId(dto.adapterId(), tenantId);
        if (existing.isPresent()) {
            throw new ApiException(ErrorCode.CONFLICT, "适配器ID已存在: " + dto.adapterId());
        }

        IntegrationAdapter adapter = new IntegrationAdapter(
            null,
            dto.adapterId(),
            tenantId,
            dto.name(),
            dto.protocolType(),
            STATUS_ACTIVE,
            dto.configJson(),
            HEALTH_NOT_CONNECTED,
            0L,
            null,
            Instant.now(),
            "system",
            Instant.now(),
            "system"
        );

        return adapterRepository.save(adapter);
    }

    /**
     * 修改指定适配器的配置信息。
     *
     * @param tenantId  租户标识
     * @param adapterId 待修改的适配器业务 ID
     * @param dto       适配器更新信息 DTO
     * @return 更新后的适配器实体
     * @throws ApiException 若适配器不存在，则抛出 ENG_INTEG_002 错误
     */
    @Transactional
    public IntegrationAdapter updateAdapter(String tenantId, String adapterId, AdapterUpdateDto dto) {
        IntegrationAdapter adapter = adapterRepository.findByAdapterIdAndTenantId(adapterId, tenantId)
            .orElseThrow(() -> new ApiException(ErrorCode.ENG_INTEG_002, "适配器不存在: " + adapterId));

        IntegrationAdapter updated = adapter.withUpdate(dto.name(), dto.protocolType(), dto.configJson(), dto.status());
        return adapterRepository.save(updated);
    }

    /**
     * 对指定适配器执行健康检查：当前仅做本地配置预检，不连接外部系统。
     *
     * <p>未接入真实外部连接器前，
     * 无法判定外部可达性——据实返回：配置非法 → {@code MISCONFIGURED}；配置合法 → {@code NOT_CONNECTED}
     * （外部可达性未知）。绝不伪造 {@code HEALTHY} 或网络 RTT。
     *
     * @param tenantId  租户标识
     * @param adapterId 当前租户内唯一的适配器业务 ID
     * @return 更新了 healthStatus 与心跳时间的适配器实体（configJson 原值保留）
     * @throws ApiException 若适配器不存在，则抛出 ENG_INTEG_002 错误
     */
    @Transactional
    public IntegrationAdapter checkAdapterHealth(String tenantId, String adapterId) {
        IntegrationAdapter adapter = adapterRepository.findByAdapterIdAndTenantId(adapterId, tenantId)
            .orElseThrow(() -> new ApiException(ErrorCode.ENG_INTEG_002, "适配器不存在: " + adapterId));

        // 无真实外部连接器：配置合法只能标 NOT_CONNECTED（外部可达性未知），不得伪造 HEALTHY；
        // 不记录伪造网络 RTT（0 表示未做真实探活）。
        String healthStatus = isConfigJsonValid(adapter.configJson()) ? HEALTH_NOT_CONNECTED : HEALTH_MISCONFIGURED;
        IntegrationAdapter checked = adapter.withHealthCheck(healthStatus, 0L, Instant.now());
        return adapterRepository.save(checked);
    }

    /**
     * 汇总当前租户适配器目录健康状态，供工作台和可观测模块读取。
     */
    @Transactional(readOnly = true)
    public AdapterHealthSummaryDto getAdapterHealthSummary(String tenantId) {
        List<IntegrationAdapter> adapters = adapterRepository.findAllByTenantId(tenantId);
        List<AdapterHealthItemDto> items = adapters.stream()
            .map(this::toHealthItem)
            .toList();

        return new AdapterHealthSummaryDto(
            adapters.size(),
            countByStatus(adapters, STATUS_ACTIVE),
            countByStatus(adapters, STATUS_SUSPENDED),
            countByHealth(adapters, HEALTH_HEALTHY),
            countByHealth(adapters, HEALTH_NOT_CONNECTED),
            countByHealth(adapters, HEALTH_MISCONFIGURED),
            Instant.now(),
            items
        );
    }

    // ==========================================
    // 2. Webhook 订阅安全服务 (Webhook)
    // ==========================================

    /**
     * 获取指定租户下订阅的所有外部 Webhook 配置通道。
     *
     * @param tenantId 租户标识
     * @return Webhook 订阅配置列表
     */
    @Transactional(readOnly = true)
    public List<IntegrationWebhookConfig> getWebhooks(String tenantId) {
        return webhookRepository.findAllByTenantId(tenantId);
    }

    /**
     * 注册创建一条新的外部 Webhook 订阅通道。
     *
     * <p>为通道强随机生成 128 位对称共享密钥（SecretKey）用于消息签名的生成与防伪。
     *
     * @param tenantId 租户标识
     * @param dto      创建 Webhook 参数 DTO，含 JSR-380 输入校验
     * @return 创建成功的 Webhook 配置实体
     * @throws ApiException 若 Webhook ID 冲突，抛出 CONFLICT 异常
     */
    @Transactional
    public IntegrationWebhookConfig createWebhook(String tenantId, WebhookCreateDto dto) {
        Optional<IntegrationWebhookConfig> existing = webhookRepository.findByWebhookIdAndTenantId(dto.webhookId(), tenantId);
        if (existing.isPresent()) {
            throw new ApiException(ErrorCode.CONFLICT, "WebhookID已存在: " + dto.webhookId());
        }

        // 强随机生成安全签名私钥 SecretKey。
        String generatedSecret = "sec_key_" + UUID.randomUUID().toString().replace("-", "");

        IntegrationWebhookConfig config = new IntegrationWebhookConfig(
            null,
            dto.webhookId(),
            tenantId,
            dto.name(),
            dto.callbackUrl(),
            generatedSecret,
            dto.eventsSubscribed(),
            STATUS_ACTIVE,
            Instant.now(),
            "system",
            Instant.now(),
            "system"
        );

        return webhookRepository.save(config);
    }

    /**
     * 对指定 Webhook 回调通道执行双向安全签名演算连通性自测试。
     *
     * <p>结合防回放 timestamp 以及 payload 进行 HMAC-SHA256 签名推导测试。
     *
     * @param tenantId 租户标识
     * @param dto      测试报文要素 DTO (含 Webhook ID 及测试 Payload)
     * @return 包含共享密钥、签名拼接规则、防回放时间戳及最终签名的推导结果 Map
     * @throws ApiException 若 Webhook 配置不存在抛出 ENG_INTEG_003，签名计算错误抛出 INTERNAL_ERROR
     */
    @Transactional(readOnly = true)
    public WebhookTestResultDto testWebhookSignature(String tenantId, WebhookTestDto dto) {
        IntegrationWebhookConfig config = webhookRepository.findByWebhookIdAndTenantId(dto.webhookId(), tenantId)
            .orElseThrow(() -> new ApiException(ErrorCode.ENG_INTEG_003, "Webhook订阅不存在: " + dto.webhookId()));

        long timestamp = Instant.now().getEpochSecond();
        String payload = dto.payload();
        String secretKey = config.secretKey();

        // 串联规则: timestamp + "." + payload
        String dataToSign = timestamp + "." + payload;
        String signature;
        try {
            signature = hmacSha256(dataToSign, secretKey);
        } catch (Exception e) {
            throw new ApiException(ErrorCode.INTERNAL_ERROR, "HMAC-SHA256 签名生成失败: " + e.getMessage());
        }

        return new WebhookTestResultDto(
            dto.webhookId(),
            config.callbackUrl(),
            secretKey,
            timestamp,
            dataToSign,
            signature,
            "SUCCESS"
        );
    }

    // ==========================================
    // 3. 死信重试与接口存证服务 (Retry & Dead-Letter)
    // ==========================================

    /**
     * 分页查询当前租户名下所有的第三方对接数据流审计与死信日志。
     *
     * @param tenantId 租户标识
     * @param offset   分页起始游标偏移量
     * @param limit    每页最大返回行数
     * @return 消息日志实体列表
     */
    @Transactional(readOnly = true)
    public List<IntegrationMessageLog> getMessageLogs(String tenantId, int offset, int limit) {
        return logRepository.pageByTenantIdOrderByCreatedAtDesc(tenantId, offset, limit);
    }

    /**
     * 获取指定租户名下集成流审计日志的累计记录条数，用于分页计算。
     *
     * @param tenantId 租户标识
     * @return 审计日志总条数
     */
    @Transactional(readOnly = true)
    public long getMessageLogsCount(String tenantId) {
        return logRepository.countByTenantId(tenantId);
    }

    /**
     * 手动触发对指定已失败（FAILED）或死信（DEAD_LETTER）队列的消息投递重试补偿。
     *
     * <p>根据幂等规则校验，已成功的消息不再重投；每次重试累加 retry_count，达到 max_retries 后归档进 DEAD_LETTER。
     *
     * <p>当前未接入真实外部连接器，
     * 无法真正重投递到 HIS/LIS——据实处理：绝不伪造 {@code SUCCESS}。空载荷或达到最大重试 → {@code DEAD_LETTER}；
     * 否则保持 {@code FAILED} 并在 errorMessage 说明根因。
     *
     * @param tenantId  租户标识
     * @param messageId 集成日志的唯一主键 UUID
     * @return 重新投递并标记结果后的消息流日志实体
     * @throws ApiException 若流日志不存在抛出 ENG_INTEG_005，已投递成功抛出 ENG_INTEG_006
     */
    @Transactional
    public IntegrationMessageLog retryMessage(String tenantId, String messageId) {
        IntegrationMessageLog msgLog = logRepository.findByMessageIdAndTenantId(messageId, tenantId)
            .orElseThrow(() -> new ApiException(ErrorCode.ENG_INTEG_005, "接口流日志不存在: " + messageId));

        if ("SUCCESS".equals(msgLog.status())) {
            throw new ApiException(ErrorCode.ENG_INTEG_006, "交易已成功，无需重复投递: " + messageId);
        }

        int newRetryCount = msgLog.retryCount() + 1;
        boolean payloadValid = msgLog.payload() != null && !msgLog.payload().isBlank();

        // 根因：空载荷无法投递；载荷合法但无外部连接器同样无法真实重投递。两者均不得标 SUCCESS。
        String cause = payloadValid
            ? "未接入真实外部连接器，无法完成真实重投递"
            : "物理载荷报文为空(Payload is empty)";

        IntegrationMessageLog retried;
        if (newRetryCount >= msgLog.maxRetries()) {
            retried = msgLog.withRetry("DEAD_LETTER", newRetryCount,
                "投递重试超限，已强制移入死信隔离舱！根因: " + cause);
        } else {
            retried = msgLog.withRetry("FAILED", newRetryCount, "重新投递失败: " + cause);
        }

        return logRepository.save(retried);
    }

    /**
     * 根据主键手动物理/逻辑删除指定的接口集成审计或死信消息日志。
     *
     * @param tenantId  租户标识
     * @param messageId 接口流日志 UUID
     * @throws ApiException 若流日志不存在，抛出 ENG_INTEG_005 错误
     */
    @Transactional
    public void deleteMessage(String tenantId, String messageId) {
        IntegrationMessageLog msgLog = logRepository.findByMessageIdAndTenantId(messageId, tenantId)
            .orElseThrow(() -> new ApiException(ErrorCode.ENG_INTEG_005, "接口流日志不存在: " + messageId));
        logRepository.delete(msgLog);
    }


    private boolean isConfigJsonValid(String configJson) {
        if (configJson == null || configJson.isBlank()) {
            return true;
        }
        try {
            objectMapper.readTree(configJson);
            return true;
        } catch (JsonProcessingException e) {
            return false;
        }
    }

    private AdapterHealthItemDto toHealthItem(IntegrationAdapter adapter) {
        return new AdapterHealthItemDto(
            adapter.adapterId(),
            adapter.name(),
            adapter.protocolType(),
            adapter.status(),
            adapter.healthStatus(),
            adapter.rttMs(),
            adapter.lastHeartbeatAt(),
            healthMessage(adapter.healthStatus())
        );
    }

    private int countByStatus(List<IntegrationAdapter> adapters, String status) {
        return (int) adapters.stream()
            .filter(adapter -> status.equals(adapter.status()))
            .count();
    }

    private int countByHealth(List<IntegrationAdapter> adapters, String healthStatus) {
        return (int) adapters.stream()
            .filter(adapter -> healthStatus.equals(adapter.healthStatus()))
            .count();
    }

    private String healthMessage(String healthStatus) {
        return switch (healthStatus) {
            case HEALTH_HEALTHY -> "真实连接器健康检查成功；状态来自实际探活，不伪造连接";
            case HEALTH_MISCONFIGURED -> "本地配置非法，未执行外部探活；不伪造连接成功";
            default -> "未接入真实外部连接器或外部不可达；不伪造连接成功";
        };
    }

    private String hmacSha256(String data, String key) throws NoSuchAlgorithmException, InvalidKeyException {
        Mac sha256Hmac = Mac.getInstance("HmacSHA256");
        SecretKeySpec secretKey = new SecretKeySpec(key.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
        sha256Hmac.init(secretKey);
        byte[] hash = sha256Hmac.doFinal(data.getBytes(StandardCharsets.UTF_8));
        return bytesToHex(hash);
    }

    private String bytesToHex(byte[] bytes) {
        StringBuilder hexString = new StringBuilder(2 * bytes.length);
        for (byte b : bytes) {
            String hex = Integer.toHexString(0xff & b);
            if (hex.length() == 1) {
                hexString.append('0');
            }
            hexString.append(hex);
        }
        return hexString.toString();
    }
}
