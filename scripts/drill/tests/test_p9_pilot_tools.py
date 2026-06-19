import importlib.util
import json
import os
import tempfile
import unittest
from pathlib import Path


DRILL_DIR = Path(__file__).resolve().parents[1]


def load_script(module_name: str, filename: str):
    spec = importlib.util.spec_from_file_location(module_name, DRILL_DIR / filename)
    module = importlib.util.module_from_spec(spec)
    assert spec.loader is not None
    spec.loader.exec_module(module)
    return module


class SeedCredentialToolTest(unittest.TestCase):

    @classmethod
    def setUpClass(cls):
        cls.tool = load_script("p9_gen_seed_creds", "p9-gen-seed-creds.py")

    def test_builds_seed_credentials_without_platform_accounts(self):
        master = {
            "tenants": [
                {
                    "tenantId": "pilot-hospital",
                    "accounts": [
                        {
                            "username": "organization-admin",
                            "role": "organization-admin",
                            "password": "Org-secret-1!",
                        },
                        {
                            "username": "clinical-decision-user",
                            "role": "clinical-decision-user",
                            "password": "Clinical-secret-1!",
                        },
                    ],
                }
            ]
        }

        result = self.tool.build_seed_credentials(master, "pilot-hospital")

        self.assertEqual("pilot-hospital", result["customerTenant"]["tenantId"])
        self.assertEqual(
            "Clinical-secret-1!",
            result["roleAccounts"]["clinical-decision-user"]["password"],
        )
        self.assertEqual({}, result["platformRoleAccounts"])

    def test_rejects_missing_or_duplicate_required_accounts(self):
        missing_org = {
            "tenants": [{"tenantId": "pilot-hospital", "accounts": []}]
        }
        duplicate_role = {
            "tenants": [
                {
                    "tenantId": "pilot-hospital",
                    "accounts": [
                        {
                            "username": "organization-admin",
                            "role": "organization-admin",
                            "password": "Org-secret-1!",
                        },
                        {
                            "username": "clinical-a",
                            "role": "clinical-decision-user",
                            "password": "Clinical-secret-1!",
                        },
                        {
                            "username": "clinical-b",
                            "role": "clinical-decision-user",
                            "password": "Clinical-secret-2!",
                        },
                    ],
                }
            ]
        }

        with self.assertRaisesRegex(ValueError, "organization-admin"):
            self.tool.build_seed_credentials(missing_org, "pilot-hospital")
        with self.assertRaisesRegex(ValueError, "重复角色"):
            self.tool.build_seed_credentials(duplicate_role, "pilot-hospital")

    def test_private_json_is_0600_and_summary_never_contains_passwords(self):
        payload = {
            "customerTenant": {
                "tenantId": "pilot-hospital",
                "adminUsername": "organization-admin",
                "password": "Org-secret-1!",
            },
            "roleAccounts": {
                "clinical-decision-user": {
                    "tenantId": "pilot-hospital",
                    "username": "clinical-decision-user",
                    "password": "Clinical-secret-1!",
                }
            },
            "platformRoleAccounts": {},
        }
        with tempfile.TemporaryDirectory() as directory:
            output = Path(directory) / "seed.json"
            self.tool.write_private_json(output, payload)
            mode = os.stat(output).st_mode & 0o777
            written = json.loads(output.read_text(encoding="utf-8"))

        summary = self.tool.safe_summary(payload)

        self.assertEqual(0o600, mode)
        self.assertEqual(payload, written)
        self.assertNotIn("Org-secret-1!", summary)
        self.assertNotIn("Clinical-secret-1!", summary)


class PilotProvisioningToolTest(unittest.TestCase):

    @classmethod
    def setUpClass(cls):
        cls.tool = load_script(
            "p9_pilot_tenant_provision", "p9-pilot-tenant-provision.py"
        )

    def test_internal_user_id_is_prefixed_only_for_global_collision(self):
        self.assertEqual(
            "clinical-decision-user",
            self.tool.choose_user_id(
                "pilot-hospital", "clinical-decision-user", {"platform-owner"}
            ),
        )
        self.assertEqual(
            "pilot-quality-governor",
            self.tool.choose_user_id(
                "pilot-hospital", "quality-governor", {"quality-governor"}
            ),
        )

    def test_upsert_tenant_replaces_duplicate_accounts_by_username(self):
        master = {
            "accounts": [],
            "tenants": [
                {
                    "tenantId": "pilot-hospital",
                    "name": "旧名称",
                    "accounts": [
                        {
                            "username": "quality-governor",
                            "userId": "quality-governor",
                            "password": "old",
                        },
                        {
                            "username": "quality-governor",
                            "userId": "pilot-quality-governor",
                            "password": "duplicate",
                        },
                    ],
                }
            ],
        }
        accounts = [
            {
                "username": "quality-governor",
                "userId": "pilot-quality-governor",
                "role": "quality-governor",
                "password": "new",
            },
            {
                "username": "clinical-decision-user",
                "userId": "clinical-decision-user",
                "role": "clinical-decision-user",
                "password": "clinical",
            },
        ]

        self.tool.upsert_tenant_record(
            master, "pilot-hospital", "演练试点医院", accounts
        )

        tenant = master["tenants"][0]
        self.assertEqual("演练试点医院", tenant["name"])
        self.assertEqual(2, tenant["accountCount"])
        self.assertEqual(
            ["clinical-decision-user", "quality-governor"],
            sorted(account["username"] for account in tenant["accounts"]),
        )


class PilotVerificationToolTest(unittest.TestCase):

    @classmethod
    def setUpClass(cls):
        cls.tool = load_script("p9_pilot_verify", "p9-pilot-verify.py")

    def complete_state(self):
        roles = [
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
        ]
        return {
            "tenantPresent": True,
            "platformGovernanceAdminPresent": True,
            "accounts": {
                role: {
                    "login": True,
                    "roles": [role],
                    "permissions": (
                        ["sandbox.run"] if role == "clinical-decision-user" else []
                    ),
                }
                for role in roles
            },
        }

    def test_complete_state_passes_with_expected_sandbox_actor(self):
        report = self.tool.evaluate_state(self.complete_state())

        self.assertTrue(report["passed"])
        self.assertEqual([], report["failures"])

    def test_missing_account_or_wrong_permission_fails_closed(self):
        state = self.complete_state()
        del state["accounts"]["nursing-collaborator"]
        state["accounts"]["clinical-decision-user"]["permissions"] = []
        state["accounts"]["quality-governor"]["permissions"] = ["sandbox.run"]

        report = self.tool.evaluate_state(state)

        self.assertFalse(report["passed"])
        self.assertTrue(
            any("nursing-collaborator" in failure for failure in report["failures"])
        )
        self.assertTrue(
            any("clinical-decision-user" in failure for failure in report["failures"])
        )
        self.assertTrue(
            any("quality-governor" in failure for failure in report["failures"])
        )


if __name__ == "__main__":
    unittest.main()
