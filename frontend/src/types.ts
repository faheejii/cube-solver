export type SolveStage = {
    name: string;
    algorithm: string;
    moveCount: number;
    solved: boolean;
    status: string;
};

export type CubeStateSnapshot = {
    cornerPerm: number[];
    cornerOri: number[];
    edgePerm: number[];
    edgeOri: number[];
};

export type CubeOrientationKey = {
    up: string;
    right: string;
    front: string;
};

export type F2LCaseDescription = {
    cornerPosition: string;
    cornerOrientation: number;
    edgePosition: string;
    edgeOrientation: number;
    initiallyConnected: boolean;
    cornerInTargetSlot: boolean;
    edgeInMiddleLayer: boolean;
};

export type F2LSelectionEvidence = {
    pairMoveCount: number;
    remainingF2LMoveCount: number;
    totalRouteMoveCount: number;
    rotationCount: number;
    preservesSolvedSlots: boolean;
    pairWasAlreadyConnected: boolean;
    shortestAvailablePair: boolean;
    selectedForGlobalRoute: boolean;
    reasonCodes: string[];
};

export type F2LPair = {
    order: number;
    corner: string;
    edge: string;
    targetSlot: string;
    algorithm: string;
    moveCount: number;
    completeMoves: string[];
    startMoveIndex: number;
    endMoveIndex: number;
    moveBreakdownAvailable: boolean;
    setupAlgorithm: string | null;
    pairingAlgorithm: string | null;
    insertionAlgorithm: string | null;
    stateBefore: CubeStateSnapshot;
    orientationBefore: CubeOrientationKey;
    stateAfter: CubeStateSnapshot;
    orientationAfter: CubeOrientationKey;
    preservedSlots: string[];
    case: F2LCaseDescription;
    selectionEvidence: F2LSelectionEvidence;
};

export type F2LModeSummary = {
    crossFace: string;
    f2lMoves: number;
    ollMoves: number;
    pllMoves: number;
    totalMoves: number;
    rotationCount: number;
    pairOrder: string[];
    pairTraceComplete: boolean;
};

export type F2LModeComparison = {
    fast: F2LModeSummary;
    optimized: F2LModeSummary;
    f2lMoveDifference: number;
    ollMoveDifference: number;
    pllMoveDifference: number;
    totalMoveDifference: number;
    rotationDifference: number;
    pairOrderChanged: boolean;
    explanationCodes: string[];
};

export type SolveResponse = {
    scramble: string;
    crossFace: string;
    f2lMode: string;
    f2lSetupCaseCount: number;
    f2lInsertCaseCount: number;
    cross: SolveStage;
    f2l: SolveStage & {
        traceComplete: boolean;
        pairAlgorithmMatchesStage: boolean;
        pairs: F2LPair[];
    };
    oll: SolveStage;
    pll: SolveStage;
    solvedF2LSlots: string;
    fullySolved: boolean;
    totalMoveCount: number;
    elapsedMs: number;
    comparison?: F2LModeComparison | null;
};

export type SolveRequest = {
    scramble: string;
    crossFace: string;
    f2lMode: string;
};

export type F2LAlgorithmCatalogEntry = {
    phase: "setup" | "insert";
    name: string;
    slot: string | null;
    nonPreservedSlot: string | null;
    preservedSlots: string[];
    signature: {
        kind: "f2l" | "f2l-setup";
        cornerPosition: string;
        cornerOrientation: number;
        edgePosition: string;
        edgeOrientation: number;
    };
    algorithm: string;
    sourceSetup: string | null;
    status: "canonical" | "experimental" | "deprecated" | "test-only" | string;
    notes: string;
    previewSetup: string | null;
};

export type LastLayerAlgorithmCatalogEntry = {
    phase: "oll" | "pll";
    name: string;
    slot: null;
    nonPreservedSlot: null;
    preservedSlots: string[];
    signature: {
        kind: "oll" | "pll";
        [key: string]: string | boolean;
    };
    algorithm: string;
    sourceSetup: null;
    previewSetup: string;
    status: "canonical" | "experimental" | "deprecated" | "test-only" | string;
    notes: string;
};

export type AlgorithmCatalogEntry = F2LAlgorithmCatalogEntry | LastLayerAlgorithmCatalogEntry;

export type AlgorithmCatalogResponse = {
    version: string;
    items: AlgorithmCatalogEntry[];
};

export type F2LAlgorithmCatalogResponse = AlgorithmCatalogResponse;

export type SolveJobRequest = SolveRequest & {
    deadlineSeconds?: number;
    deepColorNeutral?: boolean;
    solveId?: number;
    saveOnComplete?: boolean;
};

export type AuthUser = {
    id: string;
    email: string;
    displayName: string | null;
    role: "user" | "admin" | string;
};

export type LoginRequest = {
    email: string;
    password: string;
};

export type RegisterRequest = LoginRequest & {
    displayName: string;
};

export type SolveJob = {
    id: string;
    status: "queued" | "running" | "completed" | "failed" | "cancelled" | "timed_out";
    statesExplored: number;
    statesPruned: number;
    duplicateStates: number;
    bestMoves: number;
    completedCandidates: number;
    candidatesEvaluated: number;
    bestTotalMoves: number;
    phase: string;
    currentCrossFace: string;
    completedCrosses: number;
    totalCrosses: number;
    optimizationCandidate: number;
    totalOptimizationCandidates: number;
    optimizationBudgetExpired: boolean;
    result: SolveResponse | null;
    error: string | null;
};

export type SolutionProcessSource = "timer" | "history" | "background";

export type SolutionProcess = {
    id: string;
    jobId: string | null;
    source: SolutionProcessSource;
    request: SolveJobRequest;
    status: SolveJob["status"];
    statesExplored: number;
    statesPruned: number;
    duplicateStates: number;
    bestMoves: number;
    completedCandidates: number;
    candidatesEvaluated: number;
    bestTotalMoves: number;
    phase: string;
    currentCrossFace: string;
    completedCrosses: number;
    totalCrosses: number;
    optimizationCandidate: number;
    totalOptimizationCandidates: number;
    optimizationBudgetExpired: boolean;
    createdAt: number;
    updatedAt: number;
    result: SolveResponse | null;
    error: string | null;
    cancelling: boolean;
};

export type CreateSolveAttemptRequest = {
    clientAttemptId: string;
    scramble: string;
    crossFaceRequested: string;
    timerMs: number | null;
    penalty: string;
    officialMs: number | null;
    dnf: boolean;
};

export type SolveHistoryEntry = {
    id: number;
    clientAttemptId: string;
    scramble: string;
    crossFaceRequested: string;
    timerMs: number | null;
    officialMs: number | null;
    penalty: string;
    dnf: boolean;
    fastCrossFaceRequested: string | null;
    optimizedCrossFaceRequested: string | null;
    createdAt: string;
};

export type SolveHistoryResponse = {
    items: SolveHistoryEntry[];
    nextCursor: string | null;
};

export type RollingAverage = {
    status: "value" | "dnf" | "insufficient";
    valueMs: number | null;
};

export type SolveStatistics = {
    solveCount: number;
    dnfCount: number;
    bestMs: number | null;
    averageMs: number | null;
    ao5: RollingAverage;
    ao12: RollingAverage;
    recentSolves: SolveHistoryEntry[];
};

export type SavedSolution = Omit<SolveResponse, "scramble"> & {
    mode: string;
    crossFaceRequested: string;
    solverVersion: string | null;
    updatedAt: string;
};

export type SolveHistoryDetail = {
    id: number;
    clientAttemptId: string;
    scramble: string;
    crossFaceRequested: string;
    timerMs: number | null;
    officialMs: number | null;
    penalty: string;
    dnf: boolean;
    createdAt: string;
    solutions: SavedSolution[];
};

export type SaveSolutionRequest = {
    crossFaceRequested: string;
    crossFaceChosen: string;
    f2lMode: string;
    f2lSetupCaseCount: number;
    f2lInsertCaseCount: number;
    solvedF2LSlots: string;
    totalMoves: number;
    fullySolved: boolean;
    solveElapsedMs: number;
    crossAlgorithm: string;
    crossMoves: number;
    crossSolved: boolean;
    crossStatus: string;
    f2lAlgorithm: string;
    f2lMoves: number;
    f2lSolved: boolean;
    f2lStatus: string;
    ollAlgorithm: string;
    ollMoves: number;
    ollSolved: boolean;
    ollStatus: string;
    pllAlgorithm: string;
    pllMoves: number;
    pllSolved: boolean;
    pllStatus: string;
    f2lTraceJson: string;
    comparisonJson?: string | null;
};
