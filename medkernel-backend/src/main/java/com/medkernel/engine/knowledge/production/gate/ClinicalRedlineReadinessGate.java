package com.medkernel.engine.knowledge.production.gate;

import java.util.EnumSet;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;

import com.medkernel.engine.factory.KnowledgeAssetEnvelope;
import com.medkernel.engine.safety.ClinicalRedlineCatalogResponse;
import com.medkernel.engine.safety.ClinicalRedlineCategory;
import com.medkernel.engine.safety.ClinicalRedlineContentStatus;
import com.medkernel.engine.safety.ClinicalRedlineService;

/**
 * 门禁：临床安全红线体系 readiness（AIK-STD-05，FR-2 红线/剂量/高危）。
 *
 * <p>候选提审前必须确认 OPT-04 五类红线目录均已配置。当前 B0 模板候选没有可执行临床逻辑，门禁只做确定性
 * readiness 校验；后续候选 payload 具备结构化逻辑后，再在本门禁内扩展逐条命中校验。
 */
@Component
public class ClinicalRedlineReadinessGate implements CandidateGate {

    public static final String CODE = "CLINICAL_REDLINE";

    private final ClinicalRedlineService redlineService;

    public ClinicalRedlineReadinessGate(ClinicalRedlineService redlineService) {
        this.redlineService = redlineService;
    }

    @Override
    public String code() {
        return CODE;
    }

    @Override
    public GateItemResult evaluate(KnowledgeAssetEnvelope candidate, GateContext context) {
        ClinicalRedlineCatalogResponse catalog = redlineService.activeCatalog(null);
        if (catalog == null || catalog.contentStatus() == ClinicalRedlineContentStatus.NOT_CONFIGURED) {
            return GateItemResult.fail(CODE, "临床安全红线目录未配置，无法完成红线/剂量/高危门禁");
        }
        Set<ClinicalRedlineCategory> configured = catalog.redlines() == null
            ? EnumSet.noneOf(ClinicalRedlineCategory.class)
            : catalog.redlines().stream()
                .filter(row -> row != null && row.category() != null)
                .map(row -> row.category())
                .collect(Collectors.toCollection(() -> EnumSet.noneOf(ClinicalRedlineCategory.class)));
        for (ClinicalRedlineCategory required : ClinicalRedlineCategory.requiredSafetyCategories()) {
            if (!configured.contains(required)) {
                return GateItemResult.fail(CODE, "临床安全红线类目未配置：" + required.name());
            }
        }
        return GateItemResult.pass(CODE);
    }
}
