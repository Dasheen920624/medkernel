package com.medkernel.engine.datasvc;

import java.time.Instant;
import java.util.List;

/**
 * 知识检索结果（DATASVC-01 PR2-c，受控工具 {@code searchKnowledge} 的 D1 输出）。
 *
 * <p>按关键词检索知识身份的 D1 已发布资产元数据列表，服务端分页。{@code degraded} 为上游不可用时的诚实降级标记
 * （空结果不以伪装真实无匹配，铁律 #1）；真实无匹配＝ {@code total=0} 且 {@code degraded=false}。
 */
public record KnowledgeSearchResult(
    EngineDataLevel dataLevel,
    long total,
    int page,
    int size,
    List<KnowledgeSearchHit> hits,
    Instant generatedAt,
    boolean degraded,
    String degradeReason
) {}
