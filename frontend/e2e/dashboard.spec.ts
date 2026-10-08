import {expect, test} from "@playwright/test";
import {historyEntry, mockProductionApi, normalUser} from "./production-fixtures";

test.describe("authenticated dashboard production flow", () => {
    test("uses the shared rounded shape for statistics and confirmation buttons", async ({page}) => {
        await mockProductionApi(page, {user: normalUser});
        await page.goto("/");

        const inlineStatisticsRadii = await page.locator(".dashboard-inline-stats .rolling-average-stat")
            .evaluateAll((buttons) => buttons.map((button) => getComputedStyle(button).borderRadius));
        expect(inlineStatisticsRadii.length).toBeGreaterThan(0);
        expect(new Set(inlineStatisticsRadii)).toEqual(new Set(["0px"]));
        const inlineStatisticsDividers = await page.locator(".dashboard-inline-stats .inline-stat")
            .evaluateAll((cells) => cells.map((cell) => getComputedStyle(cell).borderRightWidth));
        expect(inlineStatisticsDividers).toEqual(["1px", "1px", "0px"]);

        await page.locator(".statistics-card .rail-card-header button").click();
        const statisticsDialog = page.getByRole("dialog", {name: "Statistics"});
        await expect(statisticsDialog).toBeVisible();
        const controlRows = await statisticsDialog.locator(".statistics-range-control, .statistics-metric-control")
            .evaluateAll((controls) => controls.map((control) => control.getBoundingClientRect().top));
        expect(controlRows[0]).toBe(controlRows[1]);
        const summaryRadii = await statisticsDialog.locator(".statistics-grid button")
            .evaluateAll((buttons) => buttons.map((button) => getComputedStyle(button).borderRadius));
        expect(summaryRadii.length).toBeGreaterThan(0);
        expect(new Set(summaryRadii)).toEqual(new Set(["0px"]));
        const statisticsButtonRadii = await statisticsDialog
            .locator(".statistics-range-control button, .statistics-metric-control button")
            .evaluateAll((buttons) => buttons.map((button) => getComputedStyle(button).borderRadius));
        expect(statisticsButtonRadii.length).toBeGreaterThan(0);
        expect(new Set(statisticsButtonRadii)).toEqual(new Set(["5px"]));

        await page.keyboard.press("Escape");
        await page.getByRole("button", {name: "History"}).click();
        const deleteSolveButton = page.getByRole("button", {name: "Delete solve 12.34"});
        const deleteIconColor = await deleteSolveButton.evaluate((button) => getComputedStyle(button).color);
        await deleteSolveButton.click();
        const confirmation = page.getByRole("alertdialog", {name: "Delete solve?"});
        const confirmDeleteButton = confirmation.locator(".confirmation-dialog-actions button.danger");
        const confirmationButtonColor = await confirmDeleteButton.evaluate((button) => getComputedStyle(button).backgroundColor);
        expect(deleteIconColor).toBe("rgb(217, 75, 75)");
        expect(confirmationButtonColor).toBe(deleteIconColor);
        const confirmationButtonRadii = await confirmation
            .locator(".confirmation-dialog-actions button")
            .evaluateAll((buttons) => buttons.map((button) => getComputedStyle(button).borderRadius));
        expect(confirmationButtonRadii).toEqual(["5px", "5px"]);
    });

    test("opens the solve-time statistics modal from Statistics More", async ({page}) => {
        const api = await mockProductionApi(page, {user: normalUser});
        await page.goto("/");

        await page.locator(".statistics-card .rail-card-header button").click();
        const dialog = page.getByRole("dialog", {name: "Statistics"});
        await expect(dialog).toBeVisible();
        await expect(dialog.getByRole("group", {name: /Solve time for 1 recent solve/})).toBeVisible();
        await expect(dialog.getByRole("button", {name: /Solve 1:/})).toBeVisible();
        expect(api.requests.some((request) => request.pathname === "/api/solves" && request.search.includes("limit=50"))).toBe(true);

        await dialog.getByRole("button", {name: "All solves"}).click();
        await expect(dialog.getByRole("group", {name: /Solve time for 1 total solve/})).toBeVisible();
        expect(api.requests.some((request) => request.pathname === "/api/solves" && request.search.includes("limit=100"))).toBe(true);

        await dialog.getByRole("button", {name: /Solve 1:.*Open solution/}).click();
        const solutionDialog = page.getByRole("dialog", {name: "Solve solution"});
        await expect(solutionDialog).toBeVisible();
        await expect(solutionDialog.locator(".solution-modal-context")).toContainText("Solve #1");
        await expect(dialog).toBeVisible();
        const solutionLayer = await page.locator(".solution-modal-backdrop").evaluate((node) => Number(getComputedStyle(node).zIndex));
        const statisticsLayer = await page.locator(".statistics-modal-backdrop").evaluate((node) => Number(getComputedStyle(node).zIndex));
        expect(solutionLayer).toBeGreaterThan(statisticsLayer);
        await page.keyboard.press("Escape");
        await expect(solutionDialog).toHaveCount(0);
        await expect(dialog).toBeVisible();
        await expect(dialog.getByRole("button", {name: /Solve 1:/})).toBeFocused();

        await page.keyboard.press("Escape");
        await expect(dialog).toHaveCount(0);
        await page.locator(".recent-card .rail-card-header button").click();
        await expect(page.getByRole("heading", {name: "History"})).toBeVisible();

        await page.getByRole("button", {name: "More statistics"}).click();
        await expect(page.getByRole("dialog", {name: "Statistics"})).toBeVisible();
        await page.keyboard.press("Escape");
        await expect(page.getByRole("dialog", {name: "Statistics"})).toHaveCount(0);
    });

    test("opens the solve behind the all-history Best statistic", async ({page}) => {
        const historyEntries = Array.from({length: 21}, (_, index) => ({
            ...historyEntry,
            id: 21 - index,
            clientAttemptId: `best-lookup-${21 - index}`,
            scramble: index === 20 ? "BEST_ONLY R U" : `R U ${index}`,
            timerMs: index === 20 ? 5_000 : 20_000 + index * 100,
            officialMs: index === 20 ? 5_000 : 20_000 + index * 100,
            createdAt: new Date(Date.now() - index * 1_000).toISOString(),
        }));
        const api = await mockProductionApi(page, {user: normalUser, historyEntries});
        await page.goto("/");
        await page.getByRole("button", {name: "History"}).click();

        const bestButton = page.getByRole("button", {name: "Open best solve 5.00"});
        await expect(bestButton).toBeVisible();
        await bestButton.click();

        const solutionDialog = page.getByRole("dialog", {name: "Solve solution"});
        await expect(solutionDialog.locator(".modal-scramble")).toHaveText("BEST_ONLY R U");
        expect(api.requests.some((request) => request.pathname === "/api/solves" && new URLSearchParams(request.search).get("time") === "5.00")).toBe(true);
    });

    test("opens the best solve from the Timer's inline statistics", async ({page}) => {
        const historyEntries = [
            {...historyEntry, id: 2, clientAttemptId: "timer-best-latest", scramble: "R U F", officialMs: 12_340, timerMs: 12_340},
            {...historyEntry, id: 1, clientAttemptId: "timer-best-entry", scramble: "TIMER_BEST R U", officialMs: 5_670, timerMs: 5_670},
        ];
        await mockProductionApi(page, {user: normalUser, historyEntries});
        await page.goto("/");

        const bestButton = page.locator(".dashboard-inline-stats").getByRole("button", {name: "Open best solve 5.67"});
        await expect(bestButton).toBeVisible();
        await bestButton.click();

        const solutionDialog = page.getByRole("dialog", {name: "Solve solution"});
        await expect(solutionDialog.locator(".modal-scramble")).toHaveText("TIMER_BEST R U");
    });

    test("opens Ao5 and Ao12 breakdowns from Timer, History, and Statistics", async ({page}) => {
        await mockProductionApi(page, {user: normalUser});
        await page.goto("/");

        const sidebarAo5 = page.locator(".statistics-card").getByRole("button", {name: /Open Ao5 breakdown/});
        await sidebarAo5.click();
        const sidebarBreakdown = page.getByRole("dialog", {name: "Ao5 breakdown"});
        await expect(sidebarBreakdown).toBeVisible();
        await page.keyboard.press("Escape");
        await expect(sidebarBreakdown).toHaveCount(0);
        await expect(sidebarAo5).toBeFocused();

        const timerAo5 = page.locator(".dashboard-inline-stats").getByRole("button", {name: /Open Ao5 breakdown/});
        await timerAo5.click();
        const timerBreakdown = page.getByRole("dialog", {name: "Ao5 breakdown"});
        await expect(timerBreakdown).toBeVisible();
        await expect(timerBreakdown).toContainText("1 of 5 solves");
        const solveRow = timerBreakdown.getByRole("button", {name: /Open solve 1,/});
        await solveRow.click();

        const solutionDialog = page.getByRole("dialog", {name: "Solve solution"});
        await expect(solutionDialog).toBeVisible();
        await expect(timerBreakdown).toBeVisible();
        const breakdownLayer = await page.locator(".rolling-average-backdrop").evaluate((node) => Number(getComputedStyle(node).zIndex));
        const solutionLayer = await page.locator(".solution-modal-backdrop").evaluate((node) => Number(getComputedStyle(node).zIndex));
        expect(solutionLayer).toBeGreaterThan(breakdownLayer);
        await page.keyboard.press("Escape");
        await expect(solutionDialog).toHaveCount(0);
        await expect(solveRow).toBeFocused();
        await page.keyboard.press("Escape");
        await expect(timerBreakdown).toHaveCount(0);
        await expect(timerAo5).toBeFocused();

        await page.getByRole("button", {name: "History"}).click();
        const historyStats = page.locator(".history-statistics .rail-stat");
        await expect(historyStats).toHaveCount(6);
        expect(await historyStats.evaluateAll((cells) =>
            cells.map((cell) => getComputedStyle(cell).borderRightWidth),
        )).toEqual(["1px", "1px", "1px", "1px", "1px", "0px"]);
        const historyAo12 = page.getByRole("button", {name: /Open Ao12 breakdown/});
        await historyAo12.click();
        const historyBreakdown = page.getByRole("dialog", {name: "Ao12 breakdown"});
        await expect(historyBreakdown).toBeVisible();
        await expect(historyBreakdown).toContainText("1 of 12 solves");
        await page.keyboard.press("Escape");
        await expect(historyBreakdown).toHaveCount(0);
        await expect(historyAo12).toBeFocused();

        await page.getByRole("button", {name: "Timer"}).click();
        await page.getByRole("button", {name: "More statistics"}).click();
        const statisticsDialog = page.getByRole("dialog", {name: "Statistics"});
        await expect(statisticsDialog).toBeVisible();
        const statisticsAo5 = statisticsDialog.getByRole("button", {name: /Open Ao5 breakdown/});
        await statisticsAo5.click();
        const nestedBreakdown = page.getByRole("dialog", {name: "Ao5 breakdown"});
        await expect(nestedBreakdown).toBeVisible();
        const statisticsSolveRow = nestedBreakdown.getByRole("button", {name: /Open solve 1,/});
        await statisticsSolveRow.click();
        const nestedSolution = page.getByRole("dialog", {name: "Solve solution"});
        await expect(nestedSolution).toBeVisible();
        await expect(nestedBreakdown).toBeVisible();
        await expect(statisticsDialog).toBeVisible();
        await page.keyboard.press("Escape");
        await expect(nestedSolution).toHaveCount(0);
        await expect(statisticsSolveRow).toBeFocused();
        await page.keyboard.press("Escape");
        await expect(nestedBreakdown).toHaveCount(0);
        await expect(statisticsDialog).toBeVisible();
        await expect(statisticsAo5).toBeFocused();
        await page.keyboard.press("Escape");
        await expect(statisticsDialog).toHaveCount(0);
    });

    test("loads the Three.js chunk only when a cube preview nears the viewport", async ({page}) => {
        await page.addInitScript(() => {
            type ObserverHandle = {notify: (isIntersecting: boolean) => void};
            const handles: ObserverHandle[] = [];
            Object.defineProperty(window, "__cubePreviewObservers", {value: handles});
            Object.defineProperty(window, "IntersectionObserver", {
                configurable: true,
                value: class ControlledIntersectionObserver {
                    private target: Element | null = null;

                    constructor(private readonly callback: IntersectionObserverCallback) {
                        handles.push({
                            notify: (isIntersecting) => {
                                if (!this.target) throw new Error("Preview observer has no target");
                                const rect = this.target.getBoundingClientRect();
                                this.callback([{
                                    isIntersecting,
                                    target: this.target,
                                    boundingClientRect: rect,
                                    intersectionRatio: isIntersecting ? 1 : 0,
                                    intersectionRect: rect,
                                    rootBounds: null,
                                    time: performance.now(),
                                }], this as unknown as IntersectionObserver);
                            },
                        });
                    }

                    observe(target: Element) { this.target = target; }
                    disconnect() {}
                    unobserve() {}
                    takeRecords() { return []; }
                },
            });
        });

        const rendererRequests: string[] = [];
        page.on("request", (request) => {
            const url = request.url();
            if (url.includes("three-vendor-") || /\/src\/CubePreview\.tsx(?:\?|$)/.test(url)) {
                rendererRequests.push(url);
            }
        });

        await mockProductionApi(page, {user: normalUser});
        await page.goto("/");
        await expect(page.getByRole("button", {name: "Generate new scramble"})).toBeVisible();
        await expect.poll(() => page.evaluate(() => (window as Window & {__cubePreviewObservers: unknown[]}).__cubePreviewObservers.length)).toBeGreaterThan(0);
        expect(rendererRequests).toEqual([]);

        await page.evaluate(() => {
            const observers = (window as Window & {__cubePreviewObservers: Array<{notify: (visible: boolean) => void}>}).__cubePreviewObservers;
            observers[0].notify(true);
        });
        await expect.poll(() => rendererRequests.length).toBeGreaterThan(0);
        await expect(page.locator(".scramble-cube canvas")).toBeVisible();
    });

    test("generates scrambles through the split cubing worker", async ({page}) => {
        const pageErrors: Error[] = [];
        const workerAssetRequests: string[] = [];
        page.on("pageerror", (error) => pageErrors.push(error));
        page.on("request", (request) => {
            const pathname = new URL(request.url()).pathname;
            if (pathname.startsWith("/scramble-worker/")) workerAssetRequests.push(pathname);
        });

        await mockProductionApi(page, {user: normalUser});
        await page.goto("/");

        const scramble = page.locator(".dashboard-scramble-card p");
        await expect(scramble).not.toHaveText("Preparing scramble…");
        const initialScramble = (await scramble.textContent())?.trim();

        expect(initialScramble).toBeTruthy();
        expect(initialScramble).not.toBe("R D R' D2 R D' R'");
        expect(workerAssetRequests).toContain("/scramble-worker/scramble-worker.js");
        expect(workerAssetRequests.some((path) => path.includes("/chunks/search-worker-entry-"))).toBe(true);
        expect(workerAssetRequests.every((path) => path.startsWith("/scramble-worker/"))).toBe(true);

        await page.getByRole("button", {name: "Generate new scramble"}).click();
        await expect.poll(async () => (await scramble.textContent())?.trim()).not.toBe(initialScramble);
        expect((await scramble.textContent())?.trim()).toBeTruthy();
        expect(pageErrors.filter((error) => error.message.includes("document is not defined"))).toEqual([]);
    });

    test("keeps the normal-user dashboard scoped to user features", async ({page}) => {
        await mockProductionApi(page, {user: normalUser});
        await page.goto("/");

        await expect(page.getByRole("button", {name: "Timer"})).toBeVisible();
        await expect(page.getByRole("button", {name: "History"})).toBeVisible();
        await expect(page.getByRole("button", {name: "Solutions"})).toBeVisible();
        await expect(page.getByRole("button", {name: "Algorithms"})).toHaveCount(0);
        await expect(page.locator(".sidebar-user")).toHaveText(normalUser.displayName);
        await expect(page.getByLabel("Solve timer")).toBeVisible();
        await expect(page.getByLabel("Solve timer").getByText("Best")).toBeVisible();
    });

    test("shows completed timer work in the solutions queue", async ({page}) => {
        await mockProductionApi(page, {user: normalUser});
        await page.goto("/");

        await expect(page.getByRole("button", {name: "Show solution"})).toBeEnabled();
        await page.getByRole("button", {name: "Solutions"}).click();

        await expect(page.getByRole("heading", {name: "Active Solutions"})).toBeVisible();
        await expect(page.getByText("Recent")).toBeVisible();
        await expect(page.getByText(/Timer/).last()).toBeVisible();
        await expect(page.getByRole("button", {name: "View solution"}).first()).toBeVisible();
    });

    test("persists timer settings across dashboard navigation", async ({page}) => {
        await mockProductionApi(page, {user: normalUser});
        await page.goto("/");

        await page.getByRole("button", {name: "Settings"}).click();
        await expect(page.getByRole("heading", {name: "Settings"})).toBeVisible();

        const inspection = page.getByRole("switch", {name: "Inspection time"});
        const deepColorNeutral = page.getByRole("switch", {name: "Deep color-neutral optimization"});
        const deadline = page.getByLabel("Solution computation time limit in seconds");
        const previewGroup = page.getByRole("group", {name: "Cube preview"});
        const playbackGroup = page.getByRole("group", {name: "Cube playback"});
        const twoDimensionalPreview = previewGroup.getByRole("button", {name: "2D"});
        await inspection.uncheck();
        await deepColorNeutral.check();
        await deadline.fill("45");
        await twoDimensionalPreview.click();
        await expect(inspection).not.toBeChecked();
        await expect(deepColorNeutral).toBeChecked();
        await expect(deadline).toHaveValue("45");
        await expect(playbackGroup.getByRole("button", {name: "3D"})).toHaveAttribute("aria-pressed", "true");

        await page.getByRole("button", {name: "Timer"}).click();
        await page.getByRole("button", {name: "Settings"}).click();
        await expect(page.getByRole("switch", {name: "Inspection time"})).not.toBeChecked();
        await expect(page.getByRole("switch", {name: "Deep color-neutral optimization"})).toBeChecked();
        await expect(page.getByLabel("Solution computation time limit in seconds")).toHaveValue("45");
        await expect(previewGroup.getByRole("button", {name: "2D"})).toHaveAttribute("aria-pressed", "true");
        await expect(playbackGroup.getByRole("button", {name: "3D"})).toHaveAttribute("aria-pressed", "true");

        await page.getByRole("button", {name: "Timer"}).click();
        await page.getByRole("button", {name: "Show solution"}).click();
        await expect(page.getByRole("heading", {name: "3D Playback"})).toBeVisible();
        await expect(page.getByLabel("Interactive cube preview. Drag to rotate the cube view.")).toBeVisible();
    });

    test("sends deep optimization only for optimized color-neutral solves", async ({page}) => {
        const api = await mockProductionApi(page, {user: normalUser});
        await page.goto("/");

        await page.getByRole("button", {name: "Settings"}).click();
        await page.getByRole("switch", {name: "Deep color-neutral optimization"}).check();
        await page.getByRole("button", {name: "Timer"}).click();

        await page.getByRole("button", {name: "Optimized"}).click();
        await page.locator(".workspace-toolbar .cross-face-trigger").click();
        await page.getByRole("option", {name: "Color Neutral"}).click();
        await expect.poll(() => api.requests.filter((request) =>
            request.pathname === "/api/solve-jobs" && request.method === "POST"
        ).some((request) => (request.body as Record<string, unknown> | null)?.deepColorNeutral === true)).toBe(true);

        const solveRequests = api.requests.filter((request) =>
            request.pathname === "/api/solve-jobs" && request.method === "POST"
        );
        expect(solveRequests.some((request) => {
            const body = request.body as Record<string, unknown>;
            return body.crossFace === "U" && body.f2lMode === "optimized" && body.deepColorNeutral === undefined;
        })).toBe(true);
        expect(solveRequests.some((request) => {
            const body = request.body as Record<string, unknown>;
            return body.crossFace === "CN" && body.f2lMode === "optimized" && body.deepColorNeutral === true;
        })).toBe(true);
    });
});
