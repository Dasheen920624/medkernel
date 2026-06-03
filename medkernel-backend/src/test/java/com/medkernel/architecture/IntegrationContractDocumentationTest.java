package com.medkernel.architecture;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;

import com.medkernel.engine.integration.controller.IntegrationController;

class IntegrationContractDocumentationTest {
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final Path CONTRACT_DIR = Path.of("..", "docs", "contracts", "integration");
    private static final Set<String> STANDARD_CONTEXT_RESOURCES = Set.of(
        "Patient",
        "Encounter",
        "Condition",
        "Observation",
        "Medication",
        "Procedure",
        "DiagnosticReport",
        "Document",
        "NursingAssessment",
        "CarePlan",
        "FollowUp",
        "Claim"
    );

    @Test
    void integrationOpenApiPathSnapshotMirrorsControllerWithoutGhostEndpoints() throws Exception {
        JsonNode document = readJson("integration-openapi.paths.json");

        assertThat(document.path("basePath").asText()).isEqualTo("/api/v1/engine/integration");
        assertThat(document.path("fhirFacade").path("status").asText()).isEqualTo("DEFERRED_TO_OPT_01");
        assertThat(document.path("fhirFacade").path("documentedRuntimePaths"))
            .as("OPT-01 未交付前不得在 INTEG-02 文档里伪造 FHIR 运行端点")
            .isEmpty();

        Set<String> actualEndpoints = integrationControllerEndpoints();
        Set<String> documentedEndpoints = new TreeSet<>();
        JsonNode paths = document.path("paths");
        assertThat(paths.isObject()).as("paths 必须是 OpenAPI path 对象").isTrue();

        Iterator<String> pathNames = paths.fieldNames();
        while (pathNames.hasNext()) {
            String path = pathNames.next();
            JsonNode pathNode = paths.path(path);
            Iterator<String> methods = pathNode.fieldNames();
            while (methods.hasNext()) {
                String method = methods.next();
                JsonNode operation = pathNode.path(method);
                documentedEndpoints.add(method.toUpperCase(Locale.ROOT) + " " + path);
                assertThat(operation.path("operationId").asText()).isNotBlank();
                assertThat(operation.path("summary").asText()).isNotBlank();
                assertThat(operation.path("permission").asText()).isNotBlank();
                assertThat(operation.path("auditTarget").asText()).isNotBlank();
                assertThat(operation.path("idempotency").path("requirement").asText()).isNotBlank();
                assertThat(operation.path("degradation").path("disconnectedStatus").asText()).isNotBlank();
            }
        }

        assertThat(documentedEndpoints).containsExactlyElementsOf(actualEndpoints);
    }

    @Test
    void integrationGuideCoversBoundarySecurityDegradationAuditAndAcceptanceChecklist() throws IOException {
        String guide = readText("third-party-integration-guide.md");

        assertThat(guide)
            .contains("协议矩阵")
            .contains("数据流")
            .contains("不绕引擎")
            .contains("Idempotency-Key")
            .contains("X-MedKernel-Signature")
            .contains("NOT_CONNECTED")
            .contains("NOT_SYNCED")
            .contains("traceId")
            .contains("审计")
            .contains("验收清单");
        assertThat(guide)
            .as("FHIR 门面归 OPT-01，INTEG-02 只能写待接入边界，不能写不存在的 FHIR 运行 URL")
            .doesNotContain("/api/v1/fhir")
            .doesNotContain("/fhir/");
    }

    @Test
    void fieldMappingTemplateAndExampleMapExternalFieldsToStandardContextWithTerminologyEvidence()
        throws IOException {
        JsonNode template = readJson("field-mapping-template.json");
        JsonNode example = readJson("field-mapping-example-his-adt.json");

        assertThat(template.path("schemaVersion").asText()).isEqualTo("medkernel.integration.field-mapping.v1");
        assertThat(template.path("requiredColumns"))
            .extracting(JsonNode::asText)
            .contains(
                "externalField",
                "standardResource",
                "standardPath",
                "terminologyStrategy",
                "required",
                "sourceEvidence"
            );

        JsonNode mappings = example.path("mappings");
        assertThat(mappings.isArray()).isTrue();
        assertThat(mappings).isNotEmpty();

        Set<String> coveredResources = new LinkedHashSet<>();
        boolean hasTerminologyMapping = false;
        for (JsonNode mapping : mappings) {
            assertThat(mapping.path("externalField").asText()).isNotBlank();
            assertThat(mapping.path("standardPath").asText()).isNotBlank();
            assertThat(mapping.path("sourceEvidence").asBoolean()).isTrue();
            String standardResource = mapping.path("standardResource").asText();
            assertThat(STANDARD_CONTEXT_RESOURCES).contains(standardResource);
            coveredResources.add(standardResource);
            if (mapping.path("terminology").path("mappingRequired").asBoolean()) {
                hasTerminologyMapping = true;
                assertThat(mapping.path("terminology").path("ownerCard").asText()).isEqualTo("TERM-01");
            }
        }

        assertThat(coveredResources)
            .contains("Patient", "Encounter", "Observation");
        assertThat(hasTerminologyMapping)
            .as("至少一个外部编码字段必须声明经 TERM-01 归一")
            .isTrue();
    }

    private static JsonNode readJson(String fileName) throws IOException {
        return OBJECT_MAPPER.readTree(CONTRACT_DIR.resolve(fileName).toFile());
    }

    private static String readText(String fileName) throws IOException {
        return Files.readString(CONTRACT_DIR.resolve(fileName), StandardCharsets.UTF_8);
    }

    private static Set<String> integrationControllerEndpoints() {
        String basePath = classPath(IntegrationController.class);
        Set<String> endpoints = new TreeSet<>();
        for (Method method : IntegrationController.class.getDeclaredMethods()) {
            mappedEndpoint(method, basePath).ifPresent(endpoints::add);
        }
        return endpoints;
    }

    private static String classPath(Class<?> controller) {
        RequestMapping mapping = AnnotatedElementUtils.findMergedAnnotation(controller, RequestMapping.class);
        assertThat(mapping).as(controller.getName() + " 必须声明 @RequestMapping").isNotNull();
        return first(mapping.path(), mapping.value()).orElse("");
    }

    private static Optional<String> mappedEndpoint(Method method, String basePath) {
        GetMapping get = AnnotatedElementUtils.findMergedAnnotation(method, GetMapping.class);
        if (get != null) {
            return Optional.of("GET " + normalize(basePath, first(get.path(), get.value()).orElse("")));
        }
        PostMapping post = AnnotatedElementUtils.findMergedAnnotation(method, PostMapping.class);
        if (post != null) {
            return Optional.of("POST " + normalize(basePath, first(post.path(), post.value()).orElse("")));
        }
        PutMapping put = AnnotatedElementUtils.findMergedAnnotation(method, PutMapping.class);
        if (put != null) {
            return Optional.of("PUT " + normalize(basePath, first(put.path(), put.value()).orElse("")));
        }
        PatchMapping patch = AnnotatedElementUtils.findMergedAnnotation(method, PatchMapping.class);
        if (patch != null) {
            return Optional.of("PATCH " + normalize(basePath, first(patch.path(), patch.value()).orElse("")));
        }
        DeleteMapping delete = AnnotatedElementUtils.findMergedAnnotation(method, DeleteMapping.class);
        if (delete != null) {
            return Optional.of("DELETE " + normalize(basePath, first(delete.path(), delete.value()).orElse("")));
        }
        RequestMapping request = AnnotatedElementUtils.findMergedAnnotation(method, RequestMapping.class);
        if (request != null) {
            String httpMethod = Arrays.stream(request.method())
                .findFirst()
                .map(RequestMethod::name)
                .orElse("REQUEST");
            return Optional.of(httpMethod + " " + normalize(basePath, first(request.path(), request.value()).orElse("")));
        }
        return Optional.empty();
    }

    private static Optional<String> first(String[] path, String[] value) {
        if (path != null && path.length > 0) {
            return Optional.of(path[0]);
        }
        if (value != null && value.length > 0) {
            return Optional.of(value[0]);
        }
        return Optional.empty();
    }

    private static String normalize(String basePath, String subPath) {
        String path = (basePath + "/" + subPath).replaceAll("/{2,}", "/");
        return path.length() > 1 && path.endsWith("/") ? path.substring(0, path.length() - 1) : path;
    }
}
