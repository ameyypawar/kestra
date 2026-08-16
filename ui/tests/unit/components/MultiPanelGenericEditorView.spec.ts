import {afterEach, beforeEach, describe, expect, test, vi} from "vitest"
import {mount} from "@vue/test-utils"
import {createPinia, setActivePinia} from "pinia"
import {createI18n} from "vue-i18n"
import KestraDesignSystem from "@kestra-io/design-system"
import MultiPanelGenericEditorView from "../../../src/components/MultiPanelGenericEditorView.vue"

vi.mock("vue-router", () => ({
    useRoute: () => ({query: {}, params: {}, name: "flow"}),
    useRouter: () => ({replace: vi.fn(), push: vi.fn()}),
}))

// `layout` is declared so the orientation the editor panels receive can be read
// back — the toggle used to set a value that never reached them.
vi.mock("../../../src/components/MultiPanelTabs.vue", () => ({
    default: {name: "MultiPanelTabs", template: "<div />", props: ["modelValue", "layout"]},
}))

vi.mock("../../../src/components/MultiPanelEditorTabs.vue", () => ({
    default: {
        template: "<div><slot /></div>",
        props: ["tabs", "openTabs"],
        emits: ["update:tabs"],
    },
}))

const globalConfig = {
    plugins: [
        createI18n({legacy: false, locale: "en", fallbackWarn: false, missingWarn: false}),
        KestraDesignSystem,
    ],
}

const editorElements = [
    {
        uid: "code",
        button: {label: "Code", icon: {template: "<span/>"}},
        component: {template: "<div/>"},
        deserialize: (v: string) => v === "code" ? {uid: "code", component: {template: "<div/>"}} as any : undefined,
    },
]

const STORAGE_KEY = "editor-panels-orientation"

function mountEditor() {
    return mount(MultiPanelGenericEditorView, {
        global: globalConfig,
        props: {
            editorElements,
            defaultActiveTabs: ["code"],
        },
    })
}

function panelsOrientation(wrapper: ReturnType<typeof mountEditor>) {
    return wrapper.findComponent({name: "MultiPanelTabs"}).props("layout")
}

describe("MultiPanelGenericEditorView split orientation", () => {
    beforeEach(() => {
        setActivePinia(createPinia())
        localStorage.clear()
    })

    afterEach(() => {
        localStorage.clear()
    })

    test("defaults to the panels sitting side by side", () => {
        // Given: no stored preference
        // When: component mounts
        const wrapper = mountEditor()

        // Then: horizontal, which is how the editor has always opened
        expect((wrapper.vm as any).splitOrientation).toBe("horizontal")
    })

    test("toggle button exists with accessible aria-label", () => {
        // Given: the component is mounted
        const wrapper = mountEditor()

        // When: looking for the orientation toggle
        const btn = wrapper.find(".orientation-toggle")

        // Then: it exists and has an aria-label
        expect(btn.exists()).toBe(true)
        expect(btn.attributes("aria-label")).toBeTruthy()
    })

    test("hands the orientation to the editor panels", () => {
        // Given: the component is mounted
        const wrapper = mountEditor()

        // Then: MultiPanelTabs is told how to lay its panels out. Without this the
        // toggle drives the outer splitter, which usually has a single panel and
        // so ignores it entirely.
        expect(panelsOrientation(wrapper)).toBe("horizontal")
    })

    test("the editor panels follow the toggle", async () => {
        // Given: the component is mounted side by side
        const wrapper = mountEditor()
        expect(panelsOrientation(wrapper)).toBe("horizontal")

        // When: the toggle button is clicked
        await wrapper.find(".orientation-toggle").trigger("click")

        // Then: the panels are told to stack
        expect(panelsOrientation(wrapper)).toBe("vertical")
    })

    test("toggles orientation to vertical when button is clicked", async () => {
        // Given: default horizontal orientation
        const wrapper = mountEditor()
        expect((wrapper.vm as any).splitOrientation).toBe("horizontal")

        // When: the toggle button is clicked
        await wrapper.find(".orientation-toggle").trigger("click")

        // Then: orientation switches to vertical
        expect((wrapper.vm as any).splitOrientation).toBe("vertical")
    })

    test("persists orientation toggle to localStorage", async () => {
        // Given: default horizontal orientation
        const wrapper = mountEditor()

        // When: the toggle button is clicked
        await wrapper.find(".orientation-toggle").trigger("click")

        // Then: the preference is saved in localStorage (VueUse useStorage stores string values unquoted)
        const stored = localStorage.getItem(STORAGE_KEY)
        expect(stored === "\"vertical\"" || stored === "vertical").toBe(true)
    })

    test("reads persisted orientation on mount", () => {
        // Given: a vertical orientation stored in localStorage (try both forms VueUse may read)
        localStorage.setItem(STORAGE_KEY, "vertical")

        // When: component mounts
        const wrapper = mountEditor()

        // Then: both the state and the panels start stacked
        expect((wrapper.vm as any).splitOrientation).toBe("vertical")
        expect(panelsOrientation(wrapper)).toBe("vertical")
    })

    test("ignores a value left behind by the old outer-splitter setting", () => {
        // Given: the previous key, whose "vertical" meant the playground sat below
        localStorage.setItem("editor-split-orientation", "vertical")

        // When: component mounts
        const wrapper = mountEditor()

        // Then: the panels are not silently stacked on upgrade
        expect(panelsOrientation(wrapper)).toBe("horizontal")
    })

    test("toggles back to horizontal after two clicks", async () => {
        // Given: default horizontal orientation
        const wrapper = mountEditor()
        const btn = wrapper.find(".orientation-toggle")

        // When: toggle clicked twice
        await btn.trigger("click")
        await btn.trigger("click")

        // Then: back to horizontal
        expect((wrapper.vm as any).splitOrientation).toBe("horizontal")
        expect(panelsOrientation(wrapper)).toBe("horizontal")
    })
})
