import { CalendarOutlined } from "@ant-design/icons";
import { Form, Input } from "antd";

import { isClinicalDateTimeInputValue } from "@/shared/lib/dateTimeText";

export function RectificationDueAtField() {
  return (
    <Form.Item
      name="dueAt"
      label="整改截止时间"
      extra="按院内时间填写；提交后统一换算为平台可追溯时间。"
      rules={[
        { required: true, message: "请填写整改截止时间" },
        {
          validator: (_, value?: string) =>
            !value || isClinicalDateTimeInputValue(value)
              ? Promise.resolve()
              : Promise.reject(new Error("请使用 2026年07月15日 08:30 格式")),
        },
      ]}
    >
      <Input placeholder="例如 2026年07月15日 08:30" suffix={<CalendarOutlined />} />
    </Form.Item>
  );
}
