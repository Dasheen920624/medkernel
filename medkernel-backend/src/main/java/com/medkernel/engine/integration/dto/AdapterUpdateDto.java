package com.medkernel.engine.integration.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/**
 * 外部第三方对接适配器更新 DTO Record。
 *
 * <p>用于封装异构系统适配器在更新时的业务字段及 JSR-380 输入校验规则。
 */
public record AdapterUpdateDto(
    @NotBlank(message = "系统名称不能为空")
    String name,

    @NotBlank(message = "协议类型不能为空")
    @Pattern(regexp = "HL7|FHIR|Webhook|REST|WebService", message = "协议类型必须为 HL7, FHIR, Webhook, REST, WebService 之一")
    String protocolType,

    String configJson,

    @NotBlank(message = "状态不能为空")
    @Pattern(regexp = "ACTIVE|SUSPENDED", message = "状态必须是启用或挂起")
    String status
) {}
