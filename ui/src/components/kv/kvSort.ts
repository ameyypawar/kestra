export interface TableSort {
    prop: string;
    order: "ascending" | "descending";
}

export const DEFAULT_KV_SORT: TableSort = {prop: "key", order: "ascending"}

/**
 * The table's sort as the URL carries it, in the shape the table wants back.
 *
 * `route.query.sort` is `string | (string | null)[]` — a repeated `?sort=` gives an array — and
 * nothing stops a hand-edited URL carrying a direction the table has no notion of, so anything
 * that is not `field:asc` or `field:desc` falls back to the default instead of being coerced
 * into one.
 */
export function parseSortQuery(raw: unknown): TableSort {
    const value = Array.isArray(raw) ? raw[raw.length - 1] : raw

    if (typeof value !== "string") return DEFAULT_KV_SORT

    const [prop, order] = value.split(":")
    if (!prop || (order !== "asc" && order !== "desc")) return DEFAULT_KV_SORT

    return {prop, order: order === "desc" ? "descending" : "ascending"}
}
