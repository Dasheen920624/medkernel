import { fireEvent, render, screen } from "@testing-library/react";
import { MemoryRouter } from "react-router-dom";
import { describe, expect, it, vi } from "vitest";
import { CommandPalette } from "./CommandPalette";
import type { MenuSection } from "@/shared/config/menu";

const navigate = vi.fn();

vi.mock("react-router-dom", async () => {
  const actual = await vi.importActual<Record<string, unknown>>("react-router-dom");
  return {
    ...actual,
    useNavigate: () => navigate,
  };
});

const allowedSections: MenuSection[] = [
  {
    key: "pilot-setup",
    label: "试点准备",
    items: [{ key: "terminology-mapping", label: "字典映射", path: "/terminology/mapping" }],
  },
];

describe("CommandPalette", () => {
  it("only renders the authorized menu commands passed by the layout", () => {
    render(
      <MemoryRouter future={{ v7_startTransition: true, v7_relativeSplatPath: true }}>
        <CommandPalette open onClose={vi.fn()} sections={allowedSections} />
      </MemoryRouter>,
    );

    expect(screen.getByText("字典映射")).toBeInTheDocument();
    expect(screen.queryByText("开发者控制台")).toBeNull();
    expect(screen.queryByText("/terminology/mapping")).toBeNull();
  });

  it("navigates to the selected menu command", () => {
    const onClose = vi.fn();
    render(
      <MemoryRouter future={{ v7_startTransition: true, v7_relativeSplatPath: true }}>
        <CommandPalette open onClose={onClose} sections={allowedSections} />
      </MemoryRouter>,
    );

    fireEvent.click(screen.getByText("字典映射"));

    expect(navigate).toHaveBeenCalledWith("/terminology/mapping");
    expect(onClose).toHaveBeenCalled();
  });
});
