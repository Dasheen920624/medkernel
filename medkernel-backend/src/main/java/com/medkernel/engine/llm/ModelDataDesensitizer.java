package com.medkernel.engine.llm;

/**
 * 患者敏感数据正则脱敏共享工具（LLM-01 脱敏，LLM-03 出域复用）。
 *
 * <p>纯函数、无状态：模型网关入参脱敏与出域字段脱敏共用同一套规则，杜绝重复实现导致口径漂移。
 * {@code DEFAULT}：手机号、身份证、银行卡、邮箱；{@code MASK_ALL}：在 DEFAULT 基础上再对
 * 「患者/姓名」标注的中文姓名与「病历号/就诊号/住院号/门诊号」标注的编号脱敏；{@code NONE}：不脱敏。
 * 中文正文无词边界，故不用 {@code \\b}，改用数字串前后非数字断言，避免漏脱敏或误伤普通数字。
 */
public final class ModelDataDesensitizer {

    private ModelDataDesensitizer() {
    }

    public static String desensitize(String input, String strategy) {
        if (input == null || input.isBlank() || "NONE".equalsIgnoreCase(strategy)) {
            return input;
        }

        String result = input;
        // 1. 手机号：保留前 3 后 4，中间 4 位掩码。
        result = result.replaceAll("(?<!\\d)(1[3-9]\\d)\\d{4}(\\d{4})(?!\\d)", "$1****$2");
        // 2. 中国居民身份证：保留前 6 后 4，中间 8 位掩码（先于银行卡，避免 18 位被误判为卡号）。
        result = result.replaceAll("(?<!\\d)(\\d{6})\\d{8}(\\d{3}[0-9Xx])(?!\\d)", "$1********$2");
        // 3. 银行卡：16-19 位连续数字，仅保留后 4 位。
        result = result.replaceAll("(?<!\\d)\\d{12,15}(\\d{4})(?!\\d)", "************$1");
        // 4. 邮箱：保留首字符与域名，掩码本地部分其余字符。
        result = result.replaceAll("([\\w.+-])[\\w.+-]*(@[\\w.-]+)", "$1***$2");

        if ("MASK_ALL".equalsIgnoreCase(strategy)) {
            // 5. 「患者/姓名」标注后的 2-4 位中文姓名。
            result = result.replaceAll("(患者|姓名)([:：]?\\s*)[\\u4e00-\\u9fa5]{2,4}", "$1$2**");
            // 6. 「病历号/就诊号/住院号/门诊号」标注后的字母数字编号。
            result = result.replaceAll("(病历号|就诊号|住院号|门诊号)([:：]?\\s*)[A-Za-z0-9-]{3,}", "$1$2****");
        }

        return result;
    }
}
