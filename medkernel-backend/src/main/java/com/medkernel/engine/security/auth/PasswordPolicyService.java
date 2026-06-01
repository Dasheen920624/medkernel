package com.medkernel.engine.security.auth;

import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Service;

import com.medkernel.shared.api.ApiError;
import com.medkernel.shared.api.error.ApiException;
import com.medkernel.shared.api.error.ErrorCode;
import com.medkernel.shared.config.AuthPasswordPolicy;
import com.medkernel.shared.config.SystemConfigService;

/**
 * 平台账号强密码策略校验，策略值由配置中心热读取。
 */
@Service
public class PasswordPolicyService {

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final String UPPER = "ABCDEFGHJKMNPQRSTUVWXYZ";
    private static final String LOWER = "abcdefghijkmnpqrstuvwxyz";
    private static final String DIGIT = "23456789";
    private static final String SYMBOL = "@#%!";
    private static final String ALPHABET = UPPER + LOWER + DIGIT + SYMBOL;
    private static final int DEFAULT_TEMP_PASSWORD_LENGTH = 12;

    private final SystemConfigService configService;

    public PasswordPolicyService(SystemConfigService configService) {
        this.configService = configService;
    }

    public AuthPasswordPolicy currentPolicy() {
        return configService.runtimeAuthPasswordPolicy();
    }

    public void assertCompliant(String password) {
        AuthPasswordPolicy policy = currentPolicy();
        List<ApiError> errors = violations(password, policy);
        if (!errors.isEmpty()) {
            throw new ApiException(
                ErrorCode.PWD_POLICY_VIOLATION,
                "新密码不符合强密码策略",
                errors,
                null);
        }
    }

    public String generateTemporaryPassword() {
        AuthPasswordPolicy policy = currentPolicy();
        int length = Math.max(DEFAULT_TEMP_PASSWORD_LENGTH, policy.minLength());
        StringBuilder sb = new StringBuilder(length);
        if (policy.requireUppercase()) {
            sb.append(randomChar(UPPER));
        }
        if (policy.requireLowercase()) {
            sb.append(randomChar(LOWER));
        }
        if (policy.requireDigit()) {
            sb.append(randomChar(DIGIT));
        }
        if (policy.requireSymbol()) {
            sb.append(randomChar(SYMBOL));
        }
        while (sb.length() < length) {
            sb.append(randomChar(ALPHABET));
        }
        shuffle(sb);
        String password = sb.toString();
        assertCompliant(password);
        return password;
    }

    private List<ApiError> violations(String password, AuthPasswordPolicy policy) {
        List<ApiError> errors = new ArrayList<>();
        String value = password == null ? "" : password;
        if (value.length() < policy.minLength()) {
            errors.add(error("minLength", "新密码至少 " + policy.minLength() + " 位"));
        }
        if (policy.requireUppercase() && value.chars().noneMatch(Character::isUpperCase)) {
            errors.add(error("uppercase", "新密码必须包含大写字母"));
        }
        if (policy.requireLowercase() && value.chars().noneMatch(Character::isLowerCase)) {
            errors.add(error("lowercase", "新密码必须包含小写字母"));
        }
        if (policy.requireDigit() && value.chars().noneMatch(Character::isDigit)) {
            errors.add(error("digit", "新密码必须包含数字"));
        }
        if (policy.requireSymbol() && value.chars().noneMatch(ch -> !Character.isLetterOrDigit(ch))) {
            errors.add(error("symbol", "新密码必须包含符号"));
        }
        return errors;
    }

    private ApiError error(String rule, String message) {
        return new ApiError("newPassword", rule, message);
    }

    private char randomChar(String alphabet) {
        return alphabet.charAt(RANDOM.nextInt(alphabet.length()));
    }

    private void shuffle(StringBuilder sb) {
        for (int i = sb.length() - 1; i > 0; i--) {
            int j = RANDOM.nextInt(i + 1);
            char left = sb.charAt(i);
            sb.setCharAt(i, sb.charAt(j));
            sb.setCharAt(j, left);
        }
    }
}
