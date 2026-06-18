# P6 独立验收授权门 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 让 P6 独立验收只能由内置超级管理员在系统配置空间放行，同时保留 MFA、二次确认、审计与快速关闭能力。

**Architecture:** 在共享配置层定义特权授权端口，由安全域基于 Spring Security authority 实现；`SystemConfigService` 对 P6 的更新、租户覆盖和回滚统一强制约束。前端依据安全画像禁用无权或无效的 P6 编辑入口。

**Tech Stack:** Java 21、Spring Boot、Spring Security、JUnit 5、Mockito、React、TypeScript、Vitest、Testing Library。

---

### Task 1: 后端 P6 特权授权红测

**Files:**
- Modify: `medkernel-backend/src/test/java/com/medkernel/shared/config/SystemConfigServiceTest.java`
- Modify: `medkernel-backend/src/test/java/com/medkernel/shared/config/SystemConfigControllerTest.java`

- [ ] **Step 1: 写服务层失败测试**

新增测试，构造 P6 高危配置并断言：

```java
assertThatThrownBy(() -> service.update(
    SystemConfigService.KNOWLEDGE_PRODUCTION_P6_INDEPENDENT_ACCEPTANCE_KEY,
    new SystemConfigUpdateRequest("true", "完成 P6 独立验收", 1L, true),
    "operator"))
    .isInstanceOf(ApiException.class);
verify(repository, never()).updateValue(anyString(), anyString(), anyString(), anyString(), anyString(), any());
```

同时覆盖系统超管开启、普通运维关闭、租户覆盖拒绝和回滚到 `true`。

- [ ] **Step 2: 写控制器集成失败测试**

使用已绑定 MFA 的集成运维 JWT 调用 P6 更新，期望 HTTP 403；使用已绑定 MFA 的系统超管 JWT 调用相同请求，期望 HTTP 200。

- [ ] **Step 3: 运行红测**

Run:

```bash
cd medkernel-backend
./mvnw -Dtest=SystemConfigServiceTest,SystemConfigControllerTest test
```

Expected: 新增 P6 授权测试失败，证明现有通用 `system.manage` 可提前写入。

### Task 2: 实现共享授权端口与配置服务约束

**Files:**
- Create: `medkernel-backend/src/main/java/com/medkernel/shared/config/PrivilegedConfigChangeGuard.java`
- Create: `medkernel-backend/src/main/java/com/medkernel/engine/security/SpringSecurityPrivilegedConfigChangeGuard.java`
- Modify: `medkernel-backend/src/main/java/com/medkernel/shared/config/SystemConfigService.java`
- Modify: `medkernel-backend/src/test/java/com/medkernel/shared/config/SystemConfigServiceTest.java`

- [ ] **Step 1: 定义最小授权端口**

```java
public interface PrivilegedConfigChangeGuard {
    void assertAuthorityAllowed(String resourceType, String resourceId, String requiredAuthority);
}
```

- [ ] **Step 2: 实现已验签 authority 校验**

```java
@Component
public class SpringSecurityPrivilegedConfigChangeGuard implements PrivilegedConfigChangeGuard {
    @Override
    public void assertAuthorityAllowed(String resourceType, String resourceId, String requiredAuthority) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        boolean allowed = authentication != null
            && authentication.isAuthenticated()
            && authentication.getAuthorities().stream()
                .anyMatch(authority -> requiredAuthority.equals(authority.getAuthority()));
        if (!allowed) {
            throw new ApiException(ErrorCode.FORBIDDEN, "P6 独立验收仅允许内置超级管理员放行");
        }
    }
}
```

- [ ] **Step 3: 在配置服务统一调用**

在 repository 写入前调用：

```java
assertP6AcceptanceChangeAllowed(normalizedTenantId, before, value);
```

规则为：非 `SYSTEM` 直接 `VALIDATION_FAILED`；只有目标值为 `true` 且当前值不是 `true` 时要求 `ROLE_SYSTEM_SUPERADMIN`。rollback 在目标值校验后复用同一方法。

- [ ] **Step 4: 运行后端定向测试**

Run:

```bash
cd medkernel-backend
./mvnw -Dtest=SystemConfigServiceTest,SystemConfigControllerTest test
```

Expected: PASS。

### Task 3: 前端诚实呈现 P6 操作权限

**Files:**
- Modify: `frontend/src/pages/compliance/SecurityBaseline.tsx`
- Modify: `frontend/src/pages/compliance/SecurityBaselinePanels.tsx`
- Modify: `frontend/src/pages/compliance/SecurityBaseline.test.tsx`

- [ ] **Step 1: 写前端失败测试**

在配置夹具中加入 P6 项，断言平台治理管理员看到“仅内置超管放行”且编辑按钮禁用；将安全画像角色改为 `system-superadmin` 后断言按钮可用；切换租户覆盖后始终禁用。

- [ ] **Step 2: 传递 P6 放行能力**

```tsx
const canReleaseP6 = profile.roles.some((role) => role.code === "system-superadmin");
<SystemConfigPanel canManage={canManage} canReleaseP6={canReleaseP6} />
```

- [ ] **Step 3: 收紧按钮状态并展示说明**

P6 未放行时，非超管禁用系统级编辑；租户覆盖始终禁用。P6 已放行时允许持有 `system.manage` 的用户进入关闭流程。

- [ ] **Step 4: 运行前端定向测试**

Run:

```bash
cd frontend
npm test -- --run src/pages/compliance/SecurityBaseline.test.tsx
```

Expected: PASS。

### Task 4: 文档、全量验证与本地提交

**Files:**
- Modify: `docs/cards/wave2/AIK-STD-13.md`
- Modify: `docs/superpowers/plans/2026-06-16-autonomous-knowledge-production-golive-master-plan.md`
- Modify: `docs/_HANDOFF.md`

- [ ] **Step 1: 同步事实**

记录 P6 放行只允许内置超管、禁止租户伪覆盖、关闭仍可安全降级；保持 T9.3、T9.6、T9.8 未完成，不冒领专家签署或正式生产放行。

- [ ] **Step 2: 运行全量验证**

Run:

```bash
cd medkernel-backend && ./mvnw test
cd ../frontend && npm test -- --run && npm run build
cd .. && node scripts/authenticity-guard.mjs --mode changed
```

Expected: 后端、前端、构建与真实性门禁全部通过。

- [ ] **Step 3: 自审差异**

Run:

```bash
git diff --check
git diff --stat
git status --short
```

Expected: 无空白错误，仅包含本切片文件。

- [ ] **Step 4: 本地提交**

```bash
git add medkernel-backend frontend docs
git commit -m "fix: 收紧P6独立验收放行权限"
```

Expected: 提交成功，不推送远程。
