import {expect, test} from "@playwright/test"

// #17369: the flows table is wider than its container once the window is small or
// zoomed, and it does scroll — but Element Plus hides the native scrollbar and keeps
// its own hidden, so nothing says the rest of the table is there.
test.describe("Flows table", () => {
    // 1280px at 110% browser zoom lays out as 1280/1.1 CSS px. Zoom is emulated by the
    // equivalent viewport because it is the same thing to layout, and Playwright cannot
    // drive the browser's own zoom.
    test("shows a scrollbar when it overflows sideways", async ({page}) => {
        await page.setViewportSize({width: 1164, height: 900})
        await page.goto("/ui/main/flows")

        await expect(page.locator("th").first()).toBeVisible()

        const state = () => page.evaluate(() => {
            // The page has several scrollbars (the sidebar has one too), so start from the
            // table's own header and walk out to the wrapper that holds it.
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
            }
        })

        // Precondition: at this width the table really does overflow, otherwise the
        // assertion below would pass for the wrong reason.
        await expect.poll(async () => (await state())?.hidden ?? 0).toBeGreaterThan(0)

        // The affordance a user can actually see. Asserted on the thumb rather than the
        // bar, since the bar can be laid out with a zero-width thumb inside it.
        await expect.poll(async () => (await state())?.thumbWidth ?? 0).toBeGreaterThan(0)
    })
})
