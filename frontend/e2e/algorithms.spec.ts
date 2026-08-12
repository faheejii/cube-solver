import {expect, test} from "@playwright/test";
import {adminUser, mockProductionApi, normalUser} from "./production-fixtures";

test.describe("Algorithms access and catalog flow", () => {
    test("allows admins to filter, inspect, copy, and preview catalog cases", async ({page}) => {
        await mockProductionApi(page, {user: adminUser});
        await page.context().grantPermissions(["clipboard-read", "clipboard-write"]);
        await page.goto("/");

        await page.getByRole("button", {name: "Algorithms"}).click();
        await expect(page.getByRole("heading", {name: "Algorithms"})).toBeVisible();
        await expect(page.getByText("4 cases")).toBeVisible();

        const ollRow = page.locator(".algorithm-catalog-row").filter({hasText: "OLL Sune"});
        await expect(ollRow).toBeVisible();
        await expect(page.getByText("Experimental insert")).toHaveCount(0);

        await page.getByLabel("Algorithm phase").selectOption("oll");
        await expect(page.getByText("OLL Sune")).toBeVisible();
        await expect(page.getByText("F2L FR setup")).toHaveCount(0);

        await ollRow.getByText("Details").click();
        await expect(ollRow.getByText("OLL facelets: UFR UR UBR")).toBeVisible();

        await ollRow.getByRole("button", {name: "Copy algorithm"}).click();
        await expect(ollRow.getByRole("button", {name: "Copied"})).toBeVisible();
        await expect.poll(() => page.evaluate(() => navigator.clipboard.readText())).toBe("R U R' U R U2 R'");

        await ollRow.getByRole("button", {name: "Test on cube"}).click();
        const preview = page.getByRole("dialog", {name: "OLL Sune preview"});
        await expect(preview).toBeVisible();
        await expect(preview.getByText("Setup state:")).toBeVisible();
        await expect(preview.locator("twisty-player")).toHaveAttribute("experimental-setup-alg", "R U R' U R U2 R'");
        await preview.getByRole("button", {name: "Close"}).click();
        await expect(preview).toHaveCount(0);

        await page.getByLabel("Algorithm phase").selectOption("");
        await page.getByLabel("Algorithm status").selectOption("experimental");
        await expect(page.getByText("Experimental insert")).toBeVisible();
        await expect(page.getByText("OLL Sune")).toHaveCount(0);
    });

    test("does not expose the admin catalog to normal users", async ({page}) => {
        await mockProductionApi(page, {user: normalUser});
        await page.goto("/");

        await expect(page.getByRole("button", {name: "Algorithms"})).toHaveCount(0);
        await expect(page.getByRole("heading", {name: "Algorithms"})).toHaveCount(0);
    });
});
