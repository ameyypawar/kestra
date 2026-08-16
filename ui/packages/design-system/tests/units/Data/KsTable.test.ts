import {describe, test, expect} from "vitest"
import {mount} from "@vue/test-utils"
import {createI18n} from "vue-i18n"
import {ElTable} from "element-plus"
import KestraDesignSystem from "../../../src/index"
import KsTable from "../../../src/components/Data/KsTable/KsTable.vue"
import KsTableColumn from "../../../src/components/Data/KsTable/KsTableColumn.vue"

const globalConfig = {
    plugins: [createI18n({legacy: false, locale: "en"}), KestraDesignSystem],
}

describe("KsTable", () => {
    test("renders table element", () => {
        const wrapper = mount(KsTable, {
            props: {data: [{name: "test"}]},
            global: globalConfig,
        })
        expect(wrapper.find(".kel-table").exists()).toBe(true)
    })

    test("exposes clearSelection method", () => {
        const wrapper = mount(KsTable, {
            props: {data: []},
            global: globalConfig,
        })
        expect(typeof (wrapper.vm as any).clearSelection).toBe("function")
    })

    test("exposes toggleAllSelection method", () => {
        const wrapper = mount(KsTable, {
            props: {data: []},
            global: globalConfig,
        })
        expect(typeof (wrapper.vm as any).toggleAllSelection).toBe("function")
    })

    test("exposes clearSort method", () => {
        const wrapper = mount(KsTable, {
            props: {data: []},
            global: globalConfig,
        })
        expect(typeof (wrapper.vm as any).clearSort).toBe("function")
    })

    test("exposes sort method", () => {
        const wrapper = mount(KsTable, {
            props: {data: []},
            global: globalConfig,
        })
        expect(typeof (wrapper.vm as any).sort).toBe("function")
    })

    test("renders with columns", () => {
        const wrapper = mount({
            components: {KsTable, KsTableColumn},
            template: `
                <ks-table :data="[{id: '1', name: 'Test'}]">
                    <ks-table-column prop="id" label="ID" />
                    <ks-table-column prop="name" label="Name" />
                </ks-table>
            `,
        }, {global: globalConfig})
        expect(wrapper.find(".kel-table").exists()).toBe(true)
    })

    test("keeps the scrollbar on, so sideways overflow is visible", () => {
        const wrapper = mount(KsTable, {
            props: {data: []},
            global: globalConfig,
        })
        expect(wrapper.findComponent(ElTable).props("scrollbarAlwaysOn")).toBe(true)
    })

    test("lets a caller turn the scrollbar back off", () => {
        const wrapper = mount(KsTable, {
            props: {data: [], scrollbarAlwaysOn: false},
            global: globalConfig,
        })
        expect(wrapper.findComponent(ElTable).props("scrollbarAlwaysOn")).toBe(false)
    })
})
