package com.medkernel.engine.integration.masterdata;

import org.springframework.security.core.Authentication;

/**
 * 院内人员主数据归属域端口。
 */
public interface MasterDataPersonnelPort {

    String upsert(MasterDataPersonCommand command, Authentication authentication);

    void disable(String internalId, Authentication authentication);
}
