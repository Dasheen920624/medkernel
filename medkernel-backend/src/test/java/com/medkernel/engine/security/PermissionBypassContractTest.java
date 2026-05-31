package com.medkernel.engine.security;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PermissionBypassContractTest {

    @Test
    void evaluatorResolverAndAspectDoNotContainBuiltInAdminBypassBranches() throws IOException {
        String source = String.join("\n",
            Files.readString(sourcePath("PermissionEvaluator.java")),
            Files.readString(sourcePath("DataScopeResolver.java")),
            Files.readString(sourcePath("RequirePermissionAspect.java")));

        assertThat(source)
            .doesNotContain("PLATFORM_ADMIN")
            .doesNotContain("GROUP_ADMIN")
            .doesNotContain("ROLE_PLATFORM")
            .doesNotContain("ROLE_GROUP")
            .doesNotContain("hasRole")
            .doesNotContain("isAdmin")
            .doesNotContain("superadmin");
    }

    private Path sourcePath(String fileName) {
        return Path.of("src/main/java/com/medkernel/engine/security", fileName);
    }
}
