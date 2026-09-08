import {fireEvent, render, screen} from "@testing-library/react";
import {describe, expect, it, vi} from "vitest";
import CrossFaceSelect from "../CrossFaceSelect";

const options = [
    {value: "U", label: "U"},
    {value: "D", label: "D"},
    {value: "F", label: "F"},
    {value: "B", label: "B"},
    {value: "L", label: "L"},
    {value: "R", label: "R"},
    {value: "CN", label: "Color Neutral"},
] as const;

describe("CrossFaceSelect", () => {
    it("renders the playback colors for face options and none for Color Neutral", () => {
        render(<CrossFaceSelect value="U" options={options} onChange={vi.fn()}/>);
        fireEvent.click(screen.getByRole("button", {name: /U/}));

        expect(screen.getAllByRole("option")).toHaveLength(7);
        expect(screen.getByRole("option", {name: "U"}).querySelector("[data-face=U]")).toHaveStyle({backgroundColor: "#f5f5f5"});
        expect(screen.getByRole("option", {name: "D"}).querySelector("[data-face=D]")).toHaveStyle({backgroundColor: "#f5d547"});
        expect(screen.getByRole("option", {name: "F"}).querySelector("[data-face=F]")).toHaveStyle({backgroundColor: "#35b86b"});
        expect(screen.getByRole("option", {name: "B"}).querySelector("[data-face=B]")).toHaveStyle({backgroundColor: "#3d72d8"});
        expect(screen.getByRole("option", {name: "L"}).querySelector("[data-face=L]")).toHaveStyle({backgroundColor: "#f08b35"});
        expect(screen.getByRole("option", {name: "R"}).querySelector("[data-face=R]")).toHaveStyle({backgroundColor: "#d94b4b"});
        expect(screen.getByRole("option", {name: "Color Neutral"}).querySelector(".cross-face-swatch")).toBeNull();
    });

    it("selects with the mouse and supports keyboard navigation and escape", () => {
        const onChange = vi.fn();
        render(<CrossFaceSelect value="U" options={options} onChange={onChange}/>);
        const trigger = screen.getByRole("button", {name: /U/});

        fireEvent.keyDown(trigger, {key: "ArrowDown"});
        expect(screen.getByRole("listbox")).toBeInTheDocument();
        fireEvent.keyDown(screen.getByRole("option", {name: "U"}), {key: "ArrowDown"});
        fireEvent.keyDown(screen.getByRole("option", {name: "D"}), {key: "Enter"});
        expect(onChange).toHaveBeenCalledWith("D");
        expect(screen.queryByRole("listbox")).not.toBeInTheDocument();

        fireEvent.click(trigger);
        fireEvent.keyDown(screen.getByRole("option", {name: "D"}), {key: "Escape"});
        expect(screen.queryByRole("listbox")).not.toBeInTheDocument();
    });

    it("does not open when disabled", () => {
        render(<CrossFaceSelect value="U" options={options} onChange={vi.fn()} disabled/>);
        const trigger = screen.getByRole("button", {name: /U/});

        expect(trigger).toBeDisabled();
        fireEvent.click(trigger);
        expect(screen.queryByRole("listbox")).not.toBeInTheDocument();
    });
});
