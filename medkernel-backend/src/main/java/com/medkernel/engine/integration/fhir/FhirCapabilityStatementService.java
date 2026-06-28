package com.medkernel.engine.integration.fhir;

import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

/**
 * OPT-01 FHIR 映射能力声明生成器。
 */
@Component
public class FhirCapabilityStatementService {

    private final ObjectMapper json;

    public FhirCapabilityStatementService(ObjectMapper json) {
        this.json = json;
    }

    public JsonNode mappingCapability(FhirVersion version) {
        ObjectNode statement = json.createObjectNode();
        statement.put("resourceType", "CapabilityStatement");
        statement.put("status", "active");
        statement.put("kind", "capability");
        statement.put("name", "MedKernelFhirMappingFacade");
        statement.put("title", "MedKernel FHIR 映射门面能力声明");
        statement.put("publisher", "MedKernel");
        statement.put("version", "OPT-01");
        statement.put("fhirVersion", fhirVersionNumber(version));
        statement.putArray("format").add("json");

        ObjectNode software = statement.putObject("software");
        software.put("name", "MedKernel");
        software.put("version", "OPT-01");

        ObjectNode implementation = statement.putObject("implementation");
        implementation.put("description", "OPT-01 声明 CanonicalResource 与 FHIR R4/R5 的确定性映射能力；"
            + "运行 read/search/create 由受控运行门面开放，高风险申请仍走医师确认。");

        ObjectNode rest = json.createObjectNode();
        rest.put("mode", "server");
        ArrayNode resources = rest.putArray("resource");
        resources.add(resource("Patient", "BIDIRECTIONAL", "CanonicalPatient <-> FHIR Patient"));
        resources.add(resource("Encounter", "BIDIRECTIONAL", "CanonicalEncounter <-> FHIR Encounter"));
        resources.add(resource("Condition", "BIDIRECTIONAL", "CanonicalCondition <-> FHIR Condition"));
        resources.add(resource("AllergyIntolerance", "BIDIRECTIONAL",
            "CanonicalAllergyIntolerance <-> FHIR AllergyIntolerance"));
        resources.add(resource("Observation", "BIDIRECTIONAL", "CanonicalObservation <-> FHIR Observation"));
        resources.add(resource("Medication", "BIDIRECTIONAL", "CanonicalMedication <-> FHIR Medication"));
        resources.add(resource("Procedure", "BIDIRECTIONAL", "CanonicalProcedure <-> FHIR Procedure"));
        resources.add(resource("CarePlan", "BIDIRECTIONAL", "CanonicalCarePlan <-> FHIR CarePlan"));
        resources.add(resource("DiagnosticReport", "BIDIRECTIONAL",
            "CanonicalDiagnosticReport <-> FHIR DiagnosticReport"));
        resources.add(resource("DocumentReference", "BIDIRECTIONAL", "CanonicalDocument <-> FHIR DocumentReference"));
        statement.set("rest", json.createArrayNode().add(rest));
        return statement;
    }

    public JsonNode runtimeCapability(FhirVersion version) {
        ObjectNode statement = baseStatement(version, "OPT-01");
        ObjectNode implementation = statement.putObject("implementation");
        implementation.put("description", "OPT-01 运行门面开放 11 类核心 FHIR 资源 read/search/create；"
            + "ServiceRequest create 只登记医师确认任务，不自动写申请单；"
            + "外部连接状态按 INTEG-01 诚实返回 NOT_CONNECTED。");

        ObjectNode rest = json.createObjectNode();
        rest.put("mode", "server");
        ArrayNode resources = rest.putArray("resource");
        resources.add(runtimeResource("Patient", "FHIR Patient read/search/create -> CanonicalPatient", "read", "search", "create"));
        resources.add(runtimeResource("Encounter", "FHIR Encounter read/search/create -> CanonicalEncounter", "read", "search", "create"));
        resources.add(runtimeResource("Condition", "FHIR Condition read/search/create -> CanonicalCondition + 临床事件 DIAGNOSIS", "read", "search", "create"));
        resources.add(runtimeResource("AllergyIntolerance",
            "FHIR AllergyIntolerance read/search/create -> CanonicalAllergyIntolerance + 临床事件 REPORT",
            "read", "search", "create"));
        resources.add(runtimeResource("Observation", "FHIR Observation read/search/create -> CanonicalObservation + 临床事件 REPORT", "read", "search", "create"));
        resources.add(runtimeResource("Medication", "FHIR Medication read/search/create -> CanonicalMedication + 临床事件 ORDER", "read", "search", "create"));
        resources.add(runtimeResource("Procedure", "FHIR Procedure read/search/create -> CanonicalProcedure + 临床事件 ORDER", "read", "search", "create"));
        resources.add(runtimeResource("CarePlan", "FHIR CarePlan read/search/create -> CanonicalCarePlan + 临床事件 ORDER", "read", "search", "create"));
        resources.add(runtimeResource("MedicationRequest",
            "高风险医嘱类 create 需医师确认；FHIR 门面不自动写医嘱",
            "create"));
        resources.add(runtimeResource("ServiceRequest",
            "高风险检查/治疗申请 create 需医师确认；FHIR 门面不自动写申请单",
            "read", "search", "create"));
        resources.add(runtimeResource("DiagnosticReport",
            "FHIR DiagnosticReport read/search/create -> CanonicalDiagnosticReport + 临床事件 REPORT",
            "read", "search", "create"));
        resources.add(runtimeResource("DocumentReference",
            "FHIR DocumentReference read/search/create -> CanonicalDocument + 临床事件 REPORT",
            "read", "search", "create"));
        statement.set("rest", json.createArrayNode().add(rest));
        return statement;
    }

    private ObjectNode baseStatement(FhirVersion version, String softwareVersion) {
        ObjectNode statement = json.createObjectNode();
        statement.put("resourceType", "CapabilityStatement");
        statement.put("status", "active");
        statement.put("kind", "capability");
        statement.put("name", "MedKernelFhirMappingFacade");
        statement.put("title", "MedKernel FHIR 映射门面能力声明");
        statement.put("publisher", "MedKernel");
        statement.put("version", softwareVersion);
        statement.put("fhirVersion", fhirVersionNumber(version));
        statement.putArray("format").add("json");

        ObjectNode software = statement.putObject("software");
        software.put("name", "MedKernel");
        software.put("version", softwareVersion);
        return statement;
    }

    private ObjectNode resource(String type, String direction, String documentation) {
        ObjectNode resource = json.createObjectNode();
        resource.put("type", type);
        resource.put("documentation", documentation);
        ObjectNode extension = json.createObjectNode();
        extension.put("url", "urn:medkernel:fhir:mapping-direction");
        extension.put("valueCode", direction);
        resource.set("extension", json.createArrayNode().add(extension));
        resource.set("interaction", json.createArrayNode());
        return resource;
    }

    private ObjectNode runtimeResource(String type, String documentation, String... interactions) {
        ObjectNode resource = json.createObjectNode();
        resource.put("type", type);
        resource.put("documentation", documentation);
        ArrayNode interactionNodes = resource.putArray("interaction");
        for (String interaction : interactions) {
            interactionNodes.add(json.createObjectNode().put("code", interaction));
        }
        return resource;
    }

    private static String fhirVersionNumber(FhirVersion version) {
        return switch (version) {
            case R4 -> "4.0.1";
            case R5 -> "5.0.0";
        };
    }
}
