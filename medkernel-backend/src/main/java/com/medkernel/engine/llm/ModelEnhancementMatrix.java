package com.medkernel.engine.llm;

import java.time.Instant;

import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

/**
 * 全业务模型赋能覆盖矩阵台账（LLM-05）。
 *
 * <p>平台全局「模型网关全局目录」的模型赋能覆盖图谱：登记每个可赋能业务点 ↔ 能力码 ↔ B0 主链 ↔ 配置状态。
 * {@code accessStatus}：{@code ACTIVE} 已启用（须同时具备已登记能力码与 B0 主链，过 {@code ENG-LLM-010} 门禁）、
 * {@code PENDING} 待配置（缺口诚实标注，能力码/ B0 可空）、{@code DISABLED} 停用。仿 {@link ModelCapabilityDefinition}
 * 为平台全局目录（无 {@code tenant_id}），由医疗引擎运营员维护。
 */
@Table("mk_llm_enhancement_matrix")
public record ModelEnhancementMatrix(
    @Id Long id,
    @Column("business_point") String businessPoint,
    @Column("business_name") String businessName,
    @Column("capability_code") String capabilityCode,
    @Column("b0_path") String b0Path,
    @Column("access_status") String accessStatus,
    @Column("enabled_flag") String enabledFlag,
    @Column("sort_order") int sortOrder,
    @Column("created_at") Instant createdAt,
    @Column("created_by") String createdBy,
    @Column("updated_at") Instant updatedAt,
    @Column("updated_by") String updatedBy
) {

    public boolean enabled() {
        return "Y".equalsIgnoreCase(enabledFlag);
    }
}
