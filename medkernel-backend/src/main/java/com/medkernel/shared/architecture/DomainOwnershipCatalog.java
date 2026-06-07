package com.medkernel.shared.architecture;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;

/**
 * MedKernel 领域实体和持久化表的单一 owner 目录。
 */
public final class DomainOwnershipCatalog {
    private static final List<DomainModule> MODULES = List.of(
        module("shared-audit", packages("com.medkernel.shared.audit"), prefixes(), tables("audit_event", "audit_chain_head")),
        module("shared-config", packages("com.medkernel.shared.config"), prefixes("mk_config_"), tables()),
        module("shared-idempotency", packages("com.medkernel.shared.idempotency"), prefixes(), tables("sys_idempotency")),
        module("shared-observability", packages("com.medkernel.shared.observability"), prefixes("mk_obs_"), tables()),
        module("shared-runtime-task", packages("com.medkernel.shared.runtime.task"), prefixes(),
            tables("sys_task", "sys_task_dead_letter")),
        module("engine-security", packages("com.medkernel.engine.security"), prefixes("mk_security_"),
            tables("sys_role", "sys_permission", "role_permission", "user_role_assignment",
                "platform_credential", "emergency_permission_grant", "sys_login_attempt",
                "sys_password_reset_token", "tenant_user")),
        module("engine-org", packages("com.medkernel.engine.org"), prefixes("org_"), tables()),
        module("engine-context", packages("com.medkernel.engine.context"),
            prefixes("context_", "clinical_event", "mk_context_"),
            tables("canonical_resource")),
        module("engine-clinical", packages("com.medkernel.engine.clinical"), prefixes("mk_clinical_"), tables()),
        module("engine-rule", packages("com.medkernel.engine.rule"), prefixes("rule_"), tables()),
        module("engine-pathway", packages("com.medkernel.engine.pathway"), prefixes("pathway_", "specialty_"),
            tables("patient_pathway", "clinical_clock")),
        module("engine-knowledge", packages("com.medkernel.engine.knowledge"),
            prefixes("knowledge_asset_", "knowledge_export_", "source_", "mk_diagnosis_"),
            tables("knowledge_identity", "knowledge_supersession", "citation",
                "mk_knowledge_candidate_classification", "mk_knowledge_review_assignment",
                "mk_knowledge_invalidation", "mk_knowledge_affected_case_task")),
        module("engine-package", packages("com.medkernel.engine.pkg"), prefixes("mk_pkg_"),
            tables("knowledge_package", "package_item", "release_plan", "sync_log")),
        module("engine-versioning", packages("com.medkernel.engine.versioning"), prefixes("mk_version_"), tables()),
        module("engine-projection", packages("com.medkernel.engine.projection"), prefixes("mk_projection_"), tables()),
        module("engine-evaluation", packages("com.medkernel.engine.evaluation"),
            prefixes("evaluation_", "rectification_"), tables("quality_finding")),
        module("engine-quality", packages("com.medkernel.engine.quality"),
            prefixes("mk_quality_"), tables()),
        module("engine-emr-level", packages("com.medkernel.engine.emrlevel"),
            prefixes("mk_emr_level_"), tables()),
        module("engine-terminology", packages("com.medkernel.engine.terminology"),
            prefixes("term_", "mapping_"), tables("standard_term", "local_term", "mk_term_high_risk_rule")),
        module("engine-experience",
            packages("com.medkernel.engine.experience", "com.medkernel.engine.list"),
            prefixes("mk_experience_"), tables()),
        module("engine-workflow", packages("com.medkernel.engine.workflow"), prefixes("mk_engine_workflow_"),
            tables("mk_engine_notification")),
        module("engine-followup", packages("com.medkernel.engine.followup"), prefixes("followup_"), tables()),
        module("engine-integration", packages("com.medkernel.engine.integration"),
            prefixes("integration_", "mk_fhir_", "mk_integration_"), tables()),
        module("engine-mpi", packages("com.medkernel.engine.mpi"), prefixes("mpi_"),
            tables("mk_mpi_merge_review")),
        module("engine-safety", packages("com.medkernel.engine.safety"), prefixes(),
            tables("mk_engine_clinical_redline", "mk_engine_clinical_redline_trial")),
        module("engine-recommendation",
            packages("com.medkernel.engine.recommendation", "com.medkernel.engine.cdss.risk"),
            prefixes("recommendation_"),
            tables("mk_engine_cdss_risk_matrix")),
        module("engine-llm", packages("com.medkernel.engine.llm"), prefixes("model_capability_"), tables()),
        module("engine-embed", packages("com.medkernel.engine.embed"), prefixes("embed_"), tables()),
        module("engine-tenant", packages("com.medkernel.engine.tenant"), prefixes(),
            tables("tenant_branding", "tenant_success_plan")),
        module("compliance-evidence", packages("com.medkernel.compliance.evidence"), prefixes("evidence_"), tables()),
        module("compliance-security", packages(
            "com.medkernel.compliance.datapermission",
            "com.medkernel.compliance.masking",
            "com.medkernel.compliance.exportapproval",
            "com.medkernel.compliance.identitybinding",
            "com.medkernel.compliance.interopassessment"),
            prefixes("mk_compliance_"), tables())
    );

    private DomainOwnershipCatalog() {
    }

    public static List<DomainModule> modules() {
        return MODULES;
    }

    public static Optional<DomainModule> ownerOfTable(String tableName) {
        String normalized = normalize(tableName);
        List<DomainModule> owners = MODULES.stream()
            .filter(module -> module.ownsTable(normalized))
            .toList();
        if (owners.size() > 1) {
            throw new IllegalStateException("表 " + normalized + " 存在多个 owner: "
                + owners.stream().map(DomainModule::id).toList());
        }
        return owners.stream().findFirst();
    }

    public static Set<String> tableOwnershipTokens() {
        Set<String> tokens = new LinkedHashSet<>();
        MODULES.forEach(module -> {
            module.tablePrefixes().forEach(tokens::add);
            module.tableNames().forEach(tokens::add);
        });
        return Set.copyOf(tokens);
    }

    private static DomainModule module(String id, Set<String> packages, Set<String> prefixes, Set<String> tables) {
        return new DomainModule(id, packages, prefixes, tables);
    }

    private static Set<String> packages(String... values) {
        return Set.of(values);
    }

    private static Set<String> prefixes(String... values) {
        return normalizeSet(values);
    }

    private static Set<String> tables(String... values) {
        return normalizeSet(values);
    }

    private static Set<String> normalizeSet(String... values) {
        return Stream.of(values)
            .map(DomainOwnershipCatalog::normalize)
            .filter(value -> !value.isBlank())
            .collect(java.util.stream.Collectors.toUnmodifiableSet());
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }
}
