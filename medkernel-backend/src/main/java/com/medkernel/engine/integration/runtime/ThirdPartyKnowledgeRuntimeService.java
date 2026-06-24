package com.medkernel.engine.integration.runtime;

import org.springframework.stereotype.Service;

import com.medkernel.engine.context.ClinicalRuntimeRelease;
import com.medkernel.engine.context.ClinicalRuntimeReleaseContent;
import com.medkernel.engine.context.ClinicalRuntimeReleaseContentResolver;
import com.medkernel.engine.context.ContextSnapshotRequest;
import com.medkernel.engine.context.ContextSnapshotResponse;
import com.medkernel.engine.context.ContextSnapshotService;
import com.medkernel.engine.context.CurrentClinicalRuntimeReleaseResolver;
import com.medkernel.shared.context.RequestContext;

/**
 * 第三方临床运行门面。
 *
 * <p>调用方只提交患者上下文；运行资产由认证医院的当前不可变机构生效版本唯一确定，
 * 禁止外部系统选择离线交付文件、领域或资产版本。
 */
@Service
public class ThirdPartyKnowledgeRuntimeService {

    public static final String CONTRACT_VERSION = "v1";

    private final CurrentClinicalRuntimeReleaseResolver currentReleases;
    private final ClinicalRuntimeReleaseContentResolver releaseContents;
    private final ContextSnapshotService contexts;

    public ThirdPartyKnowledgeRuntimeService(
            CurrentClinicalRuntimeReleaseResolver currentReleases,
            ClinicalRuntimeReleaseContentResolver releaseContents,
            ContextSnapshotService contexts) {
        this.currentReleases = currentReleases;
        this.releaseContents = releaseContents;
        this.contexts = contexts;
    }

    /**
     * 返回认证医院当前完整机构生效版本，并再次校验不可变明细校验码。
     */
    public ThirdPartyRuntimeReleaseResponse resolveCurrentRuntimeRelease() {
        ClinicalRuntimeRelease release =
            currentReleases.resolve(RequestContext.currentOrgScope());
        ClinicalRuntimeReleaseContent content =
            releaseContents.resolve(release.tenantId(), release.releaseId());
        return ThirdPartyRuntimeReleaseResponse.from(CONTRACT_VERSION, content);
    }

    /**
     * 写入标准临床上下文快照。
     */
    public ContextSnapshotResponse writeContext(
            ContextSnapshotRequest request,
            String idempotencyKey) {
        return contexts.create(request, idempotencyKey);
    }
}
