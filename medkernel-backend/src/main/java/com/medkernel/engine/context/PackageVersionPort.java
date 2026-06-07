package com.medkernel.engine.context;

import java.util.Optional;

/**
 * 包版本解析端口。
 *
 * <p>抽象层让上下文域不直接依赖配置包持久化细节，但所有判断必须来自关系库权威包记录。
 */
public interface PackageVersionPort {

    /** 包版本是否存在且是当前可运行的激活版本。 */
    boolean exists(String tenantId, String version);

    /** 当前租户最新激活的统一配置包版本；未注册返回 empty。 */
    Optional<String> getActive(String tenantId);
}
