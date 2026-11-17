import { expect, test } from "bun:test";
import { levelRules, schema } from "./text";

function runStyle(attrs: Record<string, unknown>): string {
  const look = schema.marks.textStyle;
  const [, shown] = look?.spec.toDOM?.(look.create(attrs), true) as [string, { style: string }];
  return shown.style;
}

test("the colour and font of a run cannot add CSS of their own", () => {
  expect(runStyle({ color: "C00000", size: "2400", font: "Segoe UI" })).toBe(
    'color:#C00000;font-size:32px;font-family:"Segoe UI", Carlito, sans-serif',
  );
  expect(runStyle({ font: "+mj-lt" })).toBe("font-size:24px");
  expect(
    runStyle({
      color: "red;display:none",
      font: 'Arial\n;display:none;x:"',
    }),
  ).toBe('font-size:24px;font-family:"Arial ;display:none;x:\\"", Carlito, sans-serif');
});

test("the colour and font of a text level cannot add CSS rules", () => {
  const rules = levelRules(
    {
      levels: [
        { color: "scheme:tx1", font: "+mn-lt" },
        { color: "000}body{display:none", font: 'x"}body{display:none}' },
        { font: "constructor" },
      ],
      anchor: null,
      scale: null,
    },
    { dk1: "1F1F1F", "+mn-lt": "Aptos" },
  );
  expect(rules.split("\n")).toEqual([
    '.slides-text p[data-level="0"]{font:24px "Aptos", Carlito, sans-serif;line-height:1.2;' +
      "margin:0 0 0 0px;text-align:left;color:#1F1F1F}",
    '.slides-text p[data-level="1"]{font:24px "x\\"}body{display:none}", Carlito, sans-serif;' +
      "line-height:1.2;margin:0 0 0 24px;text-align:left;color:inherit}",
    '.slides-text p[data-level="2"]{font:24px "constructor", Carlito, sans-serif;' +
      "line-height:1.2;margin:0 0 0 48px;text-align:left;color:inherit}",
  ]);
});
