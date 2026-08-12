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
});
