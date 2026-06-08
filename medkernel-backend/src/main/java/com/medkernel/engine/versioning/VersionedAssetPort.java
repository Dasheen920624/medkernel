package com.medkernel.engine.versioning;

/**
 * 配置资产不可变版本端口。
 *
 * <p>各业务引擎通过该端口登记和更新草稿；评审、发布、回滚统一走 {@link ReleasePort}，
 * 避免任何域绕过治理状态机。
 */
public interface VersionedAssetPort {

    /**
     * 登记草稿版本，并生成或校验稳定内容指纹。
     */
    AssetVersion registerDraft(AssetVersionRegisterCommand command);

    /** 更新草稿或评审中版本的完整登记；批准及后续状态必须拒绝原地修改。 */
    AssetVersion updateDraft(AssetVersionDraftUpdateCommand command);
}
