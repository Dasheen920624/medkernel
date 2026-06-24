package com.medkernel.engine.datasvc;

/**
 * 引擎数据服务层数据分级 D0–D5（DATASVC-01，规范 §7）。
 *
 * <p>每次查询按数据级别 + 角色返回不同字段；脱敏在后端完成（前端/CLI/MCP/提示词不承担首要脱敏）。
 * D3/D4 落库患者相关字段须字段级加密；**D5 重要个人信息默认禁止进入数据服务/CLI/MCP/模型输入**。
 */
public enum EngineDataLevel {

    /** 系统运行元数据、工具状态、版本状态。 */
    D0("系统运行元数据"),
    /** 已发布知识/规则/路径/机构生效版本元数据。 */
    D1("已发布资产元数据"),
    /** 去患者标识的聚合统计（规则命中量、知识命中率、科室趋势）。 */
    D2("去标识聚合统计"),
    /** 去标识化病例上下文或样本（落库患者字段须字段级加密）。 */
    D3("去标识病例上下文"),
    /** 授权临床会话内最小患者上下文。 */
    D4("临床会话最小上下文"),
    /** 姓名/身份证/手机号/住址/完整病历原文等重要个人信息——默认禁入数据服务。 */
    D5("重要个人信息·禁入");

    private final String label;

    EngineDataLevel(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }
}
