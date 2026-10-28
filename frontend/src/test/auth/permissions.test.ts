import {describe, expect, it} from "vitest";
import {canAccessCapability} from "../../permissions";

describe("dashboard capability access", () => {
    it("allows Algorithms for maintainers and admins", () => {
        expect(canAccessCapability("user", "algorithms.view")).toBe(false);
        expect(canAccessCapability("maintainer", "algorithms.view")).toBe(true);
        expect(canAccessCapability("admin", "algorithms.view")).toBe(true);
    });

    it("limits user management to admins", () => {
        expect(canAccessCapability("user", "users.manage")).toBe(false);
        expect(canAccessCapability("maintainer", "users.manage")).toBe(false);
        expect(canAccessCapability("admin", "users.manage")).toBe(true);
        expect(canAccessCapability("unknown", "users.manage")).toBe(false);
    });
});
