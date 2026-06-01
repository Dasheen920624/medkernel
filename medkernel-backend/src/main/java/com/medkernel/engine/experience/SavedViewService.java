package com.medkernel.engine.experience;

import java.time.Instant;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.medkernel.shared.api.error.ApiException;
import com.medkernel.shared.api.error.ErrorCode;
import com.medkernel.shared.context.OrgScope;
import com.medkernel.shared.context.RequestContext;

/**
 * 页面保存视图服务。
 */
@Service
public class SavedViewService {

    private static final String ACTIVE = "ACTIVE";
    private static final List<String> SENSITIVE_KEYS = List.of(
        "token",
        "secret",
        "password",
        "passwd",
        "api-key",
        "apikey",
        "authorization",
        "credential",
        "patient",
        "idcard",
        "identity",
        "身份证",
        "患者"
    );

    private final SavedViewRepository repository;
    private final ObjectMapper objectMapper;

    public SavedViewService(SavedViewRepository repository, ObjectMapper objectMapper) {
        this.repository = repository;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public SavedViewResponse upsert(SavedViewRequest request) {
        validateDefinition(request.definitionJson());
        String tenantId = requireTenantId();
        String userId = requireUserId();
        Instant now = Instant.now();

        SavedView saved = repository.findByTenantIdAndUserIdAndPageKeyAndViewName(
                tenantId, userId, request.pageKey(), request.viewName())
            .map(existing -> existing.updatedBy(request, userId, now))
            .orElseGet(() -> SavedView.create(newId(), tenantId, userId, request, now));

        if (request.defaultView()) {
            repository.clearOtherDefaultViews(tenantId, userId, request.pageKey(), saved.savedViewId(), now, userId);
        }
        return SavedViewResponse.from(repository.save(saved));
    }

    @Transactional(readOnly = true)
    public List<SavedViewResponse> list(String pageKey) {
        String tenantId = requireTenantId();
        String userId = requireUserId();
        return repository.findByTenantIdAndUserIdAndPageKeyAndStatusOrderByUpdatedAtDesc(
                tenantId, userId, pageKey, ACTIVE)
            .stream()
            .map(SavedViewResponse::from)
            .toList();
    }

    private void validateDefinition(String definitionJson) {
        JsonNode root;
        try {
            root = objectMapper.readTree(definitionJson);
        } catch (JsonProcessingException ex) {
            throw new ApiException(ErrorCode.BAD_REQUEST, "保存视图定义不是合法 JSON", ex);
        }
        if (containsSensitiveContent(root)) {
            throw new ApiException(ErrorCode.BAD_REQUEST, "保存视图定义包含敏感内容，请只保存筛选、排序和列配置");
        }
    }

    private boolean containsSensitiveContent(JsonNode node) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return false;
        }
        if (node.isObject()) {
            Iterator<String> fieldNames = node.fieldNames();
            while (fieldNames.hasNext()) {
                String fieldName = fieldNames.next();
                if (isSensitive(fieldName) || containsSensitiveContent(node.get(fieldName))) {
                    return true;
                }
            }
        } else if (node.isArray()) {
            for (JsonNode child : node) {
                if (containsSensitiveContent(child)) {
                    return true;
                }
            }
        } else if (node.isTextual() && isSensitive(node.asText())) {
            return true;
        }
        return false;
    }

    private boolean isSensitive(String value) {
        String normalized = value == null ? "" : value.toLowerCase(Locale.ROOT).replace("_", "-");
        return SENSITIVE_KEYS.stream().anyMatch(normalized::contains);
    }

    private String requireTenantId() {
        OrgScope scope = RequestContext.currentOrgScope();
        String tenantId = scope.tenantId();
        if (tenantId == null || tenantId.isBlank()) {
            throw ApiException.tenantMissing();
        }
        return tenantId;
    }

    private String requireUserId() {
        return RequestContext.currentUserId()
            .filter(userId -> !userId.isBlank())
            .orElseThrow(() -> new ApiException(ErrorCode.UNAUTHORIZED, "当前用户上下文缺失"));
    }

    private String newId() {
        return "sv-" + UUID.randomUUID();
    }
}
