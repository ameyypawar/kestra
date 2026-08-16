import {Ref, onUnmounted, watch} from "vue"

/** The part of a title after the last pipe, or the whole of it when there is none. */
function baseOf(title: string): string {
    const separator = title.lastIndexOf("|")
    return separator >= 0 ? title.substring(separator + 1).trim() : title
}

export default function useRouteContext(routeInfo: Ref<{title: string}>, embed: boolean = false) {
    // The last title this page set, so it can tell whether the tab still shows its own name
    // once it goes away.
    let ownTitle: string | undefined

    function handleTitle(){
        if(!embed) {
            ownTitle = (routeInfo.value?.title ?? "") + " | " + baseOf(document.title)
            document.title = ownTitle
        }
    }

    watch(() => routeInfo.value?.title, handleTitle, {immediate: true})

    onUnmounted(() => {
        if (embed || ownTitle === undefined) return

        // Only when the tab still shows this page's name: moving between two pages means the
        // arriving one has already set its own, and that has to stand. Otherwise hand the
        // base back, so leaving for a page that sets no title of its own — the login page
        // after a logout, which never rebuilds the document — does not keep this one (#17896).
        //
        // Derived here rather than remembered from mount: App.vue appends the environment
        // name to the title from its own onMounted, which runs after a child's, so a
        // remembered copy can predate the suffix and dropping it back would lose it.
        if (document.title === ownTitle) {
            document.title = baseOf(document.title)
        }
    })
}
