const HEX = /^#?([0-9a-f]{3,4}|[0-9a-f]{6}|[0-9a-f]{8})$/i;
const NAMED = /^[a-z]+$/i;
const FUNCTION = /^(?:rgba?|hsla?)\((?:[\d\s.,%/+-]|deg|g?rad|turn|none)*\)$/i;

/**
 * A colour from a file or from a person, as CSS: hex with or without its #, a name, rgb() or
 * hsl(). Anything else could end its declaration and add more CSS, so it has no colour.
 */
export function cssColor(value: unknown): string | undefined {
  if (typeof value !== "string") return undefined;
  const hex = HEX.exec(value)?.[1];
  if (hex) return `#${hex}`;
  return NAMED.test(value) || FUNCTION.test(value) ? value : undefined;
}

/** A CSS string of any text, which no quote, backslash or line in the text can end early. */
export function cssString(text: string): string {
  return `"${text.replace(/["\\]/g, "\\$&").replace(/[\n\r\f]/g, " ")}"`;
}
