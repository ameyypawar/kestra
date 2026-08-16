import {expect, test} from "@playwright/test"

// #18142: the paste step capped itself at 48rem and centred, so on a wide screen the
// YAML was read through a column far narrower than the editor it hands off to.
test.describe("Import YAML", () => {
    test("gives the editor the width the page offers", async ({page}) => {
        await page.setViewportSize({width: 1920, height: 1080})
        await page.goto("/ui/main/flows/new")

        await page.locator("[data-test='import-yaml-card']").click()
        await expect(page.locator("[data-test='import-yaml-editor']")).toBeVisible()

        const widths = await page.evaluate(() => {
            const panel = document.querySelector("[data-test='import-yaml']") as HTMLElement
            const editor = document.querySelector("[data-test='import-yaml-editor']") as HTMLElement
            return {
                available: (panel.parentElement as HTMLElement).getBoundingClientRect().width,
                editor: editor.getBoundingClientRect().width,
            }
        })

        // Guards the ratio below: without it the assertion also passes on a narrow
        // viewport, where everything is the same width for the wrong reason.
        expect(widths.available).toBeGreaterThan(1200)
        expect(widths.editor).toBeGreaterThan(widths.available * 0.85)
    })
})
