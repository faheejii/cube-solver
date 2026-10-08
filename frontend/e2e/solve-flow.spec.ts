import {expect, test} from "@playwright/test";
import {historyEntry, mockProductionApi, normalUser} from "./production-fixtures";

test.describe("timer, solve, history, and playback production flows", () => {
    test("searches scrambles and displayed solve times, with URL-restorable queries", async ({page}) => {
        const historyEntries = [
            {...historyEntry, id: 3, scramble: "R U R2", penalty: "+2", timerMs: 12_340, officialMs: 14_340},
            {...historyEntry, id: 2, scramble: "F U F2", penalty: "none", timerMs: 9_210, officialMs: 9_210},
            {...historyEntry, id: 1, scramble: "R U F'", penalty: "dnf", dnf: true, officialMs: null},
            {...historyEntry, id: 4, scramble: "L U L2", penalty: "none", timerMs: 210, officialMs: 210},
            {...historyEntry, id: 5, scramble: "B U B2", penalty: "none", timerMs: 81_450, officialMs: 81_450},
        ];
        const api = await mockProductionApi(page, {user: normalUser, historyEntries});
        await page.goto("/");
        await page.getByRole("button", {name: "History"}).click();

        const search = page.getByRole("searchbox", {name: "Search solves"});
        await search.focus();
        await expect(search).toHaveCSS("outline-style", "none");
        await expect(page.locator(".history-search-field")).toHaveCSS("box-shadow", "none");
        const [searchIconColor, defaultSearchColor] = await page.locator(".history-search-field").evaluate((field) => [
            getComputedStyle(field.querySelector("svg")!).color,
            getComputedStyle(field).color,
        ]);
        expect(searchIconColor).not.toBe(defaultSearchColor);
        await search.fill("r u");
        await expect(page.getByText("Showing 2 of 2 matching solves")).toBeVisible();
        await expect(page.locator(".history-table-row")).toHaveCount(2);
        await expect(page).toHaveURL(/\?q=r\+u$/);
        await expect(page.getByRole("combobox", {name: "Filter solves by penalty"})).toHaveCount(0);
        const filteredRequests = api.requests.filter((request) => request.pathname === "/api/solves" && request.method === "GET");
        expect(filteredRequests.some((request) => {
            const params = new URLSearchParams(request.search);
            return params.get("q") === "r u" && !params.has("penalty");
        })).toBe(true);

        const help = page.getByRole("button", {name: "Search syntax help"});
        await help.focus();
        await expect(page.getByRole("tooltip")).toContainText("M:SS.CC");
        await expect(page.getByRole("tooltip")).toContainText("*:21.*");

        await search.fill("14.34+");
        await expect(page.getByText("Showing 1 of 1 matching solves")).toBeVisible();
        await expect(page.locator(".history-table-row")).toContainText("14.34+");
        await expect(page).toHaveURL(/\?q=14\.34%2B$/);

        await search.fill("*.21");
        await expect(page.getByText("Showing 2 of 2 matching solves")).toBeVisible();
        await expect(page.locator(".history-table-row").filter({hasText: "0.21"})).toBeVisible();
        await search.fill("9.*");
        await expect(page.getByText("Showing 1 of 1 matching solves")).toBeVisible();
        await expect(page.locator(".history-table-row").filter({hasText: "9.21"})).toBeVisible();
        await search.fill("*:21.*");
        await expect(page.getByText("Showing 1 of 1 matching solves")).toBeVisible();
        await expect(page.locator(".history-table-row").filter({hasText: "1:21.45"})).toBeVisible();
        await search.fill("DNF");
        await expect(page.getByText("Showing 1 of 1 matching solves")).toBeVisible();
        await expect(page.locator(".history-table-row").filter({hasText: "DNF"})).toBeVisible();

        await search.fill("*.2");
        await expect(page.getByText("Use S.CC or M:SS.CC for a time")).toBeVisible();
        await expect(page.locator(".history-table-row")).toHaveCount(0);

        await search.fill("r u");
        await expect(page.getByText("Showing 2 of 2 matching solves")).toBeVisible();
        await expect(page).toHaveURL(/\?q=r\+u$/);

        await page.locator(".history-row-open").first().click();
        await expect(page.getByRole("dialog", {name: "Solve solution"}).locator(".solution-modal-context"))
            .toContainText("Matching result #2");
    });

    test("runs a timed solve, persists it, and opens the current solution playback", async ({page}) => {
        const api = await mockProductionApi(page, {user: normalUser});
        await page.goto("/");

        await expect(page.getByRole("button", {name: "Delete most recent solve"})).toHaveCount(0);
        await expect(page.getByRole("button", {name: "Show solution"})).toBeEnabled();
        const timer = page.getByLabel("Solve timer");
        const expectTimerOutline = async (color: string) => {
            await expect.poll(() => timer.evaluate((element) => {
                const style = getComputedStyle(element);
                return [style.borderTopColor, style.borderRightColor, style.borderBottomColor, style.borderLeftColor];
            })).toEqual([color, color, color, color]);
        };

        await timer.click();
        await expect(timer).toHaveClass(/phase-idle/);
        const holdStatus = page.locator(".timer-start-capsule");
        await page.keyboard.down("Space");
        await page.waitForTimeout(150);
        await expect(timer).toHaveClass(/phase-armed/);
        await expect(timer).not.toHaveClass(/phase-armed-ready/);
        await expect(holdStatus).toContainText("Hold Space…");
        await page.keyboard.up("Space");
        await expect(timer).toHaveClass(/phase-idle/);

        await page.keyboard.down("Space");
        await page.waitForTimeout(550);
        await expect(timer).toHaveClass(/phase-armed-ready/);
        await expectTimerOutline("rgb(47, 145, 214)");
        await expect(holdStatus).toContainText("Ready");
        await expect(holdStatus).toContainText("Release to inspect");
        await page.keyboard.up("Space");
        await expect(timer).toHaveClass(/phase-inspection/);

        await page.keyboard.down("Space");
        await page.waitForTimeout(550);
        await expect(timer).toHaveClass(/phase-armed-ready/);
        await expect(holdStatus).toContainText("Release to start solve");
        await page.keyboard.up("Space");
        await expect(timer).toHaveClass(/phase-running/);
        await expectTimerOutline("rgb(98, 189, 145)");
        await timer.click();
        await expect(timer).toHaveClass(/phase-running/);
        await page.keyboard.press("a");

        const saveToast = page.getByRole("status");
        await expect(saveToast).toContainText(/Solve saved · \d+\.\d{2}/);
        const savedTime = (await saveToast.innerText()).match(/Solve saved · (\d+\.\d{2})/)?.[1];
        expect(savedTime).toBeTruthy();
        await expect(page.locator(".dashboard-timer-number")).toHaveText(savedTime!);
        await expect(saveToast).toHaveCount(1);
        const deleteSavedSolve = page.getByRole("button", {name: "Delete most recent solve"});
        await expect(deleteSavedSolve).toBeVisible();

        const penalty = page.getByRole("group", {name: "Penalty for most recent solve"});
        await expect(penalty.getByRole("button", {name: "No penalty"})).toHaveAttribute("aria-pressed", "true");
        await penalty.getByRole("button", {name: "+2"}).click();
        await expect(page.locator(".dashboard-timer-number")).toHaveText(/\d+\.\d{2}\+/);
        await penalty.getByRole("button", {name: "DNF"}).click();
        await expect(page.locator(".dashboard-timer-number")).toHaveText("DNF");
        await expect(page.locator(".statistics-card .rail-stat").filter({hasText: "DNFs"}).locator("strong")).toHaveText("1");
        await penalty.getByRole("button", {name: "No penalty"}).click();
        await expect(page.locator(".dashboard-timer-number")).toHaveText(savedTime!);
        await expect(page.locator(".statistics-card .rail-stat").filter({hasText: "DNFs"}).locator("strong")).toHaveText("0");
        expect(api.requests.filter((request) => request.pathname.endsWith("/penalty")).map((request) => request.body)).toEqual([
            {penalty: "+2"},
            {penalty: "dnf"},
            {penalty: "none"},
        ]);
        expect(api.requests.some((request) => request.pathname === "/api/solves" && request.method === "POST")).toBe(true);
        await expect.poll(() => api.requests.some((request) => request.pathname.endsWith("/solutions/greedy") && request.method === "PUT")).toBe(true);
        const solutionSave = api.requests.find((request) => request.pathname.endsWith("/solutions/greedy") && request.method === "PUT");
        expect(solutionSave?.body).toMatchObject({
            f2lTraceJson: {
                traceComplete: expect.any(Boolean),
                pairAlgorithmMatchesStage: expect.any(Boolean),
                pairs: expect.any(Array),
            },
        });

        await page.getByRole("button", {name: "History"}).click();
        await expect(page.getByRole("heading", {name: "History"})).toBeVisible();
        await expect(page.locator(".history-table-row").first().locator(".history-time-cell strong")).toHaveText(savedTime!);
        await expect(saveToast).toContainText(/Solve saved · \d+\.\d{2}/);
        await saveToast.getByRole("button", {name: "Dismiss notification"}).click();
        await expect(saveToast).toHaveCount(0);

        await page.getByRole("button", {name: "Timer"}).click();
        await expect(page.locator(".dashboard-timer-number")).toHaveText(savedTime!);

        await page.getByRole("button", {name: "Show solution"}).click();
        const dialog = page.getByRole("dialog", {name: "Timer solution"});
        await expect(dialog).toBeVisible();
        await expect(dialog.getByText("Solution and summary")).toBeVisible();
        await expect(dialog.getByRole("region", {name: "Cube animation"})).toBeVisible();
        await expect(dialog.getByRole("button", {name: "Play playback"})).toBeVisible();
        await expect(dialog.locator("[data-playback-state]")).toHaveAttribute("data-playback-state", "paused");
        const preview = dialog.locator('[data-interactive-view="true"]');
        await expect(preview).toBeVisible();
        await preview.dragTo(preview, {
            sourcePosition: {x: 85, y: 95},
            targetPosition: {x: 145, y: 95},
        });
        await expect(dialog.getByRole("button", {name: "Play playback"})).toBeVisible();
        await dialog.getByRole("button", {name: "Play playback"}).click();
        await expect(dialog.getByRole("button", {name: "Pause playback"})).toBeVisible();

        await dialog.locator(".solution-stage-row-oll > button").click();
        await expect(dialog.locator("[data-playback-alg]")).toHaveAttribute("data-playback-alg", "F R U R' U' F'");
        await dialog.getByRole("button", {name: "Close solution"}).click();
        await expect(dialog).toHaveCount(0);

        await deleteSavedSolve.click();
        const deleteConfirmation = page.getByRole("alertdialog", {name: "Delete solve?"});
        await expect(deleteConfirmation).toContainText("This also deletes its saved Fast and Optimized solutions");
        await deleteConfirmation.getByRole("button", {name: "Delete solve"}).click();
        await expect(page.locator(".dashboard-timer-number")).toHaveText("12.34");
        await expect(deleteSavedSolve).toBeVisible();
        expect(api.requests.some((request) => request.method === "DELETE" && request.pathname === "/api/solves/2")).toBe(true);
    });

    test("loads a saved history solution and preserves stage playback setup", async ({page}) => {
        const api = await mockProductionApi(page, {user: normalUser, historyEntries: [historyEntry]});
        await page.goto("/");

        await page.getByRole("button", {name: "History"}).click();
        await expect(page.getByRole("heading", {name: "History"})).toBeVisible();
        const scramblePreview = page.locator(".history-scramble-preview").first();
        const previewSvg = scramblePreview.locator("svg.history-cube-thumbnail");
        await expect(previewSvg).toBeVisible();
        await expect(previewSvg).toHaveAttribute("data-preview-setup", historyEntry.scramble);
        await expect(scramblePreview.locator("canvas")).toHaveCount(0);
        await expect(previewSvg.locator("polygon")).toHaveCount(27);
        const previewBox = await scramblePreview.boundingBox();
        const svgBox = await previewSvg.boundingBox();
        const indexBox = await page.locator(".history-index").first().boundingBox();
        const timeBox = await page.locator(".history-time-cell").first().boundingBox();
        const rowBox = await page.locator(".history-table-row").first().boundingBox();
        expect(indexBox).not.toBeNull();
        expect(timeBox).not.toBeNull();
        expect(previewBox?.x).toBeGreaterThan(indexBox!.x);
        expect(previewBox!.x + previewBox!.width).toBeLessThan(timeBox!.x);
        expect(previewBox?.width).toBe(36);
        expect(previewBox?.height).toBe(36);
        expect(svgBox?.width).toBe(36);
        expect(svgBox?.height).toBe(36);
        expect(rowBox?.height).toBeLessThan(100);

        await page.setViewportSize({width: 390, height: 844});
        const mobilePreviewBox = await scramblePreview.boundingBox();
        const mobileSvgBox = await previewSvg.boundingBox();
        const mobileRowBox = await page.locator(".history-table-row").first().boundingBox();
        expect(mobilePreviewBox?.width).toBe(34);
        expect(mobilePreviewBox?.height).toBe(34);
        expect(mobileSvgBox?.width).toBe(34);
        expect(mobileSvgBox?.height).toBe(34);
        expect(mobileRowBox?.height).toBeLessThan(120);

        await page.setViewportSize({width: 1280, height: 720});
        await page.getByRole("button", {name: /Open solution for solve 12\.34/}).click();

        const dialog = page.getByRole("dialog", {name: "Solve solution"});
        await expect(dialog).toBeVisible();
        await expect(dialog.locator(".modal-scramble")).toHaveText(historyEntry.scramble);
        await expect(dialog.getByRole("heading", {name: "3D Playback"})).toBeVisible();
        expect(api.requests.some((request) => request.pathname === "/api/solves/1" && request.method === "GET")).toBe(true);

        const penalty = dialog.getByRole("group", {name: "Penalty for this solve"});
        await penalty.getByRole("button", {name: "+2"}).click();
        await expect(dialog.locator(".solution-modal-header h2")).toHaveText("14.34+");
        await penalty.getByRole("button", {name: "DNF"}).click();
        await expect(dialog.locator(".solution-modal-header h2")).toHaveText("DNF");
        await penalty.getByRole("button", {name: "No penalty"}).click();
        await expect(dialog.locator(".solution-modal-header h2")).toHaveText("12.34");
        expect(api.requests.filter((request) => request.pathname.endsWith("/penalty")).map((request) => request.body)).toEqual([
            {penalty: "+2"},
            {penalty: "dnf"},
            {penalty: "none"},
        ]);

        await dialog.locator(".solution-stage-row").filter({hasText: "F2L"}).getByRole("button").click();
        await expect(dialog.locator(".solution-stage-row").filter({hasText: "F2L"}).getByRole("button")).toHaveAttribute("aria-expanded", "true");
        await expect(dialog.locator("[data-preview-setup]")).toHaveAttribute("data-preview-setup", "R U R' U' R");
        await expect(dialog.locator("[data-playback-alg]")).toHaveAttribute("data-playback-alg", "U R U'");
        await dialog.getByRole("button", {name: "Close solution"}).click();
        await expect(page.locator(".history-table-row").first().locator(".history-time-cell strong")).toHaveText("12.34");
    });

    test("keeps SVG History cube thumbnails rendered and stable while scrolling", async ({page}) => {
        const pageErrors: string[] = [];
        page.on("pageerror", (error) => pageErrors.push(error.message));
        const historyEntries = Array.from({length: 20}, (_, index) => ({
            ...historyEntry,
            id: 20 - index,
            clientAttemptId: `attempt-${20 - index}`,
            createdAt: new Date(Date.UTC(2026, 6, 26, 10, 0, index)).toISOString(),
        }));
        await mockProductionApi(page, {user: normalUser, historyEntries});
        await page.goto("/");
        await page.getByRole("button", {name: "History"}).click();

        const rows = page.locator(".history-table-row");
        const firstRow = rows.first();
        const firstPreview = firstRow.locator(".history-scramble-preview");
        const firstSvg = firstPreview.locator("svg.history-cube-thumbnail");
        await expect(rows).toHaveCount(20);
        await expect(firstSvg).toBeVisible();
        await expect(firstPreview.locator("canvas")).toHaveCount(0);
        await expect(firstSvg.locator("polygon")).toHaveCount(27);
        const initialSignature = await firstSvg.getAttribute("data-render-state");
        const initialRowBox = await firstRow.boundingBox();
        expect(initialRowBox).not.toBeNull();

        await rows.last().scrollIntoViewIfNeeded();
        await expect(firstSvg).toHaveCount(1);
        await expect(firstSvg.locator("polygon")).toHaveCount(27);

        await firstRow.scrollIntoViewIfNeeded();
        await expect(firstSvg).toBeVisible();
        await expect(firstSvg).toHaveAttribute("data-render-state", initialSignature!);
        const returnedRowBox = await firstRow.boundingBox();
        expect(returnedRowBox?.height).toBe(initialRowBox?.height);
        expect(pageErrors).toEqual([]);
    });

    test("keeps the saved penalty and timer unchanged when a penalty update fails", async ({page}) => {
        const api = await mockProductionApi(page, {
            user: normalUser,
            historyEntries: [historyEntry],
            penaltyUpdateFailure: true,
        });
        await page.goto("/");

        const timerValue = page.locator(".dashboard-timer-number");
        await expect(timerValue).toHaveText("12.34");
        const penalty = page.getByRole("group", {name: "Penalty for most recent solve"});
        const noPenalty = penalty.getByRole("button", {name: "No penalty"});
        const plusTwo = penalty.getByRole("button", {name: "+2"});

        await plusTwo.click();

        await expect(page.getByRole("alert")).toHaveText("Penalty update failed");
        await expect(noPenalty).toHaveAttribute("aria-pressed", "true");
        await expect(plusTwo).toHaveAttribute("aria-pressed", "false");
        await expect(plusTwo).toBeEnabled();
        await expect(timerValue).toHaveText("12.34");
        expect(api.requests.filter((request) => request.pathname.endsWith("/penalty"))).toHaveLength(1);

        await page.getByRole("button", {name: "History"}).click();
        await page.getByRole("button", {name: /Open solution for solve 12\.34/}).click();
        const dialog = page.getByRole("dialog", {name: "Solve solution"});
        const modalPenalty = dialog.getByRole("group", {name: "Penalty for this solve"});
        await modalPenalty.getByRole("button", {name: "+2"}).click();
        await expect(dialog.getByRole("alert")).toHaveText("Penalty update failed");
        await expect(modalPenalty.getByRole("button", {name: "No penalty"})).toHaveAttribute("aria-pressed", "true");
        await expect(dialog.locator(".solution-modal-header h2")).toHaveText("12.34");
        expect(api.requests.filter((request) => request.pathname.endsWith("/penalty"))).toHaveLength(2);
    });

    test("deletes a saved solve from the right-side history popup", async ({page}) => {
        const api = await mockProductionApi(page, {user: normalUser, historyEntries: [historyEntry]});
        await page.goto("/");

        await page.getByRole("button", {name: /#1/}).click();
        const dialog = page.getByRole("dialog", {name: "Solve solution"});
        await expect(dialog).toBeVisible();

        await dialog.getByRole("button", {name: "Delete solve"}).click();
        const confirmation = page.getByRole("alertdialog", {name: "Delete solve?"});
        await expect(confirmation).toContainText("saved Fast and Optimized solutions");
        await confirmation.getByRole("button", {name: "Delete solve"}).click();

        await expect(dialog).toHaveCount(0);
        expect(api.requests.some((request) => request.pathname === "/api/solves/1" && request.method === "DELETE")).toBe(true);
    });

    test("keeps the delete confirmation open after an API failure and allows retry", async ({page}) => {
        const api = await mockProductionApi(page, {user: normalUser, historyEntries: [historyEntry], deleteFailures: 1});
        await page.goto("/");
        await page.getByRole("button", {name: "History"}).click();

        await page.getByRole("button", {name: "Delete solve 12.34"}).click();
        const confirmation = page.getByRole("alertdialog", {name: "Delete solve?"});
        await confirmation.getByRole("button", {name: "Delete solve"}).click();
        await expect(confirmation.getByRole("alert")).toHaveText("Delete failed. Please retry.");
        await expect(confirmation).toBeVisible();

        await confirmation.getByRole("button", {name: "Delete solve"}).click();
        await expect(confirmation).toHaveCount(0);
        await expect(page.locator(".history-table-row")).toHaveCount(0);
        expect(api.requests.filter((request) => request.pathname === "/api/solves/1" && request.method === "DELETE")).toHaveLength(2);
    });
});
