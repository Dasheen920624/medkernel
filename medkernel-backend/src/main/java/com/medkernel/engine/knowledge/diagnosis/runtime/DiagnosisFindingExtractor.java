package com.medkernel.engine.knowledge.diagnosis.runtime;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import org.springframework.stereotype.Component;

import com.medkernel.engine.context.CanonicalResourceType;
import com.medkernel.engine.context.ContextSnapshotResources;
import com.medkernel.engine.context.canonical.CanonicalCondition;
import com.medkernel.engine.context.canonical.CanonicalMedication;
import com.medkernel.engine.context.canonical.CanonicalObservation;
import com.medkernel.engine.context.canonical.CanonicalProcedure;

/**
 * 从患者上下文快照提取标准化发现编码集合，未标准化项归入 unmapped（部分可用，不阻断）。
 *
 * <p>仅 CONDITION 资源携带显式 codeSystem；OBSERVATION/MEDICATION/PROCEDURE 由资源类型隐含编码体系，传 null。
 */
@Component
public class DiagnosisFindingExtractor {

    private final FindingNormalizationPort port;

    public DiagnosisFindingExtractor(FindingNormalizationPort port) {
        this.port = port;
    }

    public ExtractedFindings extract(String tenantId, ContextSnapshotResources r) {
        Set<String> normalized = new LinkedHashSet<>();
        List<String> unmapped = new ArrayList<>();
        for (CanonicalCondition c : r.conditions()) {
            classify(tenantId, CanonicalResourceType.CONDITION, c.code(), c.codeSystem(), normalized, unmapped);
        }
        for (CanonicalObservation o : r.observations()) {
            classify(tenantId, CanonicalResourceType.OBSERVATION, o.code(), null, normalized, unmapped);
        }
        for (CanonicalMedication m : r.medications()) {
            classify(tenantId, CanonicalResourceType.MEDICATION, m.code(), null, normalized, unmapped);
        }
        for (CanonicalProcedure p : r.procedures()) {
            classify(tenantId, CanonicalResourceType.PROCEDURE, p.code(), null, normalized, unmapped);
        }
        return new ExtractedFindings(Set.copyOf(normalized), List.copyOf(unmapped));
    }

    private void classify(String tenantId, CanonicalResourceType type, String code, String system,
                          Set<String> normalized, List<String> unmapped) {
        if (code == null || code.isBlank()) {
            return;
        }
        port.normalize(tenantId, type, code, system)
            .ifPresentOrElse(normalized::add, () -> unmapped.add(code));
    }
}
