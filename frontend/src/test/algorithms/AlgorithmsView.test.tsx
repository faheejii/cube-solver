import {fireEvent, render, screen, waitFor} from "@testing-library/react";
import {beforeEach, describe, expect, it, vi} from "vitest";
import {CubePlaybackModeContext, CubePreviewModeContext} from "../../CubePreviewModeContext";

vi.mock("../../api", () => ({
    fetchAlgorithms: vi.fn(async () => ({
        version: "1",
        items: [{
            phase: "setup",
            name: "case-26.1-FR",
            slot: null,
            nonPreservedSlot: "FR",
            preservedSlots: ["FL", "BL"],
            signature: {kind: "f2l-setup", cornerPosition: "URF", cornerOrientation: 0, edgePosition: "BR", edgeOrientation: 1},
            algorithm: "R U' R'",
            sourceSetup: "R U R' F R' F' R U",
            status: "canonical",
            notes: "",
            previewSetup: "R U R' F R' F' R U",
        }, {
            phase: "oll",
            name: "case-1",
            slot: null,
            nonPreservedSlot: null,
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
            nonPreservedSlot: null,
            preservedSlots: [],
            signature: {kind: "pll", urfPiece: "URF", uflPiece: "UFL", ulbPiece: "ULB", ubrPiece: "UBR"},
            algorithm: "M2 U M2 U2 M2 U M2",
            sourceSetup: null,
            previewSetup: "M2 U' M2 U2 M2 U' M2",
            status: "canonical",
            notes: "",
        }],
    })),
    fetchAdminSolverProfile: vi.fn(async () => ({disabledAlgorithms: []})),
    updateAdminSolverProfile: vi.fn(async (disabledAlgorithms: string[]) => ({disabledAlgorithms})),
}));

vi.mock("../../DeferredCubePreview", () => ({
    default: ({setupAlgorithm = "", algorithm = "", displayMode = "3d", isPlaying = true}: {setupAlgorithm?: string; algorithm?: string; displayMode?: string; isPlaying?: boolean}) => (
        <div data-preview-setup={setupAlgorithm} data-preview-alg={algorithm} data-preview-mode={displayMode} data-playback-state={isPlaying ? "playing" : "paused"}/>
    ),
}));

import AlgorithmsView from "../../AlgorithmsView";
import {fetchAdminSolverProfile, fetchAlgorithms, updateAdminSolverProfile} from "../../api";

function renderAlgorithmsView() {
    return render(
        <CubePreviewModeContext.Provider value="3d">
            <CubePlaybackModeContext.Provider value="2d">
                <AlgorithmsView/>
            </CubePlaybackModeContext.Provider>
        </CubePreviewModeContext.Provider>,
    );
}

describe("AlgorithmsView", () => {
    beforeEach(() => {
        vi.mocked(fetchAdminSolverProfile).mockReset().mockResolvedValue({disabledAlgorithms: []});
        vi.mocked(updateAdminSolverProfile).mockReset().mockImplementation(async (disabledAlgorithms) => ({disabledAlgorithms}));
    });

    it("loads the account solver profile and exposes canonical disable controls", async () => {
        vi.mocked(fetchAdminSolverProfile).mockResolvedValue({disabledAlgorithms: ["oll:case-1"]});
        renderAlgorithmsView();

        expect(await screen.findByRole("checkbox", {name: "Disable case-1 for my solver"})).toBeChecked();
        expect(screen.getByText("Solver profile saved to your account.")).toBeInTheDocument();
        expect(fetchAdminSolverProfile).toHaveBeenCalledOnce();
    });

    it("saves a canonical toggle to the account profile", async () => {
        renderAlgorithmsView();
        const checkbox = await screen.findByRole("checkbox", {name: "Disable case-26.1-FR for my solver"});

        fireEvent.click(checkbox);

        await waitFor(() => expect(updateAdminSolverProfile).toHaveBeenCalledWith(["setup:case-26.1-FR"]));
        expect(checkbox).toBeChecked();
        expect(await screen.findByText("Solver profile saved to your account.")).toBeInTheDocument();
    });

    it("locks profile toggles while a replacement save is pending", async () => {
        let finishSave!: (value: {disabledAlgorithms: string[]}) => void;
        vi.mocked(updateAdminSolverProfile).mockImplementation(() => new Promise((resolve) => {
            finishSave = resolve;
        }));
        renderAlgorithmsView();
        const firstToggle = await screen.findByRole("checkbox", {name: "Disable case-26.1-FR for my solver"});
        const secondToggle = screen.getByRole("checkbox", {name: "Disable case-1 for my solver"});

        fireEvent.click(firstToggle);

        expect(firstToggle).toBeDisabled();
        expect(secondToggle).toBeDisabled();
        expect(updateAdminSolverProfile).toHaveBeenCalledTimes(1);
        finishSave({disabledAlgorithms: ["setup:case-26.1-FR"]});
        await waitFor(() => expect(firstToggle).toBeEnabled());
    });

    it("retries a failed profile load", async () => {
        vi.mocked(fetchAdminSolverProfile)
            .mockRejectedValueOnce(new Error("Temporary server error"))
            .mockResolvedValueOnce({disabledAlgorithms: []});
        renderAlgorithmsView();

        expect(await screen.findByRole("alert")).toHaveTextContent("Could not load your solver profile.");
        fireEvent.click(screen.getByRole("button", {name: "Retry"}));

        expect(await screen.findByRole("checkbox", {name: "Disable case-26.1-FR for my solver"})).toBeEnabled();
        expect(fetchAdminSolverProfile).toHaveBeenCalledTimes(2);
    });

    it("keeps a failed selection and retries saving it", async () => {
        vi.mocked(updateAdminSolverProfile)
            .mockRejectedValueOnce(new Error("Temporary server error"))
            .mockResolvedValueOnce({disabledAlgorithms: ["setup:case-26.1-FR"]});
        renderAlgorithmsView();
        const checkbox = await screen.findByRole("checkbox", {name: "Disable case-26.1-FR for my solver"});

        fireEvent.click(checkbox);
        expect(await screen.findByRole("alert")).toHaveTextContent("Changes were not saved.");
        expect(checkbox).toBeChecked();
        fireEvent.click(screen.getByRole("button", {name: "Retry"}));

        await waitFor(() => expect(updateAdminSolverProfile).toHaveBeenCalledTimes(2));
        expect(updateAdminSolverProfile).toHaveBeenLastCalledWith(["setup:case-26.1-FR"]);
        expect(await screen.findByText("Solver profile saved to your account.")).toBeInTheDocument();
    });

    it("keeps noncanonical algorithms view-only", async () => {
        vi.mocked(fetchAlgorithms).mockResolvedValueOnce({
            version: "1",
            items: [{
                phase: "oll",
                name: "experimental-oll",
                slot: null,
                nonPreservedSlot: null,
                preservedSlots: [],
                signature: {kind: "oll", u0: true},
                algorithm: "R U R'",
                sourceSetup: null,
                previewSetup: "R U' R'",
                status: "experimental",
                notes: "",
            }],
        });
        renderAlgorithmsView();

        expect(await screen.findByText("experimental-oll")).toBeInTheDocument();
        expect(screen.getByText("View only")).toBeInTheDocument();
        expect(screen.queryByRole("checkbox", {name: "Disable experimental-oll for my solver"})).not.toBeInTheDocument();
        expect(updateAdminSolverProfile).not.toHaveBeenCalled();
    });

    it("loads and filters the canonical catalog", async () => {
        const {container} = renderAlgorithmsView();

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
        const previewSurface = preview.querySelector("[data-preview-setup]");
        expect(previewSurface).toHaveAttribute("data-preview-setup", "R U R' F R' F' R U");
        expect(previewSurface).toHaveAttribute("data-preview-alg", "R U' R'");
        expect(previewSurface).toHaveAttribute("data-preview-mode", "2d");
        expect(previewSurface).toHaveAttribute("data-playback-state", "playing");

        fireEvent.click(screen.getByRole("button", {name: "Pause playback"}));
        expect(screen.getByRole("button", {name: "Play playback"})).toBeInTheDocument();
        expect(previewSurface).toHaveAttribute("data-playback-state", "paused");
        fireEvent.click(screen.getByRole("button", {name: "Play playback"}));
        expect(screen.getByRole("button", {name: "Pause playback"})).toBeInTheDocument();
        expect(previewSurface).toHaveAttribute("data-playback-state", "playing");

        fireEvent.change(screen.getByRole("combobox", {name: "Algorithm phase"}), {
            target: {value: "insert"},
        });
        await waitFor(() => expect(screen.getByRole("heading", {name: "Algorithms"})).toBeInTheDocument());
        expect(screen.queryByText("Canonical algorithm catalog")).not.toBeInTheDocument();
    });
});
