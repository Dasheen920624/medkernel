package com.medkernel.engine.experience;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.medkernel.shared.api.error.ApiException;
import com.medkernel.shared.context.OrgScope;
import com.medkernel.shared.context.RequestContext;

class SavedViewServiceTest {

    private SavedViewRepository repository;
    private SavedViewService service;

    @BeforeEach
    void setUp() {
        repository = mock(SavedViewRepository.class);
        service = new SavedViewService(repository, new ObjectMapper());
        RequestContext.restore(new RequestContext.Snapshot("trace-view", OrgScope.tenant("tenant-1"), "doctor-1"));
    }

    @AfterEach
    void tearDown() {
        RequestContext.clear();
    }

    @Test
    void upsert_updatesExistingViewForCurrentTenantAndUser() {
        SavedView existing = view("sv-01", "tenant-1", "doctor-1", "terminology.mapping", "默认视图", 2);
        when(repository.findByTenantIdAndUserIdAndPageKeyAndViewName(
            "tenant-1", "doctor-1", "terminology.mapping", "默认视图"))
            .thenReturn(Optional.of(existing));
        when(repository.save(any(SavedView.class))).thenAnswer(invocation -> invocation.getArgument(0));

        SavedViewResponse response = service.upsert(new SavedViewRequest(
            "terminology.mapping",
            "默认视图",
            "{\"filters\":[{\"key\":\"status\",\"value\":\"DRAFT\"}]}",
            true
        ));

        assertThat(response.savedViewId()).isEqualTo("sv-01");
        assertThat(response.version()).isEqualTo(3);
        assertThat(response.defaultView()).isTrue();
        verify(repository).save(any(SavedView.class));
    }

    @Test
    void list_readsOnlyCurrentTenantAndUser() {
        when(repository.findByTenantIdAndUserIdAndPageKeyAndStatusOrderByUpdatedAtDesc(
            "tenant-1", "doctor-1", "terminology.mapping", "ACTIVE"))
            .thenReturn(List.of(view("sv-01", "tenant-1", "doctor-1", "terminology.mapping", "默认视图", 1)));

        List<SavedViewResponse> views = service.list("terminology.mapping");

        assertThat(views).extracting(SavedViewResponse::savedViewId).containsExactly("sv-01");
    }

    @Test
    void upsert_rejectsSensitiveSnapshotContent() {
        assertThatThrownBy(() -> service.upsert(new SavedViewRequest(
            "terminology.mapping",
            "含敏感字段",
            "{\"filters\":{\"patientId\":\"p-1\"}}",
            false
        )))
            .isInstanceOf(ApiException.class)
            .hasMessageContaining("敏感内容");
    }

    private static SavedView view(
        String id,
        String tenantId,
        String userId,
        String pageKey,
        String viewName,
        long version
    ) {
        Instant now = Instant.parse("2026-06-01T00:00:00Z");
        return new SavedView(
            id,
            tenantId,
            userId,
            pageKey,
            viewName,
            "{\"filters\":[]}",
            "N",
            version,
            "ACTIVE",
            now,
            userId,
            now,
            userId
        );
    }
}
