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
};
