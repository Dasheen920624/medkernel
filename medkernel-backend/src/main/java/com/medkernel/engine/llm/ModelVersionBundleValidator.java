package com.medkernel.engine.llm;

/**
 * ACTIVE 模型版本包的统一可执行性校验器。
 *
 * <p>readiness 与模型网关共用同一规则，确保版本号、三份内容指纹及租户能力作用域完整一致。
 */
public final class ModelVersionBundleValidator {

    private ModelVersionBundleValidator() {
    }

    /** 校验指定租户能力下的 ACTIVE 版本包，不返回任何版本正文。 */
    public static Validation validateActive(ModelVersionBundle bundle, String tenantId, String capabilityCode) {
        if (bundle == null) {
            return Validation.invalid("当前能力未发布 ACTIVE prompt/tool/model 版本包");
        }
        if (!"ACTIVE".equals(bundle.status())) {
            return Validation.invalid("模型版本包状态不是 ACTIVE");
        }
        if (!same(tenantId, bundle.tenantId()) || !same(capabilityCode, bundle.capabilityCode())) {
            return Validation.invalid("ACTIVE 模型版本包作用域与当前租户能力不一致");
        }
        if (blank(bundle.promptVersion()) || blank(bundle.toolVersion()) || blank(bundle.modelVersion())) {
            return Validation.invalid("ACTIVE 模型版本包的版本三元组不完整");
        }
        if (!sha256(bundle.promptHash()) || !sha256(bundle.toolHash()) || !sha256(bundle.modelHash())) {
            return Validation.invalid("ACTIVE 模型版本包的内容指纹不完整");
        }
        String expectedScopeKey = tenantId + "|" + capabilityCode;
        if (!expectedScopeKey.equals(bundle.activeScopeKey())) {
            return Validation.invalid("ACTIVE 模型版本包作用域键不一致");
        }
        return Validation.valid(bundle);
    }

    private static boolean same(String expected, String actual) {
        return expected != null && expected.equals(actual);
    }

    private static boolean blank(String value) {
        return value == null || value.isBlank();
    }

    private static boolean sha256(String value) {
        return value != null && value.matches("[0-9a-fA-F]{64}");
    }

    /** ACTIVE 版本包校验结果。 */
    public record Validation(boolean valid, ModelVersionBundle bundle, String reason) {

        private static Validation valid(ModelVersionBundle bundle) {
            return new Validation(true, bundle, null);
        }

        private static Validation invalid(String reason) {
            return new Validation(false, null, reason);
        }
    }
}
