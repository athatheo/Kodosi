import { expect, test } from "bun:test";
import { type Change, Fitting, type Shape, type Slide, touches } from "./deck";

function shape(id: string): Shape {
  return {
    id,
    name: "Box",
    kind: "shape",
    placeholder: null,
    x: 0,
    y: 0,
    width: 1,
    height: 1,
    rotation: 0,
    geometry: "rect",
    fill: null,
    line: null,
    asset: null,
    link: null,
    text: null,
    look: null,
    order: 0,
    source: null,
  };
}

function slide(id: string, ...shapes: string[]): Slide {
  return { id, position: 0, shapes: shapes.map(shape), behind: [], background: null };
}

const moved = (slide: string, shape: string): Change => ({
  kind: "shape",
  slide,
  shape,
  fields: { x: 1 },
});

test("the changes that wait fit the deck as the document holds it, in their order", () => {
  const fitting = new Fitting([slide("256", "2", "3")]);
  const waiting: Change[] = [
    { kind: "addShape", slide: "256", shape: shape("2") },
    { kind: "addShape", slide: "256", shape: shape("9") },
    moved("256", "9"),
    { kind: "removeShape", slide: "256", shape: "3" },
    { kind: "removeShape", slide: "256", shape: "3" },
    { kind: "addSlide", slide: "300", position: 1 },
    { kind: "addShape", slide: "300", shape: shape("5") },
    { kind: "addSlide", slide: "256", position: 0 },
  ];
  expect(waiting.flatMap((change) => fitting.fit(change) ?? [])).toEqual([
    waiting[1],
    waiting[2],
    waiting[3],
    waiting[5],
    waiting[6],
  ] as Change[]);
  expect(fitting.lost).toBe(false);
});

test("an edit of what is gone goes, and the view hears of it", () => {
  const fitting = new Fitting([slide("256", "2")]);
  expect(fitting.fit({ kind: "removeShape", slide: "256", shape: "2" })).not.toBeNull();
  expect(fitting.fit(moved("256", "2"))).toBeNull();
  expect(fitting.lost).toBe(true);
  const elsewhere = new Fitting([slide("256", "2")]);
  expect(elsewhere.fit({ kind: "moveSlide", slide: "400", position: 2 })).toBeNull();
  expect(elsewhere.lost).toBe(true);
});

test("a removal of a slide or a shape touches the changes that name it", () => {
  expect(touches(moved("256", "2"), "256")).toBe(true);
  expect(touches(moved("256", "2"), "256", "2")).toBe(true);
  expect(touches(moved("256", "2"), "256", "3")).toBe(false);
  expect(touches(moved("256", "2"), "257")).toBe(false);
  expect(touches({ kind: "addShape", slide: "256", shape: shape("2") }, "256", "2")).toBe(true);
  expect(touches({ kind: "moveSlide", slide: "256", position: 1 }, "256", "2")).toBe(false);
});
