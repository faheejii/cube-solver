import {expect, test, type Page, type Route} from "@playwright/test";

const user = {
    id: "user-1",
    email: "solver@example.com",
    displayName: "Test Solver",
};

const historyEntry = {
    id: 1,
    clientAttemptId: "attempt-1",
    scramble: "R U R' U'",
    crossFaceRequested: "U",
    timerMs: 12_340,
    officialMs: 12_340,
    penalty: "none",
    dnf: false,
    fastCrossFaceRequested: "U",
    optimizedCrossFaceRequested: "U",
    createdAt: "2026-07-26T10:00:00Z",
};

type MockOptions = {
    authenticated?: boolean;
    historyStatus?: number;
    loginStatus?: number;
    loginError?: string;
};

async function json(route: Route, body: unknown, status = 200) {
    await route.fulfill({
        status,
        contentType: "application/json",
        body: JSON.stringify(body),
    });
}

async function mockApi(page: Page, options: MockOptions = {}) {
    let authenticated = options.authenticated ?? false;
    let historyStatus = options.historyStatus ?? 200;
    await page.route("**/api/**", async (route) => {
        const request = route.request();
        const url = new URL(request.url());

        if (url.pathname === "/api/auth/me") {
            await json(route, authenticated ? user : {error: "Authentication required"}, authenticated ? 200 : 401);
            return;
        }
        if (url.pathname === "/api/auth/register") {
            authenticated = true;
            await json(route, user);
            return;
        }
        if (url.pathname === "/api/auth/login") {
            if ((options.loginStatus ?? 200) !== 200) {
                await json(route, {error: options.loginError ?? "Invalid email or password"}, options.loginStatus);
                return;
            }
            authenticated = true;
            await json(route, user);
            return;
        }
        if (url.pathname === "/api/auth/logout") {
            authenticated = false;
            await route.fulfill({status: 204, body: ""});
            return;
        }
        if (url.pathname === "/api/solves" && request.method() === "GET") {
            if (historyStatus !== 200) {
                authenticated = false;
                await json(route, {error: "Authentication required"}, historyStatus);
                return;
            }
            await json(route, {items: [historyEntry], nextCursor: null});
            return;
        }
        if (url.pathname === "/api/stats") {
            await json(route, {
                solveCount: 1,
                dnfCount: 0,
                bestMs: 12_340,
                averageMs: 12_340,
                ao5: {status: "insufficient", valueMs: null},
                ao12: {status: "insufficient", valueMs: null},
                recentSolves: [historyEntry],
            });
            return;
        }
        await json(route, {});
    });
}

test.describe("authentication", () => {
    test("registers a new account", async ({page}) => {
        await mockApi(page);
        await page.goto("/");

        await page.getByRole("button", {name: "New here? Create an account"}).click();
        await expect(page.getByRole("heading", {name: "Create your account"})).toBeVisible();
        await page.getByLabel("Display name").fill("Test Solver");
        await page.getByLabel("Email").fill(user.email);
        await page.getByLabel("Password").fill("correct horse battery");
        await page.getByRole("button", {name: "Create account"}).click();

        await expect(page.getByRole("button", {name: "Sign out"})).toBeVisible();
        await expect(page.locator(".sidebar-user")).toHaveText(user.displayName);
    });

    test("logs in and presents API errors", async ({page}) => {
        await mockApi(page, {loginStatus: 401, loginError: "Invalid email or password"});
        await page.goto("/");

        await page.getByLabel("Email").fill(user.email);
        await page.getByLabel("Password").fill("wrong password");
        await page.getByRole("button", {name: "Sign in"}).click();

        await expect(page.getByRole("alert")).toHaveText("Invalid email or password");
        await expect(page.getByRole("button", {name: "Sign in"})).toBeEnabled();
    });

    test("restores an existing session", async ({page}) => {
        await mockApi(page, {authenticated: true});
        await page.goto("/");

        await expect(page.getByRole("button", {name: "Sign out"})).toBeVisible();
        await expect(page.locator(".sidebar-user")).toHaveText(user.displayName);
        await expect(page.getByRole("heading", {name: "Welcome back"})).toHaveCount(0);
    });

    test("logs out and removes the protected dashboard", async ({page}) => {
        await mockApi(page, {authenticated: true});
        await page.goto("/");
        await page.getByRole("button", {name: "Sign out"}).click();

        await expect(page.getByRole("heading", {name: "Welcome back"})).toBeVisible();
        await expect(page.getByRole("button", {name: "History"})).toHaveCount(0);
    });
});

test.describe("protected history", () => {
    test("shows history only for an authenticated session", async ({page}) => {
        await mockApi(page, {authenticated: true});
        await page.goto("/");
        await page.getByRole("button", {name: "History"}).click();

        await expect(page.getByRole("heading", {name: "History"})).toBeVisible();
        await expect(page.getByText("R U R' U'")).toBeVisible();
        await expect(
            page.locator(".history-table-row").filter({hasText: "R U R' U'"}).getByText("12.34")
        ).toBeVisible();
    });

    test("returns to login when a protected request reports session expiry", async ({page}) => {
        await mockApi(page, {authenticated: true, historyStatus: 401});
        await page.goto("/");
        await page.getByRole("button", {name: "History"}).click();

        await expect(page.getByText("Your session expired. Sign in again to continue.")).toBeVisible();
        await expect(page.getByRole("heading", {name: "Welcome back"})).toBeVisible();
        await expect(page.getByRole("button", {name: "History"})).toHaveCount(0);
    });
});
