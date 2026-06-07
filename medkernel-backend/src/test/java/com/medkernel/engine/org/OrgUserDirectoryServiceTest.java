package com.medkernel.engine.org;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.data.domain.Pageable;

import com.medkernel.engine.security.TenantUser;
import com.medkernel.engine.security.TenantUserRepository;
import com.medkernel.shared.api.PageRequest;
import com.medkernel.shared.context.OrgScope;
import com.medkernel.shared.context.RequestContext;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;

class OrgUserDirectoryServiceTest {

    private TenantUserRepository users;
    private OrgUserDirectoryService service;

    @BeforeEach
    void setUp() {
        users = Mockito.mock(TenantUserRepository.class);
        service = new OrgUserDirectoryService(users);
        RequestContext.restore(new RequestContext.Snapshot(
            "trace-org-users",
            OrgScope.tenant("tenant-A"),
            "qa-1"));
    }

    @AfterEach
    void clear() {
        RequestContext.clear();
    }

    @Test
    void listsOnlyActiveUsersFromCurrentTenantWithoutCredentialDetails() {
        Mockito.when(users.countByTenantIdAndStatus("tenant-A", "ACTIVE")).thenReturn(1L);
        Mockito.when(users.findByTenantIdAndStatusOrderByDisplayNameAsc(
            Mockito.eq("tenant-A"),
            Mockito.eq("ACTIVE"),
            any(Pageable.class)))
            .thenReturn(List.of(activeUser("tenant-A", "doctor-1", "王医生")));

        var page = service.list(new PageRequest(1, 20, null));

        assertThat(page.total()).isEqualTo(1);
        assertThat(page.items()).containsExactly(new OrgUserDirectoryItem("doctor-1", "王医生"));
    }

    @Test
    void searchesActiveUsersByDisplayNameOrUserId() {
        Mockito.when(users.countActiveDirectory("tenant-A", "王")).thenReturn(1L);
        Mockito.when(users.pageActiveDirectory("tenant-A", "王", 0, 20))
            .thenReturn(List.of(activeUser("tenant-A", "doctor-1", "王医生")));

        var page = service.search(new PageRequest(1, 20, null), " 王 ");

        assertThat(page.total()).isEqualTo(1L);
        assertThat(page.items()).containsExactly(new OrgUserDirectoryItem("doctor-1", "王医生"));
    }

    @Test
    void returnsEmptyPageWithoutRunningTheDataQuery() {
        Mockito.when(users.countByTenantIdAndStatus("tenant-A", "ACTIVE")).thenReturn(0L);

        var page = service.list(PageRequest.defaults());

        assertThat(page.items()).isEmpty();
        Mockito.verify(users, Mockito.never()).findByTenantIdAndStatusOrderByDisplayNameAsc(
            Mockito.anyString(),
            Mockito.anyString(),
            any(Pageable.class));
    }

    private TenantUser activeUser(String tenantId, String userId, String displayName) {
        Instant now = Instant.now();
        return new TenantUser(
            1L,
            tenantId,
            userId,
            displayName,
            "ACTIVE",
            1L,
            now,
            "system",
            now,
            "system",
            "trace");
    }
}
