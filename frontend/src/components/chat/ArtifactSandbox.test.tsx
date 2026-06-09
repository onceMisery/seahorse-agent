import { fireEvent, render, screen } from "@testing-library/react";
import { describe, expect, it, vi } from "vitest";

import { ArtifactSandbox } from "@/components/chat/ArtifactSandbox";
import type { ArtifactBlock } from "@/types";

const createObjectURL = vi.fn(() => "blob:artifact");
const revokeObjectURL = vi.fn();

Object.defineProperty(URL, "createObjectURL", {
  configurable: true,
  value: createObjectURL
});

Object.defineProperty(URL, "revokeObjectURL", {
  configurable: true,
  value: revokeObjectURL
});

describe("ArtifactSandbox", () => {
  it("renders html artifacts as a web preview and keeps source available", () => {
    const artifact: ArtifactBlock = {
      id: "artifact-html",
      language: "html",
      title: "项目介绍 Web 预览.html",
      code: "<section><h1>Redis</h1></section>",
      isComplete: true
    };

    render(<ArtifactSandbox artifact={artifact} />);

    const frame = screen.getByTitle("项目介绍 Web 预览.html");
    expect(frame).toBeInTheDocument();
    expect(frame).toHaveAttribute("srcDoc", artifact.code);

    fireEvent.click(screen.getByRole("button", { name: "查看 HTML 源码" }));
    expect(screen.getByText(artifact.code)).toBeInTheDocument();
  });
});
