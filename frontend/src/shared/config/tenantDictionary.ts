export const platformTenantId = "t-1";
export const defaultTenantId = platformTenantId;
export const platformTenantLabel = "平台主租户（唯一内置）";
export const platformTenantDescription = "全局医疗知识和标准包的源租户；客户租户进入工作台后分配。";

export const loginTenantOptions = [
  {
    label: platformTenantLabel,
    value: platformTenantId,
  },
];

export const bootstrapTenantOptions = [
  {
    label: platformTenantLabel,
    value: platformTenantId,
  },
];
