package com.medkernel.engine.knowledge.delivery;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

import com.fasterxml.jackson.databind.JsonNode;
import com.medkernel.shared.api.error.ApiException;
import com.medkernel.shared.api.error.ErrorCode;
import org.springframework.stereotype.Service;

/** 导出与导入共用的患者标识、私钥和凭据内容红线。 */
@Service
public class PortablePackageContentPolicy {

    private static final Set<String> DIRECT_IDENTIFIER_KEYS = Set.of(
        "patientid", "patientname", "mrn", "medicalrecordnumber", "idcard",
        "identitynumber", "phone", "mobile", "email");
    private static final Set<String> CLINICAL_INSTANCE_RESOURCE_TYPES = Set.of(
        "patient", "allergyintolerance", "encounter", "condition",
        "nursingassessment", "observation", "diagnosticreport", "medication",
        "medicationrequest", "procedure", "document", "documentreference",
        "careplan", "followup", "task", "claim");
    private static final List<Pattern> FORBIDDEN_SECRET_PATTERNS = List.of(
        Pattern.compile("-----BEGIN [^-\\r\\n]*PRIVATE KEY-----", Pattern.CASE_INSENSITIVE),
        Pattern.compile(
            "\"(?:password|passphrase|client[_-]?secret|api[_-]?key|access[_-]?token|"
                + "refresh[_-]?token|authorization)\"\\s*:",
            Pattern.CASE_INSENSITIVE),
        Pattern.compile("\\bbearer\\s+[A-Za-z0-9._~+/-]+=*", Pattern.CASE_INSENSITIVE));

    /** 校验单个规范资产正文和合成测试向量不得携带运行期患者标识。 */
    public void validateDocument(PortableAssetDocument document) {
        if (document == null) {
            throw invalid("医疗资源包资产正文不能为空");
        }
        assertNoClinicalInstance(document.content());
        assertNoDirectIdentifier(document.content());
        for (PortableAssetDocument.TestVector vector : document.testVectors()) {
            assertNoDirectIdentifier(vector.input());
            assertNoDirectIdentifier(vector.expected());
        }
    }

    /** 校验包内规范文件不得携带私钥或访问凭据标记。 */
    public void validateFile(String path, byte[] bytes) {
        String text = new String(bytes, StandardCharsets.UTF_8);
        for (Pattern pattern : FORBIDDEN_SECRET_PATTERNS) {
            if (pattern.matcher(text).find()) {
                throw invalid("完整医疗资源包正文含私钥或凭据标记: " + path);
            }
        }
    }

    private void assertNoDirectIdentifier(JsonNode node) {
        if (node == null || node.isNull()) {
            return;
        }
        if (node.isObject()) {
            node.fields().forEachRemaining(field -> {
                String key = field.getKey().replace("_", "")
                    .replace("-", "").toLowerCase(Locale.ROOT);
                if (DIRECT_IDENTIFIER_KEYS.contains(key)
                        && !field.getValue().isNull()
                        && !field.getValue().asText("").isBlank()) {
                    throw invalid("医疗资源包含直接患者标识字段: " + field.getKey());
                }
                assertNoDirectIdentifier(field.getValue());
            });
        } else if (node.isArray()) {
            node.forEach(this::assertNoDirectIdentifier);
        }
    }

    private void assertNoClinicalInstance(JsonNode node) {
        if (node == null || node.isNull()) {
            return;
        }
        if (node.isObject()) {
            node.fields().forEachRemaining(field -> {
                String key = field.getKey().replace("_", "")
                    .replace("-", "").toLowerCase(Locale.ROOT);
                String value = field.getValue().isTextual()
                    ? field.getValue().textValue().replace("_", "")
                        .replace("-", "").toLowerCase(Locale.ROOT)
                    : null;
                if ("resourcetype".equals(key)
                        && CLINICAL_INSTANCE_RESOURCE_TYPES.contains(value)) {
                    throw invalid("完整医疗资源包资产正文不得包含患者资源或临床实例: "
                        + field.getValue().textValue());
                }
                assertNoClinicalInstance(field.getValue());
            });
        } else if (node.isArray()) {
            node.forEach(this::assertNoClinicalInstance);
        }
    }

    private static ApiException invalid(String message) {
        return new ApiException(ErrorCode.VALIDATION_FAILED, message);
    }
}
