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
import com.medkernel.engine.integration.fhir.FhirFacadeController;
import com.medkernel.engine.integration.runtime.ThirdPartyKnowledgeRuntimeController;
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

    @Test
    void integrationOpenApiPathSnapshotMirrorsControllerWithoutGhostEndpoints() throws Exception {
        JsonNode document = readJson("integration-openapi.paths.json");

        assertThat(document.path("basePath").asText()).isEqualTo("/api/v1/engine/integration");
        assertThat(document.path("fhirFacade").path("status").asText()).isEqualTo("RUNTIME_AVAILABLE");
        assertThat(values(document.path("fhirFacade").path("documentedRuntimePaths")))
            .as("OPT-01 FHIR 运行门面文档必须与控制器真实端点一致")
            .containsExactlyElementsOf(fhirFacadeControllerEndpoints());
        assertThat(document.path("fhirFacade").path("supportedCreates"))
            .extracting(JsonNode::asText)
            .contains(
                "Patient",
                "Encounter",
                "Condition",
                "Observation",
                "Medication",
                "Procedure",
                "CarePlan",
                "ServiceRequest",
                "DiagnosticReport",
                "DocumentReference"
            );
        assertThat(document.path("fhirFacade").path("degradation").path("disconnectedStatus").asText())
            .isEqualTo("NOT_CONNECTED");
        assertThat(document.path("knowledgeRuntime").path("status").asText()).isEqualTo("RUNTIME_AVAILABLE");
        assertThat(document.path("knowledgeRuntime").path("contractVersion").asText()).isEqualTo("v1");
        assertThat(values(document.path("knowledgeRuntime").path("documentedRuntimePaths")))
            .as("6.6 第三方知识运行时文档必须与控制器真实端点一致")
            .containsExactlyElementsOf(thirdPartyKnowledgeRuntimeControllerEndpoints());
        assertThat(document.path("knowledgeRuntime").path("fieldContract").asText())
            .isEqualTo("/api/v1/engine/integration/data-contract");

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
            .contains("验收清单")
            .contains("/api/v1/engine/integration/fhir/{version}/metadata")
            .contains("/api/v1/engine/integration/fhir/{version}/{resourceType}")
            .contains("/api/v1/engine/integration/fhir/{version}/{resourceType}/{id}")
            .contains("FHIR_PHYSICIAN_CONFIRMATION")
            .contains("Bundle")
            .contains("OperationOutcome")
            .contains("/api/v1/engine/integration/knowledge-runtime/runtime-release/current")
            .contains("/api/v1/engine/integration/knowledge-runtime/context-snapshots")
            .contains("认证服务机构和医院")
            .contains("完整不可变版本明细")
            .doesNotContain("effective-package")
            .doesNotContain("packages/{packageId}");
        assertThat(guide)
            .as("FHIR 门面挂 INTEG-01 总线，不得回流旧式裸 /api/v1/fhir 幽灵端点")
            .doesNotContain("/api/v1/fhir");
    }

    @Test
    void fieldMappingTemplateAndExampleUseTheExactRuntimeAdapterSyntax()
        throws IOException {
        JsonNode template = readJson("field-mapping-template.json");
        JsonNode example = readJson("field-mapping-example-his-adt.json");

        assertThat(template.path("baseUrl").asText()).isNotBlank();
        assertThat(template.path("healthPath").asText()).startsWith("/");
        assertThat(template.path("outboundPath").asText()).startsWith("/");
        assertThat(template.path("connectTimeoutMs").asInt()).isPositive();
        assertThat(template.path("requestTimeoutMs").asInt()).isPositive();

        JsonNode mappings = example.path("fieldMappings");
        assertThat(mappings.isArray()).isTrue();
        assertThat(mappings).isNotEmpty();

        Set<String> coveredRoots = new LinkedHashSet<>();
        boolean hasTerminologyMapping = false;
        boolean hasTenantExtension = false;
        for (JsonNode mapping : mappings) {
            String sourcePath = mapping.path("sourcePath").asText();
            String targetPath = mapping.path("targetPath").asText();
            assertThat(sourcePath).startsWith("/");
            assertThat(targetPath).startsWith("/");
            coveredRoots.add(targetPath.substring(1).split("/")[0]);
            assertThat(mapping.has("termMappingId")).isFalse();
            boolean hasDictionary = mapping.hasNonNull("targetDictionaryKey");
            boolean hasCategory = mapping.hasNonNull("category");
            assertThat(hasDictionary).isEqualTo(hasCategory);
            if (hasDictionary) {
                hasTerminologyMapping = true;
                assertThat(mapping.path("targetDictionaryKey").asText()).isNotBlank();
                assertThat(mapping.path("category").asText()).isNotBlank();
            }
            if (targetPath.startsWith("/extensions/local/")) {
                hasTenantExtension = true;
            }
        }

        assertThat(coveredRoots).contains("patient", "admission", "diagnoses", "results", "extensions");
        assertThat(hasTerminologyMapping)
            .as("至少一个外部编码字段必须声明按发布文件精确归一")
            .isTrue();
        assertThat(hasTenantExtension)
            .as("样例必须覆盖有真实运行落点的院内扩展字段")
            .isTrue();
    }

    private static JsonNode readJson(String fileName) throws IOException {
        return OBJECT_MAPPER.readTree(CONTRACT_DIR.resolve(fileName).toFile());
    }

    private static String readText(String fileName) throws IOException {
        return Files.readString(CONTRACT_DIR.resolve(fileName), StandardCharsets.UTF_8);
    }

    private static Set<String> integrationControllerEndpoints() {
        return controllerEndpoints(IntegrationController.class);
    }

    private static Set<String> fhirFacadeControllerEndpoints() {
        return controllerEndpoints(FhirFacadeController.class);
    }

    private static Set<String> thirdPartyKnowledgeRuntimeControllerEndpoints() {
        return controllerEndpoints(ThirdPartyKnowledgeRuntimeController.class);
    }

    private static Set<String> controllerEndpoints(Class<?> controller) {
        String basePath = classPath(controller);
        Set<String> endpoints = new TreeSet<>();
        for (Method method : controller.getDeclaredMethods()) {
            mappedEndpoint(method, basePath).ifPresent(endpoints::add);
        }
        return endpoints;
    }

    private static Set<String> values(JsonNode array) {
        Set<String> values = new TreeSet<>();
        array.forEach(node -> values.add(node.asText()));
        return values;
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
