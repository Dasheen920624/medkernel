package com.medkernel.engine.knowledge.delivery;

/**
 * 事务物化端口。
 *
 * <p>适配器只在完整解码和校验后提交纯稳定身份文档；空库关系型写入和全有或全无事务由导入实现提供。
 */
@FunctionalInterface
public interface PortableAssetMaterializationTarget {

    /** 接收一个已经完成类型和自包含校验的资产文档。 */
    void materialize(PortableAssetDocument document);
}
