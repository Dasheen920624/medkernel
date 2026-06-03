package com.medkernel.engine.integration.fhir;

import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

/**
 * OPT-01 FHIR 映射能力声明生成器。
 *
 * <p>PR2 仅声明已经落地的确定性映射面；运行端点、create 回流与安全边界在 PR3 接入。
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
        statement.put("version", "OPT-01-PR2");
        statement.put("fhirVersion", fhirVersionNumber(version));
        statement.putArray("format").add("json");

        ObjectNode software = statement.putObject("software");
        software.put("name", "MedKernel");
        software.put("version", "OPT-01-PR2");

        ObjectNode implementation = statement.putObject("implementation");
        implementation.put("description", "OPT-01 PR2 仅声明 CanonicalResource 与 FHIR 的映射能力；"
            + "运行 read/search/create、医师确认链和安全边界由 PR3 开放。");

        ObjectNode rest = json.createObjectNode();
        rest.put("mode", "server");
        ArrayNode resources = rest.putArray("resource");
        resources.add(resource("Patient", "OUTBOUND", "CanonicalPatient -> FHIR Patient"));
        resources.add(resource("Observation", "INBOUND", "FHIR Observation -> CanonicalObservation"));
        statement.set("rest", json.createArrayNode().add(rest));
        return statement;
    }

    public JsonNode runtimeCapability(FhirVersion version) {
        ObjectNode statement = baseStatement(version, "OPT-01-PR3");
        ObjectNode implementation = statement.putObject("implementation");
        implementation.put("description", "OPT-01 PR3 运行门面仅开放 Observation create；"
            + "MedicationRequest 与 ServiceRequest create 只登记医师确认任务，不自动写医嘱；"
            + "外部连接状态按 INTEG-01 诚实返回 NOT_CONNECTED。");

        ObjectNode rest = json.createObjectNode();
        rest.put("mode", "server");
        ArrayNode resources = rest.putArray("resource");
        resources.add(runtimeResource("Observation",
            "FHIR Observation create -> CanonicalObservation + 临床事件 REPORT + INTEG-01 NOT_CONNECTED 补偿",
            "create"));
        resources.add(runtimeResource("MedicationRequest",
            "高风险医嘱类 create 需医师确认；FHIR 门面不自动写医嘱",
            "create"));
        resources.add(runtimeResource("ServiceRequest",
            "高风险检查/治疗申请 create 需医师确认；FHIR 门面不自动写申请单",
            "create"));
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
