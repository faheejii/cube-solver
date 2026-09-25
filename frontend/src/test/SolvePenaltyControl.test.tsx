import {fireEvent, render, screen} from "@testing-library/react";
import {describe, expect, it, vi} from "vitest";
import SolvePenaltyControl from "../SolvePenaltyControl";

describe("SolvePenaltyControl", () => {
    it("marks the saved penalty and requests immediate changes", () => {
        const onChange = vi.fn();
        render(<SolvePenaltyControl value="+2" saving={false} error={null} onChange={onChange}/>);

        const group = screen.getByRole("group", {name: "Penalty for most recent solve"});
        expect(screen.getByRole("button", {name: "+2"})).toHaveAttribute("aria-pressed", "true");
        expect(screen.getByRole("button", {name: "No penalty"})).toHaveAttribute("aria-pressed", "false");
        fireEvent.click(screen.getByRole("button", {name: "DNF"}));
        expect(onChange).toHaveBeenCalledWith("dnf");
        expect(group).toHaveAttribute("aria-busy", "false");
    });

    it("disables changes while saving and exposes update failures", () => {
        render(<SolvePenaltyControl value="none" saving error="Could not update solve penalty" onChange={vi.fn()}/>);

        expect(screen.getByRole("status")).toHaveTextContent("Saving penalty");
        expect(screen.getByRole("alert")).toHaveTextContent("Could not update solve penalty");
        expect(screen.getByRole("button", {name: "+2"})).toBeDisabled();
    });
});
