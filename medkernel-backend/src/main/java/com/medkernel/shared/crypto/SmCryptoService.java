package com.medkernel.shared.crypto;

import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.SecureRandom;
import java.security.Security;
import java.security.Signature;
import java.security.spec.ECGenParameterSpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;

import javax.crypto.Cipher;
import javax.crypto.spec.SecretKeySpec;

import jakarta.annotation.PostConstruct;

import org.bouncycastle.jcajce.provider.asymmetric.util.ECUtil;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.jcajce.provider.digest.SM3;
import org.springframework.stereotype.Service;

/**
 * GA-CORE-02 / W1-G5 闸门：国密 SM2 / SM3 / SM4。
 *
 * <p>采用 BouncyCastle 1.78.1 (jdk18on)。FIPS 路径由后续 GA-SEC-02 切换到 bc-fips。
 *
 * <p>合规依据：
 * <ul>
 *   <li>GB/T 32918-2017 SM2 椭圆曲线公钥密码</li>
 *   <li>GB/T 32905-2016 SM3 密码杂凑</li>
 *   <li>GB/T 32907-2016 SM4 分组密码</li>
 *   <li>GM/T 0054-2018 信息系统密码应用基本要求</li>
 *   <li>GB/T 39786-2021 信息安全技术 信息系统密码应用基本要求</li>
 * </ul>
 *
 * <p>非线程安全的部分（Cipher/Signature 实例）每次方法内重新创建，避免共享。
 */
@Service
public class SmCryptoService {

    static {
        // 启动期注册 BC Provider（idempotent）
        if (Security.getProvider(BouncyCastleProvider.PROVIDER_NAME) == null) {
            Security.addProvider(new BouncyCastleProvider());
        }
    }

    @PostConstruct
    void init() {
        // smoke：确保 SM2 曲线参数可加载
        ECUtil.getNamedCurveByName("sm2p256v1");
    }

    /** SM3 哈希（256-bit）。 */
    public byte[] sm3(byte[] data) {
        SM3.Digest digest = new SM3.Digest();
        return digest.digest(data);
    }

    /** SM3 哈希返回 hex 字符串。 */
    public String sm3Hex(String text) {
        byte[] hash = sm3(text.getBytes(StandardCharsets.UTF_8));
        StringBuilder sb = new StringBuilder(hash.length * 2);
        for (byte b : hash) {
            sb.append(String.format("%02x", b & 0xff));
        }
        return sb.toString();
    }

    /** SM4-ECB-PKCS5Padding 加密。128-bit key。 */
    public byte[] sm4Encrypt(byte[] key, byte[] plain) throws Exception {
        Cipher cipher = Cipher.getInstance("SM4/ECB/PKCS5Padding", BouncyCastleProvider.PROVIDER_NAME);
        cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(key, "SM4"));
        return cipher.doFinal(plain);
    }

    /** SM4-ECB-PKCS5Padding 解密。 */
    public byte[] sm4Decrypt(byte[] key, byte[] cipherText) throws Exception {
        Cipher cipher = Cipher.getInstance("SM4/ECB/PKCS5Padding", BouncyCastleProvider.PROVIDER_NAME);
        cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(key, "SM4"));
        return cipher.doFinal(cipherText);
    }

    /** 生成 SM2 密钥对（256-bit）。 */
    public KeyPair generateSm2KeyPair() throws Exception {
        KeyPairGenerator kpg = KeyPairGenerator.getInstance("EC", BouncyCastleProvider.PROVIDER_NAME);
        kpg.initialize(new ECGenParameterSpec("sm2p256v1"), new SecureRandom());
        return kpg.generateKeyPair();
    }

    /** SM2 公钥加密。 */
    public byte[] sm2Encrypt(PublicKey publicKey, byte[] plain) throws Exception {
        Cipher cipher = Cipher.getInstance("SM2", BouncyCastleProvider.PROVIDER_NAME);
        cipher.init(Cipher.ENCRYPT_MODE, publicKey, new SecureRandom());
        return cipher.doFinal(plain);
    }

    /** SM2 私钥解密。 */
    public byte[] sm2Decrypt(PrivateKey privateKey, byte[] cipherText) throws Exception {
        Cipher cipher = Cipher.getInstance("SM2", BouncyCastleProvider.PROVIDER_NAME);
        cipher.init(Cipher.DECRYPT_MODE, privateKey);
        return cipher.doFinal(cipherText);
    }

    /** SM3withSM2 签名。 */
    public byte[] sm2Sign(PrivateKey privateKey, byte[] payload) throws Exception {
        Signature signature = Signature.getInstance("SM3withSM2", BouncyCastleProvider.PROVIDER_NAME);
        signature.initSign(privateKey, new SecureRandom());
        signature.update(payload);
        return signature.sign();
    }

    /** SM3withSM2 验签。 */
    public boolean sm2Verify(PublicKey publicKey, byte[] payload, byte[] signatureBytes) throws Exception {
        Signature signature = Signature.getInstance("SM3withSM2", BouncyCastleProvider.PROVIDER_NAME);
        signature.initVerify(publicKey);
        signature.update(payload);
        return signature.verify(signatureBytes);
    }

    /** 还原 X.509 DER 编码的 SM2 公钥。 */
    public PublicKey decodeSm2PublicKey(String publicKeyBase64) throws Exception {
        KeyFactory keyFactory = KeyFactory.getInstance("EC", BouncyCastleProvider.PROVIDER_NAME);
        return keyFactory.generatePublic(new X509EncodedKeySpec(base64Decode(publicKeyBase64)));
    }

    /** Base64 工具（避免业务层直接依赖 java.util.Base64）。 */
    public String base64Encode(byte[] data) {
        return Base64.getEncoder().encodeToString(data);
    }

    public byte[] base64Decode(String text) {
        return Base64.getDecoder().decode(text);
    }
}
