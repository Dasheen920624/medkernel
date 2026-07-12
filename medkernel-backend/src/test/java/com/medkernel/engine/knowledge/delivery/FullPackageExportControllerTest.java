package com.medkernel.engine.knowledge.delivery;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.InputStream;
import java.lang.reflect.Method;

import jakarta.servlet.http.HttpServletResponse;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.mock.web.MockMultipartFile;

import com.medkernel.shared.api.error.ApiException;

/** 完整包签发、下载、医院预检与原子激活接口合同。 */
class FullPackageExportControllerTest {

    private final FullPackageExportService exports = mock(FullPackageExportService.class);
    private final FullPackagePreflightService preflights = mock(FullPackagePreflightService.class);
    private final FullPackageActivationService activations = mock(FullPackageActivationService.class);
    private final FullPackageExportController controller =
        new FullPackageExportController(exports, preflights, activations);

    @Test
    void signingRequiresPlatformPublishWhileMetadataAndDownloadRemainReadOnlyExports()
            throws Exception {
        assertThat(permissionOf("export", FullPackageExportController.ExportRequest.class))
            .isEqualTo("@perm.has('platform.publish')");
        assertThat(permissionOf("get", String.class))
            .isEqualTo("@perm.has('knowledge.export')");
        assertThat(permissionOf("download", String.class, HttpServletResponse.class))
            .isEqualTo("@perm.has('knowledge.export')");
        assertThat(permissionOf("preflight", String.class,
            org.springframework.web.multipart.MultipartFile.class))
            .isEqualTo("@perm.has('tenant.override')");
        assertThat(permissionOf("activate", String.class, String.class,
            FullPackageExportController.ActivationRequest.class))
            .isEqualTo("@perm.has('tenant.override')");
    }

    @Test
    void streamsRealMedicalPackageToHospitalScopedPreflight() throws Exception {
        byte[] bytes = "real-mkp-bytes".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        MockMultipartFile file = new MockMultipartFile(
            "file", "hospital-full.mkp", "application/zip", bytes);
        FullPackagePreflightPreview preview = mock(FullPackagePreflightPreview.class);
        when(preflights.preflight(any(InputStream.class), eq("hospital-A"))).thenReturn(preview);

        FullPackagePreflightPreview result = controller.preflight("hospital-A", file).data();

        ArgumentCaptor<InputStream> uploaded = ArgumentCaptor.forClass(InputStream.class);
        verify(preflights).preflight(uploaded.capture(), eq("hospital-A"));
        assertThat(uploaded.getValue().readAllBytes()).containsExactly(bytes);
        assertThat(result).isSameAs(preview);
    }

    @Test
    void rejectsEmptyMedicalPackageBeforeQuarantine() {
        MockMultipartFile empty = new MockMultipartFile(
            "file", "hospital-full.mkp", "application/zip", new byte[0]);

        assertThatThrownBy(() -> controller.preflight("hospital-A", empty))
            .isInstanceOf(ApiException.class)
            .hasMessageContaining("不能为空");

        verify(preflights, never()).preflight(any(), any());
    }

    @Test
    void activatesOnlyTheExactHospitalPreflightAndConfirmedPreview() {
        FullPackageActivation activation = mock(FullPackageActivation.class);
        when(activations.activate(any())).thenReturn(activation);

        FullPackageActivation result = controller.activate(
            "hospital-A",
            "preflight-A",
            new FullPackageExportController.ActivationRequest(
                "sm3:" + "a".repeat(64),
                "runtime-current-A"
            )
        ).data();

        ArgumentCaptor<FullPackageActivationCommand> command =
            ArgumentCaptor.forClass(FullPackageActivationCommand.class);
        verify(activations).activate(command.capture());
        assertThat(command.getValue())
            .extracting(
                FullPackageActivationCommand::hospitalId,
                FullPackageActivationCommand::preflightId,
                FullPackageActivationCommand::confirmedPreviewDigest,
                FullPackageActivationCommand::expectedCurrentReleaseId
            )
            .containsExactly(
                "hospital-A",
                "preflight-A",
                "sm3:" + "a".repeat(64),
                "runtime-current-A"
            );
        assertThat(result).isSameAs(activation);
    }

    private String permissionOf(String methodName, Class<?>... parameterTypes) throws Exception {
        Method method = FullPackageExportController.class.getMethod(methodName, parameterTypes);
        return method.getAnnotation(PreAuthorize.class).value();
    }
}
