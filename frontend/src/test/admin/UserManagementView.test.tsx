import {act, fireEvent, render, screen, waitFor, within} from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import {afterEach, beforeEach, describe, expect, it, vi} from "vitest";
import {ApiError} from "../../api";
import type {AdminManagedUser, AdminUsersResponse} from "../../api";
import type {AuthUser} from "../../types";

const api = vi.hoisted(() => ({
    fetchAdminUsers: vi.fn(),
    updateAdminUserRole: vi.fn(),
}));

vi.mock("../../api", async (importOriginal) => ({
    ...await importOriginal<typeof import("../../api")>(),
    ...api,
}));

import UserManagementView from "../../UserManagementView";

const currentAdmin: AuthUser = {
    id: "external-admin",
    email: "admin@example.com",
    displayName: "Admin",
    role: "admin",
};

const adminAccount: AdminManagedUser = {
    id: 1,
    externalId: "external-admin",
    displayName: "Admin",
    email: "admin@example.com",
    role: "admin",
    protected: true,
    updatedAt: "2026-10-28T10:00:00Z",
};

const memberAccount: AdminManagedUser = {
    id: 2,
    externalId: "external-member",
    displayName: "Casey Member",
    email: "casey@example.com",
    role: "user",
    protected: false,
    updatedAt: "2026-10-28T11:00:00Z",
};

function page(items: AdminManagedUser[], nextCursor: string | null = null): AdminUsersResponse {
    return {items, nextCursor};
}

function renderView(onAuthorizationFailure = vi.fn()) {
    return render(<UserManagementView currentUser={currentAdmin} onAuthorizationFailure={onAuthorizationFailure}/>);
}

describe("UserManagementView", () => {
    beforeEach(() => {
        api.fetchAdminUsers.mockReset().mockResolvedValue(page([adminAccount, memberAccount]));
        api.updateAdminUserRole.mockReset();
    });

    afterEach(() => vi.useRealTimers());

    it("shows protected and self roles as read-only", async () => {
        renderView();

        expect(await screen.findByText("Casey Member")).toBeInTheDocument();
        expect(screen.getByText("Protected account")).toBeInTheDocument();
        const selfRoleNote = screen.getByText("Your own role cannot be changed here");
        const selfRole = screen.getByRole("combobox", {name: "Role for admin@example.com"});
        expect(selfRoleNote.parentElement).toHaveClass("admin-user-role-control");
        expect(selfRole).toHaveAttribute("aria-describedby", "admin-user-self-role-note-1");
        expect(selfRole).toBeDisabled();
        expect(screen.getByRole("combobox", {name: "Role for casey@example.com"})).toBeEnabled();
    });

    it("debounces literal search, avoids intermediate IME requests, and clears immediately", async () => {
        vi.useFakeTimers();
        renderView();
        await act(async () => { await Promise.resolve(); });
        const search = screen.getByRole("textbox", {name: "Search name or email"});
        expect(api.fetchAdminUsers).toHaveBeenCalledTimes(1);

        fireEvent.compositionStart(search);
        fireEvent.change(search, {target: {value: "田中"}});
        await act(async () => { vi.advanceTimersByTime(600); });
        expect(api.fetchAdminUsers).toHaveBeenCalledTimes(1);

        fireEvent.compositionEnd(search, {data: "田中"});
        await act(async () => {
            vi.advanceTimersByTime(300);
            await Promise.resolve();
        });
        expect(api.fetchAdminUsers).toHaveBeenLastCalledWith("田中");

        fireEvent.click(screen.getByRole("button", {name: "Clear user search"}));
        await act(async () => { await Promise.resolve(); });
        expect(api.fetchAdminUsers).toHaveBeenLastCalledWith("");
        expect(search).toHaveValue("");
        expect(search).toHaveFocus();
    });

    it("appends cursor results without duplicate accounts", async () => {
        api.fetchAdminUsers
            .mockResolvedValueOnce(page([adminAccount], "next-page"))
            .mockResolvedValueOnce(page([adminAccount, memberAccount]));
        const user = userEvent.setup();
        renderView();

        expect(await screen.findByText("Admin", {selector: ".admin-user-name"})).toBeInTheDocument();
        await user.click(screen.getByRole("button", {name: "Load more"}));

        expect(await screen.findByText("Casey Member")).toBeInTheDocument();
        expect(screen.getAllByText("admin@example.com")).toHaveLength(1);
        expect(api.fetchAdminUsers).toHaveBeenLastCalledWith("", "next-page");
        expect(screen.queryByRole("button", {name: "Load more"})).not.toBeInTheDocument();
    });

    it("confirms role changes and updates the row only from the server response", async () => {
        const user = userEvent.setup();
        const savedAccount = {...memberAccount, role: "maintainer" as const, updatedAt: "2026-10-28T12:00:00Z"};
        api.updateAdminUserRole.mockResolvedValue(savedAccount);
        renderView();

        const roleSelect = await screen.findByRole("combobox", {name: "Role for casey@example.com"});
        const memberRow = (await screen.findByText("Casey Member")).closest("tr");
        expect(memberRow).not.toBeNull();
        await user.selectOptions(roleSelect, "maintainer");
        expect(roleSelect).toHaveValue("maintainer");
        expect(api.updateAdminUserRole).not.toHaveBeenCalled();
        await user.click(within(memberRow as HTMLElement).getByRole("button", {name: "Change role"}));

        const dialog = screen.getByRole("alertdialog", {name: "Change account role?"});
        expect(dialog).toHaveTextContent("Casey Member will change from User to Maintainer");
        await user.click(within(dialog).getByRole("button", {name: "Change role"}));

        await waitFor(() => expect(api.updateAdminUserRole).toHaveBeenCalledWith(
            memberAccount.id,
            "maintainer",
            memberAccount.updatedAt,
        ));
        await waitFor(() => expect(screen.queryByRole("alertdialog")).not.toBeInTheDocument());
        expect(roleSelect).toHaveValue("maintainer");
    });

    it("keeps a failed role change in the confirmation so it can be retried", async () => {
        const user = userEvent.setup();
        api.updateAdminUserRole
            .mockRejectedValueOnce(new Error("Network unavailable"))
            .mockResolvedValueOnce({...memberAccount, role: "admin"});
        renderView();

        const memberRow = (await screen.findByText("Casey Member")).closest("tr");
        expect(memberRow).not.toBeNull();
        await user.selectOptions(within(memberRow as HTMLElement).getByRole("combobox", {name: "Role for casey@example.com"}), "admin");
        await user.click(within(memberRow as HTMLElement).getByRole("button", {name: "Change role"}));
        const dialog = screen.getByRole("alertdialog", {name: "Change account role?"});
        await user.click(within(dialog).getByRole("button", {name: "Change role"}));
        expect(await within(dialog).findByRole("alert")).toHaveTextContent("Network unavailable");
        expect(dialog).toBeInTheDocument();

        await user.click(within(dialog).getByRole("button", {name: "Change role"}));
        await waitFor(() => expect(screen.queryByRole("alertdialog")).not.toBeInTheDocument());
    });

    it("retries initial failures and refreshes account state on a forbidden response", async () => {
        const onAuthorizationFailure = vi.fn().mockResolvedValue(undefined);
        api.fetchAdminUsers
            .mockRejectedValueOnce(new Error("Temporary failure"))
            .mockRejectedValueOnce(new ApiError("Forbidden", 403))
            .mockResolvedValueOnce(page([]));
        renderView(onAuthorizationFailure);

        expect(await screen.findByRole("alert")).toHaveTextContent("Temporary failure");
        await userEvent.setup().click(screen.getByRole("button", {name: "Retry"}));
        await waitFor(() => expect(screen.getByRole("alert")).toHaveTextContent("Your access is being refreshed"));
        expect(onAuthorizationFailure).toHaveBeenCalledOnce();
        await userEvent.setup().click(screen.getByRole("button", {name: "Retry"}));
        expect(await screen.findByText("No accounts to show")).toBeInTheDocument();
    });
});
