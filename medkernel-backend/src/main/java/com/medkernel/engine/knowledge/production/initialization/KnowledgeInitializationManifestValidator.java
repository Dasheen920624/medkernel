package com.medkernel.engine.knowledge.production.initialization;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.stereotype.Component;

import com.medkernel.shared.api.error.ApiException;
import com.medkernel.shared.api.error.ErrorCode;
import com.medkernel.shared.hash.Sha256ContentHash;

/** 初始化发行清单的 B0 完整性、依赖与稳定摘要校验器。 */
@Component
public class KnowledgeInitializationManifestValidator {

    private final KnowledgeInitializationCatalog catalog;

    public KnowledgeInitializationManifestValidator(KnowledgeInitializationCatalog catalog) {
        this.catalog = catalog;
    }

    public InitializationManifestValidation validate(InitializationManifestDraft draft) {
        requireDraft(draft);
        if (draft.releaseType() == InitializationReleaseType.FOUNDATION) {
            Set<FoundationCoverageDimension> missing =
                new LinkedHashSet<>(catalog.requiredFoundationCoverage());
            missing.removeAll(draft.coverage());
            if (!missing.isEmpty()) {
                throw badRequest("基础发行覆盖不完整，缺少 " + missing);
            }
            if (draft.phase() == InitializationPhase.F8) {
                Set<String> missingCatalogDomains =
                    new LinkedHashSet<>(catalog.requiredFoundationCatalogCodes());
                draft.items().stream()
                    .map(InitializationManifestDraftItem::catalogCode)
                    .forEach(missingCatalogDomains::remove);
                if (!missingCatalogDomains.isEmpty()) {
                    throw badRequest("基础发行总装缺少目录域 " + missingCatalogDomains);
                }
            }
        } else if (!draft.foundationReleaseComplete()) {
            throw badRequest("基础发行版未完成或版本不兼容，临床/组合发行不得开始");
        }
        if (draft.declaredEntryCount() != draft.items().size()) {
            throw badRequest("声明条目数与真实清单不一致");
        }
        long sourceCount = draft.items().stream()
            .map(InitializationManifestDraftItem::sourceVersionId)
            .distinct()
            .count();
        if (draft.declaredSourceFileCount() != sourceCount) {
            throw badRequest("声明来源文件数与真实清单不一致");
        }

        Map<String, InitializationManifestDraftItem> byCanonical = new HashMap<>();
        for (InitializationManifestDraftItem item : draft.items()) {
            validateItem(draft, item);
            InitializationManifestDraftItem duplicate = byCanonical.putIfAbsent(item.canonicalId(), item);
            if (duplicate != null) {
                throw badRequest("canonical ID 重复：" + item.canonicalId());
            }
        }
        validateReferences(draft, byCanonical);
        validateCycles(byCanonical);
        validateDimensions(byCanonical);

        String sourceManifest = draft.items().stream()
            .map(item -> item.sourceVersionId() + ":" + item.sourceHash())
            .distinct()
            .sorted()
            .reduce((left, right) -> left + "\n" + right)
            .orElse("");
        String candidateManifest = draft.items().stream()
            .sorted(Comparator.comparing(InitializationManifestDraftItem::canonicalId))
            .map(this::candidateManifestLine)
            .reduce((left, right) -> left + "\n" + right)
            .orElse("");
        String sourceHash = Sha256ContentHash.sha256(sourceManifest, "来源发行清单不能为空");
        String candidateHash = Sha256ContentHash.sha256(candidateManifest, "候选发行清单不能为空");
        String overallHash = Sha256ContentHash.sha256(
            String.join("|",
                token(draft.releaseType()),
                token(draft.releaseVersion()),
                token(draft.foundationReleaseVersion()),
                token(draft.phase()),
                token(draft.templateVersion()),
                token(draft.modelVersion()),
                token(draft.summary()),
                token(draft.declaredSourceFileCount()),
                token(draft.declaredEntryCount()),
                token(String.join(",", draft.coverage().stream()
                    .map(Enum::name)
                    .sorted()
                    .toList())),
                token(sourceHash),
                token(candidateHash)),
            "发行总摘要不能为空");
        return new InitializationManifestValidation(sourceHash, candidateHash, overallHash);
    }

    private void requireDraft(InitializationManifestDraft draft) {
        if (draft == null || draft.releaseType() == null || draft.phase() == null
                || blank(draft.releaseVersion()) || blank(draft.templateVersion())
                || blank(draft.summary()) || draft.items().isEmpty()) {
            throw badRequest("初始化发行清单缺少类型、版本、阶段或条目");
        }
        if (!draft.releaseVersion().matches("\\d+\\.\\d+\\.\\d+")) {
            throw badRequest("发行版本必须使用 major.minor.patch");
        }
        if (draft.releaseType() != InitializationReleaseType.FOUNDATION
                && (blank(draft.foundationReleaseVersion())
                    || !draft.foundationReleaseVersion().matches("\\d+\\.\\d+\\.\\d+"))) {
            throw badRequest("临床/组合发行必须锁定基础发行版本");
        }
    }

    private void validateItem(InitializationManifestDraft draft, InitializationManifestDraftItem item) {
        if (item == null || blank(item.catalogCode()) || item.assetType() == null
                || blank(item.canonicalId()) || blank(item.namespace()) || blank(item.assetVersion())
                || item.sourceVersionId() == null || !hash(item.sourceHash())
                || blank(item.candidateRef()) || !hash(item.candidateContentHash())
                || item.riskLevel() == null || item.changeType() == null) {
            throw badRequest("初始化条目缺少 canonical、版本、来源、候选或风险事实");
        }
        KnowledgeInitializationCatalogItem catalogItem = catalog.find(item.catalogCode())
            .orElseThrow(() -> badRequest("未知 KNOWGEN 目录项：" + item.catalogCode()));
        if (catalogItem.releaseType() != draft.releaseType()
                || catalogItem.phase().order() > draft.phase().order()) {
            throw badRequest(item.catalogCode() + " 不属于当前发行类型或阶段");
        }
        if (!item.assetVersion().matches("\\d+\\.\\d+\\.\\d+")) {
            throw badRequest("资产版本必须使用 major.minor.patch：" + item.canonicalId());
        }
        if (draft.releaseType() == InitializationReleaseType.FOUNDATION && item.generatedByModel()) {
            throw badRequest("基础 canonical 数据禁止由模型生成：" + item.canonicalId());
        }
        if (blank(item.sourcePolicy()) || blank(item.reviewPolicy()) || blank(item.testEvidenceRef())
                || blank(item.ownerRole()) || blank(item.runtimeConsumers()) || blank(item.rollbackStrategy())) {
            throw badRequest("六维覆盖责任不完整：" + item.canonicalId());
        }
        if (item.changeType() == InitializationChangeType.DEPRECATION
                && (blank(item.replacementCanonicalId()) || item.effectiveTo() == null)) {
            throw badRequest("废止资产必须声明 replacement 与 effectiveTo：" + item.canonicalId());
        }
        if (item.changeType() != InitializationChangeType.DEPRECATION
                && (!blank(item.replacementCanonicalId()) || item.effectiveTo() != null)) {
            throw badRequest("仅废止资产可声明 replacement 与 effectiveTo：" + item.canonicalId());
        }
        if (item.changeType() == InitializationChangeType.DEPRECATION
                && item.canonicalId().equals(item.replacementCanonicalId())) {
            throw badRequest("废止 replacement 不得指向自身：" + item.canonicalId());
        }
    }

    private void validateReferences(
            InitializationManifestDraft draft,
            Map<String, InitializationManifestDraftItem> byCanonical) {
        Set<String> available = new HashSet<>(draft.availableCanonicalIds());
        available.addAll(byCanonical.keySet());
        for (InitializationManifestDraftItem item : draft.items()) {
            List<String> refs = new ArrayList<>(item.dependencyCanonicalIds());
            if (!blank(item.parentCanonicalId())) {
                refs.add(item.parentCanonicalId());
            }
            if (!blank(item.conversionTargetCanonicalId())) {
                refs.add(item.conversionTargetCanonicalId());
            }
            for (String ref : refs) {
                if (!available.contains(ref)) {
                    throw badRequest("孤儿依赖：" + item.canonicalId() + " -> " + ref);
                }
                if (item.canonicalId().equals(ref)) {
                    throw badRequest("非法层级或自引用：" + item.canonicalId());
                }
            }
        }
    }

    private void validateCycles(Map<String, InitializationManifestDraftItem> byCanonical) {
        Set<String> visited = new HashSet<>();
        Set<String> active = new HashSet<>();
        for (String canonicalId : byCanonical.keySet()) {
            visit(canonicalId, byCanonical, visited, active, new ArrayDeque<>());
        }
    }

    private void visit(
            String canonicalId,
            Map<String, InitializationManifestDraftItem> byCanonical,
            Set<String> visited,
            Set<String> active,
            ArrayDeque<String> path) {
        if (visited.contains(canonicalId)) {
            return;
        }
        if (!active.add(canonicalId)) {
            path.addLast(canonicalId);
            throw badRequest("循环依赖：" + String.join(" -> ", path));
        }
        path.addLast(canonicalId);
        InitializationManifestDraftItem item = byCanonical.get(canonicalId);
        if (item != null) {
            for (String dependency : item.dependencyCanonicalIds()) {
                if (byCanonical.containsKey(dependency)) {
                    visit(dependency, byCanonical, visited, active, path);
                }
            }
        }
        path.removeLast();
        active.remove(canonicalId);
        visited.add(canonicalId);
    }

    private void validateDimensions(Map<String, InitializationManifestDraftItem> byCanonical) {
        for (InitializationManifestDraftItem item : byCanonical.values()) {
            if (blank(item.conversionTargetCanonicalId())) {
                continue;
            }
            InitializationManifestDraftItem target = byCanonical.get(item.conversionTargetCanonicalId());
            if (target != null && !blank(item.unitDimension())
                    && !item.unitDimension().equals(target.unitDimension())) {
                throw badRequest("单位换算量纲冲突：" + item.canonicalId()
                    + " -> " + target.canonicalId());
            }
        }
    }

    private String candidateManifestLine(InitializationManifestDraftItem item) {
        return String.join("|",
            token(item.catalogCode()),
            token(item.assetType()),
            token(item.canonicalId()),
            token(item.namespace()),
            token(item.assetVersion()),
            token(item.candidateRef()),
            token(item.candidateContentHash()),
            token(item.riskLevel()),
            token(item.generatedByModel()),
            token(String.join(",", item.dependencyCanonicalIds().stream().sorted().toList())),
            token(item.parentCanonicalId()),
            token(item.unitDimension()),
            token(item.conversionTargetCanonicalId()),
            token(item.sourcePolicy()),
            token(item.reviewPolicy()),
            token(item.testEvidenceRef()),
            token(item.ownerRole()),
            token(item.runtimeConsumers()),
            token(item.rollbackStrategy()),
            token(item.changeType()),
            token(item.replacementCanonicalId()),
            token(item.effectiveTo()));
    }

    private String token(Object value) {
        String text = value == null ? "" : value.toString();
        return text.length() + ":" + text;
    }

    private boolean blank(String value) {
        return value == null || value.isBlank();
    }

    private boolean hash(String value) {
        return value != null && value.matches("[0-9a-f]{64}");
    }

    private ApiException badRequest(String message) {
        return new ApiException(ErrorCode.BAD_REQUEST, message);
    }
}
