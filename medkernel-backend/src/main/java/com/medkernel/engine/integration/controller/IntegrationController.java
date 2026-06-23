package com.medkernel.engine.integration.controller;

import java.util.List;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import com.medkernel.engine.integration.domain.*;
import com.medkernel.engine.integration.dto.*;
import com.medkernel.engine.integration.service.IntegrationDataContractService;
import com.medkernel.engine.integration.service.IntegrationService;
import com.medkernel.shared.api.*;
import com.medkernel.shared.api.error.ApiException;
import com.medkernel.shared.audit.*;
import com.medkernel.shared.context.RequestContext;
import com.medkernel.shared.datascope.DataScope;

/**
 * 第三方系统对接总线及集成控制器。
 *
 * <p>提供多租户隔离下的异构系统适配器管理、健康检查诊断、Webhook 动态安全订阅、
 * 接口流审计死信队列手动重试投递及补偿、跨域通信及 launch token 免登接入审计等功能。
 */
@RestController
@RequestMapping("/api/v1/engine/integration")
@DataScope(requireTenant = true)
public class IntegrationController {

    private final IntegrationService integrationService;
    private final IntegrationDataContractService dataContractService;
    private final AuditEventPublisher auditEventPublisher;
    private final IsolatedAuditPublisher isolatedAuditPublisher;

    /**
     * 构造器注入外部集成服务与审计日志发布组件。
     */
    public IntegrationController(IntegrationService integrationService,
                                 AuditEventPublisher auditEventPublisher,
                                 IsolatedAuditPublisher isolatedAuditPublisher,
                                 IntegrationDataContractService dataContractService) {
        this.integrationService = integrationService;
        this.dataContractService = dataContractService;
        this.auditEventPublisher = auditEventPublisher;
        this.isolatedAuditPublisher = isolatedAuditPublisher;
    }

    /** 读取当前医院运行修订对应的第三方数据接入字段契约。 */
    @GetMapping("/data-contract")
    @PreAuthorize("@perm.has('integration.read')")
    public ApiResult<IntegrationDataContractResponse> getDataContract() {
        return ApiResult.ok(dataContractService.generate());
    }

    /**
     * 获取当前租户下注册的所有第三方集成适配器配置列表。
     *
     * @return 包含适配器列表的统一 API 返回实体
     */
    @GetMapping("/adapters")
    @PreAuthorize("@perm.has('integration.read')")
    public ApiResult<PageResponse<IntegrationAdapter>> getAdapters(
            @RequestParam(value = "page", defaultValue = "1") int page,
            @RequestParam(value = "size", defaultValue = "20") int size,
            @RequestParam(value = "sort", required = false) String sort) {
        String tenantId = RequestContext.currentOrgScope().tenantId();
        return ApiResult.ok(integrationService.getAdapters(tenantId, new PageRequest(page, size, sort)));
    }

    /**
     * 注册/创建一条新的外部第三方适配器。
     *
     * <p>创建成功后在当前事务发布一条普通审计记录；如抛出异常，则通过
     * {@link IsolatedAuditPublisher} 开启独立子事务强行持久化失败审计。
     *
     * @param dto 适配器创建 DTO，含 JSR-380 输入校验
     * @return 创建后的适配器实体对象
     */
    @PostMapping("/adapters")
    @PreAuthorize("@perm.has('integration.write')")
    public ApiResult<IntegrationAdapter> createAdapter(@Validated @RequestBody AdapterCreateDto dto) {
        String tenantId = RequestContext.currentOrgScope().tenantId();
        try {
            IntegrationAdapter adapter = integrationService.createAdapter(tenantId, dto);
            auditEventPublisher.publish(AuditEvent.of(
                AuditAction.CREATE,
                "integration_adapter",
                dto.adapterId(),
                "新建第三方适配器: " + dto.name()
            ));
            return ApiResult.ok(adapter);
        } catch (ApiException e) {
            isolatedAuditPublisher.publishInNewTx(AuditEvent.failure(
                AuditAction.CREATE,
                "integration_adapter",
                dto.adapterId(),
                e.errorCode().code(),
                "新建第三方适配器失败: " + e.getMessage()
            ));
            throw e;
        }
    }

    /**
     * 更新已有第三方适配器系统的配置。
     *
     * <p>包含协议类型变更及系统挂起 / 挂载操作，若更新失败同样会记录事务审计日志。
     *
     * @param adapterId 适配器全局唯一 ID
     * @param dto       更新信息 DTO
     * @return 更新后的适配器实体对象
     */
    @PutMapping("/adapters/{id}")
    @PreAuthorize("@perm.has('integration.write')")
    public ApiResult<IntegrationAdapter> updateAdapter(@PathVariable("id") String adapterId,
                                                       @Validated @RequestBody AdapterUpdateDto dto) {
        String tenantId = RequestContext.currentOrgScope().tenantId();
        try {
            IntegrationAdapter adapter = integrationService.updateAdapter(tenantId, adapterId, dto);
            auditEventPublisher.publish(AuditEvent.of(
                AuditAction.UPDATE,
                "integration_adapter",
                adapterId,
                "更新第三方适配器: " + dto.name()
            ));
            return ApiResult.ok(adapter);
        } catch (ApiException e) {
            isolatedAuditPublisher.publishInNewTx(AuditEvent.failure(
                AuditAction.UPDATE,
                "integration_adapter",
                adapterId,
                e.errorCode().code(),
                "更新第三方适配器失败: " + e.getMessage()
            ));
            throw e;
        }
    }

    /**
     * 获取当前租户适配器健康目录汇总。
     *
     * @return 适配器数量、连通状态分布和逐项健康说明
     */
    @GetMapping("/health")
    @PreAuthorize("@perm.has('integration.read')")
    public ApiResult<AdapterHealthSummaryDto> getAdapterHealthSummary() {
        String tenantId = RequestContext.currentOrgScope().tenantId();
        return ApiResult.ok(integrationService.getAdapterHealthSummary(tenantId));
    }

    /**
     * 获取 AdapterHub 接入编排实时状态。
     */
    @GetMapping("/adapter-hub/status")
    @PreAuthorize("@perm.has('integration.read')")
    public ApiResult<AdapterHubStatus> getAdapterHubStatus() {
        String tenantId = RequestContext.currentOrgScope().tenantId();
        return ApiResult.ok(integrationService.getAdapterHubStatus(tenantId));
    }

    /**
     * 生成并持久化当前租户数据质量报告快照。
     */
    @PostMapping("/data-quality/reports")
    @PreAuthorize("@perm.has('integration.execute')")
    public ApiResult<DataQualityReport> generateDataQualityReport() {
        String tenantId = RequestContext.currentOrgScope().tenantId();
        return ApiResult.ok(integrationService.generateDataQualityReport(tenantId));
    }

    /**
     * 查询第三方业务接口接入生命周期档案。
     */
    @GetMapping("/onboardings")
    @PreAuthorize("@perm.has('integration.read')")
    public ApiResult<PageResponse<IntegrationOnboardingResponse>> listIntegrationOnboardings(
            @RequestParam(value = "page", defaultValue = "1") int page,
            @RequestParam(value = "size", defaultValue = "20") int size,
            @RequestParam(value = "sort", required = false) String sort) {
        String tenantId = RequestContext.currentOrgScope().tenantId();
        return ApiResult.ok(integrationService.listIntegrationOnboardings(
            tenantId,
            new PageRequest(page, size, sort)));
    }

    /**
     * 创建第三方业务接口接入申请。
     */
    @PostMapping("/onboardings")
    @PreAuthorize("@perm.has('integration.write')")
    public ApiResult<IntegrationOnboardingResponse> createIntegrationOnboarding(
            @Validated @RequestBody IntegrationOnboardingCreateRequest request) {
        String tenantId = RequestContext.currentOrgScope().tenantId();
        try {
            IntegrationOnboardingResponse response = integrationService.createIntegrationOnboarding(tenantId, request);
            auditEventPublisher.publish(AuditEvent.of(
                AuditAction.CREATE,
                "mk_integration_onboarding",
                request.onboardingId(),
                "创建第三方业务接口接入申请: " + request.name()
            ));
            return ApiResult.ok(response);
        } catch (ApiException e) {
            isolatedAuditPublisher.publishInNewTx(AuditEvent.failure(
                AuditAction.CREATE,
                "mk_integration_onboarding",
                request.onboardingId(),
                e.errorCode().code(),
                "创建第三方业务接口接入申请失败: " + e.getMessage()
            ));
            throw e;
        }
    }

    /**
     * 推进第三方业务接口接入生命周期阶段。
     */
    @PostMapping("/onboardings/{id}/advance")
    @PreAuthorize("@perm.has('integration.execute')")
    public ApiResult<IntegrationOnboardingResponse> advanceIntegrationOnboarding(
            @PathVariable("id") String onboardingId,
            @Validated @RequestBody IntegrationOnboardingAdvanceRequest request) {
        String tenantId = RequestContext.currentOrgScope().tenantId();
        try {
            IntegrationOnboardingResponse response =
                integrationService.advanceIntegrationOnboarding(tenantId, onboardingId, request);
            auditEventPublisher.publish(AuditEvent.of(
                AuditAction.UPDATE,
                "mk_integration_onboarding",
                onboardingId,
                "推进第三方业务接口接入阶段到: " + response.status()
            ));
            return ApiResult.ok(response);
        } catch (ApiException e) {
            isolatedAuditPublisher.publishInNewTx(AuditEvent.failure(
                AuditAction.UPDATE,
                "mk_integration_onboarding",
                onboardingId,
                e.errorCode().code(),
                "推进第三方业务接口接入阶段失败: " + e.getMessage()
            ));
            throw e;
        }
    }

    /**
     * 查询区域协同来源及可信分级。
     */
    @GetMapping("/regional-sources")
    @PreAuthorize("@perm.has('integration.read')")
    public ApiResult<PageResponse<RegionalSourceResponse>> listRegionalSources(
            @RequestParam(value = "page", defaultValue = "1") int page,
            @RequestParam(value = "size", defaultValue = "20") int size,
            @RequestParam(value = "sort", required = false) String sort) {
        String tenantId = RequestContext.currentOrgScope().tenantId();
        return ApiResult.ok(integrationService.listRegionalSources(
            tenantId,
            new PageRequest(page, size, sort)));
    }

    /**
     * 登记区域协同来源。未完成可信分级时服务层拒绝保存。
     */
    @PostMapping("/regional-sources")
    @PreAuthorize("@perm.has('integration.write')")
    public ApiResult<RegionalSourceResponse> registerRegionalSource(
            @Validated @RequestBody RegionalSourceRegisterRequest request) {
        String tenantId = RequestContext.currentOrgScope().tenantId();
        try {
            RegionalSourceResponse response = integrationService.registerRegionalSource(tenantId, request);
            auditEventPublisher.publish(AuditEvent.of(
                AuditAction.CREATE,
                "mk_integration_regional_source",
                request.sourceId(),
                "登记区域协同来源: " + request.sourceOrganizationName()
            ));
            return ApiResult.ok(response);
        } catch (ApiException e) {
            isolatedAuditPublisher.publishInNewTx(AuditEvent.failure(
                AuditAction.CREATE,
                "mk_integration_regional_source",
                request.sourceId(),
                e.errorCode().code(),
                "登记区域协同来源失败: " + e.getMessage()
            ));
            throw e;
        }
    }

    /**
     * 手动触发指定第三方系统适配器健康检查。
     *
     * @param adapterId 适配器全局唯一 ID
     * @return 包含最新健康状态的适配器实体
     */
    @PostMapping("/adapters/{id}/health-check")
    @PreAuthorize("@perm.has('integration.execute')")
    public ApiResult<IntegrationAdapter> checkAdapterHealth(@PathVariable("id") String adapterId) {
        String tenantId = RequestContext.currentOrgScope().tenantId();
        try {
            IntegrationAdapter adapter = integrationService.checkAdapterHealth(tenantId, adapterId);
            auditEventPublisher.publish(AuditEvent.of(
                AuditAction.EXECUTE,
                "integration_adapter",
                adapterId,
                "适配器健康检查完成，状态: " + adapter.healthStatus()
            ));
            return ApiResult.ok(adapter);
        } catch (ApiException e) {
            isolatedAuditPublisher.publishInNewTx(AuditEvent.failure(
                AuditAction.EXECUTE,
                "integration_adapter",
                adapterId,
                e.errorCode().code(),
                "适配器健康检查失败: " + e.getMessage()
            ));
            throw e;
        }
    }

    /**
     * 获取当前租户下订阅的所有 Webhook 场景通知配置列表。
     *
     * @return Webhook 订阅配置列表
     */
    @GetMapping("/webhooks")
    @PreAuthorize("@perm.has('integration.read')")
    public ApiResult<PageResponse<WebhookConfigResponse>> getWebhooks(
            @RequestParam(value = "page", defaultValue = "1") int page,
            @RequestParam(value = "size", defaultValue = "20") int size,
            @RequestParam(value = "sort", required = false) String sort) {
        String tenantId = RequestContext.currentOrgScope().tenantId();
        return ApiResult.ok(integrationService.getWebhooks(
            tenantId,
            new PageRequest(page, size, sort)));
    }

    /**
     * 创建一条外部 Webhook 订阅，动态绑定待回调的事件场景列表。
     *
     * @param dto 创建 Webhook 订阅 DTO，含 HMAC-SHA256 共享密钥自动生成
     * @return 创建结果；共享密钥只在本次响应中返回一次
     */
    @PostMapping("/webhooks")
    @PreAuthorize("@perm.has('integration.write')")
    public ApiResult<WebhookCreateResponse> createWebhook(@Validated @RequestBody WebhookCreateDto dto) {
        String tenantId = RequestContext.currentOrgScope().tenantId();
        try {
            WebhookCreateResponse config = integrationService.createWebhook(tenantId, dto);
            auditEventPublisher.publish(AuditEvent.of(
                AuditAction.CREATE,
                "integration_webhook",
                dto.webhookId(),
                "新建 Webhook 订阅配置: " + dto.name()
            ));
            return ApiResult.ok(config);
        } catch (ApiException e) {
            isolatedAuditPublisher.publishInNewTx(AuditEvent.failure(
                AuditAction.CREATE,
                "integration_webhook",
                dto.webhookId(),
                e.errorCode().code(),
                "新建 Webhook 订阅配置失败: " + e.getMessage()
            ));
            throw e;
        }
    }

    /**
     * 手动触发指定 Webhook 通道的回调签名生成与双向测试。
     *
     * @param dto 测试入参 DTO (含要调试的 Webhook ID 与测试 Payload 报文)
     * @return 包含推导签名结果及通断状态的签名测试响应
     */
    @PostMapping("/webhooks/test")
    @PreAuthorize("@perm.has('integration.execute')")
    public ApiResult<WebhookTestResultDto> testWebhookSignature(@Validated @RequestBody WebhookTestDto dto) {
        String tenantId = RequestContext.currentOrgScope().tenantId();
        try {
            WebhookTestResultDto testResult = integrationService.testWebhookSignature(tenantId, dto);
            auditEventPublisher.publish(AuditEvent.of(
                AuditAction.EXECUTE,
                "integration_webhook",
                dto.webhookId(),
                "执行 Webhook 签名生成与双向连通测试"
            ));
            return ApiResult.ok(testResult);
        } catch (ApiException e) {
            isolatedAuditPublisher.publishInNewTx(AuditEvent.failure(
                AuditAction.EXECUTE,
                "integration_webhook",
                dto.webhookId(),
                e.errorCode().code(),
                "执行 Webhook 签名测试失败: " + e.getMessage()
            ));
            throw e;
        }
    }

    /**
     * 接收第三方系统 Webhook 入站消息。
     *
     * <p>签名头由服务层进行 HMAC-SHA256 常量时间校验；验签失败会拒绝并写入集成消息日志。
     *
     * @param webhookId Webhook 订阅 ID
     * @param timestamp 防重放签名时间戳
     * @param signature HMAC-SHA256 签名
     * @param dto       入站消息 DTO
     * @return 字段映射和编码归一后的处理结果
     */
    @PostMapping("/webhooks/{id}/inbound")
    @PreAuthorize("@perm.has('integration.execute')")
    public ApiResult<WebhookInboundResultDto> ingestWebhook(@PathVariable("id") String webhookId,
                                                            @RequestHeader("X-MedKernel-Timestamp") String timestamp,
                                                            @RequestHeader("X-MedKernel-Signature") String signature,
                                                            @Validated @RequestBody WebhookInboundRequestDto dto) {
        String tenantId = RequestContext.currentOrgScope().tenantId();
        try {
            WebhookInboundResultDto result = integrationService.ingestWebhook(tenantId, webhookId, timestamp, signature, dto);
            auditEventPublisher.publish(AuditEvent.of(
                AuditAction.EXECUTE,
                "integration_webhook",
                webhookId,
                "接收入站 Webhook 消息: " + result.messageId() + "，状态: " + result.status()
            ));
            return ApiResult.ok(result);
        } catch (ApiException e) {
            isolatedAuditPublisher.publishInNewTx(AuditEvent.failure(
                AuditAction.EXECUTE,
                "integration_webhook",
                webhookId,
                e.errorCode().code(),
                "接收入站 Webhook 消息失败: " + dto.messageId() + "，原因: " + e.getMessage()
            ));
            throw e;
        }
    }

    /**
     * 登记第三方出站异步同步消息。
     *
     * <p>连接配置有效时在事务提交后异步真实投递；断连、配置错误或协议无连接器时
     * 保留补偿日志，不伪造发送成功，也不阻断医生主流程。
     *
     * @param dto 出站异步同步消息
     * @return 不阻断主流程的登记结果
     */
    @PostMapping("/messages/outbound")
    @PreAuthorize("@perm.has('integration.execute')")
    public ApiResult<IntegrationOutboundResultDto> enqueueOutboundMessage(
            @Validated @RequestBody IntegrationOutboundRequestDto dto) {
        String tenantId = RequestContext.currentOrgScope().tenantId();
        try {
            IntegrationOutboundResultDto result = integrationService.enqueueOutboundMessage(tenantId, dto);
            auditEventPublisher.publish(AuditEvent.of(
                AuditAction.EXECUTE,
                "integration_message_log",
                dto.messageId(),
                "登记第三方出站异步消息，状态: " + result.status()
            ));
            return ApiResult.ok(result);
        } catch (ApiException e) {
            isolatedAuditPublisher.publishInNewTx(AuditEvent.failure(
                AuditAction.EXECUTE,
                "integration_message_log",
                dto.messageId(),
                e.errorCode().code(),
                "登记第三方出站异步消息失败: " + e.getMessage()
            ));
            throw e;
        }
    }

    /**
     * 分页查询当前租户下所有第三方集成流审计日志 (支持死信队列的查看)。
     *
     * @param page 页码，从 1 开始，默认 1
     * @param size 每页显示数量，默认 20
     * @return 分页消息审计日志响应体
     */
    @GetMapping("/logs")
    @PreAuthorize("@perm.has('integration.read')")
    public ApiResult<PageResponse<IntegrationMessageLog>> getMessageLogs(@RequestParam(value = "page", defaultValue = "1") int page,
                                                                         @RequestParam(value = "size", defaultValue = "20") int size) {
        String tenantId = RequestContext.currentOrgScope().tenantId();
        PageRequest pageReq = new PageRequest(page, size, null);
        List<IntegrationMessageLog> list = integrationService.getMessageLogs(tenantId, pageReq.offset(), pageReq.safeSize());
        long total = integrationService.getMessageLogsCount(tenantId);
        PageResponse<IntegrationMessageLog> response = PageResponse.of(list, pageReq, total);
        return ApiResult.ok(response);
    }

    /**
     * 分页查询当前租户下所有集成死信消息。
     */
    @GetMapping("/dead-letter")
    @PreAuthorize("@perm.has('integration.read')")
    public ApiResult<PageResponse<IntegrationMessageLog>> getDeadLetters(
            @RequestParam(value = "page", defaultValue = "1") int page,
            @RequestParam(value = "size", defaultValue = "20") int size) {
        String tenantId = RequestContext.currentOrgScope().tenantId();
        PageRequest pageReq = new PageRequest(page, size, null);
        List<IntegrationMessageLog> list = integrationService.getDeadLetters(tenantId, pageReq.offset(), pageReq.safeSize());
        long total = integrationService.getDeadLettersCount(tenantId);
        return ApiResult.ok(PageResponse.of(list, pageReq, total));
    }

    /**
     * 手动一键重试发送/投递指定死信消息，触发业务逻辑补偿。
     *
     * @param messageId 接口数据流日志 ID (全局唯一 UUID)
     * @return 重新投递后的流日志实体
     */
    @PostMapping("/logs/{id}/retry")
    @PreAuthorize("@perm.has('integration.execute')")
    public ApiResult<IntegrationMessageLog> retryMessage(@PathVariable("id") String messageId) {
        String tenantId = RequestContext.currentOrgScope().tenantId();
        try {
            IntegrationMessageLog msgLog = integrationService.retryMessage(tenantId, messageId);
            auditEventPublisher.publish(AuditEvent.of(
                AuditAction.EXECUTE,
                "integration_message_log",
                messageId,
                "手动执行接口流数据重试投递成功, 状态: " + msgLog.status()
            ));
            return ApiResult.ok(msgLog);
        } catch (ApiException e) {
            isolatedAuditPublisher.publishInNewTx(AuditEvent.failure(
                AuditAction.EXECUTE,
                "integration_message_log",
                messageId,
                e.errorCode().code(),
                "手动执行接口流数据重试投递失败: " + e.getMessage()
            ));
            throw e;
        }
    }

    /**
     * 人工重放指定集成死信消息；原死信证据保留，新建补偿消息。
     *
     * @param messageId 流日志 ID
     * @return 死信重放结果
     */
    @PostMapping("/dead-letter/{id}/replay")
    @PreAuthorize("@perm.has('integration.execute')")
    public ApiResult<IntegrationReplayResultDto> replayDeadLetter(@PathVariable("id") String messageId) {
        String tenantId = RequestContext.currentOrgScope().tenantId();
        try {
            IntegrationReplayResultDto result = integrationService.replayDeadLetter(tenantId, messageId);
            auditEventPublisher.publish(AuditEvent.of(
                AuditAction.EXECUTE,
                "integration_message_log",
                messageId,
                "人工重放集成死信消息，新消息: " + result.replayMessageId()
            ));
            return ApiResult.ok(result);
        } catch (ApiException e) {
            isolatedAuditPublisher.publishInNewTx(AuditEvent.failure(
                AuditAction.EXECUTE,
                "integration_message_log",
                messageId,
                e.errorCode().code(),
                "人工重放集成死信消息失败: " + e.getMessage()
            ));
            throw e;
        }
    }

    /**
     * 从回调管理视角人工重放死信消息；复用集成死信补偿链路。
     */
    @PostMapping("/callbacks/dead-letter/{id}/replay")
    @PreAuthorize("@perm.has('integration.execute')")
    public ApiResult<IntegrationReplayResultDto> replayCallbackDeadLetter(@PathVariable("id") String messageId) {
        String tenantId = RequestContext.currentOrgScope().tenantId();
        try {
            IntegrationReplayResultDto result = integrationService.replayCallbackDeadLetter(tenantId, messageId);
            auditEventPublisher.publish(AuditEvent.of(
                AuditAction.EXECUTE,
                "integration_message_log",
                messageId,
                "人工重放回调死信消息，新消息: " + result.replayMessageId()
            ));
            return ApiResult.ok(result);
        } catch (ApiException e) {
            isolatedAuditPublisher.publishInNewTx(AuditEvent.failure(
                AuditAction.EXECUTE,
                "integration_message_log",
                messageId,
                e.errorCode().code(),
                "人工重放回调死信消息失败: " + e.getMessage()
            ));
            throw e;
        }
    }
}
