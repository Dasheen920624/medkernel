package com.medkernel.engine.integration.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.medkernel.engine.integration.domain.*;
import com.medkernel.engine.integration.dto.*;
import com.medkernel.engine.integration.repository.*;
import com.medkernel.engine.terminology.StandardTerm;
import com.medkernel.engine.terminology.StandardTermRepository;
import com.medkernel.engine.terminology.TermMapping;
import com.medkernel.engine.terminology.TermMappingRepository;
import com.medkernel.engine.mpi.MpiPatientRepository;
import com.medkernel.shared.api.error.ErrorCode;
import com.medkernel.shared.api.error.ApiException;
import com.medkernel.shared.context.RequestContext;

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
    private static final String STATUS_SUCCESS = "SUCCESS";
    private static final String STATUS_FAILED = "FAILED";
    private static final String STATUS_DEAD_LETTER = "DEAD_LETTER";
    private static final String STATUS_NOT_CONNECTED = "NOT_CONNECTED";
    private static final String ONBOARDING_REQUESTED = "REQUESTED";
    private static final String ONBOARDING_AUTH_CONFIGURED = "AUTH_CONFIGURED";
    private static final String ONBOARDING_MAPPING_CONFIGURED = "MAPPING_CONFIGURED";
    private static final String ONBOARDING_ONLINE = "ONLINE";
    private static final String ONBOARDING_OFFLINE = "OFFLINE";
    private static final String ACCESS_MODE_ADAPTER = "ADAPTER";
    private static final String ACCESS_MODE_FHIR = "FHIR";
    private static final String TRUST_LOW = "LOW";
    private static final String TRUST_MEDIUM = "MEDIUM";
    private static final String TRUST_HIGH = "HIGH";
    private static final String HEALTH_HEALTHY = "HEALTHY";
    private static final String HEALTH_NOT_CONNECTED = "NOT_CONNECTED";
    private static final String HEALTH_MISCONFIGURED = "MISCONFIGURED";
    private static final String DIRECTION_INBOUND = "INBOUND";
    private static final String DIRECTION_OUTBOUND = "OUTBOUND";
    private static final String PROTOCOL_WEBHOOK = "Webhook";
    private static final String MAPPING_CONFIRMED = "CONFIRMED";
    private static final String STANDARD_ACTIVE = "ACTIVE";
    private static final long WEBHOOK_SIGNATURE_MAX_SKEW_SECONDS = 300L;

    private final IntegrationAdapterRepository adapterRepository;
    private final IntegrationWebhookConfigRepository webhookRepository;
    private final IntegrationMessageLogRepository logRepository;
    private final DataQualityReportRepository dataQualityReportRepository;
    private final IntegrationOnboardingRepository onboardingRepository;
    private final RegionalSourceRepository regionalSourceRepository;
    private final TermMappingRepository termMappingRepository;
    private final StandardTermRepository standardTermRepository;
    private final MpiPatientRepository mpiPatientRepository;
    private final ObjectMapper objectMapper;

    /**
     * 构造器注入适配器、Webhook 订阅及流日志的持久化存储库。
     */
    public IntegrationService(IntegrationAdapterRepository adapterRepository,
                              IntegrationWebhookConfigRepository webhookRepository,
                              IntegrationMessageLogRepository logRepository,
                              DataQualityReportRepository dataQualityReportRepository,
                              IntegrationOnboardingRepository onboardingRepository,
                              RegionalSourceRepository regionalSourceRepository,
                              TermMappingRepository termMappingRepository,
                              StandardTermRepository standardTermRepository,
                              MpiPatientRepository mpiPatientRepository,
                              ObjectMapper objectMapper) {
        this.adapterRepository = adapterRepository;
        this.webhookRepository = webhookRepository;
        this.logRepository = logRepository;
        this.dataQualityReportRepository = dataQualityReportRepository;
        this.onboardingRepository = onboardingRepository;
        this.regionalSourceRepository = regionalSourceRepository;
        this.termMappingRepository = termMappingRepository;
        this.standardTermRepository = standardTermRepository;
        this.mpiPatientRepository = mpiPatientRepository;
        this.objectMapper = objectMapper;
    }

    // ==========================================
    // 1. 适配器生命周期服务 (Adapter Lifecycle)
    // ==========================================

    /**
     * 根据租户 ID 检索其名下所有异构适配器列表（多租户隔离）。
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

        return adapterRepository.save(checkedAdapterHealth(adapter, Instant.now()));
    }

    /**
     * 周期探测所有启用中的第三方适配器健康状态。
     *
     * <p>当前未接入真实外部连接器，周期任务只做本地配置预检：
     * 配置合法标 {@code NOT_CONNECTED}，配置非法标 {@code MISCONFIGURED}；暂停适配器不扫描，且绝不伪造 {@code HEALTHY}。
     */
    @Transactional
    public IntegrationHealthProbeResultDto probeActiveAdapterHealth() {
        Instant checkedAt = Instant.now();
        List<IntegrationAdapter> checkedAdapters = adapterRepository.findAllByStatus(STATUS_ACTIVE).stream()
            .map(adapter -> adapterRepository.save(checkedAdapterHealth(adapter, checkedAt)))
            .toList();
        List<IntegrationHealthProbeItemDto> items = checkedAdapters.stream()
            .map(this::toHealthProbeItem)
            .toList();

        return new IntegrationHealthProbeResultDto(
            checkedAdapters.size(),
            countByHealth(checkedAdapters, HEALTH_HEALTHY),
            countByHealth(checkedAdapters, HEALTH_NOT_CONNECTED),
            countByHealth(checkedAdapters, HEALTH_MISCONFIGURED),
            checkedAt,
            items
        );
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

    /**
     * 汇总 AdapterHub 当前接入编排状态，供适配器中心页读取。
     */
    @Transactional(readOnly = true)
    public AdapterHubStatus getAdapterHubStatus(String tenantId) {
        Instant generatedAt = Instant.now();
        List<IntegrationAdapter> adapters = adapterRepository.findAllByTenantId(tenantId);
        List<AdapterHubSourceStatus> sources = adapters.stream()
            .map(adapter -> toAdapterHubSourceStatus(adapter, generatedAt))
            .toList();

        return new AdapterHubStatus(
            adapters.size(),
            countByStatus(adapters, STATUS_ACTIVE),
            countByStatus(adapters, STATUS_SUSPENDED),
            countByHealth(adapters, HEALTH_HEALTHY),
            countByHealth(adapters, HEALTH_NOT_CONNECTED),
            countByHealth(adapters, HEALTH_MISCONFIGURED),
            (int) sources.stream().filter(source -> source.mappedFieldCount() > 0).count(),
            generatedAt,
            sources
        );
    }

    /**
     * 登记第三方业务接口接入申请，接入模式可为适配器路线或标准 FHIR 门面路线。
     */
    @Transactional
    public IntegrationOnboardingResponse createIntegrationOnboarding(String tenantId,
                                                                     IntegrationOnboardingCreateRequest request) {
        if (onboardingRepository.findByOnboardingIdAndTenantId(request.onboardingId(), tenantId).isPresent()) {
            throw new ApiException(ErrorCode.CONFLICT, "接入申请已存在: " + request.onboardingId());
        }
        String accessMode = normalizeAccessMode(request.accessMode());
        String adapterId = blankToNull(request.adapterId());
        String fhirVersion = blankToNull(request.fhirVersion());
        String webhookId = blankToNull(request.callbackWebhookId());

        if (ACCESS_MODE_ADAPTER.equals(accessMode)) {
            if (adapterId == null) {
                throw new ApiException(ErrorCode.ENG_INTEG_001, "适配器接入必须绑定 adapterId");
            }
            requireAdapter(tenantId, adapterId);
        } else {
            fhirVersion = normalizeFhirVersion(fhirVersion);
        }
        if (webhookId != null) {
            requireWebhook(tenantId, webhookId);
        }

        Instant now = Instant.now();
        String actor = currentActor();
        IntegrationOnboarding saved = onboardingRepository.save(new IntegrationOnboarding(
            null,
            request.onboardingId(),
            tenantId,
            request.name(),
            accessMode,
            adapterId,
            fhirVersion,
            request.sourceSystem(),
            request.businessScenario(),
            request.orgPath(),
            webhookId,
            ONBOARDING_REQUESTED,
            "接入申请已登记，等待鉴权配置",
            now,
            actor,
            now,
            actor,
            RequestContext.currentTraceId()
        ));
        return toOnboardingResponse(saved);
    }

    /**
     * 推进第三方业务接口接入阶段；阶段完成不等于外部系统已真实连通。
     */
    @Transactional
    public IntegrationOnboardingResponse advanceIntegrationOnboarding(String tenantId,
                                                                      String onboardingId,
                                                                      IntegrationOnboardingAdvanceRequest request) {
        IntegrationOnboarding onboarding = onboardingRepository.findByOnboardingIdAndTenantId(onboardingId, tenantId)
            .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "接入申请不存在: " + onboardingId));
        String targetStatus = normalizeOnboardingStatus(request.targetStatus());
        if (!isOnboardingTransitionAllowed(onboarding.status(), targetStatus)) {
            throw new ApiException(ErrorCode.ENG_INTEG_001,
                "接入阶段不允许从 " + onboarding.status() + " 推进到 " + targetStatus);
        }
        if (ACCESS_MODE_ADAPTER.equals(onboarding.accessMode())
            && (ONBOARDING_MAPPING_CONFIGURED.equals(targetStatus) || ONBOARDING_ONLINE.equals(targetStatus))) {
            requireAdapterMappingReady(tenantId, onboarding.adapterId());
        }

        IntegrationOnboarding saved = onboardingRepository.save(
            onboarding.withStatus(targetStatus, request.evidenceText(), currentActor()));
        return toOnboardingResponse(saved);
    }

    /**
     * 查询当前租户第三方业务接口接入状态。
     */
    @Transactional(readOnly = true)
    public List<IntegrationOnboardingResponse> listIntegrationOnboardings(String tenantId) {
        return onboardingRepository.findAllByTenantId(tenantId).stream()
            .map(this::toOnboardingResponse)
            .toList();
    }

    /**
     * 登记区域协同来源。来源未完成 OPT-07 可信分级时必须显式拒绝。
     */
    @Transactional
    public RegionalSourceResponse registerRegionalSource(String tenantId, RegionalSourceRegisterRequest request) {
        String trustLevel = normalizeTrustLevel(request.trustLevel());
        if (regionalSourceRepository.findBySourceIdAndTenantId(request.sourceId(), tenantId).isPresent()) {
            throw new ApiException(ErrorCode.CONFLICT, "区域来源已存在: " + request.sourceId());
        }
        String adapterId = blankToNull(request.adapterId());
        String onboardingId = blankToNull(request.onboardingId());
        if (adapterId != null) {
            requireAdapter(tenantId, adapterId);
        }
        if (onboardingId != null) {
            onboardingRepository.findByOnboardingIdAndTenantId(onboardingId, tenantId)
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "接入申请不存在: " + onboardingId));
        }

        Instant now = Instant.now();
        String actor = currentActor();
        RegionalSource saved = regionalSourceRepository.save(new RegionalSource(
            null,
            request.sourceId(),
            tenantId,
            request.regionalNetworkName(),
            request.sourceOrganizationId(),
            request.sourceOrganizationName(),
            trustLevel,
            request.evidenceText(),
            adapterId,
            onboardingId,
            request.orgPath(),
            STATUS_ACTIVE,
            now,
            actor,
            now,
            actor,
            RequestContext.currentTraceId()
        ));
        return toRegionalSourceResponse(saved);
    }

    /**
     * 查询当前租户区域协同来源。
     */
    @Transactional(readOnly = true)
    public List<RegionalSourceResponse> listRegionalSources(String tenantId) {
        return regionalSourceRepository.findAllByTenantId(tenantId).stream()
            .map(this::toRegionalSourceResponse)
            .toList();
    }

    /**
     * 生成并持久化一次数据质量报告快照。
     */
    @Transactional
    public DataQualityReport generateDataQualityReport(String tenantId) {
        Instant generatedAt = Instant.now();
        List<IntegrationAdapter> adapters = adapterRepository.findAllByTenantId(tenantId);
        long activePatientCount = mpiPatientRepository.countActive(tenantId);
        int requiredFieldTotal = safeToInt(activePatientCount * 4);
        int requiredFieldPresent = safeToInt(mpiPatientRepository.countActiveRequiredFieldsPresent(tenantId));
        int adapterTotal = adapters.size();
        int mappedAdapterCount = (int) adapters.stream()
            .filter(adapter -> mappedFieldCount(adapter) > 0)
            .count();
        int timelyAdapterCount = (int) adapters.stream()
            .filter(adapter -> isTimely(adapter, generatedAt))
            .count();
        int notConnectedCount = countByHealth(adapters, HEALTH_NOT_CONNECTED);
        int misconfiguredCount = countByHealth(adapters, HEALTH_MISCONFIGURED);

        List<String> gaps = new ArrayList<>();
        if (requiredFieldTotal == 0) {
            gaps.add("暂无 ACTIVE MPI 患者，必填字段达标情况无法证明");
        } else if (requiredFieldPresent < requiredFieldTotal) {
            gaps.add("必填字段缺口：" + (requiredFieldTotal - requiredFieldPresent) + "/" + requiredFieldTotal);
        }
        if (adapterTotal == 0) {
            gaps.add("未登记院内系统适配器");
        } else {
            if (mappedAdapterCount < adapterTotal) {
                gaps.add("未配置字段映射：" + (adapterTotal - mappedAdapterCount) + "/" + adapterTotal);
            }
            if (timelyAdapterCount < adapterTotal) {
                gaps.add("连通核查时效缺口：" + (adapterTotal - timelyAdapterCount) + "/" + adapterTotal);
            }
        }
        if (notConnectedCount > 0) {
            gaps.add("NOT_CONNECTED 适配器：" + notConnectedCount);
        }
        if (misconfiguredCount > 0) {
            gaps.add("MISCONFIGURED 适配器：" + misconfiguredCount);
        }

        DataQualityReport report = new DataQualityReport(
            null,
            tenantId,
            generatedAt,
            requiredFieldTotal,
            requiredFieldPresent,
            rate(requiredFieldPresent, requiredFieldTotal),
            adapterTotal,
            mappedAdapterCount,
            rate(mappedAdapterCount, adapterTotal),
            timelyAdapterCount,
            rate(timelyAdapterCount, adapterTotal),
            notConnectedCount,
            misconfiguredCount,
            gaps.isEmpty() ? "未发现数据质量缺口" : String.join("；", gaps),
            generatedAt,
            RequestContext.currentUserId().orElse("system"),
            RequestContext.currentTraceId()
        );
        return dataQualityReportRepository.save(report);
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

    /**
     * 接收第三方 Webhook 入站消息，先验签再按 messageId 做租户内幂等处理。
     *
     * <p>验签失败必须拒绝并写入失败日志；验签成功后按适配器配置执行字段映射，
     * 带 {@code termMappingId} 的字段必须经 TERM-01 已确认映射归一，不能猜测标准码。
     */
    @Transactional
    public WebhookInboundResultDto ingestWebhook(String tenantId,
                                                 String webhookId,
                                                 String timestamp,
                                                 String signature,
                                                 WebhookInboundRequestDto request) {
        IntegrationWebhookConfig webhook = webhookRepository.findByWebhookIdAndTenantId(webhookId, tenantId)
            .orElseThrow(() -> new ApiException(ErrorCode.ENG_INTEG_003, "Webhook订阅不存在: " + webhookId));

        String canonicalPayload = canonicalInboundPayload(request);
        if (!isWebhookSignatureValid(timestamp, signature, canonicalPayload, webhook.secretKey())) {
            storeInboundFailure(tenantId, webhook, request, canonicalPayload, "Webhook 消息签名校验失败");
            throw new ApiException(ErrorCode.ENG_INTEG_004, "Webhook 消息签名校验失败");
        }

        Optional<IntegrationMessageLog> existing = logRepository.findByMessageIdAndTenantId(request.messageId(), tenantId);
        if (existing.isPresent()) {
            return replayExistingInboundResult(webhookId, request, existing.get());
        }

        try {
            IntegrationAdapter adapter = adapterRepository.findByAdapterIdAndTenantId(request.adapterId(), tenantId)
                .orElseThrow(() -> new ApiException(ErrorCode.ENG_INTEG_002, "适配器不存在: " + request.adapterId()));
            MappingResult mapped = mapInboundPayload(tenantId, adapter, request.payload());

            ObjectNode stored = objectMapper.createObjectNode();
            stored.put("webhookId", webhookId);
            stored.put("adapterId", request.adapterId());
            stored.set("rawPayload", request.payload());
            stored.set("mappedPayload", mapped.payload());
            stored.put("mappedFieldCount", mapped.mappedFieldCount());
            stored.put("normalizedCodeCount", mapped.normalizedCodeCount());
            stored.set("warnings", objectMapper.valueToTree(mapped.warnings()));

            IntegrationMessageLog log = new IntegrationMessageLog(
                null,
                request.messageId(),
                tenantId,
                blankToNull(request.traceId()),
                DIRECTION_INBOUND,
                blankToDefault(request.sourceSystem(), webhook.name()),
                PROTOCOL_WEBHOOK,
                "Webhook 入站验签通过，映射字段 " + mapped.mappedFieldCount()
                    + "，编码归一 " + mapped.normalizedCodeCount(),
                stored.toString(),
                STATUS_SUCCESS,
                0,
                3,
                null,
                Instant.now(),
                "system",
                Instant.now(),
                "system"
            );
            logRepository.save(log);

            return new WebhookInboundResultDto(
                request.messageId(),
                blankToNull(request.traceId()),
                webhookId,
                request.adapterId(),
                STATUS_SUCCESS,
                mapped.payload(),
                mapped.mappedFieldCount(),
                mapped.normalizedCodeCount(),
                false,
                mapped.warnings()
            );
        } catch (ApiException e) {
            storeInboundFailure(tenantId, webhook, request, canonicalPayload, e.getMessage());
            throw e;
        }
    }

    // ==========================================
    // 3. 死信重试与接口存证服务 (Retry & Dead-Letter)
    // ==========================================

    /**
     * 登记出站异步同步消息；外部系统未连接时只写补偿日志，不阻断医生主流程。
     *
     * <p>当前总线尚未接入真实发送连接器，所有出站请求均按 {@code NOT_CONNECTED}
     * 诚实落库，等待后续人工重试或死信重放，不伪造发送成功。
     */
    @Transactional
    public IntegrationOutboundResultDto enqueueOutboundMessage(String tenantId, IntegrationOutboundRequestDto request) {
        Optional<IntegrationMessageLog> existing = logRepository.findByMessageIdAndTenantId(request.messageId(), tenantId);
        if (existing.isPresent()) {
            return outboundResultFromLog(request.adapterId(), existing.get());
        }

        Optional<IntegrationAdapter> adapter = adapterRepository.findByAdapterIdAndTenantId(request.adapterId(), tenantId);
        String systemName = adapter.map(IntegrationAdapter::name)
            .orElseGet(() -> blankToDefault(request.targetSystem(), request.adapterId()));
        String protocolType = adapter.map(IntegrationAdapter::protocolType)
            .orElseGet(() -> blankToDefault(request.protocolType(), "REST"));
        String reason = adapter.isPresent()
            ? "未接入真实外部发送连接器，已登记异步补偿，不阻断主流程"
            : "适配器不存在或未接入，已登记异步补偿，不阻断主流程";

        ObjectNode stored = objectMapper.createObjectNode();
        stored.put("adapterId", request.adapterId());
        stored.put("targetSystem", request.targetSystem());
        stored.put("protocolType", request.protocolType());
        stored.set("payload", request.payload());
        stored.put("degradeReason", reason);

        IntegrationMessageLog saved = logRepository.save(new IntegrationMessageLog(
            null,
            request.messageId(),
            tenantId,
            blankToNull(request.traceId()),
            DIRECTION_OUTBOUND,
            systemName,
            protocolType,
            truncate(blankToDefault(request.payloadSummary(), "第三方出站同步已登记异步补偿"), 512),
            stored.toString(),
            STATUS_NOT_CONNECTED,
            0,
            sanitizedMaxRetries(request.maxRetries()),
            reason,
            Instant.now(),
            "system",
            Instant.now(),
            "system"
        ));

        return outboundResultFromLog(request.adapterId(), saved);
    }

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
     * 分页查询当前租户的集成死信日志，供人工排查与重放。
     */
    @Transactional(readOnly = true)
    public List<IntegrationMessageLog> getDeadLetters(String tenantId, int offset, int limit) {
        return logRepository.pageByTenantIdAndStatusOrderByUpdatedAtDesc(tenantId, STATUS_DEAD_LETTER, offset, limit);
    }

    /**
     * 查询当前租户死信日志数量。
     */
    @Transactional(readOnly = true)
    public long getDeadLettersCount(String tenantId) {
        return logRepository.countByTenantIdAndStatus(tenantId, STATUS_DEAD_LETTER);
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

        if (STATUS_SUCCESS.equals(msgLog.status())) {
            throw new ApiException(ErrorCode.ENG_INTEG_006, "交易已成功，无需重复投递: " + messageId);
        }

        int newRetryCount = msgLog.retryCount() + 1;
        boolean payloadValid = msgLog.payload() != null && !msgLog.payload().isBlank();

        // 根因：空载荷无法投递；载荷合法但无外部连接器同样无法真实重投递。两者均不得标 SUCCESS。
        String cause = payloadValid
            ? "未接入真实外部连接器，无法完成真实重投递"
            : "原始载荷报文为空(Payload is empty)";

        IntegrationMessageLog retried;
        if (newRetryCount >= msgLog.maxRetries()) {
            retried = msgLog.withRetry(STATUS_DEAD_LETTER, newRetryCount,
                "投递重试超限，已进入死信等待人工重放；根因: " + cause);
        } else {
            String retryStatus = payloadValid ? STATUS_NOT_CONNECTED : STATUS_FAILED;
            retried = msgLog.withRetry(retryStatus, newRetryCount,
                "重新投递未完成，已保留异步补偿且不阻断主流程；根因: " + cause);
        }

        return logRepository.save(retried);
    }

    /**
     * 人工重放死信消息，保留原始死信作为审计证据，并创建新的异步补偿消息。
     */
    @Transactional
    public IntegrationReplayResultDto replayDeadLetter(String tenantId, String messageId) {
        IntegrationMessageLog deadLetter = logRepository.findByMessageIdAndTenantId(messageId, tenantId)
            .orElseThrow(() -> new ApiException(ErrorCode.ENG_INTEG_005, "接口流日志不存在: " + messageId));
        if (!STATUS_DEAD_LETTER.equals(deadLetter.status())) {
            throw new ApiException(ErrorCode.ENG_INTEG_006, "只有 DEAD_LETTER 状态的接口日志可以人工重放: " + messageId);
        }

        String replayMessageId = "replay-" + UUID.randomUUID().toString().replace("-", "");
        IntegrationMessageLog replay = logRepository.save(new IntegrationMessageLog(
            null,
            replayMessageId,
            tenantId,
            deadLetter.traceId(),
            deadLetter.direction(),
            deadLetter.systemName(),
            deadLetter.protocolType(),
            truncate("死信人工重放: " + blankToDefault(deadLetter.payloadSummary(), messageId), 512),
            deadLetter.payload(),
            STATUS_NOT_CONNECTED,
            0,
            deadLetter.maxRetries(),
            "死信已人工重放为新的异步补偿消息；原死信保留为审计证据，不阻断主流程",
            Instant.now(),
            "system",
            Instant.now(),
            "system"
        ));
        return new IntegrationReplayResultDto(
            messageId,
            replay.messageId(),
            replay.traceId(),
            replay.status(),
            false,
            "死信已创建重放补偿消息，原始证据已保留"
        );
    }

    /**
     * 回调管理视角的死信重放入口，复用同一补偿链路，避免产生第二套死信语义。
     */
    @Transactional
    public IntegrationReplayResultDto replayCallbackDeadLetter(String tenantId, String messageId) {
        return replayDeadLetter(tenantId, messageId);
    }

    private IntegrationOnboardingResponse toOnboardingResponse(IntegrationOnboarding onboarding) {
        Optional<IntegrationAdapter> adapter = ACCESS_MODE_ADAPTER.equals(onboarding.accessMode())
            ? adapterRepository.findByAdapterIdAndTenantId(onboarding.adapterId(), onboarding.tenantId())
            : Optional.empty();
        int mappedFieldCount = adapter.map(this::mappedFieldCount).orElse(0);
        String healthStatus = adapter.map(IntegrationAdapter::healthStatus).orElse(HEALTH_NOT_CONNECTED);
        List<String> blockers = onboardingBlockers(onboarding, adapter, mappedFieldCount, healthStatus);
        String routeReference = ACCESS_MODE_FHIR.equals(onboarding.accessMode())
            ? "/api/v1/engine/integration/fhir/" + onboarding.fhirVersion()
            : "/api/v1/engine/integration/adapters/" + onboarding.adapterId();

        return new IntegrationOnboardingResponse(
            onboarding.onboardingId(),
            onboarding.name(),
            onboarding.status(),
            onboarding.accessMode(),
            routeReference,
            healthStatus,
            mappedFieldCount,
            blockers,
            onboarding.sourceSystem(),
            onboarding.businessScenario(),
            onboarding.orgPath(),
            onboarding.callbackWebhookId(),
            onboarding.createdAt(),
            onboarding.updatedAt()
        );
    }

    private List<String> onboardingBlockers(IntegrationOnboarding onboarding,
                                            Optional<IntegrationAdapter> adapter,
                                            int mappedFieldCount,
                                            String healthStatus) {
        List<String> blockers = new ArrayList<>();
        if (ONBOARDING_REQUESTED.equals(onboarding.status())) {
            blockers.add("未完成鉴权配置");
        }
        if ((ONBOARDING_REQUESTED.equals(onboarding.status()) || ONBOARDING_AUTH_CONFIGURED.equals(onboarding.status()))
            && ACCESS_MODE_ADAPTER.equals(onboarding.accessMode())) {
            blockers.add("未完成字段映射");
        }
        if (ACCESS_MODE_ADAPTER.equals(onboarding.accessMode()) && mappedFieldCount == 0) {
            blockers.add("未配置字段映射");
        }
        if (adapter.isEmpty() && ACCESS_MODE_ADAPTER.equals(onboarding.accessMode())) {
            blockers.add("绑定适配器不存在");
        }
        if (HEALTH_NOT_CONNECTED.equals(healthStatus)) {
            blockers.add("NOT_CONNECTED：未接入真实外部连接器，不阻断主流程");
        }
        if (HEALTH_MISCONFIGURED.equals(healthStatus)) {
            blockers.add("MISCONFIGURED：适配器配置非法，需修正后再联调");
        }
        return List.copyOf(blockers);
    }

    private RegionalSourceResponse toRegionalSourceResponse(RegionalSource source) {
        return new RegionalSourceResponse(
            source.sourceId(),
            source.regionalNetworkName(),
            source.sourceOrganizationId(),
            source.sourceOrganizationName(),
            source.trustLevel(),
            source.evidenceText(),
            source.adapterId(),
            source.onboardingId(),
            source.orgPath(),
            source.status(),
            source.createdAt(),
            source.updatedAt()
        );
    }

    private IntegrationAdapter requireAdapter(String tenantId, String adapterId) {
        return adapterRepository.findByAdapterIdAndTenantId(adapterId, tenantId)
            .orElseThrow(() -> new ApiException(ErrorCode.ENG_INTEG_002, "适配器不存在: " + adapterId));
    }

    private IntegrationWebhookConfig requireWebhook(String tenantId, String webhookId) {
        return webhookRepository.findByWebhookIdAndTenantId(webhookId, tenantId)
            .orElseThrow(() -> new ApiException(ErrorCode.ENG_INTEG_003, "Webhook订阅不存在: " + webhookId));
    }

    private void requireAdapterMappingReady(String tenantId, String adapterId) {
        IntegrationAdapter adapter = requireAdapter(tenantId, adapterId);
        if (fieldMappingRules(adapter).isEmpty()) {
            throw new ApiException(ErrorCode.ENG_INTEG_001, "适配器未配置字段映射: " + adapterId);
        }
    }

    private String normalizeAccessMode(String accessMode) {
        String normalized = normalizeUpper(accessMode);
        if (!ACCESS_MODE_ADAPTER.equals(normalized) && !ACCESS_MODE_FHIR.equals(normalized)) {
            throw new ApiException(ErrorCode.ENG_INTEG_001, "接入模式必须为 ADAPTER 或 FHIR");
        }
        return normalized;
    }

    private String normalizeFhirVersion(String fhirVersion) {
        String normalized = normalizeUpper(fhirVersion);
        if (!"R4".equals(normalized) && !"R5".equals(normalized)) {
            throw new ApiException(ErrorCode.ENG_INTEG_001, "FHIR 接入必须声明 R4 或 R5 版本");
        }
        return normalized;
    }

    private String normalizeOnboardingStatus(String status) {
        String normalized = normalizeUpper(status);
        if (!List.of(
            ONBOARDING_REQUESTED,
            ONBOARDING_AUTH_CONFIGURED,
            ONBOARDING_MAPPING_CONFIGURED,
            ONBOARDING_ONLINE,
            ONBOARDING_OFFLINE
        ).contains(normalized)) {
            throw new ApiException(ErrorCode.ENG_INTEG_001, "接入阶段不合法: " + status);
        }
        return normalized;
    }

    private String normalizeTrustLevel(String trustLevel) {
        String normalized = normalizeUpper(trustLevel);
        if (normalized.isBlank()) {
            throw new ApiException(ErrorCode.REGIONAL_SOURCE_UNGRADED, "区域来源未完成可信分级");
        }
        if (!TRUST_LOW.equals(normalized) && !TRUST_MEDIUM.equals(normalized) && !TRUST_HIGH.equals(normalized)) {
            throw new ApiException(ErrorCode.ENG_INTEG_001, "区域来源可信分级必须为 LOW/MEDIUM/HIGH");
        }
        return normalized;
    }

    private boolean isOnboardingTransitionAllowed(String currentStatus, String targetStatus) {
        if (currentStatus.equals(targetStatus) || ONBOARDING_OFFLINE.equals(targetStatus)) {
            return true;
        }
        return switch (currentStatus) {
            case ONBOARDING_REQUESTED -> ONBOARDING_AUTH_CONFIGURED.equals(targetStatus);
            case ONBOARDING_AUTH_CONFIGURED -> ONBOARDING_MAPPING_CONFIGURED.equals(targetStatus);
            case ONBOARDING_MAPPING_CONFIGURED -> ONBOARDING_ONLINE.equals(targetStatus);
            default -> false;
        };
    }

    private String normalizeUpper(String value) {
        return value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
    }

    private String currentActor() {
        return RequestContext.currentUserId().orElse("system");
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

    private IntegrationAdapter checkedAdapterHealth(IntegrationAdapter adapter, Instant checkedAt) {
        // 无真实外部连接器：配置合法只能标 NOT_CONNECTED（外部可达性未知），不得伪造 HEALTHY；
        // 不记录伪造网络 RTT（0 表示未做真实探活）。
        String healthStatus = isConfigJsonValid(adapter.configJson()) ? HEALTH_NOT_CONNECTED : HEALTH_MISCONFIGURED;
        return adapter.withHealthCheck(healthStatus, 0L, checkedAt);
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

    private AdapterHubSourceStatus toAdapterHubSourceStatus(IntegrationAdapter adapter, Instant generatedAt) {
        List<String> gaps = new ArrayList<>();
        int mappedFieldCount = mappedFieldCount(adapter);
        if (mappedFieldCount == 0) {
            gaps.add("未配置字段映射");
        }
        if (HEALTH_MISCONFIGURED.equals(adapter.healthStatus())) {
            gaps.add("适配器配置非法");
        }
        if (HEALTH_NOT_CONNECTED.equals(adapter.healthStatus())) {
            gaps.add("未连接真实外部系统");
        }
        if (!isTimely(adapter, generatedAt)) {
            gaps.add("未完成 24 小时内连通核查");
        }
        return new AdapterHubSourceStatus(
            adapter.adapterId(),
            adapter.name(),
            adapter.protocolType(),
            adapter.status(),
            adapter.healthStatus(),
            mappedFieldCount,
            adapter.lastHeartbeatAt(),
            List.copyOf(gaps)
        );
    }

    private IntegrationHealthProbeItemDto toHealthProbeItem(IntegrationAdapter adapter) {
        return new IntegrationHealthProbeItemDto(
            adapter.tenantId(),
            adapter.adapterId(),
            adapter.name(),
            adapter.protocolType(),
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

    private int mappedFieldCount(IntegrationAdapter adapter) {
        if (adapter.configJson() == null || adapter.configJson().isBlank()) {
            return 0;
        }
        try {
            JsonNode mappings = objectMapper.readTree(adapter.configJson()).path("fieldMappings");
            return mappings.isArray() ? mappings.size() : 0;
        } catch (JsonProcessingException e) {
            return 0;
        }
    }

    private boolean isTimely(IntegrationAdapter adapter, Instant now) {
        return adapter.lastHeartbeatAt() != null
            && !adapter.lastHeartbeatAt().isBefore(now.minus(Duration.ofHours(24)));
    }

    private double rate(int numerator, int denominator) {
        if (denominator <= 0) {
            return 0.0;
        }
        return Math.round((numerator * 10000.0 / denominator)) / 100.0;
    }

    private int safeToInt(long value) {
        return value > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) value;
    }

    private String healthMessage(String healthStatus) {
        return switch (healthStatus) {
            case HEALTH_HEALTHY -> "真实连接器健康检查成功；状态来自实际探活，不伪造连接";
            case HEALTH_MISCONFIGURED -> "本地配置非法，未执行外部探活；不伪造连接成功";
            default -> "未接入真实外部连接器或外部不可达；不伪造连接成功";
        };
    }

    private WebhookInboundResultDto replayExistingInboundResult(String webhookId,
                                                               WebhookInboundRequestDto request,
                                                               IntegrationMessageLog existing) {
        JsonNode mappedPayload = objectMapper.createObjectNode();
        int mappedFieldCount = 0;
        int normalizedCodeCount = 0;
        List<String> warnings = List.of();
        if (existing.payload() != null && !existing.payload().isBlank()) {
            try {
                JsonNode stored = objectMapper.readTree(existing.payload());
                JsonNode storedMappedPayload = stored.path("mappedPayload");
                if (!storedMappedPayload.isMissingNode()) {
                    mappedPayload = storedMappedPayload;
                }
                mappedFieldCount = stored.path("mappedFieldCount").asInt(0);
                normalizedCodeCount = stored.path("normalizedCodeCount").asInt(0);
                warnings = readWarnings(stored.path("warnings"));
            } catch (JsonProcessingException ignored) {
                warnings = List.of("历史入站日志载荷不是标准 JSON，已按幂等结果返回状态");
            }
        }
        return new WebhookInboundResultDto(
            existing.messageId(),
            existing.traceId(),
            webhookId,
            request.adapterId(),
            existing.status(),
            mappedPayload,
            mappedFieldCount,
            normalizedCodeCount,
            true,
            warnings
        );
    }

    private void storeInboundFailure(String tenantId,
                                     IntegrationWebhookConfig webhook,
                                     WebhookInboundRequestDto request,
                                     String canonicalPayload,
                                     String reason) {
        if (logRepository.findByMessageIdAndTenantId(request.messageId(), tenantId).isPresent()) {
            return;
        }
        logRepository.save(new IntegrationMessageLog(
            null,
            request.messageId(),
            tenantId,
            blankToNull(request.traceId()),
            DIRECTION_INBOUND,
            blankToDefault(request.sourceSystem(), webhook.name()),
            PROTOCOL_WEBHOOK,
            "Webhook 入站处理失败",
            canonicalPayload,
            STATUS_FAILED,
            0,
            3,
            reason,
            Instant.now(),
            "system",
            Instant.now(),
            "system"
        ));
    }

    private MappingResult mapInboundPayload(String tenantId, IntegrationAdapter adapter, JsonNode rawPayload) {
        ObjectNode mappedPayload = objectMapper.createObjectNode();
        List<String> warnings = new ArrayList<>();
        int mappedFieldCount = 0;
        int normalizedCodeCount = 0;

        for (FieldMappingRule rule : fieldMappingRules(adapter)) {
            JsonNode sourceValue = rawPayload.at(rule.sourcePath());
            if (sourceValue.isMissingNode() || sourceValue.isNull()) {
                warnings.add("字段缺失，未映射: " + rule.sourcePath());
                continue;
            }
            JsonNode targetValue = sourceValue.deepCopy();
            if (rule.termMappingId() != null) {
                targetValue = normalizeCodeByTermMapping(tenantId, rule.termMappingId(), sourceValue);
                normalizedCodeCount++;
            }
            writeJsonPointer(mappedPayload, rule.targetPath(), targetValue);
            mappedFieldCount++;
        }

        return new MappingResult(mappedPayload, mappedFieldCount, normalizedCodeCount, List.copyOf(warnings));
    }

    private List<FieldMappingRule> fieldMappingRules(IntegrationAdapter adapter) {
        JsonNode root;
        try {
            root = adapter.configJson() == null || adapter.configJson().isBlank()
                ? objectMapper.createObjectNode()
                : objectMapper.readTree(adapter.configJson());
        } catch (JsonProcessingException e) {
            throw new ApiException(ErrorCode.ENG_INTEG_001, "适配器字段映射配置不是合法 JSON: " + adapter.adapterId());
        }
        JsonNode mappings = root.path("fieldMappings");
        if (!mappings.isArray() || mappings.isEmpty()) {
            throw new ApiException(ErrorCode.ENG_INTEG_001, "适配器未配置字段映射: " + adapter.adapterId());
        }

        List<FieldMappingRule> rules = new ArrayList<>();
        for (JsonNode mapping : mappings) {
            String sourcePath = requiredText(mapping, "sourcePath", adapter.adapterId());
            String targetPath = requiredText(mapping, "targetPath", adapter.adapterId());
            Long termMappingId = mapping.hasNonNull("termMappingId") ? mapping.path("termMappingId").asLong() : null;
            validateJsonPointer(sourcePath, "sourcePath");
            validateJsonPointer(targetPath, "targetPath");
            rules.add(new FieldMappingRule(sourcePath, targetPath, termMappingId));
        }
        return List.copyOf(rules);
    }

    private JsonNode normalizeCodeByTermMapping(String tenantId, Long termMappingId, JsonNode sourceValue) {
        TermMapping mapping = termMappingRepository.findByTenantIdAndId(tenantId, termMappingId)
            .orElseThrow(() -> new ApiException(ErrorCode.ENG_INTEG_001, "术语映射不存在: " + termMappingId));
        if (!MAPPING_CONFIRMED.equals(mapping.statusName())) {
            throw new ApiException(ErrorCode.ENG_INTEG_001, "术语映射尚未确认，禁止入站归一: " + termMappingId);
        }
        StandardTerm standard = standardTermRepository.findByTenantIdAndId(tenantId, mapping.standardTermId())
            .orElseThrow(() -> new ApiException(ErrorCode.ENG_INTEG_001, "标准术语不存在: " + mapping.standardTermId()));
        if (!STANDARD_ACTIVE.equals(standard.statusName())) {
            throw new ApiException(ErrorCode.ENG_INTEG_001, "标准术语已禁用，禁止入站归一: " + standard.termCode());
        }

        ObjectNode normalized = objectMapper.createObjectNode();
        normalized.put("system", standard.standardSystem());
        normalized.put("code", standard.termCode());
        normalized.put("display", standard.displayName());
        normalized.put("version", standard.versionNo());
        normalized.put("sourceValue", sourceValue.isValueNode() ? sourceValue.asText() : sourceValue.toString());
        normalized.put("termMappingId", termMappingId);
        return normalized;
    }

    private void writeJsonPointer(ObjectNode root, String targetPath, JsonNode value) {
        String[] parts = targetPath.substring(1).split("/");
        ObjectNode current = root;
        for (int i = 0; i < parts.length - 1; i++) {
            String part = decodeJsonPointerPart(parts[i]);
            JsonNode child = current.get(part);
            if (child == null || child.isNull()) {
                ObjectNode created = objectMapper.createObjectNode();
                current.set(part, created);
                current = created;
            } else if (child.isObject()) {
                current = (ObjectNode) child;
            } else {
                throw new ApiException(ErrorCode.ENG_INTEG_001, "字段映射目标路径冲突: " + targetPath);
            }
        }
        current.set(decodeJsonPointerPart(parts[parts.length - 1]), value.deepCopy());
    }

    private String requiredText(JsonNode node, String fieldName, String adapterId) {
        String value = node.path(fieldName).asText(null);
        if (value == null || value.isBlank()) {
            throw new ApiException(ErrorCode.ENG_INTEG_001, "适配器字段映射缺少 " + fieldName + ": " + adapterId);
        }
        return value;
    }

    private void validateJsonPointer(String path, String fieldName) {
        if (!path.startsWith("/") || path.length() == 1) {
            throw new ApiException(ErrorCode.ENG_INTEG_001, "字段映射 " + fieldName + " 必须使用 JSON Pointer: " + path);
        }
    }

    private String decodeJsonPointerPart(String part) {
        return part.replace("~1", "/").replace("~0", "~");
    }

    private String canonicalInboundPayload(WebhookInboundRequestDto request) {
        try {
            return objectMapper.writeValueAsString(request);
        } catch (JsonProcessingException e) {
            throw new ApiException(ErrorCode.ENG_INTEG_001, "Webhook 入站载荷无法序列化: " + e.getMessage());
        }
    }

    private boolean isWebhookSignatureValid(String timestamp, String signature, String canonicalPayload, String secretKey) {
        if (timestamp == null || timestamp.isBlank() || signature == null || signature.isBlank()) {
            return false;
        }
        String normalizedTimestamp = timestamp.trim();
        if (!isWebhookTimestampFresh(normalizedTimestamp)) {
            return false;
        }
        try {
            String expected = hmacSha256(normalizedTimestamp + "." + canonicalPayload, secretKey);
            String normalized = normalizeSignature(signature);
            return MessageDigest.isEqual(
                expected.getBytes(StandardCharsets.UTF_8),
                normalized.getBytes(StandardCharsets.UTF_8)
            );
        } catch (Exception e) {
            throw new ApiException(ErrorCode.INTERNAL_ERROR, "HMAC-SHA256 签名校验失败: " + e.getMessage());
        }
    }

    private boolean isWebhookTimestampFresh(String timestamp) {
        try {
            long signedAt = Long.parseLong(timestamp);
            long delta = Instant.now().getEpochSecond() - signedAt;
            return delta >= -WEBHOOK_SIGNATURE_MAX_SKEW_SECONDS && delta <= WEBHOOK_SIGNATURE_MAX_SKEW_SECONDS;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    private String normalizeSignature(String signature) {
        String normalized = signature.trim().toLowerCase(Locale.ROOT);
        if (normalized.startsWith("sha256=")) {
            return normalized.substring("sha256=".length());
        }
        return normalized;
    }

    private List<String> readWarnings(JsonNode warningsNode) {
        if (!warningsNode.isArray()) {
            return List.of();
        }
        List<String> warnings = new ArrayList<>();
        warningsNode.forEach(item -> warnings.add(item.asText()));
        return List.copyOf(warnings);
    }

    private IntegrationOutboundResultDto outboundResultFromLog(String adapterId, IntegrationMessageLog log) {
        return new IntegrationOutboundResultDto(
            log.messageId(),
            log.traceId(),
            adapterId,
            log.status(),
            false,
            !STATUS_SUCCESS.equals(log.status()),
            log.errorMessage() == null ? "出站消息已登记" : log.errorMessage()
        );
    }

    private int sanitizedMaxRetries(Integer maxRetries) {
        if (maxRetries == null) {
            return 3;
        }
        return Math.max(1, Math.min(maxRetries, 10));
    }

    private String truncate(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength);
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }

    private String blankToDefault(String value, String defaultValue) {
        return value == null || value.isBlank() ? defaultValue : value;
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

    private record FieldMappingRule(String sourcePath, String targetPath, Long termMappingId) {
    }

    private record MappingResult(JsonNode payload, int mappedFieldCount, int normalizedCodeCount, List<String> warnings) {
    }
}
