import {describe, expect, test} from "vitest"
import type {LocationQuery} from "vue-router"
import {applyResolvedDefaults} from "../../../../src/components/Data/KsDataTable/filter/composables/useDefaultFilter"

/**
 * Defaults are resolved against the saved restore state as well as the URL, so that a key
 * the user already chose is not filled again. What may then be navigated to is only what
 * the defaults themselves added or removed — see #17798, where the saved state rode along
 * and reappeared as filters on a page the user never set them on.
 */
describe("applyResolvedDefaults", () => {
    const saved: LocationQuery = {"filters[labels][EQUALS]": "team:payments"}

    test("keeps a default that was added", () => {
        const current: LocationQuery = {}
        const considered: LocationQuery = {...current}
        const resolved: LocationQuery = {"filters[namespace][PREFIX]": "company"}

        expect(applyResolvedDefaults(current, considered, resolved))
            .toEqual({"filters[namespace][PREFIX]": "company"})
    })

    test("does not carry the saved state into the query", () => {
        const current: LocationQuery = {}
        const considered: LocationQuery = {...saved, ...current}
        const resolved: LocationQuery = {...considered, "filters[namespace][PREFIX]": "company"}

        const query = applyResolvedDefaults(current, considered, resolved)

        expect(query).toEqual({"filters[namespace][PREFIX]": "company"})
        expect(query["filters[labels][EQUALS]"]).toBeUndefined()
    })

    test("leaves the filters the URL already carried", () => {
        const current: LocationQuery = {"filters[namespace][EQUALS]": "company.team"}
        const considered: LocationQuery = {...saved, ...current}
        const resolved: LocationQuery = {...considered, "filters[timeRange][EQUALS]": "PT24H"}

        expect(applyResolvedDefaults(current, considered, resolved)).toEqual({
            "filters[namespace][EQUALS]": "company.team",
            "filters[timeRange][EQUALS]": "PT24H",
        })
    })

    test("drops a key the defaults removed", () => {
        // applyDefaultFilters strips scope when includeScope is off.
        const current: LocationQuery = {"filters[scope][EQUALS]": "USER", page: "2"}
        const considered: LocationQuery = {...current}
        const resolved: LocationQuery = {page: "2"}

        expect(applyResolvedDefaults(current, considered, resolved)).toEqual({page: "2"})
    })

    test("does not resurrect a saved key the defaults removed", () => {
        const current: LocationQuery = {}
        const considered: LocationQuery = {"filters[scope][EQUALS]": "USER"}
        const resolved: LocationQuery = {}

        expect(applyResolvedDefaults(current, considered, resolved)).toEqual({})
    })
})
