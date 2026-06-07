package com.medkernel.engine.context;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.medkernel.engine.pkg.KnowledgePackage;
import com.medkernel.engine.pkg.KnowledgePackageRepository;
import com.medkernel.engine.pkg.KnowledgePackageStatus;

class KnowledgePackageVersionAdapterTest {

    private final KnowledgePackageRepository repository = mock(KnowledgePackageRepository.class);
    private final PackageVersionPort port = new KnowledgePackageVersionAdapter(repository);

    @Test
    void acceptsOnlyActivePackageVersionFromAuthoritativeRepository() {
        when(repository.findByTenantIdAndPackageVersion("tenant-A", "pkg-2026.06"))
            .thenReturn(List.of(
                pack("draft", KnowledgePackageStatus.DRAFT),
                pack("published", KnowledgePackageStatus.PUBLISHED)));
        when(repository.findByTenantIdAndPackageVersion("tenant-A", "active-2026.06"))
            .thenReturn(List.of(pack("active-2026.06", KnowledgePackageStatus.ACTIVE)));

        assertThat(port.exists("tenant-A", "pkg-2026.06")).isFalse();
        assertThat(port.exists("tenant-A", "active-2026.06")).isTrue();
        assertThat(port.exists("tenant-A", "missing")).isFalse();
    }

    @Test
    void returnsLatestActivePackageVersion() {
        when(repository.findFirstByTenantIdAndStatusOrderByUpdatedAtDesc(
            "tenant-A", KnowledgePackageStatus.ACTIVE))
            .thenReturn(java.util.Optional.of(pack("active-2026.06", KnowledgePackageStatus.ACTIVE)));

        assertThat(port.getActive("tenant-A")).contains("active-2026.06");
    }

    @Test
    void rejectsAmbiguousActivePackagesWithTheSameVersion() {
        when(repository.findByTenantIdAndPackageVersion("tenant-A", "pkg-duplicate"))
            .thenReturn(List.of(
                pack("pkg-duplicate-a", KnowledgePackageStatus.ACTIVE),
                pack("pkg-duplicate-b", KnowledgePackageStatus.ACTIVE)));

        assertThat(port.exists("tenant-A", "pkg-duplicate")).isFalse();
    }

    private KnowledgePackage pack(String version, KnowledgePackageStatus status) {
        Instant now = Instant.parse("2026-06-06T08:00:00Z");
        return new KnowledgePackage(
            1L, "pkg-id-" + version, "tenant-A", "MEDKERNEL.DEFAULT", version,
            "默认配置包", null, status, now, "tester", now, "tester", "trace-package-version");
    }
}
