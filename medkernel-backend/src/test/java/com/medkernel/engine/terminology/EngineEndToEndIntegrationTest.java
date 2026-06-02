package com.medkernel.engine.terminology;

import static org.junit.jupiter.api.Assertions.*;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import com.medkernel.compliance.evidence.dto.EvidenceCreateDto;
import com.medkernel.compliance.evidence.dto.EvidenceResponse;
import com.medkernel.compliance.evidence.dto.EvidenceVerifyResult;
import com.medkernel.compliance.evidence.service.EvidenceService;
import com.medkernel.engine.context.ContextSnapshotService;
import com.medkernel.engine.context.ContextSnapshotRequest;
import com.medkernel.engine.context.ContextSnapshotResources;
import com.medkernel.engine.context.QualityStatus;
import com.medkernel.engine.followup.FollowupEngineService;
import com.medkernel.engine.followup.FollowupPlanGenerateRequest;
import com.medkernel.engine.followup.FollowupPlanDetailResponse;
import com.medkernel.engine.knowledge.KnowledgeIdentityService;
import com.medkernel.engine.knowledge.FragmentCreateRequest;
import com.medkernel.engine.knowledge.SourceFragment;
import com.medkernel.engine.llm.ModelGatewayService;
import com.medkernel.engine.llm.ModelTaskRequest;
import com.medkernel.engine.llm.ModelTaskResponse;
import com.medkernel.engine.recommendation.*;
import com.medkernel.shared.api.error.ApiException;
import com.medkernel.shared.context.OrgScope;
import com.medkernel.shared.context.RequestContext;

/**
 * MedKernel 顶级引擎全能力端到端集成验证测试（E2E）。
 *
 * <p>本类设定于 com.medkernel.engine.terminology 包内，以访问术语模块包私有枚举（TerminologyEnums）。
 * 覆盖知识去重、确定性语义术语映射、诊断决策 CDSS 双向反馈、时序随访分发、网关安全自愈降级以及合规证据对账验签的全生命周期。
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class EngineEndToEndIntegrationTest {

    @Autowired
    private KnowledgeIdentityService knowledgeService;

    @Autowired
    private TerminologyService terminologyService;

    @Autowired
    private ContextSnapshotService contextService;

    @Autowired
    private RecommendationEngineService recommendationService;

    @Autowired
    private FollowupEngineService followupService;

    @Autowired
    private ModelGatewayService modelGatewayService;

    @Autowired
    private EvidenceService evidenceService;

    @Autowired
    private com.medkernel.engine.knowledge.SourceDocumentRepository docRepo;

    @Autowired
    private com.medkernel.engine.knowledge.SourceVersionRepository verRepo;

    @Autowired
    private StandardTermRepository standardTermRepo;

    @Autowired
    private LocalTermRepository localTermRepo;

    private final String tenantId = "tenant-hospital-01";
    private final String doctorId = "DOC-STROKE-101";
    private final String traceId = "tr-e2e-stroke-999";

    @BeforeEach
    void setUp() {
        // 初始化当前线程的强多租户及角色动作授权上下文 (GA-ENG-BASE-01/02)
        RequestContext.restore(new RequestContext.Snapshot(traceId, OrgScope.tenant(tenantId), doctorId));
    }

    @Test
    void runFullEnginePhysicalWorkflow() {
        System.out.println("====== [1. 知识指南片段 SHA-256 去重注册] ======");
        String payload = "【卒中规范溶栓指南】对于急性缺血性卒中，溶栓前收缩压应控制在 < 185 mmHg 且舒张压 < 110 mmHg。";
        
        // 建立知识文档及版本数据环境
        var doc = docRepo.save(new com.medkernel.engine.knowledge.SourceDocument(
            null, tenantId, "DOC-STROKE-101",
            com.medkernel.engine.knowledge.SourceType.GUIDELINE,
            com.medkernel.engine.knowledge.SourceAuthorityLevel.B_GUIDELINE,
            "国家级指南发布机构与版本号可追溯",
            "急性缺血性脑卒中规范化溶栓指南 (2025版)", "卫健委", "None", "zh-CN",
            Instant.now(), "system", Instant.now(), "system"
        ));
        var ver = verRepo.save(new com.medkernel.engine.knowledge.SourceVersion(
            null, tenantId, doc.id(), "v1.0", Instant.now(), sha256(payload), "http://docs/stroke-v1.pdf", "zh-CN",
            Instant.now(), "system"
        ));

        FragmentCreateRequest fragmentReq = new FragmentCreateRequest(
            ver.id(), "sec-4.2", "溶栓血压禁忌条文", payload
        );
        SourceFragment fragment = knowledgeService.createFragment(fragmentReq);
        
        assertNotNull(fragment.id(), "知识片段 ID 非空");
        assertNotNull(fragment.contentHash(), "内容哈希已真实算得");
        assertEquals(64, fragment.contentHash().length(), "哈希符合 SHA-256 64位十六进制编码规格");

        // 验证排重阻断：相同片段内容第二次插入在不同锚点下触发哈希冲突防线
        assertThrows(ApiException.class, () -> {
            knowledgeService.createFragment(new FragmentCreateRequest(
                ver.id(), "sec-4.2-duplicate", "冲突条文", payload
            ));
        }, "相同数据重复录入触发哈希冲突防线");


        System.out.println("====== [2. 临床字典术语确定性语义别名映射] ======");
        // 建立测试字典环境，使用已打通的包私有枚举
        StandardTerm standard = standardTermRepo.save(new StandardTerm(
            null, tenantId, "ICD-10", "I63.900", TermCategory.DIAGNOSIS, "脑梗死", "naogengsi|卒中脑梗",
            "2025", StandardTermStatus.ACTIVE, null, "诊断依据", Instant.now(), "system", Instant.now(), "system"
        ));
        LocalTerm local = localTermRepo.save(new LocalTerm(
            null, tenantId, "HIS", "loc-stroke-99", TermCategory.DIAGNOSIS, "卒中脑梗", "卒中脑梗",
            "DEPT-01", LocalTermStatus.UNMAPPED, Instant.now(), Instant.now(), Instant.now(), "system", Instant.now(), "system"
        ));

        // 触发确定性候选生成：标准字典 normalized_name 提供真实别名，禁止靠字符相似度误配。
        TerminologyCandidateGenerationResponse generation = terminologyService.generateCandidates(new TerminologyCandidateGenerationRequest(
            "req-e2e-term-001", "tr-e2e-stroke-999", tenantId, "GROUP-1", "HOSP-1", "CAMPUS-1",
            "SITE-1", "DEPT-01", "NEURO", "DOC-STROKE-101", List.of("specialist"), "pkg-stroke-2026",
            "HIS", null
        ));
        assertEquals(1, generation.generatedCount(), "字典规则引擎发现了 1 个候选映射");

        // 查到候选并执行人工确认
        var candidatesPage = terminologyService.pageCandidates(
            new com.medkernel.shared.api.PageRequest(1, 10, null),
            new CandidateFilter(MappingCandidateStatus.PENDING, null, null)
        );
        assertEquals(1, candidatesPage.total());
        var candidate = candidatesPage.items().get(0);
        
        // 专家人工确认推荐映射
        TermMapping mapping = terminologyService.confirmCandidate(
            candidate.id(),
            new TerminologyCandidateConfirmRequest(
                "req-e2e-term-confirm", "tr-e2e-stroke-999", tenantId, "GROUP-1", "HOSP-1", "CAMPUS-1",
                "SITE-1", "DEPT-01", "NEURO", "DOC-STROKE-101", List.of("specialist"), "pkg-stroke-2026",
                "专家组最终确认", "医生人工确认", true, "已核对诊断编码和院内词条"
            )
        );
        assertNotNull(mapping.id(), "已成功建立映射关系");
        assertEquals("CONFIRMED", mapping.status().name());


        System.out.println("====== [3. 患者就诊急诊事件 ContextSnapshot 同步] ======");
        // 同步接收急诊疑似脑梗患者李建国（血压 185/105 mmHg）的主诉与检验快照
        String patientPayload = "{\"patientName\":\"李建国\",\"systolicBP\":185,\"diastolicBP\":105,\"diagnosis\":\"脑卒中\"}";
        ContextSnapshotRequest contextReq = new ContextSnapshotRequest(
            "PAT-777", "enc-stroke-888", "ORG-1",
            "kpv-1", "rpv-1", "ppv-1",
            new ContextSnapshotResources(
                new com.medkernel.engine.context.canonical.CanonicalPatient(
                    "PAT-777", "李建国", java.time.LocalDate.of(1958, 5, 12), "M",
                    List.of(), List.of(), "HIS", "pat-rec-id", "v1.0", Instant.now(), Instant.now(), QualityStatus.VALID
                ),
                List.of(
                    new com.medkernel.engine.context.canonical.CanonicalEncounter(
                        "enc-stroke-888", "EMERGENCY", Instant.now(), null,
                        "DEPT-01", doctorId, null, "HIS", "enc-rec-id", "v1.0", Instant.now(), Instant.now(), QualityStatus.VALID
                    )
                ),
                List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of()
            )
        );
        
        // 存入就诊上下文
        var snapshotResult = contextService.create(contextReq, "idempotency-key-stroke-888");
        assertNotNull(snapshotResult.snapshotId(), "就诊快照同步保存成功");


        System.out.println("====== [4. 规则引擎计算与 CDSS 溶栓禁忌决策卡触达] ======");
        // 同步触发 CDSS 提醒生成（高风险红线卡片强制医师确认，且至少携带一条来源文献）
        List<RecommendationSourceRequest> recSources = List.of(
            new RecommendationSourceRequest(
                RecommendationSourceType.KNOWLEDGE,
                "guideline-stroke-v3", "v1.0",
                "脑卒中溶栓临床指南", "§4.2", fragment.contentHash(), "脑卒中溶栓前收缩压控制在 < 185 mmHg"
            )
        );
        List<RecommendationCardRequest> recCards = List.of(
            new RecommendationCardRequest(
                "CARD-STROKE-BLOCK",
                RecommendationCardType.RISK,
                "溶栓前高血压禁忌警报",
                "患者当前收缩压 185 mmHg 已达溶栓高风险红线",
                "立即控制血压或暂停溶栓",
                RecommendationRiskLevel.CRITICAL,
                RecommendationInterruptLevel.STRONG_INTERRUPTIVE,
                true, // requiresPhysicianConfirmation
                true, // aiGenerated
                "脑卒中溶栓临床指南",
                "{}", "fatigue-stroke-pressure", null,
                recSources
            )
        );
        RecommendationTriggerRequest triggerReq = new RecommendationTriggerRequest(
            "TR-STROKE-101", "CDSS", "evt-id-123", snapshotResult.snapshotId(),
            "PAT-777", "enc-stroke-888", "pathway-99", "EMERGENCY", "v1.0",
            "input-digest-abc", Instant.now(), recCards
        );

        RecommendationTriggerResponse triggerResp = recommendationService.trigger(triggerReq);
        assertNotNull(triggerResp.triggerId());
        assertEquals("EVALUATED", triggerResp.status().name());

        // 提取生成的推荐卡 ID
        var cardsPage = recommendationService.listCards(
            new RecommendationCardFilter(null, null, "EMERGENCY", "PAT-777"),
            new com.medkernel.shared.api.PageRequest(1, 10, null)
        );
        assertEquals(1, cardsPage.total());
        String cardId = cardsPage.items().get(0).cardId();

        // 模拟医师在临床端进行双向反馈（医师拒绝该卡片，反馈闭环）
        var feedbackReq = new RecommendationFeedbackRequest(
            RecommendationFeedbackType.REJECT,
            "REFUSE_DRUG", "由于患者存在脑溢血极端风险，暂停阿替普酶溶栓", "DOCTOR"
        );
        var feedbackResp = recommendationService.feedback(cardId, feedbackReq);
        assertNotNull(feedbackResp.feedbackId());
        assertEquals("REJECTED", feedbackResp.cardStatus().name());


        System.out.println("====== [5. 出院事件触发时序随访问卷计划生成] ======");
        // 出院事件，系统自动根据模板，为脑卒中患者分发 30 天时序随访任务
        FollowupPlanGenerateRequest followupReq = new FollowupPlanGenerateRequest(
            "PAT-777", "enc-stroke-888", "pathway-99", "I63.900", "HIGH", List.of("QUESTIONNAIRE", "EXAM")
        );
        FollowupPlanDetailResponse followupResp = followupService.generatePlan(followupReq);
        assertNotNull(followupResp.planId());
        assertEquals("ACTIVE", followupResp.status().name());
        assertFalse(followupResp.tasks().isEmpty(), "时序随访第一期任务与问卷分发就绪");


        System.out.println("====== [6. 大模型网关安全自愈降级 (B0 主链路验收)] ======");
        // 验证大模型能力网关在未接入真实 provider 时，诚实降级到 B0 确定性基线，
        // 且绝不伪造 B1/B2 模型名、置信度、来源引文或患者数据（宪法 #9/#13）。
        ModelTaskRequest gateReq = new ModelTaskRequest(
            "knowledge.extract", "急性卒中用药指征结构化抽取",
            "DEFAULT", "required: [entity]", 60
        );

        ModelTaskResponse gateResp = modelGatewayService.submitTask(gateReq);
        assertNotNull(gateResp.taskId());
        assertEquals("DEGRADED", gateResp.status(), "未接入 provider 时任务状态为 DEGRADED 降级运行");
        assertEquals("B0", gateResp.modelMode(), "诚实退回到 B0 无模型基线");
        assertTrue(gateResp.fallbackUsed(), "标记使用了 fallback 降级");
        assertTrue(gateResp.fallbackReason().contains("B0 确定性基线"), "B0 基线归因被诚实记录");
        assertNull(gateResp.confidence(), "B0 基线不得伪造置信度");
        assertEquals("[]", gateResp.sourceCitations(), "B0 基线不得伪造来源引文");


        System.out.println("====== [7. 合规证据快照打包、验签与防篡改对账] ======");
        // 合规证据打包，对上述全流程产生的数据快照打包为 ZIP 安全压缩证据包
        String evidenceId = "evd-e2e-stroke-888";
        EvidenceCreateDto evidenceDto = new EvidenceCreateDto(
            evidenceId, traceId, "CDSS_DECISION", "EXECUTE",
            "encounter", "enc-stroke-888",
            "脑卒中急诊临床溶栓决策及医师双向反馈证据快照", patientPayload
        );
        
        EvidenceResponse evidenceResp = evidenceService.createSnapshot(tenantId, evidenceDto);
        assertNotNull(evidenceResp.evidenceId(), "证据快照创建入库成功");
        assertNotNull(evidenceResp.payloadHash(), "快照自动计算了真实的 SHA-256 防伪签名");
        
        // 对账验签校验：验证数据未被篡改
        EvidenceVerifyResult verifyResult = evidenceService.verifyEvidence(tenantId, evidenceId);
        assertTrue(verifyResult.isValid(), "证据快照双向防伪哈希对账验签成功");
        assertEquals(verifyResult.storedHash(), verifyResult.calculatedHash(), "哈希对账一致");

        System.out.println("====== 🎉 [MedKernel v1.0 GA 顶级引擎全链路 E2E 验证通过！] ======");
    }

    private String sha256(String text) {
        try {
            var digest = java.security.MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(text.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder();
            for (byte b : hash) {
                String part = Integer.toHexString(0xff & b);
                if (part.length() == 1) {
                    hex.append('0');
                }
                hex.append(part);
            }
            return hex.toString();
        } catch (Exception ex) {
            throw new IllegalStateException("无法计算测试来源正文 SHA-256", ex);
        }
    }
}
