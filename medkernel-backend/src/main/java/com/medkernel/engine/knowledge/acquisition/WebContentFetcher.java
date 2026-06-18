package com.medkernel.engine.knowledge.acquisition;

import java.net.URI;

/**
 * 公域资料抓取端口。白名单、许可、robots 和部署形态门禁由编排服务在调用前完成。
 */
public interface WebContentFetcher {

    FetchedWebContent fetch(URI uri);
}
