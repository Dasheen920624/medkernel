import { useState } from "react";
import { Form, Select } from "antd";

import { useOrgUnits, useOrgUsers } from "@/shared/api/hooks";

export function RectificationAssignmentFields() {
  const [departmentSearch, setDepartmentSearch] = useState("");
  const [userSearch, setUserSearch] = useState("");
  const departmentsQuery = useOrgUnits({
    page: 1,
    size: 50,
    sort: "name,asc",
    keyword: departmentSearch || undefined,
    level: "DEPARTMENT",
    status: "ACTIVE",
  });
  const usersQuery = useOrgUsers({ page: 1, size: 50, keyword: userSearch || undefined });

  const departmentOptions = (departmentsQuery.data?.items ?? [])
    .filter((unit) => unit.level === "DEPARTMENT" && unit.status !== "ARCHIVED")
    .map((unit) => ({
      value: unit.id ?? unit.code,
      label: unit.name,
    }));
  const userOptions = (usersQuery.data?.items ?? []).map((user) => ({
    value: user.userId,
    label: user.displayName,
  }));

  return (
    <>
      <Form.Item
        name="responsibleDepartmentId"
        label="责任科室"
        rules={[{ required: true, message: "请选择责任科室" }]}
      >
        <Select
          showSearch
          filterOption={false}
          onSearch={setDepartmentSearch}
          placeholder="选择责任科室"
          options={departmentOptions}
          loading={departmentsQuery.isLoading}
          notFoundContent="暂无可选科室"
        />
      </Form.Item>
      <Form.Item name="assigneeUserId" label="责任人">
        <Select
          allowClear
          showSearch
          filterOption={false}
          onSearch={setUserSearch}
          placeholder="可选责任人"
          options={userOptions}
          loading={usersQuery.isLoading}
          notFoundContent="暂无可选用户"
        />
      </Form.Item>
    </>
  );
}
