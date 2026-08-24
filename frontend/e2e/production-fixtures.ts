import type {Page, Route} from "@playwright/test";

export const normalUser = {
    id: "user-1",
    email: "solver@example.com",
    displayName: "Test Solver",
    role: "user",
} as const;

export const adminUser = {
    id: "admin-1",
    email: "admin@example.com",
    displayName: "Test Admin",
    role: "admin",
} as const;

export const historyEntry = {
    id: 1,
    clientAttemptId: "attempt-1",
    scramble: "R U R' U'",
    crossFaceRequested: "U",
    timerMs: 12_340,
    officialMs: 12_340,
    penalty: "none",
    dnf: false,
    fastCrossFaceRequested: "U",
    optimizedCrossFaceRequested: "U",
    createdAt: "2026-07-26T10:00:00Z",
};

const catalogEntries = [
    {
        phase: "setup",
        name: "F2L FR setup",
        slot: "FR",
        preservedSlots: ["FL", "BL", "BR"],
        signature: {kind: "f2l", cornerPosition: "UFR", cornerOrientation: 0, edgePosition: "UF", edgeOrientation: 0},
        algorithm: "R U R'",
        sourceSetup: "U R U'",
        status: "canonical",
        notes: "Canonical setup",
        previewSetup: "U R U'",
    },
    {
        phase: "insert",
        name: "F2L FR insert",
        slot: "FR",
        preservedSlots: ["FL", "BL", "BR"],
        signature: {kind: "f2l", cornerPosition: "UFR", cornerOrientation: 1, edgePosition: "UF", edgeOrientation: 1},
        algorithm: "R U' R'",
        sourceSetup: null,
        status: "canonical",
        notes: "Canonical insert",
        previewSetup: null,
    },
    {
        phase: "oll",
        name: "OLL Sune",
        slot: null,
        preservedSlots: [],
        signature: {kind: "oll", ufr: true, ur: true, ubr: true},
        algorithm: "R U R' U R U2 R'",
        sourceSetup: null,
        status: "canonical",
        notes: "Canonical OLL",
        previewSetup: "R U R' U R U2 R'",
    },
    {
        phase: "pll",
        name: "PLL Aa",
        slot: null,
        preservedSlots: [],
        signature: {kind: "pll", urfPiece: "UFR", uflPiece: "UFL", ulbPiece: "ULB", ubrPiece: "UBR"},
        algorithm: "x L2 D2 L' U' L D2 L' U L'",
        sourceSetup: null,
        status: "canonical",
        notes: "Canonical PLL",
        previewSetup: "x L2 D2 L' U' L D2 L' U L'",
    },
    {
        phase: "insert",
        name: "Experimental insert",
        slot: "FR",
        preservedSlots: [],
        signature: {kind: "f2l", cornerPosition: "UFR", cornerOrientation: 2, edgePosition: "UF", edgeOrientation: 0},
        algorithm: "U R U' R'",
        sourceSetup: null,
        status: "experimental",
        notes: "Experimental catalog entry",
        previewSetup: null,
    },
];

export type MockApiOptions = {
    authenticated?: boolean;
    user?: typeof normalUser | typeof adminUser;
    historyEntries?: typeof historyEntry[];
    algorithms?: typeof catalogEntries;
};

export type MockApiHandle = {
    requests: Array<{method: string; pathname: string; search: string; body: unknown}>;
};

export async function mockProductionApi(page: Page, options: MockApiOptions = {}): Promise<MockApiHandle> {
    let authenticated = options.authenticated ?? true;
    const user = options.user ?? normalUser;
    let entries = [...(options.historyEntries ?? [historyEntry])];
    let nextSolveId = Math.max(1, ...entries.map((entry) => entry.id)) + 1;
    const algorithms = options.algorithms ?? catalogEntries;
    const requests: MockApiHandle["requests"] = [];
    const jobs = new Map<string, unknown>();
    const savedBySolve = new Map<number, unknown>();

    for (const entry of entries) {
        savedBySolve.set(entry.id, savedSolution(entry.scramble, "greedy"));
    }

    await page.route("**/api/**", async (route) => {
        const request = route.request();
        const url = new URL(request.url());
        let body: unknown = null;
        try {
            body = request.postDataJSON();
        } catch {
            body = null;
        }
        requests.push({method: request.method(), pathname: url.pathname, search: url.search, body});

        if (url.pathname === "/api/auth/me") {
            await json(route, authenticated ? user : {error: "Authentication required"}, authenticated ? 200 : 401);
            return;
        }
        if (url.pathname === "/api/auth/login" || url.pathname === "/api/auth/register") {
            authenticated = true;
            await json(route, user);
            return;
        }
        if (url.pathname === "/api/auth/logout") {
            authenticated = false;
            await route.fulfill({status: 204, body: ""});
            return;
        }
        if (url.pathname === "/api/stats") {
            await json(route, {
                solveCount: entries.length,
                dnfCount: 0,
                bestMs: 12_340,
                averageMs: 12_340,
                ao5: {status: "insufficient", valueMs: null},
                ao12: {status: "insufficient", valueMs: null},
                recentSolves: entries.slice(0, 5),
            });
            return;
        }
        if (url.pathname === "/api/solves" && request.method() === "GET") {
            await json(route, {items: entries, nextCursor: null});
            return;
        }
        if (url.pathname === "/api/solves" && request.method() === "POST") {
            const requestBody = (body ?? {}) as Record<string, unknown>;
            const scramble = String(requestBody.scramble ?? historyEntry.scramble);
            const created = {
                ...historyEntry,
                id: nextSolveId++,
                clientAttemptId: String(requestBody.clientAttemptId ?? "attempt-created"),
                scramble,
                crossFaceRequested: String(requestBody.crossFaceRequested ?? "U"),
                timerMs: typeof requestBody.timerMs === "number" ? requestBody.timerMs : null,
                officialMs: typeof requestBody.officialMs === "number" ? requestBody.officialMs : null,
                penalty: String(requestBody.penalty ?? "none"),
                dnf: Boolean(requestBody.dnf),
                fastCrossFaceRequested: String(requestBody.crossFaceRequested ?? "U"),
                optimizedCrossFaceRequested: String(requestBody.crossFaceRequested ?? "U"),
            };
            entries = [created, ...entries];
            await json(route, created);
            return;
        }
        const solveMatch = url.pathname.match(/^\/api\/solves\/(\d+)$/);
        if (solveMatch && request.method() === "GET") {
            const id = Number(solveMatch[1]);
            const entry = entries.find((candidate) => candidate.id === id) ?? historyEntry;
            await json(route, {
                ...entry,
                solutions: [savedBySolve.get(id) ?? savedSolution(entry.scramble, "greedy")],
            });
            return;
        }
        if (solveMatch && request.method() === "DELETE") {
            const id = Number(solveMatch[1]);
            entries = entries.filter((entry) => entry.id !== id);
            savedBySolve.delete(id);
            await route.fulfill({status: 204, body: ""});
            return;
        }
        const solutionMatch = url.pathname.match(/^\/api\/solves\/(\d+)\/solutions\/([^/]+)$/);
        if (solutionMatch && request.method() === "PUT") {
            const id = Number(solutionMatch[1]);
            const mode = decodeURIComponent(solutionMatch[2]);
            const requestBody = (body ?? {}) as Record<string, unknown>;
            const saved = savedSolution(String((entries.find((entry) => entry.id === id) ?? historyEntry).scramble), mode, requestBody);
            savedBySolve.set(id, saved);
            await json(route, saved);
            return;
        }
        if (url.pathname === "/api/solve-jobs" && request.method() === "POST") {
            const requestBody = (body ?? {}) as Record<string, unknown>;
            const id = `job-${jobs.size + 1}`;
            const solveResult = resultFor(String(requestBody.scramble ?? historyEntry.scramble), String(requestBody.f2lMode ?? "greedy"));
            const job = {
                id,
                status: "completed",
                statesExplored: 12,
                statesPruned: 3,
                duplicateStates: 1,
                bestMoves: 7,
                completedCandidates: 1,
                candidatesEvaluated: 1,
                bestTotalMoves: solveResult.totalMoveCount,
                phase: "COMPLETE",
                currentCrossFace: String(requestBody.crossFace ?? "U"),
                completedCrosses: 1,
                totalCrosses: 1,
                optimizationCandidate: 1,
                totalOptimizationCandidates: 1,
                optimizationBudgetExpired: false,
                result: solveResult,
                error: null,
            };
            jobs.set(id, job);
            await json(route, job);
            return;
        }
        const jobMatch = url.pathname.match(/^\/api\/solve-jobs\/([^/]+)$/);
        if (jobMatch && request.method() === "GET") {
            await json(route, jobs.get(decodeURIComponent(jobMatch[1])) ?? {status: "completed", result: null});
            return;
        }
        if (jobMatch && request.method() === "DELETE") {
            const job = jobs.get(decodeURIComponent(jobMatch[1]));
            await json(route, {...(job as object), status: "cancelled", result: null});
            return;
        }
        if (url.pathname === "/api/algorithms") {
            const phase = url.searchParams.get("phase") ?? "";
            const slot = url.searchParams.get("slot") ?? "";
            const query = (url.searchParams.get("q") ?? "").toLowerCase();
            const status = url.searchParams.get("status") ?? "";
            const statusMatches = (entry: typeof algorithms[number]) => status === ""
                ? entry.status === "canonical"
                : status === "all" || entry.status === status;
            const filtered = algorithms.filter((entry) =>
                (!phase || entry.phase === phase)
                && (!slot || entry.slot === slot)
                && (!query || `${entry.name} ${entry.algorithm}`.toLowerCase().includes(query))
                && statusMatches(entry)
            );
            await json(route, {version: "1", items: filtered});
            return;
        }
        await json(route, {});
    });

    return {requests};
}

export function resultFor(scramble: string, f2lMode = "greedy") {
    return {
        scramble,
        crossFace: "U",
        f2lMode,
        f2lSetupCaseCount: 1,
        f2lInsertCaseCount: 1,
        cross: {name: "cross", algorithm: "R", moveCount: 1, solved: true, status: "Solved"},
        f2l: {
            name: "f2l",
            algorithm: "U R U'",
            moveCount: 3,
            solved: true,
            status: "Solved",
            traceComplete: true,
            pairAlgorithmMatchesStage: true,
            pairs: [],
        },
        oll: {name: "oll", algorithm: "F R U R' U' F'", moveCount: 6, solved: true, status: "Solved"},
        pll: {name: "pll", algorithm: "R2 U R U R' U' R' U' R' U R'", moveCount: 11, solved: true, status: "Solved"},
        solvedF2LSlots: "FR FL BL BR",
        fullySolved: true,
        totalMoveCount: 21,
        elapsedMs: 123.4,
        comparison: {
            fast: {crossFace: "U", f2lMoves: 3, ollMoves: 6, pllMoves: 11, totalMoves: 21, rotationCount: 0, pairOrder: [], pairTraceComplete: true},
            optimized: {crossFace: "U", f2lMoves: 3, ollMoves: 6, pllMoves: 11, totalMoves: 21, rotationCount: 0, pairOrder: [], pairTraceComplete: true},
            f2lMoveDifference: 0,
            ollMoveDifference: 0,
            pllMoveDifference: 0,
            totalMoveDifference: 0,
            rotationDifference: 0,
            pairOrderChanged: false,
            explanationCodes: ["NO_MEASURABLE_IMPROVEMENT"],
        },
    };
}

function savedSolution(scramble: string, mode: string, requestBody: Record<string, unknown> = {}) {
    return {
        ...resultFor(scramble, mode),
        mode,
        crossFaceRequested: String(requestBody.crossFaceRequested ?? "U"),
        solverVersion: "test-solver",
        updatedAt: "2026-07-26T10:01:00Z",
    };
}

async function json(route: Route, body: unknown, status = 200) {
    await route.fulfill({
        status,
        contentType: "application/json",
        body: JSON.stringify(body),
    });
}
