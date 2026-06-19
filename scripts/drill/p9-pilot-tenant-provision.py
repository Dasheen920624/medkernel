#!/usr/bin/env python3
"""幂等准备演练租户与治理账号，并原子更新受控账号总表。"""

import argparse
import json
import os
import re
import secrets
import sys
import tempfile
import time
import urllib.error
import urllib.parse
import urllib.request
from pathlib import Path


DEFAULT_API = "http://127.0.0.1:18080/medkernel/api/v1"
DEFAULT_MASTER = "/zoesoft/medkernel/conf/medkernel-accounts.json"
PLATFORM_TENANT = "t-1"
DEFAULT_PILOT_TENANT = "pilot-hospital"
DEFAULT_PILOT_NAME = "演练试点医院"

UPPER = "ABCDEFGHJKMNPQRSTUVWXYZ"
LOWER = "abcdefghijkmnpqrstuvwxyz"
DIGIT = "23456789"
SYMBOL = "@#%!"
ALL_PASSWORD_CHARACTERS = UPPER + LOWER + DIGIT + SYMBOL

PLATFORM_GOVERNANCE_ACCOUNT = (
    "platform-governance-admin",
    "平台治理管理员",
    "platform-governance-admin",
)
TENANT_ROLES = (
    ("identity-access-admin", "人员与访问管理员"),
    ("knowledge-governor", "机构知识治理员"),
    ("clinical-governor", "临床治理负责人"),
    ("clinical-decision-user", "临床决策使用者"),
    ("nursing-collaborator", "护理协同人员"),
    ("medication-safety-user", "药事安全人员"),
    ("diagnostic-service-user", "医技协同人员"),
    ("quality-governor", "质量与医保治理员"),
    ("compliance-auditor", "合规审计员"),
    ("integration-operator", "集成运维员"),
    ("implementation-operator", "实施运维员"),
)


def generate_password(length=14):
    if length < 12:
        raise ValueError("密码长度不得小于 12")
    characters = [
        secrets.choice(UPPER),
        secrets.choice(LOWER),
        secrets.choice(DIGIT),
        secrets.choice(SYMBOL),
    ]
    characters.extend(
        secrets.choice(ALL_PASSWORD_CHARACTERS) for _ in range(length - 4)
    )
    secrets.SystemRandom().shuffle(characters)
    return "".join(characters)


def choose_user_id(tenant_id, username, globally_used_user_ids):
    if username not in set(globally_used_user_ids):
        return username
    prefix = re.sub(r"[^a-z0-9]+", "-", tenant_id.lower()).strip("-").split("-")[0]
    return f"{prefix}-{username}"


def upsert_tenant_record(master, tenant_id, tenant_name, accounts):
    unique_accounts = {}
    for account in accounts:
        username = str(account.get("username") or "").strip()
        if not username:
            raise ValueError("租户账号缺少 username")
        unique_accounts[username] = account
    normalized_accounts = sorted(
        unique_accounts.values(), key=lambda account: account["username"]
    )
    tenant = {
        "tenantId": tenant_id,
        "name": tenant_name,
        "purpose": "演练机构定制规则与平台主源规则共同参与的全真沙盘演练",
        "accountCount": len(normalized_accounts),
        "accounts": normalized_accounts,
    }
    tenants = master.setdefault("tenants", [])
    tenants[:] = [item for item in tenants if item.get("tenantId") != tenant_id]
    tenants.append(tenant)
    tenants.sort(key=lambda item: item.get("tenantId") or "")
    return tenant


def upsert_platform_account(master, account):
    accounts = master.setdefault("accounts", [])
    accounts[:] = [
        item
        for item in accounts
        if not (
            item.get("username") == account["username"]
            and item.get("tenantId", PLATFORM_TENANT) == PLATFORM_TENANT
        )
    ]
    accounts.append(account)
    accounts.sort(key=lambda item: item.get("username") or "")
    master["accountCount"] = len(accounts)


def find_master_account(master, tenant_id, username):
    if tenant_id == PLATFORM_TENANT:
        candidates = [
            account
            for account in master.get("accounts") or []
            if account.get("username") == username
        ]
    else:
        candidates = [
            account
            for tenant in master.get("tenants") or []
            if tenant.get("tenantId") == tenant_id
            for account in tenant.get("accounts") or []
            if account.get("username") == username
        ]
    if len(candidates) > 1:
        raise ValueError(f"账号总表存在重复账号 {tenant_id}/{username}")
    return candidates[0] if candidates else None


def write_private_json_atomic(path, payload):
    destination = Path(path)
    destination.parent.mkdir(parents=True, exist_ok=True)
    descriptor, temporary_name = tempfile.mkstemp(
        dir=destination.parent, prefix=f".{destination.name}.", suffix=".tmp"
    )
    try:
        os.fchmod(descriptor, 0o600)
        with os.fdopen(descriptor, "w", encoding="utf-8") as stream:
            json.dump(payload, stream, ensure_ascii=False, indent=2)
            stream.write("\n")
            stream.flush()
            os.fsync(stream.fileno())
        os.replace(temporary_name, destination)
        os.chmod(destination, 0o600)
    except Exception:
        try:
            os.unlink(temporary_name)
        except FileNotFoundError:
            pass
        raise


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
            item.split("=", 1) for item in self.cookie.split("; ") if "=" in item
        )
        current.update(pairs)
        self.cookie = "; ".join(f"{key}={value}" for key, value in current.items())
        if "XSRF-TOKEN" in pairs:
            self.xsrf = urllib.parse.unquote(pairs["XSRF-TOKEN"])


class PilotProvisioner:

    def __init__(self, api, master, tenant_id, tenant_name):
        self.api = api.rstrip("/")
        self.master = master
        self.tenant_id = tenant_id
        self.tenant_name = tenant_name

    def call(self, method, path, session=None, body=None):
        data = json.dumps(body).encode() if body is not None else None
        request = urllib.request.Request(
            self.api + path, data=data, method=method
        )
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

    def login(self, tenant_id, username, password):
        session = Session()
        status, body, response = self.call(
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

    def require_login(self, tenant_id, username, password):
        status, body, session = self.login(tenant_id, username, password)
        if status != 200:
            raise RuntimeError(
                f"账号登录失败 {tenant_id}/{username}，HTTP {status}，"
                f"code={error_code(body)}"
            )
        return session

    def list_users(self, session):
        status, body, _ = self.call(
            "GET", "/compliance/users?page=1&size=100", session
        )
        if status != 200:
            raise RuntimeError(f"读取用户目录失败，HTTP {status}")
        return (body or {}).get("data", {}).get("items", [])

    def ensure_role(self, session, user_id, role, scope_code):
        status, body, _ = self.call(
            "POST",
            f"/compliance/users/{urllib.parse.quote(user_id)}/roles",
            session,
            {
                "roleCode": role,
                "scopeLevel": "TENANT",
                "scopeCode": scope_code,
            },
        )
        if status != 200:
            raise RuntimeError(
                f"角色分配失败 {user_id}/{role}，HTTP {status}，"
                f"code={error_code(body)}"
            )

    def finalize_password(self, tenant_id, username, initial_password):
        final_password = generate_password()
        initial_session = self.require_login(
            tenant_id, username, initial_password
        )
        status, body, _ = self.call(
            "POST",
            "/auth/change-password",
            initial_session,
            {"oldPassword": initial_password, "newPassword": final_password},
        )
        if status != 200:
            raise RuntimeError(
                f"清除首次改密状态失败 {tenant_id}/{username}，HTTP {status}，"
                f"code={error_code(body)}"
            )
        self.require_login(tenant_id, username, final_password)
        return final_password

    def existing_credential(self, tenant_id, username):
        account = find_master_account(self.master, tenant_id, username)
        password = None if account is None else account.get("password")
        if not password:
            raise RuntimeError(
                f"账号 {tenant_id}/{username} 已存在，但受控总表没有可验证凭据；"
                "拒绝猜测或静默重置"
            )
        self.require_login(tenant_id, username, password)
        return account

    def ensure_user(
        self,
        session,
        tenant_id,
        username,
        display_name,
        role,
        globally_used_user_ids,
    ):
        existing = next(
            (
                item
                for item in self.list_users(session)
                if item.get("username") == username
            ),
            None,
        )
        if existing:
            account = dict(self.existing_credential(tenant_id, username))
            user_id = existing["userId"]
            account["userId"] = user_id
            self.ensure_role(session, user_id, role, tenant_id)
            return account, "existing"

        user_id = choose_user_id(tenant_id, username, globally_used_user_ids)
        initial_password = generate_password()
        status, body, _ = self.call(
            "POST",
            "/compliance/users",
            session,
            {
                "credentialManaged": True,
                "userId": user_id,
                "username": username,
                "displayName": display_name,
                "roleCode": role,
                "initialPassword": initial_password,
            },
        )
        if status != 200:
            raise RuntimeError(
                f"创建账号失败 {tenant_id}/{username}，HTTP {status}，"
                f"code={error_code(body)}"
            )
        final_password = self.finalize_password(
            tenant_id, username, initial_password
        )
        return {
            "username": username,
            "userId": user_id,
            "role": role,
            "roleName": display_name,
            "scope": f"TENANT/{tenant_id}",
            "holder": (
                "运营方/平台治理"
                if tenant_id == PLATFORM_TENANT
                else f"{self.tenant_name}/{display_name}"
            ),
            "status": "ACTIVE",
            "loginState": "可直接登录",
            "purpose": display_name,
            "password": final_password,
            "mustChangePwd": False,
        }, "created"

    def ensure_tenant(self, platform_session):
        status, body, _ = self.call(
            "GET", "/admin/tenants", platform_session
        )
        if status != 200:
            raise RuntimeError(f"读取租户列表失败，HTTP {status}")
        exists = any(
            item.get("tenantId") == self.tenant_id
            for item in (body or {}).get("data", [])
        )
        if exists:
            organization_account = self.existing_credential(
                self.tenant_id, "organization-admin"
            )
            return organization_account, "existing"

        initial_password = generate_password()
        status, body, _ = self.call(
            "POST",
            "/admin/tenants",
            platform_session,
            {
                "tenantId": self.tenant_id,
                "tenantName": self.tenant_name,
                "adminUsername": "organization-admin",
                "adminInitialPassword": initial_password,
            },
        )
        if status != 200:
            raise RuntimeError(
                f"开通租户失败 {self.tenant_id}，HTTP {status}，"
                f"code={error_code(body)}"
            )
        final_password = self.finalize_password(
            self.tenant_id, "organization-admin", initial_password
        )
        return {
            "username": "organization-admin",
            "userId": "organization-admin",
            "role": "organization-admin",
            "roleName": "机构管理员",
            "scope": f"TENANT/{self.tenant_id}",
            "holder": f"{self.tenant_name}/机构管理员",
            "status": "ACTIVE",
            "loginState": "可直接登录",
            "purpose": "机构首个管理员；账号与演练资产发布协调",
            "password": final_password,
            "mustChangePwd": False,
        }, "created"

    def run(self):
        platform_owner = find_master_account(
            self.master, PLATFORM_TENANT, "platform-owner"
        )
        if not platform_owner or not platform_owner.get("password"):
            raise RuntimeError("受控总表缺少 platform-owner 凭据")
        platform_session = self.require_login(
            PLATFORM_TENANT, "platform-owner", platform_owner["password"]
        )

        platform_user_ids = {
            item.get("userId") for item in self.list_users(platform_session)
        }
        platform_username, platform_name, platform_role = (
            PLATFORM_GOVERNANCE_ACCOUNT
        )
        governance_account, governance_action = self.ensure_user(
            platform_session,
            PLATFORM_TENANT,
            platform_username,
            platform_name,
            platform_role,
            platform_user_ids,
        )
        governance_account["purpose"] = (
            "来源审批、用户与租户治理、系统管理；不包含 P6 放行"
        )
        upsert_platform_account(self.master, governance_account)

        organization_account, tenant_action = self.ensure_tenant(
            platform_session
        )
        organization_session = self.require_login(
            self.tenant_id,
            organization_account["username"],
            organization_account["password"],
        )

        globally_used_user_ids = {
            item.get("userId")
            for item in self.master.get("accounts") or []
            if item.get("userId")
        }
        tenant_accounts = [organization_account]
        role_actions = {}
        for role, display_name in TENANT_ROLES:
            account, action = self.ensure_user(
                organization_session,
                self.tenant_id,
                role,
                display_name,
                role,
                globally_used_user_ids,
            )
            tenant_accounts.append(account)
            role_actions[role] = action
            globally_used_user_ids.add(account["userId"])

        upsert_tenant_record(
            self.master,
            self.tenant_id,
            self.tenant_name,
            tenant_accounts,
        )
        self.master["consolidatedAt"] = time.strftime(
            "%Y-%m-%dT%H:%M:%S%z"
        )
        return {
            "tenantId": self.tenant_id,
            "platformGovernanceAdmin": governance_action,
            "tenant": tenant_action,
            "tenantAccountCount": len(tenant_accounts),
            "roleActions": role_actions,
        }


def error_code(body):
    if isinstance(body, dict):
        return body.get("code") or body.get("message") or "UNKNOWN"
    return "UNKNOWN"


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
        default=os.environ.get("SANDBOX_TENANT_ID", DEFAULT_PILOT_TENANT),
        help="演练租户 ID",
    )
    parser.add_argument(
        "--tenant-name",
        default=os.environ.get("SANDBOX_TENANT_NAME", DEFAULT_PILOT_NAME),
        help="演练租户名称",
    )
    return parser.parse_args(argv)


def main(argv=None):
    args = parse_args(argv)
    try:
        with open(args.master, encoding="utf-8") as stream:
            master = json.load(stream)
        result = PilotProvisioner(
            args.api, master, args.tenant, args.tenant_name
        ).run()
        write_private_json_atomic(args.master, master)
    except (
        OSError,
        ValueError,
        RuntimeError,
        json.JSONDecodeError,
    ) as exception:
        print(f"ERROR: {exception}", file=sys.stderr)
        return 1

    print(json.dumps(result, ensure_ascii=False, sort_keys=True))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
