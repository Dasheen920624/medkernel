package com.medkernel.engine.integration.fhir;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.fasterxml.jackson.databind.JsonNode;
import com.medkernel.engine.context.canonical.ClinicalSetting;
import com.medkernel.shared.context.RequestContext;
import com.medkernel.shared.datascope.DataScope;

import jakarta.validation.Valid;

/**
 * OPT-01 FHIR R4/R5 运行门面。
 */
@RestController
@RequestMapping("/api/v1/engine/integration/fhir")
@DataScope(requireTenant = true)
public class FhirFacadeController {

    private final FhirFacadeService service;

    public FhirFacadeController(FhirFacadeService service) {
        this.service = service;
    }

    @GetMapping("/{version}/metadata")
    @PreAuthorize("@perm.has('integration.read')")
    public JsonNode metadata(@PathVariable FhirVersion version) {
        return service.metadata(version);
    }

    @GetMapping("/{version}/{resourceType}/{id}")
    @PreAuthorize("@perm.has('integration.read')")
    public ResponseEntity<JsonNode> read(@PathVariable FhirVersion version,
                                         @PathVariable String resourceType,
                                         @PathVariable String id,
                                         @RequestHeader(value = "X-MedKernel-Fhir-Adapter", required = false)
                                         String adapterId,
                                         @RequestHeader(value = "X-Forwarded-For", required = false) String forwardedFor,
                                         @RequestHeader(value = "X-Real-IP", required = false) String realIp) {
        FhirFacadeResponse response = service.read(new FhirFacadeReadCommand(
            version, resourceType, id, adapterId, sourceIp(forwardedFor, realIp)));
        return ResponseEntity.status(response.status())
            .header("X-Trace-Id", RequestContext.currentTraceId())
            .body(response.body());
    }

    @GetMapping("/{version}/{resourceType}")
    @PreAuthorize("@perm.has('integration.read')")
    public ResponseEntity<JsonNode> search(@PathVariable FhirVersion version,
                                           @PathVariable String resourceType,
                                           @RequestHeader(value = "X-MedKernel-Fhir-Adapter", required = false)
                                           String adapterId,
                                           @RequestHeader(value = "X-Forwarded-For", required = false) String forwardedFor,
                                           @RequestHeader(value = "X-Real-IP", required = false) String realIp,
                                           @RequestParam(value = "patient", required = false) String patient) {
        FhirFacadeResponse response = service.search(new FhirFacadeSearchCommand(
            version, resourceType, adapterId, sourceIp(forwardedFor, realIp), patient));
        return ResponseEntity.status(response.status())
            .header("X-Trace-Id", RequestContext.currentTraceId())
            .body(response.body());
    }

    @PostMapping("/{version}/{resourceType}")
    @PreAuthorize("@perm.has('integration.execute')")
    public ResponseEntity<JsonNode> create(@PathVariable FhirVersion version,
                                           @PathVariable String resourceType,
                                           @RequestHeader(value = "X-MedKernel-Fhir-Adapter", required = false)
                                           String adapterId,
                                           @RequestHeader(value = "X-MedKernel-Timestamp", required = false)
                                           String timestamp,
                                           @RequestHeader(value = "X-MedKernel-Signature", required = false)
                                           String signature,
                                           @RequestHeader(value = "X-Forwarded-For", required = false) String forwardedFor,
                                           @RequestHeader(value = "X-Real-IP", required = false) String realIp,
                                           @RequestHeader("X-MedKernel-Clinical-Setting")
                                           ClinicalSetting clinicalSetting,
                                           @RequestParam(value = "snapshotId", required = false) String snapshotId,
                                           @Valid @RequestBody FhirFacadeCreateRequest request) {
        FhirFacadeResponse response = service.create(new FhirFacadeCreateCommand(
            version,
            resourceType,
            request.resource(),
            adapterId,
            timestamp,
            signature,
            sourceIp(forwardedFor, realIp),
            snapshotId,
            clinicalSetting
        ));
        return ResponseEntity.status(response.status())
            .header("X-Trace-Id", RequestContext.currentTraceId())
            .body(response.body());
    }

    private String sourceIp(String forwardedFor, String realIp) {
        if (forwardedFor != null && !forwardedFor.isBlank()) {
            return forwardedFor.split(",", 2)[0].trim();
        }
        return realIp;
    }
}
