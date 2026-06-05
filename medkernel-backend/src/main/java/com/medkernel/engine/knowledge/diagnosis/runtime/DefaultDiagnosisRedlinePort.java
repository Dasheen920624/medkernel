package com.medkernel.engine.knowledge.diagnosis.runtime;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.medkernel.engine.context.ContextSnapshotResponse;
import com.medkernel.engine.knowledge.KnowledgeAssetVersionRepository;
import com.medkernel.engine.knowledge.KnowledgeDomain;
import com.medkernel.engine.knowledge.KnowledgeIdentity;
import com.medkernel.engine.knowledge.KnowledgeIdentityRepository;
import com.medkernel.engine.rule.RuleDslEvaluator;
import com.medkernel.engine.safety.ClinicalRedlineRepository;
import com.medkernel.engine.safety.ClinicalRedlineRule;
import com.medkernel.engine.safety.ClinicalRedlineStatus;
import com.medkernel.shared.api.error.ApiException;
import com.medkernel.shared.api.error.ErrorCode;
import com.medkernel.shared.context.PlatformTenant;

import org.springframework.stereotype.Component;

/**
 * 红线合流默认实现：对 OPT-04 ACTIVE 红线 DSL 按患者结构化上下文求值，命中且红线经
 * {@code source_version_id} 关联到 DIAGNOSIS 身份时，返回该诊断身份码（编排侧据此置顶且不可疲劳抑制）。
 *
 * <p>跨域只读复用 OPT-04 红线仓储与规则 DSL 执行器，不改红线表、不写其数据。生效集为平台 +
 * 本租户 ACTIVE 红线；置顶是安全正向动作，输出按身份码去重，无需再做平台/租户互斥裁决。
 * 红线未关联诊断来源（DDI / 剂量 / 危急值等纯安全红线归推荐主链路 ClinicalRedlineMatcher 处理）、
 * 未命中、无红线时返回空集，诚实降级不阻断主链路。
 */
@Component
public class DefaultDiagnosisRedlinePort implements DiagnosisRedlinePort {

    private final ClinicalRedlineRepository redlines;
    private final KnowledgeAssetVersionRepository versions;
    private final KnowledgeIdentityRepository identities;
    private final RuleDslEvaluator evaluator;
    private final ObjectMapper json;

    public DefaultDiagnosisRedlinePort(
            ClinicalRedlineRepository redlines,
            KnowledgeAssetVersionRepository versions,
            KnowledgeIdentityRepository identities,
            RuleDslEvaluator evaluator,
            ObjectMapper json) {
        this.redlines = redlines;
        this.versions = versions;
        this.identities = identities;
        this.evaluator = evaluator;
        this.json = json;
    }

    @Override
    public Set<String> pinnedDiagnosisCodes(String tenantId, ContextSnapshotResponse snapshot) {
        if (snapshot == null) {
            return Set.of();
        }
        JsonNode context = json.valueToTree(snapshot.resources());
        Set<String> pinned = new LinkedHashSet<>();
        for (ClinicalRedlineRule redline : activeRedlines(tenantId)) {
            String diagnosisCode = diagnosisCode(redline);
            if (diagnosisCode == null) {
                continue; // 红线未关联诊断身份：不置顶诊断候选
            }
            if (evaluator.evaluate(parseDsl(redline), context).hit()) {
                pinned.add(diagnosisCode);
            }
        }
        return Set.copyOf(pinned);
    }

    /** 生效 ACTIVE 红线：非平台租户叠加平台基线红线 + 本租户红线（输出按身份码去重，置顶是安全正向叠加）。 */
    private List<ClinicalRedlineRule> activeRedlines(String tenantId) {
        List<ClinicalRedlineRule> rules = new ArrayList<>();
        if (!PlatformTenant.isPlatformTenant(tenantId)) {
            rules.addAll(redlines.findByTenantIdAndStatusOrderByCategoryAscRedlineKeyAscUpdatedAtDesc(
                PlatformTenant.ID, ClinicalRedlineStatus.ACTIVE));
        }
        rules.addAll(redlines.findByTenantIdAndStatusOrderByCategoryAscRedlineKeyAscUpdatedAtDesc(
            tenantId, ClinicalRedlineStatus.ACTIVE));
        return rules;
    }

    /** 红线 {@code source_version_id} → 知识版本 → 身份；仅 DIAGNOSIS 域返回身份码，否则返回 null。 */
    private String diagnosisCode(ClinicalRedlineRule redline) {
        if (redline.sourceVersionId() == null || isBlank(redline.conditionDsl())) {
            return null;
        }
        return versions.findByTenantIdAndId(redline.tenantId(), redline.sourceVersionId())
            .flatMap(version -> identities.findByTenantIdAndId(redline.tenantId(), version.identityId()))
            .filter(identity -> identity.domain() == KnowledgeDomain.DIAGNOSIS)
            .map(KnowledgeIdentity::identityCode)
            .orElse(null);
    }

    private JsonNode parseDsl(ClinicalRedlineRule redline) {
        try {
            return json.readTree(redline.conditionDsl());
        } catch (JsonProcessingException e) {
            throw new ApiException(ErrorCode.ENG_RULE_001, "红线 DSL 不是合法 JSON: " + redline.redlineId());
        }
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
