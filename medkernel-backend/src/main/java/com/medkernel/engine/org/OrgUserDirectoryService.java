package com.medkernel.engine.org;

import java.util.List;

import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.medkernel.engine.security.TenantUserRepository;
import com.medkernel.shared.api.PageResponse;
import com.medkernel.shared.context.RequestContext;

/**
 * 当前租户的启用用户目录，供责任人选择等业务场景复用。
 */
@Service
public class OrgUserDirectoryService {

    private static final String ACTIVE = "ACTIVE";

    private final TenantUserRepository users;

    public OrgUserDirectoryService(TenantUserRepository users) {
        this.users = users;
    }

    @Transactional(readOnly = true)
    public PageResponse<OrgUserDirectoryItem> list(com.medkernel.shared.api.PageRequest request) {
        var safe = request == null ? com.medkernel.shared.api.PageRequest.defaults() : request;
        String tenantId = RequestContext.currentOrgScope().tenantId();
        long total = users.countByTenantIdAndStatus(tenantId, ACTIVE);
        if (total == 0) {
            return PageResponse.empty(safe);
        }
        List<OrgUserDirectoryItem> items = users
            .findByTenantIdAndStatusOrderByDisplayNameAsc(
                tenantId,
                ACTIVE,
                PageRequest.of(safe.safePage() - 1, safe.safeSize()))
            .stream()
            .map(user -> new OrgUserDirectoryItem(user.userId(), user.displayName()))
            .toList();
        return PageResponse.of(items, safe, total);
    }

    /**
     * 按显示名或用户标识分页检索当前租户启用用户。
     */
    @Transactional(readOnly = true)
    public PageResponse<OrgUserDirectoryItem> search(
            com.medkernel.shared.api.PageRequest request,
            String keyword) {
        var safe = request == null ? com.medkernel.shared.api.PageRequest.defaults() : request;
        String tenantId = RequestContext.currentOrgScope().tenantId();
        String normalizedKeyword = keyword == null || keyword.isBlank() ? null : keyword.trim();
        long total = users.countActiveDirectory(tenantId, normalizedKeyword);
        if (total == 0) {
            return PageResponse.empty(safe);
        }
        List<OrgUserDirectoryItem> items = users
            .pageActiveDirectory(tenantId, normalizedKeyword, safe.offset(), safe.safeSize())
            .stream()
            .map(user -> new OrgUserDirectoryItem(user.userId(), user.displayName()))
            .toList();
        return PageResponse.of(items, safe, total);
    }
}
