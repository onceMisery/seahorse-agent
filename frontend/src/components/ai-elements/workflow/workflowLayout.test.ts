import { describe, expect, it } from "vitest";

import { workflowStepsToGraph } from "./workflowLayout";

describe("workflowStepsToGraph", () => {
  it("sorts steps and creates sequential workflow edges", () => {
    const graph = workflowStepsToGraph([
      {
        stepId: "step-2",
        stepNo: 2,
        stepType: "TOOL",
        status: "RUNNING",
        summary: "Search sources"
      },
      {
        stepId: "step-1",
        stepNo: 1,
        stepType: "PLAN",
        status: "COMPLETED",
        summary: "Plan task"
      }
    ]);

    expect(graph.nodes.map((node) => node.id)).toEqual(["step-1", "step-2"]);
    expect(graph.nodes[0].data.label).toBe("Plan task");
    expect(graph.edges).toEqual([
      expect.objectContaining({
        source: "step-1",
        target: "step-2",
        animated: true
      })
    ]);
  });
});
