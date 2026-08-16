import { defineStore } from "pinia"
import { useStorage } from "@vueuse/core"

const LOCAL_STORAGE_KEY = "starred.bookmarks"

interface Page {
    path: string;
    label?: string;
}

/**
 * A bookmark identifies a page, not one filter state of it.
 *
 * Stored paths carry query parameters the user never chose to bookmark: the flow
 * overview appends `filters[timeRange]` on load, pagination writes `page=`. Matching on
 * the raw path meant an existing bookmark stopped being recognised as soon as one of
 * those changed, and starring again appended a second entry — indistinguishable in the
 * sidebar, because the label comes from the breadcrumb and title rather than the path.
 *
 * Only the comparison is normalised. What is stored keeps its query, so a bookmark
 * still opens the view it was taken from, and entries saved before this still match.
 */
function identity(path: string): string {
    return path.split("?")[0]
}

export const useBookmarksStore = defineStore("bookmarks", () => {
    const pages = useStorage<Page[]>(LOCAL_STORAGE_KEY, [])

    function isBookmarked(path: string) {
        return pages.value.some(p => identity(p.path) === identity(path))
    }

    function add(page: Page) {
        if (!isBookmarked(page.path)) {
            pages.value = [...pages.value, page]
        }
    }

    function remove(page: Page) {
        pages.value = pages.value.filter(p => identity(p.path) !== identity(page.path))
    }

    function rename(page: Page) {
        pages.value = pages.value.map(p =>
            identity(p.path) === identity(page.path) ? { ...p, label: page.label } : p
        )
    }

    function updateAll(newPages: Array<Page>) {
        pages.value = [...newPages]
    }

    return {
        pages,
        isBookmarked,
        add,
        remove,
        rename,
        updateAll,
    }
})
