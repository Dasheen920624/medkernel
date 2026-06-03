package com.medkernel.engine.projection;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.stereotype.Component;

import com.medkernel.engine.knowledge.Citation;
import com.medkernel.engine.knowledge.CitationRepository;
import com.medkernel.engine.knowledge.KnowledgeAssetVersion;
import com.medkernel.engine.knowledge.KnowledgeAssetVersionRepository;
import com.medkernel.engine.knowledge.KnowledgeIdentity;
import com.medkernel.engine.knowledge.KnowledgeIdentityRepository;
import com.medkernel.engine.knowledge.KnowledgeVersionStatus;
import com.medkernel.engine.knowledge.SourceDocument;
import com.medkernel.engine.knowledge.SourceDocumentRepository;
import com.medkernel.engine.knowledge.SourceFragment;
import com.medkernel.engine.knowledge.SourceFragmentRepository;
import com.medkernel.engine.knowledge.SourceVersion;
import com.medkernel.engine.knowledge.SourceVersionRepository;

/**
 * 从关系库知识资产权威表生成可重放的知识图与搜索投影事实。
 */
@Component
public class KnowledgeProjectionSource {

    private static final String HAS_ACTIVE_VERSION = "HAS_ACTIVE_VERSION";
    private static final String CITES_FRAGMENT = "CITES_FRAGMENT";
    private static final String BELONGS_TO_SOURCE = "BELONGS_TO_SOURCE";

    private final KnowledgeIdentityRepository identities;
    private final KnowledgeAssetVersionRepository versions;
    private final CitationRepository citations;
    private final SourceFragmentRepository fragments;
    private final SourceVersionRepository sourceVersions;
    private final SourceDocumentRepository documents;

    public KnowledgeProjectionSource(
            KnowledgeIdentityRepository identities,
            KnowledgeAssetVersionRepository versions,
            CitationRepository citations,
            SourceFragmentRepository fragments,
            SourceVersionRepository sourceVersions,
            SourceDocumentRepository documents) {
        this.identities = identities;
        this.versions = versions;
        this.citations = citations;
        this.fragments = fragments;
        this.sourceVersions = sourceVersions;
        this.documents = documents;
    }

    public List<ProjectionFact> graphFactsForTenant(String tenantId) {
        Map<String, ProjectionFact> facts = new LinkedHashMap<>();
        for (KnowledgeAssetVersion version : activeVersions(tenantId)) {
            Optional<KnowledgeIdentity> identityOpt = identities.findByTenantIdAndId(tenantId, version.identityId());
            if (identityOpt.isEmpty()) {
                continue;
            }
            KnowledgeIdentity identity = identityOpt.get();
            add(facts, identityNode(identity));
            add(facts, versionNode(version));
            add(facts, edge(
                identityKey(identity.id()),
                HAS_ACTIVE_VERSION,
                versionKey(version.id()),
                version.updatedAt()));

            for (ResolvedCitation resolved : resolveCitations(tenantId, version)) {
                add(facts, sourceDocumentNode(resolved.document()));
                add(facts, sourceFragmentNode(resolved.fragment()));
                add(facts, edge(versionKey(version.id()), CITES_FRAGMENT,
                    fragmentKey(resolved.fragment().id()), version.updatedAt()));
                add(facts, edge(fragmentKey(resolved.fragment().id()), BELONGS_TO_SOURCE,
                    documentKey(resolved.document().id()), resolved.fragment().createdAt()));
            }
        }
        return List.copyOf(facts.values());
    }

    public List<ProjectionFact> searchFactsForTenant(String tenantId) {
        List<ProjectionFact> facts = new ArrayList<>();
        for (KnowledgeAssetVersion version : activeVersions(tenantId)) {
            Optional<KnowledgeIdentity> identityOpt = identities.findByTenantIdAndId(tenantId, version.identityId());
            if (identityOpt.isEmpty()) {
                continue;
            }
            KnowledgeIdentity identity = identityOpt.get();
            List<ResolvedCitation> resolved = resolveCitations(tenantId, version);
            SourceDocument primarySource = resolved.stream()
                .map(ResolvedCitation::document)
                .findFirst()
                .orElse(null);
            facts.add(searchDocumentNode(identity, version, primarySource, resolved.size()));
        }
        return List.copyOf(facts);
    }

    private List<KnowledgeAssetVersion> activeVersions(String tenantId) {
        return versions.findByTenantIdAndStatusOrderByUpdatedAtDescIdDesc(tenantId, KnowledgeVersionStatus.ACTIVE);
    }

    private List<ResolvedCitation> resolveCitations(String tenantId, KnowledgeAssetVersion version) {
        List<ResolvedCitation> resolved = new ArrayList<>();
        for (Citation citation : citations.findByTenantIdAndAssetVersionIdOrderByWeightDescIdAsc(tenantId, version.id())) {
            Optional<SourceFragment> fragmentOpt = fragments.findByTenantIdAndId(tenantId, citation.sourceFragmentId());
            if (fragmentOpt.isEmpty()) {
                continue;
            }
            SourceFragment fragment = fragmentOpt.get();
            Optional<SourceVersion> sourceVersionOpt = sourceVersions.findByTenantIdAndId(tenantId, fragment.sourceVersionId());
            if (sourceVersionOpt.isEmpty()) {
                continue;
            }
            SourceVersion sourceVersion = sourceVersionOpt.get();
            Optional<SourceDocument> documentOpt = documents.findByTenantIdAndId(tenantId, sourceVersion.sourceDocumentId());
            documentOpt.ifPresent(document -> resolved.add(new ResolvedCitation(citation, fragment, sourceVersion, document)));
        }
        return List.copyOf(resolved);
    }

    private ProjectionFact identityNode(KnowledgeIdentity identity) {
        return node("KNOWLEDGE_IDENTITY", identity.id(), payload(
            "tenantId", identity.tenantId(),
            "identityCode", identity.identityCode(),
            "domain", value(identity.domain()),
            "subject", identity.subject(),
            "specialtyId", identity.specialtyId(),
            "status", value(identity.status()),
            "currentVersionId", value(identity.currentVersionId()),
            "updatedAt", value(identity.updatedAt())), identity.updatedAt());
    }

    private ProjectionFact versionNode(KnowledgeAssetVersion version) {
        return node("KNOWLEDGE_VERSION", version.id(), payload(
            "tenantId", version.tenantId(),
            "identityId", value(version.identityId()),
            "versionNo", version.versionNo(),
            "versionLabel", version.versionLabel(),
            "status", value(version.status()),
            "riskLevel", value(version.riskLevel()),
            "authorityLevel", value(version.authorityLevel()),
            "gradeQuality", value(version.gradeQuality()),
            "gradeStrength", value(version.gradeStrength()),
            "organizationScope", version.effectiveOrganizationScope(),
            "applicableScope", version.effectiveApplicableScope(),
            "activeScopeKey", version.activeScopeKeyForActiveStatus(),
            "contentHash", version.contentHash(),
            "effectiveFrom", value(version.effectiveFrom()),
            "effectiveTo", value(version.effectiveTo()),
            "updatedAt", value(version.updatedAt())), version.updatedAt());
    }

    private ProjectionFact sourceDocumentNode(SourceDocument document) {
        return node("SOURCE_DOCUMENT", document.id(), payload(
            "tenantId", document.tenantId(),
            "sourceCode", document.sourceCode(),
            "sourceType", value(document.sourceType()),
            "authorityLevel", value(document.authorityLevel()),
            "title", document.title(),
            "publisher", document.publisher(),
            "language", document.language(),
            "updatedAt", value(document.updatedAt())), document.updatedAt());
    }

    private ProjectionFact sourceFragmentNode(SourceFragment fragment) {
        return node("SOURCE_FRAGMENT", fragment.id(), payload(
            "tenantId", fragment.tenantId(),
            "sourceVersionId", value(fragment.sourceVersionId()),
            "anchorPath", fragment.anchorPath(),
            "anchorLabel", fragment.anchorLabel(),
            "contentHash", fragment.contentHash(),
            "createdAt", value(fragment.createdAt())), fragment.createdAt());
    }

    private ProjectionFact searchDocumentNode(KnowledgeIdentity identity, KnowledgeAssetVersion version,
            SourceDocument sourceDocument, int citationCount) {
        return new ProjectionFact(
            ProjectionTargetType.KNOWLEDGE_SEARCH,
            ProjectionFactKind.NODE,
            "KNOWLEDGE_SEARCH_DOCUMENT",
            String.valueOf(version.id()),
            null,
            null,
            null,
            payload(
                "tenantId", version.tenantId(),
                "identityId", value(identity.id()),
                "versionId", value(version.id()),
                "identityCode", identity.identityCode(),
                "domain", value(identity.domain()),
                "subject", identity.subject(),
                "specialtyId", identity.specialtyId(),
                "versionNo", version.versionNo(),
                "riskLevel", value(version.riskLevel()),
                "authorityLevel", value(version.authorityLevel()),
                "gradeQuality", value(version.gradeQuality()),
                "gradeStrength", value(version.gradeStrength()),
                "organizationScope", version.effectiveOrganizationScope(),
                "applicableScope", version.effectiveApplicableScope(),
                "activeScopeKey", version.activeScopeKeyForActiveStatus(),
                "contentHash", version.contentHash(),
                "sourceCode", sourceDocument == null ? null : sourceDocument.sourceCode(),
                "sourceTitle", sourceDocument == null ? null : sourceDocument.title(),
                "citationCount", value(citationCount),
                "updatedAt", value(version.updatedAt())),
            null,
            version.updatedAt());
    }

    private ProjectionFact node(String type, Long id, String payload, Instant updatedAt) {
        return ProjectionFact.node(ProjectionTargetType.KNOWLEDGE_GRAPH, type, String.valueOf(id), payload, updatedAt);
    }

    private ProjectionFact edge(String subjectKey, String predicate, String objectKey, Instant updatedAt) {
        return ProjectionFact.edge(ProjectionTargetType.KNOWLEDGE_GRAPH, subjectKey, predicate, objectKey, payload(
            "from", subjectKey,
            "predicate", predicate,
            "to", objectKey,
            "updatedAt", value(updatedAt)), updatedAt);
    }

    private void add(Map<String, ProjectionFact> facts, ProjectionFact fact) {
        facts.put(fact.factKey(), fact);
    }

    private String identityKey(Long id) {
        return "KNOWLEDGE_IDENTITY:" + id;
    }

    private String versionKey(Long id) {
        return "KNOWLEDGE_VERSION:" + id;
    }

    private String fragmentKey(Long id) {
        return "SOURCE_FRAGMENT:" + id;
    }

    private String documentKey(Long id) {
        return "SOURCE_DOCUMENT:" + id;
    }

    private String payload(String... pairs) {
        List<String> parts = new ArrayList<>();
        for (int i = 0; i < pairs.length; i += 2) {
            parts.add(pairs[i] + "=" + sanitize(pairs[i + 1]));
        }
        return String.join("|", parts);
    }

    private String value(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private String sanitize(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("|", "\\|").replace("\n", " ");
    }

    private record ResolvedCitation(
        Citation citation,
        SourceFragment fragment,
        SourceVersion sourceVersion,
        SourceDocument document
    ) {}
}
