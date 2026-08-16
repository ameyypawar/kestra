import {afterEach, beforeEach, describe, expect, it} from "vitest"
import {nextTick} from "vue"
import {createPinia, setActivePinia} from "pinia"
import {useBookmarksStore} from "../../../src/stores/bookmarks"

const OVERVIEW = "/main/flows/edit/company.team/my_flow/overview"
const WITH_FILTER = `${OVERVIEW}?filters[timeRange][EQUALS]=PT24H`
const WITH_OTHER_FILTER = `${OVERVIEW}?filters[timeRange][EQUALS]=PT7D`
const EXECUTIONS = "/main/flows/edit/company.team/my_flow/executions"

describe("bookmarks store", () => {
    beforeEach(() => {
        setActivePinia(createPinia())
        localStorage.clear()
    })

    // The suite shares one jsdom per worker and fails on leaked storage keys
    // (tests/unit/leakGuard.ts), so the persisted list has to go too.
    afterEach(() => {
        localStorage.clear()
    })

    it("adds a page and persists it", async () => {
        const store = useBookmarksStore()
        store.add({path: OVERVIEW, label: "Overview"})

        expect(store.pages).toEqual([{path: OVERVIEW, label: "Overview"}])

        // useStorage writes on the watcher's flush, not on assignment.
        await nextTick()
        expect(JSON.parse(localStorage.getItem("starred.bookmarks") ?? "[]")).toHaveLength(1)
    })

    it("removes a page", () => {
        const store = useBookmarksStore()
        store.add({path: OVERVIEW, label: "Overview"})
        store.remove({path: OVERVIEW})

        expect(store.pages).toEqual([])
    })

    it("renames a page, keeping its path", () => {
        const store = useBookmarksStore()
        store.add({path: OVERVIEW, label: "Overview"})
        store.rename({path: OVERVIEW, label: "Renamed"})

        expect(store.pages).toEqual([{path: OVERVIEW, label: "Renamed"}])
    })

    describe("a bookmark identifies a page, not a filter state", () => {
        it("does not add the same page twice when only the query differs", () => {
            const store = useBookmarksStore()
            store.add({path: WITH_FILTER, label: "company.team: my_flow"})
            store.add({path: WITH_OTHER_FILTER, label: "company.team: my_flow"})

            // Two entries here would render identically in the sidebar, which shows
            // `label ?? path`, and the label carries no filter state.
            expect(store.pages).toHaveLength(1)
            expect(store.pages[0].path).toBe(WITH_FILTER)
        })

        it("recognises a bookmark after its filters change", () => {
            const store = useBookmarksStore()
            store.add({path: WITH_FILTER, label: "company.team: my_flow"})

            expect(store.isBookmarked(WITH_OTHER_FILTER)).toBe(true)
            expect(store.isBookmarked(OVERVIEW)).toBe(true)
        })

        it("keeps the query on what it stores, so the bookmark opens the view it was taken from", () => {
            const store = useBookmarksStore()
            store.add({path: WITH_FILTER, label: "company.team: my_flow"})

            expect(store.pages[0].path).toBe(WITH_FILTER)
        })

        it("removes an entry saved before this normalisation", () => {
            const store = useBookmarksStore()
            store.add({path: WITH_FILTER, label: "company.team: my_flow"})

            // The star sends the current URL, which need not carry the same query
            // as the stored entry.
            store.remove({path: WITH_OTHER_FILTER})

            expect(store.pages).toEqual([])
        })

        it("renames an entry whose stored query differs", () => {
            const store = useBookmarksStore()
            store.add({path: WITH_FILTER, label: "old"})
            store.rename({path: OVERVIEW, label: "new"})

            expect(store.pages[0].label).toBe("new")
        })

        it("still treats a different tab of the same page as its own bookmark", () => {
            const store = useBookmarksStore()
            store.add({path: OVERVIEW, label: "company.team: my_flow (Overview)"})
            store.add({path: EXECUTIONS, label: "company.team: my_flow (Executions)"})

            expect(store.pages).toHaveLength(2)
            expect(store.isBookmarked(EXECUTIONS)).toBe(true)
        })

        it("reports an unknown page as not bookmarked", () => {
            const store = useBookmarksStore()
            store.add({path: OVERVIEW, label: "Overview"})

            expect(store.isBookmarked("/main/flows")).toBe(false)
        })
    })
})
