package com.medkernel.engine.context;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.lang.reflect.Method;
import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;

import com.medkernel.engine.versioning.AssetVersion;
import com.medkernel.engine.versioning.AssetVersionOverridePolicy;
import com.medkernel.engine.versioning.AssetVersionSafetyPolicy;
import com.medkernel.engine.versioning.AssetVersionStatus;
import com.medkernel.engine.versioning.VersionedAssetType;

class ContextFieldCatalogControllerTest {

    private final ContextFieldCatalogService service = mock(ContextFieldCatalogService.class);
    private final ContextFieldCatalogDraftService drafts = mock(ContextFieldCatalogDraftService.class);
    private final ContextFieldCatalogController controller =
        new ContextFieldCatalogController(service, drafts);

    @Test
    void listCreateUpdateDeleteDelegateToService() {
        ContextFieldDescriptor descriptor = descriptor("f1");
        ContextFieldCatalogUpsertRequest request = request();
        AssetVersion draft = draft();
        when(service.query("Observation", "血糖")).thenReturn(List.of(descriptor));
        when(service.create(request)).thenReturn(descriptor);
        when(service.update("f1", request)).thenReturn(descriptor);
        when(drafts.snapshotDraft()).thenReturn(draft);

        assertThat(controller.list("Observation", "血糖").data()).containsExactly(descriptor);
        assertThat(controller.snapshotDraft().data()).isEqualTo(draft);
        assertThat(controller.create(request).data()).isEqualTo(descriptor);
        assertThat(controller.update("f1", request).data()).isEqualTo(descriptor);
        assertThat(controller.delete("f1").data()).isTrue();

        verify(service).query("Observation", "血糖");
        verify(drafts).snapshotDraft();
        verify(service).create(request);
        verify(service).update("f1", request);
        verify(service).delete("f1");
    }

    @Test
    void declaresCustomerFieldCatalogMaintenanceRoutes() throws Exception {
        assertThat(mapping("list", String.class, String.class, GetMapping.class).value()).isEmpty();
        assertThat(mapping("create", ContextFieldCatalogUpsertRequest.class, PostMapping.class).value()).isEmpty();
        assertThat(mapping("update", String.class, ContextFieldCatalogUpsertRequest.class, PutMapping.class).value())
            .containsExactly("/{fieldId}");
        assertThat(mapping("delete", String.class, DeleteMapping.class).value()).containsExactly("/{fieldId}");
        assertThat(mapping("snapshotDraft", PostMapping.class).value()).containsExactly("/drafts");
    }

    @Test
    void listContractDoesNotExposeLegacyPackageSelector() {
        assertThat(ContextFieldCatalogController.class.getMethods())
            .filteredOn(method -> method.getName().equals("list"))
            .singleElement()
            .satisfies(method -> assertThat(method.getParameters())
                .extracting(java.lang.reflect.Parameter::getName)
                .doesNotContain("packageVersion", "packageId", "packageCode"));
    }

    private static <A extends java.lang.annotation.Annotation> A mapping(
            String methodName, Class<?> firstArg, Class<A> annotation) throws Exception {
        Method method = ContextFieldCatalogController.class.getMethod(methodName, firstArg);
        return method.getAnnotation(annotation);
    }

    private static <A extends java.lang.annotation.Annotation> A mapping(
            String methodName, Class<A> annotation) throws Exception {
        Method method = ContextFieldCatalogController.class.getMethod(methodName);
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

    private static AssetVersion draft() {
        Instant now = Instant.parse("2026-06-23T00:00:00Z");
        return new AssetVersion(
            1L, "av-field-catalog", "tenant-A", VersionedAssetType.FIELD_CATALOG,
            "FIELD.CATALOG.CLINICAL_CONTEXT", "V1", "/tenant-A", "ALL",
            "a".repeat(64), AssetVersionSafetyPolicy.NORMAL, AssetVersionOverridePolicy.FREE,
            AssetVersionStatus.DRAFT, "draft:av-field-catalog", "field-catalog:working-directory",
            null, null, now, "u-1", now, "u-1", "trace-1");
    }
}
