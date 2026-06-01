package com.medkernel.engine.security.bootstrap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import java.lang.reflect.Constructor;
import java.time.Duration;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.mock.env.MockEnvironment;

import com.medkernel.engine.security.PlatformCredentialRepository;
import com.medkernel.engine.security.UserRoleAssignmentRepository;

/**
 * 启动期种子只登记部署 token，不创建生产账号，也不暴露明文 token。
 */
@ExtendWith(OutputCaptureExtension.class)
class BootstrapInitTokenSeederTest {

    @Test
    void skipsWhenDeploymentTokenMissing() throws Exception {
        BootstrapInitTokenService service = mock(BootstrapInitTokenService.class);
        BootstrapInitTokenSeeder seeder = new BootstrapInitTokenSeeder(new MockEnvironment(), service);

        seeder.run(mock(ApplicationArguments.class));

        verifyNoInteractions(service);
    }

    @Test
    void skipsWhenDeploymentTokenBlank() throws Exception {
        BootstrapInitTokenService service = mock(BootstrapInitTokenService.class);
        MockEnvironment environment = new MockEnvironment()
            .withProperty("MEDKERNEL_BOOTSTRAP_INIT_TOKEN", " ");
        BootstrapInitTokenSeeder seeder = new BootstrapInitTokenSeeder(environment, service);

        seeder.run(mock(ApplicationArguments.class));

        verifyNoInteractions(service);
    }

    @Test
    void registersExplicitDeploymentTokenWithConfiguredTtlAndDoesNotLogRawToken(CapturedOutput output) throws Exception {
        BootstrapInitTokenService service = mock(BootstrapInitTokenService.class);
        MockEnvironment environment = new MockEnvironment()
            .withProperty("MEDKERNEL_BOOTSTRAP_INIT_TOKEN", "mk-prod-init-token")
            .withProperty("MEDKERNEL_BOOTSTRAP_INIT_TOKEN_TTL_MINUTES", "45");
        BootstrapInitTokenSeeder seeder = new BootstrapInitTokenSeeder(environment, service);

        seeder.run(mock(ApplicationArguments.class));

        verify(service).registerDeploymentToken(
            "mk-prod-init-token", Duration.ofMinutes(45), "bootstrap-seeder", "startup");
        assertThat(output).doesNotContain("mk-prod-init-token");
    }

    @Test
    void seederDoesNotDependOnCredentialOrRoleRepositories() {
        Constructor<?> constructor = BootstrapInitTokenSeeder.class.getDeclaredConstructors()[0];

        assertThat(constructor.getParameterTypes())
            .doesNotContain(PlatformCredentialRepository.class, UserRoleAssignmentRepository.class);
    }
}
