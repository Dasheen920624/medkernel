package com.medkernel.shared.config;

import java.util.Locale;

import org.springframework.boot.logging.LogLevel;
import org.springframework.boot.logging.LoggingSystem;
import org.springframework.stereotype.Component;

import com.medkernel.shared.api.error.ApiException;
import com.medkernel.shared.api.error.ErrorCode;

/**
 * 配置中心日志级别热应用器。
 */
@Component
public class RuntimeLogLevelManager {

    private static final String ROOT_LOGGER_KEY = "root";

    private final LoggingSystem loggingSystem;

    public RuntimeLogLevelManager(LoggingSystem loggingSystem) {
        this.loggingSystem = loggingSystem;
    }

    public boolean supports(String key) {
        return key != null && key.startsWith(SystemConfigService.LOGGING_LEVEL_PREFIX);
    }

    public void apply(SystemConfigItem item) {
        if (item == null || !supports(item.key())) {
            return;
        }
        loggingSystem.setLogLevel(loggerName(item.key()), parseLogLevel(item.value()));
    }

    static LogLevel parseLogLevel(String value) {
        try {
            return LogLevel.valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (RuntimeException ex) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, "日志级别仅允许 TRACE/DEBUG/INFO/WARN/ERROR/OFF");
        }
    }

    private static String loggerName(String key) {
        String logger = key.substring(SystemConfigService.LOGGING_LEVEL_PREFIX.length());
        return ROOT_LOGGER_KEY.equalsIgnoreCase(logger) ? LoggingSystem.ROOT_LOGGER_NAME : logger;
    }
}
