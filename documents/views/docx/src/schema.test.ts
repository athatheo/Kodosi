import { expect, test } from "bun:test";
import { getSchema } from "@tiptap/core";
import spec from "../../../crates/docx/schema.json";
import { extensions } from "./schema";
import { styleSheet } from "./styles";

const schema = getSchema(extensions);

function runStyle(attrs: Record<string, unknown>): string {
  const look = schema.marks.textStyle;
  const [, shown] = look?.spec.toDOM?.(look.create(attrs), true) as [string, { style: string }];
  return shown.style;
}

test("the editor has the server's nodes in the server's order", () => {
  expect(Object.keys(schema.nodes)).toEqual(spec.nodes.map((node) => node.name));
});

test("the editor has the server's marks in the server's order", () => {
  expect(Object.keys(schema.marks)).toEqual(spec.marks.map((mark) => mark.name));
});

test("each node has the same content rule and attributes on both sides", () => {
  for (const node of spec.nodes) {
    const type = schema.nodes[node.name];
    expect(type?.spec.content ?? "", node.name).toBe(node.content ?? "");
    expect(Object.keys(type?.spec.attrs ?? {}).sort(), node.name).toEqual(
      Object.keys(node.attrs ?? {}).sort(),
    );
    expect(type?.spec.group ?? "", node.name).toBe(node.group ?? "");
    expect(Boolean(type?.spec.inline), node.name).toBe(Boolean(node.inline));
  }
});

test("each mark has the same attributes on both sides", () => {
  for (const mark of spec.marks) {
    expect(Object.keys(schema.marks[mark.name]?.spec.attrs ?? {}).sort(), mark.name).toEqual(
      Object.keys(mark.attrs ?? {}).sort(),
    );
  }
});

test("the colour and font of a run cannot add CSS of their own", () => {
  expect(runStyle({ color: "C00000", size: "24", font: "Times New Roman" })).toBe(
    'color:#C00000;font-size:12pt;font-family:"Times New Roman"',
  );
  expect(
    runStyle({
      color: "red;display:none",
      font: 'Arial\n;display:none;x:"',
    }),
  ).toBe('font-family:"Arial ;display:none;x:\\""');
});

test("the colour and font of a style cannot add CSS rules", () => {
  const sheet = styleSheet({
    styles: {
      defaults: { color: "000}body{display:none", font: 'x"}body{display:none}' },
      defaultParagraph: null,
      styles: {},
    },
    numbering: null,
    page: null,
  });
  expect(sheet.split("\n").slice(1)).toEqual([
    '.docx-page .tiptap{font-family:"x\\"}body{display:none}",sans-serif}',
    '.docx-page p{font-family:"x\\"}body{display:none}",sans-serif}',
  ]);
});
