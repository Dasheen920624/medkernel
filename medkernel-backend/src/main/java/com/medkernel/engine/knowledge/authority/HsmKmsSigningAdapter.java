package com.medkernel.engine.knowledge.authority;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.security.cert.Certificate;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

import com.medkernel.shared.crypto.SmCryptoService;

/**
 * 把平台签名端口映射到受控 HSM/KMS 驱动的生产适配器。
 *
 * <p>本类型只保留无秘密的驱动引用与摘要服务。造钥结果中的根指纹、公钥指纹和有效期均从公开
 * X.509 证书链独立计算；签名只把载荷发送给设施，应用进程从不接触私钥。
 */
public final class HsmKmsSigningAdapter implements SigningKeyPort {

    private static final String CERTIFICATE_TYPE = "X.509";
    private static final String CRYPTO_PROVIDER = "BC";

    private final HsmKmsSigningClient client;
    private final SmCryptoService crypto;

    /**
     * 创建只持有外置设施客户端和公开摘要能力的生产适配器。
     *
     * @param client 不可导出密钥设施客户端
     * @param crypto 国密摘要服务
     */
    public HsmKmsSigningAdapter(HsmKmsSigningClient client, SmCryptoService crypto) {
        this.client = Objects.requireNonNull(client, "client");
        this.crypto = Objects.requireNonNull(crypto, "crypto");
    }

    @Override
    public ProvisionedSigningKey provisionSigningKey(String authorityId, String issuerInstanceId) {
        HsmKmsSigningClient.ProvisionedPublicKey provisioned = Objects.requireNonNull(
            client.provisionNonExportableSigningKey(authorityId, issuerInstanceId),
            "外置密钥设施未返回公开造钥结果");
        List<X509Certificate> chain = parseCertificateChain(provisioned.certificateChainPem());
        X509Certificate leaf = chain.getFirst();
        X509Certificate root = chain.getLast();
        return new ProvisionedSigningKey(
            authorityId,
            issuerInstanceId,
            requirePublicKeyId(provisioned.keyId()),
            certificateFingerprint(root),
            provisioned.certificateChainPem(),
            publicKeyFingerprint(leaf),
            leaf.getNotBefore().toInstant(),
            leaf.getNotAfter().toInstant());
    }

    @Override
    public String publicKeyFingerprint(String certificateChainPem) {
        return publicKeyFingerprint(parseCertificateChain(certificateChainPem).getFirst());
    }

    @Override
    public byte[] sign(
            String authorityId,
            String issuerInstanceId,
            String keyId,
            byte[] canonicalPayload) {
        Objects.requireNonNull(canonicalPayload, "canonicalPayload");
        if (canonicalPayload.length == 0) {
            throw new IllegalArgumentException("待签规范化载荷不能为空");
        }
        byte[] signature = client.signWithNonExportableKey(
            authorityId,
            issuerInstanceId,
            requirePublicKeyId(keyId),
            canonicalPayload.clone());
        if (signature == null || signature.length == 0) {
            throw new IllegalStateException("外置密钥设施未返回签名值");
        }
        return signature.clone();
    }

    private List<X509Certificate> parseCertificateChain(String certificateChainPem) {
        if (certificateChainPem == null
                || certificateChainPem.isBlank()
                || certificateChainPem.toUpperCase(Locale.ROOT).contains("PRIVATE KEY")) {
            throw new IllegalArgumentException("证书链为空或包含禁止进入应用的私钥材料");
        }
        try {
            CertificateFactory factory = CertificateFactory.getInstance(
                CERTIFICATE_TYPE,
                CRYPTO_PROVIDER);
            List<X509Certificate> chain = new ArrayList<>();
            for (Certificate certificate : factory.generateCertificates(new ByteArrayInputStream(
                    certificateChainPem.getBytes(StandardCharsets.US_ASCII)))) {
                if (!(certificate instanceof X509Certificate x509Certificate)) {
                    throw new IllegalArgumentException("证书链包含非 X.509 证书");
                }
                chain.add(x509Certificate);
            }
            if (chain.size() < 2) {
                throw new IllegalArgumentException("签名证书链必须同时包含叶子证书与信任根证书");
            }
            verifyCertificateChain(chain);
            return List.copyOf(chain);
        } catch (IllegalArgumentException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new IllegalArgumentException("无法解析外置密钥设施返回的公开证书链", exception);
        }
    }

    private void verifyCertificateChain(List<X509Certificate> chain) {
        try {
            for (int index = 0; index < chain.size() - 1; index++) {
                X509Certificate certificate = chain.get(index);
                X509Certificate issuer = chain.get(index + 1);
                if (!certificate.getIssuerX500Principal().equals(issuer.getSubjectX500Principal())) {
                    throw new IllegalArgumentException("证书链签名关系无效：签发者身份不连续");
                }
                certificate.verify(issuer.getPublicKey(), CRYPTO_PROVIDER);
            }
            X509Certificate root = chain.getLast();
            if (!root.getIssuerX500Principal().equals(root.getSubjectX500Principal())) {
                throw new IllegalArgumentException("证书链签名关系无效：末端证书不是自签信任根");
            }
            root.verify(root.getPublicKey(), CRYPTO_PROVIDER);
        } catch (IllegalArgumentException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new IllegalArgumentException("证书链签名关系无效", exception);
        }
    }

    private String requirePublicKeyId(String keyId) {
        if (keyId == null || keyId.isBlank()) {
            throw new IllegalArgumentException("外置密钥公开 keyId 不能为空");
        }
        return keyId;
    }

    private String publicKeyFingerprint(X509Certificate certificate) {
        return fingerprint(certificate.getPublicKey().getEncoded());
    }

    private String certificateFingerprint(X509Certificate certificate) {
        try {
            return fingerprint(certificate.getEncoded());
        } catch (Exception exception) {
            throw new IllegalArgumentException("无法编码公开信任根证书", exception);
        }
    }

    private String fingerprint(byte[] publicMaterial) {
        return "sm3:" + HexFormat.of().formatHex(crypto.sm3(publicMaterial));
    }
}
