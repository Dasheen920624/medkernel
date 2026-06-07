import { Tabs } from "antd";
import { useLocation, useNavigate } from "react-router-dom";

const tabRoutes = [
  { key: "/dashboard", label: "工作台" },
  { key: "/workbench/readiness-validation", label: "验收自检" },
];

export function WorkbenchTabs() {
  const location = useLocation();
  const navigate = useNavigate();
  const activeKey = tabRoutes.find((route) => location.pathname === route.key)?.key ?? "/dashboard";

  return (
    <Tabs
      data-testid="readiness-validation-tabs"
      activeKey={activeKey}
      items={tabRoutes}
      onChange={(key) => navigate(key)}
    />
  );
}
