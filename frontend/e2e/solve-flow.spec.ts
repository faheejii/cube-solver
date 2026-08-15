import {expect, test} from "@playwright/test";
import {historyEntry, mockProductionApi, normalUser} from "./production-fixtures";

test.describe("timer, solve, history, and playback production flows", () => {
    test("runs a timed solve, persists it, and opens the current solution playback", async ({page}) => {
        const api = await mockProductionApi(page, {user: normalUser});
        await page.goto("/");

        await expect(page.getByRole("button", {name: "Show solution"})).toBeEnabled();
        const timer = page.getByLabel("Solve timer");

        await page.keyboard.press("Space");
        await expect(timer).toHaveClass(/phase-inspection/);
        await page.keyboard.press("Space");
        await expect(timer).toHaveClass(/phase-running/);
        await page.keyboard.press("Space");
        await expect(timer).toHaveClass(/phase-stopped/);

        const saveToast = page.getByRole("status");
        await expect(saveToast).toContainText(/Solve saved · \d+\.\d{2}/);
        await expect(saveToast).toHaveCount(1);
        expect(api.requests.some((request) => request.pathname === "/api/solves" && request.method === "POST")).toBe(true);
        await expect.poll(() => api.requests.some((request) => request.pathname.endsWith("/solutions/greedy") && request.method === "PUT")).toBe(true);

        await page.getByRole("button", {name: "History"}).click();
        await expect(page.getByRole("heading", {name: "History"})).toBeVisible();
        await expect(saveToast).toContainText(/Solve saved · \d+\.\d{2}/);
        await saveToast.getByRole("button", {name: "Dismiss notification"}).click();
        await expect(saveToast).toHaveCount(0);

        await page.getByRole("button", {name: "Timer"}).click();

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

        await dialog.locator('[aria-label="Animation stage"]').getByRole("button", {name: "OLL"}).click();
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
        await expect(dialog.getByText("Playback")).toBeVisible();
        expect(api.requests.some((request) => request.pathname === "/api/solves/1" && request.method === "GET")).toBe(true);

        await dialog.locator(".solution-stage-row").filter({hasText: "F2L"}).getByRole("button").click();
        await expect(dialog.locator(".solution-stage-row").filter({hasText: "F2L"}).getByRole("button")).toHaveAttribute("aria-expanded", "true");
        await dialog.locator('[aria-label="Animation stage"]').getByRole("button", {name: "F2L"}).click();
        await expect(dialog.locator("[data-preview-setup]")).toHaveAttribute("data-preview-setup", "R U R' U' R");
        await expect(dialog.locator("[data-playback-alg]")).toHaveAttribute("data-playback-alg", "U R U'");
        await dialog.getByRole("button", {name: "Close solution"}).click();
    });
});
