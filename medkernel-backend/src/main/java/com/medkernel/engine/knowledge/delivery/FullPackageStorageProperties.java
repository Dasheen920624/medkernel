package com.medkernel.engine.knowledge.delivery;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.ConstructorBinding;

/**
 * 平台完整医疗资源包受管存储配置。
 *
 * @param root 受管目录根；只保存无凭据本地路径，可由部署环境覆盖
 */
@ConfigurationProperties(prefix = "medkernel.knowledge.package-storage")
public record FullPackageStorageProperties(String root) {

    @ConstructorBinding
    public FullPackageStorageProperties {
        if (root == null || root.isBlank()) {
            root = "./data/knowledge-packages";
        }
    }

    public FullPackageStorageProperties() {
        this("./data/knowledge-packages");
    }
}
