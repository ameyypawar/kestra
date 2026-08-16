import {beforeEach, describe, expect, it} from "vitest"
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

    it("adds a page and persists it", () => {
        const store = useBookmarksStore()
        store.add({path: OVERVIEW, label: "Overview"})

        expect(store.pages).toEqual([{path: OVERVIEW, label: "Overview"}])
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

            // Two entries here render identically in the sidebar — the label comes
            // from the breadcrumb and title, not the path.
            expect(store.pages).toHaveLength(1)
            expect(store.pages[0].path).toBe(WITH_FILTER)
        })

        it("recognises a bookmark after its filters change", () => {
            const store = useBookmarksStore()
            store.add({path: WITH_FILTER, label: "company.team: my_flow"})

            expect(store.isBookmarked(WITH_OTHER_FILTER)).toBe(true)
            expect(store.isBookmarked(OVERVIEW)).toBe(true)
        })

        it("keeps the query on what it stores, so the view opens as bookmarked", () => {
            const store = useBookmarksStore()
            store.add({path: WITH_FILTER, label: "company.team: my_flow"})

            expect(store.pages[0].path).toBe(WITH_FILTER)
        })

        it("removes an entry saved before this normalisation", () => {
            const store = useBookmarksStore()
            store.add({path: WITH_FILTER, label: "company.team: my_flow"})

            // The star sends whatever the current URL is, which need not carry the
            // same query as the stored entry.
            store.remove({path: WITH_OTHER_FILTER})

            expect(store.pages).toEqual([])
        })

        it("renames an entry whose stored query differs", () => {
            const store = useBookmarksStore()
            store.add({path: WITH_FILTER, label: "old"})
            store.rename({path: OVERVIEW, label: "new"})

            expect(store.pages[0].label).toBe("new")
        })

        it("still treats different pages as different bookmarks", () => {
            const store = useBookmarksStore()
            store.add({path: OVERVIEW, label: "Overview"})
            store.add({path: EXECUTIONS, label: "Executions"})

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
