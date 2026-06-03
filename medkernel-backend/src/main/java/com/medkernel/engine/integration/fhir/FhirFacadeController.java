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
                                           @RequestHeader(value = "X-MedKernel-Package-Version", required = false)
                                           String packageVersion,
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
            packageVersion
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
