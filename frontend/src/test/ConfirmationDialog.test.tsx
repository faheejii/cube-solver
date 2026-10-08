import {fireEvent, render, screen} from "@testing-library/react";
import {describe, expect, it, vi} from "vitest";
import ConfirmationDialog from "../ConfirmationDialog";

describe("ConfirmationDialog", () => {
    it("starts on the safe action and cancels on Escape", () => {
        const onCancel = vi.fn();
        render(
            <ConfirmationDialog
                title="Delete solve?"
                description="This cannot be undone."
                confirmLabel="Delete"
                tone="danger"
                onCancel={onCancel}
                onConfirm={vi.fn()}
            />,
        );

        expect(screen.getByRole("alertdialog", {name: "Delete solve?"})).toBeInTheDocument();
        expect(screen.getByRole("button", {name: "Cancel"})).toHaveFocus();
        fireEvent.keyDown(window, {key: "Escape"});
        expect(onCancel).toHaveBeenCalledOnce();
    });

    it("traps tab focus, shows request errors, and prevents dismissal while pending", () => {
        const onCancel = vi.fn();
        const onConfirm = vi.fn();
        render(
            <ConfirmationDialog
                title="Delete solve?"
                description="Saved solutions will also be removed."
                confirmLabel="Delete"
                pendingLabel="Deleting…"
                pending
                error="Network unavailable"
                onCancel={onCancel}
                onConfirm={onConfirm}
            />,
        );

        expect(screen.getByRole("alert")).toHaveTextContent("Network unavailable");
        expect(screen.getByRole("button", {name: "Deleting…"})).toBeDisabled();
        fireEvent.keyDown(window, {key: "Escape"});
        expect(onCancel).not.toHaveBeenCalled();
        expect(onConfirm).not.toHaveBeenCalled();
    });
});
