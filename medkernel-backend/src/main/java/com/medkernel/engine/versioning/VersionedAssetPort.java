package com.medkernel.engine.versioning;

/**
 * 配置资产不可变版本端口。
 *
 * <p>各业务引擎通过该端口登记版本、发布版本和激活版本，避免在知识、规则、路径、
 * 包等引擎内重复实现互相漂移的版本规则。
 */
public interface VersionedAssetPort {

    /**
     * 登记草稿版本，并生成或校验稳定内容指纹。
     */
    AssetVersion registerDraft(AssetVersionRegisterCommand command);

    /**
     * 更新草稿或待审核版本内容；已发布及后续状态必须拒绝原地修改。
     */
    AssetVersion updateDraftContent(
        String tenantId,
        String versionId,
        String content,
        String contentHash,
        String actor
    );

    /**
     * 将草稿或待审核版本置为已发布，只读但尚未成为运行生效版本。
     */
    AssetVersion publish(String tenantId, String versionId, String actor);

    /**
     * 将已发布版本激活到同一生效域；同域已有其他 ACTIVE 时拒绝。
     */
    AssetVersion activate(String tenantId, String versionId, String actor);
}
