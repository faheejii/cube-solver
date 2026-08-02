import {fireEvent, render, screen, waitFor} from "@testing-library/react";
import {describe, expect, it, vi} from "vitest";

vi.mock("cubing/twisty", () => ({}));

vi.mock("../api", () => ({
    fetchAlgorithms: vi.fn(async () => ({
        version: "1",
        items: [{
            phase: "setup",
            name: "case-26.1-FR",
            slot: "FR",
            preservedSlots: ["FL", "BL"],
            signature: {kind: "f2l", cornerPosition: "URF", cornerOrientation: 0, edgePosition: "BR", edgeOrientation: 1},
            algorithm: "R U' R'",
            sourceSetup: "R U R' F R' F' R U",
            status: "canonical",
            notes: "",
            previewSetup: "R U R' F R' F' R U",
        }, {
            phase: "oll",
            name: "case-1",
            slot: null,
            preservedSlots: [],
            signature: {kind: "oll", u0: true, u1: false},
            algorithm: "R U2 R2 F R F' U2 R' F R F'",
            sourceSetup: null,
            previewSetup: "F R' F' R U2 F R' F' R2 U2 R'",
            status: "canonical",
            notes: "",
        }, {
            phase: "pll",
            name: "case-1-pll",
            slot: null,
            preservedSlots: [],
            signature: {kind: "pll", urfPiece: "URF", uflPiece: "UFL", ulbPiece: "ULB", ubrPiece: "UBR"},
            algorithm: "M2 U M2 U2 M2 U M2",
            sourceSetup: null,
            previewSetup: "M2 U' M2 U2 M2 U' M2",
            status: "canonical",
            notes: "",
        }],
    })),
}));

import AlgorithmsView from "../AlgorithmsView";

describe("AlgorithmsView", () => {
    it("loads and filters the canonical catalog", async () => {
        const {container} = render(<AlgorithmsView/>);

        expect(container.querySelector(".algorithm-catalog-list")).toBeInTheDocument();
        expect(await screen.findByText("case-26.1-FR")).toBeInTheDocument();
        expect(screen.getByText("R U' R'")).toBeInTheDocument();
        expect(screen.getByText("case-1-pll")).toBeInTheDocument();
        expect(screen.getAllByRole("button", {name: "Copy algorithm"})).toHaveLength(3);
        fireEvent.click(screen.getAllByText("Details")[0]);
        expect(screen.getByText(/OLL facelets: U0/)).toBeInTheDocument();
        expect(screen.getByText(/Source setup: R U R' F R' F' R U/)).toBeInTheDocument();
        fireEvent.click(screen.getAllByRole("button", {name: "Test on cube"})[0]);
        const preview = await screen.findByRole("dialog", {name: "case-26.1-FR preview"});
        expect(preview).toBeInTheDocument();
        expect(preview).toHaveTextContent("Setup state: R U R' F R' F' R U");

        fireEvent.change(screen.getByRole("combobox", {name: "Algorithm phase"}), {
            target: {value: "insert"},
        });
        await waitFor(() => expect(screen.getByText("Canonical algorithm catalog")).toBeInTheDocument());
    });
});
