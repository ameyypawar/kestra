import {Ref, onUnmounted, watch} from "vue"

export default function useRouteContext(routeInfo: Ref<{title: string}>, embed: boolean = false) {
    // The title as it was before this page took it over, and the last value this page set.
    // Kept so the page can hand the title back when it goes away, rather than leaving its own
    // name on a tab that has moved on to somewhere that sets no title — the login page after a
    // logout, which is a client-side navigation and so never rebuilds the document (#17896).
    let baseTitle: string | undefined
    let ownTitle: string | undefined

    function handleTitle(){
        if(!embed) {
            let base

            if (document.title.lastIndexOf("|") >= 0) {
                base = document.title.substring(document.title.lastIndexOf("|") + 1).trim()
            } else {
                base = document.title
            }

            baseTitle ??= base
            ownTitle = (routeInfo.value?.title ?? "") + " | " + base
            document.title = ownTitle
        }
    }

    watch(() => routeInfo.value?.title, handleTitle, {immediate: true})

    onUnmounted(() => {
        if (embed || baseTitle === undefined) return

        // Only when the title is still ours. Leaving one titled page for another means the
        // arriving page has already set its own, and overwriting that would put the tab a
        // page behind.
        if (document.title === ownTitle) {
            document.title = baseTitle
        }
    })
}
