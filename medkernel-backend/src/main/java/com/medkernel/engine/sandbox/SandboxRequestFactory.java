package com.medkernel.engine.sandbox;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.medkernel.engine.context.ContextSnapshotRequest;
import com.medkernel.engine.context.ContextSnapshotResources;
import com.medkernel.engine.context.QualityStatus;
import com.medkernel.engine.context.canonical.CanonicalEncounter;
import com.medkernel.engine.context.canonical.CanonicalObservation;
import com.medkernel.engine.context.canonical.CanonicalPatient;
import com.medkernel.engine.embed.EmbedIntegrationMode;
import com.medkernel.engine.embed.EmbedLaunchTokenRequest;
import com.medkernel.engine.recommendation.RecommendationCardRequest;
import com.medkernel.engine.recommendation.RecommendationCardType;
import com.medkernel.engine.recommendation.RecommendationInterruptLevel;
import com.medkernel.engine.recommendation.RecommendationRiskLevel;
import com.medkernel.engine.recommendation.RecommendationSourceRequest;
import com.medkernel.engine.recommendation.RecommendationSourceType;
import com.medkernel.engine.recommendation.RecommendationTriggerRequest;
import com.medkernel.engine.security.DefaultPermissionPolicy;
import com.medkernel.engine.security.PermissionCode;
import com.medkernel.engine.security.RoleCode;
import com.medkernel.shared.context.OrgScope;
import com.medkernel.shared.context.RequestContext;

/**
 * 依据内置场景、标准请求上下文和认证角色构造真实引擎请求。
 */
final class SandboxRequestFactory {

    private static final String SOURCE_SYSTEM = "MEDKERNEL_SANDBOX";

    private SandboxRequestFactory() {
    }

    static ContextSnapshotRequest snapshot(
            SandboxScenario scenario,
            SandboxRunRequest request,
            String traceId,
            ObjectMapper json) {
        OrgScope scope = RequestContext.currentOrgScope();
        String tenantId = requireTenant(scope);
        String orgUnitId = scope.nearestOrgUnitIdOrTenant(tenantId);
        Instant occurredAt = request.occurredAt() == null ? Instant.now() : request.occurredAt();
        ContextSnapshotResources resources = request.contextOverride() == null
            ? defaultResources(scenario, occurredAt)
            : json.convertValue(request.contextOverride(), ContextSnapshotResources.class);

        return new ContextSnapshotRequest(
            "sandbox:" + scenario.id() + ":" + traceId,
            traceId,
            tenantId,
            scope.groupId(),
            scope.hospitalId(),
            scope.campusId(),
            scope.siteId(),
            scope.departmentId(),
            scope.wardId(),
            scope.specialtyId(),
            RequestContext.currentUserId().orElse(null),
            authenticatedRoleCodes(),
            scenario.patientId(),
            scenario.encounterId(),
            orgUnitId,
            scenario.packageVersion(),
            resources);
    }

    static RecommendationTriggerRequest trigger(
            SandboxScenario scenario,
            String snapshotId,
            SandboxRunRequest request,
            String traceId,
            String patientPathwayId) {
        Instant occurredAt = request.occurredAt() == null ? Instant.now() : request.occurredAt();
        String runScopedTriggerCode = "sandbox:" + scenario.id() + ":" + traceId;
        return new RecommendationTriggerRequest(
            runScopedTriggerCode,
            scenario.triggerPoint(),
            "sandbox-event:" + scenario.id() + ":" + traceId,
            snapshotId,
            scenario.patientId(),
            scenario.encounterId(),
            patientPathwayId,
            scenario.id(),
            scenario.packageVersion(),
            "sandbox:" + scenario.expectedRuleCode() + ":" + snapshotId,
            occurredAt,
            recommendationCandidates(scenario, patientPathwayId),
            Boolean.FALSE);
    }

    static EmbedLaunchTokenRequest launchToken(
            SandboxScenario scenario,
            SandboxRunRequest request,
            String traceId) {
        return new EmbedLaunchTokenRequest(
            primarySandboxRole(),
            scenario.patientId(),
            scenario.encounterId(),
            scenario.triggerPoint(),
            300,
            request.integrationMode(),
            scenario.triggerPoint(),
            traceId,
            request.parentOrigin());
    }

    private static List<RecommendationCardRequest> recommendationCandidates(
            SandboxScenario scenario,
            String patientPathwayId) {
        if ("RULE_ONLY".equals(scenario.playbook())) {
            return List.of();
        }
        RecommendationCardType cardType = switch (scenario.playbook()) {
            case "PATHWAY" -> RecommendationCardType.PATHWAY;
            case "FOLLOWUP" -> RecommendationCardType.FOLLOWUP;
            case "EVALUATION" -> RecommendationCardType.QUALITY;
            case "EMBED" -> RecommendationCardType.KNOWLEDGE;
            default -> RecommendationCardType.EXAM;
        };
        RecommendationRiskLevel riskLevel = RecommendationRiskLevel.valueOf(
            scenario.expectedSeverity());
        RecommendationInterruptLevel interruptLevel =
            riskLevel == RecommendationRiskLevel.LOW
                ? RecommendationInterruptLevel.INFO
                : RecommendationInterruptLevel.WEAK_INTERRUPTIVE;
        String sourceRefId = patientPathwayId == null ? scenario.id() : patientPathwayId;
        return List.of(new RecommendationCardRequest(
            "SBX." + scenario.playbook(),
            cardType,
            scenario.title(),
            "沙盘引擎编排根据标准上下文生成可追溯的人工确认建议。",
            scenario.expectedAction(),
            riskLevel,
            interruptLevel,
            true,
            false,
            "全真体验沙盘受控编排候选卡",
            "{\"reason\":\"SANDBOX_ENGINE_ORCHESTRATION\"}",
            "sandbox:" + scenario.id() + ":" + scenario.patientId(),
            null,
            null,
            List.of(new RecommendationSourceRequest(
                "PATHWAY".equals(scenario.playbook())
                    ? RecommendationSourceType.PATHWAY
                    : RecommendationSourceType.CONTEXT,
                sourceRefId,
                scenario.packageVersion(),
                "全真体验沙盘标准上下文",
                "sandbox:" + scenario.id(),
                null,
                "由沙盘场景与标准上下文确定性生成"))));
    }

    private static ContextSnapshotResources defaultResources(
            SandboxScenario scenario,
            Instant occurredAt) {
        CanonicalPatient patient = new CanonicalPatient(
            scenario.patientId(),
            "沙盘患者",
            LocalDate.of(1965, 6, 1),
            "M",
            List.of(),
            SOURCE_SYSTEM,
            scenario.patientId(),
            scenario.packageVersion(),
            occurredAt,
            occurredAt,
            QualityStatus.VALID);
        CanonicalEncounter encounter = new CanonicalEncounter(
            scenario.encounterId(),
            "ED",
            occurredAt.minusSeconds(3600),
            null,
            "ED",
            "SBX-DOCTOR-001",
            null,
            SOURCE_SYSTEM,
            scenario.encounterId(),
            scenario.packageVersion(),
            occurredAt,
            occurredAt,
            QualityStatus.VALID);
        CanonicalObservation observation = new CanonicalObservation(
            "SBX-LAB-K-OBS-001",
            "2823-3",
            "血清钾",
            new BigDecimal("6.8"),
            null,
            "mmol/L",
            "3.5-5.5",
            "HIGH",
            SOURCE_SYSTEM,
            "SBX-LAB-K-RESULT-001",
            scenario.packageVersion(),
            occurredAt,
            occurredAt,
            QualityStatus.VALID);
        return new ContextSnapshotResources(
            patient,
            List.of(),
            List.of(encounter),
            List.of(),
            List.of(),
            List.of(observation),
            List.of(),
            List.of(),
            List.of(),
            List.of(),
            List.of(),
            List.of(),
            List.of());
    }

    private static String requireTenant(OrgScope scope) {
        if (scope == null || !scope.hasTenant()) {
            throw new IllegalStateException("运行沙盘缺少租户上下文");
        }
        return scope.tenantId();
    }

    private static List<String> authenticatedRoleCodes() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            return List.of();
        }
        return authentication.getAuthorities().stream()
            .map(authority -> RoleCode.fromAuthority(authority.getAuthority()).orElse(null))
            .filter(role -> role != null)
            .map(RoleCode::code)
            .distinct()
            .toList();
    }

    private static String primarySandboxRole() {
        List<String> authenticatedRoles = authenticatedRoleCodes();
        return Arrays.stream(RoleCode.values())
            .filter(role -> authenticatedRoles.contains(role.code()))
            .filter(role -> DefaultPermissionPolicy.permissionsOf(role).contains(PermissionCode.SANDBOX_RUN))
            .findFirst()
            .map(RoleCode::code)
            .orElseThrow(() -> new IllegalStateException("当前认证用户没有可签发沙盘嵌入令牌的职责角色"));
    }
}
