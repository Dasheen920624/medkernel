package com.medkernel.engine.knowledge.production.gate;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.medkernel.engine.factory.KnowledgeAssetEnvelope;
import com.medkernel.shared.api.error.ApiException;
import com.medkernel.shared.context.OrgScope;
import com.medkernel.shared.context.RequestContext;

/**
 * 候选安全门禁编排服务（AIK-STD-05，FR-1/4/5）。
 *
 * <p>对候选信封跑全部 {@link CandidateGate}（确定性、不依赖模型，按门禁码定序保证结果可复现），逐项落
 * {@code mk_aik_gate_result} 审计轨迹，返回总判定。任一项不过即整体不过、候选不提审（不静默放行，铁律 #1）。
 * 单项内部异常诚实判不过（不吞错放行）。
 */
@Service
public class CandidateSafetyGateService {

    private final List<CandidateGate> gates;
    private final AikGateResultRepository resultRepository;

    public CandidateSafetyGateService(List<CandidateGate> gates, AikGateResultRepository resultRepository) {
        this.gates = gates.stream().sorted(Comparator.comparing(CandidateGate::code)).toList();
        this.resultRepository = resultRepository;
    }

    /** 评估候选并持久化逐项门禁结果，返回总判定。 */
    @Transactional
    public GateOutcome evaluate(KnowledgeAssetEnvelope candidate, GateContext context) {
        List<GateItemResult> items = new ArrayList<>();
        boolean allPassed = true;
        Instant now = Instant.now();
        String actor = RequestContext.currentUserId().orElse(null);

        for (CandidateGate gate : gates) {
            GateItemResult result;
            try {
                result = gate.evaluate(candidate, context);
            } catch (RuntimeException exception) {
                result = GateItemResult.fail(gate.code(), "门禁执行异常：" + exception.getMessage());
            }
            items.add(result);
            if (!result.passed()) {
                allPassed = false;
            }
            resultRepository.save(new AikGateResult(null, context.tenantId(), context.jobCode(),
                candidate.contentHash(), result.code(), result.passed(), result.reason(), now, actor));
        }
        return new GateOutcome(allPassed, items);
    }

    /** 列某 job 的门禁结果（FR-5 可审计回溯），按评估顺序。 */
    @Transactional(readOnly = true)
    public List<AikGateResult> listResults(String jobCode) {
        return resultRepository.findByTenantIdAndJobCodeOrderByIdAsc(requireCurrentTenant(), jobCode);
    }

    private String requireCurrentTenant() {
        OrgScope scope = RequestContext.currentOrgScope();
        if (scope == null || !scope.hasTenant()) {
            throw ApiException.tenantMissing();
        }
        return scope.tenantId();
    }
}
