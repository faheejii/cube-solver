import {fireEvent, render, screen, waitFor, within} from "@testing-library/react";
import {beforeEach, describe, expect, it, vi} from "vitest";
import {CubePlaybackModeContext, CubePreviewModeContext} from "../../CubePreviewModeContext";
import type {AlgorithmCatalogResponse} from "../../types";

vi.mock("../../api", () => ({
    actOnAlgorithmRevision: vi.fn(async (algorithmId: string, revisionId: string, _action: string, expectedVersion: number) => ({
        id: revisionId, algorithmId, revisionNumber: 1, definitionJson: "{}", authorUserId: 1,
        status: "test-only", createdAt: "2026-01-01T00:00:00Z", version: expectedVersion + 1,
    })),
    createAlgorithm: vi.fn(async () => ({
        id: "revision-new", algorithmId: "algorithm-new", revisionNumber: 1,
        definitionJson: JSON.stringify({phase: "oll", name: "new-case", algorithm: "R U R'", notes: ""}),
        authorUserId: 1, status: "experimental", createdAt: "2026-01-01T00:00:00Z", version: 0,
    })),
    fetchAlgorithmDetail: vi.fn(async () => ({
        id: "algorithm-1", phase: "oll", name: "case-1", ownerUserId: null,
        activeCanonicalRevisionId: "revision-1",
        revisions: [{id: "revision-1", algorithmId: "algorithm-1", revisionNumber: 1,
            definitionJson: JSON.stringify({phase: "oll", name: "case-1", algorithm: "R U R'", notes: "Seed case"}),
            authorUserId: null, status: "canonical", createdAt: "2026-01-01T00:00:00Z", version: 1}],
        history: [{action: "seed_import", actorUserId: null, revisionId: "revision-1", previousStatus: null,
            newStatus: "canonical", reason: null, occurredAt: "2026-01-01T00:00:00Z"}],
    })),
    fetchAlgorithms: vi.fn(async () => ({
        version: "1",
        catalogVersion: 7,
        nextCursor: null,
        items: [{
            algorithmId: "algorithm-setup", revisionId: "revision-setup", revisionNumber: 1, revisionVersion: 1, ownerUserId: null, trialEligible: false, permittedActions: [],
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
            algorithmId: "algorithm-oll", revisionId: "revision-oll", revisionNumber: 1, revisionVersion: 1, ownerUserId: null, trialEligible: false, permittedActions: [],
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
            algorithmId: "algorithm-pll", revisionId: "revision-pll", revisionNumber: 1, revisionVersion: 1, ownerUserId: null, trialEligible: false, permittedActions: [],
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
    updateAdminSolverProfile: vi.fn(async (profile: {disabledAlgorithmIds?: string[]; enabledTrialRevisionIds?: string[]; disabledAlgorithms?: string[]}) => ({
        disabledAlgorithms: profile.disabledAlgorithms ?? [],
        disabledAlgorithmIds: profile.disabledAlgorithmIds ?? [],
        enabledTrialRevisionIds: profile.enabledTrialRevisionIds ?? [],
        inactiveTrialRevisionIds: [],
    })),
    validateAlgorithmDefinition: vi.fn(async (definition: {phase: string; name: string; algorithm: string; sourceSetup?: string; slot?: string; nonPreservedSlot?: string}) => ({
        valid: true, phase: definition.phase, name: definition.name, algorithm: definition.algorithm,
        sourceSetup: definition.sourceSetup ?? null, slot: definition.slot ?? null,
        nonPreservedSlot: definition.nonPreservedSlot ?? null, validatorNote: "Definition is valid.",
    })),
}));

vi.mock("../../DeferredCubePreview", () => ({
    default: ({setupAlgorithm = "", algorithm = "", displayMode = "3d", isPlaying = true}: {setupAlgorithm?: string; algorithm?: string; displayMode?: string; isPlaying?: boolean}) => (
        <div data-preview-setup={setupAlgorithm} data-preview-alg={algorithm} data-preview-mode={displayMode} data-playback-state={isPlaying ? "playing" : "paused"}/>
    ),
}));

import AlgorithmsView from "../../AlgorithmsView";
import {
    actOnAlgorithmRevision,
    createAlgorithm,
    fetchAdminSolverProfile,
    fetchAlgorithmDetail,
    fetchAlgorithms,
    updateAdminSolverProfile,
    validateAlgorithmDefinition,
} from "../../api";

function renderAlgorithmsView() {
    return render(
        <CubePreviewModeContext.Provider value="3d">
            <CubePlaybackModeContext.Provider value="2d">
                <AlgorithmsView currentUser={{id: "admin-id", email: "admin@example.test", displayName: "Admin", role: "admin"}}
                    onAuthorizationFailure={vi.fn(async () => undefined)}/>
            </CubePlaybackModeContext.Provider>
        </CubePreviewModeContext.Provider>,
    );
}

describe("AlgorithmsView", () => {
    beforeEach(() => {
        vi.mocked(fetchAlgorithms).mockReset().mockImplementation(async (): Promise<AlgorithmCatalogResponse> => ({
            version: "1", catalogVersion: 7, nextCursor: null, items: [
                {algorithmId: "algorithm-setup", revisionId: "revision-setup", revisionNumber: 1, revisionVersion: 1, ownerUserId: null, permittedActions: [], phase: "setup", name: "case-26.1-FR", slot: null, nonPreservedSlot: "FR", preservedSlots: ["FL", "BL"], signature: {kind: "f2l-setup", cornerPosition: "URF", cornerOrientation: 0, edgePosition: "BR", edgeOrientation: 1}, algorithm: "R U' R'", sourceSetup: "R U R' F R' F' R U", status: "canonical", notes: "", previewSetup: "R U R' F R' F' R U"},
                {algorithmId: "algorithm-oll", revisionId: "revision-oll", revisionNumber: 1, revisionVersion: 1, ownerUserId: null, permittedActions: [], phase: "oll", name: "case-1", slot: null, nonPreservedSlot: null, preservedSlots: [], signature: {kind: "oll", u0: true, u1: false}, algorithm: "R U2 R2 F R F' U2 R' F R F'", sourceSetup: null, previewSetup: "F R' F' R U2 F R' F' R2 U2 R'", status: "canonical", notes: ""},
                {algorithmId: "algorithm-pll", revisionId: "revision-pll", revisionNumber: 1, revisionVersion: 1, ownerUserId: null, permittedActions: [], phase: "pll", name: "case-1-pll", slot: null, nonPreservedSlot: null, preservedSlots: [], signature: {kind: "pll", urfPiece: "URF", uflPiece: "UFL", ulbPiece: "ULB", ubrPiece: "UBR"}, algorithm: "M2 U M2 U2 M2 U M2", sourceSetup: null, previewSetup: "M2 U' M2 U2 M2 U' M2", status: "canonical", notes: ""},
            ],
        }));
        vi.mocked(fetchAlgorithmDetail).mockReset().mockResolvedValue({
            id: "algorithm-1", phase: "oll", name: "case-1", ownerUserId: null, activeCanonicalRevisionId: "revision-1",
            revisions: [{id: "revision-1", algorithmId: "algorithm-1", revisionNumber: 1, definitionJson: JSON.stringify({phase: "oll", name: "case-1", algorithm: "R U R'", notes: "Seed case"}), authorUserId: null, status: "canonical", createdAt: "2026-01-01T00:00:00Z", version: 1}],
            history: [{action: "seed_import", actorUserId: null, revisionId: "revision-1", previousStatus: null, newStatus: "canonical", reason: null, occurredAt: "2026-01-01T00:00:00Z"}],
        });
        vi.mocked(fetchAdminSolverProfile).mockReset().mockResolvedValue({disabledAlgorithms: [], disabledAlgorithmIds: [], enabledTrialRevisionIds: [], inactiveTrialRevisionIds: []});
        vi.mocked(updateAdminSolverProfile).mockReset().mockImplementation(async (profile) => ({
            disabledAlgorithms: profile.disabledAlgorithms ?? [],
            disabledAlgorithmIds: profile.disabledAlgorithmIds ?? [],
            enabledTrialRevisionIds: profile.enabledTrialRevisionIds ?? [],
            inactiveTrialRevisionIds: [],
        }));
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

        await waitFor(() => expect(updateAdminSolverProfile).toHaveBeenCalledWith({disabledAlgorithmIds: ["algorithm-setup"], enabledTrialRevisionIds: []}));
        expect(checkbox).toBeChecked();
        expect(await screen.findByText("Solver profile saved to your account.")).toBeInTheDocument();
    });

    it("locks profile toggles while a replacement save is pending", async () => {
        let finishSave!: (value: {disabledAlgorithms: string[]; disabledAlgorithmIds: string[]; enabledTrialRevisionIds: string[]; inactiveTrialRevisionIds: string[]}) => void;
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
        finishSave({disabledAlgorithms: [], disabledAlgorithmIds: ["algorithm-setup"], enabledTrialRevisionIds: [], inactiveTrialRevisionIds: []});
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
            .mockResolvedValueOnce({disabledAlgorithms: [], disabledAlgorithmIds: ["algorithm-setup"], enabledTrialRevisionIds: [], inactiveTrialRevisionIds: []});
        renderAlgorithmsView();
        const checkbox = await screen.findByRole("checkbox", {name: "Disable case-26.1-FR for my solver"});

        fireEvent.click(checkbox);
        expect(await screen.findByRole("alert")).toHaveTextContent("Changes were not saved.");
        expect(checkbox).toBeChecked();
        fireEvent.click(screen.getByRole("button", {name: "Retry"}));

        await waitFor(() => expect(updateAdminSolverProfile).toHaveBeenCalledTimes(2));
        expect(updateAdminSolverProfile).toHaveBeenLastCalledWith({disabledAlgorithmIds: ["algorithm-setup"], enabledTrialRevisionIds: []});
        expect(await screen.findByText("Solver profile saved to your account.")).toBeInTheDocument();
    });

    it("uses personal opt-in for experimental algorithms without disable or deprecate actions", async () => {
        vi.mocked(fetchAlgorithms).mockResolvedValueOnce({
            version: "1",
            catalogVersion: 7,
            nextCursor: null,
            items: [{
                algorithmId: "algorithm-exp", revisionId: "revision-exp", revisionNumber: 1, revisionVersion: 1, ownerUserId: 1, trialEligible: true, permittedActions: ["submit"],
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
        const optIn = screen.getByRole("checkbox", {name: "Enable revision 1 of experimental-oll for my solver"});
        expect(optIn).not.toBeChecked();
        expect(screen.queryByRole("checkbox", {name: "Disable experimental-oll for my solver"})).not.toBeInTheDocument();
        expect(screen.queryByRole("button", {name: "Deprecate"})).not.toBeInTheDocument();
        fireEvent.click(optIn);
        await waitFor(() => expect(updateAdminSolverProfile).toHaveBeenCalledWith({
            disabledAlgorithmIds: [], enabledTrialRevisionIds: ["revision-exp"],
        }));
    });

    it("filters the catalog to algorithms disabled in the current solver profile", async () => {
        vi.mocked(fetchAdminSolverProfile).mockResolvedValue({disabledAlgorithms: ["setup:case-26.1-FR"]});
        renderAlgorithmsView();

        await screen.findByText("case-1-pll");
        fireEvent.change(screen.getByRole("combobox", {name: "Algorithm status"}), {
            target: {value: "disabled"},
        });

        expect(await screen.findByText("case-26.1-FR")).toBeInTheDocument();
        await waitFor(() => expect(screen.queryByText("case-1-pll")).not.toBeInTheDocument());
        expect(screen.getByText("1 shown")).toBeInTheDocument();
        expect(fetchAlgorithms).toHaveBeenLastCalledWith({phase: "", slot: "", search: "", status: "", limit: 20, includeNonCanonical: false});
    });

    it("loads and filters the canonical catalog", async () => {
        const {container} = renderAlgorithmsView();

        expect(container.querySelector(".algorithm-catalog-list")).toBeInTheDocument();
        expect(await screen.findByText("case-26.1-FR")).toBeInTheDocument();
        expect(screen.getByText("R U' R'")).toBeInTheDocument();
        expect(screen.getByText("case-1-pll")).toBeInTheDocument();
        const copyButtons = screen.getAllByRole("button", {name: "Copy algorithm"});
        expect(copyButtons).toHaveLength(3);
        expect(copyButtons[0]).toHaveAttribute("title", "Copy algorithm");
        expect(copyButtons[0].textContent).toBe("");
        expect(copyButtons[0].querySelector("svg")).toBeInTheDocument();
        const previewButtons = screen.getAllByRole("button", {name: "Test on cube"});
        expect(previewButtons).toHaveLength(3);
        expect(previewButtons[0]).toHaveAttribute("title", "Test on cube");
        expect(previewButtons[0].textContent).toBe("");
        expect(previewButtons[0].querySelector("svg")).toBeInTheDocument();
        fireEvent.click(screen.getAllByRole("button", {name: /Details/})[0]);
        expect(screen.getByText(/OLL facelets: U0/)).toBeInTheDocument();
        expect(screen.getByText(/Source setup: R U R' F R' F' R U/)).toBeInTheDocument();
        fireEvent.click(screen.getByRole("button", {name: "Close"}));
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

    it("loads the next cursor page and appends only new revisions", async () => {
        vi.mocked(fetchAlgorithms)
            .mockResolvedValueOnce({version: "1", catalogVersion: 7, nextCursor: "cursor-1", items: [{algorithmId: "a", revisionId: "r1", revisionNumber: 1, revisionVersion: 2, ownerUserId: 1, permittedActions: [], phase: "oll", name: "first", slot: null, nonPreservedSlot: null, preservedSlots: [], signature: {kind: "oll", u0: true}, algorithm: "R U R'", sourceSetup: null, previewSetup: "R U' R'", status: "canonical", notes: ""}]})
            .mockResolvedValueOnce({version: "1", catalogVersion: 7, nextCursor: null, items: [
                {algorithmId: "a", revisionId: "r1", revisionNumber: 1, revisionVersion: 2, ownerUserId: 1, permittedActions: [], phase: "oll", name: "first", slot: null, nonPreservedSlot: null, preservedSlots: [], signature: {kind: "oll", u0: true}, algorithm: "R U R'", sourceSetup: null, previewSetup: "R U' R'", status: "canonical", notes: ""},
                {algorithmId: "b", revisionId: "r2", revisionNumber: 1, revisionVersion: 1, ownerUserId: 1, permittedActions: [], phase: "pll", name: "second", slot: null, nonPreservedSlot: null, preservedSlots: [], signature: {kind: "pll", urfPiece: "URF"}, algorithm: "U", sourceSetup: null, previewSetup: "U'", status: "test-only", notes: ""},
            ]});
        renderAlgorithmsView();
        expect(await screen.findByText("first")).toBeInTheDocument();
        fireEvent.click(screen.getByRole("button", {name: "Load more"}));
        expect(await screen.findByText("second")).toBeInTheDocument();
        expect(screen.getAllByRole("button", {name: /Details/})).toHaveLength(2);
        expect(fetchAlgorithms).toHaveBeenLastCalledWith({phase: "", slot: "", search: "", status: "", cursor: "cursor-1", limit: 20, includeNonCanonical: false});
    });

    it("shows only permitted workflow actions and loads revision details", async () => {
        vi.mocked(fetchAlgorithms).mockResolvedValueOnce({version: "1", catalogVersion: 8, nextCursor: null, items: [{
            algorithmId: "algorithm-1", revisionId: "revision-1", revisionNumber: 2, revisionVersion: 3, ownerUserId: 1,
            permittedActions: ["submit"], phase: "oll", name: "draft", slot: null, nonPreservedSlot: null,
            preservedSlots: [], signature: {kind: "oll", u0: true}, algorithm: "R U R'", sourceSetup: null,
            previewSetup: "R U' R'", status: "experimental", notes: "",
        }]});
        renderAlgorithmsView();
        expect(await screen.findByRole("button", {name: "Share for testing"})).toBeInTheDocument();
        expect(screen.queryByRole("button", {name: "Approve as canonical"})).not.toBeInTheDocument();
        fireEvent.click(screen.getByRole("button", {name: "Share for testing"}));
        await waitFor(() => expect(actOnAlgorithmRevision).toHaveBeenCalledWith("algorithm-1", "revision-1", "submit", 3, undefined));
        fireEvent.click(screen.getByRole("button", {name: /Details/}));
        expect(await screen.findByRole("dialog", {name: "Algorithm details"})).toHaveTextContent("Seed case");
        expect(screen.getByText("Workflow history")).toBeInTheDocument();
        expect(fetchAlgorithmDetail).toHaveBeenCalledWith("algorithm-1");
    });

    it("requires a reason before an admin can return a revision for changes", async () => {
        vi.mocked(fetchAlgorithms).mockResolvedValueOnce({version: "1", catalogVersion: 8, nextCursor: null, items: [{
            algorithmId: "algorithm-review", revisionId: "revision-review", revisionNumber: 2, revisionVersion: 4, ownerUserId: 1,
            permittedActions: ["request-changes"], phase: "oll", name: "review-case", slot: null, nonPreservedSlot: null,
            preservedSlots: [], signature: {kind: "oll", u0: true}, algorithm: "R U R'", sourceSetup: null,
            previewSetup: "R U' R'", status: "test-only", notes: "",
        }]});
        renderAlgorithmsView();
        fireEvent.click(await screen.findByRole("button", {name: "Request changes"}));
        const dialog = screen.getByRole("alertdialog", {name: "Request changes?"});
        const confirm = within(dialog).getByRole("button", {name: "Request changes"});
        expect(confirm).toBeDisabled();
        fireEvent.change(within(dialog).getByLabelText("Reason"), {target: {value: "Verify preservation of the back slot."}});
        expect(confirm).toBeEnabled();
        fireEvent.click(confirm);
        await waitFor(() => expect(actOnAlgorithmRevision).toHaveBeenCalledWith(
            "algorithm-review", "revision-review", "request-changes", 4, "Verify preservation of the back slot.",
        ));
    });

    it("confirms restoring a previously approved revision", async () => {
        vi.mocked(fetchAlgorithms).mockResolvedValueOnce({version: "1", catalogVersion: 8, nextCursor: null, items: [{
            algorithmId: "algorithm-restore", revisionId: "revision-restore", revisionNumber: 1, revisionVersion: 5, ownerUserId: 1,
            permittedActions: ["restore"], phase: "pll", name: "retired-case", slot: null, nonPreservedSlot: null,
            preservedSlots: [], signature: {kind: "pll", urfPiece: "URF"}, algorithm: "U", sourceSetup: null,
            previewSetup: "U'", status: "deprecated", notes: "",
        }]});
        renderAlgorithmsView();
        fireEvent.click(await screen.findByRole("button", {name: "Restore as canonical"}));
        expect(screen.getByRole("alertdialog", {name: "Restore algorithm?"})).toHaveTextContent("new solves");
        fireEvent.click(within(screen.getByRole("alertdialog", {name: "Restore algorithm?"})).getByRole("button", {name: "Restore as canonical"}));
        await waitFor(() => expect(actOnAlgorithmRevision).toHaveBeenCalledWith(
            "algorithm-restore", "revision-restore", "restore", 5, "",
        ));
    });

    it("validates a new experimental algorithm before creating it", async () => {
        renderAlgorithmsView();
        fireEvent.click(await screen.findByRole("button", {name: "New algorithm"}));
        const dialog = screen.getByRole("dialog", {name: "New algorithm"});
        fireEvent.change(within(dialog).getByLabelText("Phase"), {target: {value: "oll"}});
        fireEvent.change(within(dialog).getByLabelText("Name"), {target: {value: "new-case"}});
        fireEvent.change(within(dialog).getByLabelText("Algorithm"), {target: {value: "R U R'"}});
        fireEvent.click(within(dialog).getByRole("button", {name: "Validate"}));
        expect(await within(dialog).findByText("Definition is valid.")).toBeInTheDocument();
        expect(validateAlgorithmDefinition).toHaveBeenCalledWith({phase: "oll", name: "new-case", algorithm: "R U R'", notes: ""});
        fireEvent.click(within(dialog).getByRole("button", {name: "Create experimental"}));
        await waitFor(() => expect(createAlgorithm).toHaveBeenCalledWith({phase: "oll", name: "new-case", algorithm: "R U R'", notes: ""}));
    });
});
