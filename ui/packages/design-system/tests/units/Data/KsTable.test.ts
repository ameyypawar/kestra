import {describe, test, expect} from "vitest"
import {mount} from "@vue/test-utils"
import {ElTable} from "element-plus"
import KestraDesignSystem from "../../../src/index"
import KsTable from "../../../src/components/Data/KsTable/KsTable.vue"

const globalConfig = {plugins: [KestraDesignSystem]}

describe("KsTable", () => {
    test("keeps the scrollbar on, so sideways overflow is visible", () => {
        const wrapper = mount(KsTable, {props: {data: []}, global: globalConfig})

        expect(wrapper.findComponent(ElTable).props("scrollbarAlwaysOn")).toBe(true)
    })

    test("lets a caller turn it back off", () => {
        const wrapper = mount(KsTable, {
            props: {data: [], scrollbarAlwaysOn: false},
            global: globalConfig,
        })

        expect(wrapper.findComponent(ElTable).props("scrollbarAlwaysOn")).toBe(false)
    })
})
