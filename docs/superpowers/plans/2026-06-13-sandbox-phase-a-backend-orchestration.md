# 全真体验沙盘 · 阶段A（后端编排）实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 新增 `POST /api/v1/engine/sandbox/scenarios/{scenarioId}/run` 编排端点（权限 `sandbox.run`），进程内顺序调既有引擎服务（上下文快照 → 推荐触发 → 嵌入 token），把已发布的"高钾危急值"场景端到端跑通并返回完整路径轨迹。

**Architecture:** 新增 `com.medkernel.engine.sandbox` 包。`SandboxScenarioController`（`@PreAuthorize("@perm.has('sandbox.run')")`）→ `SandboxOrchestrationService` 在进程内**直接调既有服务方法**（`ContextSnapshotService.create` / `RecommendationEngineService.trigger` / `EmbedEngineService.generateToken`），不经各自 HTTP 控制器、不重复其 `@PreAuthorize`；逐步聚合 `SandboxStepTrace` 返回 `SandboxRunResponse`。场景定义本阶段内置于 `SandboxScenarioCatalog`（仅 `#1` 高钾），后续阶段扩展为注册表。

**Tech Stack:** Spring Boot / Java 21 / JUnit 5 + Mockito / Maven；权限经 `@perm.has` + `DefaultPermissionPolicy`；DTO 用 Java record；引擎契约见 spec `docs/superpowers/specs/2026-06-13-medkernel-fulltruth-sandbox-design.md` §16–18、§23。

**前置依赖：** 阶段A 单测/契约测试不依赖真实规则；端到端验收需目标库已发布 `P5.ACT4.CRITICAL.K`（134 已有；本地需先经治理 seed）。**先读 spec §23 实现者踩坑预警。**

---

## 文件结构

| 文件 | 职责 |
|---|---|
| Create `…/engine/sandbox/SandboxStepTrace.java` | 单步轨迹记录（stage/endpoint/request/response/serverFacts/status/error） |
| Create `…/engine/sandbox/SandboxRunRequest.java` | 编排入参（entryMode + 可选 contextOverride/occurredAt） |
| Create `…/engine/sandbox/SandboxRunResponse.java` | 编排结果（traceId + steps + 聚合标识 + embedToken/embedUrl） |
| Create `…/engine/sandbox/SandboxScenario.java` | 内置场景定义（id/triggerPoint/patientId/encounterId/ruleCode/动作/严重度/上下文构造） |
| Create `…/engine/sandbox/SandboxScenarioCatalog.java` | 场景目录（阶段A 仅 `#1`），按 id 取场景、构造三步请求 |
| Create `…/engine/sandbox/SandboxOrchestrationService.java` | 顺序编排三步 + 轨迹聚合 |
| Create `…/engine/sandbox/SandboxScenarioController.java` | REST 端点 + `@PreAuthorize('sandbox.run')` |
| Modify `…/engine/security/PermissionCode.java` | 新增 `SANDBOX_RUN` |
| Modify `…/engine/security/DefaultPermissionPolicy.java` | 给沙盘角色授 `sandbox.run` + 菜单 `sandbox`，确保具 `embed.read`/`recommendation.read`/`recommendation.accept` |
| Modify `…/engine/contract/ServiceContractCatalog.java` | 登记 `/engine/sandbox/scenarios/{id}/run` 契约 |
| Test `…/engine/sandbox/SandboxOrchestrationServiceTest.java` | mock 三服务，断言调用序列 + 轨迹聚合 + 失败短路 |
| Test `…/engine/sandbox/SandboxScenarioControllerSecurityTest.java` | 无 `sandbox.run`→403；有→200 |
| Test `…/engine/sandbox/SandboxScenarioApiContractTest.java` | 端点契约与 `ServiceContractCatalog` 一致 |
| Modify Test `…/engine/security/DefaultPermissionPolicyTest.java` + `PermissionDimensionModelTest.java` | 同步两处权限/菜单快照断言（spec §23.6） |

（包根 `medkernel-backend/src/main/java/com/medkernel`，测试根 `medkernel-backend/src/test/java/com/medkernel`。）

---

## Task 1: 新增 `sandbox.run` 权限码

**Files:** Modify `…/engine/security/PermissionCode.java`

- [ ] **Step 1: 写失败测试**　Test `…/engine/security/PermissionCodeTest.java`（无则新建）

```java
@Test
void sandboxRunPermissionIsRegistered() {
    PermissionCode code = PermissionCode.valueOf("SANDBOX_RUN");
    assertThat(code.code()).isEqualTo("sandbox.run");
    assertThat(code.risk()).isEqualTo(PermissionCode.Risk.MEDIUM);
}
```

- [ ] **Step 2: 跑测试确认失败**　Run: `mvn -pl medkernel-backend -Dtest=PermissionCodeTest#sandboxRunPermissionIsRegistered test`　Expected: FAIL（`SANDBOX_RUN` 不存在）。

- [ ] **Step 3: 加枚举值**　在 `PermissionCode.java` 既有 `EMBED_WRITE("embed.write", Risk.MEDIUM, "...")` 附近加：

```java
SANDBOX_RUN("sandbox.run", Risk.MEDIUM, "运行全真体验沙盘场景编排"),
```

- [ ] **Step 4: 跑测试确认通过**　Run 同 Step 2　Expected: PASS。

- [ ] **Step 5: 提交**

```bash
git add medkernel-backend/src/main/java/com/medkernel/engine/security/PermissionCode.java medkernel-backend/src/test/java/com/medkernel/engine/security/PermissionCodeTest.java
git commit -m "feat(sandbox): 新增 sandbox.run 权限码"
```

---

## Task 2: 编排 DTO（StepTrace / RunRequest / RunResponse）

**Files:** Create `SandboxStepTrace.java`、`SandboxRunRequest.java`、`SandboxRunResponse.java`（均 `package com.medkernel.engine.sandbox;`）

- [ ] **Step 1: 写 `SandboxStepTrace`**

```java
package com.medkernel.engine.sandbox;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.Map;

/** 编排单步轨迹：阶段、端点、原始请求/响应、服务端事实、状态。 */
public record SandboxStepTrace(
    String stage,        // CONTEXT | RECOMMENDATION | TOKEN
    String endpoint,
    JsonNode request,
    JsonNode response,
    Map<String, Object> serverFacts,
    String status,       // OK | FAIL
    String error
) {
    public static SandboxStepTrace ok(String stage, String endpoint, JsonNode req, JsonNode resp, Map<String, Object> facts) {
        return new SandboxStepTrace(stage, endpoint, req, resp, facts, "OK", null);
    }
    public static SandboxStepTrace fail(String stage, String endpoint, JsonNode req, String error) {
        return new SandboxStepTrace(stage, endpoint, req, null, Map.of(), "FAIL", error);
    }
}
```

- [ ] **Step 2: 写 `SandboxRunRequest`**

```java
package com.medkernel.engine.sandbox;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.Instant;

/** 沙盘运行入参：entryMode 决定数据入口；contextOverride 为可选自定义录入资源；为空用场景预置。 */
public record SandboxRunRequest(
    String entryMode,        // SNAPSHOT（阶段A 仅此）| EVENT | ADAPTER（后续）
    JsonNode contextOverride,
    Instant occurredAt
) {
    public SandboxRunRequest {
        entryMode = (entryMode == null || entryMode.isBlank()) ? "SNAPSHOT" : entryMode;
    }
}
```

- [ ] **Step 3: 写 `SandboxRunResponse`**

```java
package com.medkernel.engine.sandbox;

import java.util.List;

/** 沙盘运行结果：路径轨迹 + 聚合标识 + 嵌入入口。 */
public record SandboxRunResponse(
    String scenarioId,
    String traceId,
    List<SandboxStepTrace> steps,
    String snapshotId,
    String triggerId,
    int cardCount,
    String embedToken,
    String embedUrl,
    String result          // PASS | FAIL
) {}
```

- [ ] **Step 4: 编译确认**　Run: `mvn -pl medkernel-backend -DskipTests compile`　Expected: BUILD SUCCESS。

- [ ] **Step 5: 提交**

```bash
git add medkernel-backend/src/main/java/com/medkernel/engine/sandbox/Sandbox*.java
git commit -m "feat(sandbox): 编排 DTO StepTrace/RunRequest/RunResponse"
```

---

## Task 3: 场景目录（内置高钾场景 #1）

**Files:** Create `SandboxScenario.java`、`SandboxScenarioCatalog.java`

- [ ] **Step 1: 写失败测试**　Test `…/engine/sandbox/SandboxScenarioCatalogTest.java`

```java
package com.medkernel.engine.sandbox;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import org.junit.jupiter.api.Test;

class SandboxScenarioCatalogTest {
    private final SandboxScenarioCatalog catalog = new SandboxScenarioCatalog();

    @Test
    void resolvesCriticalPotassiumScenario() {
        SandboxScenario s = catalog.require("sbx-lab-critical-k");
        assertThat(s.triggerPoint()).isEqualTo("result-review");
        assertThat(s.expectedRuleCode()).isEqualTo("P5.ACT4.CRITICAL.K");
        assertThat(s.patientId()).isNotBlank();
    }

    @Test
    void unknownScenarioThrows() {
        assertThatThrownBy(() -> catalog.require("nope"))
            .isInstanceOf(IllegalArgumentException.class);
    }
}
```

- [ ] **Step 2: 跑测试确认失败**　Run: `mvn -pl medkernel-backend -Dtest=SandboxScenarioCatalogTest test`　Expected: FAIL（类不存在）。

- [ ] **Step 3: 写 `SandboxScenario`**

```java
package com.medkernel.engine.sandbox;

/** 内置沙盘场景定义。阶段A 仅高钾；上下文资源由 catalog 据此构造。 */
public record SandboxScenario(
    String id,
    String servicePackage,   // clinical-run
    String engine,           // rule
    String triggerPoint,     // result-review
    String ruleType,         // LAB
    String title,
    String patientId,
    String encounterId,
    String expectedRuleCode,
    String expectedAction,   // STRONG_REMINDER
    String expectedSeverity, // CRITICAL
    String packageVersion
) {}
```

- [ ] **Step 4: 写 `SandboxScenarioCatalog`**

```java
package com.medkernel.engine.sandbox;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

/** 阶段A 内置场景目录（仅 #1）。后续阶段改为可扩展注册表。 */
@Component
public class SandboxScenarioCatalog {

    private final Map<String, SandboxScenario> byId = new LinkedHashMap<>();

    public SandboxScenarioCatalog() {
        register(new SandboxScenario(
            "sbx-lab-critical-k", "clinical-run", "rule", "result-review", "LAB",
            "血钾危急值红线",
            "SBX-LAB-K-001", "SBX-LAB-K-ENC-001",
            "P5.ACT4.CRITICAL.K", "STRONG_REMINDER", "CRITICAL", "2026.06.1"));
    }

    private void register(SandboxScenario s) { byId.put(s.id(), s); }

    public SandboxScenario require(String id) {
        SandboxScenario s = byId.get(id);
        if (s == null) {
            throw new IllegalArgumentException("未知沙盘场景: " + id);
        }
        return s;
    }

    public List<SandboxScenario> all() { return List.copyOf(byId.values()); }
}
```

- [ ] **Step 5: 跑测试确认通过**　Run 同 Step 2　Expected: PASS。

- [ ] **Step 6: 提交**

```bash
git add medkernel-backend/src/main/java/com/medkernel/engine/sandbox/SandboxScenario*.java medkernel-backend/src/test/java/com/medkernel/engine/sandbox/SandboxScenarioCatalogTest.java
git commit -m "feat(sandbox): 内置高钾场景目录"
```

---

## Task 4: 编排服务（顺序调三引擎 + 轨迹聚合）

**Files:** Create `SandboxOrchestrationService.java`；Test `SandboxOrchestrationServiceTest.java`

> 编排序列见 spec §18：① `ContextSnapshotService.create(req, idempotencyKey)` → snapshotId；② `RecommendationEngineService.trigger(req)` → triggerId/cardCount；③ `EmbedEngineService.generateToken(req)` → token/embedUrl。请求体构造见 spec §16.1/§16.2/§16.5；注意 §23 踩坑（encounterType=`ED`、统一入参信封、`package_version` snake_case）。

- [ ] **Step 1: 写失败测试（成功路径 + 失败短路）**

```java
package com.medkernel.engine.sandbox;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.medkernel.engine.context.ContextSnapshotResponse;
import com.medkernel.engine.context.ContextSnapshotService;
import com.medkernel.engine.context.ContextSnapshotStatus;
import com.medkernel.engine.embed.EmbedEngineService;
import com.medkernel.engine.embed.EmbedLaunchTokenResponse;
import com.medkernel.engine.recommendation.RecommendationEngineService;
import com.medkernel.engine.recommendation.RecommendationTriggerResponse;
import com.medkernel.engine.recommendation.RecommendationTriggerStatus;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

class SandboxOrchestrationServiceTest {
    private final ContextSnapshotService snapshots = mock(ContextSnapshotService.class);
    private final RecommendationEngineService recommendations = mock(RecommendationEngineService.class);
    private final EmbedEngineService embed = mock(EmbedEngineService.class);
    private final SandboxOrchestrationService svc = new SandboxOrchestrationService(
        new SandboxScenarioCatalog(), snapshots, recommendations, embed, new ObjectMapper());

    @Test
    void runHappyPathProducesThreeOkStepsAndAggregates() {
        when(snapshots.create(any(), anyString())).thenReturn(
            new ContextSnapshotResponse("ctx-x", ContextSnapshotStatus.ACTIVE, "2026.06.1", "tr"));
        when(recommendations.trigger(any())).thenReturn(
            new RecommendationTriggerResponse("rt-x", RecommendationTriggerStatus.EVALUATED, 1, "tr"));
        when(embed.generateToken(any())).thenReturn(
            new EmbedLaunchTokenResponse("tok-x", Instant.now().plusSeconds(300), "/embed/launch?token=tok-x"));

        SandboxRunResponse r = svc.run("sbx-lab-critical-k", new SandboxRunRequest("SNAPSHOT", null, Instant.now()));

        assertThat(r.result()).isEqualTo("PASS");
        assertThat(r.steps()).hasSize(3);
        assertThat(r.steps()).allMatch(s -> "OK".equals(s.status()));
        assertThat(r.snapshotId()).isEqualTo("ctx-x");
        assertThat(r.triggerId()).isEqualTo("rt-x");
        assertThat(r.cardCount()).isEqualTo(1);
        assertThat(r.embedToken()).isEqualTo("tok-x");

        InOrder o = inOrder(snapshots, recommendations, embed);
        o.verify(snapshots).create(any(), anyString());
        o.verify(recommendations).trigger(any());
        o.verify(embed).generateToken(any());
    }

    @Test
    void recommendationFailureShortCircuitsAndMarksFail() {
        when(snapshots.create(any(), anyString())).thenReturn(
            new ContextSnapshotResponse("ctx-x", ContextSnapshotStatus.ACTIVE, "2026.06.1", "tr"));
        when(recommendations.trigger(any())).thenThrow(new RuntimeException("boom"));

        SandboxRunResponse r = svc.run("sbx-lab-critical-k", new SandboxRunRequest("SNAPSHOT", null, Instant.now()));

        assertThat(r.result()).isEqualTo("FAIL");
        assertThat(r.steps()).hasSize(2);
        assertThat(r.steps().get(1).status()).isEqualTo("FAIL");
        assertThat(r.steps().get(1).error()).contains("boom");
        verifyNoInteractions(embed);
    }
}
```

> 注：`ContextSnapshotResponse`/`RecommendationTriggerResponse`/`EmbedLaunchTokenResponse` 的构造器形参以各 record 实际定义为准（见 spec §16 / 源码）；若参数个数不符，按源码补齐——**勿改引擎源码**，只调测试里的构造实参。

- [ ] **Step 2: 跑测试确认失败**　Run: `mvn -pl medkernel-backend -Dtest=SandboxOrchestrationServiceTest test`　Expected: FAIL（类不存在）。

- [ ] **Step 3: 写 `SandboxOrchestrationService`**

```java
package com.medkernel.engine.sandbox;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.medkernel.engine.context.ContextSnapshotRequest;
import com.medkernel.engine.context.ContextSnapshotResponse;
import com.medkernel.engine.context.ContextSnapshotService;
import com.medkernel.engine.embed.EmbedEngineService;
import com.medkernel.engine.embed.EmbedLaunchTokenRequest;
import com.medkernel.engine.embed.EmbedLaunchTokenResponse;
import com.medkernel.engine.recommendation.RecommendationEngineService;
import com.medkernel.engine.recommendation.RecommendationTriggerRequest;
import com.medkernel.engine.recommendation.RecommendationTriggerResponse;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;

/** 沙盘编排：进程内顺序调既有引擎服务，聚合路径轨迹。不复制业务逻辑。 */
@Service
public class SandboxOrchestrationService {

    private final SandboxScenarioCatalog catalog;
    private final ContextSnapshotService snapshots;
    private final RecommendationEngineService recommendations;
    private final EmbedEngineService embed;
    private final ObjectMapper json;

    public SandboxOrchestrationService(SandboxScenarioCatalog catalog,
                                       ContextSnapshotService snapshots,
                                       RecommendationEngineService recommendations,
                                       EmbedEngineService embed,
                                       ObjectMapper json) {
        this.catalog = catalog;
        this.snapshots = snapshots;
        this.recommendations = recommendations;
        this.embed = embed;
        this.json = json;
    }

    public SandboxRunResponse run(String scenarioId, SandboxRunRequest request) {
        SandboxScenario s = catalog.require(scenarioId);
        String traceId = "sbx-" + scenarioId + "-" + System.currentTimeMillis();
        List<SandboxStepTrace> steps = new ArrayList<>();

        // ① 上下文快照
        ContextSnapshotRequest snapReq = SandboxRequestFactory.snapshot(s, request, traceId);
        ContextSnapshotResponse snapResp;
        try {
            snapResp = snapshots.create(snapReq, "sbx-snap-" + scenarioId + "-" + UUID.randomUUID());
        } catch (RuntimeException ex) {
            steps.add(SandboxStepTrace.fail("CONTEXT", "/engine/context/snapshots", json.valueToTree(snapReq), ex.getMessage()));
            return fail(scenarioId, traceId, steps);
        }
        steps.add(SandboxStepTrace.ok("CONTEXT", "/engine/context/snapshots",
            json.valueToTree(snapReq), json.valueToTree(snapResp),
            Map.of("snapshotId", snapResp.snapshotId(), "status", String.valueOf(snapResp.status()))));

        // ② 推荐触发
        RecommendationTriggerRequest recReq = SandboxRequestFactory.trigger(s, snapResp.snapshotId(), traceId);
        RecommendationTriggerResponse recResp;
        try {
            recResp = recommendations.trigger(recReq);
        } catch (RuntimeException ex) {
            steps.add(SandboxStepTrace.fail("RECOMMENDATION", "/engine/recommendations/triggers", json.valueToTree(recReq), ex.getMessage()));
            return fail(scenarioId, traceId, steps);
        }
        steps.add(SandboxStepTrace.ok("RECOMMENDATION", "/engine/recommendations/triggers",
            json.valueToTree(recReq), json.valueToTree(recResp),
            Map.of("triggerId", recResp.triggerId(), "cardCount", recResp.cardCount())));

        // ③ 嵌入 token
        EmbedLaunchTokenRequest tokReq = SandboxRequestFactory.launchToken(s);
        EmbedLaunchTokenResponse tokResp;
        try {
            tokResp = embed.generateToken(tokReq);
        } catch (RuntimeException ex) {
            steps.add(SandboxStepTrace.fail("TOKEN", "/engine/embed/launch-tokens", json.valueToTree(tokReq), ex.getMessage()));
            return fail(scenarioId, traceId, steps);
        }
        steps.add(SandboxStepTrace.ok("TOKEN", "/engine/embed/launch-tokens",
            json.valueToTree(tokReq), json.valueToTree(tokResp),
            Map.of("token", tokResp.token())));

        return new SandboxRunResponse(scenarioId, traceId, steps,
            snapResp.snapshotId(), recResp.triggerId(), recResp.cardCount(),
            tokResp.token(), tokResp.embedUrl(), "PASS");
    }

    private SandboxRunResponse fail(String scenarioId, String traceId, List<SandboxStepTrace> steps) {
        return new SandboxRunResponse(scenarioId, traceId, steps, null, null, 0, null, null, "FAIL");
    }
}
```

- [ ] **Step 4: 写 `SandboxRequestFactory`**（同包，构造三步真实请求；信封字段见 §16/§23）

```java
package com.medkernel.engine.sandbox;

import com.medkernel.engine.context.ContextSnapshotRequest;
import com.medkernel.engine.embed.EmbedLaunchTokenRequest;
import com.medkernel.engine.recommendation.RecommendationTriggerRequest;
import com.medkernel.shared.security.RequestContext;   // 取当前登录态 tenantId/userId/roleCodes/orgUnitId
import java.time.Instant;
import java.util.List;

/** 据场景 + 当前登录态构造三步真实引擎请求（信封字段满足各 DTO 校验）。 */
final class SandboxRequestFactory {
    private SandboxRequestFactory() {}

    static ContextSnapshotRequest snapshot(SandboxScenario s, SandboxRunRequest req, String traceId) {
        // 据 spec §16.1：顶层 patientId/encounterId/orgUnitId/package_version + resources。
        // resources 取 req.contextOverride（自定义录入）否则用场景预置构造（高钾 6.8）。
        // 实施时按 ContextSnapshotRequest record 形参顺序构造；resources 用 ContextSnapshotResources + Canonical* records。
        // encounterType 必须 "ED"（§23.1）。tenant/user/role/orgUnit 取 RequestContext.currentOrgScope()。
        throw new UnsupportedOperationException("按 §16.1 + ContextSnapshotResources 源码构造，见下方实施备注");
    }

    static RecommendationTriggerRequest trigger(SandboxScenario s, String snapshotId, String traceId) {
        return new RecommendationTriggerRequest(
            "sbx-" + s.id(), s.triggerPoint(), null, snapshotId,
            s.patientId(), s.encounterId(), null, s.id(), s.packageVersion(),
            null, Instant.now(), List.of());
    }

    static EmbedLaunchTokenRequest launchToken(SandboxScenario s) {
        var scope = RequestContext.currentOrgScope();
        return new EmbedLaunchTokenRequest(
            scope.userId(), primaryRole(scope), s.patientId(), s.encounterId(), s.triggerPoint(), null, null);
    }

    private static String primaryRole(Object scope) { return "clinical-decision-user"; }
}
```

> **实施备注（必读）：** `snapshot(...)` 与 `RequestContext` 取值方式需按真实源码补齐——`RequestContext.currentOrgScope()` 的确切方法名/字段（tenantId/userId/hospitalId/roleCodes）、`ContextSnapshotResources`/`CanonicalPatient`/`CanonicalEncounter`/`CanonicalObservation` 的构造器形参顺序、`RecommendationTriggerRequest`/`EmbedLaunchTokenRequest` 的精确形参，均以源码为准（spec §16 已列字段，构造器参数个数可能与示例略有出入，按编译器报错补齐）。高钾观察值：code `2823-3`、valueNumeric `6.8`、unit `mmol/L`、referenceRange `3.5-5.5`、criticalFlag `HIGH`、qualityStatus `VALID`。

- [ ] **Step 5: 跑测试确认通过**　Run 同 Step 2　Expected: PASS（两用例）。

- [ ] **Step 6: 提交**

```bash
git add medkernel-backend/src/main/java/com/medkernel/engine/sandbox/SandboxOrchestrationService.java medkernel-backend/src/main/java/com/medkernel/engine/sandbox/SandboxRequestFactory.java medkernel-backend/src/test/java/com/medkernel/engine/sandbox/SandboxOrchestrationServiceTest.java
git commit -m "feat(sandbox): 编排服务顺序调三引擎并聚合轨迹"
```

---

## Task 5: REST 控制器 + 权限守卫

**Files:** Create `SandboxScenarioController.java`；Test `SandboxScenarioControllerSecurityTest.java`

- [ ] **Step 1: 写安全测试**（仿既有 `*ControllerSecurityTest` 套路：`@WebMvcTest` 或 `@SpringBootTest`+`MockMvc`，按本仓库既有控制器安全测试模式照搬）

```java
// 期望：未带 sandbox.run 的主体 POST /api/v1/engine/sandbox/scenarios/sbx-lab-critical-k/run → 403
// 带 sandbox.run 的主体 → 200，body.data.scenarioId == "sbx-lab-critical-k"
// 具体注解/MockBean 与本仓库 EmbedEngineController 的安全测试保持一致（@PreAuthorize + @WithMockUser/自定义权限装配）
```

- [ ] **Step 2: 跑测试确认失败**　Run: `mvn -pl medkernel-backend -Dtest=SandboxScenarioControllerSecurityTest test`　Expected: FAIL。

- [ ] **Step 3: 写控制器**

```java
package com.medkernel.engine.sandbox;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.medkernel.shared.api.ApiResult;
import com.medkernel.shared.datascope.DataScope;

/** 全真体验沙盘场景编排控制器。仅 sandbox.run 门；编排在服务层进程内调既有引擎服务。 */
@RestController
@RequestMapping("/api/v1/engine/sandbox")
@DataScope(requireTenant = true)
public class SandboxScenarioController {

    private final SandboxOrchestrationService service;

    public SandboxScenarioController(SandboxOrchestrationService service) {
        this.service = service;
    }

    @PostMapping("/scenarios/{scenarioId}/run")
    @PreAuthorize("@perm.has('sandbox.run')")
    public ApiResult<SandboxRunResponse> run(@PathVariable String scenarioId,
                                             @RequestBody(required = false) SandboxRunRequest request) {
        SandboxRunRequest req = request == null ? new SandboxRunRequest(null, null, null) : request;
        return ApiResult.ok(service.run(scenarioId, req));
    }
}
```

- [ ] **Step 4: 跑测试确认通过**　Run 同 Step 2　Expected: PASS。

- [ ] **Step 5: 提交**

```bash
git add medkernel-backend/src/main/java/com/medkernel/engine/sandbox/SandboxScenarioController.java medkernel-backend/src/test/java/com/medkernel/engine/sandbox/SandboxScenarioControllerSecurityTest.java
git commit -m "feat(sandbox): 场景编排 REST 端点 + sandbox.run 守卫"
```

---

## Task 6: 权限策略接线（两处断言同步）

**Files:** Modify `DefaultPermissionPolicy.java`；Modify Test `DefaultPermissionPolicyTest.java` + `PermissionDimensionModelTest.java`

> **spec §23.6 铁律**：改 `DefaultPermissionPolicy` 角色权限/菜单白名单，必须同步 `PermissionDimensionModelTest` 与 `DefaultPermissionPolicyTest` 两处断言，发布前跑全量 `mvn test`（非定向类），否则 CI `jdk-matrix-smoke` 全红。

- [ ] **Step 1: 写失败测试**（在两处断言里给沙盘角色加 `sandbox.run` + 菜单 `sandbox`；先确认沙盘角色已含 `embed.read`/`recommendation.read`/`recommendation.accept`，缺则一并加并在断言体现）。先确定"沙盘角色"——推荐复用 `clinical-decision-user`（已可看嵌入终端），或新增专用 `sandbox-operator`。**实施前用一句话与既有角色集核对**；本计划默认授予 `clinical-decision-user`。

```java
// DefaultPermissionPolicyTest：新增/扩展断言
// clinicalDecisionUserCanRunSandbox(): 角色权限集 contains "sandbox.run"
// 角色菜单快照 contains "sandbox"
// PermissionDimensionModelTest：对应同型菜单/权限全集断言同步加入 sandbox.run / menu sandbox
```

- [ ] **Step 2: 跑测试确认失败**　Run: `mvn -pl medkernel-backend -Dtest=DefaultPermissionPolicyTest,PermissionDimensionModelTest test`　Expected: FAIL。

- [ ] **Step 3: 改 `DefaultPermissionPolicy`**　给目标角色权限集加 `PermissionCode.SANDBOX_RUN.code()`、菜单集加 `"sandbox"`（菜单 key 命名与既有 `menu.*` / routes 一致，见前端 Task 对齐）；确认 `embed.read`/`recommendation.read`/`recommendation.accept` 在该角色，缺则补。

- [ ] **Step 4: 跑测试确认通过**　Run 同 Step 2　Expected: PASS。

- [ ] **Step 5: 全量回归**　Run: `mvn -pl medkernel-backend test`　Expected: 全绿（含权限两处断言）。

- [ ] **Step 6: 提交**

```bash
git add medkernel-backend/src/main/java/com/medkernel/engine/security/DefaultPermissionPolicy.java medkernel-backend/src/test/java/com/medkernel/engine/security/DefaultPermissionPolicyTest.java medkernel-backend/src/test/java/com/medkernel/engine/security/PermissionDimensionModelTest.java
git commit -m "feat(sandbox): 授沙盘角色 sandbox.run + sandbox 菜单（两处断言同步）"
```

---

## Task 7: 服务契约目录登记

**Files:** Modify `ServiceContractCatalog.java`；Test `SandboxScenarioApiContractTest.java`

- [ ] **Step 1: 看既有契约登记格式**　Read `…/engine/contract/ServiceContractCatalog.java`，照既有 embed/recommendation 端点条目格式登记 `POST /api/v1/engine/sandbox/scenarios/{scenarioId}/run`（method/path/permission=`sandbox.run`/描述）。

- [ ] **Step 2: 写契约一致性测试**　断言 catalog 含该端点且权限=`sandbox.run`，方法=POST（仿既有 `*ApiContractTest`）。

- [ ] **Step 3: 跑测试确认失败 → 登记 → 通过**　Run: `mvn -pl medkernel-backend -Dtest=SandboxScenarioApiContractTest test`。

- [ ] **Step 4: 提交**

```bash
git add medkernel-backend/src/main/java/com/medkernel/engine/contract/ServiceContractCatalog.java medkernel-backend/src/test/java/com/medkernel/engine/sandbox/SandboxScenarioApiContractTest.java
git commit -m "feat(sandbox): 登记 sandbox 编排端点服务契约"
```

---

## Task 8: 全量验证 + 端到端冒烟（对真引擎）

- [ ] **Step 1: 全量后端测试**　Run: `mvn -pl medkernel-backend test`　Expected: 全绿。

- [ ] **Step 2: 守卫**　Run 本仓库既有 `check-comment-zh`、`authenticity-guard --mode=all`、`config-boundary-guard --mode=inventory`、`git diff --check`。

- [ ] **Step 3: 端到端冒烟（需已发布 `P5.ACT4.CRITICAL.K` 的环境，如 134）**　以具 `sandbox.run` 的账号 `POST /engine/sandbox/scenarios/sbx-lab-critical-k/run`，断言响应 `result=PASS`、3 步 OK、`cardCount>=1`、`embedToken` 非空；服务端回查 `rule_execution_log.hit`/`recommendation_card`/`embed_launch_token` 有真行。冒烟脚本置 `scripts/sandbox/sandbox-phase-a-smoke.mjs`（复用幕6 登录/CSRF/apiPost 基建）。

- [ ] **Step 4: 提交冒烟脚本**

```bash
git add scripts/sandbox/sandbox-phase-a-smoke.mjs
git commit -m "test(sandbox): 阶段A 后端编排端到端冒烟脚本"
```

---

## 阶段A 之后（各自独立计划）
- **A2 前端沙盘页**：`/sandbox` 路由 + `SandboxHost`/`SandboxDataEntry`/`SandboxPathInspector`/`SandboxEmbedFrame` + `useRunSandboxScenario` hook + `routes.ts`/`routes.test.ts`（菜单 key 与 Task 6 对齐）。
- **B 规则全类型内容**：`scripts/sandbox/seed-scenarios.mjs` 经治理 API 发布 #2–#10（spec §20），catalog 扩展登记。
- **C 外圈引擎**：推荐综合卡/路径/随访/质控/嵌入三模式（spec §7 #11-15）。
- **D 打磨**：演示叙事、截图证据、跨域真宿主验证评估。

## 自审记录
- **spec 覆盖**：本计划覆盖 spec §4 后端组件、§5/§18 SNAPSHOT 编排、§11 权限（sandbox.run + 两处断言）、§13 后端测试、§16 契约、§22 阶段A 验收。前端 §6/§9/§10 与内容 §7/§20 由 A2/B 计划承接（已注明）。
- **占位扫描**：`SandboxRequestFactory.snapshot(...)` 与 `RequestContext` 取值标注"按源码补齐"——因 `ContextSnapshotResources`/`Canonical*` 构造器形参顺序与 `RequestContext` API 需读源码确认，已在实施备注给出确切字段值（高钾 6.8 等）与约束（encounterType=ED、信封字段），非逻辑占位；Task 4 Step 4 要求按编译器补齐构造实参。
- **类型一致性**：`SandboxScenario`/`SandboxStepTrace`/`SandboxRunRequest`/`SandboxRunResponse` 字段在 Task 2/3/4/5 间一致；`run(scenarioId, SandboxRunRequest)` 签名在服务与控制器一致。
