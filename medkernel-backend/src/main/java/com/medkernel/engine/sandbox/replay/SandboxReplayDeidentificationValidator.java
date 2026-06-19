package com.medkernel.engine.sandbox.replay;

import java.util.Iterator;
import java.util.Locale;
import java.util.Set;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Component;

import com.medkernel.shared.api.error.ApiException;
import com.medkernel.shared.api.error.ErrorCode;

/** 对导入的历史上下文执行 D4 严格去标识校验。 */
@Component
public class SandboxReplayDeidentificationValidator {

    public static final String PROFILE = "MEDKERNEL_D4_STRICT_V1";
    private static final Set<String> FORBIDDEN_KEYS = Set.of(
        "patientname", "idcard", "identitynumber", "identitycard", "phone", "mobile",
        "telephone", "address", "contactname", "contactphone");

    public void validate(JsonNode context) {
        if (context == null || !context.isObject()) {
            reject("上下文必须为对象");
        }
        JsonNode patient = context.path("resources").path("patient");
        if (!patient.isObject()) {
            reject("缺少 resources.patient");
        }
        requireDeidentified(patient.path("mpi"), "患者 mpi");
        requireDeidentified(patient.path("name"), "患者 name");
        inspect(context, "$");
    }

    private void inspect(JsonNode node, String path) {
        if (node.isObject()) {
            Iterator<String> fields = node.fieldNames();
            while (fields.hasNext()) {
                String field = fields.next();
                JsonNode value = node.get(field);
                String normalized = field.toLowerCase(Locale.ROOT);
                if (FORBIDDEN_KEYS.contains(normalized)) {
                    reject("禁止直接标识字段 " + path + "." + field);
                }
                if ("sourcerecordid".equals(normalized)
                        && !value.isNull()
                        && (!value.isTextual() || !value.textValue().startsWith("DEID-"))) {
                    reject("sourceRecordId 必须使用 DEID- 假名");
                }
                inspect(value, path + "." + field);
            }
        } else if (node.isArray()) {
            for (int index = 0; index < node.size(); index++) {
                inspect(node.get(index), path + "[" + index + "]");
            }
        }
    }

    private static void requireDeidentified(JsonNode node, String label) {
        if (!node.isTextual() || !node.textValue().startsWith("DEID-")) {
            reject(label + " 必须使用 DEID- 假名");
        }
    }

    private static void reject(String reason) {
        throw new ApiException(ErrorCode.BAD_REQUEST, "D4 严格去标识校验失败：" + reason);
    }
}
