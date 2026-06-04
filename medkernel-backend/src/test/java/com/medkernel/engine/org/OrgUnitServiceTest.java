package com.medkernel.engine.org;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import com.medkernel.shared.api.PageRequest;
import com.medkernel.shared.api.PageResponse;
import com.medkernel.shared.api.error.ApiException;
import com.medkernel.shared.api.error.ErrorCode;
import com.medkernel.shared.context.OrgLevel;
import com.medkernel.shared.context.OrgScope;
import com.medkernel.shared.context.RequestContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;

class OrgUnitServiceTest {

    private OrgUnitRepository repository;
    private OrgHierarchyRepository hierarchyRepository;
    private OrgUnitService service;

    @BeforeEach
    void setUp() {
        repository = Mockito.mock(OrgUnitRepository.class);
        hierarchyRepository = Mockito.mock(OrgHierarchyRepository.class);
        service = new OrgUnitService(repository, hierarchyRepository);
    }

    @AfterEach
    void clear() {
        RequestContext.clear();
    }

    @Test
    void rejectsRequestsWithoutTenantContext() {
        RequestContext.restore(new RequestContext.Snapshot("trace", OrgScope.empty(), null));
        assertThatThrownBy(() -> service.listByCurrentTenant(PageRequest.defaults()))
            .isInstanceOf(ApiException.class)
            .extracting("errorCode")
            .isEqualTo(ErrorCode.TENANT_CONTEXT_MISSING);
    }

    @Test
    void filtersByCurrentTenantWhenListing() {
        RequestContext.restore(new RequestContext.Snapshot("trace", OrgScope.tenant("t-99"), "u-1"));
        Mockito.when(repository.countByTenantId("t-99")).thenReturn(3L);
        Mockito.when(repository.pageByTenantId(eq("t-99"), anyInt(), anyInt()))
            .thenReturn(List.of(sampleHospital("t-99")));

        PageResponse<OrgUnit> page = service.listByCurrentTenant(PageRequest.defaults());

        assertThat(page.total()).isEqualTo(3);
        assertThat(page.items()).hasSize(1);
        assertThat(page.items().get(0).tenantId()).isEqualTo("t-99");
    }

    @Test
    void getByCodeNotFoundThrowsApiException() {
        RequestContext.restore(new RequestContext.Snapshot("trace", OrgScope.tenant("t-1"), "u"));
        Mockito.when(repository.findByTenantIdAndCode(eq("t-1"), eq("MISSING")))
            .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getByCurrentTenantAndCode("MISSING"))
            .isInstanceOf(ApiException.class)
            .extracting("errorCode")
            .isEqualTo(ErrorCode.NOT_FOUND);
    }

    @Test
    void childrenMapGroupsByParent() {
        RequestContext.restore(new RequestContext.Snapshot("trace", OrgScope.tenant("t-1"), "u"));
        OrgUnit root = sample("t-1", null, OrgLevel.HOSPITAL, "HOSP-001", "1");
        OrgUnit dept1 = sample("t-1", "1", OrgLevel.DEPARTMENT, "DEPT-A", "2");
        OrgUnit dept2 = sample("t-1", "1", OrgLevel.DEPARTMENT, "DEPT-B", "3");
        Mockito.when(repository.findByTenantIdOrderByLevelAscCodeAsc("t-1"))
            .thenReturn(List.of(root, dept1, dept2));

        var map = service.childrenMapByCurrentTenant();
        assertThat(map.get("ROOT")).hasSize(1);
        assertThat(map.get("1")).extracting(OrgUnit::code).containsExactly("DEPT-A", "DEPT-B");
    }

    @Test
    void emptyTenantReturnsEmptyPage() {
        RequestContext.restore(new RequestContext.Snapshot("trace", OrgScope.tenant("t-new"), "u"));
        Mockito.when(repository.countByTenantId("t-new")).thenReturn(0L);

        PageResponse<OrgUnit> page = service.listByCurrentTenant(PageRequest.defaults());
        assertThat(page.items()).isEmpty();
        assertThat(page.hasNext()).isFalse();
        Mockito.verify(repository, Mockito.never()).pageByTenantId(any(), anyInt(), anyInt());
    }

    @Test
    void createOrgUnitSuccessfully() {
        RequestContext.restore(new RequestContext.Snapshot("trace", OrgScope.tenant("t-1"), "u-1"));
        OrgUnit parent = sample("t-1", null, OrgLevel.HOSPITAL, "HOSP-001", "parent-1");
        OrgUnit input = sample("t-1", "parent-1", OrgLevel.CAMPUS, "CAMP-002", null);
        OrgUnit expected = sample("t-1", "parent-1", OrgLevel.CAMPUS, "CAMP-002", "20");

        Mockito.when(repository.findByTenantIdAndCode("t-1", "CAMP-002")).thenReturn(Optional.empty());
        Mockito.when(repository.findByTenantIdAndId("t-1", "parent-1")).thenReturn(Optional.of(parent));
        Mockito.when(repository.save(any(OrgUnit.class))).thenReturn(expected);

        OrgUnit saved = service.createOrgUnit(input);

        assertThat(saved.id()).isEqualTo("20");
        assertThat(saved.code()).isEqualTo("CAMP-002");
        assertThat(saved.tenantId()).isEqualTo("t-1");
    }

    @Test
    void rejectsInvertedParentLevelOnCreate() {
        RequestContext.restore(new RequestContext.Snapshot("trace", OrgScope.tenant("t-1"), "u-1"));
        // 父级层级更低（科室）而子级更高（集团）——倒挂，应拒绝
        OrgUnit parent = sample("t-1", null, OrgLevel.DEPARTMENT, "DEPT-001", "parent-1");
        OrgUnit input = sample("t-1", "parent-1", OrgLevel.GROUP, "GROUP-001", null);

        Mockito.when(repository.findByTenantIdAndCode("t-1", "GROUP-001")).thenReturn(Optional.empty());
        Mockito.when(repository.findByTenantIdAndId("t-1", "parent-1")).thenReturn(Optional.of(parent));

        assertThatThrownBy(() -> service.createOrgUnit(input))
            .isInstanceOf(ApiException.class)
            .extracting("errorCode")
            .isEqualTo(ErrorCode.ORG_LEVEL_INVALID);
    }

    @Test
    void allowsSkipLevelCreate() {
        RequestContext.restore(new RequestContext.Snapshot("trace", OrgScope.tenant("t-1"), "u-1"));
        // 科室直挂医院（跳过院区 / 服务点）在放宽后应允许
        OrgUnit parent = sample("t-1", null, OrgLevel.HOSPITAL, "HOSP-001", "parent-1");
        OrgUnit input = sample("t-1", "parent-1", OrgLevel.DEPARTMENT, "DEPT-009", null);
        OrgUnit expected = sample("t-1", "parent-1", OrgLevel.DEPARTMENT, "DEPT-009", "30");

        Mockito.when(repository.findByTenantIdAndCode("t-1", "DEPT-009")).thenReturn(Optional.empty());
        Mockito.when(repository.findByTenantIdAndId("t-1", "parent-1")).thenReturn(Optional.of(parent));
        Mockito.when(repository.save(any(OrgUnit.class))).thenReturn(expected);

        OrgUnit saved = service.createOrgUnit(input);

        assertThat(saved.code()).isEqualTo("DEPT-009");
    }

    @Test
    void rejectsInvertedParentLevelOnReparent() {
        RequestContext.restore(new RequestContext.Snapshot("trace", OrgScope.tenant("t-1"), "u-1"));
        // 把集团迁到科室之下——父级层级更低，倒挂，应拒绝
        OrgUnit unit = sample("t-1", "tenant-1", OrgLevel.GROUP, "GROUP-001", "group-1");
        OrgUnit parent = sample("t-1", "site-1", OrgLevel.DEPARTMENT, "DEPT-001", "dept-1");

        Mockito.when(repository.findByTenantIdAndId("t-1", "group-1")).thenReturn(Optional.of(unit));
        Mockito.when(repository.findByTenantIdAndId("t-1", "dept-1")).thenReturn(Optional.of(parent));
        Mockito.when(hierarchyRepository.isDescendant("t-1", "group-1", "dept-1")).thenReturn(false);

        assertThatThrownBy(() -> service.reparentOrgUnit("group-1", "dept-1"))
            .isInstanceOf(ApiException.class)
            .extracting("errorCode")
            .isEqualTo(ErrorCode.ORG_LEVEL_INVALID);
    }

    @Test
    void createOrgUnitCodeConflictThrowsApiException() {
        RequestContext.restore(new RequestContext.Snapshot("trace", OrgScope.tenant("t-1"), "u-1"));
        OrgUnit input = sample("t-1", null, OrgLevel.CAMPUS, "CAMP-002", null);
        OrgUnit existing = sample("t-1", null, OrgLevel.CAMPUS, "CAMP-002", "10");

        Mockito.when(repository.findByTenantIdAndCode("t-1", "CAMP-002")).thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> service.createOrgUnit(input))
            .isInstanceOf(ApiException.class)
            .extracting("errorCode")
            .isEqualTo(ErrorCode.BAD_REQUEST);
    }

    @Test
    void createOrgUnitNoTenantContextThrowsApiException() {
        RequestContext.restore(new RequestContext.Snapshot("trace", OrgScope.empty(), null));
        OrgUnit input = sample("t-1", null, OrgLevel.CAMPUS, "CAMP-002", null);

        assertThatThrownBy(() -> service.createOrgUnit(input))
            .isInstanceOf(ApiException.class)
            .extracting("errorCode")
            .isEqualTo(ErrorCode.TENANT_CONTEXT_MISSING);
    }

    private OrgUnit sampleHospital(String tenantId) {
        return sample(tenantId, null, OrgLevel.HOSPITAL, "HOSP-001", "10");
    }

    private OrgUnit sample(String tenantId, String parentId, OrgLevel level, String code, String id) {
        Instant now = Instant.now();
        return new OrgUnit(id, parentId, tenantId, "/" + code, level, code, code, null, null,
            OrgUnitStatus.ACTIVE, now, "system", now, "system");
    }
}
