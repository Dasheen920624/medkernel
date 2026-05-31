import { PageShell } from "@/shared/ui/PageShell";

export default function IdentityBinding() {
  return (
    <PageShell
      title="统一身份绑定与登录设置"
      description="CAS、LDAP、OIDC 与 SAML 身份源"
      state="empty"
      stateProps={{
        title: "身份源配置接口尚未接入",
        description:
          "当前版本不展示本地身份源配置样例；待 AUTH-01/AUTH-03 接入真实委托身份源 API 后，再启用保存、测试和启停。",
      }}
    >
      <></>
    </PageShell>
  );
}
