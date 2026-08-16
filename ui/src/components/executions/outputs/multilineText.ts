/**
 * The value to render as plain text rather than as JSON, or `undefined` to leave it to the
 * tree and raw views.
 *
 * A task output holding a log or a stack trace is a string with newlines in it, and both other
 * views destroy them: the tree shows one flattened preview, and JSON escapes them to a literal
 * `\n`. Only strings that actually span lines are taken over — a single-line value still gets
 * the tree, where its structure and the expression paths are the useful part.
 */
export function multilineTextValue(value: unknown): string | undefined {
    return typeof value === "string" && value.includes("\n") ? value : undefined
}
