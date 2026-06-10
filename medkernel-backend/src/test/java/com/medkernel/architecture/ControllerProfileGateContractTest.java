package com.medkernel.architecture;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;

import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.RestController;

/**
 * 控制器 profile 裁剪契约。
 *
 * <p>回归 2026-06-10 真实部署缺陷：租户开通控制器标 {@code @Profile({"dev","test"})}，
 * govcloud 生产 profile 下接口直接 404，而前端菜单与五维权限均认为该能力存在。
 * 平台 API 面不得随构建 profile 增减——能力可用性由五维权限与配置中心运行时治理；
 * dev 专属种子器等非 API bean 不受本契约约束。
 */
class ControllerProfileGateContractTest {

    private final JavaClasses classes = new ClassFileImporter()
        .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
        .importPackages("com.medkernel..");

    @Test
    void restControllersMustNotBeProfileGated() {
        List<String> violations = new ArrayList<>();
        for (JavaClass javaClass : classes) {
            Class<?> reflected = javaClass.reflect();
            if (reflected.getAnnotation(RestController.class) == null) {
                continue;
            }
            if (reflected.getAnnotation(Profile.class) != null) {
                violations.add(reflected.getName()
                    + " 是 @RestController 却按 @Profile 裁剪，生产 profile 将出现菜单有、接口 404 的契约断裂");
            }
        }

        assertThat(violations).isEmpty();
    }
}
