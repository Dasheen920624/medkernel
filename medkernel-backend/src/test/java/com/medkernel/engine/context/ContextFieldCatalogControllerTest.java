package com.medkernel.engine.context;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.lang.reflect.Method;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;

class ContextFieldCatalogControllerTest {

    private final ContextFieldCatalogService service = mock(ContextFieldCatalogService.class);
    private final ContextFieldCatalogController controller = new ContextFieldCatalogController(service);

    @Test
    void listCreateUpdateDeleteDelegateToService() {
        ContextFieldDescriptor descriptor = descriptor("f1");
        ContextFieldCatalogUpsertRequest request = request();
        when(service.query("Observation", "血糖", "pkg-2026.06")).thenReturn(List.of(descriptor));
        when(service.create(request)).thenReturn(descriptor);
        when(service.update("f1", request)).thenReturn(descriptor);

        assertThat(controller.list("Observation", "血糖", "pkg-2026.06").data()).containsExactly(descriptor);
        assertThat(controller.create(request).data()).isEqualTo(descriptor);
        assertThat(controller.update("f1", request).data()).isEqualTo(descriptor);
        assertThat(controller.delete("f1").data()).isTrue();

        verify(service).query("Observation", "血糖", "pkg-2026.06");
        verify(service).create(request);
        verify(service).update("f1", request);
        verify(service).delete("f1");
    }

    @Test
    void declaresCustomerFieldCatalogMaintenanceRoutes() throws Exception {
        assertThat(mapping("list", String.class, String.class, String.class, GetMapping.class).value()).isEmpty();
        assertThat(mapping("create", ContextFieldCatalogUpsertRequest.class, PostMapping.class).value()).isEmpty();
        assertThat(mapping("update", String.class, ContextFieldCatalogUpsertRequest.class, PutMapping.class).value())
            .containsExactly("/{fieldId}");
        assertThat(mapping("delete", String.class, DeleteMapping.class).value()).containsExactly("/{fieldId}");
    }

    private static <A extends java.lang.annotation.Annotation> A mapping(
            String methodName, Class<?> firstArg, Class<A> annotation) throws Exception {
        Method method = ContextFieldCatalogController.class.getMethod(methodName, firstArg);
        return method.getAnnotation(annotation);
    }

    private static <A extends java.lang.annotation.Annotation> A mapping(
            String methodName, Class<?> firstArg, Class<?> secondArg, Class<A> annotation) throws Exception {
        Method method = ContextFieldCatalogController.class.getMethod(methodName, firstArg, secondArg);
        return method.getAnnotation(annotation);
    }

    private static <A extends java.lang.annotation.Annotation> A mapping(
            String methodName, Class<?> firstArg, Class<?> secondArg, Class<?> thirdArg,
            Class<A> annotation) throws Exception {
        Method method = ContextFieldCatalogController.class.getMethod(methodName, firstArg, secondArg, thirdArg);
        return method.getAnnotation(annotation);
    }

    private static ContextFieldDescriptor descriptor(String fieldId) {
        return new ContextFieldDescriptor(
            "检验检查", "检验/体征结果", "Observation", "observations[].code",
            "检验编码", "code", null, "LOINC", "说明", "TENANT", fieldId, false);
    }

    private static ContextFieldCatalogUpsertRequest request() {
        return new ContextFieldCatalogUpsertRequest(
            "检验检查", "检验/体征结果", "Observation", "observations[].code",
            "检验编码", "code", null, "LOINC", "说明");
    }
}
