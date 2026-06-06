package com.medkernel.engine.rule;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.medkernel.shared.api.error.ApiException;
import com.medkernel.shared.api.error.ErrorCode;

/**
 * 规则 DSL 临床算子支持库。
 *
 * <p>集中承载单位安全换算、时间窗判定和受控临床公式，保持 {@link RuleDslEvaluator}
 * 只负责条件树调度与解释拼装。当前白名单为确定性代码，不落库；如后续接 TERM-01 字典，
 * 仍需保持无换算关系时诚实拒绝。
 */
final class ClinicalRuleOperatorSupport {

    /** 参考范围「下限-上限」：两个非负小数，分隔符支持 {@code - – ~ ～ to 至}（同单位，不换算）。 */
    private static final Pattern REFERENCE_RANGE =
        Pattern.compile("^(\\d+(?:\\.\\d+)?)\\s*(?:-|–|~|～|to|至)\\s*(\\d+(?:\\.\\d+)?)$");
    /** 参考范围仅上界：{@code < <= ≤} 前缀。 */
    private static final Pattern REFERENCE_UPPER_ONLY =
        Pattern.compile("^(?:<=|<|≤)\\s*(\\d+(?:\\.\\d+)?)$");
    /** 参考范围仅下界：{@code > >= ≥} 前缀。 */
    private static final Pattern REFERENCE_LOWER_ONLY =
        Pattern.compile("^(?:>=|>|≥)\\s*(\\d+(?:\\.\\d+)?)$");

    private final ObjectMapper json;

    ClinicalRuleOperatorSupport(ObjectMapper json) {
        this.json = json;
    }

    Outcome basicOutcome(boolean matched, JsonNode actual, JsonNode expected) {
        return new Outcome(matched, actual, expected, !exists(actual), null, null, null, null);
    }

    /**
     * {@code is_missing}：临床取值缺失时命中——字段未解析、或解析到的对象不含
     * {@code value/valueNumeric/valueString}。本算子总能定论（缺失即答案），故 {@code missing} 标记恒为
     * {@code false}，由 {@code matched} 表达「是否缺值」。
     */
    Outcome isMissing(JsonNode actual) {
        boolean matched = !hasClinicalValue(actual);
        return new Outcome(matched, actual, null, false, null, null, null, null);
    }

    private boolean hasClinicalValue(JsonNode actual) {
        if (!exists(actual)) {
            return false;
        }
        if (actual.isObject()) {
            return exists(actual.path("value"))
                || exists(actual.path("valueNumeric"))
                || exists(actual.path("valueString"));
        }
        return true;
    }

    Outcome between(String fact, JsonNode actual, JsonNode expected) {
        return betweenOutcome(fact, actual, expected, false);
    }

    /**
     * {@code not_between}：值存在且落在 {@code [min, max]} 之外才命中。取值缺失时按缺失产生未命中，
     * 不得把「缺值」臆测为「越界=命中」（缺失守卫，与 between 同义诚实降级）。
     */
    Outcome notBetween(String fact, JsonNode actual, JsonNode expected) {
        return betweenOutcome(fact, actual, expected, true);
    }

    private Outcome betweenOutcome(String fact, JsonNode actual, JsonNode expected, boolean negate) {
        String label = negate ? "not_between" : "between";
        if (!exists(actual)) {
            return new Outcome(false, actual, expected, true, null, null, null, null);
        }
        requireObject(expected, label + ".value");
        Measurement measurement = measurement(fact, actual);
        BigDecimal min = requiredDecimal(expected, "min");
        BigDecimal max = requiredDecimal(expected, "max");
        if (min.compareTo(max) > 0) {
            throw invalid(label + ".value.min 不能大于 max");
        }
        boolean includeMin = expected.path("includeMin").asBoolean(true);
        boolean includeMax = expected.path("includeMax").asBoolean(true);
        String expectedUnit = optionalText(expected, "unit");
        String unit = expectedUnit == null ? measurement.unit() : expectedUnit;
        if (expectedUnit != null && !sameUnit(measurement.unit(), expectedUnit)) {
            throw unitIncompatible("字段 " + fact + " 单位 " + emptyToQuestion(measurement.unit())
                + " 不能按 " + label + " 直接作为 " + expectedUnit + " 比较");
        }

        int lower = measurement.value().compareTo(min);
        int upper = measurement.value().compareTo(max);
        boolean within = (includeMin ? lower >= 0 : lower > 0) && (includeMax ? upper <= 0 : upper < 0);
        boolean matched = negate != within;
        String formula = formatNumber(measurement.value()) + unitSuffix(unit)
            + (negate ? " not between " : " between ")
            + (includeMin ? "[" : "(")
            + formatNumber(min) + ", " + formatNumber(max)
            + (includeMax ? "]" : ")");
        return clinicalOutcome(matched, actual, expected, measurement.value(), unit, measurement.source(), formula);
    }

    Outcome unitCompare(String fact, JsonNode actual, JsonNode expected) {
        if (!exists(actual)) {
            return new Outcome(false, actual, expected, true, null, null, null, null);
        }
        requireObject(expected, "unit_compare.value");
        Measurement measurement = measurement(fact, actual);
        String comparison = optionalText(expected, "comparison");
        if (comparison == null) {
            comparison = optionalText(expected, "operator");
        }
        if (comparison == null) {
            throw invalid("unit_compare.value 缺少 comparison");
        }
        BigDecimal threshold = requiredDecimal(expected, "value");
        String targetUnit = requiredText(expected, "unit");
        String analyte = requiredText(expected, "analyte");
        ConvertedMeasurement converted = convertMeasurement(fact, measurement, targetUnit, analyte);
        boolean matched = compareNumbers(converted.value(), threshold, comparison);
        BigDecimal displayValue = round2(converted.value());
        String thresholdText = expected.get("value").asText();
        String formula = converted.formula() + "; "
            + formatNumber(displayValue) + " "
            + comparison.toLowerCase(Locale.ROOT) + " " + thresholdText + " " + targetUnit;
        return clinicalOutcome(matched, actual, expected, displayValue, targetUnit, measurement.source(), formula);
    }

    Outcome temporal(String fact, JsonNode actual, JsonNode expected) {
        requireObject(expected, "temporal.value");
        String mode = requiredText(expected, "mode").toLowerCase(Locale.ROOT);
        Duration window = parseWindow(requiredText(expected, "window"));
        Instant referenceTime = parseReferenceTime(requiredText(expected, "referenceTime"));
        int count = optionalPositiveInt(expected, "count", 1);
        if (!exists(actual)) {
            return new Outcome(false, actual, expected, true, null, null, null, null);
        }
        if (!actual.isArray()) {
            throw insufficientData("字段 " + fact + " 必须是标准上下文时间序列数组");
        }
        List<Measurement> measurements = measurementsInWindow(fact, actual, referenceTime.minus(window), referenceTime);

        return switch (mode) {
            case "consecutive" -> consecutiveTemporal(fact, expected, measurements, window, referenceTime, count);
            case "trend" -> trendTemporal(fact, expected, measurements, window, referenceTime, count);
            default -> throw operatorInvalid("不支持的 temporal.mode: " + mode);
        };
    }

    Outcome derived(String fact, JsonNode context, JsonNode expected) {
        requireObject(expected, "derived.value");
        String formulaName = requiredText(expected, "formula").toUpperCase(Locale.ROOT);
        JsonNode parameters = expected.path("parameters");
        requireObject(parameters, "derived.value.parameters");
        DerivedResult result = switch (formulaName) {
            case "CKD_EPI_2021_EGFR" -> calculateEgfr(context, parameters);
            case "COCKCROFT_GAULT_CRCL" -> calculateCrcl(context, parameters);
            case "MOSTELLER_BSA" -> calculateBsa(context, parameters);
            default -> throw operatorInvalid("derived 公式不在白名单: " + formulaName);
        };

        String expectedUnit = optionalText(expected, "unit");
        if (expectedUnit != null && !sameUnit(result.unit(), expectedUnit)) {
            throw unitIncompatible("字段 " + fact + " 公式单位 " + result.unit() + " 不能作为 " + expectedUnit + " 比较");
        }
        String comparison = optionalText(expected, "comparison");
        if (comparison == null) {
            comparison = "gte";
        }
        boolean matched;
        String comparisonFormula;
        if ("between".equalsIgnoreCase(comparison)) {
            BigDecimal min = requiredDecimal(expected, "min");
            BigDecimal max = requiredDecimal(expected, "max");
            boolean includeMin = expected.path("includeMin").asBoolean(true);
            boolean includeMax = expected.path("includeMax").asBoolean(true);
            int lower = result.value().compareTo(min);
            int upper = result.value().compareTo(max);
            matched = (includeMin ? lower >= 0 : lower > 0) && (includeMax ? upper <= 0 : upper < 0);
            comparisonFormula = "; " + formatNumber(result.value()) + unitSuffix(result.unit()) + " between "
                + (includeMin ? "[" : "(")
                + formatNumber(min) + ", " + formatNumber(max)
                + (includeMax ? "]" : ")");
        } else {
            BigDecimal threshold = requiredDecimal(expected, "value");
            matched = compareNumbers(result.value(), threshold, comparison);
            comparisonFormula = "; " + formatNumber(result.value()) + unitSuffix(result.unit()) + " "
                + comparison.toLowerCase(Locale.ROOT) + " " + expected.get("value").asText()
                + unitSuffix(result.unit());
        }
        return clinicalOutcome(matched, numberNode(result.value()), expected, result.value(),
            result.unit(), result.source(), result.formula() + comparisonFormula);
    }

    /**
     * 参考范围算子 {@code within_ref/above_ref/below_ref}：取 Observation 自带的 {@code referenceRange}
     * （与取值同单位，不换算）判定数值相对参考区间的位置。范围不可解析或所需边界缺失时诚实抛
     * {@code INSUFFICIENT_DATA}，不臆测；取值缺失则按缺失产生未命中。
     *
     * @param mode {@code within}（区间内）/ {@code above}（高于上界）/ {@code below}（低于下界）
     */
    Outcome referenceRange(String fact, JsonNode actual, String mode) {
        if (!exists(actual)) {
            return new Outcome(false, actual, null, true, null, null, null, null);
        }
        Measurement measurement = measurement(fact, actual);
        String rangeText = optionalText(actual, "referenceRange");
        if (rangeText == null) {
            throw insufficientData("字段 " + fact + " 缺少参考范围 referenceRange");
        }
        ReferenceRange range = parseReferenceRange(fact, rangeText);
        BigDecimal value = measurement.value();
        boolean matched = switch (mode) {
            case "within" -> (range.low() == null || value.compareTo(range.low()) >= 0)
                && (range.high() == null || value.compareTo(range.high()) <= 0);
            case "above" -> {
                if (range.high() == null) {
                    throw insufficientData("字段 " + fact + " 参考范围无上界，无法判断 above_ref: " + rangeText);
                }
                yield value.compareTo(range.high()) > 0;
            }
            case "below" -> {
                if (range.low() == null) {
                    throw insufficientData("字段 " + fact + " 参考范围无下界，无法判断 below_ref: " + rangeText);
                }
                yield value.compareTo(range.low()) < 0;
            }
            default -> throw operatorInvalid("不支持的参考范围模式: " + mode);
        };
        String formula = formatNumber(value) + unitSuffix(measurement.unit())
            + " " + mode + " ref " + formatRange(range);
        return clinicalOutcome(matched, actual, null, value, measurement.unit(), measurement.source(), formula);
    }

    private ReferenceRange parseReferenceRange(String fact, String raw) {
        String text = raw.trim();
        Matcher upper = REFERENCE_UPPER_ONLY.matcher(text);
        if (upper.matches()) {
            return new ReferenceRange(null, new BigDecimal(upper.group(1)));
        }
        Matcher lower = REFERENCE_LOWER_ONLY.matcher(text);
        if (lower.matches()) {
            return new ReferenceRange(new BigDecimal(lower.group(1)), null);
        }
        Matcher range = REFERENCE_RANGE.matcher(text);
        if (range.matches()) {
            BigDecimal low = new BigDecimal(range.group(1));
            BigDecimal high = new BigDecimal(range.group(2));
            if (low.compareTo(high) > 0) {
                throw invalid("字段 " + fact + " 参考范围下界大于上界: " + raw);
            }
            return new ReferenceRange(low, high);
        }
        throw insufficientData("字段 " + fact + " 参考范围无法解析: " + raw);
    }

    private String formatRange(ReferenceRange range) {
        if (range.low() != null && range.high() != null) {
            return "[" + formatNumber(range.low()) + ", " + formatNumber(range.high()) + "]";
        }
        if (range.high() != null) {
            return "≤" + formatNumber(range.high());
        }
        return "≥" + formatNumber(range.low());
    }

    private Outcome consecutiveTemporal(
        String fact,
        JsonNode expected,
        List<Measurement> measurements,
        Duration window,
        Instant referenceTime,
        int count
    ) {
        JsonNode condition = expected.path("condition");
        requireObject(condition, "temporal.value.condition");
        String comparison = requiredText(condition, "operator");
        BigDecimal threshold = requiredDecimal(condition, "value");
        String unit = optionalText(condition, "unit");
        List<Measurement> run = new ArrayList<>();
        List<Measurement> matchedRun = List.of();
        for (Measurement measurement : measurements) {
            if (unit != null && !sameUnit(measurement.unit(), unit)) {
                throw unitIncompatible("字段 " + fact + " 时间序列单位 " + emptyToQuestion(measurement.unit())
                    + " 不能作为 " + unit + " 比较");
            }
            if (compareNumbers(measurement.value(), threshold, comparison)) {
                run.add(measurement);
                if (run.size() >= count) {
                    matchedRun = List.copyOf(run.subList(run.size() - count, run.size()));
                    break;
                }
            } else {
                run.clear();
            }
        }

        boolean matched = !matchedRun.isEmpty();
        String source = matched ? joinSources(matchedRun) : joinSources(run);
        String formula = window + " window ending " + referenceTime
            + " consecutive " + count + " where value "
            + comparison.toLowerCase(Locale.ROOT) + " " + condition.get("value").asText()
            + unitSuffix(unit);
        return clinicalOutcome(matched, null, expected, BigDecimal.valueOf(matched ? matchedRun.size() : 0),
            "次", source, formula);
    }

    private Outcome trendTemporal(
        String fact,
        JsonNode expected,
        List<Measurement> measurements,
        Duration window,
        Instant referenceTime,
        int count
    ) {
        String direction = requiredText(expected, "direction").toLowerCase(Locale.ROOT);
        if (!"up".equals(direction) && !"down".equals(direction)) {
            throw operatorInvalid("不支持的 temporal.direction: " + direction);
        }
        if (count < 2) {
            throw invalid("temporal.value.count 在 trend 模式下必须至少为 2");
        }
        String unit = optionalText(expected, "unit");
        if (measurements.size() < count) {
            String formula = window + " window ending " + referenceTime
                + " trend " + direction + " across " + count + " values";
            return clinicalOutcome(false, null, expected, BigDecimal.valueOf(measurements.size()), "次",
                joinSources(measurements), formula);
        }
        List<Measurement> tail = measurements.subList(measurements.size() - count, measurements.size());
        for (Measurement measurement : tail) {
            if (unit != null && !sameUnit(measurement.unit(), unit)) {
                throw unitIncompatible("字段 " + fact + " 趋势单位 " + emptyToQuestion(measurement.unit())
                    + " 不能作为 " + unit + " 比较");
            }
        }
        boolean matched = true;
        for (int index = 1; index < tail.size(); index++) {
            int compared = tail.get(index).value().compareTo(tail.get(index - 1).value());
            if (("up".equals(direction) && compared <= 0) || ("down".equals(direction) && compared >= 0)) {
                matched = false;
                break;
            }
        }
        String formula = window + " window ending " + referenceTime
            + " trend " + direction + " across " + count + " values";
        return clinicalOutcome(matched, null, expected, BigDecimal.valueOf(tail.size()), "次",
            joinSources(tail), formula);
    }

    private DerivedResult calculateEgfr(JsonNode context, JsonNode parameters) {
        Measurement creatinine = requiredPositiveMeasurementParameter(context, parameters, "creatinine");
        ConvertedMeasurement scr = convertMeasurement("derived.egfr.creatinine", creatinine, "mg/dL", "creatinine");
        BigDecimal age = requiredPositiveNumericParameter(context, parameters, "age");
        SexProfile sex = requiredSexParameter(context, parameters, "sex");
        double ratio = scr.value().doubleValue() / (sex.female() ? 0.7d : 0.9d);
        double alpha = sex.female() ? -0.241d : -0.302d;
        double sexFactor = sex.female() ? 1.012d : 1.0d;
        double egfr = 142d
            * Math.pow(Math.min(ratio, 1d), alpha)
            * Math.pow(Math.max(ratio, 1d), -1.200d)
            * Math.pow(0.9938d, age.doubleValue())
            * sexFactor;
        BigDecimal value = round2(BigDecimal.valueOf(egfr));
        String formula = "CKD_EPI_2021_EGFR: 142 * min(Scr/k,1)^alpha * max(Scr/k,1)^-1.200"
            + " * 0.9938^Age * sexFactor; Scr=" + formatNumber(scr.value()) + " mg/dL"
            + ", Age=" + formatNumber(age) + ", Sex=" + sex.label();
        return new DerivedResult(value, "mL/min/1.73m2", creatinine.source(), formula);
    }

    private DerivedResult calculateCrcl(JsonNode context, JsonNode parameters) {
        Measurement creatinine = requiredPositiveMeasurementParameter(context, parameters, "creatinine");
        ConvertedMeasurement scr = convertMeasurement("derived.crcl.creatinine", creatinine, "mg/dL", "creatinine");
        BigDecimal age = requiredPositiveNumericParameter(context, parameters, "age");
        BigDecimal weightKg = requiredPositiveNumericParameter(context, parameters, "weightKg");
        SexProfile sex = requiredSexParameter(context, parameters, "sex");
        double sexFactor = sex.female() ? 0.85d : 1.0d;
        double crcl = ((140d - age.doubleValue()) * weightKg.doubleValue() * sexFactor)
            / (72d * scr.value().doubleValue());
        BigDecimal value = round2(BigDecimal.valueOf(crcl));
        String formula = "COCKCROFT_GAULT_CRCL: ((140 - Age) * WeightKg * sexFactor) / (72 * Scr); Scr="
            + formatNumber(scr.value()) + " mg/dL, Age=" + formatNumber(age)
            + ", WeightKg=" + formatNumber(weightKg) + ", Sex=" + sex.label();
        return new DerivedResult(value, "mL/min", creatinine.source(), formula);
    }

    private DerivedResult calculateBsa(JsonNode context, JsonNode parameters) {
        BigDecimal heightCm = requiredPositiveNumericParameter(context, parameters, "heightCm");
        BigDecimal weightKg = requiredPositiveNumericParameter(context, parameters, "weightKg");
        double bsa = Math.sqrt(heightCm.doubleValue() * weightKg.doubleValue() / 3600d);
        BigDecimal value = round2(BigDecimal.valueOf(bsa));
        String formula = "MOSTELLER_BSA: sqrt(HeightCm * WeightKg / 3600); HeightCm="
            + formatNumber(heightCm) + ", WeightKg=" + formatNumber(weightKg);
        return new DerivedResult(value, "m2", null, formula);
    }

    private BigDecimal requiredNumericParameter(JsonNode context, JsonNode parameters, String name) {
        String path = requiredText(parameters, name);
        JsonNode value = findPath(context, path);
        if (!exists(value)) {
            throw insufficientData("derived 参数缺失: " + name + " (" + path + ")");
        }
        if (value.isNumber()) {
            return value.decimalValue();
        }
        if (value.isObject() && value.path("value").isNumber()) {
            return value.path("value").decimalValue();
        }
        throw insufficientData("derived 参数不是数值: " + name + " (" + path + ")");
    }

    private BigDecimal requiredPositiveNumericParameter(JsonNode context, JsonNode parameters, String name) {
        BigDecimal value = requiredNumericParameter(context, parameters, name);
        if (value.compareTo(BigDecimal.ZERO) <= 0) {
            throw insufficientData("derived 参数必须大于 0: " + name);
        }
        return value;
    }

    private Measurement requiredMeasurementParameter(JsonNode context, JsonNode parameters, String name) {
        String path = requiredText(parameters, name);
        JsonNode value = findPath(context, path);
        if (!exists(value)) {
            throw insufficientData("derived 参数缺失: " + name + " (" + path + ")");
        }
        Measurement measurement = measurement(name, value);
        if (measurement.unit() == null) {
            throw unitIncompatible("derived 参数缺少单位: " + name + " (" + path + ")");
        }
        return measurement;
    }

    private Measurement requiredPositiveMeasurementParameter(JsonNode context, JsonNode parameters, String name) {
        Measurement measurement = requiredMeasurementParameter(context, parameters, name);
        if (measurement.value().compareTo(BigDecimal.ZERO) <= 0) {
            throw insufficientData("derived 参数必须大于 0: " + name);
        }
        return measurement;
    }

    private SexProfile requiredSexParameter(JsonNode context, JsonNode parameters, String name) {
        String path = requiredText(parameters, name);
        JsonNode value = findPath(context, path);
        if (!exists(value) || !value.isTextual()) {
            throw insufficientData("derived 参数缺失: " + name + " (" + path + ")");
        }
        String normalized = value.asText().trim().toUpperCase(Locale.ROOT);
        if ("F".equals(normalized) || "FEMALE".equals(normalized) || "女".equals(value.asText().trim())) {
            return new SexProfile("FEMALE", true);
        }
        if ("M".equals(normalized) || "MALE".equals(normalized) || "男".equals(value.asText().trim())) {
            return new SexProfile("MALE", false);
        }
        throw insufficientData("derived 参数 sex 仅支持 MALE/FEMALE: " + path);
    }

    private List<Measurement> measurementsInWindow(String fact, JsonNode actual, Instant start, Instant end) {
        List<Measurement> measurements = new ArrayList<>();
        for (JsonNode item : actual) {
            Measurement measurement = measurement(fact, item);
            if (measurement.observedAt() == null) {
                throw insufficientData("字段 " + fact + " 时间序列缺少 observedAt");
            }
            if (!measurement.observedAt().isBefore(start) && !measurement.observedAt().isAfter(end)) {
                measurements.add(measurement);
            }
        }
        measurements.sort(Comparator.comparing(Measurement::observedAt));
        return List.copyOf(measurements);
    }

    private Measurement measurement(String fact, JsonNode node) {
        if (!exists(node)) {
            throw insufficientData("字段 " + fact + " 缺少临床取值");
        }
        if (node.isNumber()) {
            return new Measurement(node.decimalValue(), null, null, null);
        }
        if (!node.isObject()) {
            throw insufficientData("字段 " + fact + " 必须是数值或包含 value 的对象");
        }
        JsonNode value = node.path("value");
        if (!value.isNumber()) {
            throw insufficientData("字段 " + fact + " 缺少数值 value");
        }
        return new Measurement(
            value.decimalValue(),
            optionalText(node, "unit"),
            firstText(node, "source", "sourceRef", "id"),
            optionalInstant(node, "observedAt", "time", "effectiveTime", "recordedAt"));
    }

    private ConvertedMeasurement convertMeasurement(
        String fact,
        Measurement measurement,
        String targetUnit,
        String analyte
    ) {
        if (measurement.unit() == null) {
            throw unitIncompatible("字段 " + fact + " 缺少单位，不能换算到 " + targetUnit);
        }
        if (sameUnit(measurement.unit(), targetUnit)) {
            return new ConvertedMeasurement(measurement.value(),
                formatNumber(measurement.value()) + " " + measurement.unit());
        }
        String normalizedAnalyte = analyte.trim().toLowerCase(Locale.ROOT);
        String from = normalizeUnit(measurement.unit());
        String to = normalizeUnit(targetUnit);
        if ("glucose".equals(normalizedAnalyte) && "mg/dl".equals(from) && "mmol/l".equals(to)) {
            BigDecimal converted = measurement.value().divide(new BigDecimal("18.0182"), 8, RoundingMode.HALF_UP);
            return new ConvertedMeasurement(converted,
                formatNumber(measurement.value()) + " " + measurement.unit()
                    + " / 18.0182 = " + formatNumber(round2(converted)) + " " + targetUnit);
        }
        if ("glucose".equals(normalizedAnalyte) && "mmol/l".equals(from) && "mg/dl".equals(to)) {
            BigDecimal converted = measurement.value().multiply(new BigDecimal("18.0182"));
            return new ConvertedMeasurement(converted,
                formatNumber(measurement.value()) + " " + measurement.unit()
                    + " * 18.0182 = " + formatNumber(round2(converted)) + " " + targetUnit);
        }
        if ("creatinine".equals(normalizedAnalyte) && "mg/dl".equals(from) && "umol/l".equals(to)) {
            BigDecimal converted = measurement.value().multiply(new BigDecimal("88.4"));
            return new ConvertedMeasurement(converted,
                formatNumber(measurement.value()) + " " + measurement.unit()
                    + " * 88.4 = " + formatNumber(round2(converted)) + " " + targetUnit);
        }
        if ("creatinine".equals(normalizedAnalyte) && "umol/l".equals(from) && "mg/dl".equals(to)) {
            BigDecimal converted = measurement.value().divide(new BigDecimal("88.4"), 8, RoundingMode.HALF_UP);
            return new ConvertedMeasurement(converted,
                formatNumber(measurement.value()) + " " + measurement.unit()
                    + " / 88.4 = " + formatNumber(round2(converted)) + " " + targetUnit);
        }
        throw unitIncompatible("字段 " + fact + " 不存在 " + analyte + " 的 "
            + measurement.unit() + " 到 " + targetUnit + " 安全换算关系");
    }

    private boolean compareNumbers(BigDecimal left, BigDecimal right, String operator) {
        int compared = left.compareTo(right);
        return switch (operator.toLowerCase(Locale.ROOT)) {
            case "gt" -> compared > 0;
            case "gte" -> compared >= 0;
            case "lt" -> compared < 0;
            case "lte" -> compared <= 0;
            case "equals", "eq" -> compared == 0;
            case "not_equals", "ne" -> compared != 0;
            default -> throw operatorInvalid("不支持的数值比较算子: " + operator);
        };
    }

    private Outcome clinicalOutcome(
        boolean matched,
        JsonNode actual,
        JsonNode expected,
        BigDecimal value,
        String unit,
        String source,
        String formula
    ) {
        return new Outcome(matched, actual, expected, false, numberNode(value), unit, source, formula);
    }

    private JsonNode numberNode(BigDecimal value) {
        if (value == null) {
            return json.nullNode();
        }
        return json.getNodeFactory().numberNode(value);
    }

    private JsonNode findPath(JsonNode source, String path) {
        JsonNode current = source;
        for (String segment : path.split("\\.")) {
            if (current == null || current.isMissingNode() || current.isNull()) {
                return json.missingNode();
            }
            current = current.path(segment);
        }
        return current == null ? json.missingNode() : current;
    }

    private void requireObject(JsonNode node, String label) {
        if (node == null || !node.isObject()) {
            throw invalid(label + " 必须是 JSON 对象");
        }
    }

    private String requiredText(JsonNode node, String field) {
        String value = node.path(field).asText(null);
        if (value == null || value.isBlank()) {
            throw invalid("规则 DSL 缺少字段: " + field);
        }
        return value;
    }

    private BigDecimal requiredDecimal(JsonNode node, String field) {
        JsonNode value = node.path(field);
        if (!value.isNumber()) {
            throw invalid("规则 DSL 缺少数值字段: " + field);
        }
        return value.decimalValue();
    }

    private Duration parseWindow(String value) {
        try {
            Duration window = Duration.parse(value);
            if (window.isZero() || window.isNegative()) {
                throw invalid("temporal.value.window 必须大于 0: " + value);
            }
            return window;
        } catch (RuntimeException exception) {
            if (exception instanceof ApiException apiException) {
                throw apiException;
            }
            throw invalid("temporal.value.window 必须是 ISO-8601 Duration: " + value);
        }
    }

    private Instant parseReferenceTime(String value) {
        try {
            return Instant.parse(value);
        } catch (RuntimeException exception) {
            throw invalid("temporal.value.referenceTime 必须是 ISO-8601 Instant: " + value);
        }
    }

    private Instant optionalInstant(JsonNode node, String... fields) {
        String value = firstText(node, fields);
        if (value == null) {
            return null;
        }
        try {
            return Instant.parse(value);
        } catch (RuntimeException exception) {
            throw invalid("时间字段必须是 ISO-8601 Instant: " + value);
        }
    }

    private String firstText(JsonNode node, String... fields) {
        for (String field : fields) {
            String value = optionalText(node, field);
            if (value != null) {
                return value;
            }
        }
        return null;
    }

    private String optionalText(JsonNode node, String field) {
        if (node == null || node.isMissingNode() || !node.has(field) || node.path(field).isNull()) {
            return null;
        }
        String value = node.path(field).asText(null);
        return value == null || value.isBlank() ? null : value.trim();
    }

    private int optionalPositiveInt(JsonNode node, String field, int defaultValue) {
        if (node == null || node.isMissingNode() || !node.has(field) || node.path(field).isNull()) {
            return defaultValue;
        }
        JsonNode value = node.path(field);
        if (!value.isIntegralNumber() || !value.canConvertToInt()) {
            throw invalid("temporal.value." + field + " 必须是整数");
        }
        int result = value.asInt();
        if (result <= 0) {
            throw invalid("temporal.value." + field + " 必须大于 0");
        }
        return result;
    }

    private boolean sameUnit(String left, String right) {
        if (left == null || right == null) {
            return left == null && right == null;
        }
        return normalizeUnit(left).equals(normalizeUnit(right));
    }

    private String normalizeUnit(String unit) {
        return unit.trim()
            .replace("μ", "u")
            .replace("µ", "u")
            .replace(" ", "")
            .toLowerCase(Locale.ROOT);
    }

    private String unitSuffix(String unit) {
        return unit == null || unit.isBlank() ? "" : " " + unit;
    }

    private String emptyToQuestion(String value) {
        return value == null || value.isBlank() ? "?" : value;
    }

    private String joinSources(List<Measurement> measurements) {
        List<String> sources = measurements.stream()
            .map(Measurement::source)
            .filter(source -> source != null && !source.isBlank())
            .toList();
        return sources.isEmpty() ? null : String.join(",", sources);
    }

    private BigDecimal round2(BigDecimal value) {
        return value.setScale(2, RoundingMode.HALF_UP);
    }

    private String formatNumber(BigDecimal value) {
        return value.stripTrailingZeros().toPlainString();
    }

    private boolean exists(JsonNode actual) {
        if (actual == null || actual.isMissingNode() || actual.isNull()) {
            return false;
        }
        if (actual.isTextual()) {
            return !actual.asText().isBlank();
        }
        if (actual.isArray() || actual.isObject()) {
            return actual.size() > 0;
        }
        return true;
    }

    private ApiException invalid(String message) {
        return new ApiException(ErrorCode.RULE_DSL_INVALID, message);
    }

    private ApiException operatorInvalid(String message) {
        return new ApiException(ErrorCode.DSL_OPERATOR_INVALID, message);
    }

    private ApiException unitIncompatible(String message) {
        return new ApiException(ErrorCode.UNIT_INCOMPATIBLE, message);
    }

    private ApiException insufficientData(String message) {
        return new ApiException(ErrorCode.INSUFFICIENT_DATA, message);
    }

    record Outcome(
        boolean matched,
        JsonNode actual,
        JsonNode expected,
        boolean missing,
        JsonNode value,
        String unit,
        String source,
        String formula) {
    }

    private record Measurement(
        BigDecimal value,
        String unit,
        String source,
        Instant observedAt) {
    }

    private record ConvertedMeasurement(BigDecimal value, String formula) {
    }

    private record DerivedResult(
        BigDecimal value,
        String unit,
        String source,
        String formula) {
    }

    private record SexProfile(String label, boolean female) {
    }

    /** 解析后的参考范围；{@code low}/{@code high} 为 {@code null} 表示该侧无界（单侧范围）。 */
    private record ReferenceRange(BigDecimal low, BigDecimal high) {
    }
}
