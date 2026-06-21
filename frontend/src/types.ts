export type SolveStage = {
  name: string;
  algorithm: string;
  moveCount: number;
  solved: boolean;
  status: string;
};

export type SolveResponse = {
  scramble: string;
  crossFace: string;
  f2lMode: string;
  f2lSetupCaseCount: number;
  f2lInsertCaseCount: number;
  cross: SolveStage;
  f2l: SolveStage;
  oll: SolveStage;
  pll: SolveStage;
  solvedF2LSlots: string;
  fullySolved: boolean;
  totalMoveCount: number;
  elapsedMs: number;
};

export type SolveRequest = {
  scramble: string;
  crossFace: string;
  f2lMode: string;
};

export type SolveJobRequest = SolveRequest & {
  userId?: string;
  solveId?: number;
  saveOnComplete?: boolean;
};

export type SolveJob = {
  id: string;
  status: "queued" | "running" | "completed" | "failed" | "cancelled";
  statesExplored: number;
  statesPruned: number;
  duplicateStates: number;
  bestMoves: number;
  completedCandidates: number;
  candidatesEvaluated: number;
  bestTotalMoves: number;
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
  createdAt: number;
  updatedAt: number;
  result: SolveResponse | null;
  error: string | null;
  cancelling: boolean;
};

export type CreateSolveAttemptRequest = {
  userId: string;
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
  userId: string;
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
};
