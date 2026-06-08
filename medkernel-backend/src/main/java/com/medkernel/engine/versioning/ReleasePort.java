package com.medkernel.engine.versioning;

/**
 * 配置资产通用发布端口。
 */
public interface ReleasePort {

    VersionReleasePlan submitForReview(VersionReleaseCommand command);

    VersionReleasePlan rejectReview(VersionReleaseCommand command);

    VersionReleasePlan approveReview(VersionReleaseCommand command);

    VersionReleasePlan releaseGray(VersionReleaseCommand command);

    VersionReleasePlan publish(VersionReleaseCommand command);

    VersionReleasePlan rollback(VersionRollbackCommand command);
}
