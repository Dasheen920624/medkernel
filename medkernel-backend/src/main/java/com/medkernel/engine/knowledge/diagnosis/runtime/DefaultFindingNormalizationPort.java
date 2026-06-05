package com.medkernel.engine.knowledge.diagnosis.runtime;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.stereotype.Component;

import com.medkernel.engine.context.CanonicalResourceType;
import com.medkernel.engine.terminology.StandardTerm;
import com.medkernel.engine.terminology.StandardTermRepository;
import com.medkernel.engine.terminology.TermMapping;
import com.medkernel.engine.terminology.TermMappingRepository;

/**
 * 发现标准化默认实现：本地编码 → TERM-01 标准编码，仅认确定性结果（不猜不补、不做字符近似兜底）。
 *
 * <p>镜像 ContextSnapshotService 的字典映射口径：CONDITION→TERM.DIAGNOSIS、OBSERVATION→TERM.LAB、
 * MEDICATION→TERM.DRUG、PROCEDURE→TERM.PROCEDURE。查唯一 CONFIRMED 本地→标准映射并取其标准编码；
 * 缺失或歧义（&gt;1 条 CONFIRMED）均归未映射（守 TERM-01 确定性候选原则）。跨域只读复用 engine.terminology 仓储、不写其表。
 *
 * <p>注：术语状态枚举在 terminology 包内为包私有，"院内直接用标准字典编码则透传"的判定需经 terminology 端口
 * 访问其 ACTIVE 状态，作进一步细化（当前未映射的诚实归类，不伪造命中）。
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
    private final TermMappingRepository mappings;

    public DefaultFindingNormalizationPort(StandardTermRepository standardTerms, TermMappingRepository mappings) {
        this.standardTerms = standardTerms;
        this.mappings = mappings;
    }

    @Override
    public Optional<String> normalize(String tenantId, CanonicalResourceType type, String localCode, String codeSystem) {
        if (localCode == null || localCode.isBlank()) {
            return Optional.empty();
        }
        String targetDictionary = DICTIONARY_BY_TYPE.get(type);
        if (targetDictionary == null) {
            return Optional.empty(); // 该资源类型不纳入诊断发现标准化
        }
        String sourceSystem = codeSystem == null ? "" : codeSystem.trim();
        // 查唯一 CONFIRMED 本地→标准映射；缺失或歧义(>1)均不猜，归未映射
        List<TermMapping> confirmed = mappings.findConfirmedByTenantIdAndAnchor(
            tenantId, sourceSystem.isBlank() ? null : sourceSystem, localCode, targetDictionary, null);
        if (confirmed.size() != 1) {
            return Optional.empty();
        }
        return standardTerms.findByTenantIdAndId(tenantId, confirmed.get(0).standardTermId())
            .map(StandardTerm::termCode);
    }
}
