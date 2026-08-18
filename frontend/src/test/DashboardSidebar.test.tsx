import {render, screen} from "@testing-library/react";
import {describe, expect, it, vi} from "vitest";
import DashboardSidebar from "../DashboardSidebar";
import type {AuthUser} from "../types";

const baseUser: AuthUser = {
    id: "user-1",
    email: "user@example.com",
    displayName: "User",
    role: "user",
};

function renderSidebar(user: AuthUser) {
    return render(
        <DashboardSidebar
            activeView="timer"
            activeProcessCount={0}
            user={user}
            onViewChange={vi.fn()}
            onLogout={vi.fn()}
        />
    );
}

describe("DashboardSidebar roles", () => {
    it("hides Algorithms from normal users", () => {
        renderSidebar(baseUser);
        expect(screen.queryByRole("button", {name: "Algorithms"})).not.toBeInTheDocument();
    });

    it("shows Algorithms to admins", () => {
        renderSidebar({...baseUser, role: "admin"});
        expect(screen.getByRole("button", {name: "Algorithms"})).toBeInTheDocument();
    });

    it("shows Settings to every signed-in user", () => {
        renderSidebar(baseUser);
        expect(screen.getByRole("button", {name: "Settings"})).toBeInTheDocument();
    });
});
