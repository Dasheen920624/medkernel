package com.medkernel.shared.export;

/**
 * 异步导出产物来源（导出审批「登记完成」环节的可信产物提供方）。
 *
 * <p>每个导出来源（大列表引擎、引擎数据服务层等）实现本接口，按其负责的资源类型对外提供已完成产物。
 * 导出审批服务按审批申请的资源类型解析唯一来源，不直连各来源实现、不耦合具体导出子系统。
 * 接口置于 shared 层，依赖方向恒为引擎包实现 shared 接口、业务包依赖 shared 接口（SYS-02）。
 */
public interface ExportArtifactProvider {

    /**
     * 是否负责给定资源类型（资源类型按导出审批的规范化形式传入，小写下划线）。
     */
    boolean supports(String resourceType);

    /**
     * 返回已成功完成的导出产物（含按真实文件计算的 SM3 摘要）。任务未成功或文件缺失时抛结构化异常。
     */
    ExportArtifact completedExportArtifact(String jobId);
}
