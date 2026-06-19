#!/usr/bin/env python3
"""从受控账号总表生成沙盘铺底所需的最小凭据文件。"""

import argparse
import json
import os
import sys
from pathlib import Path


DEFAULT_MASTER = "/zoesoft/medkernel/conf/medkernel-accounts.json"
DEFAULT_OUTPUT = "/tmp/pilot-seed-creds.json"
DEFAULT_TENANT = "pilot-hospital"


def require_single(items, predicate, label):
    matches = [item for item in items if predicate(item)]
    if len(matches) != 1:
        raise ValueError(f"{label} 必须且只能存在一条，实际 {len(matches)} 条")
    return matches[0]


def build_seed_credentials(master, tenant_id):
    tenants = master.get("tenants") or []
    tenant = require_single(
        tenants,
        lambda item: item.get("tenantId") == tenant_id,
        f"租户 {tenant_id}",
    )
    accounts = tenant.get("accounts") or []
    organization_admin = require_single(
        accounts,
        lambda item: item.get("role") == "organization-admin",
        "organization-admin",
    )

    role_accounts = {}
    for account in accounts:
        role = str(account.get("role") or "").strip()
        username = str(account.get("username") or "").strip()
        password = str(account.get("password") or "")
        if not role or not username or not password:
            raise ValueError("试点账号缺少 role、username 或 password")
        if role in role_accounts:
            raise ValueError(f"重复角色 {role}")
        role_accounts[role] = {
            "username": username,
            "password": password,
            "tenantId": tenant_id,
        }

    return {
        "customerTenant": {
            "tenantId": tenant_id,
            "adminUsername": organization_admin["username"],
            "password": organization_admin["password"],
        },
        "roleAccounts": role_accounts,
        "platformRoleAccounts": {},
    }


def write_private_json(path, payload):
    output = Path(path)
    output.parent.mkdir(parents=True, exist_ok=True)
    fd = os.open(output, os.O_WRONLY | os.O_CREAT | os.O_TRUNC, 0o600)
    with os.fdopen(fd, "w", encoding="utf-8") as stream:
        json.dump(payload, stream, ensure_ascii=False, indent=2)
        stream.write("\n")
    os.chmod(output, 0o600)


def safe_summary(payload):
    summary = {
        "tenantId": payload["customerTenant"]["tenantId"],
        "adminUsername": payload["customerTenant"]["adminUsername"],
        "roles": sorted(payload["roleAccounts"]),
        "platformRoleCount": len(payload["platformRoleAccounts"]),
    }
    return json.dumps(summary, ensure_ascii=False, sort_keys=True)


def parse_args(argv=None):
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument(
        "--master",
        default=os.environ.get("MEDKERNEL_ACCOUNT_MASTER", DEFAULT_MASTER),
        help="受控账号总表路径",
    )
    parser.add_argument(
        "--output",
        default=os.environ.get("SANDBOX_SEED_CREDENTIALS", DEFAULT_OUTPUT),
        help="最小沙盘凭据输出路径",
    )
    parser.add_argument(
        "--tenant",
        default=os.environ.get("SANDBOX_TENANT_ID", DEFAULT_TENANT),
        help="演练租户 ID",
    )
    return parser.parse_args(argv)


def main(argv=None):
    args = parse_args(argv)
    try:
        with open(args.master, encoding="utf-8") as stream:
            master = json.load(stream)
        payload = build_seed_credentials(master, args.tenant)
        write_private_json(args.output, payload)
    except (OSError, ValueError, json.JSONDecodeError) as exception:
        print(f"ERROR: {exception}", file=sys.stderr)
        return 1

    print(f"WROTE {args.output} {safe_summary(payload)}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
