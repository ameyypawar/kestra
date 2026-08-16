import {describe, expect, it} from "vitest"
import {multilineTextValue} from "../../../../../src/components/executions/outputs/multilineText"

describe("multilineTextValue", () => {
    it("takes a string that spans lines", () => {
        const trace = "Traceback (most recent call last):\n  File \"a.py\", line 1\nValueError: boom"

        expect(multilineTextValue(trace)).toBe(trace)
    })

    it("keeps the leading whitespace a trace relies on", () => {
        const indented = "line one\n    indented continuation"

        expect(multilineTextValue(indented)).toBe(indented)
    })

    it("leaves a single-line string to the tree", () => {
        // Long, but with nothing to preserve — the tree's paths are more useful here.
        expect(multilineTextValue("a".repeat(500))).toBeUndefined()
        expect(multilineTextValue("")).toBeUndefined()
    })

    it("leaves structure to the tree", () => {
        expect(multilineTextValue({stdout: "one\ntwo"})).toBeUndefined()
        expect(multilineTextValue(["one\ntwo"])).toBeUndefined()
    })

    it("ignores values that are not text", () => {
        expect(multilineTextValue(undefined)).toBeUndefined()
        expect(multilineTextValue(null)).toBeUndefined()
        expect(multilineTextValue(42)).toBeUndefined()
        expect(multilineTextValue(true)).toBeUndefined()
    })

    it("handles a trailing newline, which a log usually has", () => {
        expect(multilineTextValue("done\n")).toBe("done\n")
    })
})
