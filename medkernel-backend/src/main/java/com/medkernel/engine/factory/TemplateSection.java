package com.medkernel.engine.factory;

/**
 * 专业资产模板的结构章节（AIK-STD-12 FR-1）。
 *
 * <p>仅承载结构骨架元数据——章节名取自既有专业文书结构标准（如药品说明书法定项、护理程序五步），
 * 供生产/审核对照核查完整性。本类不含任何医学内容，正文须按真实来源填充（守铁律 #1）。
 *
 * @param key 稳定章节码
 * @param label 章节中文名
 * @param required 该专业资产是否必备此章节
 * @param hint 结构填写提示
 */
public record TemplateSection(String key, String label, boolean required, String hint) {
}
