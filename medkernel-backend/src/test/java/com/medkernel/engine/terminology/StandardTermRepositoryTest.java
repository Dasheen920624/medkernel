package com.medkernel.engine.terminology;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.Test;

/**
 * 标准术语仓储 {@code findActive} 便捷方法：委托底层 status 查询并固定传 ACTIVE，
 * 把包私有状态枚举封装在 terminology 包内，供跨包（如诊断发现标准化）只读复用而无需引用枚举。
 */
class StandardTermRepositoryTest {

    @Test
    void findActiveDelegatesWithActiveStatusOnly() {
        StandardTermRepository repo = mock(StandardTermRepository.class, CALLS_REAL_METHODS);
        StandardTerm active = term("ICD-X");
        when(repo.findByTenantIdAndStandardSystemAndTermCodeAndStatus(
                "t-1", "TERM.DIAGNOSIS", "ICD-X", StandardTermStatus.ACTIVE))
            .thenReturn(Optional.of(active));

        assertThat(repo.findActiveByTenantIdAndStandardSystemAndTermCode("t-1", "TERM.DIAGNOSIS", "ICD-X"))
            .contains(active);
    }

    @Test
    void findActiveReturnsEmptyWhenNoActiveTerm() {
        StandardTermRepository repo = mock(StandardTermRepository.class, CALLS_REAL_METHODS);

        assertThat(repo.findActiveByTenantIdAndStandardSystemAndTermCode("t-1", "TERM.DIAGNOSIS", "MISSING"))
            .isEmpty();
    }

    private StandardTerm term(String code) {
        return new StandardTerm(1L, "t-1", "TERM.DIAGNOSIS", code, null, code, null, null,
            StandardTermStatus.ACTIVE, null, null, null, null, null, null);
    }
}
