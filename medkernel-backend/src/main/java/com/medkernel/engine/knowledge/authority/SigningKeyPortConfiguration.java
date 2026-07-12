package com.medkernel.engine.knowledge.authority;

import java.util.List;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.medkernel.shared.api.error.ApiException;
import com.medkernel.shared.api.error.ErrorCode;
import com.medkernel.shared.crypto.SmCryptoService;

/**
 * 外置签名密钥端口的安全降级配置。
 *
 * <p>尚未接入 HSM/KMS 时仍允许无签发能力的系统基线启动；任何造钥或证书解析请求都会返回
 * 诚实的下游不可用错误，不会生成本地临时私钥或伪造成功结果。目标环境适配器存在时本配置自动让位。
 */
@Configuration(proxyBeanMethods = false)
public class SigningKeyPortConfiguration {

    @Bean
    @ConditionalOnMissingBean(SigningKeyPort.class)
    SigningKeyPort signingKeyPort(
            ObjectProvider<HsmKmsSigningClient> clientProvider,
            SmCryptoService crypto) {
        List<HsmKmsSigningClient> clients = clientProvider.orderedStream().toList();
        if (clients.size() > 1) {
            throw new IllegalStateException("外置 HSM/KMS 签名驱动只能配置一个，当前发现 " + clients.size() + " 个");
        }
        if (clients.size() == 1) {
            return new HsmKmsSigningAdapter(clients.getFirst(), crypto);
        }
        return unavailableSigningKeyPort();
    }

    SigningKeyPort unavailableSigningKeyPort() {
        return new UnavailableSigningKeyPort();
    }

    /** 未接入外置密钥设施时的拒绝型端口。 */
    private static final class UnavailableSigningKeyPort implements SigningKeyPort {

        @Override
        public ProvisionedSigningKey provisionSigningKey(
                String authorityId,
                String issuerInstanceId) {
            throw unavailable();
        }

        @Override
        public String publicKeyFingerprint(String certificateChainPem) {
            throw unavailable();
        }

        @Override
        public byte[] sign(
                String authorityId,
                String issuerInstanceId,
                String keyId,
                byte[] canonicalPayload) {
            throw unavailable();
        }

        private ApiException unavailable() {
            return new ApiException(
                ErrorCode.DOWNSTREAM_UNAVAILABLE,
                "外置 HSM/KMS 签名密钥设施未配置，平台签发能力当前不可用");
        }
    }
}
