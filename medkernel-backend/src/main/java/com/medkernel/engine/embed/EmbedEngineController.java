package com.medkernel.engine.embed;

import java.util.List;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.medkernel.shared.api.ApiResult;
import com.medkernel.shared.datascope.DataScope;
import jakarta.validation.Valid;

/**
 * 页面嵌入引擎接口控制器 (GA-ENG-API-11)。
 *
 * <p>提供外部工作站集成所需的启动凭证生成、单次交换校验、用户交互闭环反馈以及安全域名允许清单管理的 REST API 服务。
 * 签发与允许清单管理受 {@link DataScope} 保护；外部宿主端点仅接受服务端校验的一次性启动凭证。
 */
@RestController
@RequestMapping("/api/v1/engine/embed")
public class EmbedEngineController {

    private final EmbedEngineService service;

    public EmbedEngineController(EmbedEngineService service) {
        this.service = service;
    }

    /**
     * 生成一次性页面嵌入启动凭证。
     *
     * @param request 启动凭证申请请求信息
     * @return 启动凭证及嵌入链接响应
     */
    @PostMapping("/launch-tokens")
    @PreAuthorize("@perm.has('embed.write')")
    @DataScope(requireTenant = true)
    public ApiResult<EmbedLaunchTokenResponse> generateToken(@Valid @RequestBody EmbedLaunchTokenRequest request) {
        return ApiResult.ok(service.generateToken(request));
    }

    /**
     * 使用启动凭证兑换获取嵌入会话临床上下文，并物理标记凭证为已使用。
     *
     * @param request 启动凭证兑换请求
     * @return 会话及关联的临床上下文
     */
    @PostMapping("/launch")
    public ApiResult<EmbedLaunchContextResponse> validateAndExchange(
            @Valid @RequestBody EmbedLaunchRequest request) {
        return ApiResult.ok(service.validateAndExchange(request));
    }

    /**
     * 使用已兑换凭证读取该患者、就诊和触发点范围内的临床建议。
     */
    @PostMapping("/recommendations")
    public ApiResult<EmbedRecommendationCardsResponse> recommendations(
            @Valid @RequestBody EmbedRecommendationCardsRequest request) {
        return ApiResult.ok(service.listCards(request));
    }

    /**
     * 回传记录医师在工作站嵌入页面的交互采纳与拒绝反馈，保证合规审计。
     *
     * @param request 反馈请求参数
     * @return 反馈受理响应
     */
    @PostMapping("/feedback")
    public ApiResult<EmbedFeedbackResponse> feedback(@Valid @RequestBody EmbedFeedbackRequest request) {
        return ApiResult.ok(service.feedback(request));
    }

    /**
     * 为当前服务机构添加允许嵌入的 Origin 来源域名。
     *
     * @param request 域名 Origin 配置请求
     * @return 空响应
     */
    @PostMapping("/origins")
    @PreAuthorize("@perm.has('embed.write')")
    @DataScope(requireTenant = true)
    public ApiResult<Void> addOrigin(@Valid @RequestBody EmbedOriginRequest request) {
        service.addOrigin(request);
        return ApiResult.empty();
    }

    /**
     * 获取当前服务机构下配置的所有 Origin 来源域名允许清单。
     *
     * @return Origin 来源域名允许清单
     */
    @GetMapping("/origins")
    @PreAuthorize("@perm.has('embed.read')")
    @DataScope(requireTenant = true)
    public ApiResult<List<String>> getOrigins() {
        return ApiResult.ok(service.getOrigins());
    }
}
