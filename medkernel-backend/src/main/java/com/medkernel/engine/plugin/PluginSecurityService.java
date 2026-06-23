package com.medkernel.engine.plugin;

import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.medkernel.engine.contract.ServiceContract;
import com.medkernel.engine.contract.ServiceContractCatalog;
import com.medkernel.shared.api.error.ApiException;
import com.medkernel.shared.api.error.ErrorCode;
import com.medkernel.shared.audit.AuditAction;
import com.medkernel.shared.audit.AuditRecorder;
import com.medkernel.shared.context.OrgScope;
import com.medkernel.shared.context.RequestContext;

/**
 * 插件安全边界服务。
 */
@Service
public class PluginSecurityService {

    private static final TypeReference<List<PluginCapabilityResponse>> CAPABILITY_LIST_TYPE =
        new TypeReference<>() {
        };

    private final PluginSecurityRepository repository;
    private final ObjectMapper objectMapper;
    private final AuditRecorder auditRecorder;

    public PluginSecurityService(PluginSecurityRepository repository,
                                 ObjectMapper objectMapper,
                                 AuditRecorder auditRecorder) {
        this.repository = repository;
        this.objectMapper = objectMapper;
        this.auditRecorder = auditRecorder;
    }

    public PluginListResponse list() {
        String tenantId = currentTenant();
        return new PluginListResponse(repository.listByTenant(tenantId).stream()
            .map(this::toResponse)
            .toList());
    }

    @Transactional
    public PluginResponse register(PluginRegisterRequest request) {
        String tenantId = currentTenant();
        String actor = currentActor();
        List<PluginCapabilityResponse> capabilities = normalizeCapabilities(request.capabilities());
        PluginAuthorityBoundary boundary = capabilities.stream()
            .anyMatch(capability -> capability.capabilityType() == PluginCapabilityType.WRITE)
            ? PluginAuthorityBoundary.CONTROLLED_WRITE
            : PluginAuthorityBoundary.READ_ONLY;
        String pluginId = "plug-" + UUID.randomUUID();
        try {
            PluginRecord record = repository.insertPlugin(
                tenantId,
                pluginId,
                request.pluginCode().trim(),
                request.displayName().trim(),
                PluginStatus.PENDING_REVIEW,
                boundary,
                toJson(capabilities),
                actor,
                RequestContext.currentTraceId());
            auditRecorder.record(AuditAction.CREATE, "mk_plugin_registry", pluginId,
                "注册插件 " + request.pluginCode().trim());
            return toResponse(record);
        } catch (DuplicateKeyException ex) {
            throw ApiException.conflict("插件编码已存在");
        }
    }

    @Transactional
    public PluginGrantResponse grant(String pluginId, PluginGrantRequest request) {
        String tenantId = currentTenant();
        PluginRecord plugin = repository.findByTenantAndPluginId(tenantId, pluginId)
            .orElseThrow(() -> ApiException.notFound("插件 " + pluginId));
        if (plugin.status() == PluginStatus.DISABLED) {
            throw ApiException.conflict("插件已禁用，不能授权");
        }
        List<PluginCapabilityResponse> selected = selectCapabilities(plugin, request.capabilityKeys());
        boolean requiresAuthorizationReason = selected.stream()
            .anyMatch(capability -> capability.capabilityType() == PluginCapabilityType.WRITE);
        if (requiresAuthorizationReason && isBlank(request.authorizationReason())) {
            throw new ApiException(ErrorCode.BAD_REQUEST, "受控写入授权必须填写授权原因");
        }
        boolean touchesClinicalData = selected.stream().anyMatch(PluginCapabilityResponse::clinicalData);
        if (requiresAuthorizationReason && touchesClinicalData && !request.clinicalSafetyConfirmed()) {
            throw new ApiException(ErrorCode.BAD_REQUEST, "临床数据写能力必须完成临床安全确认");
        }

        String actor = currentActor();
        List<PluginGrantRecord> grants;
        try {
            grants = selected.stream()
                .map(capability -> repository.insertGrant(
                    tenantId,
                    pluginId,
                    "grant-" + UUID.randomUUID(),
                    capability,
                    trimToNull(request.authorizationReason()),
                    request.clinicalSafetyConfirmed(),
                    actor,
                    RequestContext.currentTraceId()))
                .toList();
        } catch (DuplicateKeyException ex) {
            throw ApiException.conflict("插件能力已授权，请勿重复授权");
        }
        repository.updateStatus(tenantId, pluginId, PluginStatus.AUTHORIZED, actor, RequestContext.currentTraceId());
        auditRecorder.record(AuditAction.PERMISSION_CHANGE, "mk_plugin_grant", pluginId,
            "授权插件能力 " + selected.stream()
                .map(PluginCapabilityResponse::capabilityKey)
                .collect(Collectors.joining(",")));
        return new PluginGrantResponse(pluginId, PluginGrantStatus.AUTHORIZED,
            grants.stream().map(this::toGrantResponse).toList());
    }

    @Transactional
    public PluginResponse disable(String pluginId) {
        String tenantId = currentTenant();
        PluginRecord existing = repository.findByTenantAndPluginId(tenantId, pluginId)
            .orElseThrow(() -> ApiException.notFound("插件 " + pluginId));
        if (existing.status() == PluginStatus.DISABLED) {
            return toResponse(existing);
        }
        PluginRecord updated = repository.updateStatus(
            tenantId,
            pluginId,
            PluginStatus.DISABLED,
            currentActor(),
            RequestContext.currentTraceId());
        if (updated == null) {
            throw ApiException.notFound("插件 " + pluginId);
        }
        auditRecorder.record(AuditAction.UPDATE, "mk_plugin_registry", pluginId, "禁用插件 " + existing.pluginCode());
        return toResponse(updated);
    }

    private List<PluginCapabilityResponse> normalizeCapabilities(List<PluginCapabilityRequest> capabilities) {
        if (capabilities == null || capabilities.isEmpty()) {
            throw new ApiException(ErrorCode.BAD_REQUEST, "插件至少声明一个能力");
        }
        HashSet<String> keys = new HashSet<>();
        return capabilities.stream()
            .map(capability -> {
                String key = capability.capabilityKey().trim();
                if (!keys.add(key.toLowerCase(Locale.ROOT))) {
                    throw new ApiException(ErrorCode.BAD_REQUEST, "插件能力键重复：" + key);
                }
                ServiceContract contract = ServiceContractCatalog.contracts().stream()
                    .filter(item -> item.id().equals(capability.serviceContractId().trim()))
                    .findFirst()
                    .orElseThrow(() -> new ApiException(ErrorCode.BAD_REQUEST,
                        "服务契约不存在：" + capability.serviceContractId()));
                return new PluginCapabilityResponse(
                    key,
                    capability.capabilityType(),
                    contract.id(),
                    contract.title(),
                    capability.clinicalData());
            })
            .toList();
    }

    private List<PluginCapabilityResponse> selectCapabilities(PluginRecord plugin, List<String> capabilityKeys) {
        if (capabilityKeys == null || capabilityKeys.isEmpty()) {
            throw new ApiException(ErrorCode.BAD_REQUEST, "授权能力不能为空");
        }
        Map<String, PluginCapabilityResponse> declared = parseCapabilities(plugin.capabilitiesJson()).stream()
            .collect(Collectors.toMap(
                capability -> capability.capabilityKey().toLowerCase(Locale.ROOT),
                Function.identity(),
                (left, right) -> left));
        return capabilityKeys.stream()
            .map(key -> {
                PluginCapabilityResponse capability = declared.get(key.trim().toLowerCase(Locale.ROOT));
                if (capability == null) {
                    throw new ApiException(ErrorCode.BAD_REQUEST, "插件未声明能力：" + key);
                }
                return capability;
            })
            .toList();
    }

    private PluginResponse toResponse(PluginRecord record) {
        return new PluginResponse(
            record.pluginId(),
            record.pluginCode(),
            record.displayName(),
            record.status(),
            record.authorityBoundary(),
            parseCapabilities(record.capabilitiesJson()),
            record.version(),
            record.updatedAt());
    }

    private PluginGrantItemResponse toGrantResponse(PluginGrantRecord record) {
        return new PluginGrantItemResponse(
            record.grantId(),
            record.capabilityKey(),
            record.capabilityType(),
            record.serviceContractId(),
            record.status(),
            record.clinicalSafetyConfirmed(),
            record.grantedAt());
    }

    private List<PluginCapabilityResponse> parseCapabilities(String json) {
        try {
            return objectMapper.readValue(json, CAPABILITY_LIST_TYPE);
        } catch (JsonProcessingException ex) {
            throw new ApiException(ErrorCode.INTERNAL_ERROR, "插件能力声明不可解析", ex);
        }
    }

    private String toJson(List<PluginCapabilityResponse> capabilities) {
        try {
            return objectMapper.writeValueAsString(capabilities);
        } catch (JsonProcessingException ex) {
            throw new ApiException(ErrorCode.INTERNAL_ERROR, "插件能力声明不可序列化", ex);
        }
    }

    private static String currentTenant() {
        OrgScope scope = RequestContext.currentOrgScope();
        if (scope == null || !scope.hasTenant()) {
            throw ApiException.tenantMissing();
        }
        return scope.tenantId();
    }

    private static String currentActor() {
        return RequestContext.currentUserId().orElse("system");
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private static String trimToNull(String value) {
        return isBlank(value) ? null : value.trim();
    }
}
