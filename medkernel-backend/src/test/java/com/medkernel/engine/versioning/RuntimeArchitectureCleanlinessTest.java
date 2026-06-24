package com.medkernel.engine.versioning;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.medkernel.shared.context.OrgScope;

import static org.assertj.core.api.Assertions.assertThat;

class RuntimeArchitectureCleanlinessTest {

    private static final Path REMOVED_RUNTIME_SELECTOR_GUARD =
        Path.of("src/main/java/com/medkernel/engine/versioning/RemovedRuntimeSelectorFields.java");

    @Test
    void productionRuntimeCodeDoesNotDependOnRetiredPackageNamespace() throws IOException {
        Path sourceRoot = Path.of("src/main/java/com/medkernel");
        Path retiredPackageRoot = Path.of("src/main/java/com/medkernel/engine/pkg");

        List<Path> offenders;
        try (var files = Files.walk(sourceRoot)) {
            offenders = files
                .filter(Files::isRegularFile)
                .filter(path -> !path.startsWith(retiredPackageRoot))
                .filter(path -> {
                    try {
                        return Files.readString(path).contains("com.medkernel.engine.pkg.");
                    } catch (IOException ex) {
                        throw new IllegalStateException("读取源码失败：" + path, ex);
                    }
                })
                .toList();
        }

        assertThat(offenders)
            .as("旧发布容器命名空间只能留在待删除目录自身，不能再被新运行链路依赖")
            .isEmpty();
    }

    @Test
    void retiredPackageContainerProductionSourceTreeIsRemoved() {
        assertThat(Path.of("src/main/java/com/medkernel/engine/pkg"))
            .as("旧发布容器目录必须整块移除，不能继续作为可扫描 Spring 组件存在")
            .doesNotExist();
    }

    @Test
    void productionSourcesDoNotExposeRetiredPackageApiRoutes() throws IOException {
        Path sourceRoot = Path.of("src/main/java");

        List<Path> offenders;
        try (var files = Files.walk(sourceRoot)) {
            offenders = files
                .filter(Files::isRegularFile)
                .filter(path -> {
                    try {
                        return Files.readString(path).contains("/api/v1/engine/pkg");
                    } catch (IOException ex) {
                        throw new IllegalStateException("读取源码失败：" + path, ex);
                    }
                })
                .toList();
        }

        assertThat(offenders)
            .as("旧 /api/v1/engine/pkg 发布容器接口必须由平台标准版本、机构生效版本和离线交付文件接口替代")
            .isEmpty();
    }

    @Test
    void productionSourcesDoNotUseRetiredRuntimePackageSelectionTerms() throws IOException {
        Path sourceRoot = Path.of("src/main/java");
        List<String> retiredTerms = List.of(
            "PackageConsistency",
            "packageId",
            "packageCode",
            "packageVersion",
            "package_id",
            "package_code",
            "package_version");

        List<String> offenders;
        try (var files = Files.walk(sourceRoot)) {
            offenders = files
                .filter(Files::isRegularFile)
                .filter(path -> !path.equals(REMOVED_RUNTIME_SELECTOR_GUARD))
                .flatMap(path -> retiredTerms.stream()
                    .filter(term -> sourceContains(path, term))
                    .map(term -> path + " 包含旧运行定位词 " + term))
                .toList();
        }

        assertThat(offenders)
            .as("生产代码不得继续暴露旧发布容器选择词；运行事实必须来自当前机构生效版本")
            .isEmpty();
    }

    @Test
    void productionSourcesDoNotHideRetiredRuntimePackageSelectionTermsWithStringConcatenation() throws IOException {
        Path sourceRoot = Path.of("src/main/java");
        List<String> hiddenTerms = List.of(
            "\"package\" + \"Id\"",
            "\"package\" + \"Version\"",
            "\"knowledge\" + \"PackageId\"",
            "\"release\" + \"PackageId\"");

        List<String> offenders;
        try (var files = Files.walk(sourceRoot)) {
            offenders = files
                .filter(Files::isRegularFile)
                .flatMap(path -> hiddenTerms.stream()
                    .filter(term -> sourceContains(path, term))
                    .map(term -> path + " 用字符串拼接隐藏旧运行定位词 " + term))
                .toList();
        }

        assertThat(offenders)
            .as("禁止通过字符串拼接绕过旧发布容器字段扫描；需要拒绝旧输入时只能使用专门护栏")
            .isEmpty();
    }

    @Test
    void orgScopeDoesNotExposeRetiredSevenArgumentCompatibilityConstructor() {
        assertThat(OrgScope.class.getDeclaredConstructors())
            .as("组织上下文必须显式携带 wardId 槽位；不得保留旧 7 参兼容构造器")
            .noneMatch(constructor -> constructor.getParameterCount() == 7);
    }

    private static boolean sourceContains(Path path, String term) {
        try {
            return Files.readString(path).contains(term);
        } catch (IOException ex) {
            throw new IllegalStateException("读取源码失败：" + path, ex);
        }
    }
}
