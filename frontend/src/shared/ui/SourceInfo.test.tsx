import { render, screen } from "@testing-library/react";

import { SourceInfo } from "./SourceInfo";

describe("SourceInfo", () => {
  it("展示真实来源、版本、分级和审核信息", () => {
    render(
      <SourceInfo
        sourceDocumentId={12}
        sourceVersionId={34}
        authorityLevel="A_GUIDELINE"
        anchors="第 3 章 2.1 节"
        reviewedBy="reviewer-01"
        reviewedAt="2026-06-06T08:30:00Z"
      />,
    );

    expect(screen.getByText("来源文档 #12")).toBeInTheDocument();
    expect(screen.getByText("来源版本 #34")).toBeInTheDocument();
    expect(screen.getByText("A_GUIDELINE")).toBeInTheDocument();
    expect(screen.getByText("第 3 章 2.1 节")).toBeInTheDocument();
    expect(screen.getByText(/reviewer-01/)).toBeInTheDocument();
  });

  it("缺少来源时明确显示未登记，而不是伪造来源", () => {
    render(<SourceInfo />);

    expect(screen.getByText("来源未登记")).toBeInTheDocument();
  });
});
