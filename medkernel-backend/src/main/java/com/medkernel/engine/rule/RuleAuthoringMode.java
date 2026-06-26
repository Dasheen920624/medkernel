package com.medkernel.engine.rule;

/**
 * 规则编写模式枚举（GA-ENG-API-05）。
 *
 * <p>取值含义：{@code TEMPLATE} 模板向导、{@code VISUAL} 可视化编辑器、{@code DSL} JSON DSL；
 * 当前规则编写能力以 {@code DSL} 为权威受控配置，模板向导和可视化编辑器枚举用于前台按能力开放。
 */
public enum RuleAuthoringMode {
    TEMPLATE,
    VISUAL,
    DSL
}
