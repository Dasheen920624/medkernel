package com.medkernel.engine.knowledge.delivery;

/**
 * 激活一个已通过预检的真实完整包。
 *
 * @param hospitalId 目标医院
 * @param preflightId 不可变预检标识
 * @param confirmedPreviewDigest 操作者确认的完整预览摘要
 * @param expectedCurrentReleaseId 首次激活为空，升级时为确认时的当前机构版本
 */
public record FullPackageActivationCommand(
    String hospitalId,
    String preflightId,
    String confirmedPreviewDigest,
    String expectedCurrentReleaseId
) {
}
