package com.medkernel.engine.context;

import java.lang.reflect.RecordComponent;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.medkernel.engine.context.canonical.CanonicalObservation;
import com.medkernel.engine.context.canonical.CanonicalPatient;

/**
 * 把标准上下文快照桥接为规则、路径、CDSS 共同可消费的事实结构。
 *
 * <p>同一份输出同时保留历史 dotted fact（如 {@code observation.HB.value}）和字段目录
 * canonical path 所需的数组资源（如 {@code observations[].valueNumeric}），避免维护页保存成功但运行期
 * 不命中的问题。
 */
public final class ContextFactBridge {

    private ContextFactBridge() {
    }

    public static Map<String, Object> facts(ContextSnapshotResources resources) {
        if (resources == null) {
            return Map.of();
        }
        LinkedHashMap<String, Object> facts = new LinkedHashMap<>();
        if (resources.patient() != null) {
            Map<String, Object> patient = patientMap(resources.patient());
            facts.put("patient", patient);
            facts.put("context.patient", patient);
            facts.put("context.patient.mpi", resources.patient().mpi());
            facts.put("patient.mpi", resources.patient().mpi());
        }
        facts.put("allergyIntolerances", resourceList(resources.allergyIntolerances()));
        facts.put("encounters", resourceList(resources.encounters()));
        facts.put("conditions", resourceList(resources.conditions()));
        facts.put("nursingAssessments", resourceList(resources.nursingAssessments()));
        facts.put("observations", resources.observations().stream()
            .map(ContextFactBridge::observationMap)
            .toList());
        facts.put("diagnosticReports", resourceList(resources.diagnosticReports()));
        facts.put("medications", resourceList(resources.medications()));
        facts.put("procedures", resourceList(resources.procedures()));
        facts.put("documents", resourceList(resources.documents()));
        facts.put("carePlans", resourceList(resources.carePlans()));
        facts.put("followUps", resourceList(resources.followUps()));
        facts.put("claims", resourceList(resources.claims()));
        facts.put("extensions", resources.extensions().deepCopy());
        for (CanonicalObservation observation : resources.observations()) {
            Object value = observation.valueNumeric() == null
                ? observation.valueString()
                : observation.valueNumeric();
            String code = observation.code();
            facts.put("observation." + code + ".value", value);
            facts.put("observation." + code + ".valueNumeric", observation.valueNumeric());
            facts.put("observation." + code + ".criticalFlag", observation.criticalFlag());
            facts.put("context.observations." + code + ".value", value);
            facts.put("context.observations." + code + ".criticalFlag", observation.criticalFlag());
        }
        return facts;
    }

    public static JsonNode conditionContext(ObjectMapper json, ContextSnapshotResources resources) {
        ObjectNode root = json.createObjectNode();
        facts(resources).forEach((path, value) -> putDottedPath(json, root, path, value));
        return root;
    }

    private static Map<String, Object> patientMap(CanonicalPatient patient) {
        return recordMap(patient);
    }

    private static Map<String, Object> observationMap(CanonicalObservation observation) {
        Map<String, Object> value = recordMap(observation);
        value.put("value", observation.valueNumeric() == null ? observation.valueString() : observation.valueNumeric());
        return value;
    }

    private static List<Map<String, Object>> resourceList(Collection<?> resources) {
        if (resources == null || resources.isEmpty()) {
            return List.of();
        }
        return resources.stream()
            .map(ContextFactBridge::recordMap)
            .toList();
    }

    private static Map<String, Object> recordMap(Object record) {
        if (record == null) {
            return Map.of();
        }
        LinkedHashMap<String, Object> value = new LinkedHashMap<>();
        for (RecordComponent component : record.getClass().getRecordComponents()) {
            try {
                value.put(component.getName(), normalize(component.getAccessor().invoke(record)));
            } catch (ReflectiveOperationException exception) {
                throw new IllegalStateException("无法展开上下文资源字段: " + component.getName(), exception);
            }
        }
        return value;
    }

    private static Object normalize(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Instant instant) {
            return instant.toString();
        }
        if (value instanceof LocalDate date) {
            return date.toString();
        }
        if (value instanceof QualityStatus status) {
            return status.name();
        }
        if (value instanceof Enum<?> enumValue) {
            return enumValue.name();
        }
        if (value instanceof Collection<?> collection) {
            return collection.stream()
                .map(ContextFactBridge::normalize)
                .toList();
        }
        if (value.getClass().isRecord()) {
            return recordMap(value);
        }
        return value;
    }

    private static void putDottedPath(ObjectMapper json, ObjectNode root, String path, Object value) {
        if (isBlank(path)) {
            return;
        }
        String[] segments = path.split("\\.");
        ObjectNode current = root;
        for (int index = 0; index < segments.length; index += 1) {
            String segment = segments[index];
            if (isBlank(segment)) {
                continue;
            }
            if (index == segments.length - 1) {
                current.set(segment, value == null ? json.nullNode() : json.valueToTree(value));
                return;
            }
            JsonNode child = current.get(segment);
            if (child == null || !child.isObject()) {
                child = json.createObjectNode();
                current.set(segment, child);
            }
            current = (ObjectNode) child;
        }
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
