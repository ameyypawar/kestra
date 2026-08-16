import {Ref, onUnmounted, watch} from "vue"

/** The part of a title after the last pipe, or the whole of it when there is none. */
function baseOf(title: string): string {
    const separator = title.lastIndexOf("|")
    return separator >= 0 ? title.substring(separator + 1).trim() : title
}

/**
 * Whoever wrote the tab title last. Shared across every caller, so a page can tell on the way
 * out whether the title is still the one it put there, without comparing strings — two pages
 * can carry the same name, and App.vue rewrites the title after the fact to append the
 * environment name.
 */
let titleOwner: symbol | undefined

export default function useRouteContext(routeInfo: Ref<{title: string}>, embed: boolean = false) {
    const owner = Symbol("routeContext")

    function handleTitle(){
        if(!embed) {
            document.title = (routeInfo.value?.title ?? "") + " | " + baseOf(document.title)
            titleOwner = owner
        }
    }

    watch(() => routeInfo.value?.title, handleTitle, {immediate: true})

    onUnmounted(() => {
        // Only the page still holding the title gives it back. On a normal route change the
        // arriving page has already taken ownership, and its title has to stand; here the
        // page is leaving for one that sets no title of its own — the login page after a
        // logout, which is a client-side navigation and never rebuilds the document (#17896).
        if (embed || titleOwner !== owner) return

        document.title = baseOf(document.title)
        titleOwner = undefined
    })
}
