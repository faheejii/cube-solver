import {expect, test} from "@playwright/test";
import {historyEntry, mockProductionApi, normalUser} from "./production-fixtures";

test.describe("timer, solve, history, and playback production flows", () => {
    test("runs a timed solve, persists it, and opens the current solution playback", async ({page}) => {
        const api = await mockProductionApi(page, {user: normalUser});
        await page.goto("/");

        await expect(page.getByRole("button", {name: "Show solution"})).toBeEnabled();
        const timer = page.getByLabel("Solve timer");

        await timer.click();
        await expect(timer).toHaveClass(/phase-idle/);
        await page.keyboard.press("Space");
        await expect(timer).toHaveClass(/phase-inspection/);
        await page.keyboard.press("Space");
        await expect(timer).toHaveClass(/phase-running/);
        await timer.click();
        await expect(timer).toHaveClass(/phase-running/);
        await page.keyboard.press("a");
        await expect(timer).toHaveClass(/phase-stopped/);

        const saveToast = page.getByRole("status");
        await expect(saveToast).toContainText(/Solve saved · \d+\.\d{2}/);
        const savedTime = (await saveToast.innerText()).match(/Solve saved · (\d+\.\d{2})/)?.[1];
        expect(savedTime).toBeTruthy();
        await expect(page.locator(".dashboard-timer-number")).toHaveText(savedTime!);
        await expect(saveToast).toHaveCount(1);

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
    });

    test("loads a saved history solution and preserves stage playback setup", async ({page}) => {
        const api = await mockProductionApi(page, {user: normalUser, historyEntries: [historyEntry]});
        await page.goto("/");

        await page.getByRole("button", {name: "History"}).click();
        await expect(page.getByRole("heading", {name: "History"})).toBeVisible();
        await page.getByRole("button", {name: "Solution", exact: true}).click();

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
        await page.getByRole("button", {name: "Solution", exact: true}).click();
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

        page.once("dialog", (confirmation) => confirmation.accept());
        await dialog.getByRole("button", {name: "Delete solve"}).click();

        await expect(dialog).toHaveCount(0);
        expect(api.requests.some((request) => request.pathname === "/api/solves/1" && request.method === "DELETE")).toBe(true);
    });
});
