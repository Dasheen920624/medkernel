package com.medkernel.engine.integration.controller;

import java.util.List;
import java.util.Map;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import com.medkernel.engine.integration.domain.*;
import com.medkernel.engine.integration.dto.*;
import com.medkernel.engine.integration.service.IntegrationService;
import com.medkernel.shared.api.*;
import com.medkernel.shared.api.error.ApiException;
import com.medkernel.shared.audit.*;
import com.medkernel.shared.context.RequestContext;
import com.medkernel.shared.datascope.DataScope;

/**
 * 第三方系统对接总线及集成控制器。
 *
 * <p>提供多租户隔离下的异构系统适配器管理、自检诊断、Webhook 动态安全订阅、
 * 接口流审计死信队列手动重试投递及补偿、跨域通信及 launch token 免登接入审计等功能。
 */
@RestController
@RequestMapping("/api/v1/engine/integration")
@DataScope(requireTenant = true)
public class IntegrationController {

    private final IntegrationService integrationService;
    private final AuditEventPublisher auditEventPublisher;
    private final IsolatedAuditPublisher isolatedAuditPublisher;

    /**
     * 构造器注入外部集成服务与审计日志发布组件。
     */
    public IntegrationController(IntegrationService integrationService,
                                 AuditEventPublisher auditEventPublisher,
                                 IsolatedAuditPublisher isolatedAuditPublisher) {
        this.integrationService = integrationService;
        this.auditEventPublisher = auditEventPublisher;
        this.isolatedAuditPublisher = isolatedAuditPublisher;
    }

    /**
     * 获取当前租户下注册的所有第三方集成适配器配置列表。
     *
     * @return 包含适配器列表的统一 API 返回实体
     */
    @GetMapping("/adapters")
    @PreAuthorize("@perm.has('integration.read')")
    public ApiResult<List<IntegrationAdapter>> getAdapters() {
        String tenantId = RequestContext.currentOrgScope().tenantId();
        return ApiResult.ok(integrationService.getAdapters(tenantId));
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
     * <p>包含协议类型变更及系统挂起/挂载操作，若更新失败同样会记录物理子事务审计日志。
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
     * 手动触发指定第三方系统适配器的物理连接健康自检 (Ping) 并计算单向时延。
     *
     * @param adapterId 适配器全局唯一 ID
     * @return 包含最新 RTT 时延与状态的适配器实体
     */
    @PostMapping("/adapters/{id}/ping")
    @PreAuthorize("@perm.has('integration.execute')")
    public ApiResult<IntegrationAdapter> pingAdapter(@PathVariable("id") String adapterId) {
        String tenantId = RequestContext.currentOrgScope().tenantId();
        try {
            IntegrationAdapter adapter = integrationService.pingAdapter(tenantId, adapterId);
            auditEventPublisher.publish(AuditEvent.of(
                AuditAction.EXECUTE,
                "integration_adapter",
                adapterId,
                "适配器自检连接健康诊断成功"
            ));
            return ApiResult.ok(adapter);
        } catch (ApiException e) {
            isolatedAuditPublisher.publishInNewTx(AuditEvent.failure(
                AuditAction.EXECUTE,
                "integration_adapter",
                adapterId,
                e.errorCode().code(),
                "适配器自检连接健康诊断失败: " + e.getMessage()
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
    public ApiResult<List<IntegrationWebhookConfig>> getWebhooks() {
        String tenantId = RequestContext.currentOrgScope().tenantId();
        return ApiResult.ok(integrationService.getWebhooks(tenantId));
    }

    /**
     * 创建一条外部 Webhook 订阅，动态绑定待回调的事件场景列表。
     *
     * @param dto 创建 Webhook 订阅 DTO，含 HMAC-SHA256 共享密钥自动生成
     * @return 创建后的 Webhook 订阅实体
     */
    @PostMapping("/webhooks")
    @PreAuthorize("@perm.has('integration.write')")
    public ApiResult<IntegrationWebhookConfig> createWebhook(@Validated @RequestBody WebhookCreateDto dto) {
        String tenantId = RequestContext.currentOrgScope().tenantId();
        try {
            IntegrationWebhookConfig config = integrationService.createWebhook(tenantId, dto);
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
     * @return 包含推导签名结果及通断状态的键值对 Map 响应体
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
                "执行 Webhook 签名自检测连通测试失败: " + e.getMessage()
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
     * 手动物理或逻辑删除指定集成流审计日志，通常用于已补偿且已结案的高危警报归档。
     *
     * @param messageId 流日志 ID
     * @return 空返回实体
     */
    @DeleteMapping("/logs/{id}")
    @PreAuthorize("@perm.has('integration.execute')")
    public ApiResult<Void> deleteMessage(@PathVariable("id") String messageId) {
        String tenantId = RequestContext.currentOrgScope().tenantId();
        try {
            integrationService.deleteMessage(tenantId, messageId);
            auditEventPublisher.publish(AuditEvent.of(
                AuditAction.DELETE,
                "integration_message_log",
                messageId,
                "手动删除接口集成流审计日志与重试项"
            ));
            return ApiResult.empty();
        } catch (ApiException e) {
            isolatedAuditPublisher.publishInNewTx(AuditEvent.failure(
                AuditAction.DELETE,
                "integration_message_log",
                messageId,
                e.errorCode().code(),
                "手动删除接口审计日志失败: " + e.getMessage()
            ));
            throw e;
        }
    }
}
