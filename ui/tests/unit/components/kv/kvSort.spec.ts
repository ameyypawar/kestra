import {describe, expect, it} from "vitest"
import {DEFAULT_KV_SORT, parseSortQuery} from "../../../../src/components/kv/kvSort"

describe("parseSortQuery", () => {
    it("reads a field and direction", () => {
        expect(parseSortQuery("updateDate:desc")).toEqual({prop: "updateDate", order: "descending"})
        expect(parseSortQuery("key:asc")).toEqual({prop: "key", order: "ascending"})
    })

    it("falls back when there is no sort in the URL", () => {
        expect(parseSortQuery(undefined)).toEqual(DEFAULT_KV_SORT)
        expect(parseSortQuery(null)).toEqual(DEFAULT_KV_SORT)
        expect(parseSortQuery("")).toEqual(DEFAULT_KV_SORT)
    })

    it("falls back rather than inventing a direction", () => {
        // Previously anything that was not "desc" was read as ascending, so a typo or a
        // half-written value silently became a real sort.
        expect(parseSortQuery("key")).toEqual(DEFAULT_KV_SORT)
        expect(parseSortQuery("key:")).toEqual(DEFAULT_KV_SORT)
        expect(parseSortQuery("key:sideways")).toEqual(DEFAULT_KV_SORT)
        expect(parseSortQuery(":asc")).toEqual(DEFAULT_KV_SORT)
    })

    it("takes the last of a repeated sort parameter", () => {
        // vue-router hands back an array for `?sort=a:asc&sort=b:desc`; joining it produced
        // the field "a" with the direction "asc,b".
        expect(parseSortQuery(["key:asc", "updateDate:desc"])).toEqual({prop: "updateDate", order: "descending"})
        expect(parseSortQuery(["key:asc"])).toEqual({prop: "key", order: "ascending"})
        expect(parseSortQuery([])).toEqual(DEFAULT_KV_SORT)
        expect(parseSortQuery([null])).toEqual(DEFAULT_KV_SORT)
    })
})
