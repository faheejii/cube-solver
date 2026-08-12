import {useCallback, useState} from "react";
import {cancelSolveJob, startSolveJob} from "../api";
import {isTerminalProcess, SolveJobCancelledError, trimFinishedProcesses, waitForSolveJob} from "../jobs";
import type {SolutionProcess, SolutionProcessSource, SolveJob, SolveJobRequest, SolveResponse} from "../types";

export function useSolveProcesses() {
    const [processes, setProcesses] = useState<SolutionProcess[]>([]);

    const updateProcessFromJob = useCallback((processId: string, job: SolveJob) => {
        setProcesses((current) => trimFinishedProcesses(current.map((process) =>
            process.id === processId
                ? {
                    ...process,
                    jobId: job.id,
                    status: job.status,
                    statesExplored: job.statesExplored,
                    statesPruned: job.statesPruned,
                    duplicateStates: job.duplicateStates,
                    bestMoves: job.bestMoves,
                    completedCandidates: job.completedCandidates,
                    candidatesEvaluated: job.candidatesEvaluated,
                    bestTotalMoves: job.bestTotalMoves,
                    phase: job.phase,
                    currentCrossFace: job.currentCrossFace,
                    completedCrosses: job.completedCrosses,
                    totalCrosses: job.totalCrosses,
                    optimizationCandidate: job.optimizationCandidate,
                    totalOptimizationCandidates: job.totalOptimizationCandidates,
                    optimizationBudgetExpired: job.optimizationBudgetExpired,
                    result: job.result,
                    error: job.error,
                    cancelling: isTerminalProcess({...process, status: job.status})
                        ? false
                        : process.cancelling,
                    updatedAt: Date.now(),
                }
                : process
        )));
    }, []);

    const runTrackedSolve = useCallback((
        request: SolveJobRequest,
        source: SolutionProcessSource,
        onProgress?: (job: SolveJob) => void,
        onJobCreated?: (job: SolveJob) => void,
    ): Promise<SolveResponse> => {
        const processId = crypto.randomUUID();
        const now = Date.now();
        const initial: SolutionProcess = {
            id: processId,
            jobId: null,
            source,
            request,
            status: "queued",
            statesExplored: 0,
            statesPruned: 0,
            duplicateStates: 0,
            bestMoves: -1,
            completedCandidates: 0,
            candidatesEvaluated: 0,
            bestTotalMoves: -1,
            phase: "QUEUED",
            currentCrossFace: "",
            completedCrosses: 0,
            totalCrosses: 0,
            optimizationCandidate: 0,
            totalOptimizationCandidates: 0,
            optimizationBudgetExpired: false,
            createdAt: now,
            updatedAt: now,
            result: null,
            error: null,
            cancelling: false,
        };
        setProcesses((current) => [initial, ...current]);

        return startSolveJob(request)
            .then((job) => {
                onJobCreated?.(job);
                updateProcessFromJob(processId, job);
                return waitForSolveJob(job, (progress) => {
                    updateProcessFromJob(processId, progress);
                    onProgress?.(progress);
                });
            })
            .catch((solveError) => {
                const message = solveError instanceof Error ? solveError.message : "Solve request failed";
                setProcesses((current) => trimFinishedProcesses(current.map((process) =>
                    process.id === processId && !isTerminalProcess(process)
                        ? {
                            ...process,
                            status: solveError instanceof SolveJobCancelledError ? "cancelled" : "failed",
                            error: message,
                            cancelling: false,
                            updatedAt: Date.now(),
                        }
                        : process
                )));
                throw solveError;
            });
    }, [updateProcessFromJob]);

    const terminateProcess = useCallback(async (process: SolutionProcess) => {
        if (!process.jobId || isTerminalProcess(process)) {
            return;
        }
        setProcesses((current) => current.map((entry) =>
            entry.id === process.id ? {...entry, cancelling: true} : entry
        ));
        try {
            updateProcessFromJob(process.id, await cancelSolveJob(process.jobId));
        } catch (cancelError) {
            const message = cancelError instanceof Error ? cancelError.message : "Cancellation failed";
            setProcesses((current) => current.map((entry) =>
                entry.id === process.id ? {...entry, cancelling: false, error: message} : entry
            ));
        }
    }, [updateProcessFromJob]);

    const retryProcess = useCallback((process: SolutionProcess) => {
        void runTrackedSolve(process.request, process.source);
    }, [runTrackedSolve]);

    const dismissProcess = useCallback((processId: string) => {
        setProcesses((current) => current.filter((process) => process.id !== processId));
    }, []);

    const clearFinishedProcesses = useCallback(() => {
        setProcesses((current) => current.filter((process) => !isTerminalProcess(process)));
    }, []);

    return {
        processes,
        runTrackedSolve,
        terminateProcess,
        retryProcess,
        dismissProcess,
        clearFinishedProcesses,
    };
}
