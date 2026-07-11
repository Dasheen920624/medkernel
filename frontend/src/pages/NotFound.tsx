import { Result, Button, Typography } from "antd";
import { useNavigate } from "react-router-dom";

export default function NotFound() {
  const navigate = useNavigate();
  return (
    <Result
      status="404"
      title={<Typography.Title level={2}>未找到页面</Typography.Title>}
      subTitle="当前地址没有对应的业务页面，请返回工作台或通过菜单进入已授权功能。"
      extra={
        <Button type="primary" onClick={() => navigate("/dashboard")}>
          返回工作台
        </Button>
      }
    />
  );
}
