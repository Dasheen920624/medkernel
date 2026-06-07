package com.medkernel.engine.knowledge.diagnosis;

import com.medkernel.engine.knowledge.Citation;
import com.medkernel.engine.knowledge.KnowledgeAssetVersion;
import com.medkernel.engine.knowledge.KnowledgeIdentity;
import com.medkernel.engine.knowledge.SourceDocument;
import com.medkernel.engine.knowledge.SourceFragment;
import com.medkernel.engine.knowledge.SourceVersion;

/**
 * 诊断知识建档结果：返回后续编辑所需版本标识和完整来源证据链。
 */
public record DiagnosisAssetDraftResponse(
    KnowledgeIdentity identity,
    KnowledgeAssetVersion version,
    SourceDocument source,
    SourceVersion sourceVersion,
    SourceFragment fragment,
    Citation citation
) {}
