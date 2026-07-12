package com.medkernel.engine.knowledge.authority;

import java.lang.reflect.Method;
import java.lang.reflect.RecordComponent;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.Repository;
import org.springframework.data.relational.core.mapping.Table;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 平台知识权威领域与仓储端口契约。
 *
 * <p>合同只定义宿主无关的持久化事实与租户隔离查询；具体签发和未来冷迁移行为由服务层合同覆盖。
 */
class AuthorityRepositoryContractTest {

    private static final List<Class<?>> MODELS = List.of(
        Authority.class,
        IssuerInstance.class,
        TrustRoot.class,
        SigningKey.class,
        Handover.class,
        Revocation.class,
        PackageRegistration.class
    );

    @Test
    void domainRecordsCarryStableIdentityVersionAndAuditWithoutHostCoupling() {
        assertRecord(
            Authority.class,
            "mk_knowledge_authority",
            "id", "tenantId", "authorityId", "activeIssuerInstanceId",
            "activeTrustRootFingerprint", "handoverSequence", "releaseSequence",
            "lockVersion", "createdAt", "createdBy", "updatedAt", "updatedBy", "traceId");
        assertRecord(
            IssuerInstance.class,
            "mk_knowledge_issuer_instance",
            "id", "tenantId", "authorityId", "issuerInstanceId", "displayName", "status",
            "lastHandoverSequence", "activatedAt", "frozenAt", "handedOverAt",
            "lockVersion", "createdAt", "createdBy", "updatedAt", "updatedBy", "traceId");
        assertRecord(
            TrustRoot.class,
            "mk_knowledge_trust_root",
            "id", "tenantId", "authorityId", "rootFingerprint", "rootCertificatePem",
            "predecessorFingerprint", "effectiveHandoverSequence", "status", "validFrom",
            "validUntil", "transitionAuthorizedByKeyId", "transitionSignature",
            "lockVersion", "createdAt", "createdBy", "updatedAt", "updatedBy", "traceId");
        assertRecord(
            SigningKey.class,
            "mk_knowledge_signing_key",
            "id", "tenantId", "authorityId", "issuerInstanceId", "keyId", "rootFingerprint",
            "certificateChainPem", "status", "notBefore", "notAfter",
            "authorizedFromHandoverSequence", "authorizedThroughHandoverSequence",
            "lockVersion", "createdAt", "createdBy", "updatedAt", "updatedBy", "traceId");
        assertRecord(
            Handover.class,
            "mk_knowledge_authority_handover",
            "id", "tenantId", "authorityId", "handoverId", "handoverSequence",
            "sourceIssuerInstanceId", "targetIssuerInstanceId", "expectedActiveIssuerInstanceId",
            "databaseDigest", "materialDigest", "auditDigest", "registryDigest",
            "trustChainDigest", "handoverManifestDigest", "signedByKeyId", "signature", "status",
            "frozenAt", "verifiedAt", "activatedAt", "abortedAt", "lockVersion",
            "createdAt", "createdBy", "updatedAt", "updatedBy", "traceId");
        assertRecord(
            Revocation.class,
            "mk_knowledge_key_revocation",
            "id", "tenantId", "authorityId", "revocationId", "revocationSequence", "keyId",
            "effectiveReleaseSequence", "reason", "signedByKeyId", "signature", "revokedAt",
            "lockVersion", "createdAt", "createdBy", "updatedAt", "updatedBy", "traceId");
        assertRecord(
            PackageRegistration.class,
            "mk_knowledge_package_registration",
            "id", "tenantId", "authorityId", "deliveryId", "releaseSequence", "manifestDigest",
            "platformReleaseIdentity", "packageFileDigest", "packageFileSize", "storageCoordinate",
            "issuerInstanceId", "keyId", "parentDeliveryId", "parentManifestDigest",
            "baseManifestDigest", "packageType", "signingStatus", "signedAt", "registeredAt",
            "lockVersion", "createdAt", "createdBy", "updatedAt", "updatedBy", "traceId");

        assertThat(MODELS)
            .allSatisfy(model -> assertThat(componentNames(model))
                .doesNotContain("host", "hostName", "ip", "ipAddress", "deploymentPath"));
    }

    @Test
    void persistedRecordsContainNoPrivateSigningMaterial() {
        assertThat(MODELS)
            .allSatisfy(model -> assertThat(componentNames(model))
                .noneMatch(AuthorityRepositoryContractTest::looksLikePrivateMaterial));
    }

    @Test
    void lifecycleCatalogsExpressSingleIssuerAndMonotonicHandoverStates() {
        assertThat(names(IssuerInstanceStatus.values()))
            .containsExactly("STANDBY", "ACTIVE", "FROZEN", "HANDED_OVER", "REVOKED");
        assertThat(names(TrustRootStatus.values()))
            .containsExactly("STANDBY", "ACTIVE", "RETIRED", "REVOKED");
        assertThat(names(SigningKeyStatus.values()))
            .containsExactly("STANDBY", "ACTIVE", "DISABLED", "REVOKED");
        assertThat(names(HandoverStatus.values()))
            .containsExactly("DRAFT", "FROZEN", "VERIFIED", "ACTIVATED", "ABORTED");
        assertThat(names(MedicalPackageType.values()))
            .containsExactly("FULL", "DELTA");
        assertThat(names(PackageSigningStatus.values()))
            .containsExactly("SIGNED", "REVOKED");
    }

    @Test
    void repositoryPortsExposeOnlyTenantScopedIdentityQueries() throws ReflectiveOperationException {
        assertRepository(AuthorityRepository.class, Authority.class);
        assertRepository(IssuerInstanceRepository.class, IssuerInstance.class);
        assertRepository(TrustRootRepository.class, TrustRoot.class);
        assertRepository(SigningKeyRepository.class, SigningKey.class);
        assertRepository(HandoverRepository.class, Handover.class);
        assertRepository(RevocationRepository.class, Revocation.class);
        assertRepository(PackageRegistrationRepository.class, PackageRegistration.class);

        assertOptionalMethod(AuthorityRepository.class, "findByTenantIdAndAuthorityId", String.class, String.class);
        assertOptionalMethod(
            IssuerInstanceRepository.class,
            "findByTenantIdAndAuthorityIdAndIssuerInstanceId",
            String.class, String.class, String.class);
        assertOptionalMethod(
            TrustRootRepository.class,
            "findByTenantIdAndAuthorityIdAndRootFingerprint",
            String.class, String.class, String.class);
        assertOptionalMethod(
            SigningKeyRepository.class,
            "findByTenantIdAndAuthorityIdAndKeyId",
            String.class, String.class, String.class);
        assertOptionalMethod(
            HandoverRepository.class,
            "findByTenantIdAndAuthorityIdAndHandoverSequence",
            String.class, String.class, long.class);
        assertOptionalMethod(
            RevocationRepository.class,
            "findByTenantIdAndAuthorityIdAndRevocationSequence",
            String.class, String.class, long.class);
        assertOptionalMethod(
            PackageRegistrationRepository.class,
            "findByTenantIdAndAuthorityIdAndReleaseSequence",
            String.class, String.class, long.class);

        for (Class<?> repository : repositories()) {
            assertThat(repository.getDeclaredMethods())
                .filteredOn(method -> !"save".equals(method.getName()))
                .allSatisfy(method -> {
                    assertThat(method.getName()).doesNotStartWith("delete");
                    assertThat(method.getParameterTypes())
                        .as(repository.getSimpleName() + "." + method.getName())
                        .isNotEmpty()
                        .startsWith(String.class);
                });
        }
    }

    private void assertRecord(Class<?> type, String tableName, String... components) {
        assertThat(type.isRecord()).as(type.getSimpleName() + " 必须不可变").isTrue();
        assertThat(componentNames(type)).containsExactly(components);
        assertThat(type.getAnnotation(Table.class))
            .as(type.getSimpleName() + " 表映射")
            .isNotNull()
            .extracting(Table::value)
            .isEqualTo(tableName);
    }

    private void assertRepository(Class<?> type, Class<?> aggregateType) throws NoSuchMethodException {
        assertThat(Repository.class.isAssignableFrom(type)).isTrue();
        assertThat(CrudRepository.class.isAssignableFrom(type)).isFalse();
        Method save = type.getMethod("save", aggregateType);
        assertThat(save.getReturnType()).isEqualTo(aggregateType);
    }

    private void assertOptionalMethod(Class<?> type, String name, Class<?>... parameters)
        throws NoSuchMethodException {
        assertThat(type.getMethod(name, parameters).getReturnType()).isEqualTo(Optional.class);
    }

    private List<Class<?>> repositories() {
        return List.of(
            AuthorityRepository.class,
            IssuerInstanceRepository.class,
            TrustRootRepository.class,
            SigningKeyRepository.class,
            HandoverRepository.class,
            RevocationRepository.class,
            PackageRegistrationRepository.class);
    }

    private static List<String> componentNames(Class<?> type) {
        return Arrays.stream(type.getRecordComponents())
            .map(RecordComponent::getName)
            .toList();
    }

    private static boolean looksLikePrivateMaterial(String componentName) {
        String normalized = componentName.toLowerCase(Locale.ROOT);
        return normalized.contains("privatekey")
            || normalized.contains("private_key")
            || normalized.contains("secret")
            || normalized.contains("keymaterial")
            || normalized.contains("key_material");
    }

    private static String[] names(Enum<?>[] values) {
        return Arrays.stream(values).map(Enum::name).toArray(String[]::new);
    }
}
