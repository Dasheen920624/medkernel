package com.medkernel.architecture;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import org.junit.jupiter.api.Test;
import org.springframework.data.relational.core.mapping.Table;

import com.medkernel.shared.architecture.DomainModule;
import com.medkernel.shared.architecture.DomainOwnershipCatalog;

class DomainOwnershipContractTest {
    private static final Pattern JAVA_PACKAGE = Pattern.compile("package\\s+([a-zA-Z0-9_.]+)\\s*;");
    private static final Pattern SQL_WRITE = Pattern.compile(
        "\\b(?:INSERT\\s+INTO|UPDATE|DELETE\\s+FROM)\\s+([a-zA-Z_][a-zA-Z0-9_]*)",
        Pattern.CASE_INSENSITIVE);
    private static final Pattern BLOCK_COMMENT = Pattern.compile("/\\*.*?\\*/", Pattern.DOTALL);
    private static final Pattern LINE_COMMENT = Pattern.compile("(?m)//.*$");

    private final JavaClasses classes = new ClassFileImporter()
        .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
        .importPackages("com.medkernel..");

    @Test
    void tableEntitiesMustHaveExactlyOneOwnerAndLiveInOwningPackage() {
        List<String> violations = new ArrayList<>();
        for (JavaClass javaClass : classes) {
            Class<?> reflected = javaClass.reflect();
            Table table = reflected.getAnnotation(Table.class);
            if (table == null) {
                continue;
            }
            String tableName = normalize(table.value());
            Optional<DomainModule> owner = DomainOwnershipCatalog.ownerOfTable(tableName);
            if (owner.isEmpty()) {
                violations.add(reflected.getName() + " 声明未登记 owner 的表 " + tableName);
                continue;
            }
            if (!owner.get().ownsPackage(reflected.getPackageName())) {
                violations.add(reflected.getName() + " 映射表 " + tableName
                    + "，但不在 owner 包 " + owner.get().id() + " 内");
            }
        }

        assertThat(violations).isEmpty();
    }

    @Test
    void tableOwnershipTokensMustBeUnique() {
        assertThat(DomainOwnershipCatalog.tableOwnershipTokens()).doesNotHaveDuplicates();
    }

    @Test
    void directSqlWritesMustStayInsideOwningModule() throws IOException {
        Path mainJava = Path.of("src/main/java");
        List<String> violations = new ArrayList<>();
        int checkedWrites = 0;

        try (var files = Files.walk(mainJava)) {
            for (Path file : files.filter(path -> path.toString().endsWith(".java")).toList()) {
                String content = Files.readString(file);
                String packageName = packageName(content);
                String source = stripComments(content);
                Matcher matcher = SQL_WRITE.matcher(source);
                while (matcher.find()) {
                    checkedWrites++;
                    String tableName = normalize(matcher.group(1));
                    Optional<DomainModule> owner = DomainOwnershipCatalog.ownerOfTable(tableName);
                    if (owner.isEmpty()) {
                        violations.add(file + " 直写未登记 owner 的表 " + tableName);
                        continue;
                    }
                    if (!owner.get().ownsPackage(packageName)) {
                        violations.add(file + " 直写 " + tableName + "，但 owner 是 " + owner.get().id());
                    }
                }
            }
        }

        assertThat(checkedWrites).isGreaterThan(0);
        assertThat(violations).isEmpty();
    }

    private static String packageName(String content) {
        Matcher matcher = JAVA_PACKAGE.matcher(content);
        assertThat(matcher.find()).as("Java 文件必须声明 package").isTrue();
        return matcher.group(1);
    }

    private static String stripComments(String content) {
        return LINE_COMMENT.matcher(BLOCK_COMMENT.matcher(content).replaceAll("")).replaceAll("");
    }

    private static String normalize(String tableName) {
        return tableName.toLowerCase(Locale.ROOT).trim();
    }
}
