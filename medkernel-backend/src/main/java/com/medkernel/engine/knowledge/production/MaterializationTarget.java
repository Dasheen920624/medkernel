package com.medkernel.engine.knowledge.production;

import jakarta.validation.Valid;

/**
 * 物化目标知识身份（AIK-STD-13）：生产方显式声明——现有身份 id 异或新建身份壳，二选一。
 *
 * <p>「候选属哪个知识主题」是语义决定（核心 §7 唯一权威），由生产方/操作者显式声明，物化遂为纯机械 B0；
 * 自动语义分流（候选→主题推断）属解析管道（AIK-STD-04/10），不在本卡。
 */
public record MaterializationTarget(
    Long targetIdentityId,
    @Valid NewIdentitySpec newIdentity
) {
    public void validate() {
        boolean hasExisting = targetIdentityId != null;
        boolean hasNew = newIdentity != null;
        if (hasExisting == hasNew) {
            throw new IllegalArgumentException("物化目标须二选一：targetIdentityId 或 newIdentity");
        }
    }
}
