import {describe, expect, it} from "vitest";
import {isTerminalProcess, SolveJobCancelledError, trimFinishedProcesses, waitForSolveJob} from "../jobs";
import type {SolutionProcess, SolveJob} from "../types";

function job(status: SolveJob["status"], error?: string): SolveJob {
    return {
        id: "job-1",
        status,
        statesExplored: 0,
        statesPruned: 0,
        duplicateStates: 0,
        bestMoves: -1,
        completedCandidates: 0,
        candidatesEvaluated: 0,
        bestTotalMoves: -1,
        phase: "queued",
        currentCrossFace: "",
        completedCrosses: 0,
        totalCrosses: 0,
        optimizationCandidate: 0,
        totalOptimizationCandidates: 0,
        optimizationBudgetExpired: false,
        result: null,
        error: error ?? null,
    };
}

describe("solve job state", () => {
    it("reports cancellation distinctly", async () => {
        await expect(waitForSolveJob(job("cancelled"))).rejects.toBeInstanceOf(SolveJobCancelledError);
    });

    it("reports timeout detail", async () => {
        await expect(waitForSolveJob(job("timed_out", "Deadline reached")))
            .rejects.toThrow("Deadline reached");
    });

    it("recognizes every terminal status", () => {
        for (const status of ["completed", "failed", "cancelled", "timed_out"] as const) {
            expect(isTerminalProcess({status})).toBe(true);
        }
        expect(isTerminalProcess({status: "running"})).toBe(false);
    });

    it("keeps active work and only the twenty newest finished jobs", () => {
        const base = {
            jobId: "job",
            source: "timer",
            request: {scramble: "R", crossFace: "D", f2lMode: "greedy"},
            statesExplored: 0,
            statesPruned: 0,
            duplicateStates: 0,
            bestMoves: -1,
            completedCandidates: 0,
            candidatesEvaluated: 0,
            bestTotalMoves: -1,
            phase: "queued",
            currentCrossFace: "",
            completedCrosses: 0,
            totalCrosses: 0,
            optimizationCandidate: 0,
            totalOptimizationCandidates: 0,
            optimizationBudgetExpired: false,
            result: null,
            error: null,
            cancelling: false,
        } satisfies Omit<SolutionProcess, "id" | "status" | "createdAt" | "updatedAt">;
        const active: SolutionProcess = {...base, id: "active", status: "running", createdAt: 0, updatedAt: 0};
        const finished = Array.from({length: 25}, (_, index): SolutionProcess => ({
            ...base,
            id: `finished-${index}`,
            status: "completed",
            createdAt: index,
            updatedAt: index,
        }));

        const trimmed = trimFinishedProcesses([active, ...finished]);

        expect(trimmed).toHaveLength(21);
        expect(trimmed[0].id).toBe("active");
        expect(trimmed[1].id).toBe("finished-24");
    });
});
