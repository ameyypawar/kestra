import {expect, test} from "@playwright/test"

// #17369: the flows table is wider than its container on a small or zoomed window.
// It does scroll, but Element Plus hides the native scrollbar and auto-hides its own,
// so at rest nothing says the rest of the table is there.
test.describe("Flows table", () => {
    // Narrow enough that the table overflows whatever the instance holds. The issue
    // reports 1280 at 110% zoom, which lays out as ~1164 CSS px, but whether that
    // width overflows depends on the data — namespace and id lengths drive the
    // column widths — so it would make this test pass or fail on seeding.
    test("shows a scrollbar when it overflows sideways", async ({page}) => {
        await page.setViewportSize({width: 800, height: 900})
        await page.goto("/ui/main/flows")

        await expect(page.locator("th").first()).toBeVisible()

        const state = () => page.evaluate(() => {
            // The page has several scrollbars — the sidebar has one too — so start from
            // the table's own header and walk out to the wrapper holding it.
            let wrap: HTMLElement | null = document.querySelector("th")
            while (wrap && !wrap.classList?.contains("kel-scrollbar__wrap")) {
                wrap = wrap.parentElement
            }
            if (!wrap) return null
            const thumb = wrap.parentElement?.querySelector(
                ".kel-scrollbar__bar.is-horizontal .kel-scrollbar__thumb",
            ) as HTMLElement | null
            return {
                hidden: wrap.scrollWidth - wrap.clientWidth,
                thumbWidth: thumb?.getBoundingClientRect().width ?? 0,
                // The bar is laid out even with nothing to scroll, so width alone does
                // not mean anything is on screen.
                thumbOpacity: thumb ? Number(getComputedStyle(thumb).opacity) : 0,
            }
        })

        // Precondition: the table really does overflow here, otherwise the assertions
        // below would hold for the wrong reason.
        await expect.poll(async () => (await state())?.hidden ?? 0).toBeGreaterThan(0)

        await expect.poll(async () => (await state())?.thumbWidth ?? 0).toBeGreaterThan(0)
        await expect.poll(async () => (await state())?.thumbOpacity ?? 0).toBeGreaterThan(0)
    })
})
