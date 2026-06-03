package com.medkernel.architecture;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springdoc.core.models.GroupedOpenApi;

import com.medkernel.engine.contract.OpenApiContractConfiguration;
import com.medkernel.engine.contract.ServiceContractCatalog;

class OpenApiContractConfigurationTest {

    @Test
    void medkernelOpenApiGroupUsesServiceContractCatalogPaths() {
        GroupedOpenApi api = new OpenApiContractConfiguration().medkernelServiceContractsOpenApi();

        assertThat(api).isNotNull();
        assertThat(ServiceContractCatalog.openApiPaths())
            .contains("/api/v1/engine/events/**")
            .contains("/api/v1/engine/clinical-events/**")
            .contains("/api/v1/engine/clinical-events:batch/**")
            .contains("/api/v1/engine/clinical-events:replay/**")
            .contains("/api/v1/engine/recommendations/**")
            .contains("/api/v1/engine/recommendations:evaluate/**")
            .contains("/api/v1/engine/rule/**")
            .contains("/api/v1/engine/pathway/**");
    }

    @Test
    void thirdPartyIntegrationOpenApiGroupIsDeclaredForVendorOnboarding() {
        GroupedOpenApi api = new OpenApiContractConfiguration().medkernelThirdPartyIntegrationOpenApi();

        assertThat(api).isNotNull();
        assertThat(ServiceContractCatalog.openApiPaths()).contains("/api/v1/engine/integration/**");
    }
}
