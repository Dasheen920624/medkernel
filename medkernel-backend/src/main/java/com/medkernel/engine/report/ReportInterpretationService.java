package com.medkernel.engine.report;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.medkernel.engine.context.ContextSnapshotResources;
import com.medkernel.engine.context.ContextSnapshotResponse;
import com.medkernel.engine.context.ContextSnapshotService;
import com.medkernel.engine.context.ContextSnapshotStatus;
import com.medkernel.engine.context.canonical.CanonicalDiagnosticReport;
import com.medkernel.engine.recommendation.RecommendationCardRequest;
import com.medkernel.engine.recommendation.RecommendationCardType;
import com.medkernel.engine.recommendation.RecommendationEngineService;
import com.medkernel.engine.recommendation.RecommendationInterruptLevel;
import com.medkernel.engine.recommendation.RecommendationRiskLevel;
import com.medkernel.engine.recommendation.RecommendationSourceRequest;
import com.medkernel.engine.recommendation.RecommendationSourceType;
import com.medkernel.engine.recommendation.RecommendationTriggerRequest;
import com.medkernel.shared.api.error.ApiException;
import com.medkernel.shared.api.error.ErrorCode;
import com.medkernel.shared.context.RequestContext;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 医技报告解读运行服务。
 *
 * <p>服务端使用上下文快照锁定的机构生效版本读取医技项目说明书，输出辅助解释和复核建议；
 * 不改写已签发报告，不自动开立医嘱。
 */
@Service
public class ReportInterpretationService {

    static final String ADVISORY_EMPTY =
        "当前机构生效版本没有匹配的医技项目说明书，系统未生成报告解读；这不是排除异常或风险的结论。";
    static final String ADVISORY_PRESENT =
        "报告解读仅用于辅助阅读，不改写已签发报告，不替代医师判断。";
    private static final String TRIGGER_HOOK = "result-review";
    private static final String SCENARIO_CODE = "S36";

    private final ContextSnapshotService snapshots;
    private final RuntimeReleaseDiagnosticItemSelector diagnosticItems;
    private final RecommendationEngineService recommendationEngine;
    private final ObjectMapper objectMapper;

    public ReportInterpretationService(
            ContextSnapshotService snapshots,
            RuntimeReleaseDiagnosticItemSelector diagnosticItems,
            RecommendationEngineService recommendationEngine,
            ObjectMapper objectMapper) {
        this.snapshots = snapshots;
        this.diagnosticItems = diagnosticItems;
        this.recommendationEngine = recommendationEngine;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public ReportInterpretationResponse interpret(ReportInterpretationRequest request) {
        if (request == null || request.contextSnapshotId() == null || request.contextSnapshotId().isBlank()) {
            throw new ApiException(ErrorCode.ENG_REC_001, "报告解读必须指定标准上下文快照");
        }
        ContextSnapshotResponse snapshot = snapshots.findById(request.contextSnapshotId());
        ContextSnapshotResources resources = requireActiveSnapshot(snapshot);
        String tenantId = tenant();
        String runtimeReleaseId = requireText(snapshot.runtimeReleaseId(), "机构生效版本");
        List<RuntimeDiagnosticItemReference> itemRefs =
            diagnosticItems.select(tenantId, runtimeReleaseId).stream()
                .sorted(Comparator.comparing(RuntimeDiagnosticItemReference::itemCode))
                .toList();

        List<ReportInterpretationItem> interpretations = new ArrayList<>();
        for (CanonicalDiagnosticReport report : resources.diagnosticReports()) {
            match(report, itemRefs)
                .map(item -> interpret(report, item, runtimeReleaseId))
                .ifPresent(interpretations::add);
        }
        if (!interpretations.isEmpty()) {
            persist(snapshot, interpretations, itemRefs);
        }
        return new ReportInterpretationResponse(
            snapshot.snapshotId(),
            runtimeReleaseId,
            interpretations,
            interpretations.isEmpty() ? ADVISORY_EMPTY : ADVISORY_PRESENT,
            traceId());
    }

    private ContextSnapshotResources requireActiveSnapshot(ContextSnapshotResponse snapshot) {
        if (snapshot == null || snapshot.status() != ContextSnapshotStatus.ACTIVE
                || snapshot.resources() == null || snapshot.resources().patient() == null) {
            throw new ApiException(ErrorCode.ENG_REC_001, "报告解读只能使用已生效标准上下文");
        }
        return snapshot.resources();
    }

    private java.util.Optional<RuntimeDiagnosticItemReference> match(
            CanonicalDiagnosticReport report,
            List<RuntimeDiagnosticItemReference> items) {
        String reportType = normalize(report.reportType());
        String conclusion = normalize(report.conclusion());
        String findings = normalize(String.join(" ", safeList(report.keyFindings())));
        return items.stream()
            .filter(item -> {
                String code = normalize(item.itemCode());
                String name = normalize(item.itemName());
                return reportType.equals(code)
                    || reportType.contains(code)
                    || code.contains(reportType)
                    || (!name.isBlank() && (conclusion.contains(name) || findings.contains(name)));
            })
            .findFirst();
    }

    private ReportInterpretationItem interpret(
            CanonicalDiagnosticReport report,
            RuntimeDiagnosticItemReference item,
            String runtimeReleaseId) {
        List<String> highlights = highlights(report);
        boolean critical = containsAny(text(report), List.of("危急", "critical", "紧急"));
        String summary = "已签发报告「" + display(report.reportType()) + "」结合当前机构生效版本 "
            + runtimeReleaseId + " 中的「" + item.itemName() + "」生成辅助解读。";
        return new ReportInterpretationItem(
            report.reportId(),
            report.reportType(),
            report.conclusion(),
            item.itemCode(),
            item.itemName(),
            item.knowledgeVersionId(),
            item.versionNo(),
            critical,
            summary,
            highlights,
            recommendations(critical, !highlights.isEmpty()));
    }

    private List<String> highlights(CanonicalDiagnosticReport report) {
        LinkedHashMap<String, Boolean> values = new LinkedHashMap<>();
        safeList(report.keyFindings()).stream()
            .map(this::display)
            .filter(this::hasText)
            .forEach(value -> values.put(value, Boolean.TRUE));
        if (values.isEmpty() && hasText(report.conclusion())) {
            values.put(display(report.conclusion()), Boolean.TRUE);
        }
        if (containsAny(text(report), List.of("危急", "critical")) && !values.containsKey("危急值")) {
            values.put("危急值", Boolean.TRUE);
        }
        return List.copyOf(values.keySet());
    }

    private List<String> recommendations(boolean critical, boolean abnormal) {
        if (critical) {
            return List.of(
                "请按本机构危急值闭环完成人工确认、回报和记录，系统不自动修改报告。",
                "医师结合症状、体征、既往趋势和医技项目说明书判断后续处理，系统不自动开立医嘱。"
            );
        }
        if (abnormal) {
            return List.of(
                "请结合患者上下文复核异常重点，必要时安排复查或专科评估，系统不自动开立医嘱。"
            );
        }
        return List.of("当前仅生成报告阅读辅助说明，系统不自动开立医嘱。");
    }

    private void persist(
            ContextSnapshotResponse snapshot,
            List<ReportInterpretationItem> interpretations,
            List<RuntimeDiagnosticItemReference> itemRefs) {
        Map<String, RuntimeDiagnosticItemReference> byCode = itemRefs.stream()
            .collect(LinkedHashMap::new, (map, item) -> map.putIfAbsent(item.itemCode(), item), Map::putAll);
        List<RecommendationCardRequest> cards = interpretations.stream()
            .map(item -> toCard(item, byCode.get(item.itemCode()), snapshot.runtimeReleaseId()))
            .toList();
        String encounterId = snapshot.resources().encounters().isEmpty()
            ? null
            : snapshot.resources().encounters().getFirst().encounterId();
        recommendationEngine.trigger(new RecommendationTriggerRequest(
            "REPORT-" + snapshot.snapshotId(),
            TRIGGER_HOOK,
            null,
            snapshot.snapshotId(),
            snapshot.resources().patient().mpi(),
            encounterId,
            null,
            SCENARIO_CODE,
            "report:" + snapshot.snapshotId() + ":" + snapshot.runtimeReleaseId(),
            null,
            cards,
            Boolean.FALSE));
    }

    private RecommendationCardRequest toCard(
            ReportInterpretationItem item,
            RuntimeDiagnosticItemReference source,
            String runtimeReleaseId) {
        RecommendationRiskLevel risk = item.criticalRisk()
            ? RecommendationRiskLevel.HIGH
            : RecommendationRiskLevel.LOW;
        RecommendationInterruptLevel interrupt = item.criticalRisk()
            ? RecommendationInterruptLevel.WEAK_INTERRUPTIVE
            : RecommendationInterruptLevel.INFO;
        String versionNo = source == null ? item.versionNo() : source.versionNo();
        String sourceHash = source == null ? null : source.contentHash();
        return new RecommendationCardRequest(
            "report-" + item.reportId(),
            cardType(item.reportType()),
            "医技报告解读：" + display(item.reportType()),
            item.summary(),
            "请医师结合原始报告和患者情况复核；系统不改写已签发报告，不自动开立医嘱。",
            risk,
            interrupt,
            true,
            false,
            "医技项目说明书 " + display(item.itemName()) + " " + display(versionNo),
            explanationJson(item, runtimeReleaseId, sourceHash),
            "report:" + item.reportId(),
            null,
            null,
            List.of(new RecommendationSourceRequest(
                RecommendationSourceType.KNOWLEDGE,
                String.valueOf(item.sourceVersionId()),
                item.versionNo(),
                item.itemName(),
                item.itemCode(),
                sourceHash,
                "医技项目说明书"))
        );
    }

    private RecommendationCardType cardType(String reportType) {
        String value = normalize(reportType);
        if (value.startsWith("lab") || value.contains("检验")) {
            return RecommendationCardType.LAB;
        }
        return RecommendationCardType.EXAM;
    }

    private String explanationJson(ReportInterpretationItem item, String runtimeReleaseId, String sourceHash) {
        try {
            return objectMapper.writeValueAsString(Map.of(
                "reportId", item.reportId(),
                "runtimeReleaseId", runtimeReleaseId,
                "itemCode", item.itemCode(),
                "itemName", item.itemName(),
                "sourceVersionId", item.sourceVersionId(),
                "sourceContentHash", sourceHash == null ? "" : sourceHash,
                "criticalRisk", item.criticalRisk(),
                "abnormalHighlights", item.abnormalHighlights(),
                "recommendations", item.recommendations()
            ));
        } catch (JsonProcessingException exception) {
            throw new ApiException(ErrorCode.INTERNAL_ERROR, "报告解读解释序列化失败", exception);
        }
    }

    private String text(CanonicalDiagnosticReport report) {
        return String.join(" ", safeList(report.keyFindings())) + " " + display(report.conclusion());
    }

    private boolean containsAny(String value, List<String> terms) {
        String normalized = normalize(value);
        return terms.stream().map(this::normalize).anyMatch(normalized::contains);
    }

    private List<String> safeList(List<String> values) {
        return values == null ? List.of() : values;
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }

    private String display(String value) {
        return value == null ? "" : value.trim();
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private String requireText(String value, String label) {
        if (!hasText(value)) {
            throw new ApiException(ErrorCode.ENG_REC_001, label + "不能为空");
        }
        return value.trim();
    }

    private String tenant() {
        String tenantId = RequestContext.currentOrgScope().tenantId();
        if (!hasText(tenantId)) {
            throw ApiException.tenantMissing();
        }
        return tenantId;
    }

    private String traceId() {
        return RequestContext.currentTraceId();
    }
}
