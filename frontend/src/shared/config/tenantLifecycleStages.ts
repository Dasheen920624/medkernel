export const tenantLifecycleStages = [
  { key: "PREPARATION", title: "系统准备", description: "对接就绪与数据导入" },
  { key: "PILOT", title: "临床试点", description: "科室试运行与质控监测" },
  { key: "ACCEPTANCE", title: "上线验收", description: "质量指标达成与物理验收" },
  { key: "PROMOTION", title: "全院推广", description: "全科室推广与流程闭环" },
  { key: "RUNNING", title: "常态运行", description: "长期稳定常态化生产运行" },
  { key: "RENEWAL", title: "年度续约", description: "服务成效评估与续约演进" },
] as const;
