package com.medkernel.engine.knowledge.parsing;

/**
 * 解析失败诚实异常（AIK-STD-02 FR-5）。无法解析（空文档/损坏/结构不可识别）时抛出，
 * 编排层据此把 job 记为 FAILED 并诚实记录原因，绝不产伪结构（铁律 #1）。
 */
public class DocumentParseException extends RuntimeException {

    public DocumentParseException(String message) {
        super(message);
    }
}
