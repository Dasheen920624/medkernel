package com.medkernel.engine.knowledge.authority;

import java.io.ByteArrayInputStream;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.PrivateKey;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.asn1.x509.BasicConstraints;
import org.bouncycastle.asn1.x509.Extension;
import org.bouncycastle.asn1.x509.KeyUsage;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder;
import org.bouncycastle.operator.ContentSigner;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;

import com.medkernel.shared.crypto.SmCryptoService;

/**
 * 仅供测试使用的进程内签名适配器。
 *
 * <p>测试密钥在运行时随机生成且只存在于测试 JVM 内存；生产源码、配置、数据库和测试资源均不包含
 * 固定私钥。本适配器用于在没有真实 HSM/KMS 的单元测试中验证公开元数据与签名端口合同。
 */
public final class InMemorySigningAdapter implements SigningKeyPort {

    private static final String SIGNATURE_ALGORITHM = "SM3withSM2";
    private static final String PROVIDER = "BC";

    private final SmCryptoService crypto = new SmCryptoService();
    private final Clock clock;
    private final AtomicLong sequence = new AtomicLong();
    private final Map<String, TestKey> keys = new LinkedHashMap<>();
    private final KeyPair rootKeyPair;
    private final X509Certificate rootCertificate;
    private final String rootFingerprint;

    public InMemorySigningAdapter(Clock clock) {
        this.clock = clock;
        try {
            this.rootKeyPair = crypto.generateSm2KeyPair();
            Instant now = clock.instant().truncatedTo(ChronoUnit.SECONDS);
            this.rootCertificate = issueCertificate(
                "CN=MedKernel Test Root",
                rootKeyPair,
                "CN=MedKernel Test Root",
                rootKeyPair.getPrivate(),
                now.minus(1, ChronoUnit.DAYS),
                now.plus(3_650, ChronoUnit.DAYS),
                true,
                BigInteger.ONE);
            this.rootFingerprint = fingerprint(rootCertificate.getEncoded());
        } catch (Exception exception) {
            throw new IllegalStateException("无法初始化测试签名根", exception);
        }
    }

    @Override
    public ProvisionedSigningKey provisionSigningKey(String authorityId, String issuerInstanceId) {
        try {
            long keySequence = sequence.incrementAndGet();
            String keyId = "memory:key:" + issuerInstanceId + ":" + keySequence;
            Instant notBefore = clock.instant().truncatedTo(ChronoUnit.SECONDS);
            Instant notAfter = notBefore.plus(365, ChronoUnit.DAYS);
            KeyPair keyPair = crypto.generateSm2KeyPair();
            X509Certificate leaf = issueCertificate(
                "CN=" + issuerInstanceId,
                keyPair,
                rootCertificate.getSubjectX500Principal().getName(),
                rootKeyPair.getPrivate(),
                notBefore,
                notAfter,
                false,
                BigInteger.valueOf(keySequence + 1));
            String certificateChainPem = pem(leaf) + pem(rootCertificate);
            keys.put(keyId, new TestKey(authorityId, issuerInstanceId, keyPair.getPrivate()));
            return new ProvisionedSigningKey(
                authorityId,
                issuerInstanceId,
                keyId,
                rootFingerprint,
                certificateChainPem,
                fingerprint(leaf.getPublicKey().getEncoded()),
                notBefore,
                notAfter);
        } catch (Exception exception) {
            throw new IllegalStateException("无法创建测试签名密钥", exception);
        }
    }

    @Override
    public String publicKeyFingerprint(String certificateChainPem) {
        try {
            CertificateFactory factory = CertificateFactory.getInstance("X.509", PROVIDER);
            X509Certificate leaf = (X509Certificate) factory.generateCertificate(
                new ByteArrayInputStream(certificateChainPem.getBytes(StandardCharsets.US_ASCII)));
            return fingerprint(leaf.getPublicKey().getEncoded());
        } catch (Exception exception) {
            throw new IllegalArgumentException("测试证书链无效", exception);
        }
    }

    @Override
    public byte[] sign(
            String authorityId,
            String issuerInstanceId,
            String keyId,
            byte[] canonicalPayload) {
        TestKey key = keys.get(keyId);
        if (key == null
                || !key.authorityId().equals(authorityId)
                || !key.issuerInstanceId().equals(issuerInstanceId)) {
            throw new IllegalArgumentException("测试签名密钥不存在或绑定身份不匹配");
        }
        try {
            return crypto.sm2Sign(key.privateKey(), canonicalPayload.clone());
        } catch (Exception exception) {
            throw new IllegalStateException("测试签名失败", exception);
        }
    }

    private X509Certificate issueCertificate(
            String subject,
            KeyPair subjectKeyPair,
            String issuer,
            PrivateKey issuerPrivateKey,
            Instant notBefore,
            Instant notAfter,
            boolean certificateAuthority,
            BigInteger serial) throws Exception {
        JcaX509v3CertificateBuilder builder = new JcaX509v3CertificateBuilder(
            new X500Name(issuer),
            serial,
            java.util.Date.from(notBefore),
            java.util.Date.from(notAfter),
            new X500Name(subject),
            subjectKeyPair.getPublic());
        builder.addExtension(Extension.basicConstraints, true, new BasicConstraints(certificateAuthority));
        builder.addExtension(
            Extension.keyUsage,
            true,
            new KeyUsage(certificateAuthority
                ? KeyUsage.keyCertSign | KeyUsage.cRLSign
                : KeyUsage.digitalSignature));
        ContentSigner signer = new JcaContentSignerBuilder(SIGNATURE_ALGORITHM)
            .setProvider(PROVIDER)
            .build(issuerPrivateKey);
        return new JcaX509CertificateConverter()
            .setProvider(PROVIDER)
            .getCertificate(builder.build(signer));
    }

    private String pem(X509Certificate certificate) throws Exception {
        String encoded = Base64.getMimeEncoder(64, new byte[]{'\n'}).encodeToString(certificate.getEncoded());
        return "-----BEGIN CERTIFICATE-----\n" + encoded + "\n-----END CERTIFICATE-----\n";
    }

    private String fingerprint(byte[] publicMaterial) {
        return "sm3:" + HexFormat.of().formatHex(crypto.sm3(publicMaterial));
    }

    /** 测试 JVM 内部私钥容器；该类型永远不得迁入生产源码。 */
    private record TestKey(String authorityId, String issuerInstanceId, PrivateKey privateKey) {
    }
}
