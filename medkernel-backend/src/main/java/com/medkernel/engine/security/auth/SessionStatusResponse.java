package com.medkernel.engine.security.auth;

/**
 * 当前登录会话状态响应。
 *
 * @param remainingSeconds          当前 JWT 距离过期的剩余秒数
 * @param idleTimeoutSeconds        前端无操作自动登出窗口，单位秒
 * @param warningSeconds            无操作超时前提醒窗口，单位秒
 * @param maxSessionSeconds         单次登录最大会话时长，单位秒
 * @param maxSessionRemainingSeconds 当前会话距离最大时长耗尽的剩余秒数
 * @param serverTime                服务端时间，ISO-8601 字符串
 */
public record SessionStatusResponse(
    long remainingSeconds,
    long idleTimeoutSeconds,
    long warningSeconds,
    long maxSessionSeconds,
    long maxSessionRemainingSeconds,
    String serverTime
) {}
