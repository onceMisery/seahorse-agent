import { render, screen } from "@testing-library/react";
import { describe, expect, it } from "vitest";
import { A2UILiteRenderer } from "@/components/a2ui-lite/A2UILiteRenderer";
import type { A2UILiteSurface } from "@/components/a2ui-lite/a2uiTypes";

const surface: A2UILiteSurface = {
  version: "seahorse-a2ui-lite/v1",
  title: "Research summary",
  root: {
    id: "root",
    type: "callout",
    props: { title: "结论", body: "可以升级为 Agent Workbench。" }
  }
};

describe("A2UILiteRenderer", () => {
  it("renders whitelisted components", () => {
    render(<A2UILiteRenderer surface={surface} onAction={() => undefined} />);
    expect(screen.getByText("结论")).toBeInTheDocument();
    expect(screen.getByText("可以升级为 Agent Workbench。")).toBeInTheDocument();
  });
});
