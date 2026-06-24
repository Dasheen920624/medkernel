package com.medkernel.engine.knowledge.diagnosis.runtime;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.stereotype.Component;

import com.medkernel.engine.context.CanonicalResourceType;
import com.medkernel.engine.terminology.EffectiveTermMapping;
import com.medkernel.engine.terminology.EffectiveTermMappingResolver;
import com.medkernel.engine.terminology.StandardTerm;
import com.medkernel.engine.terminology.StandardTermRepository;
import com.medkernel.engine.versioning.PlatformAuthority;

/**
 * 发现标准化默认实现：本地编码 → TERM-01 标准编码，仅认确定性结果（不猜不补、不做字符近似兜底）。
 *
 * <p>镜像 ContextSnapshotService 的字典映射口径：CONDITION→TERM.DIAGNOSIS、OBSERVATION→TERM.LAB、
 * MEDICATION→TERM.DRUG、PROCEDURE→TERM.PROCEDURE。优先取唯一已全量激活术语包快照的标准编码；
 * 无映射时若 localCode 本身已是该字典 ACTIVE 标准码则透传（院内直接用标准字典编码的场景）；
 * 歧义（&gt;1 个激活目标）保持未映射、不以透传掩盖冲突（守 TERM-01 确定性候选原则）。
 * 跨域只读复用 engine.terminology 有效映射解析器，不写其表。
 */
@Component
public class DefaultFindingNormalizationPort implements FindingNormalizationPort {

    private static final Map<CanonicalResourceType, String> DICTIONARY_BY_TYPE =
        new EnumMap<>(CanonicalResourceType.class);

    static {
        DICTIONARY_BY_TYPE.put(CanonicalResourceType.CONDITION, "TERM.DIAGNOSIS");
        DICTIONARY_BY_TYPE.put(CanonicalResourceType.OBSERVATION, "TERM.LAB");
        DICTIONARY_BY_TYPE.put(CanonicalResourceType.MEDICATION, "TERM.DRUG");
        DICTIONARY_BY_TYPE.put(CanonicalResourceType.PROCEDURE, "TERM.PROCEDURE");
    }

    private final StandardTermRepository standardTerms;
    private final EffectiveTermMappingResolver effectiveMappings;

    public DefaultFindingNormalizationPort(
            StandardTermRepository standardTerms,
            EffectiveTermMappingResolver effectiveMappings) {
        this.standardTerms = standardTerms;
        this.effectiveMappings = effectiveMappings;
    }

    @Override
    public Optional<String> normalize(
            String tenantId,
            String runtimeReleaseId,
            CanonicalResourceType type,
            String localCode,
            String codeSystem) {
        if (localCode == null || localCode.isBlank()) {
            return Optional.empty();
        }
        String targetDictionary = DICTIONARY_BY_TYPE.get(type);
        if (targetDictionary == null) {
            return Optional.empty(); // 该资源类型不纳入诊断发现标准化
        }
        String sourceSystem = codeSystem == null ? "" : codeSystem.trim();
        List<String> standardSources = standardTermSources(tenantId);
        List<EffectiveTermMapping> confirmed = effectiveMappings.resolve(
            tenantId, runtimeReleaseId,
            sourceSystem.isBlank() ? null : sourceSystem,
            localCode, targetDictionary, null);
        if (confirmed.size() == 1) {
            return Optional.ofNullable(confirmed.get(0).standardCode());
        }
        if (confirmed.isEmpty()) {
            // 院内直接用标准字典编码：无映射时，localCode 本身若是该字典 ACTIVE 标准码则透传（不猜不补）
            return standardTerms.findFirstActiveByTenantIdsAndStandardSystemAndTermCode(
                    standardSources, tenantId, targetDictionary, localCode)
                .map(StandardTerm::termCode);
        }
        return Optional.empty(); // 歧义：保持未映射，不以透传掩盖映射冲突
    }

    private static List<String> standardTermSources(String tenantId) {
        String current = tenantId == null ? "" : tenantId.trim();
        if (PlatformAuthority.PLATFORM_TENANT_ID.equals(current)) {
            return List.of(PlatformAuthority.PLATFORM_TENANT_ID);
        }
        if (current.isBlank()) {
            return List.of(PlatformAuthority.PLATFORM_TENANT_ID);
        }
        return List.of(PlatformAuthority.PLATFORM_TENANT_ID, current);
    }
}
