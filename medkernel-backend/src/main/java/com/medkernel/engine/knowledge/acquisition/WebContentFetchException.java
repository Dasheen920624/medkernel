package com.medkernel.engine.knowledge.acquisition;

/**
 * 公域资料抓取失败。失败原因会进入获取运行账本。
 */
public class WebContentFetchException extends RuntimeException {

    public WebContentFetchException(String message) {
        super(message);
    }

    public WebContentFetchException(String message, Throwable cause) {
        super(message, cause);
    }
}
