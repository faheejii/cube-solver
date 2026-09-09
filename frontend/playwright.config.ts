import {defineConfig, devices} from "@playwright/test";

const usePreviewServer = process.env.PLAYWRIGHT_SERVER === "preview";

export default defineConfig({
    testDir: "./e2e",
    fullyParallel: true,
    forbidOnly: !!process.env.CI,
    retries: process.env.CI ? 2 : 0,
    reporter: process.env.CI ? "github" : "list",
    use: {
        baseURL: process.env.PLAYWRIGHT_BASE_URL ?? "http://127.0.0.1:4173",
        permissions: ["clipboard-read", "clipboard-write"],
        trace: "on-first-retry",
        ...devices["Desktop Chrome"],
    },
    webServer: process.env.PLAYWRIGHT_BASE_URL ? undefined : {
        command: usePreviewServer
            ? "npm run preview -- --host 127.0.0.1 --port 4173"
            : "npm run dev -- --host 127.0.0.1 --port 4173",
        url: "http://127.0.0.1:4173",
        reuseExistingServer: !process.env.CI && !usePreviewServer,
        timeout: 120_000,
    },
});
