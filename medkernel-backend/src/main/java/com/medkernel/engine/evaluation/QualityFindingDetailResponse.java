package com.medkernel.engine.evaluation;

import java.util.List;

/**
 * 质量问题详情响应。
 *
 * <p>聚合问题本体、当前整改任务和按时间排序的复核记录，供质控闭环详情页使用。
 */
public record QualityFindingDetailResponse(
    QualityFinding finding,
    RectificationTask rectificationTask,
    List<RectificationReview> reviews
) {}
