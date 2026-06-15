package com.medkernel.engine.factory;

import java.util.List;

import com.medkernel.engine.knowledge.KnowledgeDomain;
import com.medkernel.engine.versioning.VersionedAssetType;

/**
 * 全专业领域标准资产模板（AIK-STD-12 FR-1）。
 *
 * <p>按结构维 {@link VersionedAssetType} × 医学领域维 {@link KnowledgeDomain} 定位一个专业的标准资产结构。
 * 模板＝结构骨架（章节清单），不新建资产类型、不预填医学内容（守铁律 #1）。医学领域型模板
 * （assetType=KNOWLEDGE × domain）供知识审核台按领域匹配；结构型模板 domain 为空，供编著/生产工作台。
 *
 * @param professionCode 专业稳定码
 * @param displayName 专业中文名
 * @param assetType 资产结构类型
 * @param knowledgeDomain 医学领域（结构型模板为空）
 * @param sections 标准结构章节（有序，非空）
 */
public record ProfessionalAssetTemplate(
    String professionCode,
    String displayName,
    VersionedAssetType assetType,
    KnowledgeDomain knowledgeDomain,
    List<TemplateSection> sections
) {

    public ProfessionalAssetTemplate {
        sections = sections == null ? List.of() : List.copyOf(sections);
    }
}
