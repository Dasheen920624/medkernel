package com.medkernel.engine.knowledge.production.initialization;

import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.springframework.stereotype.Component;

/** KNOWGEN-01～35 的稳定初始化发行目录与 F0～F8 依赖顺序。 */
@Component
public class KnowledgeInitializationCatalog {

    private static final List<KnowledgeInitializationCatalogItem> ITEMS = List.of(
        item(29, "权威来源、许可与适用范围目录", InitializationReleaseType.FOUNDATION, InitializationPhase.F0),
        item(1, "标准术语", InitializationReleaseType.FOUNDATION, InitializationPhase.F1),
        item(26, "医疗数据元与上下文字段目录", InitializationReleaseType.FOUNDATION, InitializationPhase.F1),
        item(27, "基础编码系统、值集、单位与系统字典", InitializationReleaseType.FOUNDATION, InitializationPhase.F1),
        item(28, "医疗主数据与互操作基线", InitializationReleaseType.FOUNDATION, InitializationPhase.F1),
        item(25, "临床证据分级库", InitializationReleaseType.FOUNDATION, InitializationPhase.F2),
        item(2, "药品说明书事实", InitializationReleaseType.CLINICAL_CONTENT, InitializationPhase.F3),
        item(3, "国家与学会指南条款", InitializationReleaseType.CLINICAL_CONTENT, InitializationPhase.F3),
        item(20, "医疗核心制度核查", InitializationReleaseType.CLINICAL_CONTENT, InitializationPhase.F3),
        item(7, "评估指标", InitializationReleaseType.COMPOSITE, InitializationPhase.F4),
        item(16, "医学评分量表与计算器", InitializationReleaseType.COMPOSITE, InitializationPhase.F4),
        item(18, "检查检验适当性", InitializationReleaseType.COMPOSITE, InitializationPhase.F4),
        item(30, "可复用执行构件", InitializationReleaseType.COMPOSITE, InitializationPhase.F4),
        item(4, "临床规则", InitializationReleaseType.COMPOSITE, InitializationPhase.F5),
        item(19, "特殊人群剂量与药物基因组", InitializationReleaseType.COMPOSITE, InitializationPhase.F5),
        item(22, "急救与生命支持", InitializationReleaseType.CLINICAL_CONTENT, InitializationPhase.F5),
        item(23, "围术期、麻醉与输血", InitializationReleaseType.CLINICAL_CONTENT, InitializationPhase.F5),
        item(35, "器械耗材与设备安全", InitializationReleaseType.CLINICAL_CONTENT, InitializationPhase.F5),
        item(5, "专病路径", InitializationReleaseType.COMPOSITE, InitializationPhase.F6),
        item(6, "CDSS 推荐模板", InitializationReleaseType.COMPOSITE, InitializationPhase.F6),
        item(8, "随访计划", InitializationReleaseType.COMPOSITE, InitializationPhase.F6),
        item(9, "护理资产", InitializationReleaseType.CLINICAL_CONTENT, InitializationPhase.F6),
        item(10, "医技报告解读", InitializationReleaseType.CLINICAL_CONTENT, InitializationPhase.F6),
        item(11, "床旁知识卡", InitializationReleaseType.CLINICAL_CONTENT, InitializationPhase.F6),
        item(12, "中医药资产", InitializationReleaseType.CLINICAL_CONTENT, InitializationPhase.F6),
        item(13, "医保病案资产", InitializationReleaseType.CLINICAL_CONTENT, InitializationPhase.F6),
        item(14, "公卫与院感资产", InitializationReleaseType.CLINICAL_CONTENT, InitializationPhase.F6),
        item(17, "鉴别诊断知识库", InitializationReleaseType.CLINICAL_CONTENT, InitializationPhase.F6),
        item(21, "罕见病知识库", InitializationReleaseType.CLINICAL_CONTENT, InitializationPhase.F6),
        item(24, "患教材料与知情同意模板", InitializationReleaseType.CLINICAL_CONTENT, InitializationPhase.F6),
        item(33, "全生命周期预防与特殊人群", InitializationReleaseType.CLINICAL_CONTENT, InitializationPhase.F6),
        item(34, "联合照护", InitializationReleaseType.CLINICAL_CONTENT, InitializationPhase.F6),
        item(31, "机构临床参数与本地化模板", InitializationReleaseType.COMPOSITE, InitializationPhase.F7),
        item(15, "基础知识资产总验收", InitializationReleaseType.FOUNDATION, InitializationPhase.F8),
        item(32, "知识金标回归与发行验收", InitializationReleaseType.FOUNDATION, InitializationPhase.F8)
    );

    public List<KnowledgeInitializationCatalogItem> listAll() {
        return ITEMS;
    }

    public Optional<KnowledgeInitializationCatalogItem> find(String catalogCode) {
        if (catalogCode == null) {
            return Optional.empty();
        }
        return ITEMS.stream()
            .filter(item -> item.catalogCode().equalsIgnoreCase(catalogCode.trim()))
            .findFirst();
    }

    public Set<FoundationCoverageDimension> requiredFoundationCoverage() {
        return Set.copyOf(Arrays.asList(FoundationCoverageDimension.values()));
    }

    public Set<String> requiredFoundationCatalogCodes() {
        return ITEMS.stream()
            .filter(item -> item.releaseType() == InitializationReleaseType.FOUNDATION)
            .map(KnowledgeInitializationCatalogItem::catalogCode)
            .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
    }

    private static KnowledgeInitializationCatalogItem item(
            int number,
            String title,
            InitializationReleaseType releaseType,
            InitializationPhase phase) {
        return new KnowledgeInitializationCatalogItem(
            "KNOWGEN-%02d".formatted(number),
            title,
            releaseType,
            phase);
    }
}
