import {render, screen, waitFor} from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import {beforeEach, describe, expect, it, vi} from "vitest";
import type {AuthUser} from "../types";

const api = vi.hoisted(() => ({
    fetchCurrentUser: vi.fn<() => Promise<AuthUser | null>>(),
    login: vi.fn(),
    logout: vi.fn(),
    register: vi.fn(),
}));

vi.mock("../api", () => ({
    ...api,
    SESSION_EXPIRED_EVENT: "cube-solver:session-expired",
}));

vi.mock("../App", () => ({
    default: ({user, onLogout}: {user: AuthUser; onLogout: () => void}) => (
        <div>
            <span>Signed in as {user.email}</span>
            <button type="button" onClick={onLogout}>Sign out</button>
        </div>
    ),
}));

import AuthenticatedApp from "../AuthenticatedApp";

const authenticatedUser: AuthUser = {
    id: "user-1",
    email: "cube@example.com",
    displayName: "Cube",
    role: "user",
};

describe("AuthenticatedApp", () => {
    beforeEach(() => {
        api.fetchCurrentUser.mockReset();
        api.login.mockReset();
        api.logout.mockReset();
        api.register.mockReset();
    });

    it("restores an existing session", async () => {
        api.fetchCurrentUser.mockResolvedValue(authenticatedUser);

        render(<AuthenticatedApp/>);

        expect(await screen.findByText("Signed in as cube@example.com")).toBeInTheDocument();
    });

    it("signs in from the login form", async () => {
        api.fetchCurrentUser.mockResolvedValue(null);
        api.login.mockResolvedValue(authenticatedUser);
        const user = userEvent.setup();
        render(<AuthenticatedApp/>);

        await user.type(await screen.findByLabelText("Email"), "cube@example.com");
        await user.type(screen.getByLabelText("Password"), "password123");
        await user.click(screen.getByRole("button", {name: "Sign in"}));

        await waitFor(() => expect(api.login).toHaveBeenCalledWith({
            email: "cube@example.com",
            password: "password123",
        }));
        expect(await screen.findByText("Signed in as cube@example.com")).toBeInTheDocument();
    });

    it("returns to authentication after logout", async () => {
        api.fetchCurrentUser.mockResolvedValue(authenticatedUser);
        api.logout.mockResolvedValue(undefined);
        const user = userEvent.setup();
        render(<AuthenticatedApp/>);

        await user.click(await screen.findByRole("button", {name: "Sign out"}));

        expect(await screen.findByRole("button", {name: "Sign in"})).toBeInTheDocument();
    });
});
