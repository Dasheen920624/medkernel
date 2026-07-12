package com.medkernel.architecture;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import org.junit.jupiter.api.Test;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

import com.medkernel.engine.security.PermissionCode;
import com.medkernel.engine.contract.ServiceContract;
import com.medkernel.engine.contract.ServiceContractCatalog;
import com.medkernel.shared.audit.AuditAction;

class ServiceContractGovernanceTest {
    private static final Pattern PERMISSION_CODE = Pattern.compile("@perm\\.has\\('([^']+)'\\)");

    private final JavaClasses classes = new ClassFileImporter()
        .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
        .importPackages("com.medkernel..");

    @Test
    void everyApiControllerMustBeDeclaredInServiceContractCatalog() {
        Set<String> actualControllers = apiControllers().stream()
            .map(Class::getName)
            .collect(Collectors.toCollection(TreeSet::new));
        Set<String> declaredControllers = ServiceContractCatalog.contracts().stream()
            .map(ServiceContract::controllerClassName)
            .collect(Collectors.toCollection(TreeSet::new));

        assertThat(declaredControllers).containsExactlyElementsOf(actualControllers);
    }

    @Test
    void everyApiEndpointMustDeclareSecurityAndCatalogedPermissions() {
        List<String> violations = new ArrayList<>();

        for (Class<?> controller : apiControllers()) {
            ServiceContract contract = ServiceContractCatalog.contractOfController(controller.getName())
                .orElseThrow(() -> new AssertionError("服务契约缺少控制器 " + controller.getName()));
            String basePath = classPath(controller);
            assertThat(contract.basePath()).isEqualTo(basePath);

            for (Method method : controller.getDeclaredMethods()) {
                Optional<MappedEndpoint> endpoint = mappedEndpoint(method, basePath);
                if (endpoint.isEmpty()) {
                    continue;
                }
                String endpointKey = endpoint.get().key();
                PreAuthorize auth = Optional.ofNullable(
                        AnnotatedElementUtils.findMergedAnnotation(method, PreAuthorize.class))
                    .orElse(AnnotatedElementUtils.findMergedAnnotation(controller, PreAuthorize.class));

                if (!contract.isPublicEndpoint(endpointKey) && auth == null) {
                    violations.add(endpointKey + " 缺少 @PreAuthorize 且不在公开契约内");
                    continue;
                }
                if (auth != null) {
                    Matcher matcher = PERMISSION_CODE.matcher(auth.value());
                    while (matcher.find()) {
                        String code = matcher.group(1);
                        if (PermissionCode.fromCode(code).isEmpty()) {
                            violations.add(endpointKey + " 引用未登记权限码 " + code);
                        }
                        if (!contract.declaresPermission(code)) {
                            violations.add(endpointKey + " 使用权限 " + code + " 但服务契约未声明");
                        }
                    }
                }
                if (!"GET".equals(endpoint.get().httpMethod())
                    && !contract.isPublicEndpoint(endpointKey)
                    && contract.auditPoints().isEmpty()) {
                    violations.add(endpointKey + " 是非 GET 服务入口但未声明审计点");
                }
            }
        }

        assertThat(violations).isEmpty();
    }

    @Test
    void serviceContractOpenApiPathsMustCoverEveryControllerBasePath() {
        Set<String> paths = new TreeSet<>(ServiceContractCatalog.openApiPaths());
        Set<String> expected = apiControllers().stream()
            .flatMap(controller -> classPaths(controller).stream())
            .map(path -> path + "/**")
            .collect(Collectors.toCollection(TreeSet::new));

        assertThat(paths).containsExactlyElementsOf(expected);
    }

    @Test
    void declarativeAssetControllerMustDeclarePermissionsOpenApiAndAuditContract() {
        ServiceContract contract = ServiceContractCatalog.contractOfController(
                "com.medkernel.engine.authoring.DeclarativeAssetController")
            .orElseThrow(() -> new AssertionError("声明式资产控制器缺少服务契约"));

        assertThat(contract.id()).isEqualTo("authoring-declarative-assets");
        assertThat(contract.basePath()).isEqualTo("/api/v1/engine/authoring/declarative-assets");
        assertThat(contract.openApiPaths())
            .containsExactly("/api/v1/engine/authoring/declarative-assets/**");
        assertThat(contract.permissions())
            .extracting(permission -> permission.code())
            .containsExactlyInAnyOrder("asset.read", "asset.write");
        assertThat(contract.auditPoints())
            .extracting(audit -> audit.action())
            .contains(AuditAction.CREATE, AuditAction.UPDATE);
    }

    @Test
    void releaseGovernanceContractUsesImpactAssessmentLanguage() {
        ServiceContract contract = ServiceContractCatalog.contractOfController(
                "com.medkernel.engine.versioning.ReleaseGovernanceController")
            .orElseThrow(() -> new AssertionError("发布治理控制器缺少服务契约"));

        assertThat(contract.title()).isEqualTo("发布影响评估与灰度治理服务");
        assertThat(contract.auditPoints())
            .extracting(audit -> audit.purpose())
            .contains("记录发布前影响评估与灰度观测");
    }

    @Test
    void knowledgePackageContractCoversSignedRoundTripAndHospitalActivation() {
        ServiceContract contract = ServiceContractCatalog.contractOfController(
                "com.medkernel.engine.knowledge.delivery.FullPackageExportController")
            .orElseThrow(() -> new AssertionError("完整医疗资源包控制器缺少服务契约"));

        assertThat(contract.title()).isEqualTo("完整医疗资源包签发与医院导入服务");
        assertThat(contract.permissions())
            .extracting(permission -> permission.code())
            .containsExactlyInAnyOrder("platform.publish", "knowledge.export", "tenant.override");
        assertThat(contract.auditPoints())
            .extracting(audit -> audit.purpose())
            .contains(
                "签发并登记完整医疗资源包",
                "记录完整医疗资源包固定根验签只读预检",
                "原子物化并激活完整医疗资源包");
    }

    @Test
    void controllersMustExposeSingleCanonicalBasePath() {
        assertThat(apiControllers())
            .allSatisfy(controller -> assertThat(classPaths(controller))
                .as(controller.getName() + " 只能声明一个 canonical base path")
                .hasSize(1));
    }

    @Test
    void serviceContractsMustNotRegisterLegacyTenantPlatformRoutes() {
        assertThat(ServiceContractCatalog.openApiPaths())
            .doesNotContain(
                "/api/v1/platform/branding/**",
                "/api/v1/platform/success/lifecycle/**",
                "/api/v1/tenant/org-units/**",
                "/api/v1/engine/events/**",
                "/api/v1/engine/authoring/fragments/**",
                "/api/v1/clinical/mpi/**");
    }

    private List<Class<?>> apiControllers() {
        return classes.stream()
            .filter(javaClass -> javaClass.isAnnotatedWith(RestController.class))
            .map(JavaClass::reflect)
            .filter(controller -> classPath(controller).startsWith("/api/v1"))
            .toList();
    }

    private String classPath(Class<?> controller) {
        return classPaths(controller).stream().findFirst().orElse("");
    }

    private List<String> classPaths(Class<?> controller) {
        RequestMapping mapping = AnnotatedElementUtils.findMergedAnnotation(controller, RequestMapping.class);
        assertThat(mapping).as(controller.getName() + " 必须声明 @RequestMapping").isNotNull();
        List<String> paths = new ArrayList<>();
        paths.addAll(Arrays.asList(mapping.path()));
        paths.addAll(Arrays.asList(mapping.value()));
        return paths.stream()
            .filter(path -> path != null && !path.isBlank())
            .distinct()
            .toList();
    }

    private Optional<MappedEndpoint> mappedEndpoint(Method method, String basePath) {
        GetMapping get = AnnotatedElementUtils.findMergedAnnotation(method, GetMapping.class);
        if (get != null) {
            return Optional.of(new MappedEndpoint("GET", normalize(basePath, first(get.path(), get.value()).orElse(""))));
        }
        PostMapping post = AnnotatedElementUtils.findMergedAnnotation(method, PostMapping.class);
        if (post != null) {
            return Optional.of(new MappedEndpoint("POST", normalize(basePath, first(post.path(), post.value()).orElse(""))));
        }
        PutMapping put = AnnotatedElementUtils.findMergedAnnotation(method, PutMapping.class);
        if (put != null) {
            return Optional.of(new MappedEndpoint("PUT", normalize(basePath, first(put.path(), put.value()).orElse(""))));
        }
        PatchMapping patch = AnnotatedElementUtils.findMergedAnnotation(method, PatchMapping.class);
        if (patch != null) {
            return Optional.of(new MappedEndpoint("PATCH", normalize(basePath, first(patch.path(), patch.value()).orElse(""))));
        }
        DeleteMapping delete = AnnotatedElementUtils.findMergedAnnotation(method, DeleteMapping.class);
        if (delete != null) {
            return Optional.of(new MappedEndpoint("DELETE", normalize(basePath, first(delete.path(), delete.value()).orElse(""))));
        }
        RequestMapping request = AnnotatedElementUtils.findMergedAnnotation(method, RequestMapping.class);
        if (request != null) {
            String httpMethod = Arrays.stream(request.method())
                .findFirst()
                .map(RequestMethod::name)
                .orElse("REQUEST");
            return Optional.of(new MappedEndpoint(httpMethod, normalize(basePath, first(request.path(), request.value()).orElse(""))));
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

    private record MappedEndpoint(String httpMethod, String path) {
        String key() {
            return httpMethod + " " + path;
        }
    }
}
