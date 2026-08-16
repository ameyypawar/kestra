import {Ref, onUnmounted, watch} from "vue"

/** The part of a title after the last pipe, or the whole of it when there is none. */
function baseOf(title: string): string {
    const separator = title.lastIndexOf("|")
    return separator >= 0 ? title.substring(separator + 1).trim() : title
}

/** The part of a title before the last pipe — the page's own name. */
function pageOf(title: string): string {
    const separator = title.lastIndexOf("|")
    return separator >= 0 ? title.substring(0, separator).trim() : ""
}

export default function useRouteContext(routeInfo: Ref<{title: string}>, embed: boolean = false) {
    // The page name this instance last put in the tab, so it can tell on the way out whether
    // the tab is still showing it.
    let ownPage: string | undefined

    function handleTitle(){
        if(!embed) {
            ownPage = routeInfo.value?.title ?? ""
            document.title = ownPage + " | " + baseOf(document.title)
        }
    }

    watch(() => routeInfo.value?.title, handleTitle, {immediate: true})

    onUnmounted(() => {
        if (embed || ownPage === undefined) return

        // Matched on the page name rather than the whole title: App.vue appends the
        // environment name after a page has claimed it, so comparing the two in full would
        // never match on an instance that has one, and the title would never be handed back.
        //
        // When the names differ, the page arriving after this one has already set its own and
        // that has to stand. When they match, hand the base back — so leaving for a page that
        // sets no title of its own, the login page after a logout, does not keep this one
        // sitting in the tab (#17896).
        if (pageOf(document.title) === ownPage) {
            document.title = baseOf(document.title)
        }
    })
}
