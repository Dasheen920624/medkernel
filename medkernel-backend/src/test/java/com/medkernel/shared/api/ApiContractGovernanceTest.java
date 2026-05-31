package com.medkernel.shared.api;

import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import jakarta.validation.Valid;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.type.filter.AnnotationTypeFilter;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * BASE-03 · Controller 入参治理测试。
 */
class ApiContractGovernanceTest {

    @Test
    void requestBodyParametersUseValidatedRecordDtos() throws Exception {
        List<String> violations = new ArrayList<>();
        for (Class<?> controller : restControllers()) {
            for (Method method : controller.getDeclaredMethods()) {
                for (Parameter parameter : method.getParameters()) {
                    if (!hasAnnotation(parameter, RequestBody.class)) {
                        continue;
                    }
                    Class<?> type = parameter.getType();
                    if (isForbiddenBodyType(type)) {
                        violations.add(methodRef(controller, method) + " 使用了裸请求体类型 " + type.getName());
                    }
                    if (!type.isRecord()) {
                        violations.add(methodRef(controller, method) + " 请求体不是 Record DTO: " + type.getName());
                    }
                    if (!hasValidationAnnotation(parameter)) {
                        violations.add(methodRef(controller, method) + " 请求体缺少 @Valid/@Validated: " + type.getName());
                    }
                }
            }
        }

        assertThat(violations).isEmpty();
    }

    private List<Class<?>> restControllers() throws ClassNotFoundException {
        ClassPathScanningCandidateComponentProvider scanner =
            new ClassPathScanningCandidateComponentProvider(false);
        scanner.addIncludeFilter(new AnnotationTypeFilter(RestController.class));
        List<Class<?>> controllers = new ArrayList<>();
        for (var bean : scanner.findCandidateComponents("com.medkernel")) {
            String className = bean.getBeanClassName();
            if (className != null && isProductionController(className)) {
                controllers.add(Class.forName(className));
            }
        }
        return controllers;
    }

    private boolean isProductionController(String className) {
        return !className.contains("Test$") && !className.endsWith("Test");
    }

    private boolean isForbiddenBodyType(Class<?> type) {
        return type == Object.class
            || type == String.class
            || Map.class.isAssignableFrom(type)
            || "com.fasterxml.jackson.databind.JsonNode".equals(type.getName());
    }

    private boolean hasValidationAnnotation(Parameter parameter) {
        return hasAnnotation(parameter, Valid.class) || hasAnnotation(parameter, Validated.class);
    }

    private boolean hasAnnotation(Parameter parameter, Class<? extends java.lang.annotation.Annotation> annotation) {
        return parameter.getAnnotation(annotation) != null;
    }

    private String methodRef(Class<?> controller, Method method) {
        return controller.getName() + "#" + method.getName();
    }
}
