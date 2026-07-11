import assert from "node:assert/strict";
import { existsSync, readFileSync } from "node:fs";
import { fileURLToPath } from "node:url";
import path from "node:path";
import test from "node:test";

const repositoryRoot = path.resolve(
  path.dirname(fileURLToPath(import.meta.url)),
  "../..",
);
const catalogPath = path.join(
  repositoryRoot,
  "docs/contracts/product/product-entry-catalog.v1.json",
);
const sharedAuditEventPath = path.join(
  repositoryRoot,
  "docs/contracts/events/shared-audit-event.v1.json",
);

const responsibilityRoles = [
  "platform-admin",
  "engine-operator",
  "clinical-user",
  "auditor",
];
const sixStates = [
  "loading",
  "empty",
  "ready",
  "error",
  "forbidden",
  "degraded",
];
const sectionCodes = new Set([
  "workbench",
  "organization-people",
  "knowledge-governance",
  "knowledge-production",
  "clinical-collaboration",
  "quality-management",
  "compliance-security",
  "system-operations",
]);
const placements = new Set(["primary", "header", "profile"]);

function assertNonBlank(value, message) {
  assert.equal(typeof value, "string", message);
  assert.notEqual(value.trim(), "", message);
}

function assertProductEntryCatalog(catalog, sharedAuditEvent) {
  assert.equal(sharedAuditEvent.$id, "shared-audit-event.v1");
  assert.equal(catalog.schemaVersion, "1.0.0");
  assert.equal(catalog.catalogId, "medkernel-product-entry-catalog");
  assert.deepEqual(catalog.responsibilityRoles, responsibilityRoles);
  assert.deepEqual(catalog.sixStates, sixStates);
  assert.deepEqual(catalog.organizationScopeContract?.dataPermissionCodes, [
    "data.department",
    "data.hospital",
    "data.group",
    "data.desensitized",
  ]);
  assert.equal(
    catalog.organizationScopeContract?.mode,
    "effective-assignment-intersection",
  );
  assert.equal(catalog.organizationScopeContract?.tenantBound, true);
  assert.ok(Array.isArray(catalog.entries));
  assert.equal(catalog.entries.length, 35, "产品入口必须恰好为 35 个");

  const entryCodes = new Set();
  const routes = new Set();
  const placementCounts = { primary: 0, header: 0, profile: 0 };
  const coveredRoles = new Set();

  for (const entry of catalog.entries) {
    assert.match(entry.entryCode, /^[a-z0-9]+(?:-[a-z0-9]+)*$/);
    assert.ok(
      !entryCodes.has(entry.entryCode),
      `入口编码重复：${entry.entryCode}`,
    );
    entryCodes.add(entry.entryCode);

    assertNonBlank(entry.displayName, `${entry.entryCode} 缺少中文名称`);
    assert.ok(
      sectionCodes.has(entry.sectionCode),
      `${entry.entryCode} 分组无效`,
    );
    assert.ok(
      placements.has(entry.placement),
      `${entry.entryCode} 承载位置无效`,
    );
    placementCounts[entry.placement] += 1;

    assert.match(entry.route, /^\/[a-z0-9][a-z0-9/-]*$/);
    assert.ok(!routes.has(entry.route), `入口路由重复：${entry.route}`);
    routes.add(entry.route);

    assert.ok(Array.isArray(entry.responsibilityRoles));
    assert.ok(
      entry.responsibilityRoles.length > 0,
      `${entry.entryCode} 未指定职责`,
    );
    assert.equal(
      new Set(entry.responsibilityRoles).size,
      entry.responsibilityRoles.length,
      `${entry.entryCode} 职责重复`,
    );
    for (const role of entry.responsibilityRoles) {
      assert.ok(
        responsibilityRoles.includes(role),
        `${entry.entryCode} 包含非固定职责 ${role}`,
      );
      coveredRoles.add(role);
    }

    assert.ok(Array.isArray(entry.requiredPermissions));
    assert.ok(
      entry.requiredPermissions.length > 0,
      `${entry.entryCode} 未声明权限`,
    );
    assert.equal(
      new Set(entry.requiredPermissions).size,
      entry.requiredPermissions.length,
      `${entry.entryCode} 权限重复`,
    );
    assert.ok(
      entry.requiredPermissions.includes(`menu.${entry.entryCode}`),
      `${entry.entryCode} 缺少对应菜单权限`,
    );
    for (const permission of entry.requiredPermissions) {
      assert.match(permission, /^[a-z][a-z0-9-]*(?:[.:][a-z0-9-]+)+$/);
    }

    assert.equal(
      entry.organizationScopeMode,
      "effective-assignment-intersection",
    );
    assert.ok(Array.isArray(entry.coreActions));
    assert.ok(
      entry.coreActions.length > 0,
      `${entry.entryCode} 未声明核心动作`,
    );
    const actionCodes = new Set();
    for (const action of entry.coreActions) {
      assert.match(
        action.actionCode,
        new RegExp(`^${entry.entryCode}(?:\\.[a-z0-9-]+)+$`),
      );
      assert.ok(
        !actionCodes.has(action.actionCode),
        `${entry.entryCode} 核心动作编码重复`,
      );
      actionCodes.add(action.actionCode);
      assertNonBlank(action.label, `${entry.entryCode} 核心动作缺少说明`);
      assert.ok(
        entry.requiredPermissions.includes(action.requiredPermission),
        `${entry.entryCode} 核心动作引用未登记权限`,
      );
    }

    assert.equal(entry.consumerReadback?.required, true);
    assert.equal(
      entry.consumerReadback?.strategy,
      "authoritative-service-state",
    );
    assert.equal(
      entry.consumerReadback?.evidenceKey,
      `catalog.entries.${entry.entryCode}.readback`,
    );
    assert.equal(entry.auditReadback?.required, true);
    assert.equal(entry.auditReadback?.strategy, "shared-audit-event");
    assert.equal(entry.auditReadback?.eventContract, "shared-audit-event.v1");
    assert.equal(
      entry.auditReadback?.evidenceKey,
      `catalog.entries.${entry.entryCode}.audit`,
    );
    assert.deepEqual(entry.sixStates, sixStates);
    assert.equal(entry.evidenceKey, `catalog.entries.${entry.entryCode}`);
  }

  assert.deepEqual([...coveredRoles].sort(), [...responsibilityRoles].sort());
  assert.deepEqual(placementCounts, { primary: 33, header: 1, profile: 1 });
}

test("产品入口合同唯一登记 35 个入口及上线必需语义", () => {
  assert.ok(existsSync(catalogPath), `产品入口合同不存在：${catalogPath}`);
  assert.ok(existsSync(sharedAuditEventPath), "共享审计事件合同不存在");

  const catalog = JSON.parse(readFileSync(catalogPath, "utf8"));
  const sharedAuditEvent = JSON.parse(
    readFileSync(sharedAuditEventPath, "utf8"),
  );
  assertProductEntryCatalog(catalog, sharedAuditEvent);
});

test("产品入口合同拒绝平行加入第 36 项", () => {
  const catalog = JSON.parse(readFileSync(catalogPath, "utf8"));
  const sharedAuditEvent = JSON.parse(
    readFileSync(sharedAuditEventPath, "utf8"),
  );
  const drifted = structuredClone(catalog);
  drifted.entries.push({
    entryCode: "parallel-entry",
    route: "/parallel-entry",
  });

  assert.throws(
    () => assertProductEntryCatalog(drifted, sharedAuditEvent),
    /产品入口必须恰好为 35 个/u,
  );
});
