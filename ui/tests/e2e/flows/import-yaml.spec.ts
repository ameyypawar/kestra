import {expect, test} from "@playwright/test"

// #18142: the paste step capped itself at 48rem and centred, so on a wide screen the
// YAML was read through a column using roughly a third of the space on offer.
test.describe("Import YAML", () => {
    test("gives the editor the width the page offers", async ({page}) => {
        await page.setViewportSize({width: 1920, height: 1080})
        await page.goto("/ui/main/flows/new")

        await page.locator("[data-test='import-yaml-card']").click()
        await expect(page.locator("[data-test='import-yaml-editor']")).toBeVisible()

        const measure = () => page.evaluate(() => {
            const panel = document.querySelector("[data-test='import-yaml']") as HTMLElement
            const editor = document.querySelector("[data-test='import-yaml-editor']") as HTMLElement
            // Monaco's own text surface: the wrapper can be wide while the editor
            // inside it keeps the layout it was first given.
            const lines = editor?.querySelector(".view-lines") as HTMLElement | null
            const available = (panel?.parentElement as HTMLElement)?.getBoundingClientRect().width ?? 0
            return {
                available,
                editor: editor?.getBoundingClientRect().width ?? 0,
                lines: lines?.getBoundingClientRect().width ?? 0,
            }
        })

        // Guards the ratios below: without it they also hold on a narrow viewport,
        // where everything is the same width for the wrong reason.
        await expect.poll(async () => (await measure()).available).toBeGreaterThan(1200)

        // Polled rather than measured once, so a layout that is still settling under a
        // parallel run reads as slow rather than as a regression.
        await expect.poll(async () => {
            const {available, editor} = await measure()
            return available ? editor / available : 0
        }).toBeGreaterThan(0.85)

        await expect.poll(async () => {
            const {available, lines} = await measure()
            return available ? lines / available : 0
        }).toBeGreaterThan(0.8)
    })
})
