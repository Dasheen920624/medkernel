package com.medkernel.engine.org;

/**
 * 组织用户目录选择项，仅暴露业务操作所需的用户标识与显示名称。
 */
public record OrgUserDirectoryItem(
    String userId,
    String displayName
) {
}
