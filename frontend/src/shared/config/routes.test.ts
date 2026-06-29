import { readFileSync } from "node:fs";
import { resolve } from "node:path";
import { describe, expect, it } from "vitest";
import {
  canAccessRoute,
  findRouteByPath,
  getRouteBreadcrumb,
  routeMetas,
  routeSections,
  customerRouteMetas,
} from "./routes";

function backendDefaultMenuSnapshots(): Map<string, string[]> {
  const source = readFileSync(
    resolve(
      process.cwd(),
      "../medkernel-backend/src/test/java/com/medkernel/engine/security/DefaultPermissionPolicyTest.java",
    ),
    "utf8",
  );
  const snapshots = new Map<string, string[]>();
  const entryPattern = /"([a-z-]+)", List\.of\(([\s\S]*?)\)(?=,\n\s*"[a-z-]+"|\n\s*\);)/g;
  for (const match of source.matchAll(entryPattern)) {
    const roleCode = match[1];
    const menuKeys = Array.from(match[2].matchAll(/"([^"]+)"/g), (quoted) => quoted[1]);
    snapshots.set(roleCode, menuKeys);
  }
  return snapshots;
}

describe("route metadata", () => {
  it("registers every current frontend page route", () => {
    expect(routeMetas.length).toBeGreaterThanOrEqual(36);
    expect(findRouteByPath("/terminology/mapping")?.title).toBe("术语与字典");
    expect(findRouteByPath("/advanced/graph")?.placement).toBe("primary");
  });

  it("separates clinical runtime domains from the knowledge production product surface", () => {
    expect(routeSections.map((section) => [section.key, section.label])).toEqual([
      ["workbench", "工作台"],
      ["organization-people", "机构与人员"],
      ["knowledge-governance", "知识治理"],
      ["knowledge-production", "知识生产"],
      ["clinical-collaboration", "临床协同"],
      ["quality-management", "质量管理"],
      ["compliance-security", "合规安全"],
      ["system-operations", "系统运维"],
    ]);

    const placementCounts = routeMetas
      .filter((route) => route.requireAuth && route.menuKey)
      .reduce<Record<string, number>>((counts, route) => {
        counts[route.placement] = (counts[route.placement] ?? 0) + 1;
        return counts;
      }, {});

    expect(placementCounts).toEqual({
      primary: 32,
      header: 1,
      profile: 1,
    });
  });

  it("removes duplicate menu permissions from merged pages", () => {
    // /rule/validate 是临床执行侧（医师确认危急值提醒），守卫只需 rule.read；
    // 不应要求 menu.rule-definitions（治理侧菜单），否则 clinical-user 被拦死无法完成医师确认。
    expect(findRouteByPath("/rule/validate")).toMatchObject({
      sectionKey: "knowledge-governance",
      placement: "hidden",
      hidden: true,
      requiredPermissions: ["rule.read"],
    });
    expect(findRouteByPath("/rule/validate")?.menuKey).toBeUndefined();

    expect(findRouteByPath("/qc/eval/results")).toMatchObject({
      sectionKey: "quality-management",
      placement: "hidden",
      hidden: true,
      requiredPermissions: ["menu.qc-alerts", "evaluation.read"],
    });
    expect(findRouteByPath("/qc/eval/results")?.menuKey).toBeUndefined();
  });

  it("keeps cross-domain entries outside the sidebar and classifies advanced capabilities normally", () => {
    expect(findRouteByPath("/notifications")).toMatchObject({
      sectionKey: "workbench",
      placement: "header",
      menuLabel: "消息通知",
    });
    expect(findRouteByPath("/notifications/settings")).toMatchObject({
      sectionKey: "workbench",
      placement: "profile",
      menuLabel: "通知偏好",
    });
    expect(findRouteByPath("/advanced/provenance")).toMatchObject({
      sectionKey: "knowledge-governance",
      placement: "primary",
      menuLabel: "来源与血缘",
    });
    expect(findRouteByPath("/advanced/ai-workflows")).toMatchObject({
      sectionKey: "knowledge-production",
      placement: "primary",
      menuLabel: "模型能力",
    });
    expect(findRouteByPath("/advanced/domestic")).toMatchObject({
      sectionKey: "system-operations",
      placement: "primary",
      menuLabel: "国产化自检",
    });
    expect(findRouteByPath("/system/runtime-diagnostics")).toMatchObject({
      sectionKey: "system-operations",
      placement: "primary",
      menuLabel: "运行诊断",
    });
  });

  it("keeps paths unique", () => {
    const paths = routeMetas.map((route) => route.path);
    expect(new Set(paths).size).toBe(paths.length);
  });

  it("uses permissions as the only frontend route gate", () => {
    const route = findRouteByPath("/config/releases");
    const permissionProfile = {
      permissions: [{ code: "asset.read" }],
      menuKeys: ["runtime-releases"],
    };

    expect(canAccessRoute(route, permissionProfile)).toBe(true);
    expect(
      canAccessRoute(route, {
        ...permissionProfile,
        roles: [{ code: "auditor" }],
      }),
    ).toBe(true);
    expect(
      canAccessRoute(route, {
        permissions: [],
        menuKeys: ["runtime-releases"],
      }),
    ).toBe(false);
  });

  it("只使用四个上线职责描述页面责任，不泄漏已废弃的岗位角色", () => {
    const allowedRoles = new Set(["平台管理员", "医疗引擎运营员", "临床使用者", "审计员"]);

    routeMetas.forEach((route) => {
      route.experience?.primaryRole.split(" / ").forEach((role) => {
        expect(allowedRoles.has(role), `${route.path} 使用了非上线职责：${role}`).toBe(true);
      });
    });
  });

  it("为多角色真实前台页面登记客户可读的角色视角", () => {
    expect(findRouteByPath("/cdss/fatigue")?.experience?.stakeholderViews).toEqual([
      {
        role: "医生",
        responsibility: "确认高风险提醒并登记采纳或不采纳理由",
        boundary: "不会自动生成医嘱",
      },
      {
        role: "药师",
        responsibility: "复核联合用药和 DDI 风险",
        boundary: "只记录复核意见，不替代医师确认",
      },
      {
        role: "医技",
        responsibility: "生成报告解读供临床参考",
        boundary: "不会改写已签发报告",
      },
    ]);
    expect(findRouteByPath("/clinical/followup")?.experience?.stakeholderViews).toEqual([
      {
        role: "护士",
        responsibility: "代填随访问卷并登记来源",
        boundary: "不能替患者或医生生成临床结论",
      },
      {
        role: "患者代理",
        responsibility: "回收患者自填问卷和报告",
        boundary: "只进入随访任务，不直接形成诊疗决策",
      },
      {
        role: "医生",
        responsibility: "复核异常回院事件",
        boundary: "复核后再进入线下处置或医嘱系统",
      },
    ]);
    expect(findRouteByPath("/admin/users")?.experience?.stakeholderViews).toEqual([
      {
        role: "平台管理员",
        responsibility: "维护人员、任职、登录账号与组织范围",
        boundary: "临时密码只在受控激活流程展示",
      },
      {
        role: "实施工程师",
        responsibility: "按机构上线阶段批量导入人员",
        boundary: "预检冲突未修正时不会写入任一行",
      },
    ]);
    expect(findRouteByPath("/admin/audit")?.experience?.stakeholderViews).toEqual([
      {
        role: "审计员",
        responsibility: "追溯审计事件、导出证据与验签结果",
        boundary: "默认不展示追踪号、事件编号和载荷摘要",
      },
      {
        role: "信息科",
        responsibility: "用诊断链定位系统运行问题",
        boundary: "诊断链需具备证据详情权限后展开",
      },
    ]);
  });

  it("为高风险治理与运维页面登记全视角职责边界", () => {
    expect(findRouteByPath("/qc/dashboard")?.experience?.stakeholderViews).toEqual([
      {
        role: "院长",
        responsibility: "查看全院质量趋势、风险聚类和整改成效",
        boundary: "只呈现治理态势，不直接下发临床处置或考核结论",
      },
      {
        role: "质控负责人",
        responsibility: "定位高风险问题并分派整改责任",
        boundary: "整改闭环需保留责任人、期限和复核证据",
      },
    ]);
    expect(findRouteByPath("/knowledge/governance")?.experience?.stakeholderViews).toEqual([
      {
        role: "医疗引擎运营员",
        responsibility: "审核知识候选并完成发布、驳回、替换或恢复",
        boundary: "发布前必须保留来源、验证病例和责任确认",
      },
      {
        role: "临床专家",
        responsibility: "复核医学内容、适用人群和禁忌边界",
        boundary: "专家意见进入治理记录，不绕过平台发布门禁",
      },
    ]);
    expect(findRouteByPath("/knowledge/production")?.experience?.stakeholderViews).toEqual([
      {
        role: "医疗引擎运营员",
        responsibility: "配置模型服务、医学评测和知识候选生成",
        boundary: "模型输出只进入候选治理链，不自动发布",
      },
      {
        role: "模型安全负责人",
        responsibility: "确认公网模型患者上下文外调策略和字段预览",
        boundary: "核心敏感标识默认屏蔽，发送前需要责任确认",
      },
    ]);
    expect(findRouteByPath("/adapter/hub")?.experience?.stakeholderViews).toEqual([
      {
        role: "信息科",
        responsibility: "核查院内系统连接、字段映射、死信和健康状态",
        boundary: "断连必须诚实暴露为未连接，不伪造成可用",
      },
      {
        role: "实施工程师",
        responsibility: "完成协议联调、回放校验和上线前数据质量确认",
        boundary: "联调通过不等于生产启用，仍需发布审批",
      },
    ]);
    expect(findRouteByPath("/system/providers")?.experience?.stakeholderViews).toEqual([
      {
        role: "信息科",
        responsibility: "核查数据库、知识图谱、模型服务和备份恢复状态",
        boundary: "模型或图谱不可用时必须展示降级原因",
      },
      {
        role: "平台管理员",
        responsibility: "确认运行保障项是否满足上线和恢复要求",
        boundary: "不能用手工口径覆盖健康检查和恢复证据",
      },
    ]);
  });

  it("为临床协同和运行诊断入口登记全视角职责边界", () => {
    expect(findRouteByPath("/mpi")?.experience?.stakeholderViews).toEqual([
      {
        role: "医生",
        responsibility: "查阅授权范围内的患者 360 与身份状态",
        boundary: "只能使用已授权患者事实，不处理身份合并",
      },
      {
        role: "信息科",
        responsibility: "复核重复身份、合并拆分和跨系统标识质量",
        boundary: "高风险合并拆分必须保留复核理由和审计证据",
      },
    ]);
    expect(findRouteByPath("/pathway/patients")?.experience?.stakeholderViews).toEqual([
      {
        role: "医生",
        responsibility: "查看患者路径节点、变异原因和下一步建议",
        boundary: "路径建议不能自动替代医嘱或病程记录",
      },
      {
        role: "护士",
        responsibility: "跟进路径节点任务、随访提醒和执行状态",
        boundary: "护理记录进入协同任务，不直接改变路径版本",
      },
      {
        role: "患者代理",
        responsibility: "接收随访提醒和回院提示",
        boundary: "患者反馈需由临床人员复核后进入处置",
      },
    ]);
    expect(findRouteByPath("/workflow/todos")?.experience?.stakeholderViews).toEqual([
      {
        role: "医生",
        responsibility: "处理临床确认、会诊和复核类待办",
        boundary: "完成待办只记录协同结论，不自动开立医嘱",
      },
      {
        role: "护士",
        responsibility: "接收护理执行、随访和转交任务",
        boundary: "转交必须选择院内人员和原因",
      },
      {
        role: "药师",
        responsibility: "处理用药复核和风险提醒待办",
        boundary: "药师意见不替代医师最终确认",
      },
    ]);
    expect(findRouteByPath("/notifications")?.experience?.stakeholderViews).toEqual([
      {
        role: "临床使用者",
        responsibility: "查看与本人职责相关的未读提醒和协同通知",
        boundary: "通知只提示关注，不直接完成业务动作",
      },
      {
        role: "平台管理员",
        responsibility: "识别账号、配置和运行类通知",
        boundary: "配置变更仍需进入对应管理页面完成",
      },
      {
        role: "审计员",
        responsibility: "关注导出、验签和高风险操作通知",
        boundary: "通知摘要默认不展示低频证据编号",
      },
    ]);
    expect(findRouteByPath("/advanced/domestic")?.experience?.stakeholderViews).toEqual([
      {
        role: "信息科",
        responsibility: "核查国产数据库、国密、浏览器和中间件适配状态",
        boundary: "未通过项必须保留阻断原因，不能标记为兼容",
      },
      {
        role: "实施工程师",
        responsibility: "按现场环境补齐驱动、证书和运行参数",
        boundary: "现场修复需回写配置中心或部署脚本，不靠口头交接",
      },
    ]);
    expect(findRouteByPath("/system/runtime-diagnostics")?.experience?.stakeholderViews).toEqual([
      {
        role: "信息科",
        responsibility: "查看运行诊断、追踪链路和故障定位信息",
        boundary: "证据详情权限外不展示追踪号和原始载荷",
      },
      {
        role: "审计员",
        responsibility: "核对诊断链是否具备审计追溯和导出证据",
        boundary: "只能验证证据，不修改运行状态",
      },
    ]);
  });

  it("does not keep hidden demo-only routes in the production router metadata", () => {
    expect(routeMetas.map((route) => route.path)).not.toContain("/demo/step-flow");
    expect(routeMetas.some((route) => route.title.includes("StepFlow"))).toBe(false);
  });

  it("registers the WORKBENCH-02 readiness validation route without opening a new menu slot", () => {
    const route = findRouteByPath("/workbench/readiness-validation");

    expect(route).toMatchObject({
      title: "验收自检",
      sectionKey: "workbench",
      hidden: true,
      placement: "hidden",
      pageType: "workbench",
    });
    expect(route?.menuKey).toBeUndefined();
    expect(route?.menuLabel).toBeUndefined();
    expect(route?.requiredPermissions).toEqual(["menu.workbench", "workbench:readiness:view"]);
  });

  it("makes the dashboard an explicit default landing route for any account with its menu", () => {
    const route = findRouteByPath("/dashboard");

    expect(route?.requiredPermissions).toEqual(["menu.workbench"]);
    expect(
      canAccessRoute(route, {
        permissions: [],
        menuKeys: ["workbench"],
      }),
    ).toBe(true);
  });

  it("requires evaluation read permission for every primary quality workspace", () => {
    const routes = [
      ["/qc/dashboard", "qc-dashboard"],
      ["/qc/alerts", "qc-alerts"],
      ["/qc/insurance", "insurance-audit"],
      ["/qc/eval/sets", "qc-eval-sets"],
    ] as const;

    routes.forEach(([path, menuKey]) => {
      const route = findRouteByPath(path);
      expect(route?.requiredPermissions).toEqual([`menu.${menuKey}`, "evaluation.read"]);
      expect(
        canAccessRoute(route, {
          roles: [{ code: "platform-admin" }],
          permissions: [],
          menuKeys: [menuKey],
        }),
        path,
      ).toBe(false);
    });
  });

  it("does not retain the retired standalone medical regression review route", () => {
    expect(findRouteByPath("/qc/model-evaluations")).toBeUndefined();
  });

  it("requires audit read permission for the audit workspace", () => {
    const route = findRouteByPath("/admin/audit");

    expect(route?.requiredPermissions).toEqual(["menu.admin-audit", "audit.read"]);
    expect(
      canAccessRoute(route, {
        roles: [{ code: "platform-admin" }],
        permissions: [],
        menuKeys: ["admin-audit"],
      }),
    ).toBe(false);
  });

  it("lets the built-in superadmin use every backend-granted authenticated route", () => {
    const authenticatedRoutes = routeMetas.filter((route) => route.requireAuth);
    const permissions = Array.from(
      new Set(authenticatedRoutes.flatMap((route) => route.requiredPermissions)),
      (code) => ({ code }),
    );
    const menuKeys = authenticatedRoutes
      .map((route) => route.menuKey)
      .filter((menuKey): menuKey is string => Boolean(menuKey));
    const profile = {
      roles: [{ code: "system-superadmin" }],
      permissions,
      menuKeys,
    };

    authenticatedRoutes.forEach((route) => {
      expect(canAccessRoute(route, profile), route.path).toBe(true);
    });
  });

  it("requires the implementation menu and tenant read permission", () => {
    const route = findRouteByPath("/onboarding/guide");

    expect(route?.requiredPermissions).toEqual(["menu.implementation-guide", "tenant.read"]);
    expect(
      canAccessRoute(route, {
        roles: [{ code: "platform-admin" }],
        permissions: [{ code: "tenant.read" }],
        menuKeys: ["implementation-guide"],
      }),
    ).toBe(true);
    expect(
      canAccessRoute(route, {
        roles: [{ code: "platform-admin" }],
        permissions: [{ code: "tenant.read" }],
        menuKeys: ["implementation-guide"],
      }),
    ).toBe(true);
    expect(
      canAccessRoute(route, {
        roles: [{ code: "clinical-user" }],
        permissions: [],
        menuKeys: ["implementation-guide"],
      }),
    ).toBe(false);
  });

  it("requires the tenant onboarding menu and tenant read permission", () => {
    const route = findRouteByPath("/tenant/onboarding");

    expect(route?.title).toBe("服务机构");
    expect(route?.requiredPermissions).toEqual(["menu.tenant-onboarding", "tenant.read"]);
    expect(
      canAccessRoute(route, {
        roles: [{ code: "platform-admin" }],
        permissions: [{ code: "tenant.read" }],
        menuKeys: ["tenant-onboarding"],
      }),
    ).toBe(true);
    expect(
      canAccessRoute(route, {
        roles: [{ code: "platform-admin" }],
        permissions: [{ code: "tenant.read" }],
        menuKeys: ["tenant-onboarding"],
      }),
    ).toBe(true);
    expect(
      canAccessRoute(route, {
        roles: [{ code: "clinical-user" }],
        permissions: [],
        menuKeys: ["tenant-onboarding"],
      }),
    ).toBe(false);
  });

  it("requires both the operations menu and system read permission for runtime status", () => {
    const route = findRouteByPath("/system/providers");

    expect(route?.requiredPermissions).toEqual(["menu.system-providers", "system.read"]);
    expect(
      canAccessRoute(route, {
        roles: [{ code: "platform-admin" }],
        permissions: [{ code: "system.read" }],
        menuKeys: ["system-providers"],
      }),
    ).toBe(true);
    expect(
      canAccessRoute(route, {
        roles: [{ code: "auditor" }],
        permissions: [],
        menuKeys: ["system-providers"],
      }),
    ).toBe(false);
  });

  it("uses one release governance entry without retaining the old package route", () => {
    const route = findRouteByPath("/config/releases");

    expect(route?.requiredPermissions).toEqual(["menu.runtime-releases", "asset.read"]);
    expect(route?.menuLabel).toBe("发布治理");
    expect(
      canAccessRoute(route, {
        roles: [{ code: "platform-admin" }],
        permissions: [{ code: "asset.read" }],
        menuKeys: ["runtime-releases"],
      }),
    ).toBe(true);
    expect(
      canAccessRoute(route, {
        roles: [{ code: "engine-operator" }],
        permissions: [{ code: "asset.read" }],
        menuKeys: ["runtime-releases"],
      }),
    ).toBe(true);
    expect(
      canAccessRoute(route, {
        roles: [{ code: "clinical-user" }],
        permissions: [],
        menuKeys: ["runtime-releases"],
      }),
    ).toBe(false);
  });

  it("requires domain read permissions for rule and pathway configuration workspaces", () => {
    expect(findRouteByPath("/pathway/templates")?.requiredPermissions).toEqual([
      "menu.pathway-templates",
      "pathway.read",
    ]);
    expect(findRouteByPath("/rule/definitions")?.requiredPermissions).toEqual([
      "menu.rule-definitions",
      "rule.read",
    ]);
  });

  it("separates knowledge review from manual diagnosis knowledge maintenance", () => {
    expect(findRouteByPath("/knowledge/governance")).toMatchObject({
      title: "知识审核与发布",
      menuLabel: "知识审核与发布",
      pageType: "review",
    });
    expect(findRouteByPath("/knowledge/governance")?.experience?.goal).toBe(
      "审核知识候选并完成发布、驳回、替换或恢复",
    );

    expect(findRouteByPath("/knowledge/diagnosis")).toMatchObject({
      title: "诊断知识维护",
      breadcrumb: ["知识治理", "诊断知识维护"],
      sectionKey: "knowledge-governance",
      menuKey: "diagnosis-knowledge",
      menuLabel: "诊断知识维护",
      placement: "primary",
      requiredPermissions: ["menu.diagnosis-knowledge", "knowledge.read"],
      pageType: "configuration",
    });
  });

  it("keeps unified authoring assets as a governed knowledge route without adding a second-level menu", () => {
    const route = findRouteByPath("/authoring/assets");

    expect(route?.sectionKey).toBe("knowledge-governance");
    expect(route?.placement).toBe("hidden");
    expect(route?.hidden).toBe(true);
    expect(route?.menuKey).toBeUndefined();
    expect(route?.menuLabel).toBeUndefined();
    expect(route?.requiredPermissions).toEqual(["rule.read", "pathway.read"]);
    expect(
      canAccessRoute(route, {
        roles: [{ code: "platform-admin" }],
        permissions: [{ code: "rule.read" }, { code: "pathway.read" }],
        menuKeys: ["runtime-releases"],
      }),
    ).toBe(true);
  });

  it("opens terminology mapping to authorized readers and leaves actions to page permissions", () => {
    const route = findRouteByPath("/terminology/mapping");

    expect(route?.requiredPermissions).toEqual(["menu.terminology-mapping", "term.read"]);
    expect(
      canAccessRoute(route, {
        roles: [{ code: "platform-admin" }],
        permissions: [{ code: "term.read" }],
        menuKeys: ["terminology-mapping"],
      }),
    ).toBe(true);
    expect(
      canAccessRoute(route, {
        roles: [{ code: "engine-operator" }],
        permissions: [{ code: "term.read" }, { code: "term.write" }],
        menuKeys: ["terminology-mapping"],
      }),
    ).toBe(true);
    expect(
      canAccessRoute(route, {
        roles: [{ code: "clinical-user" }],
        permissions: [{ code: "term.read" }, { code: "term.write" }],
        menuKeys: ["terminology-mapping"],
      }),
    ).toBe(true);
    expect(
      canAccessRoute(route, {
        roles: [{ code: "platform-admin" }],
        permissions: [{ code: "term.read" }],
        menuKeys: ["terminology-mapping"],
      }),
    ).toBe(true);
    expect(
      canAccessRoute(route, {
        roles: [{ code: "platform-admin" }],
        permissions: [{ code: "term.read" }],
        menuKeys: ["terminology-mapping"],
      }),
    ).toBe(true);
    expect(
      canAccessRoute(route, {
        roles: [{ code: "clinical-user" }],
        permissions: [{ code: "term.read" }, { code: "term.write" }, { code: "term.publish" }],
        menuKeys: ["terminology-mapping"],
      }),
    ).toBe(true);
    expect(
      canAccessRoute(route, {
        roles: [{ code: "platform-admin" }],
        permissions: [],
        menuKeys: ["terminology-mapping"],
      }),
    ).toBe(false);
  });

  it("requires integration read write and execute permissions for adapter hub", () => {
    const route = findRouteByPath("/adapter/hub");
    const protocolFilter = route?.experience?.defaultFilters.find(
      (filter) => filter.key === "protocolType",
    );

    expect(route?.requiredPermissions).toEqual([
      "menu.adapter-hub",
      "integration.read",
      "integration.write",
      "integration.execute",
    ]);
    expect(protocolFilter?.label).toBe("接入协议");
    expect(protocolFilter?.options?.map((option) => option.value)).toEqual([
      "HL7",
      "FHIR",
      "Webhook",
      "REST",
      "WebService",
    ]);
    expect(
      canAccessRoute(route, {
        roles: [{ code: "platform-admin" }],
        permissions: [
          { code: "integration.read" },
          { code: "integration.write" },
          { code: "integration.execute" },
        ],
        menuKeys: ["adapter-hub"],
      }),
    ).toBe(true);
    expect(
      canAccessRoute(route, {
        roles: [{ code: "platform-admin" }],
        permissions: [
          { code: "integration.read" },
          { code: "integration.write" },
          { code: "integration.execute" },
        ],
        menuKeys: ["adapter-hub"],
      }),
    ).toBe(true);
    expect(
      canAccessRoute(route, {
        roles: [{ code: "platform-admin" }],
        permissions: [
          { code: "integration.read" },
          { code: "integration.write" },
          { code: "integration.execute" },
        ],
        menuKeys: ["adapter-hub"],
      }),
    ).toBe(true);
    expect(
      canAccessRoute(route, {
        roles: [{ code: "clinical-user" }],
        permissions: [
          { code: "integration.read" },
          { code: "integration.write" },
          { code: "integration.execute" },
        ],
        menuKeys: ["adapter-hub"],
      }),
    ).toBe(true);
    expect(
      canAccessRoute(route, {
        roles: [{ code: "platform-admin" }],
        permissions: [{ code: "integration.read" }],
        menuKeys: ["adapter-hub"],
      }),
    ).toBe(false);
  });

  it("requires breadcrumb metadata for authenticated pages", () => {
    routeMetas
      .filter((route) => route.requireAuth)
      .forEach((route) => {
        expect(route.breadcrumb.length).toBeGreaterThan(0);
        expect(route.title.length).toBeGreaterThan(0);
      });
  });

  it("requires BASE-06 route authorization metadata for every authenticated route", () => {
    routeMetas
      .filter((route) => route.requireAuth)
      .forEach((route) => {
        expect(route.requiredPermissions, `${route.path} 缺少 requiredPermissions`).toBeDefined();
        expect(Array.isArray(route.requiredPermissions)).toBe(true);
      });
  });

  it("binds menu routes to the INFRA-05 second-level menu permission code", () => {
    routeMetas
      .filter(
        (route) =>
          route.requireAuth && route.sectionKey && route.menuKey && route.placement !== "hidden",
      )
      .forEach((route) => {
        expect(route.requiredPermissions).toContain(`menu.${route.menuKey}`);
        if (route.menuKey !== route.sectionKey) {
          expect(route.requiredPermissions).not.toContain(`menu.${route.sectionKey}`);
        }
      });
  });

  it("accepts backend menuKeys as the route authorization source for second-level menus", () => {
    expect(
      canAccessRoute(findRouteByPath("/qc/eval/results"), {
        roles: [{ code: "engine-operator" }],
        permissions: [{ code: "evaluation.read" }],
        menuKeys: ["qc-alerts"],
      }),
    ).toBe(true);
    expect(
      canAccessRoute(findRouteByPath("/advanced/provenance"), {
        roles: [{ code: "auditor" }],
        permissions: [{ code: "knowledge.read" }],
        menuKeys: ["provenance"],
      }),
    ).toBe(true);
  });

  it("requires both the provenance menu and knowledge read permission", () => {
    const route = findRouteByPath("/advanced/provenance");

    expect(route?.requiredPermissions).toEqual(["menu.provenance", "knowledge.read"]);
    expect(
      canAccessRoute(route, {
        roles: [{ code: "engine-operator" }],
        permissions: [{ code: "knowledge.read" }],
        menuKeys: ["provenance"],
      }),
    ).toBe(true);
    expect(
      canAccessRoute(route, {
        roles: [{ code: "platform-admin" }],
        permissions: [],
        menuKeys: ["provenance"],
      }),
    ).toBe(false);
  });

  it("allows the platform administrator to enter knowledge governance for platform retirement", () => {
    const route = findRouteByPath("/knowledge/governance");

    expect(
      canAccessRoute(route, {
        roles: [{ code: "platform-admin" }],
        permissions: [{ code: "knowledge.review" }],
        menuKeys: ["knowledge-governance"],
      }),
    ).toBe(true);
  });

  it("allows dedicated knowledge and access responsibilities into their owned pages", () => {
    const knowledgeRoute = findRouteByPath("/knowledge/governance");
    const productionRoute = findRouteByPath("/knowledge/production");
    const usersRoute = findRouteByPath("/admin/users");
    const identityRoute = findRouteByPath("/security/identity-binding");

    for (const roleCode of ["engine-operator", "engine-operator"]) {
      expect(
        canAccessRoute(knowledgeRoute, {
          roles: [{ code: roleCode }],
          permissions: [{ code: "knowledge.review" }],
          menuKeys: ["knowledge-governance"],
        }),
      ).toBe(true);
    }
    expect(
      canAccessRoute(productionRoute, {
        roles: [{ code: "platform-admin" }],
        permissions: [{ code: "knowledge.read" }],
        menuKeys: ["knowledge-production"],
      }),
    ).toBe(true);
    expect(
      canAccessRoute(usersRoute, {
        roles: [{ code: "platform-admin" }],
        permissions: [{ code: "org.read" }],
        menuKeys: ["admin-users"],
      }),
    ).toBe(true);
    expect(
      canAccessRoute(usersRoute, {
        roles: [{ code: "platform-admin" }],
        permissions: [{ code: "org.read" }],
        menuKeys: ["admin-users"],
      }),
    ).toBe(true);
    expect(
      canAccessRoute(identityRoute, {
        roles: [{ code: "platform-admin" }],
        permissions: [{ code: "org.read" }],
        menuKeys: ["identity-bindings"],
      }),
    ).toBe(true);
    expect(
      canAccessRoute(identityRoute, {
        roles: [{ code: "platform-admin" }],
        permissions: [{ code: "org.read" }],
        menuKeys: ["identity-bindings"],
      }),
    ).toBe(true);
  });

  it("keeps frontend route guards aligned with backend default menu snapshots", () => {
    const routeByMenuKey = new Map(
      routeMetas
        .filter((route) => route.requireAuth && route.menuKey)
        .map((route) => [route.menuKey, route]),
    );
    const snapshots = backendDefaultMenuSnapshots();
    const mismatches: string[] = [];

    expect(snapshots.size).toBe(4);
    snapshots.forEach((menuKeys, roleCode) => {
      menuKeys.forEach((menuKey) => {
        const route = routeByMenuKey.get(menuKey);
        if (!route) {
          mismatches.push(`${roleCode}:${menuKey}:missing-route`);
          return;
        }
        const allowed = canAccessRoute(route, {
          roles: [{ code: roleCode }],
          permissions: route.requiredPermissions.map((code) => ({ code })),
          menuKeys: [menuKey],
        });
        if (!allowed) {
          mismatches.push(`${roleCode}:${menuKey}:${route.path}`);
        }
      });
    });
    expect(mismatches).toEqual([]);
  });

  it("requires graph menu and projection read permission", () => {
    const route = findRouteByPath("/advanced/graph");

    expect(route?.placement).toBe("primary");
    expect(route?.requiredPermissions).toEqual(["menu.graph-explore", "projection.read"]);
    expect(
      canAccessRoute(route, {
        roles: [{ code: "engine-operator" }],
        permissions: [{ code: "projection.read" }],
        menuKeys: ["graph-explore"],
      }),
    ).toBe(true);
    expect(
      canAccessRoute(route, {
        roles: [{ code: "clinical-user" }],
        permissions: [{ code: "projection.read" }],
        menuKeys: ["graph-explore"],
      }),
    ).toBe(true);
  });

  it("splits review, institution maintenance and production into separate lifecycle entries", () => {
    expect(findRouteByPath("/knowledge/governance")).toMatchObject({
      title: "知识审核与发布",
      sectionKey: "knowledge-governance",
      menuKey: "knowledge-governance",
      requiredPermissions: ["menu.knowledge-governance", "knowledge.review"],
      pageType: "review",
    });
    expect(findRouteByPath("/knowledge/institution")).toMatchObject({
      title: "机构知识",
      sectionKey: "knowledge-governance",
      menuKey: "institution-knowledge",
      requiredPermissions: ["menu.institution-knowledge", "knowledge.write"],
      pageType: "configuration",
    });
    expect(findRouteByPath("/knowledge/production")).toMatchObject({
      title: "知识生产",
      sectionKey: "knowledge-production",
      menuKey: "knowledge-production",
      requiredPermissions: ["menu.knowledge-production", "knowledge.read"],
      pageType: "configuration",
      stateMachine: "config",
    });
  });

  it("requires the knowledge production menu and knowledge read permission", () => {
    const route = findRouteByPath("/knowledge/production");

    expect(
      canAccessRoute(route, {
        roles: [{ code: "engine-operator" }],
        permissions: [{ code: "knowledge.read" }],
        menuKeys: ["knowledge-production"],
      }),
    ).toBe(true);
    expect(
      canAccessRoute(route, {
        roles: [{ code: "clinical-user" }],
        permissions: [{ code: "knowledge.read" }],
        menuKeys: [],
      }),
    ).toBe(false);
  });

  it("requires model capability menu and llm read permission", () => {
    const route = findRouteByPath("/advanced/ai-workflows");

    expect(route?.placement).toBe("primary");
    expect(route?.requiredPermissions).toEqual(["menu.ai-workflows", "llm.read"]);
    expect(
      canAccessRoute(route, {
        roles: [{ code: "platform-admin" }],
        permissions: [{ code: "llm.read" }],
        menuKeys: ["ai-workflows"],
      }),
    ).toBe(true);
    expect(
      canAccessRoute(route, {
        roles: [{ code: "clinical-user" }],
        permissions: [],
        menuKeys: ["ai-workflows"],
      }),
    ).toBe(false);
  });

  it("requires the WORKBENCH-02 readiness action permission in addition to the workbench menu", () => {
    expect(
      canAccessRoute(findRouteByPath("/workbench/readiness-validation"), {
        roles: [{ code: "platform-admin" }],
        permissions: [{ code: "workbench:readiness:view" }],
        menuKeys: ["workbench"],
      }),
    ).toBe(true);
    expect(
      canAccessRoute(findRouteByPath("/workbench/readiness-validation"), {
        roles: [{ code: "clinical-user" }],
        permissions: [{ code: "workbench:readiness:view" }],
        menuKeys: ["workbench"],
      }),
    ).toBe(true);
    expect(
      canAccessRoute(findRouteByPath("/workbench/readiness-validation"), {
        roles: [{ code: "platform-admin" }],
        permissions: [],
        menuKeys: ["workbench"],
      }),
    ).toBe(false);
  });

  it("rejects legacy first-level section menuKeys for second-level routes", () => {
    expect(
      canAccessRoute(findRouteByPath("/terminology/mapping"), {
        roles: [{ code: "platform-admin" }],
        permissions: [],
        menuKeys: ["pilot-setup"],
      }),
    ).toBe(false);
    expect(
      canAccessRoute(findRouteByPath("/qc/eval/results"), {
        roles: [{ code: "engine-operator" }],
        permissions: [],
        menuKeys: ["quality-improve"],
      }),
    ).toBe(false);
  });

  it("requires experience metadata for authenticated menu routes", () => {
    const menuRoutes = routeMetas.filter((route) => route.requireAuth && route.menuKey);

    expect(menuRoutes.length).toBeGreaterThan(0);
    menuRoutes.forEach((route) => {
      expect(route.experience, `${route.path} 缺少 experience`).toBeDefined();
      expect(route.experience?.primaryRole).toBeTruthy();
      expect(route.experience?.goal).toBeTruthy();
      expect(route.experience?.defaultView).toBeTruthy();
      expect(route.experience?.evidence).toBeTruthy();
      expect(route.experience?.interruptionLevel).toMatch(/^(none|info|weak|strong)$/);
      expect(route.experience?.dataScale.exportStrategy).toMatch(/^(none|disabled|async)$/);
      expect(route.experience?.defaultFilters.length ?? 0).toBeLessThanOrEqual(3);
    });
  });

  it("marks every authenticated route as six-state capable", () => {
    routeMetas.forEach((route) => {
      expect(route.requiresSixStates).toBe(route.requireAuth);
    });
  });

  it("requires configuration routes to use the 7-step flow and an approved state machine", () => {
    const configurationRoutes = routeMetas.filter((route) => route.pageType === "configuration");

    expect(configurationRoutes.length).toBeGreaterThan(0);
    configurationRoutes.forEach((route) => {
      expect(route.requiresStepFlow, `${route.path} 缺少 7 步流约束`).toBe(true);
      expect(["config", "change"]).toContain(route.stateMachine);
    });
  });

  it("classifies the unified security baseline as system management instead of a readonly dashboard", () => {
    const route = findRouteByPath("/security/baseline");

    expect(route).toEqual(
      expect.objectContaining({
        title: "安全与配置",
        pageType: "system",
        requiresStepFlow: false,
        requiredPermissions: ["menu.security-baseline", "system.read"],
      }),
    );
    expect(route?.experience?.goal).toContain("管理");
    expect(route?.experience?.riskLevel).toBe("high");
  });

  it("treats identity binding as a tenant-scoped managed security workflow", () => {
    const route = findRouteByPath("/security/identity-binding");

    expect(route).toEqual(
      expect.objectContaining({
        title: "身份来源",
        pageType: "system",
        stateMachine: "change",
        requiredPermissions: ["menu.identity-bindings", "org.read"],
      }),
    );
    expect(route?.experience?.goal).toContain("管理");
    expect(route?.experience?.riskLevel).toBe("high");
  });

  it("returns customer routes without hidden tools and includes normally classified advanced capabilities", () => {
    expect(customerRouteMetas.some((route) => route.placement === "hidden")).toBe(false);
    expect(customerRouteMetas.map((route) => route.path)).toContain("/dashboard");
    expect(customerRouteMetas.map((route) => route.path)).toContain("/notifications");
    expect(customerRouteMetas.map((route) => route.path)).toContain("/system/runtime-diagnostics");
  });

  it("builds breadcrumbs from route metadata", () => {
    expect(getRouteBreadcrumb("/qc/dashboard")).toEqual(["质量管理", "质量管理概览"]);
    expect(getRouteBreadcrumb("/missing")).toEqual(["未找到页面"]);
  });

  it("allows clinical users with rule permission to reach physician confirmation", () => {
    const route = findRouteByPath("/rule/validate");
    if (!route) {
      throw new Error("缺少 /rule/validate 路由元数据");
    }
    // 医师需要在此页完成危急值提醒的人工确认（override），守卫只需 rule.read。
    expect(
      canAccessRoute(route, {
        roles: [{ code: "clinical-user" }],
        permissions: [{ code: "rule.read" }, { code: "rule.override" }],
        menuKeys: ["patient-pathways"],
      }),
    ).toBe(true);
    // 无 rule.read 的角色仍被拦截。
    expect(
      canAccessRoute(route, {
        roles: [{ code: "clinical-user" }],
        permissions: [],
        menuKeys: ["patient-pathways"],
      }),
    ).toBe(false);
  });

  it("classifies the full-truth sandbox as an ordinary clinical workspace", () => {
    const route = findRouteByPath("/sandbox");

    expect(route).toMatchObject({
      title: "全真体验沙盘",
      sectionKey: "clinical-collaboration",
      menuKey: "sandbox",
      placement: "primary",
      requiredPermissions: ["menu.sandbox", "sandbox.run"],
    });
    for (const roleCode of ["clinical-user", "engine-operator"]) {
      expect(
        canAccessRoute(route, {
          roles: [{ code: roleCode }],
          permissions: [{ code: "sandbox.run" }],
          menuKeys: ["sandbox"],
        }),
        `${roleCode} 应能进入全真体验沙盘`,
      ).toBe(true);
    }
    expect(
      canAccessRoute(route, {
        roles: [{ code: "clinical-user" }],
        permissions: [],
        menuKeys: ["sandbox"],
      }),
    ).toBe(false);
  });
});
