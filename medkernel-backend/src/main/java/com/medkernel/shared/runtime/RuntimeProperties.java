package com.medkernel.shared.runtime;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * `medkernel.runtime.*` 运行底座配置。
 */
@Component
@ConfigurationProperties(prefix = "medkernel.runtime")
public class RuntimeProperties {

    private String environment = "dev";
    private String deploymentMode = "local";
    private String databaseDialect = "h2";
    private String migrationLocation = "classpath:db/migration/h2";
    private Map<String, FeatureFlag> featureFlags = new LinkedHashMap<>();
    private Backup backup = new Backup();
    private DomesticProfile domesticProfile = new DomesticProfile();

    public String getEnvironment() {
        return environment;
    }

    public void setEnvironment(String environment) {
        this.environment = environment;
    }

    public String getDeploymentMode() {
        return deploymentMode;
    }

    public void setDeploymentMode(String deploymentMode) {
        this.deploymentMode = deploymentMode;
    }

    public String getDatabaseDialect() {
        return databaseDialect;
    }

    public void setDatabaseDialect(String databaseDialect) {
        this.databaseDialect = databaseDialect;
    }

    public String getMigrationLocation() {
        return migrationLocation;
    }

    public void setMigrationLocation(String migrationLocation) {
        this.migrationLocation = migrationLocation;
    }

    public Map<String, FeatureFlag> getFeatureFlags() {
        return featureFlags;
    }

    public void setFeatureFlags(Map<String, FeatureFlag> featureFlags) {
        this.featureFlags = featureFlags == null ? new LinkedHashMap<>() : featureFlags;
    }

    public Backup getBackup() {
        return backup;
    }

    public void setBackup(Backup backup) {
        this.backup = backup == null ? new Backup() : backup;
    }

    public DomesticProfile getDomesticProfile() {
        return domesticProfile;
    }

    public void setDomesticProfile(DomesticProfile domesticProfile) {
        this.domesticProfile = domesticProfile == null ? new DomesticProfile() : domesticProfile;
    }

    public static class FeatureFlag {
        private String displayName;
        private boolean enabled;
        private String risk = "LOW";
        private String owner = "信息科";
        private String description = "";

        public String getDisplayName() {
            return displayName;
        }

        public void setDisplayName(String displayName) {
            this.displayName = displayName;
        }

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public String getRisk() {
            return risk;
        }

        public void setRisk(String risk) {
            this.risk = risk;
        }

        public String getOwner() {
            return owner;
        }

        public void setOwner(String owner) {
            this.owner = owner;
        }

        public String getDescription() {
            return description;
        }

        public void setDescription(String description) {
            this.description = description;
        }
    }

    public static class Backup {
        private boolean enabled;
        private String rpo = "未启用";
        private String rto = "未启用";
        private String backupScript = "./deploy/docker/scripts/backup.sh";
        private String restoreScript = "./deploy/docker/scripts/restore.sh";
        private String checksumPolicy = "SHA-256 摘要随备份文件生成，恢复前自动校验";
        private String drillEvidenceFile = "";

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public String getRpo() {
            return rpo;
        }

        public void setRpo(String rpo) {
            this.rpo = rpo;
        }

        public String getRto() {
            return rto;
        }

        public void setRto(String rto) {
            this.rto = rto;
        }

        public String getBackupScript() {
            return backupScript;
        }

        public void setBackupScript(String backupScript) {
            this.backupScript = backupScript;
        }

        public String getRestoreScript() {
            return restoreScript;
        }

        public void setRestoreScript(String restoreScript) {
            this.restoreScript = restoreScript;
        }

        public String getChecksumPolicy() {
            return checksumPolicy;
        }

        public void setChecksumPolicy(String checksumPolicy) {
            this.checksumPolicy = checksumPolicy;
        }

        public String getDrillEvidenceFile() {
            return drillEvidenceFile;
        }

        public void setDrillEvidenceFile(String drillEvidenceFile) {
            this.drillEvidenceFile = drillEvidenceFile;
        }
    }

    public static class DomesticProfile {
        private String targetOs = "麒麟 / 统信 / openEuler";
        private String targetJdk = "KAE-JDK 21 / BiSheng JDK 21";
        private List<String> databaseVendors = List.of("达梦", "人大金仓");
        private List<String> cryptoAlgorithms = List.of("SM2", "SM3", "SM4");
        private String evidence = "国产化自检、五方言迁移合同、国密算法 smoke";

        public String getTargetOs() {
            return targetOs;
        }

        public void setTargetOs(String targetOs) {
            this.targetOs = targetOs;
        }

        public String getTargetJdk() {
            return targetJdk;
        }

        public void setTargetJdk(String targetJdk) {
            this.targetJdk = targetJdk;
        }

        public List<String> getDatabaseVendors() {
            return databaseVendors;
        }

        public void setDatabaseVendors(List<String> databaseVendors) {
            this.databaseVendors = databaseVendors == null ? List.of() : List.copyOf(databaseVendors);
        }

        public List<String> getCryptoAlgorithms() {
            return cryptoAlgorithms;
        }

        public void setCryptoAlgorithms(List<String> cryptoAlgorithms) {
            this.cryptoAlgorithms = cryptoAlgorithms == null ? List.of() : List.copyOf(cryptoAlgorithms);
        }

        public String getEvidence() {
            return evidence;
        }

        public void setEvidence(String evidence) {
            this.evidence = evidence;
        }
    }
}
