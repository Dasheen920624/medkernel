package com.medkernel.engine.knowledge.delivery;

import java.time.Instant;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.medkernel.engine.context.ClinicalRuntimeAssetSelection;
import com.medkernel.engine.knowledge.authority.Authority;
import com.medkernel.engine.knowledge.authority.AuthorityRepository;
import com.medkernel.engine.knowledge.authority.IssuerInstance;
import com.medkernel.engine.knowledge.authority.IssuerInstanceRepository;
import com.medkernel.engine.knowledge.authority.IssuerInstanceStatus;
import com.medkernel.engine.knowledge.authority.MedicalPackageType;
import com.medkernel.engine.knowledge.authority.PackageRegistration;
import com.medkernel.engine.knowledge.authority.PackageRegistrationRepository;
import com.medkernel.engine.knowledge.authority.PackageSigningStatus;
import com.medkernel.engine.knowledge.authority.SigningKey;
import com.medkernel.engine.knowledge.authority.SigningKeyRepository;
import com.medkernel.engine.knowledge.authority.SigningKeyStatus;
import com.medkernel.engine.knowledge.authority.TrustRoot;
import com.medkernel.engine.knowledge.authority.TrustRootRepository;
import com.medkernel.engine.knowledge.authority.TrustRootStatus;
import com.medkernel.engine.knowledge.authority.VerifiedPackageSignature;
import com.medkernel.engine.release.PlatformBaselineItem;
import com.medkernel.engine.release.PlatformBaselineItemRepository;
import com.medkernel.engine.release.PlatformBaselineRelease;
import com.medkernel.engine.release.PlatformBaselineReleaseRepository;
import com.medkernel.engine.release.ReleaseEntryState;
import com.medkernel.engine.versioning.AssetDependency;
import com.medkernel.engine.versioning.AssetDependencyRepository;
import com.medkernel.engine.versioning.AssetIdentity;
import com.medkernel.engine.versioning.AssetIdentityRepository;
import com.medkernel.engine.versioning.AssetIdentityStatus;
import com.medkernel.engine.versioning.AssetValidationRecord;
import com.medkernel.engine.versioning.AssetValidationRecordRepository;
import com.medkernel.engine.versioning.AssetVersion;
import com.medkernel.engine.versioning.AssetVersionContent;
import com.medkernel.engine.versioning.AssetVersionContentRepository;
import com.medkernel.engine.versioning.AssetVersionNumbers;
import com.medkernel.engine.versioning.AssetVersionRepository;
import com.medkernel.engine.versioning.AssetVersionStatus;
import com.medkernel.engine.versioning.PortableAssetProvenance;
import com.medkernel.engine.versioning.PortableAssetProvenanceRepository;
import com.medkernel.shared.api.error.ApiException;
import com.medkernel.shared.api.error.ErrorCode;
import com.medkernel.shared.context.PlatformTenant;
import com.medkernel.shared.crypto.SmCryptoService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 把已经完成容器校验和固定根验签的 FULL 包事务性重建为关系库权威事实。
 *
 * <p>所有标识由签名包内稳定事实确定性派生；精确重试不重复写，任何不一致或数据库失败都会
 * 回滚整个物化事务，禁止形成只有哈希没有正文、只有基线没有来源或只有资产没有包登记的半包。
 */
@Service
public class FullPackageMaterializer {

    private final AssetIdentityRepository identities;
    private final AssetVersionRepository versions;
    private final AssetVersionContentRepository contents;
    private final AssetDependencyRepository dependencies;
    private final AssetValidationRecordRepository validations;
    private final PortableAssetProvenanceRepository provenances;
    private final FullPackageWithdrawalRepository withdrawals;
    private final PlatformBaselineReleaseRepository baselines;
    private final PlatformBaselineItemRepository baselineItems;
    private final PackageRegistrationRepository registrations;
    private final AuthorityRepository authorities;
    private final IssuerInstanceRepository issuers;
    private final SigningKeyRepository signingKeys;
    private final TrustRootRepository trustRoots;
    private final FullPackageArtifactStore artifacts;
    private final FullPackageProvenanceCodec provenanceCodec;
    private final CanonicalJson canonicalJson;
    private final SmCryptoService crypto;

    public FullPackageMaterializer(
            AssetIdentityRepository identities,
            AssetVersionRepository versions,
            AssetVersionContentRepository contents,
            AssetDependencyRepository dependencies,
            AssetValidationRecordRepository validations,
            PortableAssetProvenanceRepository provenances,
            FullPackageWithdrawalRepository withdrawals,
            PlatformBaselineReleaseRepository baselines,
            PlatformBaselineItemRepository baselineItems,
            PackageRegistrationRepository registrations,
            AuthorityRepository authorities,
            IssuerInstanceRepository issuers,
            SigningKeyRepository signingKeys,
            TrustRootRepository trustRoots,
            FullPackageArtifactStore artifacts,
            FullPackageProvenanceCodec provenanceCodec,
            ObjectMapper json,
            SmCryptoService crypto) {
        this.identities = identities;
        this.versions = versions;
        this.contents = contents;
        this.dependencies = dependencies;
        this.validations = validations;
        this.provenances = provenances;
        this.withdrawals = withdrawals;
        this.baselines = baselines;
        this.baselineItems = baselineItems;
        this.registrations = registrations;
        this.authorities = authorities;
        this.issuers = issuers;
        this.signingKeys = signingKeys;
        this.trustRoots = trustRoots;
        this.artifacts = artifacts;
        this.provenanceCodec = provenanceCodec;
        this.canonicalJson = new CanonicalJson(json);
        this.crypto = crypto;
    }

    /** 在单个关系库事务内重建包内全部稳定事实，并返回机构激活所需的活动资产选择。 */
    @Transactional
    public FullPackageMaterializationResult materialize(
            FullPackageInspection inspection,
            VerifiedPackageSignature verified,
            String actor,
            String traceId,
            Instant now) {
        requireBoundInspection(inspection, verified);
        String normalizedActor = required(actor, "完整包物化操作人");
        String normalizedTrace = required(traceId, "完整包物化链路标识");
        if (now == null) {
            throw invalid("完整包物化时间不能为空");
        }
        Authority authority = ensureSigningLineage(
            verified, normalizedActor, normalizedTrace, now);
        Map<AssetKey, PortableAssetDocument> documents = new HashMap<>();
        for (PortableAssetDocument document : inspection.documents()) {
            AssetKey key = new AssetKey(document.assetType(), document.assetIdentity());
            if (documents.putIfAbsent(key, document) != null) {
                throw conflict("完整包物化资产稳定身份重复: " + document.assetIdentity());
            }
        }

        List<FullPackageReleaseDocument.Entry> orderedEntries =
            inspection.releaseDocument().entries().stream()
                .sorted(Comparator
                    .comparing((FullPackageReleaseDocument.Entry entry) -> entry.assetType().name())
                    .thenComparing(FullPackageReleaseDocument.Entry::assetIdentity))
                .toList();
        for (FullPackageReleaseDocument.Entry entry : orderedEntries) {
            PortableAssetDocument document = documents.get(
                new AssetKey(entry.assetType(), entry.assetIdentity()));
            long sequence = document == null
                ? 0L
                : AssetVersionNumbers.sequence(document.versionNo(), "完整包资产版本号");
            ensureIdentity(entry, sequence, normalizedActor, normalizedTrace, now);
            if (entry.state() == ReleaseEntryState.ACTIVE) {
                if (document == null) {
                    throw conflict("完整包活动条目缺少已校验正文: " + entry.assetIdentity());
                }
                materializeDocument(
                    document,
                    inspection.manifest().authorityId(),
                    inspection.manifest().deliveryId(),
                    verified.signedAt(),
                    normalizedActor,
                    normalizedTrace,
                    now);
            }
        }
        materializeWithdrawals(
            inspection,
            normalizedActor,
            normalizedTrace,
            now);
        materializeBaseline(
            inspection.releaseDocument(), orderedEntries,
            normalizedActor, normalizedTrace, now);
        StoredFullPackage stored = artifacts.adoptVerified(
            inspection.artifact(), inspection.manifest(), verified.manifestDigest());
        registerPackage(
            inspection, verified, stored, authority,
            normalizedActor, normalizedTrace, now);

        List<ClinicalRuntimeAssetSelection> active = orderedEntries.stream()
            .filter(entry -> entry.state() == ReleaseEntryState.ACTIVE)
            .map(entry -> ClinicalRuntimeAssetSelection.platform(
                entry.assetType(), entry.assetIdentity()))
            .toList();
        return new FullPackageMaterializationResult(
            inspection.releaseDocument().platformReleaseIdentity(),
            verified.releaseSequence(),
            active);
    }

    private void requireBoundInspection(
            FullPackageInspection inspection,
            VerifiedPackageSignature verified) {
        if (inspection == null || verified == null
                || inspection.manifest() == null
                || inspection.signatureEnvelope() == null
                || inspection.releaseDocument() == null
                || inspection.artifact() == null
                || inspection.manifest().packageType() != MedicalPackageType.FULL
                || !Objects.equals(
                    inspection.manifest().authorityId(), verified.authorityId())
                || !Objects.equals(
                    inspection.manifest().issuerInstanceId(), verified.issuerInstanceId())
                || !Objects.equals(inspection.manifest().keyId(), verified.keyId())
                || inspection.manifest().releaseSequence() != verified.releaseSequence()
                || !Objects.equals(
                    inspection.signatureEnvelope().authorityId(), verified.authorityId())
                || !Objects.equals(
                    inspection.signatureEnvelope().issuerInstanceId(),
                    verified.issuerInstanceId())
                || !Objects.equals(
                    inspection.signatureEnvelope().keyId(), verified.keyId())
                || !Objects.equals(
                    inspection.signatureEnvelope().rootFingerprint(),
                    verified.rootFingerprint())
                || inspection.signatureEnvelope().releaseSequence()
                    != verified.releaseSequence()
                || !Objects.equals(
                    inspection.signatureEnvelope().manifestDigest(), verified.manifestDigest())
                || !Objects.equals(
                    inspection.signatureEnvelope().certificateChainPem(),
                    verified.certificateChainPem())
                || !Objects.equals(
                    inspection.signatureEnvelope().signedAt(), verified.signedAt())
                || verified.keyNotBefore() == null
                || verified.keyNotAfter() == null
                || verified.verifiedAt() == null
                || verified.signedAt() == null
                || verified.signedAt().isBefore(verified.keyNotBefore())
                || !verified.signedAt().isBefore(verified.keyNotAfter())
                || verified.verifiedAt().isBefore(verified.keyNotBefore())
                || !verified.verifiedAt().isBefore(verified.keyNotAfter())
                || !Objects.equals(
                    inspection.manifest().platformReleaseIdentity(),
                    inspection.releaseDocument().platformReleaseIdentity())) {
            throw conflict("完整包物化输入未精确绑定已验证签名和平台版本");
        }
    }

    private Authority ensureSigningLineage(
            VerifiedPackageSignature verified,
            String actor,
            String traceId,
            Instant now) {
        Authority authority = authorities.findByTenantIdAndAuthorityIdForUpdate(
                PlatformTenant.ID, verified.authorityId())
            .orElseThrow(() -> conflict("完整包对应的平台知识权威不存在"));
        if (!Objects.equals(
                authority.activeTrustRootFingerprint(), verified.rootFingerprint())
                || (authority.activeIssuerInstanceId() == null
                    ? authority.releaseSequence() != 0
                    : !Objects.equals(
                        authority.activeIssuerInstanceId(), verified.issuerInstanceId()))) {
            throw conflict("完整包签发身份与院内预置信任状态冲突");
        }
        TrustRoot root = trustRoots
            .findByTenantIdAndAuthorityIdAndRootFingerprint(
                PlatformTenant.ID, verified.authorityId(), verified.rootFingerprint())
            .orElseThrow(() -> conflict("完整包信任根未由院内独立预置"));
        if (root.status() != TrustRootStatus.ACTIVE
                || root.validFrom() == null
                || root.validUntil() == null
                || verified.signedAt().isBefore(root.validFrom())
                || !verified.signedAt().isBefore(root.validUntil())
                || verified.verifiedAt().isBefore(root.validFrom())
                || !verified.verifiedAt().isBefore(root.validUntil())) {
            throw conflict("完整包信任根不是当前有效的院内预置信任根");
        }

        IssuerInstance issuer = issuers
            .findByTenantIdAndAuthorityIdAndIssuerInstanceId(
                PlatformTenant.ID, verified.authorityId(), verified.issuerInstanceId())
            .orElse(null);
        if (issuer == null) {
            issuer = issuers.save(new IssuerInstance(
                null,
                PlatformTenant.ID,
                verified.authorityId(),
                verified.issuerInstanceId(),
                "完整包导入签发实例 " + verified.issuerInstanceId(),
                IssuerInstanceStatus.ACTIVE,
                authority.handoverSequence(),
                verified.signedAt(),
                null,
                null,
                null,
                now,
                actor,
                now,
                actor,
                traceId));
        } else if (issuer.status() != IssuerInstanceStatus.ACTIVE
                || issuer.lastHandoverSequence() != authority.handoverSequence()) {
            throw conflict("完整包签发实例与院内活动实例状态冲突");
        }

        SigningKey key = signingKeys.findByTenantIdAndAuthorityIdAndKeyId(
                PlatformTenant.ID, verified.authorityId(), verified.keyId())
            .orElse(null);
        if (key == null) {
            signingKeys.save(new SigningKey(
                null,
                PlatformTenant.ID,
                verified.authorityId(),
                verified.issuerInstanceId(),
                verified.keyId(),
                verified.rootFingerprint(),
                verified.certificateChainPem(),
                SigningKeyStatus.ACTIVE,
                verified.keyNotBefore(),
                verified.keyNotAfter(),
                authority.handoverSequence(),
                null,
                null,
                now,
                actor,
                now,
                actor,
                traceId));
        } else if (!sameSigningKey(key, verified, authority.handoverSequence())) {
            throw conflict("完整包签名公钥与院内不可变密钥账本冲突");
        }

        if (authority.activeIssuerInstanceId() != null) {
            return authority;
        }
        return authorities.save(new Authority(
            authority.id(),
            authority.tenantId(),
            authority.authorityId(),
            verified.issuerInstanceId(),
            authority.activeTrustRootFingerprint(),
            authority.handoverSequence(),
            authority.releaseSequence(),
            authority.lockVersion(),
            authority.createdAt(),
            authority.createdBy(),
            now,
            actor,
            traceId));
    }

    private boolean sameSigningKey(
            SigningKey key,
            VerifiedPackageSignature verified,
            long handoverSequence) {
        return Objects.equals(key.issuerInstanceId(), verified.issuerInstanceId())
            && Objects.equals(key.rootFingerprint(), verified.rootFingerprint())
            && Objects.equals(key.certificateChainPem(), verified.certificateChainPem())
            && key.status() == SigningKeyStatus.ACTIVE
            && Objects.equals(key.notBefore(), verified.keyNotBefore())
            && Objects.equals(key.notAfter(), verified.keyNotAfter())
            && key.authorizedFromHandoverSequence() <= handoverSequence
            && (key.authorizedThroughHandoverSequence() == null
                || key.authorizedThroughHandoverSequence() >= handoverSequence);
    }

    private void ensureIdentity(
            FullPackageReleaseDocument.Entry entry,
            long incomingSequence,
            String actor,
            String traceId,
            Instant now) {
        AssetIdentity existing = identities
            .findByTenantIdAndAssetTypeAndAssetIdentity(
                PlatformTenant.ID, entry.assetType(), entry.assetIdentity())
            .orElse(null);
        if (existing == null) {
            identities.save(new AssetIdentity(
                null,
                PlatformTenant.ID,
                entry.assetType(),
                entry.assetIdentity(),
                AssetIdentityStatus.ACTIVE,
                incomingSequence,
                now,
                actor,
                now,
                actor,
                traceId));
            return;
        }
        if (existing.status() == AssetIdentityStatus.RETIRED
                && entry.state() == ReleaseEntryState.ACTIVE) {
            throw conflict("完整包活动资产身份在本地已退役: " + entry.assetIdentity());
        }
        if (incomingSequence <= existing.latestVersionSequence()) {
            return;
        }
        identities.save(new AssetIdentity(
            existing.id(),
            existing.tenantId(),
            existing.assetType(),
            existing.assetIdentity(),
            existing.status(),
            incomingSequence,
            existing.createdAt(),
            existing.createdBy(),
            now,
            actor,
            traceId));
    }

    private void materializeDocument(
            PortableAssetDocument document,
            String authorityId,
            String deliveryId,
            Instant signedAt,
            String actor,
            String traceId,
            Instant now) {
        String contentHash = FullPackageReleaseIntegrity.plainSha256(
            document.contentSha256());
        String sourceRef = "mkp:" + authorityId + "/" + document.versionId();
        AssetVersion expected = new AssetVersion(
            null,
            document.versionId(),
            PlatformTenant.ID,
            document.assetType(),
            document.assetIdentity(),
            document.versionNo(),
            document.organizationScope(),
            document.applicableScope(),
            contentHash,
            document.safetyPolicy(),
            document.overridePolicy(),
            AssetVersionStatus.PUBLISHED,
            "version:" + document.versionId(),
            sourceRef,
            signedAt,
            null,
            now,
            actor,
            now,
            actor,
            traceId);
        AssetVersion existing = versions
            .findByVersionIdAndTenantId(document.versionId(), PlatformTenant.ID)
            .orElse(null);
        if (existing == null) {
            versions.save(expected);
        } else if (!sameVersion(existing, expected)) {
            throw conflict("完整包资产版本与本地不可变账本冲突: " + document.versionId());
        }
        ensureContent(document, contentHash, actor, traceId, now);
        ensureDependencies(document, actor, traceId, now);
        ensureValidation(document, contentHash, signedAt, actor, traceId);
        ensureProvenance(document, authorityId, deliveryId, actor, traceId, now);
    }

    private boolean sameVersion(AssetVersion left, AssetVersion right) {
        return Objects.equals(left.versionId(), right.versionId())
            && Objects.equals(left.tenantId(), right.tenantId())
            && left.assetType() == right.assetType()
            && Objects.equals(left.assetIdentity(), right.assetIdentity())
            && Objects.equals(left.versionNo(), right.versionNo())
            && Objects.equals(left.organizationScope(), right.organizationScope())
            && Objects.equals(left.applicableScope(), right.applicableScope())
            && Objects.equals(left.contentHash(), right.contentHash())
            && left.safetyPolicy() == right.safetyPolicy()
            && left.overridePolicy() == right.overridePolicy()
            && left.status() == right.status()
            && Objects.equals(left.activeScopeKey(), right.activeScopeKey())
            && Objects.equals(left.sourceRef(), right.sourceRef())
            && Objects.equals(left.effectiveFrom(), right.effectiveFrom())
            && Objects.equals(left.effectiveTo(), right.effectiveTo());
    }

    private void ensureContent(
            PortableAssetDocument document,
            String contentHash,
            String actor,
            String traceId,
            Instant now) {
        String contentJson = new String(
            canonicalJson.encode(document.content()), java.nio.charset.StandardCharsets.UTF_8);
        AssetVersionContent existing = contents
            .findByTenantIdAndVersionId(PlatformTenant.ID, document.versionId())
            .orElse(null);
        if (existing == null) {
            contents.save(new AssetVersionContent(
                null,
                document.versionId(),
                PlatformTenant.ID,
                contentJson,
                contentHash,
                now,
                actor,
                now,
                actor,
                traceId));
            return;
        }
        if (!Objects.equals(existing.contentJson(), contentJson)
                || !Objects.equals(existing.contentHash(), contentHash)) {
            throw conflict("完整包资产正文与本地不可变正文冲突: " + document.versionId());
        }
    }

    private void ensureDependencies(
            PortableAssetDocument document,
            String actor,
            String traceId,
            Instant now) {
        List<AssetDependency> expected = document.dependencies().stream()
            .map(dependency -> new AssetDependency(
                null,
                stableId(
                    "dep-",
                    document.versionId(),
                    dependency.assetType().name(),
                    dependency.assetIdentity(),
                    dependency.versionId(),
                    dependency.dependencyKind().name()),
                PlatformTenant.ID,
                document.assetType(),
                document.assetIdentity(),
                document.versionId(),
                dependency.assetType(),
                dependency.assetIdentity(),
                dependency.versionNo(),
                dependency.versionNo(),
                dependency.dependencyKind(),
                now,
                actor,
                now,
                actor,
                traceId))
            .toList();
        List<AssetDependency> existing = dependencies
            .findByTenantIdAndAssetTypeAndAssetIdentityAndVersionIdOrderByDependsOnAssetTypeAscDependsOnIdentityAsc(
                PlatformTenant.ID,
                document.assetType(),
                document.assetIdentity(),
                document.versionId());
        if (existing == null || existing.isEmpty()) {
            expected.forEach(dependencies::save);
            return;
        }
        if (existing.size() != expected.size()
                || !existing.stream().map(this::dependencyFact).toList()
                    .equals(expected.stream().map(this::dependencyFact).toList())) {
            throw conflict("完整包资产精确依赖与本地账本冲突: " + document.versionId());
        }
    }

    private String dependencyFact(AssetDependency edge) {
        return String.join("\u001f",
            edge.dependencyId(),
            edge.tenantId(),
            edge.assetType().name(),
            edge.assetIdentity(),
            edge.versionId(),
            edge.dependsOnAssetType().name(),
            edge.dependsOnIdentity(),
            nullToEmpty(edge.minVersionNo()),
            nullToEmpty(edge.maxVersionNo()),
            edge.dependencyKind().name());
    }

    private void ensureValidation(
            PortableAssetDocument document,
            String contentHash,
            Instant signedAt,
            String actor,
            String traceId) {
        String validationId = stableId(
            "validation-",
            document.versionId(),
            document.validation().profile(),
            document.validation().resultDigest());
        AssetValidationRecord expected = new AssetValidationRecord(
            null,
            validationId,
            PlatformTenant.ID,
            document.versionId(),
            contentHash,
            true,
            document.validation().profile() + ":" + document.validation().resultDigest(),
            signedAt,
            actor,
            traceId);
        AssetValidationRecord existing = validations.findByValidationId(validationId).orElse(null);
        if (existing == null) {
            validations.save(expected);
            return;
        }
        if (!Objects.equals(existing.tenantId(), expected.tenantId())
                || !Objects.equals(existing.versionId(), expected.versionId())
                || !Objects.equals(existing.contentHash(), expected.contentHash())
                || !Boolean.TRUE.equals(existing.passed())
                || !Objects.equals(existing.summary(), expected.summary())) {
            throw conflict("完整包资产校验事实与本地账本冲突: " + document.versionId());
        }
    }

    private void ensureProvenance(
            PortableAssetDocument document,
            String authorityId,
            String deliveryId,
            String actor,
            String traceId,
            Instant now) {
        FullPackageProvenanceCodec.EncodedProvenance encoded = provenanceCodec.encode(document);
        String provenanceId = stableId(
            "provenance-", authorityId, document.versionId(), encoded.digest());
        PortableAssetProvenance existing = provenances
            .findByTenantIdAndVersionId(PlatformTenant.ID, document.versionId())
            .orElse(null);
        if (existing == null) {
            provenances.save(new PortableAssetProvenance(
                null,
                provenanceId,
                PlatformTenant.ID,
                authorityId,
                deliveryId,
                document.assetType(),
                document.assetIdentity(),
                document.versionId(),
                encoded.json(),
                encoded.digest(),
                now,
                actor,
                now,
                actor,
                traceId));
            return;
        }
        if (!Objects.equals(existing.authorityId(), authorityId)
                || existing.assetType() != document.assetType()
                || !Objects.equals(existing.assetIdentity(), document.assetIdentity())
                || !Objects.equals(existing.provenanceJson(), encoded.json())
                || !Objects.equals(existing.provenanceDigest(), encoded.digest())) {
            throw conflict("完整包资产来源许可事实与本地账本冲突: " + document.versionId());
        }
    }

    private void materializeWithdrawals(
            FullPackageInspection inspection,
            String actor,
            String traceId,
            Instant now) {
        for (FullPackageReleaseDocument.Withdrawal withdrawal
                : inspection.releaseDocument().withdrawals()) {
            String withdrawalId = stableId(
                "withdrawal-",
                inspection.manifest().authorityId(),
                inspection.manifest().deliveryId(),
                withdrawal.assetType().name(),
                withdrawal.assetIdentity(),
                withdrawal.withdrawnVersionId());
            FullPackageWithdrawal existing = withdrawals
                .findByTenantIdAndAuthorityIdAndDeliveryIdAndAssetTypeAndAssetIdentityAndWithdrawnVersionId(
                    PlatformTenant.ID,
                    inspection.manifest().authorityId(),
                    inspection.manifest().deliveryId(),
                    withdrawal.assetType(),
                    withdrawal.assetIdentity(),
                    withdrawal.withdrawnVersionId())
                .orElse(null);
            FullPackageWithdrawal expected = new FullPackageWithdrawal(
                null,
                withdrawalId,
                PlatformTenant.ID,
                inspection.manifest().authorityId(),
                inspection.manifest().deliveryId(),
                inspection.manifest().releaseSequence(),
                withdrawal.assetType(),
                withdrawal.assetIdentity(),
                withdrawal.withdrawnVersionId(),
                withdrawal.successorVersionId(),
                withdrawal.reasonDigest(),
                now,
                actor,
                now,
                actor,
                traceId);
            if (existing == null) {
                withdrawals.save(expected);
            } else if (!sameWithdrawal(existing, expected)) {
                throw conflict("完整包撤回事实与本地账本冲突: "
                    + withdrawal.withdrawnVersionId());
            }
            withdrawExistingVersion(withdrawal, actor, traceId, now);
        }
    }

    private boolean sameWithdrawal(FullPackageWithdrawal left, FullPackageWithdrawal right) {
        return Objects.equals(left.withdrawalId(), right.withdrawalId())
            && left.releaseSequence() == right.releaseSequence()
            && left.assetType() == right.assetType()
            && Objects.equals(left.assetIdentity(), right.assetIdentity())
            && Objects.equals(left.withdrawnVersionId(), right.withdrawnVersionId())
            && Objects.equals(left.successorVersionId(), right.successorVersionId())
            && Objects.equals(left.reasonDigest(), right.reasonDigest());
    }

    private void withdrawExistingVersion(
            FullPackageReleaseDocument.Withdrawal withdrawal,
            String actor,
            String traceId,
            Instant now) {
        AssetVersion local = versions
            .findByVersionIdAndTenantId(
                withdrawal.withdrawnVersionId(), PlatformTenant.ID)
            .orElse(null);
        if (local == null) {
            return;
        }
        if (local.assetType() != withdrawal.assetType()
                || !Objects.equals(local.assetIdentity(), withdrawal.assetIdentity())) {
            throw conflict("完整包撤回版本与本地稳定身份冲突: "
                + withdrawal.withdrawnVersionId());
        }
        if (local.status() != AssetVersionStatus.WITHDRAWN) {
            versions.save(local.withStatusAndWindow(
                AssetVersionStatus.WITHDRAWN,
                "version:" + local.versionId(),
                local.effectiveFrom(),
                now,
                now,
                actor));
        }
    }

    private void materializeBaseline(
            FullPackageReleaseDocument release,
            List<FullPackageReleaseDocument.Entry> orderedEntries,
            String actor,
            String traceId,
            Instant now) {
        String manifestHash = FullPackageReleaseIntegrity.plainSha256(
            release.platformManifestSha256());
        PlatformBaselineRelease existing = baselines
            .findByBaselineReleaseId(release.platformReleaseIdentity())
            .orElse(null);
        if (existing == null) {
            baselines.save(new PlatformBaselineRelease(
                null,
                release.platformReleaseIdentity(),
                release.revisionNo(),
                manifestHash,
                now,
                actor,
                now,
                actor,
                traceId));
            for (FullPackageReleaseDocument.Entry entry : orderedEntries) {
                baselineItems.save(toBaselineItem(
                    release.platformReleaseIdentity(), entry, actor, traceId, now));
            }
            return;
        }
        if (existing.revisionNo() != release.revisionNo()
                || !Objects.equals(existing.manifestSha256(), manifestHash)) {
            throw conflict("完整包平台版本与本地不可变基线冲突: "
                + release.platformReleaseIdentity());
        }
        List<String> actual = baselineItems
            .findByBaselineReleaseIdOrderByAssetTypeAscAssetIdentityAsc(
                release.platformReleaseIdentity())
            .stream()
            .map(this::baselineFact)
            .toList();
        List<String> expected = orderedEntries.stream()
            .map(entry -> baselineFact(toBaselineItem(
                release.platformReleaseIdentity(), entry, actor, traceId, now)))
            .toList();
        if (!actual.equals(expected)) {
            throw conflict("完整包平台版本明细与本地不可变基线冲突: "
                + release.platformReleaseIdentity());
        }
    }

    private PlatformBaselineItem toBaselineItem(
            String releaseId,
            FullPackageReleaseDocument.Entry entry,
            String actor,
            String traceId,
            Instant now) {
        return new PlatformBaselineItem(
            null,
            releaseId,
            PlatformTenant.ID,
            entry.assetType(),
            entry.assetIdentity(),
            entry.state(),
            entry.versionId(),
            entry.versionNo(),
            FullPackageReleaseIntegrity.plainSha256(entry.sourceContentSha256()),
            now,
            actor,
            traceId);
    }

    private String baselineFact(PlatformBaselineItem item) {
        return String.join("\u001f",
            item.baselineReleaseId(),
            item.sourceTenantId(),
            item.assetType().name(),
            item.assetIdentity(),
            item.entryState().name(),
            nullToEmpty(item.versionId()),
            nullToEmpty(item.versionNo()),
            nullToEmpty(item.contentHash()));
    }

    private void registerPackage(
            FullPackageInspection inspection,
            VerifiedPackageSignature verified,
            StoredFullPackage stored,
            Authority authority,
            String actor,
            String traceId,
            Instant now) {
        PackageRegistration expected = new PackageRegistration(
            null,
            PlatformTenant.ID,
            verified.authorityId(),
            inspection.manifest().deliveryId(),
            verified.releaseSequence(),
            verified.manifestDigest(),
            inspection.manifest().platformReleaseIdentity(),
            stored.packageFileDigest(),
            stored.packageFileSize(),
            stored.storageCoordinate(),
            verified.issuerInstanceId(),
            verified.keyId(),
            null,
            null,
            null,
            MedicalPackageType.FULL,
            PackageSigningStatus.SIGNED,
            verified.signedAt(),
            now,
            null,
            now,
            actor,
            now,
            actor,
            traceId);
        PackageRegistration existing = registrations
            .findByTenantIdAndAuthorityIdAndDeliveryId(
                PlatformTenant.ID,
                verified.authorityId(),
                inspection.manifest().deliveryId())
            .orElse(null);
        if (existing == null) {
            if (verified.releaseSequence() <= authority.releaseSequence()) {
                throw conflict("完整包发布序号必须高于本地已接受序号，当前="
                    + authority.releaseSequence() + "，实际=" + verified.releaseSequence());
            }
            if (registrations.findByTenantIdAndAuthorityIdAndReleaseSequence(
                    PlatformTenant.ID,
                    verified.authorityId(),
                    verified.releaseSequence()).isPresent()) {
                throw conflict("完整包发布序号已绑定其他不可变交付事实");
            }
            registrations.save(expected);
            authorities.save(new Authority(
                authority.id(),
                authority.tenantId(),
                authority.authorityId(),
                authority.activeIssuerInstanceId(),
                authority.activeTrustRootFingerprint(),
                authority.handoverSequence(),
                verified.releaseSequence(),
                authority.lockVersion(),
                authority.createdAt(),
                authority.createdBy(),
                now,
                actor,
                traceId));
            return;
        }
        if (!sameRegistration(existing, expected)) {
            throw conflict("完整包登记与本地不可变包账本冲突: "
                + inspection.manifest().deliveryId());
        }
        if (authority.releaseSequence() < verified.releaseSequence()) {
            throw conflict("完整包登记已存在但平台权威发布游标落后");
        }
    }

    private boolean sameRegistration(PackageRegistration left, PackageRegistration right) {
        return left.releaseSequence() == right.releaseSequence()
            && Objects.equals(left.manifestDigest(), right.manifestDigest())
            && Objects.equals(left.platformReleaseIdentity(), right.platformReleaseIdentity())
            && Objects.equals(left.packageFileDigest(), right.packageFileDigest())
            && left.packageFileSize() == right.packageFileSize()
            && Objects.equals(left.storageCoordinate(), right.storageCoordinate())
            && Objects.equals(left.issuerInstanceId(), right.issuerInstanceId())
            && Objects.equals(left.keyId(), right.keyId())
            && left.packageType() == MedicalPackageType.FULL
            && left.signingStatus() == PackageSigningStatus.SIGNED
            && Objects.equals(left.signedAt(), right.signedAt());
    }

    private String stableId(String prefix, String... facts) {
        return prefix + crypto.sm3Hex(String.join("\n", facts)).substring(0, 40);
    }

    private static String required(String value, String label) {
        if (value == null || value.isBlank()) {
            throw invalid(label + "不能为空");
        }
        return value.trim();
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    private static ApiException invalid(String message) {
        return new ApiException(ErrorCode.VALIDATION_FAILED, message);
    }

    private static ApiException conflict(String message) {
        return new ApiException(ErrorCode.CONFLICT, message);
    }

    private record AssetKey(
        com.medkernel.engine.versioning.VersionedAssetType type,
        String identity
    ) {
    }
}
