import {afterAll, afterEach, beforeAll, beforeEach, describe, expect, it} from "vitest"
import {defineComponent, h, nextTick, ref, type Ref} from "vue"
import {mount, VueWrapper} from "@vue/test-utils"
import useRouteContext from "../../../src/composables/useRouteContext"

function mountRouteContext(routeInfo: Ref<{title: string}>, embed = false) {
    return mount(defineComponent({
        setup() {
            useRouteContext(routeInfo, embed)
        },
        render: () => h("div"),
    }))
}

describe("useRouteContext", () => {
    let wrapper: VueWrapper
    let originalTitle: string

    beforeAll(() => {
        originalTitle = document.title
    })

    afterAll(() => {
        document.title = originalTitle
    })

    beforeEach(() => {
        document.title = "Kestra EE"
    })

    afterEach(() => {
        wrapper?.unmount()
    })

    it("sets the title on mount", () => {
        wrapper = mountRouteContext(ref({title: "Initial"}))
        expect(document.title).toBe("Initial | Kestra EE")
    })

    it("updates the title when routeInfo.title changes after mount (async-loaded entity name)", async () => {
        const routeInfo = ref({title: "Loading"})
        wrapper = mountRouteContext(routeInfo)

        routeInfo.value = {title: "Loaded Entity"}
        await nextTick()

        expect(document.title).toBe("Loaded Entity | Kestra EE")
    })

    it("does not accumulate whitespace across repeated title changes", async () => {
        const routeInfo = ref({title: "First"})
        wrapper = mountRouteContext(routeInfo)

        routeInfo.value = {title: "Second"}
        await nextTick()
        routeInfo.value = {title: "Third"}
        await nextTick()

        expect(document.title).toBe("Third | Kestra EE")
    })

    it("does not touch document.title when embed is true", () => {
        wrapper = mountRouteContext(ref({title: "Ignored"}), true)
        expect(document.title).toBe("Kestra EE")
    })

    it("does not double the pipe when the base title starts with '|' (browser-trimmed leading space)", async () => {
        document.title = "| Kestra EE"
        const routeInfo = ref({title: "Default Dashboard"})
        wrapper = mountRouteContext(routeInfo)

        expect(document.title).toBe("Default Dashboard | Kestra EE")
    })

    it("gives the base title back when the page goes away", () => {
        // Logging out pushes to a login page that sets no title of its own, so without this
        // the tab keeps the name of the page behind it (#17896).
        const page = mountRouteContext(ref({title: "Flows"}))
        expect(document.title).toBe("Flows | Kestra EE")

        page.unmount()

        expect(document.title).toBe("Kestra EE")
    })

    it("gives back the base as it stands, not as it was at mount", () => {
        // App.vue appends the environment name from its own onMounted, which runs after a
        // child's, so the suffix can arrive after a page has already claimed the title.
        // Handing back a copy remembered at mount would drop it.
        const page = mountRouteContext(ref({title: "Flows"}))
        expect(document.title).toBe("Flows | Kestra EE")

        document.title = "Flows | Kestra EE - Production"

        page.unmount()

        expect(document.title).toBe("Kestra EE - Production")
    })

    it("still hands back the base after the title has changed in place", async () => {
        const routeInfo = ref({title: "First"})
        const page = mountRouteContext(routeInfo)

        routeInfo.value = {title: "Second"}
        await nextTick()

        page.unmount()

        expect(document.title).toBe("Kestra EE")
    })

    it("leaves the title alone when another page has already taken it", () => {
        const leaving = mountRouteContext(ref({title: "Flows"}))
        wrapper = mountRouteContext(ref({title: "Executions"}))
        expect(document.title).toBe("Executions | Kestra EE")

        leaving.unmount()

        expect(document.title).toBe("Executions | Kestra EE")
    })

    it("does not restore anything when embed is true", () => {
        const page = mountRouteContext(ref({title: "Ignored"}), true)

        page.unmount()

        expect(document.title).toBe("Kestra EE")
    })
})
