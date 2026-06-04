package com.medkernel.migration;

import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 五方言迁移静态合同门禁。
 *
 * <p>达梦、金仓没有稳定公开容器可供普通 CI 执行，因此这里把版本序列、表族、索引、
 * 业务约束和关键字段作为全部五方言的最低合同；可启动的数据库仍由 Flyway smoke 执行解析验证。
 */
class MigrationBaselineContractTest {

    private static final List<String> DIALECTS = List.of("h2", "postgres", "oracle", "dm", "kingbase");
    private static final List<String> EXPECTED_MIGRATIONS = List.of(
        "V1__init.sql",
        "V2__org_audit_baseline.sql",
        "V3__knowledge_asset_baseline.sql",
        "V4__terminology_mapping_baseline.sql",
        "V5__audit_chain_baseline.sql",
        "V6__security_permission_baseline.sql",
        "V7__clinical_context_baseline.sql",
        "V8__observability_baseline.sql",
        "V9__audit_event_outcome.sql",
        "V10__clinical_event_api.sql",
        "V11__rule_engine_api.sql",
        "V12__pathway_engine_api.sql",
        "V13__recommendation_cdss_api.sql",
        "V14__evaluation_quality_api.sql",
        "V15__package_release_baseline.sql",
        "V16__followup_engine_api.sql",
        "V17__embed_engine_api.sql",
        "V18__model_gateway_api.sql",
        "V19__large_list_api.sql",
        "V20__integration_engine_api.sql",
        "V21__audit_evidence_api.sql",
        "V22__engine_remediation.sql",
        "V23__tenant_pilot_baseline.sql",
        "V24__mpi_patient_registry.sql",
        "V25__security_user_role_seed.sql",
        "V26__integration_adapter_health_states.sql",
        "V27__platform_credential.sql",
        "V28__emergency_permission_grant.sql",
        "V29__sys_idempotency.sql",
        "V30__audit_event_spine_contract.sql",
        "V31__configuration_center.sql",
        "V32__source_fragment_content_hash.sql",
        "V33__package_sync_not_synced_status.sql",
        "V34__experience_foundation_persistence.sql",
        "V35__experience_user_preference.sql",
        "V36__bootstrap_init_token.sql",
        "V37__large_list_audit_event_indexes.sql",
        "V38__standard_clinical_model.sql",
        "V39__clinical_event_context_scope.sql",
        "V40__projection_sync_baseline.sql",
        "V41__runtime_task_framework.sql",
        "V42__runtime_task_retry_dead_letter.sql",
        "V43__menu_permission_granularity.sql",
        "V44__system_superadmin_seed.sql",
        "V45__credential_login_attempt.sql",
        "V46__auth_mfa_sm3_reset.sql",
        "V47__workbench_demo_validation_permission.sql",
        "V48__context_snapshot_standard_contract.sql",
        "V49__knowledge_citation_anchor_offsets.sql",
        "V50__knowledge_trust_grading.sql",
        "V51__knowledge_projection_targets.sql",
        "V52__knowledge_candidate_workflow.sql",
        "V53__terminology_high_risk_rules.sql",
        "V54__asset_version_framework.sql",
        "V55__version_inheritance_override.sql",
        "V56__version_release_replay.sql",
        "V57__knowledge_effective_scope_unique.sql",
        "V58__knowledge_invalidation_affected_tasks.sql",
        "V59__integration_adapter_tenant_unique.sql",
        "V60__integration_message_tenant_unique.sql",
        "V61__integration_webhook_tenant_unique.sql",
        "V62__integration_message_not_connected_status.sql",
        "V63__fhir_resource_mapping.sql",
        "V64__mpi_merge_review_data_quality_report.sql",
        "V65__pilot_package_template.sql",
        "V66__integration_business_service_package.sql",
        "V67__clinical_event_api_contract.sql",
        "V68__recommendation_cdss_contract.sql",
        "V69__followup_api09_contract.sql",
        "V70__embed_api11_contract.sql",
        "V71__followup_controlled_plan_clock.sql",
        "V72__cdss_risk_matrix.sql",
        "V73__clinical_redline.sql",
        "V74__context_field_catalog.sql",
        "V75__clinical_redline_silent_trial.sql",
        "V76__recommendation_source_redline_type.sql",
        "V77__workflow_collaboration.sql",
        "V78__diagnosis_knowledge_asset.sql",
        "V79__version_propagation_and_override_policy.sql",
        "V80__workflow_transfer_reason.sql",
        "V81__workflow_sync_event_notifications.sql",
        "V82__workflow_organization_scope.sql"
    );
    private static final Set<String> REQUIRED_TABLES = Set.of(
        "medkernel_meta", "org_unit", "org_closure", "audit_event", "source_document", "source_version",
        "source_fragment", "knowledge_identity", "knowledge_asset_version", "citation",
        "knowledge_supersession", "knowledge_export_job", "mk_knowledge_invalidation", "mk_knowledge_affected_case_task",
        "mk_knowledge_candidate_classification", "mk_knowledge_review_assignment",
        "standard_term", "local_term", "mk_term_high_risk_rule",
        "term_mapping", "mapping_candidate", "mapping_conflict", "term_mapping_package",
        "term_mapping_package_item", "term_mapping_package_release", "audit_chain_head",
        "sys_role", "sys_permission", "role_permission", "user_role_assignment",
        "context_snapshot", "canonical_resource", "clinical_event", "context_idempotency_key",
        "mk_obs_state_transition", "mk_obs_payload_store", "clinical_event_payload", "clinical_event_outbox",
        "rule_definition", "rule_version", "rule_test_case", "rule_execution_log",
        "specialty_package", "specialty_profile", "pathway_template", "pathway_node",
        "pathway_edge", "patient_pathway", "pathway_variance", "clinical_clock",
        "specialty_metric_binding", "recommendation_trigger", "recommendation_card",
        "recommendation_source", "recommendation_feedback", "recommendation_fatigue_signal",
        "mk_engine_cdss_risk_matrix", "mk_engine_clinical_redline", "mk_engine_clinical_redline_trial",
        "evaluation_indicator", "evaluation_run", "evaluation_result", "quality_finding",
        "rectification_task", "rectification_review", "evaluation_idempotency_key",
        "knowledge_package", "package_item", "release_plan", "sync_target", "sync_log",
        "mk_pkg_pilot_package_template", "mk_pkg_pilot_template_item",
        "followup_plan", "followup_task", "followup_questionnaire", "followup_event",
        "mk_engine_workflow_todo", "mk_engine_notification",
        "embed_launch_token", "embed_origin_whitelist",
        "model_capability_task", "model_capability_policy",
        "mk_experience_saved_view", "mk_experience_export_task", "mk_experience_user_pref",
        "integration_adapter", "integration_webhook_config", "integration_message_log",
        "mk_integration_onboarding", "mk_integration_regional_source",
        "evidence_snapshot",
        "tenant_branding", "tenant_success_plan",
        "mpi_patient", "mk_mpi_merge_review", "mk_integration_data_quality_report",
        "platform_credential", "sys_login_attempt", "sys_password_reset_token",
        "emergency_permission_grant",
        "sys_idempotency",
        "sys_task", "sys_task_dead_letter",
        "mk_security_bootstrap_init_token",
        "mk_config_item", "mk_config_history",
        "mk_clinical_patient", "mk_clinical_encounter", "mk_clinical_condition",
        "mk_clinical_observation", "mk_clinical_medication", "mk_clinical_procedure",
        "mk_clinical_diagnostic_report", "mk_clinical_document",
        "mk_clinical_nursing_assessment", "mk_clinical_care_plan",
        "mk_clinical_follow_up", "mk_clinical_claim",
        "mk_projection_sync", "mk_projection_snapshot",
        "mk_version_asset_version", "mk_version_inheritance_override",
        "mk_version_release_plan", "mk_version_activation_transaction", "mk_version_replay_binding",
        "mk_fhir_resource_mapping", "mk_fhir_mapping_rule",
        "mk_context_field_catalog",
        "mk_diagnosis_criterion", "mk_diagnosis_differential", "mk_diagnosis_care_pointer",
        "mk_diagnosis_test_case", "mk_diagnosis_confidence_policy"
    );
    private static final Set<String> REQUIRED_INDEXES = Set.of(
        "idx_org_unit_parent", "idx_org_unit_tenant_lv", "idx_org_unit_path",
        "idx_org_closure_ancestor", "idx_org_closure_descendant",
        "idx_audit_event_resource",
        "idx_audit_event_actor", "idx_audit_event_tenant", "idx_audit_event_trace",
        "idx_audit_event_large_cursor", "idx_audit_event_large_action",
        "idx_audit_event_large_resource", "idx_audit_event_large_actor",
        "idx_source_document_tenant_type", "idx_source_document_tenant_auth",
        "idx_source_version_tenant_doc", "idx_source_fragment_tenant_ver",
        "idx_knowledge_identity_tenant_domain", "idx_knowledge_identity_specialty",
        "idx_knowledge_identity_updated", "idx_knowledge_av_identity_status",
        "idx_knowledge_av_effective_scope",
        "idx_knowledge_av_tenant_status", "idx_knowledge_av_tenant_updated",
        "idx_knowledge_av_content_hash", "idx_knowledge_av_authority", "idx_citation_tenant_av", "idx_citation_fragment",
        "idx_supersession_tenant_identity", "idx_supersession_old", "idx_supersession_new",
        "idx_mk_knowledge_invalidation_identity", "idx_mk_knowledge_invalidation_status",
        "idx_mk_knowledge_affected_task_status", "idx_mk_knowledge_affected_task_version",
        "idx_export_job_tenant_status", "idx_export_job_tenant_created",
        "idx_candidate_classification_identity", "idx_candidate_classification_status",
        "idx_candidate_classification_candidate", "idx_review_assignment_identity",
        "idx_review_assignment_status", "idx_review_assignment_candidate",
        "idx_standard_term_tenant_category", "idx_standard_term_tenant_updated",
        "idx_mk_term_high_risk_rule_tenant_status", "idx_mk_term_high_risk_rule_category",
        "idx_local_term_tenant_source", "idx_local_term_department",
        "idx_term_mapping_tenant_status", "idx_term_mapping_local_standard",
        "idx_mapping_candidate_tenant_status", "idx_mapping_conflict_tenant_status",
        "idx_term_pkg_tenant_status", "idx_term_pkg_scope", "idx_term_pkg_item_package",
        "idx_term_pkg_release_package", "idx_sys_role_tenant_active", "idx_sys_permission_dimension",
        "idx_role_permission_tenant_role",
        "idx_user_role_assignment_user",
        "idx_context_snapshot_tenant_patient", "idx_context_snapshot_tenant_enc",
        "idx_context_snapshot_status", "idx_context_snapshot_tenant_request",
        "idx_context_snapshot_org_path", "idx_context_snapshot_package_version",
        "idx_canonical_resource_snapshot",
        "idx_canonical_resource_tenant_type", "idx_clinical_event_tenant_received",
        "idx_clinical_event_snapshot", "idx_context_idempotency_expires",
        "uk_clinical_event_idempotency", "idx_clinical_event_trigger", "idx_clinical_event_callback",
        "idx_most_entity", "idx_most_tenant_time", "idx_most_trace", "idx_most_failed",
        "idx_mops_trace", "idx_mops_entity", "idx_mops_tenant_time",
        "idx_canonical_resource_trace",
        "idx_audit_event_outcome",
        "idx_cep_tenant_time", "idx_outbox_pending", "idx_outbox_tenant",
        "idx_clinical_event_patient", "idx_clinical_event_encounter",
        "idx_rule_definition_tenant_status", "idx_rule_definition_type_risk",
        "idx_rule_version_rule_status", "idx_rule_test_case_version_type",
        "idx_rule_execution_tenant_time", "idx_rule_execution_rule_time",
        "idx_rule_execution_trigger",
        "idx_specialty_package_tenant_status", "idx_specialty_package_disease",
        "idx_specialty_profile_package", "idx_pathway_template_tenant_status",
        "idx_pathway_template_package", "idx_pathway_template_disease",
        "idx_pathway_node_template_order", "idx_pathway_edge_template_from",
        "idx_pathway_edge_template_to", "idx_patient_pathway_patient",
        "idx_patient_pathway_template_status", "idx_pathway_variance_pathway_time",
        "idx_clinical_clock_pathway", "idx_clinical_clock_due",
        "idx_specialty_metric_package", "idx_specialty_metric_template",
        "idx_rec_trigger_tenant_time", "idx_rec_trigger_patient", "idx_rec_trigger_status",
        "idx_rec_trigger_scenario", "idx_rec_card_trigger", "idx_rec_card_tenant_status",
        "idx_rec_card_risk", "idx_rec_card_fatigue", "idx_rec_source_card",
        "idx_rec_feedback_card_time", "uk_rec_feedback_idempotency",
        "idx_rec_fatigue_card", "idx_rec_fatigue_key", "idx_rec_fatigue_tenant_time",
        "idx_cdss_risk_matrix_active", "idx_cdss_risk_matrix_version", "idx_rec_card_risk_matrix",
        "idx_clinical_redline_active", "idx_clinical_redline_category", "idx_clinical_redline_source",
        "idx_clinical_redline_trial_redline", "idx_clinical_redline_trial_status",
        "idx_eval_indicator_tenant_status", "idx_eval_indicator_code_status",
        "idx_eval_run_tenant_time", "idx_eval_run_context",
        "idx_eval_result_run", "idx_eval_result_indicator",
        "idx_quality_finding_status", "idx_quality_finding_department",
        "idx_rect_task_finding", "idx_rect_task_department_status",
        "idx_rect_review_finding", "idx_eval_idempotency_resource",
        "idx_knowledge_pkg_tenant_status", "idx_package_item_pkg",
        "idx_release_plan_pkg", "idx_sync_target_tenant", "idx_sync_log_plan",
        "idx_pkg_tpl_tenant_status", "idx_pkg_tpli_template",
        "idx_followup_plan_tenant_patient", "idx_followup_plan_status", "idx_followup_plan_fact",
        "uk_followup_plan_idempotency",
        "idx_followup_task_tenant_plan", "idx_followup_task_due_date",
        "uk_followup_task_idempotency", "idx_followup_task_status_due", "idx_followup_task_clock",
        "idx_followup_questionnaire_task", "idx_followup_questionnaire_plan",
        "uk_followup_questionnaire_idempotency", "idx_followup_event_plan",
        "idx_followup_event_type", "uk_followup_event_idempotency",
        "idx_workflow_todo_tenant_status_due", "idx_workflow_todo_assignee_status",
        "idx_workflow_todo_org_scope",
        "idx_notification_tenant_status_created", "idx_notification_recipient_status",
        "idx_notification_org_scope",
        "idx_embed_token_tenant", "idx_embed_token_status_expired", "idx_embed_token_hook",
        "idx_model_task_tenant",
        "idx_saved_view_user_page", "idx_saved_view_default", "idx_user_pref_user_key",
        "idx_export_task_status", "idx_export_task_resource",
        "idx_integ_adapter_tenant", "idx_integ_webhook_tenant", "idx_integ_msg_tenant", "idx_integ_msg_trace",
        "idx_integ_onb_tenant_status", "idx_integ_onb_adapter",
        "idx_integ_regional_tenant_trust", "idx_integ_regional_org",
        "idx_evd_tenant", "idx_evd_trace", "idx_mpi_patient_tenant_status",
        "idx_mpi_mrv_tenant_status", "idx_mpi_mrv_source", "idx_dqr_tenant_generated",
        "idx_platform_credential_login", "idx_sys_login_attempt_locked_until",
        "idx_pwd_reset_token_lookup", "idx_pwd_reset_token_expiry",
        "idx_emergency_permission_active", "idx_emergency_permission_expiry",
        "idx_sys_idempotency_expiry",
        "idx_sys_task_status_ts", "idx_sys_task_mode_ts", "idx_sys_task_org_ts",
        "idx_sys_task_retry_ts", "idx_sys_task_dead_letter",
        "idx_sys_task_dead_tenant_ts", "idx_sys_task_dead_task",
        "idx_bootstrap_init_token_expires",
        "idx_audit_event_org_path",
        "idx_audit_event_env",
        "idx_config_item_tenant_key", "idx_config_history_tenant_key",
        "idx_mk_clinical_patient_org_path",
        "idx_mk_clinical_encounter_patient", "idx_mk_clinical_encounter_org_path",
        "idx_mk_clinical_condition_patient", "idx_mk_clinical_condition_org_path",
        "idx_mk_clinical_condition_code", "idx_mk_clinical_observation_patient",
        "idx_mk_clinical_observation_org_path", "idx_mk_clinical_observation_code",
        "idx_mk_clinical_medication_patient", "idx_mk_clinical_medication_org_path",
        "idx_mk_clinical_medication_code", "idx_mk_clinical_procedure_patient",
        "idx_mk_clinical_procedure_org_path", "idx_mk_clinical_procedure_code",
        "idx_mk_clinical_diagnostic_report_patient", "idx_mk_clinical_diagnostic_report_org_path",
        "idx_mk_clinical_document_patient", "idx_mk_clinical_document_org_path",
        "idx_mk_clinical_nursing_assessment_patient", "idx_mk_clinical_nursing_assessment_org_path",
        "idx_mk_clinical_care_plan_patient", "idx_mk_clinical_care_plan_org_path",
        "idx_mk_clinical_follow_up_patient", "idx_mk_clinical_follow_up_org_path",
        "idx_mk_clinical_claim_patient", "idx_mk_clinical_claim_org_path",
        "idx_mk_projection_sync_tenant_target_ts", "idx_mk_projection_sync_tenant_status",
        "idx_mk_projection_snapshot_tenant_target",
        "idx_mk_version_asset_version_tenant_status", "idx_mk_version_asset_version_identity",
        "idx_mk_version_asset_version_active_scope",
        "idx_mk_version_inheritance_override_scope", "idx_mk_version_inheritance_override_version",
        "idx_mk_version_release_plan_asset", "idx_mk_version_release_plan_version",
        "idx_mk_version_activation_transaction_asset", "idx_mk_version_replay_binding_version",
        "idx_mk_fhir_res_map_tenant", "idx_mk_fhir_res_map_canon", "idx_mk_fhir_rule_tenant",
        "idx_mk_ctx_field_catalog_tenant",
        "idx_mk_diagnosis_criterion_finding", "idx_mk_diagnosis_criterion_version",
        "idx_mk_diagnosis_differential_version", "idx_mk_diagnosis_pointer_version",
        "idx_mk_diagnosis_testcase_version", "idx_mk_diagnosis_confpolicy_tenant"
    );
    private static final Set<String> COMMON_CONSTRAINTS = Set.of(
        "uk_org_unit_tenant_code", "ck_org_unit_level", "ck_org_unit_status",
        "pk_org_closure", "fk_org_closure_ancestor", "fk_org_closure_descendant",
        "ck_org_closure_depth",
        "uk_audit_event_event_id", "ck_audit_event_status",
        "uk_source_document_tenant_code", "ck_source_document_type", "ck_source_document_authority",
        "uk_source_version_doc_no", "uk_source_version_doc_hash", "uk_source_fragment_version_anchor", "uk_source_fragment_version_hash",
        "uk_knowledge_identity_tenant_code", "ck_knowledge_identity_domain", "ck_knowledge_identity_status",
        "uk_knowledge_asset_version", "uk_knowledge_asset_version_active_scope",
        "ck_knowledge_asset_version_status", "ck_knowledge_asset_version_risk",
        "ck_knowledge_asset_version_authority", "ck_knowledge_asset_grade_quality", "ck_knowledge_asset_grade_strength",
        "uk_citation_av_fragment", "ck_citation_relation", "ck_citation_anchor_offsets", "ck_knowledge_supersession_type",
        "uk_mk_knowledge_invalidation_key", "ck_mk_knowledge_invalidation_type",
        "ck_mk_knowledge_invalidation_status", "ck_mk_knowledge_invalidation_risk",
        "ck_mk_knowledge_invalidation_review", "uk_mk_knowledge_affected_task_key",
        "ck_mk_knowledge_affected_task_type", "ck_mk_knowledge_affected_task_status",
        "ck_mk_knowledge_affected_target_type",
        "uk_knowledge_export_job_code", "ck_knowledge_export_job_type", "ck_knowledge_export_job_status",
        "ck_knowledge_candidate_classification", "ck_knowledge_candidate_review_status",
        "ck_review_assignment_review_status", "ck_review_assignment_decision",
        "uk_standard_term_code", "ck_standard_term_category", "ck_standard_term_status",
        "uk_mk_term_high_risk_rule_code", "ck_mk_term_high_risk_rule_type", "ck_mk_term_high_risk_rule_category", "ck_mk_term_high_risk_rule_status",
        "uk_local_term_code", "ck_local_term_category", "ck_local_term_status",
        "ck_term_mapping_status", "ck_term_mapping_risk",
        "ck_mapping_candidate_status", "ck_mapping_candidate_source", "ck_mapping_candidate_risk",
        "ck_mapping_conflict_type", "ck_mapping_conflict_status", "ck_mapping_conflict_risk",
        "uk_term_mapping_package", "ck_term_mapping_package_status",
        "ck_term_pkg_release_event", "ck_term_pkg_release_mode",
        "uk_sys_role_tenant_code", "uk_sys_permission_code",
        "ck_sys_role_builtin", "ck_sys_role_active", "ck_sys_permission_dimension", "ck_sys_permission_risk",
        "ck_sys_permission_active", "uk_role_permission", "ck_role_permission_effect",
        "uk_user_role_assignment", "ck_user_role_assignment_active",
        "uk_context_snapshot_id", "ck_context_snapshot_status", "ck_context_snapshot_quality",
        "uk_canonical_resource_id", "ck_canonical_resource_type", "ck_canonical_resource_quality",
        "uk_clinical_event_id", "ck_clinical_event_type", "ck_clinical_event_status",
        "ck_clinical_event_trigger_point",
        "uk_context_idempotency_tenant_key",
        "ck_most_error_class",
        "uk_mops_payload_id", "ck_mops_storage_type",
        "ck_audit_event_outcome",
        "uk_event_payload", "ck_storage_type",
        "uk_outbox_event_id", "ck_outbox_status",
        "uk_rule_definition_tenant_code", "ck_rule_definition_type",
        "ck_rule_definition_mode", "ck_rule_definition_risk", "ck_rule_definition_status",
        "uk_rule_version_rule_no", "ck_rule_version_status",
        "uk_rule_test_case_id", "ck_rule_test_case_type", "ck_rule_test_case_status",
        "uk_rule_execution_id", "ck_rule_execution_status", "ck_rule_execution_severity",
        "uk_specialty_package_tenant_code", "ck_specialty_package_status",
        "uk_specialty_profile_package_code", "uk_pathway_template_tenant_code",
        "ck_pathway_template_level", "ck_pathway_template_status",
        "uk_pathway_node_template_code", "ck_pathway_node_type", "ck_pathway_node_terminal",
        "uk_pathway_edge_template_code", "ck_pathway_edge_type",
        "uk_patient_pathway_id", "ck_patient_pathway_status",
        "uk_pathway_variance_id", "ck_pathway_variance_type",
        "uk_clinical_clock_id", "ck_clinical_clock_status",
        "uk_specialty_metric_binding", "ck_specialty_metric_required",
        "uk_rec_trigger_id", "uk_rec_trigger_tenant_code", "ck_rec_trigger_status",
        "uk_rec_card_id", "uk_rec_card_trigger_code", "ck_rec_card_type",
        "ck_rec_card_risk", "ck_rec_card_interrupt", "ck_rec_card_status",
        "ck_rec_card_physician_confirmation", "ck_rec_card_ai_generated",
        "ck_rec_card_automation", "ck_rec_card_review", "ck_rec_card_auto_execution",
        "uk_cdss_risk_matrix_id", "uk_cdss_risk_matrix_scope_version",
        "ck_cdss_risk_matrix_severity", "ck_cdss_risk_matrix_auto", "ck_cdss_risk_matrix_risk",
        "ck_cdss_risk_matrix_review", "ck_cdss_risk_matrix_status", "ck_cdss_risk_matrix_auto_exec",
        "uk_clinical_redline_id", "uk_clinical_redline_version", "uk_clinical_redline_active_scope",
        "ck_clinical_redline_category", "ck_clinical_redline_status", "ck_clinical_redline_hazard",
        "ck_clinical_redline_review", "ck_clinical_redline_override",
        "uk_clinical_redline_trial_id", "ck_clinical_redline_trial_status",
        "ck_clinical_redline_trial_gate", "ck_clinical_redline_trial_counts",
        "uk_rec_source_id", "ck_rec_source_type", "uk_rec_feedback_id",
        "ck_rec_feedback_type", "uk_rec_fatigue_id", "ck_rec_fatigue_signal",
        "uk_eval_indicator_id", "uk_eval_indicator_tenant_version",
        "ck_eval_indicator_subject", "ck_eval_indicator_status",
        "uk_eval_run_id", "uk_eval_run_tenant_code", "ck_eval_run_type", "ck_eval_run_status",
        "uk_eval_result_id", "ck_eval_result_subject", "ck_eval_result_level",
        "uk_quality_finding_id", "uk_quality_finding_result_code",
        "ck_quality_finding_severity", "ck_quality_finding_status",
        "uk_rect_task_id", "uk_rect_task_finding", "ck_rect_task_status",
        "uk_rect_review_id", "ck_rect_review_decision",
        "uk_eval_idempotency_operation_key", "ck_eval_idempotency_operation",
        "ck_eval_idempotency_finding_status", "ck_eval_idempotency_task_status",
        "uk_knowledge_package_id", "uk_knowledge_package_tenant_version", "ck_knowledge_package_status",
        "uk_package_item_id", "uk_package_item_tenant_asset", "ck_package_item_asset_type",
        "uk_release_plan_id", "ck_release_plan_strategy", "ck_release_plan_scope_type", "ck_release_plan_status",
        "uk_sync_target_id", "ck_sync_target_type", "ck_sync_target_status",
        "uk_sync_log_id", "ck_sync_log_status",
        "uk_pkg_tpl_tenant_code", "ck_pkg_tpl_status",
        "fk_pkg_tpli_template", "uk_pkg_tpli_asset", "ck_pkg_tpli_type",
        "ck_pkg_tpli_required", "ck_pkg_tpli_sort",
        "uk_followup_plan_id", "uk_followup_task_id",
        "uk_followup_questionnaire_id", "uk_followup_event_id",
        "uk_workflow_todo_id", "uk_workflow_todo_source",
        "ck_workflow_todo_source_type", "ck_workflow_todo_priority", "ck_workflow_todo_status",
        "uk_notification_id", "uk_notification_dedupe",
        "ck_notification_source_type", "ck_notification_level", "ck_notification_status",
        "uk_embed_launch_token", "uk_embed_origin_tenant",
        "uk_model_task_id", "uk_model_policy_tenant",
        "pk_saved_view", "uk_saved_view_user_name", "ck_saved_view_default", "ck_saved_view_status",
        "pk_user_pref", "uk_user_pref_user_key", "ck_user_pref_status",
        "pk_export_task", "uk_export_task_idempotency", "ck_export_task_scope", "ck_export_task_status",
        "uk_integration_adapter", "uk_integration_webhook", "uk_integration_message",
        "ck_integration_adapter_status", "ck_integration_adapter_health",
        "ck_integration_webhook_status", "ck_integration_message_dir", "ck_integration_message_status",
        "uk_integ_onboarding_tenant_id", "ck_integ_onboarding_mode",
        "ck_integ_onboarding_status", "ck_integ_onboarding_fhir",
        "uk_integ_regional_source_id", "ck_integ_regional_trust", "ck_integ_regional_status",
        "uk_evidence_snapshot",
        "uk_tenant_branding", "uk_tenant_success_plan",
        "uk_mpi_patient_id", "uk_mpi_merge_review_pair", "ck_mpi_merge_review_risk",
        "ck_mpi_merge_review_status", "ck_dqr_required_nonneg", "ck_dqr_adapter_nonneg",
        "ck_dqr_status_nonneg", "ck_dqr_rates",
        "uk_platform_credential_id", "uk_platform_credential_username",
        "ck_platform_credential_status", "ck_platform_credential_mustchg",
        "uk_sys_login_attempt_id", "uk_sys_login_attempt_tenant_user", "ck_sys_login_attempt_failed_count",
        "uk_password_reset_token_id", "ck_password_reset_token_expiry",
        "ck_emergency_permission_code", "ck_emergency_permission_active",
        "uk_sys_idempotency_tenant_key", "ck_sys_idempotency_status",
        "uk_sys_task_tenant_task", "ck_sys_task_mode", "ck_sys_task_status",
        "uk_sys_task_dead_letter", "uk_sys_task_dead_task", "ck_sys_task_dead_mode",
        "uk_bootstrap_init_token_id", "uk_bootstrap_init_token_hash", "ck_bootstrap_init_token_status",
        "uk_audit_event_dedupe",
        "pk_config_item", "uk_config_item_tenant_key", "ck_config_item_value_type",
        "ck_config_item_risk", "ck_config_item_source", "ck_config_item_protected",
        "ck_config_item_active", "pk_config_history", "ck_config_history_change_type",
        "uk_mk_clinical_patient_source", "uk_mk_clinical_encounter_source",
        "uk_mk_clinical_condition_source", "uk_mk_clinical_observation_source",
        "uk_mk_clinical_medication_source", "uk_mk_clinical_procedure_source",
        "uk_mk_clinical_diagnostic_report_source", "uk_mk_clinical_document_source",
        "uk_mk_clinical_nursing_assessment_source", "uk_mk_clinical_care_plan_source",
        "uk_mk_clinical_follow_up_source", "uk_mk_clinical_claim_source",
        "uk_mk_projection_sync_tenant_sync", "ck_mk_projection_sync_target",
        "ck_mk_projection_sync_status", "uk_mk_projection_snapshot_fact",
        "ck_mk_projection_snapshot_target", "ck_mk_projection_snapshot_kind",
        "uk_mk_version_asset_version_id", "uk_mk_version_asset_version_no",
        "uk_mk_version_asset_version_active", "ck_mk_version_asset_version_type",
        "ck_mk_version_asset_version_status", "ck_mk_version_asset_version_hash",
        "ck_mk_version_asset_safety_policy", "ck_mk_version_asset_override_policy",
        "uk_mk_version_inheritance_override_id", "uk_mk_version_inheritance_override_active",
        "ck_mk_version_inheritance_override_type", "ck_mk_version_inheritance_override_mode",
        "ck_mk_version_inheritance_override_propagation",
        "uk_mk_version_release_plan_id", "ck_mk_version_release_plan_type",
        "ck_mk_version_release_plan_scope", "ck_mk_version_release_plan_status",
        "uk_mk_version_activation_transaction_id", "uk_mk_version_activation_transaction_idem",
        "ck_mk_version_activation_transaction_type", "ck_mk_version_activation_transaction_action",
        "uk_mk_version_replay_binding_id", "uk_mk_version_replay_binding_event",
        "ck_mk_version_replay_binding_type", "ck_mk_version_replay_binding_hash",
        "uk_mk_fhir_res_map_fhir", "uk_mk_fhir_res_map_canon",
        "ck_mk_fhir_res_map_ver", "ck_mk_fhir_res_map_status",
        "ck_mk_fhir_res_map_rate", "ck_mk_fhir_res_map_missing",
        "uk_mk_fhir_rule_code", "ck_mk_fhir_rule_ver",
        "ck_mk_fhir_rule_status", "ck_mk_fhir_rule_version",
        "uk_mk_ctx_field_catalog_tenant_path", "ck_mk_ctx_field_catalog_data_type",
        "ck_mk_ctx_field_catalog_status",
        "ck_mk_diagnosis_criterion_dir", "ck_mk_diagnosis_criterion_weight",
        "ck_mk_diagnosis_pointer_type", "ck_mk_diagnosis_testcase_conf",
        "uk_mk_diagnosis_testcase", "uk_mk_diagnosis_confpolicy"
    );
    private static final Set<String> TENANT_TABLES = Set.of(
        "org_unit", "org_closure", "audit_event", "source_document", "source_version", "source_fragment",
        "knowledge_identity", "knowledge_asset_version", "citation", "knowledge_supersession",
        "knowledge_export_job", "mk_knowledge_candidate_classification", "mk_knowledge_review_assignment",
        "standard_term", "local_term", "mk_term_high_risk_rule", "term_mapping", "mapping_candidate",
        "mapping_conflict", "term_mapping_package", "term_mapping_package_item",
        "term_mapping_package_release", "audit_chain_head", "sys_role", "role_permission", "user_role_assignment",
        "context_snapshot", "canonical_resource", "clinical_event", "context_idempotency_key",
        "mk_obs_state_transition", "mk_obs_payload_store", "clinical_event_payload", "clinical_event_outbox",
        "rule_definition", "rule_version", "rule_test_case", "rule_execution_log",
        "specialty_package", "specialty_profile", "pathway_template", "pathway_node",
        "pathway_edge", "patient_pathway", "pathway_variance", "clinical_clock",
        "specialty_metric_binding", "recommendation_trigger", "recommendation_card",
        "recommendation_source", "recommendation_feedback", "recommendation_fatigue_signal",
        "mk_engine_cdss_risk_matrix", "mk_engine_clinical_redline",
        "evaluation_indicator", "evaluation_run", "evaluation_result", "quality_finding",
        "rectification_task", "rectification_review", "evaluation_idempotency_key",
        "knowledge_package", "package_item", "release_plan", "sync_target", "sync_log",
        "followup_plan", "followup_task", "followup_questionnaire", "followup_event",
        "mk_engine_workflow_todo", "mk_engine_notification",
        "model_capability_task", "model_capability_policy",
        "mk_experience_saved_view", "mk_experience_export_task", "mk_experience_user_pref",
        "integration_adapter", "integration_webhook_config", "integration_message_log",
        "mk_integration_onboarding", "mk_integration_regional_source",
        "evidence_snapshot",
        "tenant_branding", "tenant_success_plan",
        "mpi_patient", "mk_mpi_merge_review", "mk_integration_data_quality_report",
        "platform_credential", "sys_login_attempt", "sys_password_reset_token",
        "emergency_permission_grant",
        "sys_idempotency",
        "sys_task", "sys_task_dead_letter",
        "mk_config_item", "mk_config_history",
        "mk_clinical_patient", "mk_clinical_encounter", "mk_clinical_condition",
        "mk_clinical_observation", "mk_clinical_medication", "mk_clinical_procedure",
        "mk_clinical_diagnostic_report", "mk_clinical_document",
        "mk_clinical_nursing_assessment", "mk_clinical_care_plan",
        "mk_clinical_follow_up", "mk_clinical_claim",
        "mk_projection_sync", "mk_projection_snapshot",
        "mk_version_asset_version", "mk_version_inheritance_override",
        "mk_version_release_plan", "mk_version_activation_transaction", "mk_version_replay_binding",
        "mk_fhir_resource_mapping", "mk_fhir_mapping_rule"
    );
    private static final Set<String> MUTABLE_AUDITED_TABLES = Set.of(
        "org_unit", "source_document", "knowledge_identity", "knowledge_asset_version",
        "mk_knowledge_candidate_classification", "mk_knowledge_review_assignment",
        "standard_term", "local_term", "mk_term_high_risk_rule", "term_mapping", "mapping_candidate", "mapping_conflict",
        "term_mapping_package", "sys_role", "sys_permission", "role_permission", "user_role_assignment",
        "rule_definition", "rule_version", "rule_test_case",
        "specialty_package", "specialty_profile", "pathway_template", "pathway_node",
        "pathway_edge", "patient_pathway", "pathway_variance", "clinical_clock",
        "specialty_metric_binding", "recommendation_trigger", "recommendation_card",
        "recommendation_source", "recommendation_feedback", "recommendation_fatigue_signal",
        "mk_engine_cdss_risk_matrix", "mk_engine_clinical_redline",
        "evaluation_indicator", "evaluation_run", "evaluation_result", "quality_finding",
        "rectification_task", "rectification_review",
        "knowledge_package", "package_item", "release_plan", "sync_target", "sync_log",
        "followup_plan", "followup_task", "followup_questionnaire", "followup_event",
        "mk_engine_workflow_todo", "mk_engine_notification",
        "embed_launch_token", "embed_origin_whitelist",
        "model_capability_task", "model_capability_policy",
        "mk_experience_saved_view", "mk_experience_export_task", "mk_experience_user_pref",
        "integration_adapter", "integration_webhook_config", "integration_message_log",
        "mk_integration_onboarding", "mk_integration_regional_source",
        "evidence_snapshot",
        "tenant_branding", "tenant_success_plan",
        "mpi_patient", "mk_mpi_merge_review",
        "platform_credential", "sys_login_attempt", "sys_password_reset_token",
        "emergency_permission_grant",
        "sys_task", "sys_task_dead_letter",
        "mk_config_item",
        "mk_clinical_patient", "mk_clinical_encounter", "mk_clinical_condition",
        "mk_clinical_observation", "mk_clinical_medication", "mk_clinical_procedure",
        "mk_clinical_diagnostic_report", "mk_clinical_document",
        "mk_clinical_nursing_assessment", "mk_clinical_care_plan",
        "mk_clinical_follow_up", "mk_clinical_claim",
        "mk_version_asset_version", "mk_version_inheritance_override",
        "mk_version_release_plan", "mk_version_activation_transaction", "mk_version_replay_binding",
        "mk_fhir_resource_mapping", "mk_fhir_mapping_rule"
    );
    private static final Map<String, Set<String>> TECHNICAL_AUDIT_FIELDS = Map.ofEntries(
        Map.entry("audit_event", Set.of("occurred_at", "actor_user_id", "created_at")),
        Map.entry("mk_obs_state_transition", Set.of("occurred_at", "actor", "created_at", "created_by")),
        Map.entry("mk_obs_payload_store", Set.of("created_at", "created_by", "deleted_at", "deleted_by")),
        Map.entry("knowledge_supersession", Set.of("transitioned_at", "transitioned_by")),
        Map.entry("knowledge_export_job", Set.of("requested_by", "created_at", "started_at", "completed_at", "expires_at")),
        Map.entry("term_mapping_package_release", Set.of("created_at", "created_by")),
        Map.entry("audit_chain_head", Set.of("last_signature", "updated_at")),
        Map.entry("rule_execution_log", Set.of("actor_user_id", "executed_at", "created_at")),
        Map.entry("specialty_package", Set.of("published_at", "published_by")),
        Map.entry("patient_pathway", Set.of("entered_at", "completed_at", "exited_at")),
        Map.entry("sys_task", Set.of("started_at", "finished_at", "trace_id")),
        Map.entry("sys_task_dead_letter", Set.of("trace_id", "replayed_at")),
        Map.entry("sys_login_attempt", Set.of("trace_id")),
        Map.entry("sys_password_reset_token", Set.of("trace_id")),
        Map.entry("mk_projection_sync", Set.of("started_at", "finished_at", "requested_by", "trace_id")),
        Map.entry("mk_projection_snapshot", Set.of("source_updated_at", "synced_at", "trace_id")),
        Map.entry("mk_mpi_merge_review", Set.of("requested_at", "requested_by", "reviewed_at", "reviewed_by", "trace_id", "created_at", "updated_at")),
        Map.entry("mk_integration_data_quality_report", Set.of("generated_at", "created_at", "created_by", "trace_id")),
        Map.entry("mk_integration_onboarding", Set.of("created_at", "created_by", "updated_at", "updated_by", "trace_id")),
        Map.entry("mk_integration_regional_source", Set.of("created_at", "created_by", "updated_at", "updated_by", "trace_id"))
    );
    private static final Map<String, Set<String>> LIFECYCLE_FIELDS = Map.<String, Set<String>>ofEntries(
        Map.entry("org_unit", Set.of("status")),
        Map.entry("audit_event", Set.of("status")),
        Map.entry("source_version", Set.of("version_no")),
        Map.entry("knowledge_identity", Set.of("status")),
        Map.entry("knowledge_asset_version", Set.of("version_no", "status")),
        Map.entry("mk_knowledge_candidate_classification", Set.of("classification", "review_status", "content_hash")),
        Map.entry("mk_knowledge_review_assignment", Set.of("review_status", "decision")),
        Map.entry("knowledge_export_job", Set.of("status")),
        Map.entry("standard_term", Set.of("version_no", "status")),
        Map.entry("mk_term_high_risk_rule", Set.of("rule_type", "status")),
        Map.entry("local_term", Set.of("status")),
        Map.entry("term_mapping", Set.of("status")),
        Map.entry("mapping_candidate", Set.of("status")),
        Map.entry("mapping_conflict", Set.of("status")),
        Map.entry("term_mapping_package", Set.of("package_version", "status")),
        Map.entry("sys_role", Set.of("active_flag")),
        Map.entry("sys_permission", Set.of("dimension", "active_flag")),
        Map.entry("mk_security_bootstrap_init_token", Set.of("status")),
        Map.entry("context_snapshot", Set.of("status", "quality_status")),
        Map.entry("clinical_event", Set.of("processing_status")),
        Map.entry("clinical_event_outbox", Set.of("claim_status")),
        Map.entry("rule_definition", Set.of("status", "risk_level")),
        Map.entry("rule_version", Set.of("version_no", "status")),
        Map.entry("rule_test_case", Set.of("case_type", "last_status")),
        Map.entry("rule_execution_log", Set.of("status", "severity")),
        Map.entry("specialty_package", Set.of("package_version", "status")),
        Map.entry("pathway_template", Set.of("template_version", "status")),
        Map.entry("pathway_node", Set.of("node_type")),
        Map.entry("pathway_edge", Set.of("edge_type")),
        Map.entry("patient_pathway", Set.of("status")),
        Map.entry("pathway_variance", Set.of("variance_type")),
        Map.entry("clinical_clock", Set.of("status")),
        Map.entry("recommendation_trigger", Set.of("status")),
        Map.entry("recommendation_card", Set.of("card_type", "risk_level", "interrupt_level", "status")),
        Map.entry("mk_engine_cdss_risk_matrix", Set.of("severity_level", "automation_level", "risk_level",
            "review_requirement", "status", "matrix_version")),
        Map.entry("mk_engine_clinical_redline", Set.of("category", "status", "hazard_severity",
            "review_requirement", "redline_version")),
        Map.entry("recommendation_source", Set.of("source_type")),
        Map.entry("recommendation_feedback", Set.of("feedback_type")),
        Map.entry("recommendation_fatigue_signal", Set.of("signal_type")),
        Map.entry("evaluation_indicator", Set.of("version_no", "subject_type", "status")),
        Map.entry("evaluation_run", Set.of("run_type", "status")),
        Map.entry("evaluation_result", Set.of("subject_type", "result_level")),
        Map.entry("quality_finding", Set.of("severity", "status")),
        Map.entry("rectification_task", Set.of("status")),
        Map.entry("rectification_review", Set.of("decision")),
        Map.entry("knowledge_package", Set.of("package_version", "status")),
        Map.entry("package_item", Set.of("asset_type")),
        Map.entry("release_plan", Set.of("strategy", "scope_type", "status")),
        Map.entry("sync_target", Set.of("target_type", "status")),
        Map.entry("sync_log", Set.of("status")),
        Map.entry("followup_plan", Set.of("status")),
        Map.entry("followup_task", Set.of("status", "task_type")),
        Map.entry("followup_questionnaire", Set.of("status")),
        Map.entry("followup_event", Set.of("event_type")),
        Map.entry("mk_engine_workflow_todo", Set.of("source_type", "priority", "status")),
        Map.entry("mk_engine_notification", Set.of("source_type", "notification_level", "status")),
        Map.entry("embed_launch_token", Set.of("status")),
        Map.entry("model_capability_task", Set.of("model_mode", "status")),
        Map.entry("model_capability_policy", Set.of("route_strategy")),
        Map.entry("mk_experience_saved_view", Set.of("version", "status")),
        Map.entry("mk_experience_export_task", Set.of("selected_scope", "status")),
        Map.entry("mk_experience_user_pref", Set.of("version", "status")),
        Map.entry("integration_adapter", Set.of("status", "health_status")),
        Map.entry("integration_webhook_config", Set.of("status")),
        Map.entry("integration_message_log", Set.of("status", "direction")),
        Map.entry("mk_integration_onboarding", Set.of("access_mode", "status", "fhir_version")),
        Map.entry("mk_integration_regional_source", Set.of("trust_level", "status")),
        Map.entry("tenant_success_plan", Set.of("current_stage")),
        Map.entry("mpi_patient", Set.of("status")),
        Map.entry("mk_mpi_merge_review", Set.of("risk_level", "status")),
        Map.entry("emergency_permission_grant", Set.of("active_flag")),
        Map.entry("sys_task", Set.of("task_mode", "status")),
        Map.entry("sys_task_dead_letter", Set.of("task_mode")),
        Map.entry("mk_config_item", Set.of("value_type", "risk_level", "source", "protected_flag", "active_flag", "version")),
        Map.entry("mk_config_history", Set.of("change_type", "version")),
        Map.entry("mk_projection_sync", Set.of("target_type", "status")),
        Map.entry("mk_projection_snapshot", Set.of("target_type", "fact_kind")),
        Map.entry("mk_version_asset_version", Set.of("version_no", "content_hash", "status")),
        Map.entry("mk_version_inheritance_override", Set.of("override_mode")),
        Map.entry("mk_version_release_plan", Set.of("scope_type", "status")),
        Map.entry("mk_version_activation_transaction", Set.of("action")),
        Map.entry("mk_version_replay_binding", Set.of("result_hash")),
        Map.entry("mk_fhir_resource_mapping", Set.of("fhir_version", "mapping_status")),
        Map.entry("mk_fhir_mapping_rule", Set.of("fhir_version", "rule_version", "status"))
    );

    private static final Pattern TABLE_PATTERN =
        Pattern.compile("(?i)CREATE\\s+TABLE(?:\\s+IF\\s+NOT\\s+EXISTS)?\\s+([a-z0-9_]+)");
    private static final Pattern TABLE_BLOCK_PATTERN = Pattern.compile(
        "(?is)CREATE\\s+TABLE(?:\\s+IF\\s+NOT\\s+EXISTS)?\\s+([a-z0-9_]+)\\s*\\((.*?)\\);");
    private static final Pattern INDEX_PATTERN = Pattern.compile(
        "(?i)CREATE\\s+(?:UNIQUE\\s+)?INDEX(?:\\s+IF\\s+NOT\\s+EXISTS)?\\s+([a-z0-9_]+)");
    // 只匹配独立的 CONSTRAINT 关键字声明：前置 (?<![a-z0-9_]) 排除把列名后缀（如 value_constraint / temporal_constraint）误当约束名；
    // (?<!DROP\s) 排除 DROP CONSTRAINT 回退语句。
    private static final Pattern CONSTRAINT_PATTERN =
        Pattern.compile("(?i)(?<![a-z0-9_])(?<!DROP\\s)CONSTRAINT\\s+([a-z0-9_]+)");

    @Test
    void everyDialectPublishesTheSameAuthoritativeMigrationSequence() throws IOException {
        for (String dialect : DIALECTS) {
            assertThat(migrationFiles(dialect))
                .as("%s 权威迁移序列", dialect)
                .containsExactlyElementsOf(EXPECTED_MIGRATIONS);
        }
    }

    @Test
    void integrationAdapterUniqueConstraintIsTenantScopedInEveryDialect() {
        for (String dialect : DIALECTS) {
            String sql = readMigration(dialect, "V59__integration_adapter_tenant_unique.sql")
                .toLowerCase(Locale.ROOT)
                .replaceAll("\\s+", " ");

            assertThat(sql)
                .as("%s 适配器唯一约束必须收窄到租户作用域", dialect)
                .contains("drop constraint")
                .contains("uk_integration_adapter")
                .contains("unique (tenant_id, adapter_id)");
        }
    }

    @Test
    void integrationMessageIdempotencyConstraintIsTenantScopedInEveryDialect() {
        for (String dialect : DIALECTS) {
            String sql = readMigration(dialect, "V60__integration_message_tenant_unique.sql")
                .toLowerCase(Locale.ROOT)
                .replaceAll("\\s+", " ");

            assertThat(sql)
                .as("%s 入站消息幂等键必须收窄到租户作用域", dialect)
                .contains("drop constraint")
                .contains("uk_integration_message")
                .contains("unique (tenant_id, message_id)");
        }
    }

    @Test
    void integrationWebhookUniqueConstraintIsTenantScopedInEveryDialect() {
        for (String dialect : DIALECTS) {
            String sql = readMigration(dialect, "V61__integration_webhook_tenant_unique.sql")
                .toLowerCase(Locale.ROOT)
                .replaceAll("\\s+", " ");

            assertThat(sql)
                .as("%s Webhook 唯一约束必须收窄到租户作用域", dialect)
                .contains("drop constraint")
                .contains("uk_integration_webhook")
                .contains("unique (tenant_id, webhook_id)");
        }
    }

    @Test
    void integrationMessageStatusAllowsNotConnectedForNonBlockingDegradationInEveryDialect() {
        for (String dialect : DIALECTS) {
            String sql = readMigration(dialect, "V62__integration_message_not_connected_status.sql")
                .toLowerCase(Locale.ROOT)
                .replaceAll("\\s+", " ");

            assertThat(sql)
                .as("%s 集成消息状态必须支持断连降级且不伪造成功", dialect)
                .contains("ck_integration_message_status")
                .contains("not_connected")
                .contains("dead_letter");
        }
    }

    @Test
    void v63ShouldDeclareFhirResourceMappingAndRulesForAllDialects() {
        for (String dialect : DIALECTS) {
            String sql = readMigration(dialect, "V63__fhir_resource_mapping.sql")
                .toLowerCase(Locale.ROOT)
                .replaceAll("\\s+", " ");

            assertThat(sql)
                .as("%s FHIR 资源映射与规则表", dialect)
                .contains("mk_fhir_resource_mapping")
                .contains("mk_fhir_mapping_rule")
                .contains("uk_mk_fhir_res_map_fhir")
                .contains("uk_mk_fhir_res_map_canon")
                .contains("ck_mk_fhir_res_map_ver")
                .contains("ck_mk_fhir_res_map_status")
                .contains("idx_mk_fhir_res_map_tenant")
                .contains("uk_mk_fhir_rule_code")
                .contains("ck_mk_fhir_rule_ver")
                .contains("ck_mk_fhir_rule_status")
                .contains("idx_mk_fhir_rule_tenant")
                .contains("comment on table mk_fhir_resource_mapping")
                .contains("comment on table mk_fhir_mapping_rule");
        }
    }

    @Test
    void v77ShouldDeclareWorkflowCollaborationTablesForAllDialects() {
        for (String dialect : DIALECTS) {
            String sql = readMigration(dialect, "V77__workflow_collaboration.sql")
                .toLowerCase(Locale.ROOT)
                .replaceAll("\\s+", " ");

            assertThat(sql)
                .as("%s 临床协同待办与通知中心迁移", dialect)
                .contains("mk_engine_workflow_todo")
                .contains("mk_engine_notification")
                .contains("uk_workflow_todo_source")
                .contains("uk_notification_dedupe")
                .contains("ck_workflow_todo_status")
                .contains("ck_notification_status")
                .contains("idx_workflow_todo_tenant_status_due")
                .contains("idx_notification_tenant_status_created")
                .contains("comment on table mk_engine_workflow_todo")
                .contains("comment on table mk_engine_notification");
        }
    }

    @Test
    void v80ShouldPersistWorkflowTransferReasonForAllDialects() {
        for (String dialect : DIALECTS) {
            String sql = readMigration(dialect, "V80__workflow_transfer_reason.sql")
                .toLowerCase(Locale.ROOT)
                .replaceAll("\\s+", " ");

            assertThat(sql)
                .as("%s 待办转交说明迁移", dialect)
                .contains("mk_engine_workflow_todo")
                .contains("transfer_reason")
                .contains("comment on column mk_engine_workflow_todo.transfer_reason");
        }
    }

    @Test
    void v81ShouldAllowWorkflowSyncEventNotificationsForAllDialects() {
        for (String dialect : DIALECTS) {
            String sql = readMigration(dialect, "V81__workflow_sync_event_notifications.sql")
                .toLowerCase(Locale.ROOT)
                .replaceAll("\\s+", " ");

            assertThat(sql)
                .as("%s 临床同步事件通知来源迁移", dialect)
                .contains("mk_engine_notification")
                .contains("ck_notification_source_type")
                .contains("sync_event")
                .contains("comment on column mk_engine_notification.source_type");
        }
    }

    @Test
    void v82ShouldAddWorkflowOrganizationScopeForAllDialects() {
        for (String dialect : DIALECTS) {
            String sql = readMigration(dialect, "V82__workflow_organization_scope.sql")
                .toLowerCase(Locale.ROOT)
                .replaceAll("\\s+", " ");

            assertThat(sql)
                .as("%s 临床协同组织作用域迁移", dialect)
                .contains("mk_engine_workflow_todo")
                .contains("mk_engine_notification")
                .contains("org_unit_id")
                .contains("idx_workflow_todo_org_scope")
                .contains("idx_notification_org_scope")
                .contains("comment on column mk_engine_workflow_todo.org_unit_id")
                .contains("comment on column mk_engine_notification.org_unit_id");
        }
    }

    @Test
    void everyDialectPreservesRequiredTablesIndexesAndBusinessConstraints() throws IOException {
        for (String dialect : DIALECTS) {
            String ddl = combinedDdl(dialect);
            assertThat(names(TABLE_PATTERN, ddl)).as("%s 表族", dialect)
                .containsExactlyInAnyOrderElementsOf(REQUIRED_TABLES);
            assertThat(names(INDEX_PATTERN, ddl)).as("%s 索引", dialect)
                .containsExactlyInAnyOrderElementsOf(REQUIRED_INDEXES);

            Set<String> expectedConstraints = COMMON_CONSTRAINTS;
            if (dialect.equals("oracle") || dialect.equals("dm")) {
                expectedConstraints = new HashSet<>(COMMON_CONSTRAINTS);
                expectedConstraints.add("ck_mapping_candidate_conflict");
                expectedConstraints.add("ck_mk_fhir_rule_required");
            }
            assertThat(names(CONSTRAINT_PATTERN, ddl)).as("%s 业务约束", dialect)
                .containsExactlyInAnyOrderElementsOf(expectedConstraints);
        }
    }

    @Test
    void schemaConsistencyReportHasNoTableColumnDiffsAcrossDialects() throws IOException {
        Map<String, Set<String>> reference = tableColumns(combinedDdl("h2"));
        Map<String, Object> diffs = new LinkedHashMap<>();

        for (String dialect : DIALECTS) {
            Map<String, Set<String>> current = tableColumns(combinedDdl(dialect));
            for (String table : REQUIRED_TABLES) {
                Set<String> expectedColumns = reference.getOrDefault(table, Set.of());
                Set<String> actualColumns = current.getOrDefault(table, Set.of());
                if (!expectedColumns.equals(actualColumns)) {
                    diffs.put(dialect + "." + table, Map.of(
                        "missing", difference(expectedColumns, actualColumns),
                        "extra", difference(actualColumns, expectedColumns)
                    ));
                }
            }
        }

        assertThat(diffs).as("五方言表/列一致性差异清单").isEmpty();
    }

    @Test
    void tenantIsolationAuditAndLifecycleColumnsRemainPresent() throws IOException {
        for (String dialect : DIALECTS) {
            Map<String, String> tables = tableBlocks(combinedDdl(dialect));
            TENANT_TABLES.forEach(table ->
                assertThat(tables.get(table)).as("%s.%s 租户字段", dialect, table).contains("tenant_id"));
            MUTABLE_AUDITED_TABLES.forEach(table ->
                assertThat(tables.get(table)).as("%s.%s 审计字段", dialect, table)
                    .contains("created_at", "created_by", "updated_at", "updated_by"));
            TECHNICAL_AUDIT_FIELDS.forEach((table, fields) ->
                assertThat(tables.get(table)).as("%s.%s 专属审计字段", dialect, table)
                    .contains(fields.toArray(String[]::new)));
            LIFECYCLE_FIELDS.forEach((table, fields) ->
                assertThat(tables.get(table)).as("%s.%s 状态或版本字段", dialect, table)
                    .contains(fields.toArray(String[]::new)));
        }
    }

    @Test
    void v8ShouldDeclareObservabilityBaseline() {
        String h2 = readMigration("h2", "V8__observability_baseline.sql");
        assertThat(h2).contains(
            "CREATE TABLE IF NOT EXISTS mk_obs_state_transition",
            "CREATE TABLE IF NOT EXISTS mk_obs_payload_store",
            "org_path",
            "payload_base64",
            "deleted_at");
        assertThat(h2).contains("ALTER TABLE canonical_resource ADD COLUMN IF NOT EXISTS trace_id");
        assertThat(h2).contains("ck_most_error_class", "ck_mops_storage_type", "uk_mops_payload_id");
        assertThat(h2).contains("idx_most_entity", "idx_most_trace", "idx_mops_trace");
    }

    @Test
    void v8ShouldExistInAllFiveDialects() {
        for (String dialect : List.of("postgres", "oracle", "dm", "kingbase", "h2")) {
            assertThat(migrationPathFor(dialect, "V8__observability_baseline.sql"))
                .as("dialect %s must ship V8", dialect)
                .exists();
        }
    }

    @Test
    void v48ShouldExtendContextSnapshotStandardContract() {
        for (String dialect : DIALECTS) {
            String sql = readMigration(dialect, "V48__context_snapshot_standard_contract.sql")
                .toLowerCase(Locale.ROOT);
            assertThat(sql).as("%s V48 标准上下文字段", dialect)
                .contains("context_snapshot", "request_id", "org_path", "package_version");
            assertThat(sql).as("%s V48 标准上下文索引", dialect)
                .contains(
                    "idx_context_snapshot_tenant_request",
                    "idx_context_snapshot_org_path",
                    "idx_context_snapshot_package_version"
                );
        }
    }

    @Test
    void v49ShouldAddCitationAnchorOffsetsForAllDialects() {
        for (String dialect : DIALECTS) {
            String ddl = readMigration(dialect, "V49__knowledge_citation_anchor_offsets.sql");
            assertThat(ddl).as("%s 引用锚点偏移字段", dialect)
                .contains("uk_source_version_doc_hash")
                .contains("start_offset")
                .contains("end_offset")
                .contains("ck_citation_anchor_offsets")
                .contains("COMMENT ON COLUMN citation.start_offset")
                .contains("COMMENT ON COLUMN citation.end_offset");
        }
    }

    @Test
    void v50ShouldAddKnowledgeTrustGradingForAllDialects() {
        for (String dialect : DIALECTS) {
            String ddl = readMigration(dialect, "V50__knowledge_trust_grading.sql");
            assertThat(ddl).as("%s 知识可信分级迁移", dialect)
                .contains("authority_basis")
                .contains("authority_level")
                .contains("grade_quality")
                .contains("grade_strength")
                .contains("conflict_arbitration")
                .contains("ck_source_document_authority")
                .contains("ck_knowledge_asset_version_authority")
                .contains("ck_knowledge_asset_grade_quality")
                .contains("ck_knowledge_asset_grade_strength")
                .contains("idx_knowledge_av_authority")
                .contains("COMMENT ON COLUMN source_document.authority_basis")
                .contains("COMMENT ON COLUMN knowledge_asset_version.authority_level")
                .contains("COMMENT ON COLUMN knowledge_asset_version.grade_quality")
                .contains("COMMENT ON COLUMN knowledge_asset_version.grade_strength")
                .contains("COMMENT ON COLUMN knowledge_asset_version.conflict_arbitration");
        }
    }

    @Test
    void v51ShouldAllowKnowledgeGraphAndSearchProjectionTargetsForAllDialects() {
        for (String dialect : DIALECTS) {
            String ddl = readMigration(dialect, "V51__knowledge_projection_targets.sql");
            assertThat(ddl).as("%s 知识图与搜索投影目标迁移", dialect)
                .contains("ck_mk_projection_sync_target")
                .contains("ck_mk_projection_snapshot_target")
                .contains("KNOWLEDGE_GRAPH")
                .contains("KNOWLEDGE_SEARCH")
                .contains("COMMENT ON COLUMN mk_projection_sync.target_type")
                .contains("COMMENT ON COLUMN mk_projection_snapshot.target_type");
        }
    }

    @Test
    void v52ShouldAddKnowledgeCandidateWorkflowForAllDialects() {
        for (String dialect : DIALECTS) {
            String ddl = readMigration(dialect, "V52__knowledge_candidate_workflow.sql");
            assertThat(ddl).as("%s 知识候选识别与审核迁移", dialect)
                .contains("mk_knowledge_candidate_classification")
                .contains("mk_knowledge_review_assignment")
                .contains("org_path")
                .contains("PENDING_REPLACEMENT_REVIEW")
                .contains("NEW_ASSET")
                .contains("SAME_IDENTITY_NEW_VERSION")
                .contains("DUPLICATE")
                .contains("CONFLICT")
                .contains("DUPLICATE_SKIPPED")
                .contains("APPROVED")
                .contains("REJECTED")
                .contains("ck_knowledge_asset_version_status")
                .contains("ck_knowledge_candidate_classification")
                .contains("ck_knowledge_candidate_review_status")
                .contains("ck_review_assignment_review_status")
                .contains("ck_review_assignment_decision")
                .contains("idx_candidate_classification_identity")
                .contains("idx_candidate_classification_status")
                .contains("idx_review_assignment_identity")
                .contains("idx_review_assignment_status")
                .contains("COMMENT ON TABLE mk_knowledge_candidate_classification")
                .contains("COMMENT ON TABLE mk_knowledge_review_assignment")
                .contains("COMMENT ON COLUMN mk_knowledge_candidate_classification.org_path")
                .contains("COMMENT ON COLUMN mk_knowledge_review_assignment.org_path")
                .contains("COMMENT ON COLUMN mk_knowledge_candidate_classification.diff_summary")
                .contains("COMMENT ON COLUMN mk_knowledge_review_assignment.reason");
        }
    }

    @Test
    void v53ShouldSeedTerminologyHighRiskRulesForAllDialects() {
        for (String dialect : DIALECTS) {
            String ddl = readMigration(dialect, "V53__terminology_high_risk_rules.sql");
            assertThat(ddl).as("%s 术语高危近似规则迁移", dialect)
                .contains("mk_term_high_risk_rule")
                .contains("MED-C1-TROPONIN-TI")
                .contains("MED-C1-K-NA")
                .contains("MED-C1-LEFT-RIGHT")
                .contains("MED-C1-DOSE-10X")
                .contains("MED-C1-INSULIN-UML")
                .contains("MUTUALLY_EXCLUSIVE_TERMS")
                .contains("DOSE_MAGNITUDE")
                .contains("UNIT_STRENGTH")
                .contains("ck_mk_term_high_risk_rule_type")
                .contains("ck_mk_term_high_risk_rule_category")
                .contains("ck_mk_term_high_risk_rule_status")
                .contains("idx_mk_term_high_risk_rule_tenant_status")
                .contains("idx_mk_term_high_risk_rule_category")
                .contains("COMMENT ON TABLE mk_term_high_risk_rule")
                .contains("COMMENT ON COLUMN mk_term_high_risk_rule.evidence_text");
        }
    }

    @Test
    void v54ShouldDeclareAssetVersionFrameworkForAllDialects() {
        for (String dialect : DIALECTS) {
            String ddl = readMigration(dialect, "V54__asset_version_framework.sql");
            assertThat(ddl).as("%s 不可变资产版本框架迁移", dialect)
                .contains("mk_version_asset_version")
                .contains("content_hash")
                .contains("org_path")
                .contains("active_scope_key")
                .contains("uk_mk_version_asset_version_id")
                .contains("uk_mk_version_asset_version_no")
                .contains("uk_mk_version_asset_version_active")
                .contains("ck_mk_version_asset_version_type")
                .contains("ck_mk_version_asset_version_status")
                .contains("ck_mk_version_asset_version_hash")
                .contains("idx_mk_version_asset_version_tenant_status")
                .contains("idx_mk_version_asset_version_identity")
                .contains("idx_mk_version_asset_version_active_scope")
                .contains("COMMENT ON TABLE mk_version_asset_version")
                .contains("COMMENT ON COLUMN mk_version_asset_version.content_hash")
                .contains("COMMENT ON COLUMN mk_version_asset_version.active_scope_key");
        }
    }

    @Test
    void v55ShouldDeclareVersionInheritanceOverrideForAllDialects() {
        for (String dialect : DIALECTS) {
            String ddl = readMigration(dialect, "V55__version_inheritance_override.sql");
            assertThat(ddl).as("%s 继承覆盖迁移", dialect)
                .contains("mk_version_asset_version")
                .contains("safety_policy")
                .contains("mk_version_inheritance_override")
                .contains("override_mode")
                .contains("diff_summary")
                .contains("override_reason")
                .contains("impact_scope")
                .contains("uk_mk_version_inheritance_override_id")
                .contains("uk_mk_version_inheritance_override_active")
                .contains("ck_mk_version_asset_safety_policy")
                .contains("ck_mk_version_inheritance_override_type")
                .contains("ck_mk_version_inheritance_override_mode")
                .contains("idx_mk_version_inheritance_override_scope")
                .contains("idx_mk_version_inheritance_override_version")
                .contains("COMMENT ON COLUMN mk_version_asset_version.safety_policy")
                .contains("COMMENT ON TABLE mk_version_inheritance_override")
                .contains("COMMENT ON COLUMN mk_version_inheritance_override.diff_summary")
                .contains("COMMENT ON COLUMN mk_version_inheritance_override.override_reason")
                .contains("COMMENT ON COLUMN mk_version_inheritance_override.impact_scope");
        }
    }

    @Test
    void v56ShouldDeclareVersionReleaseReplayForAllDialects() {
        for (String dialect : DIALECTS) {
            String ddl = readMigration(dialect, "V56__version_release_replay.sql");
            assertThat(ddl).as("%s 发布流与历史重放迁移", dialect)
                .contains("WITHDRAWN")
                .contains("mk_version_release_plan")
                .contains("mk_version_activation_transaction")
                .contains("mk_version_replay_binding")
                .contains("BED_PERCENT")
                .contains("ROLLBACKED")
                .contains("FULL_ACTIVATE")
                .contains("ck_mk_version_release_plan_scope")
                .contains("ck_mk_version_release_plan_status")
                .contains("uk_mk_version_activation_transaction_idem")
                .contains("ck_mk_version_activation_transaction_action")
                .contains("ck_mk_version_replay_binding_hash")
                .contains("idx_mk_version_release_plan_asset")
                .contains("idx_mk_version_activation_transaction_asset")
                .contains("idx_mk_version_replay_binding_version")
                .contains("COMMENT ON TABLE mk_version_release_plan")
                .contains("COMMENT ON TABLE mk_version_activation_transaction")
                .contains("COMMENT ON TABLE mk_version_replay_binding")
                .contains("COMMENT ON COLUMN mk_version_asset_version.status");
        }
    }

    @Test
    void v57ShouldDeclareKnowledgeEffectiveScopeUniqueForAllDialects() {
        for (String dialect : DIALECTS) {
            String ddl = readMigration(dialect, "V57__knowledge_effective_scope_unique.sql");
            assertThat(ddl).as("%s 知识完整适用域唯一约束迁移", dialect)
                .contains("organization_scope")
                .contains("applicable_scope")
                .contains("active_scope_key")
                .contains("uk_knowledge_asset_version_active_scope")
                .contains("idx_knowledge_av_effective_scope")
                .contains("COMMENT ON COLUMN knowledge_asset_version.organization_scope")
                .contains("COMMENT ON COLUMN knowledge_asset_version.applicable_scope")
                .contains("COMMENT ON COLUMN knowledge_asset_version.active_scope_key");
        }
    }

    @Test
    void v9ShouldExtendAuditEventWithOutcome() {
        String h2 = readMigration("h2", "V9__audit_event_outcome.sql");
        assertThat(h2).contains("ALTER TABLE audit_event ADD COLUMN");
        assertThat(h2).contains("outcome");
        assertThat(h2).contains("error_code");
        assertThat(h2).contains("ck_audit_event_outcome");
        assertThat(h2).contains("idx_audit_event_outcome");
    }

    @Test
    void v30ShouldExtendAuditEventIntoCompleteSpineContract() {
        String h2 = readMigration("h2", "V30__audit_event_spine_contract.sql");
        assertThat(h2).contains("ALTER TABLE audit_event ADD COLUMN");
        assertThat(h2).contains(
            "actor_roles",
            "org_path",
            "environment_key",
            "before_snapshot",
            "after_snapshot",
            "dedupe_key");
        assertThat(h2).contains("uk_audit_event_dedupe");
        assertThat(h2).contains("idx_audit_event_org_path");
        assertThat(h2).contains("idx_audit_event_env");
    }

    @Test
    void v32ShouldAddSourceFragmentContentHashForAllDialects() {
        for (String dialect : DIALECTS) {
            String ddl = readMigration(dialect, "V32__source_fragment_content_hash.sql");
            assertThat(ddl).as("%s source_fragment 内容指纹迁移", dialect)
                .contains("content_hash")
                .contains("uk_source_fragment_version_hash")
                .contains("COMMENT ON COLUMN source_fragment.content_hash");
        }
    }

    @Test
    void v33ShouldAddNotSyncedPackageSyncStatusForAllDialects() {
        for (String dialect : DIALECTS) {
            String ddl = readMigration(dialect, "V33__package_sync_not_synced_status.sql");
            assertThat(ddl).as("%s 包同步 NOT_SYNCED 状态迁移", dialect)
                .contains("ck_release_plan_status")
                .contains("ck_sync_log_status")
                .contains("NOT_SYNCED")
                .contains("COMMENT ON COLUMN release_plan.status")
                .contains("COMMENT ON COLUMN sync_log.status");
        }
    }

    @Test
    void v34ShouldDeclareExperienceFoundationPersistenceForAllDialects() {
        for (String dialect : DIALECTS) {
            String ddl = readMigration(dialect, "V34__experience_foundation_persistence.sql");
            assertThat(ddl).as("%s 产品体验底座持久化", dialect)
                .contains("mk_experience_saved_view")
                .contains("uk_saved_view_user_name")
                .contains("COMMENT ON TABLE mk_experience_saved_view")
                .contains("COMMENT ON TABLE mk_experience_export_task");

            String largeListDdl = readMigration(dialect, "V19__large_list_api.sql");
            assertThat(largeListDdl).as("%s 异步导出任务权威表", dialect)
                .contains("mk_experience_export_task")
                .contains("uk_export_task_idempotency")
                .contains("ck_export_task_status")
                .doesNotContain("large_list_export_job");
        }
    }

    @Test
    void v35ShouldDeclareExperienceUserPreferenceForAllDialects() {
        for (String dialect : DIALECTS) {
            String ddl = readMigration(dialect, "V35__experience_user_preference.sql");
            assertThat(ddl).as("%s 用户体验偏好持久化", dialect)
                .contains("mk_experience_user_pref")
                .contains("uk_user_pref_user_key")
                .contains("idx_user_pref_user_key")
                .contains("COMMENT ON TABLE mk_experience_user_pref")
                .contains("COMMENT ON COLUMN mk_experience_user_pref.pref_key")
                .contains("COMMENT ON COLUMN mk_experience_user_pref.pref_value");
        }
    }

    @Test
    void v36ShouldDeclareBootstrapInitTokenForAllDialects() {
        for (String dialect : DIALECTS) {
            String ddl = readMigration(dialect, "V36__bootstrap_init_token.sql");
            assertThat(ddl).as("%s 首发 init token 持久化", dialect)
                .contains("mk_security_bootstrap_init_token")
                .contains("token_hash")
                .contains("expires_at")
                .contains("uk_bootstrap_init_token_hash")
                .contains("idx_bootstrap_init_token_expires")
                .contains("COMMENT ON TABLE mk_security_bootstrap_init_token")
                .contains("COMMENT ON COLUMN mk_security_bootstrap_init_token.token_hash");
        }
    }

    @Test
    void v44ShouldSeedSystemSuperAdminForAllDialects() {
        for (String dialect : DIALECTS) {
            String ddl = readMigration(dialect, "V44__system_superadmin_seed.sql");
            assertThat(ddl).as("%s 内置超级管理员种子", dialect)
                .contains("system-superadmin")
                .contains("内置超级管理员")
                .contains("sys_role")
                .contains("user_role_assignment")
                .contains("system-superadmin-1");
        }
    }

    @Test
    void v2ShouldDeclareSevenLayerOrgHierarchyAndClosure() {
        for (String dialect : DIALECTS) {
            String ddl = readMigration(dialect, "V2__org_audit_baseline.sql");
            assertThat(ddl).as("%s 组织表必须带组织路径", dialect)
                .contains("org_path");
            assertThat(ddl).as("%s 组织层级必须包含专病层", dialect)
                .contains("SPECIALTY")
                .doesNotContain("WARD");
            assertThat(ddl).as("%s 组织闭包表", dialect)
                .contains("org_closure")
                .contains("ancestor_id")
                .contains("descendant_id")
                .contains("ck_org_closure_depth")
                .contains("idx_org_closure_ancestor")
                .contains("idx_org_closure_descendant");
        }
    }

    @Test
    void v9ShouldExistInAllFiveDialects() {
        for (String dialect : List.of("postgres", "oracle", "dm", "kingbase", "h2")) {
            assertThat(migrationPathFor(dialect, "V9__audit_event_outcome.sql"))
                .as("dialect %s must ship V9", dialect)
                .exists();
        }
    }

    @Test
    void v10ShouldDeclareClinicalEventApiTablesAndColumns() {
        String h2 = readMigration("h2", "V10__clinical_event_api.sql");
        assertThat(h2).contains("CREATE TABLE IF NOT EXISTS clinical_event_payload");
        assertThat(h2).contains("CREATE TABLE IF NOT EXISTS clinical_event_outbox");
        assertThat(h2).contains("ALTER TABLE clinical_event ADD COLUMN IF NOT EXISTS patient_id");
        assertThat(h2).contains("ALTER TABLE clinical_event ADD COLUMN IF NOT EXISTS encounter_id");
        assertThat(h2).contains("ALTER TABLE clinical_event ADD COLUMN IF NOT EXISTS package_version");
        assertThat(h2).contains("ALTER TABLE clinical_event ADD COLUMN IF NOT EXISTS error_code");
        assertThat(h2).contains("ALTER TABLE clinical_event ADD COLUMN IF NOT EXISTS error_class");
        assertThat(h2).contains("ALTER TABLE clinical_event ADD COLUMN IF NOT EXISTS retry_count");
        assertThat(h2).contains("ALTER TABLE clinical_event ADD COLUMN IF NOT EXISTS root_event_id");
        assertThat(h2).contains("uk_event_payload");
        assertThat(h2).contains("uk_outbox_event_id");
        assertThat(h2).contains("idx_outbox_pending");
    }

    @Test
    void v10ShouldExistInAllFiveDialects() {
        for (String dialect : List.of("postgres", "oracle", "dm", "kingbase", "h2")) {
            assertThat(migrationPathFor(dialect, "V10__clinical_event_api.sql"))
                .as("dialect %s must ship V10", dialect)
                .exists();
        }
    }

    @Test
    void v67ShouldDeclareClinicalEventCustomerContractColumnsAndIndexes() {
        String h2 = readMigration("h2", "V67__clinical_event_api_contract.sql");
        assertThat(h2).contains("trigger_point");
        assertThat(h2).contains("idempotency_key");
        assertThat(h2).contains("callback_webhook_id");
        assertThat(h2).contains("ck_clinical_event_trigger_point");
        assertThat(h2).contains("uk_clinical_event_idempotency");
        assertThat(h2).contains("idx_clinical_event_trigger");
        assertThat(h2).contains("idx_clinical_event_callback");
        assertThat(h2).contains("PATIENT_VIEW", "ORDER_SIGN", "MEDICATION_PRESCRIBE",
            "RESULT_REVIEW", "DISCHARGE_SIGN", "FOLLOWUP_ALERT");
    }

    @Test
    void v67ShouldExistInAllFiveDialects() {
        for (String dialect : List.of("postgres", "oracle", "dm", "kingbase", "h2")) {
            assertThat(migrationPathFor(dialect, "V67__clinical_event_api_contract.sql"))
                .as("dialect %s must ship V67", dialect)
                .exists();
        }
    }

    @Test
    void v68ShouldDeclareRecommendationFeedbackIdempotencyAndSuppressionContract() {
        String h2 = readMigration("h2", "V68__recommendation_cdss_contract.sql");
        assertThat(h2).contains("recommendation_feedback");
        assertThat(h2).contains("idempotency_key");
        assertThat(h2).contains("uk_rec_feedback_idempotency");
        assertThat(h2).contains("recommendation_fatigue_signal");
        assertThat(h2).contains("SUPPRESSED");
        assertThat(h2).contains("COMMENT ON COLUMN recommendation_feedback.idempotency_key");
    }

    @Test
    void v68ShouldExistInAllFiveDialects() {
        for (String dialect : List.of("postgres", "oracle", "dm", "kingbase", "h2")) {
            assertThat(migrationPathFor(dialect, "V68__recommendation_cdss_contract.sql"))
                .as("dialect %s must ship V68", dialect)
                .exists();
        }
    }

    @Test
    void v69ShouldDeclareFollowupCustomerApiContractColumnsAndIndexes() {
        String h2 = readMigration("h2", "V69__followup_api09_contract.sql");
        assertThat(h2).contains(
            "followup_plan",
            "idempotency_key",
            "followup_questionnaire",
            "plan_id",
            "questionnaire_template_id",
            "answer_data",
            "submitted_at",
            "executor_id",
            "uk_followup_plan_idempotency",
            "uk_followup_task_idempotency",
            "uk_followup_questionnaire_idempotency",
            "uk_followup_event_idempotency",
            "idx_followup_task_status_due",
            "idx_followup_questionnaire_plan",
            "RETURN_VISIT",
            "IN_PROGRESS",
            "ABNORMAL_RETURN",
            "NOTIFICATION_REQUESTED",
            "COMMENT ON COLUMN followup_event.idempotency_key");
    }

    @Test
    void v69ShouldExistInAllFiveDialects() {
        for (String dialect : List.of("postgres", "oracle", "dm", "kingbase", "h2")) {
            assertThat(migrationPathFor(dialect, "V69__followup_api09_contract.sql"))
                .as("dialect %s must ship V69", dialect)
                .exists();
        }
    }

    @Test
    void v70ShouldDeclareEmbedApi11ContractColumnsAndIndexes() {
        String h2 = readMigration("h2", "V70__embed_api11_contract.sql");
        assertThat(h2).contains(
            "embed_launch_token",
            "integration_mode",
            "hook",
            "hook_instance",
            "consumed_at",
            "idx_embed_token_status_expired",
            "idx_embed_token_hook",
            "IFRAME",
            "SDK",
            "API",
            "REVOKED",
            "COMMENT ON COLUMN embed_launch_token.integration_mode");
    }

    @Test
    void v70ShouldExistInAllFiveDialects() {
        for (String dialect : List.of("postgres", "oracle", "dm", "kingbase", "h2")) {
            assertThat(migrationPathFor(dialect, "V70__embed_api11_contract.sql"))
                .as("dialect %s must ship V70", dialect)
                .exists();
        }
    }

    @Test
    void v71ShouldDeclareFollowupControlledPlanClockColumnsAndIndexes() {
        String h2 = readMigration("h2", "V71__followup_controlled_plan_clock.sql");
        assertThat(h2).contains(
            "source_fact_type",
            "source_fact_id",
            "generation_rule_code",
            "generation_explanation",
            "clinical_clock_id",
            "idx_followup_plan_fact",
            "idx_followup_task_clock",
            "COMMENT ON COLUMN followup_plan.generation_explanation",
            "COMMENT ON COLUMN followup_task.clinical_clock_id");
    }

    @Test
    void v71ShouldExistInAllFiveDialects() {
        for (String dialect : List.of("postgres", "oracle", "dm", "kingbase", "h2")) {
            assertThat(migrationPathFor(dialect, "V71__followup_controlled_plan_clock.sql"))
                .as("dialect %s must ship V71", dialect)
                .exists();
        }
    }

    @Test
    void v72ShouldDeclareCdssRiskMatrixAndRecommendationTraceColumns() {
        String h2 = readMigration("h2", "V72__cdss_risk_matrix.sql");
        assertThat(h2).contains(
            "mk_engine_cdss_risk_matrix",
            "trigger_point",
            "severity_level",
            "automation_level",
            "review_requirement",
            "silent_run_hours",
            "release_gate",
            "auto_execution_allowed",
            "samd_classification",
            "regulatory_evidence",
            "risk_matrix_version",
            "idx_cdss_risk_matrix_active",
            "idx_rec_card_risk_matrix",
            "ck_cdss_risk_matrix_auto_exec",
            "COMMENT ON COLUMN mk_engine_cdss_risk_matrix.samd_classification",
            "COMMENT ON COLUMN recommendation_card.risk_matrix_explanation");
    }

    @Test
    void v72ShouldExistInAllFiveDialects() {
        for (String dialect : List.of("postgres", "oracle", "dm", "kingbase", "h2")) {
            assertThat(migrationPathFor(dialect, "V72__cdss_risk_matrix.sql"))
                .as("dialect %s must ship V72", dialect)
                .exists();
        }
    }

    @Test
    void v73ShouldDeclareClinicalRedlineVersionedCatalogWithoutSeededMedicalConstants() {
        String h2 = readMigration("h2", "V73__clinical_redline.sql");
        assertThat(h2).contains(
            "mk_engine_clinical_redline",
            "category",
            "redline_key",
            "redline_version",
            "hazard_severity",
            "condition_dsl",
            "evidence_source",
            "risk_matrix_id",
            "lower_tenant_override_allowed",
            "idx_clinical_redline_active",
            "uk_clinical_redline_active_scope",
            "ck_clinical_redline_category",
            "COMMENT ON COLUMN mk_engine_clinical_redline.condition_dsl");
        assertThat(h2).doesNotContain("INSERT INTO mk_engine_clinical_redline");
    }

    @Test
    void v73ShouldExistInAllFiveDialects() {
        for (String dialect : List.of("postgres", "oracle", "dm", "kingbase", "h2")) {
            assertThat(migrationPathFor(dialect, "V73__clinical_redline.sql"))
                .as("dialect %s must ship V73", dialect)
                .exists();
        }
    }

    @Test
    void v75ShouldDeclareClinicalRedlineSilentTrialEvidenceWithoutSeededMedicalConstants() {
        String h2 = readMigration("h2", "V75__clinical_redline_silent_trial.sql");
        assertThat(h2).contains(
            "mk_engine_clinical_redline_trial",
            "trial_id",
            "redline_id",
            "redline_version",
            "required_silent_hours",
            "actual_silent_hours",
            "evaluated_case_count",
            "safety_incident_count",
            "evidence_reference",
            "idx_clinical_redline_trial_redline",
            "ck_clinical_redline_trial_counts",
            "COMMENT ON COLUMN mk_engine_clinical_redline_trial.evidence_reference");
        assertThat(h2).doesNotContain("INSERT INTO mk_engine_clinical_redline_trial");
    }

    @Test
    void v78ShouldDeclareDiagnosisKnowledgeAssetTablesAndDomainCheckForAllDialects() {
        for (String dialect : DIALECTS) {
            String sql = readMigration(dialect, "V78__diagnosis_knowledge_asset.sql")
                .toLowerCase(Locale.ROOT)
                .replaceAll("\\s+", " ");

            assertThat(sql)
                .as("%s 诊断知识 5 张子表与命中/置信结构字段", dialect)
                .contains("mk_diagnosis_criterion")
                .contains("mk_diagnosis_differential")
                .contains("mk_diagnosis_care_pointer")
                .contains("mk_diagnosis_test_case")
                .contains("mk_diagnosis_confidence_policy")
                .contains("finding_term_code")
                .contains("expected_confidence")
                .contains("scope_key")
                .contains("ck_mk_diagnosis_criterion_dir")
                .contains("ck_mk_diagnosis_criterion_weight")
                .contains("ck_mk_diagnosis_pointer_type")
                .contains("ck_mk_diagnosis_testcase_conf")
                .contains("uk_mk_diagnosis_testcase")
                .contains("uk_mk_diagnosis_confpolicy")
                .contains("idx_mk_diagnosis_criterion_finding")
                .contains("comment on table mk_diagnosis_confidence_policy");

            assertThat(sql)
                .as("%s 必须放开 knowledge_identity domain 约束收纳 diagnosis（否则诊断身份插不进库）", dialect)
                .contains("alter table knowledge_identity drop constraint ck_knowledge_identity_domain")
                .contains("ck_knowledge_identity_domain check (domain in")
                .contains("'diagnosis'");

            // 仅放行可配置的 DEFAULT 置信策略种子；不得在迁移里写死任何诊断标准/鉴别/测试病例等医学常量。
            assertThat(sql)
                .as("%s 默认置信策略开箱种子（可被租户/科室覆盖）", dialect)
                .contains("insert into mk_diagnosis_confidence_policy")
                .contains("'default'");
            assertThat(sql)
                .as("%s 不得种入诊断标准/测试病例等医学常量", dialect)
                .doesNotContain("insert into mk_diagnosis_criterion")
                .doesNotContain("insert into mk_diagnosis_test_case")
                .doesNotContain("insert into mk_diagnosis_differential")
                .doesNotContain("insert into mk_diagnosis_care_pointer");
        }
    }

    @Test
    void v75ShouldExistInAllFiveDialects() {
        for (String dialect : List.of("postgres", "oracle", "dm", "kingbase", "h2")) {
            assertThat(migrationPathFor(dialect, "V75__clinical_redline_silent_trial.sql"))
                .as("dialect %s must ship V75", dialect)
                .exists();
        }
    }

    @Test
    void v78ShouldExistInAllFiveDialects() {
        for (String dialect : List.of("postgres", "oracle", "dm", "kingbase", "h2")) {
            assertThat(migrationPathFor(dialect, "V78__diagnosis_knowledge_asset.sql"))
                .as("dialect %s must ship V78", dialect)
                .exists();
        }
    }

    @Test
    void v76ShouldExtendRecommendationSourceTypeForClinicalRedlineWithoutSeededMedicalConstants() {
        String h2 = readMigration("h2", "V76__recommendation_source_redline_type.sql");
        assertThat(h2).contains(
            "recommendation_source",
            "ck_rec_source_type",
            "'REDLINE'",
            "COMMENT ON COLUMN recommendation_source.source_type");
        assertThat(h2).doesNotContain("INSERT INTO recommendation_source");
    }

    @Test
    void v76ShouldExistInAllFiveDialects() {
        for (String dialect : List.of("postgres", "oracle", "dm", "kingbase", "h2")) {
            assertThat(migrationPathFor(dialect, "V76__recommendation_source_redline_type.sql"))
                .as("dialect %s must ship V76", dialect)
                .exists();
        }
    }

    @Test
    void v79ShouldDeclarePropagationAndOverridePolicyForAllDialects() {
        for (String dialect : DIALECTS) {
            String ddl = readMigration(dialect, "V79__version_propagation_and_override_policy.sql");
            assertThat(ddl).as("%s 传播语义与覆盖策略护栏迁移", dialect)
                .contains("mk_version_inheritance_override")
                .contains("propagation")
                .contains("INHERITABLE")
                .contains("EXCLUSIVE")
                .contains("ck_mk_version_inheritance_override_propagation")
                .contains("mk_version_asset_version")
                .contains("override_policy")
                .contains("FREE")
                .contains("REVIEW")
                .contains("LOCKED")
                .contains("ck_mk_version_asset_override_policy")
                .contains("COMMENT ON COLUMN mk_version_inheritance_override.propagation")
                .contains("COMMENT ON COLUMN mk_version_asset_version.override_policy");
        }
    }

    @Test
    void v79ShouldExistInAllFiveDialects() {
        for (String dialect : List.of("postgres", "oracle", "dm", "kingbase", "h2")) {
            assertThat(migrationPathFor(dialect, "V79__version_propagation_and_override_policy.sql"))
                .as("dialect %s must ship V79", dialect)
                .exists();
        }
    }

    @Test
    void v11ShouldDeclareRuleEngineApiTablesAndColumns() {
        String h2 = readMigration("h2", "V11__rule_engine_api.sql");
        assertThat(h2).contains("CREATE TABLE IF NOT EXISTS rule_definition");
        assertThat(h2).contains("CREATE TABLE IF NOT EXISTS rule_version");
        assertThat(h2).contains("CREATE TABLE IF NOT EXISTS rule_test_case");
        assertThat(h2).contains("CREATE TABLE IF NOT EXISTS rule_execution_log");
        assertThat(h2).contains("dsl_json");
        assertThat(h2).contains("explanation_json");
        assertThat(h2).contains("input_digest");
        assertThat(h2).contains("uk_rule_definition_tenant_code");
        assertThat(h2).contains("ck_rule_definition_status");
        assertThat(h2).contains("ck_rule_test_case_type");
        assertThat(h2).contains("idx_rule_execution_trigger");
    }

    @Test
    void v11ShouldExistInAllFiveDialects() {
        for (String dialect : List.of("postgres", "oracle", "dm", "kingbase", "h2")) {
            assertThat(migrationPathFor(dialect, "V11__rule_engine_api.sql"))
                .as("dialect %s must ship V11", dialect)
                .exists();
        }
    }

    @Test
    void v12ShouldDeclarePathwayEngineApiTablesAndColumns() {
        String h2 = readMigration("h2", "V12__pathway_engine_api.sql");
        assertThat(h2).contains("CREATE TABLE IF NOT EXISTS specialty_package");
        assertThat(h2).contains("CREATE TABLE IF NOT EXISTS specialty_profile");
        assertThat(h2).contains("CREATE TABLE IF NOT EXISTS pathway_template");
        assertThat(h2).contains("CREATE TABLE IF NOT EXISTS pathway_node");
        assertThat(h2).contains("CREATE TABLE IF NOT EXISTS pathway_edge");
        assertThat(h2).contains("CREATE TABLE IF NOT EXISTS patient_pathway");
        assertThat(h2).contains("CREATE TABLE IF NOT EXISTS pathway_variance");
        assertThat(h2).contains("CREATE TABLE IF NOT EXISTS clinical_clock");
        assertThat(h2).contains("CREATE TABLE IF NOT EXISTS specialty_metric_binding");
        assertThat(h2).contains("entry_criteria_json");
        assertThat(h2).contains("condition_json");
        assertThat(h2).contains("current_node_code");
        assertThat(h2).contains("metric_code");
        assertThat(h2).contains("ck_pathway_node_type");
        assertThat(h2).contains("ck_patient_pathway_status");
        assertThat(h2).contains("idx_clinical_clock_due");
    }

    @Test
    void v12ShouldExistInAllFiveDialects() {
        for (String dialect : List.of("postgres", "oracle", "dm", "kingbase", "h2")) {
            assertThat(migrationPathFor(dialect, "V12__pathway_engine_api.sql"))
                .as("dialect %s must ship V12", dialect)
                .exists();
        }
    }

    @Test
    void v13ShouldDeclareRecommendationCdssApiTablesAndColumns() {
        String h2 = readMigration("h2", "V13__recommendation_cdss_api.sql");
        assertThat(h2).contains("CREATE TABLE IF NOT EXISTS recommendation_trigger");
        assertThat(h2).contains("CREATE TABLE IF NOT EXISTS recommendation_card");
        assertThat(h2).contains("CREATE TABLE IF NOT EXISTS recommendation_source");
        assertThat(h2).contains("CREATE TABLE IF NOT EXISTS recommendation_feedback");
        assertThat(h2).contains("CREATE TABLE IF NOT EXISTS recommendation_fatigue_signal");
        assertThat(h2).contains("input_digest");
        assertThat(h2).contains("source_summary");
        assertThat(h2).contains("explanation_json");
        assertThat(h2).contains("requires_physician_confirmation");
        assertThat(h2).contains("ai_generated");
        assertThat(h2).contains("fatigue_key");
        assertThat(h2).contains("ck_rec_card_risk");
        assertThat(h2).contains("ck_rec_feedback_type");
        assertThat(h2).contains("idx_rec_fatigue_key");
    }

    @Test
    void v13ShouldExistInAllFiveDialects() {
        for (String dialect : List.of("postgres", "oracle", "dm", "kingbase", "h2")) {
            assertThat(migrationPathFor(dialect, "V13__recommendation_cdss_api.sql"))
                .as("dialect %s must ship V13", dialect)
                .exists();
        }
    }

    @Test
    void v14ShouldDeclareEvaluationQualityApiTablesAndColumns() {
        String h2 = readMigration("h2", "V14__evaluation_quality_api.sql");
        assertThat(h2).contains("CREATE TABLE IF NOT EXISTS evaluation_indicator");
        assertThat(h2).contains("CREATE TABLE IF NOT EXISTS evaluation_run");
        assertThat(h2).contains("CREATE TABLE IF NOT EXISTS evaluation_result");
        assertThat(h2).contains("CREATE TABLE IF NOT EXISTS quality_finding");
        assertThat(h2).contains("CREATE TABLE IF NOT EXISTS rectification_task");
        assertThat(h2).contains("CREATE TABLE IF NOT EXISTS rectification_review");
        assertThat(h2).contains("denominator_definition");
        assertThat(h2).contains("numerator_definition");
        assertThat(h2).contains("evidence_summary");
        assertThat(h2).contains("responsible_department_id");
        assertThat(h2).contains("assignee_user_id");
        assertThat(h2).contains("comment");
        assertThat(h2).contains("ck_eval_indicator_status");
        assertThat(h2).contains("ck_quality_finding_severity");
        assertThat(h2).contains("idx_rect_task_department_status");
    }

    @Test
    void v14ShouldExistInAllFiveDialects() {
        for (String dialect : List.of("postgres", "oracle", "dm", "kingbase", "h2")) {
            assertThat(migrationPathFor(dialect, "V14__evaluation_quality_api.sql"))
                .as("dialect %s must ship V14", dialect)
                .exists();
        }
    }

    @Test
    void v14RectificationLookupIndexMustNotDuplicateUniqueFindingKey() {
        for (String dialect : DIALECTS) {
            String ddl = readMigration(dialect, "V14__evaluation_quality_api.sql");
            assertThat(ddl)
                .as("%s 整改任务查询索引必须覆盖状态，避免与唯一键重复", dialect)
                .containsPattern("idx_rect_task_finding\\s+ON\\s+rectification_task\\s*"
                    + "\\(tenant_id,\\s*finding_id,\\s*status\\)");
        }
    }

    @Test
    void v14ReviewCommentColumnMustAvoidOracleReservedKeyword() {
        for (String dialect : DIALECTS) {
            String ddl = readMigration(dialect, "V14__evaluation_quality_api.sql");
            assertThat(ddl)
                .as("%s 复核意见列必须避开 Oracle 保留字", dialect)
                .contains("review_comment")
                .doesNotContainPattern("(?m)^\\s*comment\\s+VARCHAR");
        }
    }

    @Test
    void v15ReleaseScopeMustFollowSevenLayerOrgScope() {
        for (String dialect : DIALECTS) {
            String ddl = readMigration(dialect, "V15__package_release_baseline.sql");
            assertThat(ddl)
                .as("%s 包发布作用域必须跟随七层组织口径", dialect)
                .contains("SPECIALTY")
                .doesNotContain("WARD")
                .doesNotContain("DOCTOR_TEAM");
        }
    }

    @Test
    void v25ShouldSeedInitialUsersAndRolesInAllDialects() {
        for (String dialect : DIALECTS) {
            String ddl = readMigration(dialect, "V25__security_user_role_seed.sql");
            assertThat(ddl).as("%s V25 初始化用户角色数据", dialect)
                .contains("user_role_assignment")
                .contains("admin-1")
                .contains("implementation-1")
                .contains("doctor-1")
                .contains("hospital-admin")
                .contains("implementation-engineer")
                .contains("doctor")
                .contains("migration-v25");
        }
    }

    private List<String> migrationFiles(String dialect) throws IOException {
        try (var files = Files.list(migrationPath(dialect))) {
            return files.map(path -> path.getFileName().toString())
                .filter(name -> name.endsWith(".sql"))
                .sorted((left, right) -> Integer.compare(migrationVersion(left), migrationVersion(right)))
                .toList();
        }
    }

    private int migrationVersion(String filename) {
        int separator = filename.indexOf("__");
        return Integer.parseInt(filename.substring(1, separator));
    }

    private String combinedDdl(String dialect) throws IOException {
        StringBuilder ddl = new StringBuilder();
        for (String migration : EXPECTED_MIGRATIONS) {
            ddl.append(Files.readString(migrationPath(dialect).resolve(migration))).append('\n');
        }
        return ddl.toString().toLowerCase(Locale.ROOT);
    }

    private Path migrationPath(String dialect) {
        var resource = getClass().getClassLoader().getResource("db/migration/" + dialect);
        assertThat(resource).as("%s 迁移资源目录", dialect).isNotNull();
        try {
            return Path.of(resource.toURI());
        } catch (URISyntaxException exception) {
            throw new IllegalStateException("无法读取迁移资源目录: " + dialect, exception);
        }
    }

    private Set<String> names(Pattern pattern, String ddl) {
        LinkedHashSet<String> names = new LinkedHashSet<>();
        var matcher = pattern.matcher(ddl);
        while (matcher.find()) {
            names.add(matcher.group(1).toLowerCase(Locale.ROOT));
        }
        return names;
    }

    private Map<String, String> tableBlocks(String ddl) {
        Map<String, String> blocks = new LinkedHashMap<>();
        var matcher = TABLE_BLOCK_PATTERN.matcher(ddl);
        while (matcher.find()) {
            blocks.put(matcher.group(1).toLowerCase(Locale.ROOT), matcher.group(2));
        }
        return blocks;
    }

    private Map<String, Set<String>> tableColumns(String ddl) {
        Map<String, Set<String>> columns = new LinkedHashMap<>();
        tableBlocks(ddl).forEach((table, block) -> columns.put(table, columnNames(block)));
        return columns;
    }

    private Set<String> columnNames(String tableBlock) {
        LinkedHashSet<String> columns = new LinkedHashSet<>();
        for (String line : tableBlock.split("\\R")) {
            String trimmed = line.strip().replaceFirst(",\\s*$", "");
            if (trimmed.isBlank()
                || trimmed.startsWith("--")
                || trimmed.startsWith("constraint ")
                || trimmed.startsWith("primary key")
                || trimmed.startsWith("unique ")
                || trimmed.startsWith("foreign key")
                || trimmed.startsWith("check ")) {
                continue;
            }

            String name = trimmed.split("\\s+", 2)[0];
            if (name.matches("[a-z][a-z0-9_]*")) {
                columns.add(name);
            }
        }
        return columns;
    }

    private Set<String> difference(Set<String> left, Set<String> right) {
        LinkedHashSet<String> result = new LinkedHashSet<>(left);
        result.removeAll(right);
        return result;
    }

    private String readMigration(String dialect, String filename) {
        try {
            return Files.readString(migrationPathFor(dialect, filename));
        } catch (IOException e) {
            throw new IllegalStateException("无法读取迁移文件: " + dialect + "/" + filename, e);
        }
    }

    private Path migrationPathFor(String dialect, String filename) {
        return migrationPath(dialect).resolve(filename);
    }
}
