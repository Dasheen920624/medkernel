package com.medkernel.engine.integration;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.RecordComponent;
import java.util.Arrays;

import org.junit.jupiter.api.Test;
import org.springframework.web.bind.annotation.RequestHeader;

import com.medkernel.engine.integration.dto.WebhookInboundRequestDto;
import com.medkernel.engine.integration.fhir.FhirFacadeController;
import com.medkernel.engine.integration.fhir.FhirFacadeCreateCommand;
import com.medkernel.engine.integration.inbound.InboundClinicalEventCommand;
import com.medkernel.engine.integration.inbound.InboundTerminologyMappingPort;

class InboundClinicalContractTest {

    @Test
    void webhookAndFhirInputsNeverExposePackageSelection() throws Exception {
        assertThat(componentNames(WebhookInboundRequestDto.class))
            .doesNotContain("packageId", "packageCode", "packageVersion");
        assertThat(componentNames(FhirFacadeCreateCommand.class))
            .doesNotContain("packageId", "packageCode", "packageVersion");

        var create = Arrays.stream(FhirFacadeController.class.getDeclaredMethods())
            .filter(method -> method.getName().equals("create"))
            .findFirst()
            .orElseThrow();
        assertThat(Arrays.stream(create.getParameters())
            .map(parameter -> parameter.getAnnotation(RequestHeader.class))
            .filter(annotation -> annotation != null)
            .map(annotation -> annotation.value().isBlank() ? annotation.name() : annotation.value()))
            .doesNotContain("X-MedKernel-Package-Version");
    }

    @Test
    void internalInboundFlowCarriesOneServerLockedRuntimeRelease() {
        assertThat(componentNames(InboundClinicalEventCommand.class))
            .contains("runtimeReleaseId")
            .doesNotContain("packageId", "packageCode", "packageVersion");
        assertThat(Arrays.stream(InboundTerminologyMappingPort.class.getDeclaredMethods())
            .flatMap(method -> Arrays.stream(method.getParameters()))
            .map(parameter -> parameter.getName()))
            .contains("runtimeReleaseId")
            .doesNotContain("packageVersion");
    }

    private static String[] componentNames(Class<?> type) {
        return Arrays.stream(type.getRecordComponents())
            .map(RecordComponent::getName)
            .toArray(String[]::new);
    }
}
