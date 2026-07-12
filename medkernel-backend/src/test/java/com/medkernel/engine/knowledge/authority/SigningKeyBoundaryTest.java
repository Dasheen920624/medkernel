package com.medkernel.engine.knowledge.authority;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.ByteArrayInputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.RecordComponent;
import java.nio.charset.StandardCharsets;
import java.security.Key;
import java.security.KeyPair;
import java.security.PrivateKey;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;

import com.medkernel.shared.api.error.ApiException;
import com.medkernel.shared.api.error.ErrorCode;
import com.medkernel.shared.crypto.SmCryptoService;

/** 外置 HSM/KMS 签名私钥不可导出边界合同测试。 */
class SigningKeyBoundaryTest {

    private static final String AUTHORITY_ID = "mka-medkernel-cn-01";
    private static final String ISSUER_ID = "issuer-platform-134";
    private static final Instant NOW = Instant.parse("2026-07-12T08:00:00Z");

    @Test
    void productionPortsAndAdapterNeverExposeOrRetainPrivateKeyTypes() {
        assertPublicBoundary(SigningKeyPort.class);
        assertPublicBoundary(HsmKmsSigningClient.class);
        assertPublicBoundary(HsmKmsSigningClient.ProvisionedPublicKey.class);
        assertPublicBoundary(SigningKeyPort.ProvisionedSigningKey.class);

        assertThat(Arrays.stream(HsmKmsSigningAdapter.class.getDeclaredFields()))
            .extracting(Field::getType)
            .allMatch(type -> !isPrivateKeyType(type));
        assertThat(Arrays.stream(HsmKmsSigningAdapter.class.getDeclaredFields()))
            .extracting(Field::getName)
            .noneMatch(SigningKeyBoundaryTest::looksLikePrivateMaterial);
    }

    @Test
    void persistenceEntitiesContainOnlyPublicSigningMetadata() {
        List<Class<?>> authorityEntities = List.of(
            Authority.class,
            IssuerInstance.class,
            TrustRoot.class,
            SigningKey.class,
            Handover.class,
            Revocation.class,
            PackageRegistration.class);

        assertThat(authorityEntities).allSatisfy(entity -> {
            assertThat(entity.isRecord()).as(entity.getSimpleName()).isTrue();
            assertThat(Arrays.stream(entity.getRecordComponents()))
                .extracting(RecordComponent::getName)
                .noneMatch(SigningKeyBoundaryTest::looksLikePrivateMaterial);
            assertThat(Arrays.stream(entity.getRecordComponents()))
                .extracting(RecordComponent::getType)
                .allMatch(type -> !isPrivateKeyType(type));
        });
    }

    @Test
    void hsmKmsAdapterUsesOnlyExternalHandleForProvisioningAndSigning() throws Exception {
        Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
        InMemorySigningAdapter facility = new InMemorySigningAdapter(clock);
        SigningKeyPort.ProvisionedSigningKey facilityKey =
            facility.provisionSigningKey(AUTHORITY_ID, ISSUER_ID);
        HsmKmsSigningClient client = mock(HsmKmsSigningClient.class);
        when(client.provisionNonExportableSigningKey(AUTHORITY_ID, ISSUER_ID))
            .thenReturn(new HsmKmsSigningClient.ProvisionedPublicKey(
                facilityKey.keyId(),
                facilityKey.certificateChainPem()));
        byte[] payload = "canonical-medical-package-manifest".getBytes(StandardCharsets.UTF_8);
        when(client.signWithNonExportableKey(
                eq(AUTHORITY_ID), eq(ISSUER_ID), eq(facilityKey.keyId()), any(byte[].class)))
            .thenAnswer(invocation -> facility.sign(
                invocation.getArgument(0),
                invocation.getArgument(1),
                invocation.getArgument(2),
                invocation.getArgument(3)));

        HsmKmsSigningAdapter adapter = new HsmKmsSigningAdapter(client, new SmCryptoService());
        SigningKeyPort.ProvisionedSigningKey provisioned =
            adapter.provisionSigningKey(AUTHORITY_ID, ISSUER_ID);
        byte[] signature = adapter.sign(AUTHORITY_ID, ISSUER_ID, provisioned.keyId(), payload);

        assertThat(provisioned.authorityId()).isEqualTo(AUTHORITY_ID);
        assertThat(provisioned.issuerInstanceId()).isEqualTo(ISSUER_ID);
        assertThat(provisioned.keyId()).isEqualTo(facilityKey.keyId());
        assertThat(provisioned.rootFingerprint()).isEqualTo(facilityKey.rootFingerprint());
        assertThat(provisioned.publicKeyFingerprint()).isEqualTo(facilityKey.publicKeyFingerprint());
        assertThat(signature).isNotEmpty();
        assertThat(verifySignature(provisioned.certificateChainPem(), payload, signature)).isTrue();
        verify(client).provisionNonExportableSigningKey(AUTHORITY_ID, ISSUER_ID);
        verify(client).signWithNonExportableKey(
            eq(AUTHORITY_ID), eq(ISSUER_ID), eq(facilityKey.keyId()), any(byte[].class));
    }

    @Test
    void defaultPortStartsBaselineButRejectsEveryPrivateKeyOperationHonestly() {
        SigningKeyPort port = new SigningKeyPortConfiguration().unavailableSigningKeyPort();
        byte[] payload = "manifest".getBytes(StandardCharsets.UTF_8);

        assertUnavailable(() -> port.provisionSigningKey(AUTHORITY_ID, ISSUER_ID));
        assertUnavailable(() -> port.publicKeyFingerprint("CERTIFICATE"));
        assertUnavailable(() -> port.sign(AUTHORITY_ID, ISSUER_ID, "kms:key:134", payload));
    }

    @Test
    void configurationSelectsOneExternalDriverAndRejectsAmbiguousDrivers() {
        SigningKeyPortConfiguration configuration = new SigningKeyPortConfiguration();
        DefaultListableBeanFactory oneDriver = new DefaultListableBeanFactory();
        oneDriver.registerSingleton("hsmKmsClient", mock(HsmKmsSigningClient.class));

        SigningKeyPort configured = configuration.signingKeyPort(
            oneDriver.getBeanProvider(HsmKmsSigningClient.class),
            new SmCryptoService());

        assertThat(configured).isInstanceOf(HsmKmsSigningAdapter.class);

        DefaultListableBeanFactory ambiguousDrivers = new DefaultListableBeanFactory();
        ambiguousDrivers.registerSingleton("hsmKmsClientA", mock(HsmKmsSigningClient.class));
        ambiguousDrivers.registerSingleton("hsmKmsClientB", mock(HsmKmsSigningClient.class));
        assertThatThrownBy(() -> configuration.signingKeyPort(
                ambiguousDrivers.getBeanProvider(HsmKmsSigningClient.class),
                new SmCryptoService()))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("只能配置一个");
    }

    @Test
    void adapterRejectsCertificatePayloadContainingPrivateKeyMarker() {
        InMemorySigningAdapter facility = new InMemorySigningAdapter(
            Clock.fixed(NOW, ZoneOffset.UTC));
        SigningKeyPort.ProvisionedSigningKey publicKey =
            facility.provisionSigningKey(AUTHORITY_ID, ISSUER_ID);
        HsmKmsSigningClient client = mock(HsmKmsSigningClient.class);
        when(client.provisionNonExportableSigningKey(AUTHORITY_ID, ISSUER_ID))
            .thenReturn(new HsmKmsSigningClient.ProvisionedPublicKey(
                publicKey.keyId(),
                publicKey.certificateChainPem()
                    + "-----BEGIN PRIVATE KEY-----\nforbidden\n-----END PRIVATE KEY-----\n"));

        HsmKmsSigningAdapter adapter = new HsmKmsSigningAdapter(client, new SmCryptoService());

        assertThatThrownBy(() -> adapter.provisionSigningKey(AUTHORITY_ID, ISSUER_ID))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("私钥材料");
    }

    @Test
    void adapterRejectsUnrelatedLeafWithTrustedRootMerelyAppended() {
        Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
        SigningKeyPort.ProvisionedSigningKey first = new InMemorySigningAdapter(clock)
            .provisionSigningKey(AUTHORITY_ID, ISSUER_ID);
        SigningKeyPort.ProvisionedSigningKey second = new InMemorySigningAdapter(clock)
            .provisionSigningKey(AUTHORITY_ID, ISSUER_ID + "-other-root");
        String forgedChain = firstCertificate(first.certificateChainPem())
            + lastCertificate(second.certificateChainPem());
        HsmKmsSigningClient client = mock(HsmKmsSigningClient.class);
        when(client.provisionNonExportableSigningKey(AUTHORITY_ID, ISSUER_ID))
            .thenReturn(new HsmKmsSigningClient.ProvisionedPublicKey(first.keyId(), forgedChain));

        HsmKmsSigningAdapter adapter = new HsmKmsSigningAdapter(client, new SmCryptoService());

        assertThatThrownBy(() -> adapter.provisionSigningKey(AUTHORITY_ID, ISSUER_ID))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("证书链签名关系无效");
    }

    private String firstCertificate(String certificateChainPem) {
        String endMarker = "-----END CERTIFICATE-----\n";
        return certificateChainPem.substring(0, certificateChainPem.indexOf(endMarker) + endMarker.length());
    }

    private String lastCertificate(String certificateChainPem) {
        String beginMarker = "-----BEGIN CERTIFICATE-----";
        return certificateChainPem.substring(certificateChainPem.lastIndexOf(beginMarker));
    }

    private boolean verifySignature(
            String certificateChainPem,
            byte[] payload,
            byte[] signature) throws Exception {
        CertificateFactory factory = CertificateFactory.getInstance("X.509", "BC");
        X509Certificate leaf = (X509Certificate) factory.generateCertificate(
            new ByteArrayInputStream(certificateChainPem.getBytes(StandardCharsets.US_ASCII)));
        return new SmCryptoService().sm2Verify(leaf.getPublicKey(), payload, signature);
    }

    private void assertUnavailable(ThrowingCall call) {
        assertThatThrownBy(call::invoke)
            .isInstanceOfSatisfying(ApiException.class, exception ->
                assertThat(exception.errorCode()).isEqualTo(ErrorCode.DOWNSTREAM_UNAVAILABLE));
    }

    private static void assertPublicBoundary(Class<?> type) {
        assertThat(Arrays.stream(type.getDeclaredMethods())).allSatisfy(method -> {
            assertThat(method.getName()).doesNotMatch("(?i).*(export|read|get).*private.*");
            assertThat(isPrivateKeyType(method.getReturnType())).as(method.toString()).isFalse();
            assertThat(Arrays.stream(method.getParameterTypes()))
                .as(method.toString())
                .allMatch(parameterType -> !isPrivateKeyType(parameterType));
        });
        if (type.isRecord()) {
            assertThat(Arrays.stream(type.getRecordComponents()))
                .extracting(RecordComponent::getName)
                .noneMatch(SigningKeyBoundaryTest::looksLikePrivateMaterial);
        }
    }

    private static boolean isPrivateKeyType(Class<?> type) {
        return PrivateKey.class.isAssignableFrom(type)
            || KeyPair.class.isAssignableFrom(type)
            || Key.class.equals(type);
    }

    private static boolean looksLikePrivateMaterial(String name) {
        String normalized = name.toLowerCase(Locale.ROOT).replace("_", "");
        return normalized.contains("privatekey")
            || normalized.contains("secret")
            || normalized.contains("credential")
            || normalized.equals("keymaterial");
    }

    @FunctionalInterface
    private interface ThrowingCall {
        void invoke() throws Exception;
    }
}
