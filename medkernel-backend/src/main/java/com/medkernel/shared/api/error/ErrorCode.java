package com.medkernel.shared.api.error;

import java.util.Arrays;
import java.util.Optional;

/**
 * MedKernel v1.0 GA 统一错误码。
 *
 * <p>命名前缀：
 * <ul>
 *   <li>{@code ENG-API-*}：API 契约（参数、鉴权、HTTP 语义）</li>
 *   <li>{@code ENG-BASE-*}：基础底座（租户、组织、权限上下文）</li>
 *   <li>{@code ENG-SYS-*}：系统级（内部错误、下游故障）</li>
 *   <li>{@code ENG-OBS-*}：可观测性骨干（GA-ENG-OBS-01）</li>
 *   <li>{@code ENG-CONTEXT-*}、{@code ENG-EVENT-*} 等业务域</li>
 * </ul>
 *
 * <p>每个 ErrorCode 含 errorClass（INPUT/AUTH/DATA/EXTERNAL/INTERNAL）+ retryable，
 * 用于客户端决策与状态历史持久化分类。
 *
 * <p>code 一旦发布对客户端可见，禁止改名；只能新增或废弃（标记 @Deprecated 并保留）。
 */
public enum ErrorCode {

    OK("OK", 200, "操作成功", ErrorClass.INTERNAL, false), // OK 非错误，errorClass 占位

    BAD_REQUEST("ENG-API-001", 400, "请求参数无效", ErrorClass.INPUT, false),
    VALIDATION_FAILED("ENG-API-002", 400, "请求参数校验失败", ErrorClass.INPUT, false),
    UNAUTHORIZED("ENG-API-003", 401, "未授权访问", ErrorClass.AUTH, false),
    FORBIDDEN("ENG-API-004", 403, "无权限执行该操作", ErrorClass.AUTH, false),
    NOT_FOUND("ENG-API-005", 404, "资源不存在", ErrorClass.DATA, false),
    METHOD_NOT_ALLOWED("ENG-API-006", 405, "方法不允许", ErrorClass.INPUT, false),
    CONFLICT("ENG-API-007", 409, "资源冲突", ErrorClass.DATA, false),
    TOO_MANY_REQUESTS("ENG-API-008", 429, "请求过于频繁，请稍后重试", ErrorClass.INPUT, true),
    UNSUPPORTED_MEDIA_TYPE("ENG-API-009", 415, "不支持的请求媒体类型", ErrorClass.INPUT, false),

    TENANT_CONTEXT_MISSING("ENG-BASE-001", 400, "租户上下文缺失", ErrorClass.AUTH, false),
    TENANT_FORBIDDEN("ENG-BASE-002", 403, "无权访问该租户数据", ErrorClass.AUTH, false),
    DATA_SCOPE_DENIED("ENG-BASE-003", 403, "数据范围权限不足", ErrorClass.AUTH, false),

    INTERNAL_ERROR("ENG-SYS-001", 500, "服务内部错误", ErrorClass.INTERNAL, false),
    DOWNSTREAM_UNAVAILABLE("ENG-SYS-002", 503, "下游服务不可用", ErrorClass.EXTERNAL, true),
    MODEL_DEGRADED("ENG-SYS-003", 503, "AI 模型不可用，已降级到无模型基线", ErrorClass.EXTERNAL, true),

    ENG_CONTEXT_001("ENG-CONTEXT-001", 400, "上下文 schema 校验失败", ErrorClass.INPUT, false),
    ENG_CONTEXT_002("ENG-CONTEXT-002", 400, "包版本不存在", ErrorClass.DATA, false),
    ENG_CONTEXT_003("ENG-CONTEXT-003", 400, "标准上下文 quality_status=INVALID 被拒绝", ErrorClass.DATA, false),
    ENG_CONTEXT_004("ENG-CONTEXT-004", 409, "幂等键冲突且 payload 不一致", ErrorClass.DATA, false),

    ENG_OBS_001("ENG-OBS-001", 404, "payload 不存在或已归档", ErrorClass.DATA, false),
    ENG_OBS_002("ENG-OBS-002", 500, "状态历史写入失败", ErrorClass.INTERNAL, false),

    ENG_EVENT_001("ENG-EVENT-001", 400, "事件 schema 校验失败", ErrorClass.INPUT, false),
    ENG_EVENT_002("ENG-EVENT-002", 409, "事件 ID 已存在且 payload 不一致", ErrorClass.INPUT, false),
    ENG_EVENT_003("ENG-EVENT-003", 404, "临床事件不存在", ErrorClass.DATA, false),
    ENG_EVENT_004("ENG-EVENT-004", 503, "payload 存储不可用", ErrorClass.EXTERNAL, true),
    ENG_EVENT_005("ENG-EVENT-005", 500, "事件处理失败已进入死信", ErrorClass.INTERNAL, false),
    ENG_EVENT_006("ENG-EVENT-006", 400, "当前状态不允许重放", ErrorClass.INPUT, false),

    ENG_RULE_001("ENG-RULE-001", 400, "规则 DSL 校验失败", ErrorClass.INPUT, false),
    ENG_RULE_002("ENG-RULE-002", 404, "规则不存在", ErrorClass.DATA, false),
    ENG_RULE_003("ENG-RULE-003", 404, "规则版本不存在", ErrorClass.DATA, false),
    ENG_RULE_004("ENG-RULE-004", 409, "发布门禁失败", ErrorClass.DATA, false),
    ENG_RULE_005("ENG-RULE-005", 500, "规则执行失败", ErrorClass.INTERNAL, false),
    ENG_RULE_006("ENG-RULE-006", 409, "当前规则状态不允许该操作", ErrorClass.DATA, false),

    ENG_PATHWAY_001("ENG-PATHWAY-001", 400, "路径模板校验失败", ErrorClass.INPUT, false),
    ENG_PATHWAY_002("ENG-PATHWAY-002", 404, "路径模板不存在", ErrorClass.DATA, false),
    ENG_PATHWAY_003("ENG-PATHWAY-003", 404, "患者路径不存在", ErrorClass.DATA, false),
    ENG_PATHWAY_004("ENG-PATHWAY-004", 409, "路径模板发布门禁失败", ErrorClass.DATA, false),
    ENG_PATHWAY_005("ENG-PATHWAY-005", 409, "当前路径状态不允许该操作", ErrorClass.DATA, false),
    ENG_PATHWAY_006("ENG-PATHWAY-006", 400, "路径推进事件不合法", ErrorClass.INPUT, false),
    ENG_PATHWAY_007("ENG-PATHWAY-007", 404, "专病包不存在", ErrorClass.DATA, false),

    ENG_REC_001("ENG-REC-001", 400, "推荐触发请求校验失败", ErrorClass.INPUT, false),
    ENG_REC_002("ENG-REC-002", 404, "推荐触发不存在", ErrorClass.DATA, false),
    ENG_REC_003("ENG-REC-003", 404, "推荐卡不存在", ErrorClass.DATA, false),
    ENG_REC_004("ENG-REC-004", 409, "推荐卡当前状态不允许反馈", ErrorClass.DATA, false),
    ENG_REC_005("ENG-REC-005", 400, "推荐来源解释不完整", ErrorClass.INPUT, false),
    ENG_REC_006("ENG-REC-006", 409, "高风险推荐缺少医师确认门禁", ErrorClass.DATA, false),

    ENG_EVAL_001("ENG-EVAL-001", 400, "评估指标或运行请求校验失败", ErrorClass.INPUT, false),
    ENG_EVAL_002("ENG-EVAL-002", 404, "评估指标不存在", ErrorClass.DATA, false),
    ENG_EVAL_003("ENG-EVAL-003", 409, "当前指标状态不允许该操作", ErrorClass.DATA, false),
    ENG_EVAL_004("ENG-EVAL-004", 409, "评估运行引用了未激活指标", ErrorClass.DATA, false),
    ENG_EVAL_005("ENG-EVAL-005", 404, "质控问题或整改任务不存在", ErrorClass.DATA, false),
    ENG_EVAL_006("ENG-EVAL-006", 400, "高风险质控问题缺少责任、期限或证据", ErrorClass.INPUT, false),
    ENG_EVAL_007("ENG-EVAL-007", 409, "整改或复核状态冲突", ErrorClass.DATA, false),
    ENG_EVAL_008("ENG-EVAL-008", 409, "整改或复核幂等键与请求内容冲突", ErrorClass.DATA, false),

    ENG_PACKAGE_001("ENG-PACKAGE-001", 404, "知识包或同步目标不存在", ErrorClass.DATA, false),
    ENG_PACKAGE_002("ENG-PACKAGE-002", 400, "包资产状态不合法", ErrorClass.DATA, false),
    ENG_PACKAGE_003("ENG-PACKAGE-003", 400, "无效的发布灰度范围或发布策略", ErrorClass.INPUT, false),
    ENG_PACKAGE_004("ENG-PACKAGE-004", 409, "包发布门禁校验失败", ErrorClass.DATA, false),
    ENG_PACKAGE_005("ENG-PACKAGE-005", 500, "投影目标同步部分或全部失败", ErrorClass.EXTERNAL, true),
    
    ENG_FOLLOW_001("ENG-FOLLOW-001", 400, "随访计划生成请求无效", ErrorClass.INPUT, false),
    ENG_FOLLOW_002("ENG-FOLLOW-002", 404, "随访计划不存在", ErrorClass.DATA, false),
    ENG_FOLLOW_003("ENG-FOLLOW-003", 404, "随访任务不存在", ErrorClass.DATA, false),
    ENG_FOLLOW_004("ENG-FOLLOW-004", 409, "当前随访状态不允许该操作", ErrorClass.DATA, false),
    ENG_FOLLOW_005("ENG-FOLLOW-005", 500, "随访异常事件上报失败", ErrorClass.INTERNAL, false),

    ENG_EMBED_001("ENG-EMBED-001", 400, "启动令牌无效或已过期", ErrorClass.INPUT, false),
    ENG_EMBED_002("ENG-EMBED-002", 400, "非法的 Origin 域名", ErrorClass.INPUT, false),
    ENG_EMBED_003("ENG-EMBED-003", 409, "启动令牌已被使用", ErrorClass.DATA, false),
    ENG_EMBED_004("ENG-EMBED-004", 404, "启动令牌不存在", ErrorClass.DATA, false),
    ENG_LLM_001("ENG-LLM-001", 400, "能力尚未在该组织激活或不可用", ErrorClass.INPUT, false),
    ENG_LLM_002("ENG-LLM-002", 422, "模型输出不匹配期望结构Schema", ErrorClass.DATA, false),
    ENG_LLM_003("ENG-LLM-003", 504, "调用模型接口超时", ErrorClass.EXTERNAL, true),
    ENG_LLM_004("ENG-LLM-004", 404, "模型调用任务不存在", ErrorClass.DATA, false),
    ENG_LLM_005("ENG-LLM-005", 400, "数据包含敏感字段且未配置脱敏策略", ErrorClass.INPUT, false),
    ENG_LIST_001("ENG-LIST-001", 400, "非法的列表资源类型或排序字段", ErrorClass.INPUT, false),
    ENG_LIST_002("ENG-LIST-002", 404, "异步导出任务不存在", ErrorClass.DATA, false),
    ENG_LIST_003("ENG-LIST-003", 409, "导出任务尚未完成，无法提供下载", ErrorClass.DATA, false),
    ENG_LIST_004("ENG-LIST-004", 500, "文件导出物理 IO 失败", ErrorClass.INTERNAL, false),
    SORT_FIELD_NOT_ALLOWED("ENG-LIST-005", 400, "排序字段不在大规模列表白名单内", ErrorClass.INPUT, false),
    PAGE_SIZE_EXCEEDED("ENG-LIST-006", 400, "请求页大小超过大规模列表上限", ErrorClass.INPUT, false),
    FILTER_FIELD_NOT_ALLOWED("ENG-LIST-007", 400, "过滤字段不在大规模列表白名单内", ErrorClass.INPUT, false),
    
    ENG_INTEG_001("ENG-INTEG-001", 400, "集成适配器配置不合法", ErrorClass.INPUT, false),
    ENG_INTEG_002("ENG-INTEG-002", 404, "集成适配器不存在", ErrorClass.DATA, false),
    ENG_INTEG_003("ENG-INTEG-003", 404, "Webhook 订阅不存在", ErrorClass.DATA, false),
    ENG_INTEG_004("ENG-INTEG-004", 400, "Webhook 消息签名校验失败", ErrorClass.AUTH, false),
    ENG_INTEG_005("ENG-INTEG-005", 404, "集成接口日志不存在", ErrorClass.DATA, false),
    ENG_INTEG_006("ENG-INTEG-006", 409, "当前接口日志状态不允许重试", ErrorClass.DATA, false),

    ENG_EVID_001("ENG-EVID-001", 404, "证据快照不存在", ErrorClass.DATA, false),
    ENG_EVID_002("ENG-EVID-002", 400, "防伪数字指纹检验失败，数据已被篡改", ErrorClass.AUTH, false),
    ENG_EVID_003("ENG-EVID-003", 409, "证据快照已存在，无法重复创建", ErrorClass.DATA, false),
    ENG_KNOW_001("ENG-KNOW-001", 404, "来源文献或版本不存在", ErrorClass.DATA, false),
    ENG_KNOW_002("ENG-KNOW-002", 409, "知识版本内容指纹冲突已存在", ErrorClass.DATA, false),
    ENG_AUTH_001("ENG-AUTH-001", 401, "用户名或密码不正确", ErrorClass.AUTH, false),
    ENG_AUTH_002("ENG-AUTH-002", 403, "账号已被禁用或锁定", ErrorClass.AUTH, false),
    ENG_AUTH_003("ENG-AUTH-003", 400, "登录请求参数不合法", ErrorClass.INPUT, false),
    ENG_AUTH_004("ENG-AUTH-004", 400, "原密码不正确", ErrorClass.AUTH, false),
    ENG_AUTH_005("ENG-AUTH-005", 404, "账号不存在", ErrorClass.DATA, false),
    ENG_AUTH_006("ENG-AUTH-006", 409, "用户名在该租户下已存在", ErrorClass.DATA, false),
    ENG_AUTH_007("ENG-AUTH-007", 401, "初始化 token 无效", ErrorClass.AUTH, false),
    ENG_AUTH_008("ENG-AUTH-008", 401, "初始化 token 已过期", ErrorClass.AUTH, false),
    ENG_AUTH_009("ENG-AUTH-009", 409, "初始化 token 已使用或已撤销", ErrorClass.AUTH, false),
    ENG_AUTH_010("ENG-AUTH-010", 403, "高危操作需要先绑定 MFA", ErrorClass.AUTH, false),
    ENG_AUTH_011("ENG-AUTH-011", 400, "应急命令缺少确认短语", ErrorClass.AUTH, false),
    ENG_TENANT_001("ENG-TENANT-001", 409, "租户已存在，无法重复开通", ErrorClass.DATA, false),
    ENG_CONFIG_001("ENG-CONFIG-001", 403, "高危运行配置禁止从界面关闭", ErrorClass.AUTH, false),
    ENG_CONFIG_002("ENG-CONFIG-002", 409, "高危配置变更需要二次确认和变更原因", ErrorClass.AUTH, false),
    ENG_AUDIT_001("ENG-AUDIT-001", 403, "审计持久化为高危配置，禁止从界面关闭", ErrorClass.AUTH, false);

    private final String code;
    private final int httpStatus;
    private final String defaultMessage;
    private final ErrorClass errorClass;
    private final boolean retryable;

    ErrorCode(String code, int httpStatus, String defaultMessage,
              ErrorClass errorClass, boolean retryable) {
        this.code = code;
        this.httpStatus = httpStatus;
        this.defaultMessage = defaultMessage;
        this.errorClass = errorClass;
        this.retryable = retryable;
    }

    public String code() {
        return code;
    }

    public int httpStatus() {
        return httpStatus;
    }

    public String defaultMessage() {
        return defaultMessage;
    }

    public ErrorClass errorClass() {
        return errorClass;
    }

    public boolean retryable() {
        return retryable;
    }

    public static Optional<ErrorCode> fromCode(String code) {
        if (code == null) {
            return Optional.empty();
        }
        String normalized = code.trim();
        return Arrays.stream(values())
            .filter(c -> c.code.equalsIgnoreCase(normalized))
            .findFirst();
    }

    public enum ErrorClass {
        /** 输入数据问题：客户端可修复 */
        INPUT,
        /** 权限/认证问题 */
        AUTH,
        /** 业务数据不一致：管理员排查 */
        DATA,
        /** 外部依赖：可重试 */
        EXTERNAL,
        /** 系统内部错误：研发排查 */
        INTERNAL
    }
}
