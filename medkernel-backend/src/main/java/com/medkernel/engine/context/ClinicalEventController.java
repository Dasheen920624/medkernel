package com.medkernel.engine.context;

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

import com.medkernel.shared.api.ApiResult;
import com.medkernel.shared.api.PageRequest;
import com.medkernel.shared.api.PageResponse;
import com.medkernel.shared.datascope.DataScope;
import com.medkernel.shared.observability.DiagnoseResponse;

import jakarta.validation.Valid;

/**
 * GA-ENG-API-02 临床事件 API。
 */
@RestController
@RequestMapping("/api/v1/engine/clinical-events")
@DataScope(requireTenant = true)
public class ClinicalEventController {

    private final ClinicalEventService service;

    public ClinicalEventController(ClinicalEventService service) {
        this.service = service;
    }

    @PostMapping
    @PreAuthorize("@perm.has('event.write')")
    public ResponseEntity<ApiResult<ClinicalEventAcceptedResponse>> receive(
            @RequestBody @Valid ClinicalEventRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(ApiResult.ok(service.receive(request)));
    }

    @GetMapping("/{eventId}")
    @PreAuthorize("@perm.has('event.read')")
    public ApiResult<ClinicalEventDetailResponse> findById(@PathVariable String eventId) {
        return ApiResult.ok(service.findById(eventId));
    }

    @GetMapping("/{eventId}/payload")
    @PreAuthorize("@perm.has('event.read')")
    public ApiResult<ClinicalEventPayloadResponse> payload(@PathVariable String eventId) {
        return ApiResult.ok(service.payload(eventId));
    }

    @GetMapping("/{eventId}/diagnose")
    @PreAuthorize("@perm.has('event.read')")
    public ApiResult<DiagnoseResponse> diagnose(@PathVariable String eventId) {
        return ApiResult.ok(service.diagnose(eventId));
    }

    @GetMapping("/dead-letter")
    @PreAuthorize("@perm.has('event.read')")
    public ApiResult<PageResponse<ClinicalEventDeadLetterResponse>> deadLetters(
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size,
            @RequestParam(required = false) String sort) {
        return ApiResult.ok(service.listDeadLetters(new PageRequest(page, size, sort)));
    }

    @PostMapping("/dead-letter/{deadLetterId}/replay")
    @PreAuthorize("@perm.has('event.write')")
    public ApiResult<ClinicalEventReplayResponse> replayDeadLetter(@PathVariable String deadLetterId) {
        return ApiResult.ok(service.replayDeadLetter(deadLetterId));
    }

    @GetMapping
    @PreAuthorize("@perm.has('event.read')")
    public ApiResult<PageResponse<ClinicalEventDetailResponse>> list(
            @RequestParam(required = false) String patientId,
            @RequestParam(required = false) String encounterId,
            @RequestParam(required = false) ClinicalEventStatus status,
            @RequestParam(required = false) ClinicalEventType eventType,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size,
            @RequestParam(required = false) String sort) {
        return ApiResult.ok(service.list(
            new ClinicalEventFilter(patientId, encounterId, status, eventType),
            new PageRequest(page, size, sort)));
    }
}
