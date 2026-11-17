import { Fragment, type Node as ProseNode, Slice } from "@tiptap/pm/model";
import { Plugin, PluginKey } from "@tiptap/pm/state";
import { ReplaceAroundStep, ReplaceStep } from "@tiptap/pm/transform";
import { PICTURE_STARTS } from "./generated/writable";
import { cellSpan } from "./schema";

/** What a document can take from a paste. */
export interface Holding {
  /** Each piece of the file that the document has held, which it can still write. */
  held: ReadonlySet<string>;
  /** The pieces in the document now, which a paste of the same piece would copy. */
  present: ReadonlySet<string>;
  /** Whether the document has the bytes of a picture, in a kind that the server writes. */
  picture: (asset: string) => boolean;
}

/** The server writes a new picture into the file only in a kind that it knows. */
export function writablePicture(bytes: Uint8Array | undefined): boolean {
  return PICTURE_STARTS.some((start) => start.every((byte, index) => bytes?.[index] === byte));
}

function piecesIn(fragment: Fragment, into = new Set<string>()): Set<string> {
  fragment.descendants((node) => {
    for (const key of [node.attrs.raw, node.attrs.props]) {
      if (typeof key === "string") into.add(key);
    }
  });
  return into;
}

/**
 * The blocks around a paste come from the clipboard as they were, with no parse rule between: a
 * pasted block takes a new id, and the properties of the file only where the document holds them.
 */
function fittedAttrs(node: ProseNode, holding: Holding): ProseNode["attrs"] {
  const { id, props, span } = node.attrs;
  const keepsProps = typeof props !== "string" || holding.held.has(props);
  const fits = (id === undefined || id === null) && keepsProps;
  if (fits && (span === undefined || cellSpan(span) === span)) return node.attrs;
  return {
    ...node.attrs,
    ...(id === undefined ? {} : { id: null }),
    ...(keepsProps ? {} : { props: null }),
    ...(span === undefined ? {} : { span: cellSpan(span) }),
  };
}

/**
 * A piece of the file that the editor keeps whole moves with a cut and a paste. A copy of it would
 * repeat ids that the file allows once, and a piece from another file is not in this one, so they
 * paste as the text that they show; a picture pastes as a new picture where the document has its
 * bytes. Anything else would leave the document unable to save.
 */
export function fitPaste(slice: Slice, holding: Holding): Slice {
  return new Slice(fitted(slice.content, holding), slice.openStart, slice.openEnd);
}

function fitted(fragment: Fragment, holding: Holding): Fragment {
  const kept: ProseNode[] = [];
  fragment.forEach((node) => {
    const raw = node.attrs.raw;
    const moves = typeof raw === "string" && holding.held.has(raw) && !holding.present.has(raw);
    switch (node.type.name) {
      case "opaque":
        if (moves) kept.push(node);
        return;
      case "inlineOpaque": {
        const shown = String(node.attrs.text).replace(/\s+/g, " ");
        if (moves) kept.push(node);
        else if (shown.trim()) kept.push(node.type.schema.text(shown, node.marks));
        return;
      }
      case "image": {
        const asset = node.attrs.asset;
        if (moves) kept.push(node);
        else if (typeof asset === "string" && holding.picture(asset)) {
          kept.push(node.type.create({ ...node.attrs, raw: null }, null, node.marks));
        }
        return;
      }
    }
    const attrs = fittedAttrs(node, holding);
    if (node.isLeaf) {
      kept.push(attrs === node.attrs ? node : node.type.create(attrs, null, node.marks));
      return;
    }
    const content = fitted(node.content, holding);
    const fits =
      attrs === node.attrs && content.eq(node.content)
        ? node
        : node.type.createAndFill(attrs, content, node.marks);
    if (fits) kept.push(fits);
  });
  return Fragment.from(kept);
}

const HELD = new PluginKey<Set<string>>("held pieces");

export function fitsPastes(picture: (asset: string) => boolean): Plugin<Set<string>> {
  return new Plugin({
    key: HELD,
    state: {
      init: (_, state) => piecesIn(state.doc.content),
      apply(transaction, held) {
        for (const step of transaction.steps) {
          if (step instanceof ReplaceStep || step instanceof ReplaceAroundStep) {
            piecesIn(step.slice.content, held);
          }
        }
        return held;
      },
    },
    props: {
      transformPasted(slice, view) {
        // A drag inside the editor moves its pieces, which stay in the document until the drop.
        if (view.dragging?.move) return slice;
        return fitPaste(slice, {
          held: HELD.getState(view.state) ?? new Set(),
          present: piecesIn(view.state.doc.content),
          picture,
        });
      },
    },
  });
}
