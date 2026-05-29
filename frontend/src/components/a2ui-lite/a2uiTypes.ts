export type A2UILiteComponentType = "metric" | "table" | "source_grid" | "callout" | "action_row";

export interface A2UILiteNode {
  id: string;
  type: A2UILiteComponentType;
  props: Record<string, unknown>;
  children?: A2UILiteNode[];
}

export interface A2UILiteSurface {
  version: "seahorse-a2ui-lite/v1";
  title?: string;
  root: A2UILiteNode;
}

export interface A2UILiteAction {
  type: "open_artifact" | "select_source" | "copy_text" | "set_prompt_draft";
  payload: Record<string, unknown>;
}
