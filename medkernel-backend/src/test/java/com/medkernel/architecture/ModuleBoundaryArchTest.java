package com.medkernel.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.library.dependencies.SlicesRuleDefinition;
import org.junit.jupiter.api.Test;

class ModuleBoundaryArchTest {
    private static final String ROOT_PACKAGE = "com.medkernel..";
    private static final String ENGINE_PACKAGE = "com.medkernel.engine..";
    private static final String SHARED_PACKAGE = "com.medkernel.shared..";
    private static final String BUSINESS_PACKAGE = "com.medkernel.compliance..";
    private static final String VERSIONING_PACKAGE = "com.medkernel.engine.versioning..";
    private static final String[] VERSIONING_CONSUMER_PACKAGES = {
        "com.medkernel.engine.pkg..",
        "com.medkernel.engine.knowledge..",
        "com.medkernel.engine.terminology..",
        "com.medkernel.engine.pathway..",
        "com.medkernel.engine.recommendation.."
    };

    private final JavaClasses classes = new ClassFileImporter()
        .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
        .importPackages(ROOT_PACKAGE);

    @Test
    void engineAndSharedMustNotDependOnBusinessPackages() {
        noClasses()
            .that().resideInAnyPackage(ENGINE_PACKAGE, SHARED_PACKAGE)
            .should().dependOnClassesThat().resideInAnyPackage(BUSINESS_PACKAGE)
            .because("SYS-02 要求依赖方向只能是业务包到引擎核心或 shared")
            .check(classes);
    }

    @Test
    void sharedKernelMustNotDependOnEnginePackages() {
        noClasses()
            .that().resideInAPackage(SHARED_PACKAGE)
            .should().dependOnClassesThat().resideInAPackage(ENGINE_PACKAGE)
            .because("shared 是最底层公共契约，不能反向依赖 engine")
            .check(classes);
    }

    @Test
    void versioningCoreMustNotDependOnItsConsumerDomains() {
        noClasses()
            .that().resideInAPackage(VERSIONING_PACKAGE)
            .should().dependOnClassesThat().resideInAnyPackage(VERSIONING_CONSUMER_PACKAGES)
            .because("统一版本与继承解析是底座，消费域只能单向依赖底座")
            .check(classes);
    }

    @Test
    void topLevelMedkernelPackagesMustBeFreeOfCycles() {
        SlicesRuleDefinition.slices()
            .matching("com.medkernel.(*)..")
            .should().beFreeOfCycles()
            .check(classes);
    }
}
