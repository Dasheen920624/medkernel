package com.medkernel.engine.datasvc;

import java.time.Instant;
import java.util.Locale;

import org.springframework.stereotype.Service;

import com.medkernel.shared.api.error.ApiException;
import com.medkernel.shared.api.error.ErrorCode;

/**
 * 引擎数据服务层 · 隐私分级策略服务（DATASVC-01 PR2-c，受控工具 {@code validatePrivacyPolicy} 上游）。
 *
 * <p>按数据分级 D0–D5 策略（规范 §7 / FR-2）判定某一拟用级别是否准入数据服务/CLI/MCP/模型输入。无上游表，
 * 纯策略判定（判定结果本身为 D0 策略元数据）：D0/D1/D2 准入；D3/D4 在 T6.4 字段级加密账本与字段分级
 * 元数据落地后准入，但必须经后端字段级加密、最小化输出和受控工具治理；D5 重要个人信息禁入。非法级别结构化 400。
 */
@Service
public class PrivacyPolicyService {

    /**
     * 判定数据分级是否准入。{@code levelName} 须为 D0–D5 之一，否则结构化 400。
     */
    public PrivacyPolicyDecision validate(String levelName) {
        EngineDataLevel level = parseLevel(levelName);
        Instant now = Instant.now();
        return switch (level) {
            case D0, D1, D2 -> new PrivacyPolicyDecision(level.name(), true, false,
                "可经数据服务/CLI/MCP 输出（去标识或已发布资产元数据）", EngineDataLevel.D0, now);
            case D3, D4 -> new PrivacyPolicyDecision(level.name(), true, true,
                "须字段级加密；仅允许后端加密落库、最小必要脱敏输出和受控工具治理后使用",
                EngineDataLevel.D0, now);
            case D5 -> new PrivacyPolicyDecision(level.name(), false, false,
                "重要个人信息禁入数据服务/CLI/MCP/模型输入（FR-2）", EngineDataLevel.D0, now);
        };
    }

    private EngineDataLevel parseLevel(String levelName) {
        if (levelName == null || levelName.isBlank()) {
            throw new ApiException(ErrorCode.BAD_REQUEST, "validatePrivacyPolicy 必须指定数据分级 target（D0–D5）");
        }
        try {
            return EngineDataLevel.valueOf(levelName.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException notALevel) {
            throw new ApiException(ErrorCode.BAD_REQUEST, "数据分级取值无效：" + levelName + "（须为 D0–D5）");
        }
    }
}
