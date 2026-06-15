package com.medkernel.engine.factory;

import com.medkernel.engine.knowledge.SourceAuthorityLevel;

/**
 * 资产信封的来源/引用绑定（AIK-STD-01，FR-2 来源 + 可信分级）。
 *
 * <p>每条断言须绑定 ≥1 来源（核心 §7 来源可溯 / 铁律 #1 无源拒收）；{@code authorityLevel} 取 OPT-07 五级权威。
 *
 * @param sourceRef 来源标识（来源文档 / 片段锚点引用），非空
 * @param authorityLevel 来源权威分级（A 法规…E 反馈），非空
 */
public record AssetSourceRef(
    String sourceRef,
    SourceAuthorityLevel authorityLevel
) {
}
