package com.medkernel.engine.context;

import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Component;

import com.medkernel.engine.context.canonical.CanonicalCondition;
import com.medkernel.engine.context.canonical.CanonicalEncounter;
import com.medkernel.engine.context.canonical.CanonicalPatient;

/**
 * 标准上下文 schema 与业务必填校验。
 *
 * <p>Bean Validation 已拦截基本必填项；本组件追加：
 * <ul>
 *   <li>Patient 缺失（CRITICAL，拒绝创建）</li>
 *   <li>关键业务字段缺失（如 Encounter.admissionTime ERROR / Patient.birthDate WARN）</li>
 *   <li>从各资源 qualityStatus 计算 snapshot 整体 quality</li>
 * </ul>
 */
@Component
public class ContextValidator {

    public List<MissingFieldEntry> findMissingFields(ContextSnapshotResources resources) {
        List<MissingFieldEntry> missing = new ArrayList<>();
        if (resources.patient() == null) {
            missing.add(new MissingFieldEntry("PATIENT", "*", "CRITICAL"));
            return missing;
        }
        CanonicalPatient p = resources.patient();
        if (p.birthDate() == null) {
            missing.add(new MissingFieldEntry("PATIENT", "birthDate", "WARN"));
        }
        if (resources.encounters() != null) {
            for (CanonicalEncounter enc : resources.encounters()) {
                if (enc.admissionTime() == null) {
                    missing.add(new MissingFieldEntry("ENCOUNTER", "admissionTime", "ERROR"));
                }
            }
        }
        if (resources.conditions() != null) {
            for (CanonicalCondition c : resources.conditions()) {
                if (c.codeSystem() == null || c.codeSystem().isBlank()) {
                    missing.add(new MissingFieldEntry("CONDITION", "codeSystem", "ERROR"));
                }
            }
        }
        return missing;
    }

    public QualityStatus computeQuality(ContextSnapshotResources resources) {
        if (resources.patient() == null) {
            return QualityStatus.INVALID;
        }
        boolean hasInvalid = false;
        boolean hasPartial = false;
        for (QualityStatus s : collectStatuses(resources)) {
            if (s == QualityStatus.INVALID) {
                hasInvalid = true;
            } else if (s == QualityStatus.PARTIAL) {
                hasPartial = true;
            }
        }
        if (hasInvalid) {
            return QualityStatus.INVALID;
        }
        if (hasPartial) {
            return QualityStatus.PARTIAL;
        }
        return QualityStatus.VALID;
    }

    private List<QualityStatus> collectStatuses(ContextSnapshotResources r) {
        List<QualityStatus> all = new ArrayList<>();
        if (r.patient() != null) {
            all.add(r.patient().qualityStatus());
        }
        r.allergyIntolerances().forEach(e -> all.add(e.qualityStatus()));
        r.encounters().forEach(e -> all.add(e.qualityStatus()));
        r.conditions().forEach(e -> all.add(e.qualityStatus()));
        r.nursingAssessments().forEach(e -> all.add(e.qualityStatus()));
        r.observations().forEach(e -> all.add(e.qualityStatus()));
        r.diagnosticReports().forEach(e -> all.add(e.qualityStatus()));
        r.medications().forEach(e -> all.add(e.qualityStatus()));
        r.procedures().forEach(e -> all.add(e.qualityStatus()));
        r.documents().forEach(e -> all.add(e.qualityStatus()));
        r.carePlans().forEach(e -> all.add(e.qualityStatus()));
        r.followUps().forEach(e -> all.add(e.qualityStatus()));
        r.claims().forEach(e -> all.add(e.qualityStatus()));
        return all;
    }
}
