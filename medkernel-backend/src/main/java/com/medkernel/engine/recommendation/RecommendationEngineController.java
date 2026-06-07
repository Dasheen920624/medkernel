package com.medkernel.engine.recommendation;

import java.util.List;

import com.medkernel.shared.api.ApiResult;
import com.medkernel.shared.api.PageRequest;
import com.medkernel.shared.api.PageResponse;
import com.medkernel.shared.datascope.DataScope;
import com.medkernel.shared.observability.DiagnoseResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * GA-ENG-API-07 推荐/CDSS API（触发、推荐卡查询、卡详情、来源解释、医师反馈、疲劳治理信号、诊断）。
 *
 * <p>所有接口要求请求上下文携带租户（{@link DataScope#requireTenant}）。
 * 触发与来源解释属于受控写入（{@code recommendation.write}），
 * 列表与详情属于读（{@code recommendation.read}），
 * 医师反馈使用专属权限 {@code recommendation.accept}；
 * 不提供绕过权限或租户隔离的调试接口。
 */
@RestController
@RequestMapping("/api/v1/engine/recommendations")
@DataScope(requireTenant = true)
public class RecommendationEngineController {

    private final RecommendationEngineService service;

    public RecommendationEngineController(RecommendationEngineService service) {
        this.service = service;
    }

    /**
     * 接收推荐触发并可携带候选推荐卡入参，返回 triggerId 与本次落库卡数。
     */
    @PostMapping("/triggers")
    @PreAuthorize("@perm.has('recommendation.write')")
    public ResponseEntity<ApiResult<RecommendationTriggerResponse>> trigger(
            @RequestBody @Valid RecommendationTriggerRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(ApiResult.ok(service.trigger(request)));
    }

    /**
     * 分页查询当前租户的推荐卡，支持按 status / riskLevel / scenarioCode / patientId / encounterId / triggerPoint 过滤。
     */
    @GetMapping("/cards")
    @PreAuthorize("@perm.has('recommendation.read')")
    public ApiResult<PageResponse<RecommendationCard>> cards(
            @RequestParam(required = false) RecommendationCardStatus status,
            @RequestParam(required = false) RecommendationRiskLevel riskLevel,
            @RequestParam(required = false) String scenarioCode,
            @RequestParam(required = false) String patientId,
            @RequestParam(required = false) String encounterId,
            @RequestParam(required = false) String triggerPoint,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size,
            @RequestParam(required = false) String sort) {
        return ApiResult.ok(service.listCards(
            new RecommendationCardFilter(status, riskLevel, scenarioCode, patientId, encounterId, triggerPoint),
            new PageRequest(page, size, sort)));
    }

    /**
     * 分页查询医生端临床提醒卡聚合视图，返回卡片与触发上下文，避免页面拼接实体内部字段。
     */
    @GetMapping("/clinical-cards")
    @PreAuthorize("@perm.has('recommendation.read')")
    public ApiResult<PageResponse<RecommendationClinicalCardResponse>> clinicalCards(
            @RequestParam(required = false) RecommendationCardStatus status,
            @RequestParam(required = false) RecommendationRiskLevel riskLevel,
            @RequestParam(required = false) String scenarioCode,
            @RequestParam(required = false) String patientId,
            @RequestParam(required = false) String encounterId,
            @RequestParam(required = false) String triggerPoint,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size,
            @RequestParam(required = false) String sort) {
        return ApiResult.ok(service.listClinicalCards(
            new RecommendationCardFilter(status, riskLevel, scenarioCode, patientId, encounterId, triggerPoint),
            new PageRequest(page, size, sort)));
    }

    /**
     * 查询推荐提醒闭环真实统计，供采纳率、拒绝率和疲劳治理看板只读复用。
     */
    @GetMapping("/stats")
    @PreAuthorize("@perm.has('recommendation.read')")
    public ApiResult<RecommendationStatsResponse> stats(
            @RequestParam(required = false) RecommendationCardStatus status,
            @RequestParam(required = false) RecommendationRiskLevel riskLevel,
            @RequestParam(required = false) String scenarioCode,
            @RequestParam(required = false) String patientId,
            @RequestParam(required = false) String encounterId,
            @RequestParam(required = false) String triggerPoint) {
        return ApiResult.ok(service.stats(
            new RecommendationCardFilter(status, riskLevel, scenarioCode, patientId, encounterId, triggerPoint)));
    }

    /**
     * 查询推荐卡详情，聚合来源、反馈和疲劳治理信号。
     */
    @GetMapping("/cards/{cardId}")
    @PreAuthorize("@perm.has('recommendation.read')")
    public ApiResult<RecommendationCardDetailResponse> cardDetail(@PathVariable String cardId) {
        return ApiResult.ok(service.cardDetail(cardId));
    }

    /**
     * 查询推荐卡的来源解释列表（证据链，按创建时间升序）。
     */
    @GetMapping("/cards/{cardId}/sources")
    @PreAuthorize("@perm.has('recommendation.read')")
    public ApiResult<List<RecommendationSource>> sources(@PathVariable String cardId) {
        return ApiResult.ok(service.sources(cardId));
    }

    /**
     * 提交医师反馈并推进推荐卡状态；终止态卡或已过期卡禁止再反馈（{@code ENG_REC_004}）。
     */
    @PostMapping("/cards/{cardId}/feedback")
    @PreAuthorize("@perm.has('recommendation.accept')")
    public ApiResult<RecommendationFeedbackResponse> feedback(
            @PathVariable String cardId,
            @RequestBody @Valid RecommendationFeedbackRequest request) {
        return ApiResult.ok(service.feedback(cardId, request));
    }

    /**
     * 分页查询疲劳治理信号，支持按 fatigueKey / signalType 过滤。
     */
    @GetMapping("/fatigue-signals")
    @PreAuthorize("@perm.has('recommendation.read')")
    public ApiResult<PageResponse<RecommendationFatigueSignal>> fatigueSignals(
            @RequestParam(required = false) String fatigueKey,
            @RequestParam(required = false) RecommendationFatigueSignalType signalType,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size,
            @RequestParam(required = false) String sort) {
        return ApiResult.ok(service.fatigueSignals(
            new RecommendationFatigueSignalFilter(fatigueKey, signalType),
            new PageRequest(page, size, sort)));
    }

    /**
     * 输出推荐触发诊断响应（聚合触发、推荐卡、反馈、疲劳信号），不存在抛 {@code ENG_REC_002}。
     */
    @GetMapping("/triggers/{triggerId}/diagnose")
    @PreAuthorize("@perm.has('recommendation.read')")
    public ApiResult<DiagnoseResponse> diagnose(@PathVariable String triggerId) {
        return ApiResult.ok(service.diagnose(triggerId));
    }
}
