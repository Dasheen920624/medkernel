package com.medkernel.architecture;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.lang.reflect.RecordComponent;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.Spliterators;
import java.util.TreeSet;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import org.junit.jupiter.api.Test;

import com.medkernel.engine.contract.DomainEventSchema;
import com.medkernel.engine.contract.DomainEventSchemaCatalog;

class DomainEventSchemaContractTest {
    private final JavaClasses classes = new ClassFileImporter()
        .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
        .importPackages("com.medkernel..");
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void everyProductionEventRecordMustHaveVersionedSchema() {
        Set<String> actual = productionEventRecords().stream()
            .map(Class::getName)
            .collect(Collectors.toCollection(TreeSet::new));
        Set<String> declared = DomainEventSchemaCatalog.schemas().stream()
            .map(DomainEventSchema::recordClassName)
            .collect(Collectors.toCollection(TreeSet::new));

        assertThat(declared).containsExactlyElementsOf(actual);
    }

    @Test
    void eventSchemaRequiredFieldsMustMatchRecordComponentsInOrder() throws IOException {
        List<String> violations = new java.util.ArrayList<>();
        for (DomainEventSchema schema : DomainEventSchemaCatalog.schemas()) {
            Class<?> eventClass = reflect(schema.recordClassName());
            List<String> recordFields = Arrays.stream(eventClass.getRecordComponents())
                .map(RecordComponent::getName)
                .toList();
            JsonNode root = objectMapper.readTree(contractPath(schema).toFile());
            List<String> required = StreamSupport.stream(
                    Spliterators.spliteratorUnknownSize(root.withArray("required").elements(), 0), false)
                .map(JsonNode::asText)
                .toList();
            Set<String> properties = new TreeSet<>();
            root.path("properties").fieldNames().forEachRemaining(properties::add);

            if (!required.equals(recordFields)) {
                violations.add(schema.schemaId() + " required 字段与 record 组件不一致: "
                    + required + " != " + recordFields);
            }
            if (!properties.containsAll(recordFields)) {
                violations.add(schema.schemaId() + " properties 缺少字段: " + recordFields);
            }
            if (!root.path("$id").asText().equals(schema.schemaId())) {
                violations.add(schema.schemaId() + " JSON $id 与目录声明不一致");
            }
            if (root.path("x-medkernel-version").asInt() != schema.version()) {
                violations.add(schema.schemaId() + " JSON 版本与目录声明不一致");
            }
            if (!schema.schemaId().endsWith(".v" + schema.version())) {
                violations.add(schema.schemaId() + " 必须以 .v" + schema.version() + " 结尾");
            }
        }

        assertThat(violations).isEmpty();
    }

    private List<Class<?>> productionEventRecords() {
        return classes.stream()
            .map(JavaClass::reflect)
            .filter(Class::isRecord)
            .filter(clazz -> clazz.getSimpleName().endsWith("Event"))
            .toList();
    }

    private static Class<?> reflect(String className) {
        try {
            return Class.forName(className);
        } catch (ClassNotFoundException ex) {
            throw new AssertionError("事件 schema 指向不存在的类 " + className, ex);
        }
    }

    private static Path contractPath(DomainEventSchema schema) {
        return Path.of("..").resolve(schema.contractFile()).normalize();
    }
}
