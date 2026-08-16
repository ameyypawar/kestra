import {expect, test as authenticated} from "./fixtures/auth"
import type {Page} from "@playwright/test"
import {FlowsApi} from "./api/flows.api"
import {shared} from "./fixtures/shared"

const TENANT = process.env.E2E_TENANT ?? "main"

/*
 * A cookie-free API context, same override as blocks.fixture and
 * executions.fixture. The built-in `request` inherits `use.storageState`, which
 * carries the basic-auth cookie; CsrfTokenFilter#hasCookieAuth then treats the
 * POST as cookie-authenticated and rejects it with a 403 unless it also carries
 * an X-CSRF-TOKEN. FlowsApi authenticates with the CSRF-exempt Authorization
 * header instead, so a fresh context keeps that path open. `page` keeps the
 * shared storage state, so the UI stays signed in.
 */
const test = authenticated.extend({
    request: async ({playwright, baseURL}, use) => {
        const context = await playwright.request.newContext({
            baseURL,
            storageState: {cookies: [], origins: []},
        })
        await use(context)
        await context.dispose()
    },
})

// The toggle in the editor tab bar is labelled "split horizontally / vertically".
// It used to drive the splitter holding the editor and the playground, which
// outside playground mode has a single panel — so nothing moved (#18143).
test.describe("Flow editor — split orientation", () => {
    let flowsApi: FlowsApi
    let flowId: string

    test.beforeEach(async ({page, request, baseURL}) => {
        flowsApi = new FlowsApi(request, baseURL)
        flowId = await flowsApi.generateFlowViaApi("blocks-canvas.yaml", "blocks-canvas-fixture")

        await page.goto("/ui/flows")
        // A fresh flow id means fresh panel state; clear the orientation so the
        // run does not inherit a preference from an earlier one.
        await page.evaluate(() => {
            localStorage.removeItem("editor-panels-orientation")
            localStorage.removeItem("editor-split-orientation")
        })
        await page.goto(`/ui/${TENANT}/flows/edit/${shared.namespace}/${flowId}/edit`)
    })

    test.afterEach(async () => {
        await flowsApi.removeFlowsViaApi()
    })

    async function openSecondPanel(page: Page) {
        // Each editor tab button opens its own panel, so this leaves two side by
        // side — an orientation is only observable with more than one.
        await page.getByRole("button", {name: "Topology", exact: true}).click()
        await expect(page.locator("[data-panel-index]")).toHaveCount(2)
    }

    async function panelBoxes(page: Page) {
        const panels = page.locator("[data-panel-index]")
        const first = await panels.nth(0).boundingBox()
        const second = await panels.nth(1).boundingBox()
        expect(first).not.toBeNull()
        expect(second).not.toBeNull()
        return [first!, second!]
    }

    test("the toggle moves the editor panels between side-by-side and stacked", async ({page}) => {
        await openSecondPanel(page)

        // Side by side: same top edge, different left edge.
        const [beforeA, beforeB] = await panelBoxes(page)
        expect(Math.abs(beforeA.y - beforeB.y)).toBeLessThan(4)
        expect(Math.abs(beforeA.x - beforeB.x)).toBeGreaterThan(50)

        await page.locator(".orientation-toggle").click()

        // Stacked: same left edge, different top edge. Before the fix both
        // measurements were unchanged, because the click reached a splitter with
        // only one panel in it.
        await expect(async () => {
            const [afterA, afterB] = await panelBoxes(page)
            expect(Math.abs(afterA.x - afterB.x)).toBeLessThan(4)
            expect(Math.abs(afterA.y - afterB.y)).toBeGreaterThan(50)
        }).toPass({timeout: 5000})
    })

    test("the choice survives a reload", async ({page}) => {
        await openSecondPanel(page)
        await page.locator(".orientation-toggle").click()

        await expect(async () => {
            const [a, b] = await panelBoxes(page)
            expect(Math.abs(a.x - b.x)).toBeLessThan(4)
        }).toPass({timeout: 5000})

        await page.reload()
        await expect(page.locator("[data-panel-index]")).toHaveCount(2)

        const [a, b] = await panelBoxes(page)
        expect(Math.abs(a.x - b.x)).toBeLessThan(4)
        expect(Math.abs(a.y - b.y)).toBeGreaterThan(50)
    })
})
