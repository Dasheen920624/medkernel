package com.medkernel.engine.versioning;

/**
 * 灰度自动暂停通知端口。
 */
public interface RolloutPauseNotifier {

    void notifyPaused(VersionReleasePlan plan, String reason);
}
