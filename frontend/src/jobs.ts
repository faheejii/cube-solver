import {fetchSolveJob} from "./api";
import type {SolutionProcess, SolveJob, SolveResponse} from "./types";

export class SolveJobCancelledError extends Error {
    constructor() {
        super("Solve cancelled");
        this.name = "SolveJobCancelledError";
    }
}

export async function waitForSolveJob(
    initialJob: SolveJob,
    onProgress?: (job: SolveJob) => void,
    pollIntervalMs = 500,
): Promise<SolveResponse> {
    let job = initialJob;
    while (true) {
        onProgress?.(job);
        if (job.status === "completed") {
            if (!job.result) throw new Error("Optimized solve completed without a result");
            return job.result;
        }
        if (job.status === "failed") throw new Error(job.error ?? "Solve failed");
        if (job.status === "cancelled") throw new SolveJobCancelledError();
        if (job.status === "timed_out") throw new Error(job.error ?? "Solve timed out");
        await new Promise<void>((resolve) => window.setTimeout(resolve, pollIntervalMs));
        job = await fetchSolveJob(job.id);
    }
}

export function isTerminalProcess(process: Pick<SolutionProcess, "status">): boolean {
    return ["completed", "failed", "cancelled", "timed_out"].includes(process.status);
}

export function trimFinishedProcesses(processes: SolutionProcess[]): SolutionProcess[] {
    const active = processes.filter((process) => !isTerminalProcess(process));
    const finished = processes
        .filter(isTerminalProcess)
        .sort((left, right) => right.updatedAt - left.updatedAt)
        .slice(0, 20);
    return [...active, ...finished];
}
