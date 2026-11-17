import { expect, test } from "bun:test";
import { getSchema } from "@tiptap/core";
import { Fragment, type Node as ProseNode, Slice } from "@tiptap/pm/model";
import { fitPaste, type Holding, writablePicture } from "./paste";
import { extensions } from "./schema";

const schema = getSchema(extensions);
const { paragraph, inlineOpaque, image, opaque, table, tableRow, tableCell } = schema.nodes;
const ASSET = "ab".repeat(32);
const PARAGRAPH = paragraph?.create().attrs;

function pasted(blocks: ProseNode[], holding: Partial<Holding>): unknown {
  const slice = new Slice(Fragment.from(blocks), 0, 0);
  const nothing = { held: new Set<string>(), present: new Set<string>(), picture: () => false };
  return fitPaste(slice, { ...nothing, ...holding }).content.toJSON();
}

function inParagraph(...content: ProseNode[]): ProseNode[] {
  return [paragraph?.create(null, content)].filter((node) => node !== undefined);
}

test("a cut and a paste moves a piece of the file with its key", () => {
  const footnote = inlineOpaque?.create({ raw: "f1", text: "" });
  if (!footnote) throw new Error("no piece");
  expect(pasted(inParagraph(footnote), { held: new Set(["f1"]) })).toEqual([
    {
      type: "paragraph",
      attrs: PARAGRAPH,
      content: [{ type: "inlineOpaque", attrs: { raw: "f1", text: "" } }],
    },
  ]);
});

test("a copy of a piece, or one from another file, pastes as the text that it shows", () => {
  const bold = schema.marks.bold?.create();
  const field = inlineOpaque?.create({ raw: "f2", text: "Page\f 3" }, null, bold ? [bold] : []);
  const bookmark = inlineOpaque?.create({ raw: "f3", text: "" });
  if (!field || !bookmark) throw new Error("no pieces");
  const text = [
    {
      type: "paragraph",
      attrs: PARAGRAPH,
      content: [{ type: "text", marks: [{ type: "bold" }], text: "Page 3" }],
    },
  ];
  const copied = { held: new Set(["f2", "f3"]), present: new Set(["f2", "f3"]) };
  expect(pasted(inParagraph(field, bookmark), copied)).toEqual(text);
  expect(pasted(inParagraph(field, bookmark), {})).toEqual(text);
});

test("a picture pastes as a new picture where the document has its bytes", () => {
  const picture = image?.create({ asset: ASSET, width: 96, height: 48, raw: "p1" });
  if (!picture) throw new Error("no picture");
  const copied = { held: new Set(["p1"]), present: new Set(["p1"]) };
  expect(pasted(inParagraph(picture), { ...copied, picture: (asset) => asset === ASSET })).toEqual([
    {
      type: "paragraph",
      attrs: PARAGRAPH,
      content: [{ type: "image", attrs: { asset: ASSET, width: 96, height: 48, raw: null } }],
    },
  ]);
  expect(pasted(inParagraph(picture), {})).toEqual([{ type: "paragraph", attrs: PARAGRAPH }]);
});

test("a block that the editor keeps whole pastes only when it moves", () => {
  const block = opaque?.create({ raw: "t1", text: "" });
  const cell = block && tableCell?.create(null, [block]);
  const row = cell && tableRow?.create(null, [cell]);
  const grid = row && table?.create(null, [row]);
  if (!block || !grid) throw new Error("no block");
  expect(pasted([block], { held: new Set(["t1"]) })).toEqual([block.toJSON()]);
  expect(pasted([grid], { held: new Set(["t1"]), present: new Set(["t1"]) })).toEqual([
    {
      type: "table",
      attrs: { id: null, props: null },
      content: [
        {
          type: "tableRow",
          attrs: { props: null },
          content: [
            {
              type: "tableCell",
              attrs: { props: null, span: 1, merged: false },
              content: [{ type: "paragraph", attrs: PARAGRAPH }],
            },
          ],
        },
      ],
    },
  ]);
});

test("only a PNG, a JPEG or a GIF can paste as a new picture", () => {
  const png = [0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a];
  const jpeg = [0xff, 0xd8, 0xff, 0xe0];
  const gif = [0x47, 0x49, 0x46, 0x38, 0x39, 0x61];
  const emf = [0x01, 0x00, 0x00, 0x00, 0x6c, 0x00];
  expect([png, jpeg, gif, emf].map((bytes) => writablePicture(new Uint8Array(bytes)))).toEqual([
    true,
    true,
    true,
    false,
  ]);
  expect(writablePicture(undefined)).toBe(false);
});

test("the blocks around a paste take a new id and only the properties that the document holds", () => {
  const cell = tableCell?.create({ span: 5000 }, paragraph?.create({ id: "p1", props: "spacing" }));
  const row = cell && tableRow?.create({ props: "height" }, [cell, cell]);
  const grid = row && table?.create({ id: "t1", props: "borders" }, row);
  if (!grid) throw new Error("no table");
  expect(pasted([grid], { held: new Set(["borders"]) })).toMatchObject([
    {
      type: "table",
      attrs: { id: null, props: "borders" },
      content: [
        {
          type: "tableRow",
          attrs: { props: null },
          content: [
            {
              attrs: { span: 1000 },
              content: [{ type: "paragraph", attrs: { id: null, props: null } }],
            },
            {},
          ],
        },
      ],
    },
  ]);
});
