package com.medkernel.engine.knowledge.delivery;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Method;

import jakarta.servlet.http.HttpServletResponse;

import org.junit.jupiter.api.Test;
import org.springframework.security.access.prepost.PreAuthorize;

/** 完整包签发与只读下载权限边界合同。 */
class FullPackageExportControllerTest {

    @Test
    void signingRequiresPlatformPublishWhileMetadataAndDownloadRemainReadOnlyExports()
            throws Exception {
        assertThat(permissionOf("export", FullPackageExportController.ExportRequest.class))
            .isEqualTo("@perm.has('platform.publish')");
        assertThat(permissionOf("get", String.class))
            .isEqualTo("@perm.has('knowledge.export')");
        assertThat(permissionOf("download", String.class, HttpServletResponse.class))
            .isEqualTo("@perm.has('knowledge.export')");
    }

    private String permissionOf(String methodName, Class<?>... parameterTypes) throws Exception {
        Method method = FullPackageExportController.class.getMethod(methodName, parameterTypes);
        return method.getAnnotation(PreAuthorize.class).value();
    }
}
