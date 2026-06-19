#!/usr/bin/env python3
"""只读验证试点租户、12 个角色账号及沙盘权限边界。"""

import argparse
import json
import os
import sys
import urllib.error
import urllib.parse
import urllib.request


DEFAULT_API = "http://127.0.0.1:18080/medkernel/api/v1"
DEFAULT_MASTER = "/zoesoft/medkernel/conf/medkernel-accounts.json"
DEFAULT_PILOT = "pilot-hospital"
PLATFORM_TENANT = "t-1"
REQUIRED_ROLES = (
    "organization-admin",
    "identity-access-admin",
    "knowledge-governor",
    "clinical-governor",
    "clinical-decision-user",
    "nursing-collaborator",
    "medication-safety-user",
    "diagnostic-service-user",
    "quality-governor",
    "compliance-auditor",
    "integration-operator",
    "implementation-operator",
)


class Session:

    def __init__(self):
        self.cookie = ""
        self.xsrf = ""

    def absorb(self, response):
        pairs = {}
        for header in response.headers.get_all("Set-Cookie") or []:
            first = header.split(";", 1)[0].strip()
            if "=" in first:
                key, value = first.split("=", 1)
                pairs[key] = value
        current = dict(
            part.split("=", 1) for part in self.cookie.split("; ") if "=" in part
        )
        current.update(pairs)
        self.cookie = "; ".join(f"{key}={value}" for key, value in current.items())
        if "XSRF-TOKEN" in pairs:
            self.xsrf = urllib.parse.unquote(pairs["XSRF-TOKEN"])


def api_call(api, method, path, session=None, body=None):
    data = json.dumps(body).encode() if body is not None else None
    request = urllib.request.Request(api + path, data=data, method=method)
    request.add_header("Accept", "application/json")
    if data is not None:
        request.add_header("Content-Type", "application/json")
    if session and session.cookie:
        request.add_header("Cookie", session.cookie)
        if method not in ("GET", "HEAD") and session.xsrf:
            request.add_header("X-XSRF-TOKEN", session.xsrf)
    try:
        response = urllib.request.urlopen(request, timeout=40)
        raw = response.read().decode()
        return response.status, json.loads(raw) if raw else None, response
    except urllib.error.HTTPError as exception:
        raw = exception.read().decode("utf-8", "replace")
        try:
            parsed = json.loads(raw)
        except json.JSONDecodeError:
            parsed = {"raw": raw[:200]}
        return exception.code, parsed, exception


def login(api, tenant_id, username, password):
    session = Session()
    status, body, response = api_call(
        api,
        "POST",
        "/auth/login",
        body={
            "tenantId": tenant_id,
            "username": username,
            "password": password,
        },
    )
    if status == 200 and hasattr(response, "headers"):
        session.absorb(response)
    return status, body, session


def find_single(items, key, value, label):
    matches = [item for item in items if item.get(key) == value]
    if len(matches) != 1:
        raise ValueError(f"{label} 必须且只能存在一条，实际 {len(matches)} 条")
    return matches[0]


def evaluate_state(state):
    failures = []
    if not state.get("tenantPresent"):
        failures.append("试点租户不存在")
    if not state.get("platformGovernanceAdminPresent"):
        failures.append("platform-governance-admin 不存在或角色不正确")

    accounts = state.get("accounts") or {}
    for role in REQUIRED_ROLES:
        account = accounts.get(role)
        if account is None:
            failures.append(f"缺少账号 {role}")
            continue
        if not account.get("login"):
            failures.append(f"账号 {role} 无法登录")
        if role not in (account.get("roles") or []):
            failures.append(f"账号 {role} 缺少同名角色")

    clinical_permissions = set(
        (accounts.get("clinical-decision-user") or {}).get("permissions") or []
    )
    if "sandbox.run" not in clinical_permissions:
        failures.append("clinical-decision-user 缺少 sandbox.run")

    quality_permissions = set(
        (accounts.get("quality-governor") or {}).get("permissions") or []
    )
    if "sandbox.run" in quality_permissions:
        failures.append("quality-governor 不应持有 sandbox.run")

    return {
        "passed": not failures,
        "tenantPresent": bool(state.get("tenantPresent")),
        "platformGovernanceAdminPresent": bool(
            state.get("platformGovernanceAdminPresent")
        ),
        "accountCount": len(accounts),
        "failures": failures,
    }


def collect_state(api, master, tenant_id):
    platform_owner = find_single(
        master.get("accounts") or [],
        "username",
        "platform-owner",
        "platform-owner",
    )
    status, _, platform_session = login(
        api, PLATFORM_TENANT, "platform-owner", platform_owner["password"]
    )
    if status != 200:
        raise RuntimeError(f"platform-owner 登录失败，HTTP {status}")

    status, body, _ = api_call(api, "GET", "/admin/tenants", platform_session)
    if status != 200:
        raise RuntimeError(f"读取租户失败，HTTP {status}")
    tenants = (body or {}).get("data") or []
    tenant_present = any(item.get("tenantId") == tenant_id for item in tenants)

    platform_detail_status, body, _ = api_call(
        api,
        "GET",
        "/compliance/users/platform-governance-admin",
        platform_session,
    )
    platform_roles = (
        [role.get("code") for role in (body or {}).get("data", {}).get("roles", [])]
        if platform_detail_status == 200
        else []
    )

    tenant = find_single(
        master.get("tenants") or [], "tenantId", tenant_id, f"租户 {tenant_id}"
    )
    accounts_by_role = {}
    for account in tenant.get("accounts") or []:
        role = account.get("role")
        if role in accounts_by_role:
            raise ValueError(f"账号总表存在重复角色 {role}")
        accounts_by_role[role] = account

    organization_admin = accounts_by_role.get("organization-admin")
    if not organization_admin:
        raise ValueError("账号总表缺少 organization-admin")
    status, _, organization_session = login(
        api,
        tenant_id,
        organization_admin["username"],
        organization_admin["password"],
    )
    if status != 200:
        raise RuntimeError(f"organization-admin 登录失败，HTTP {status}")

    account_state = {}
    for role in REQUIRED_ROLES:
        account = accounts_by_role.get(role)
        if not account:
            continue
        login_status, _, _ = login(
            api, tenant_id, account["username"], account["password"]
        )
        user_id = account.get("userId") or account["username"]
        detail_status, detail_body, _ = api_call(
            api,
            "GET",
            f"/compliance/users/{urllib.parse.quote(user_id)}",
            organization_session,
        )
        detail = (detail_body or {}).get("data", {}) if detail_status == 200 else {}
        account_state[role] = {
            "login": login_status == 200,
            "roles": [item.get("code") for item in detail.get("roles", [])],
            "permissions": [
                item.get("code") for item in detail.get("effectivePermissions", [])
            ],
        }

    return {
        "tenantPresent": tenant_present,
        "platformGovernanceAdminPresent": (
            platform_detail_status == 200
            and "platform-governance-admin" in platform_roles
        ),
        "accounts": account_state,
    }


def parse_args(argv=None):
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument(
        "--api",
        default=os.environ.get("MEDKERNEL_API", DEFAULT_API),
        help="MedKernel API base",
    )
    parser.add_argument(
        "--master",
        default=os.environ.get("MEDKERNEL_ACCOUNT_MASTER", DEFAULT_MASTER),
        help="受控账号总表路径",
    )
    parser.add_argument(
        "--tenant",
        default=os.environ.get("SANDBOX_TENANT_ID", DEFAULT_PILOT),
        help="演练租户 ID",
    )
    return parser.parse_args(argv)


def main(argv=None):
    args = parse_args(argv)
    try:
        with open(args.master, encoding="utf-8") as stream:
            master = json.load(stream)
        state = collect_state(args.api.rstrip("/"), master, args.tenant)
        report = evaluate_state(state)
    except (OSError, ValueError, RuntimeError, json.JSONDecodeError) as exception:
        print(
            json.dumps(
                {"passed": False, "failures": [str(exception)]}, ensure_ascii=False
            )
        )
        return 1

    print(json.dumps(report, ensure_ascii=False, sort_keys=True))
    return 0 if report["passed"] else 1


if __name__ == "__main__":
    raise SystemExit(main())
