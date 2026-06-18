package com.medkernel.shared.config;

/**
 * 特权配置变更授权端口。
 *
 * <p>实现方只能信任认证链产生的权限信息，不得信任请求体或调用方传入的角色声明。
 */
public interface PrivilegedConfigChangeGuard {

    void assertSystemSuperAdminAllowed(String resourceType, String resourceId);
}
