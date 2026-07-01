import { Form, Input } from "antd";

export function RectificationDueAtField() {
  return (
    <Form.Item
      name="dueAt"
      label="整改截止时间"
      extra="按院内时间选择；提交后统一换算为平台可追溯时间。"
      rules={[{ required: true, message: "请选择整改截止时间" }]}
    >
      <Input type="datetime-local" step={60} />
    </Form.Item>
  );
}
