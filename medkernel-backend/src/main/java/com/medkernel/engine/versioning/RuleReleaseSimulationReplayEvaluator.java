package com.medkernel.engine.versioning;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import org.springframework.stereotype.Component;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.medkernel.engine.context.ContextSnapshot;
import com.medkernel.engine.context.ContextSnapshotResponse;
import com.medkernel.engine.context.ContextSnapshotService;
import com.medkernel.engine.context.ContextSnapshotStatus;
import com.medkernel.engine.org.OrgHierarchyRepository;
import com.medkernel.engine.org.OrgUnit;
import com.medkernel.engine.rule.RuleApplicabilityDecision;
import com.medkernel.engine.rule.RuleApplicabilityService;
import com.medkernel.engine.rule.RuleDefinition;
import com.medkernel.engine.rule.RuleDefinitionRepository;
import com.medkernel.engine.rule.RuleDslEvaluation;
import com.medkernel.engine.rule.RuleDslEvaluator;
import com.medkernel.engine.rule.RuleRiskLevel;
import com.medkernel.engine.rule.RuleVersion;
import com.medkernel.engine.rule.RuleVersionRepository;
import com.medkernel.shared.api.error.ApiException;
import com.medkernel.shared.api.error.ErrorCode;
import com.medkernel.shared.context.OrgLevel;
import com.medkernel.shared.context.OrgScope;

/**
 * 规则资产发布前只读历史回放执行器。
 */
@Component
public class RuleReleaseSimulationReplayEvaluator implements ReleaseSimulationReplayEvaluator {

    private final RuleDefinitionRepository definitions;
    private final RuleVersionRepository versions;
    private final ContextSnapshotService contextSnapshots;
    private final RuleDslEvaluator evaluator;
    private final RuleApplicabilityService applicability;
    private final OrgHierarchyRepository orgHierarchy;
    private final ObjectMapper json;

    public RuleReleaseSimulationReplayEvaluator(
            RuleDefinitionRepository definitions,
            RuleVersionRepository versions,
            ContextSnapshotService contextSnapshots,
            RuleDslEvaluator evaluator,
            RuleApplicabilityService applicability,
            OrgHierarchyRepository orgHierarchy,
            ObjectMapper json) {
        this.definitions = definitions;
        this.versions = versions;
        this.contextSnapshots = contextSnapshots;
        this.evaluator = evaluator;
        this.applicability = applicability;
        this.orgHierarchy = orgHierarchy;
        this.json = json;
    }

    @Override
    public boolean supports(VersionedAssetType assetType) {
        return assetType == VersionedAssetType.RULE;
    }

    @Override
    public ReleaseSimulationResult.Replay replay(
            ReleaseSimulationCommand command,
            AssetVersion currentVersion,
            AssetVersion candidateVersion,
            List<ContextSnapshot> snapshots) {
        RuleVersion candidateRule = requireRuleVersion(candidateVersion);
        RuleVersion currentRule = currentVersion == null ? null : requireRuleVersion(currentVersion);
        JsonNode candidateDsl = readDsl(candidateRule);
        JsonNode currentDsl = currentRule == null ? null : readDsl(currentRule);
        long changed = 0;
        long triggerIncreases = 0;
        long triggerDecreases = 0;
        long severityIncreases = 0;
        long severityDecreases = 0;
        List<String> highRiskSnapshotIds = new ArrayList<>();

        for (ContextSnapshot snapshot : snapshots) {
            ContextSnapshotResponse context = contextSnapshots.findById(snapshot.snapshotId());
            if (context.status() != ContextSnapshotStatus.ACTIVE || context.resources() == null) {
                throw new ApiException(
                    ErrorCode.CONFLICT,
                    "历史回放快照不可用: " + snapshot.snapshotId()
                );
            }
            JsonNode contextJson = json.valueToTree(context.resources());
            OrgScope orgScope = orgScope(snapshot);
            RuleDslEvaluation before = evaluate(
                currentRule, currentDsl, contextJson, orgScope, context.runtimeReleaseId());
            RuleDslEvaluation after = evaluate(
                candidateRule, candidateDsl, contextJson, orgScope, context.runtimeReleaseId());
            boolean caseChanged = before.hit() != after.hit()
                || before.severity() != after.severity()
                || !actionSignature(before).equals(actionSignature(after));
            if (caseChanged) {
                changed++;
            }
            if (!before.hit() && after.hit()) {
                triggerIncreases++;
            } else if (before.hit() && !after.hit()) {
                triggerDecreases++;
            }
            if (before.hit() && after.hit()) {
                int severityDirection = compareSeverity(before.severity(), after.severity());
                if (severityDirection > 0) {
                    severityIncreases++;
                } else if (severityDirection < 0) {
                    severityDecreases++;
                }
            }
            if (isHighRiskIncrease(before, after)) {
                highRiskSnapshotIds.add(snapshot.snapshotId());
            }
        }
        return new ReleaseSimulationResult.Replay(
            "SUPPORTED",
            snapshots.size(),
            changed,
            triggerIncreases,
            triggerDecreases,
            severityIncreases,
            severityDecreases,
            highRiskSnapshotIds,
            List.of(),
            null
        );
    }

    private RuleVersion requireRuleVersion(AssetVersion assetVersion) {
        RuleDefinition definition = definitions.findByTenantIdAndRuleCode(
            assetVersion.tenantId(),
            assetVersion.assetIdentity()
        ).orElseThrow(() -> new ApiException(
            ErrorCode.NOT_FOUND,
            "规则资产缺少规则定义: " + assetVersion.assetIdentity()
        ));
        int versionNo = AssetVersionNumbers.intSequence(
            assetVersion.versionNo(), "规则统一资产版本号");
        return versions.findByRuleIdAndTenantIdAndVersionNo(
            definition.ruleId(),
            assetVersion.tenantId(),
            versionNo
        ).orElseThrow(() -> new ApiException(
            ErrorCode.NOT_FOUND,
            "规则统一资产版本缺少对应 DSL: " + assetVersion.versionId()
        ));
    }

    private RuleDslEvaluation evaluate(
            RuleVersion version,
            JsonNode dsl,
            JsonNode context,
            OrgScope orgScope,
            String runtimeReleaseId) {
        if (version == null || dsl == null) {
            return new RuleDslEvaluation(false, null, List.of(), json.createObjectNode());
        }
        RuleApplicabilityDecision decision = applicability.evaluate(
            dsl,
            context,
            orgScope,
            version.versionId()
        );
        if (!decision.applicable()) {
            return new RuleDslEvaluation(false, null, List.of(), decision.details());
        }
        return evaluator.evaluate(dsl, context, version.tenantId(), runtimeReleaseId);
    }

    private OrgScope orgScope(ContextSnapshot snapshot) {
        String groupId = null;
        String hospitalId = null;
        String campusId = null;
        String siteId = null;
        String departmentId = null;
        String specialtyId = null;
        for (OrgUnit unit : orgHierarchy.findAncestorsAndSelf(snapshot.tenantId(), snapshot.orgUnitId())) {
            if (unit.specialtyId() != null && !unit.specialtyId().isBlank()) {
                specialtyId = unit.specialtyId();
            }
            if (unit.level() == OrgLevel.REGION) {
                groupId = unit.id();
            } else if (unit.level() == OrgLevel.FACILITY) {
                hospitalId = unit.id();
            } else if (unit.level() == OrgLevel.CAMPUS) {
                campusId = unit.id();
            } else if (unit.level() == OrgLevel.DEPARTMENT) {
                departmentId = unit.id();
            } else if (unit.level() == OrgLevel.WARD) {
                siteId = unit.id();
            }
        }
        return new OrgScope(
            snapshot.tenantId(),
            groupId,
            hospitalId,
            campusId,
            siteId,
            departmentId,
            null,
            specialtyId
        );
    }

    private List<String> actionSignature(RuleDslEvaluation evaluation) {
        return evaluation.actions().stream()
            .map(action -> action.actionCode().name() + ":" + action.severity().name())
            .sorted()
            .toList();
    }

    private int compareSeverity(RuleRiskLevel before, RuleRiskLevel after) {
        int beforeOrdinal = before == null ? -1 : before.ordinal();
        int afterOrdinal = after == null ? -1 : after.ordinal();
        return Integer.compare(afterOrdinal, beforeOrdinal);
    }

    private boolean isHighRiskIncrease(
            RuleDslEvaluation before,
            RuleDslEvaluation after) {
        if (!after.hit()
                || (after.severity() != RuleRiskLevel.HIGH
                    && after.severity() != RuleRiskLevel.CRITICAL)) {
            return false;
        }
        return !before.hit() || compareSeverity(before.severity(), after.severity()) > 0;
    }

    private JsonNode readDsl(RuleVersion version) {
        try {
            return json.readTree(version.dslJson());
        } catch (JsonProcessingException exception) {
            throw new ApiException(
                ErrorCode.VALIDATION_FAILED,
                "规则 DSL 无法解析: " + version.versionId(),
                exception
            );
        }
    }
}
