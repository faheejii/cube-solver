import {expect, test} from "@playwright/test";
import {mockProductionApi, normalUser} from "./production-fixtures";

test.describe("authenticated dashboard production flow", () => {
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
        await inspection.uncheck();
        await deepColorNeutral.check();
        await deadline.fill("45");
        await expect(inspection).not.toBeChecked();
        await expect(deepColorNeutral).toBeChecked();
        await expect(deadline).toHaveValue("45");

        await page.getByRole("button", {name: "Timer"}).click();
        await page.getByRole("button", {name: "Settings"}).click();
        await expect(page.getByRole("switch", {name: "Inspection time"})).not.toBeChecked();
        await expect(page.getByRole("switch", {name: "Deep color-neutral optimization"})).toBeChecked();
        await expect(page.getByLabel("Solution computation time limit in seconds")).toHaveValue("45");
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
