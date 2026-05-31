package com.medkernel.shared.config;

import java.time.Instant;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import com.medkernel.shared.runtime.RuntimeProperties;

/**
 * 启动期把 YAML 默认配置导入关系库配置中心。
 */
@Component
public class SystemConfigSeeder implements ApplicationRunner {

    private final RuntimeProperties runtimeProperties;
    private final SystemConfigService service;

    public SystemConfigSeeder(RuntimeProperties runtimeProperties, SystemConfigService service) {
        this.runtimeProperties = runtimeProperties;
        this.service = service;
    }

    @Override
    public void run(ApplicationArguments args) {
        Instant seededAt = Instant.now();
        runtimeProperties.getFeatureFlags().entrySet().stream()
            .sorted(java.util.Map.Entry.comparingByKey())
            .forEach(entry -> {
                RuntimeProperties.FeatureFlag flag = entry.getValue();
                service.seed(new SystemConfigSeed(
                    SystemConfigService.SYSTEM_TENANT,
                    SystemConfigService.RUNTIME_FLAG_PREFIX + entry.getKey() + SystemConfigService.RUNTIME_FLAG_SUFFIX,
                    Boolean.toString(flag.isEnabled()),
                    "BOOLEAN",
                    flag.getDisplayName(),
                    flag.getRisk(),
                    flag.getOwner(),
                    flag.getDescription(),
                    "YML_SEED",
                    "HIGH".equalsIgnoreCase(flag.getRisk()),
                    seededAt), "system");
            });
        seedBackupPolicy(seededAt);
    }

    private void seedBackupPolicy(Instant seededAt) {
        RuntimeProperties.Backup backup = runtimeProperties.getBackup();
        seedBackupValue("enabled", Boolean.toString(backup.isEnabled()), "BOOLEAN", "备份策略启用", "MEDIUM",
            "控制运行形态是否启用数据库备份策略，启用后仍需真实恢复演练证据。", seededAt);
        seedBackupValue("rpo", backup.getRpo(), "STRING", "备份 RPO", "MEDIUM",
            "配置备份恢复点目标，用于运维验收和恢复演练。", seededAt);
        seedBackupValue("rto", backup.getRto(), "STRING", "备份 RTO", "MEDIUM",
            "配置恢复时间目标，用于运维验收和恢复演练。", seededAt);
        seedBackupValue("backup-script", backup.getBackupScript(), "STRING", "备份脚本", "MEDIUM",
            "当前部署形态使用的真实备份脚本路径。", seededAt);
        seedBackupValue("restore-script", backup.getRestoreScript(), "STRING", "恢复脚本", "MEDIUM",
            "当前部署形态使用的真实恢复脚本路径。", seededAt);
        seedBackupValue("checksum-policy", backup.getChecksumPolicy(), "STRING", "备份校验策略", "MEDIUM",
            "备份文件生成与恢复前校验使用的摘要策略。", seededAt);
    }

    private void seedBackupValue(String key,
                                 String value,
                                 String valueType,
                                 String displayName,
                                 String risk,
                                 String description,
                                 Instant seededAt) {
        service.seed(new SystemConfigSeed(
            SystemConfigService.SYSTEM_TENANT,
            SystemConfigService.RUNTIME_BACKUP_PREFIX + key,
            value,
            valueType,
            displayName,
            risk,
            "信息科 / 运维组",
            description,
            "YML_SEED",
            false,
            seededAt), "system");
    }
}
