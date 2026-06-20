import type {
  CreateSolveAttemptRequest,
  SaveSolutionRequest,
  SavedSolution,
  SolveHistoryDetail,
  SolveHistoryEntry,
  SolveHistoryResponse,
  SolveRequest,
  SolveResponse,
} from "./types";

export async function solveCube(request: SolveRequest): Promise<SolveResponse> {
  const response = await fetch("/api/solve", {
    method: "POST",
    headers: {
      "Content-Type": "application/json",
    },
    body: JSON.stringify(request),
  });

  const payload = (await response.json()) as SolveResponse | { error: string };
  if (!response.ok) {
    throw new Error("error" in payload ? payload.error : "Solve request failed");
  }

  return payload as SolveResponse;
}

export async function createSolveAttempt(request: CreateSolveAttemptRequest): Promise<SolveHistoryEntry> {
  const response = await fetch("/api/solves", {
    method: "POST",
    headers: {
      "Content-Type": "application/json",
    },
    body: JSON.stringify(request),
  });

  const payload = (await response.json()) as SolveHistoryEntry | { error: string };
  if (!response.ok) {
    throw new Error("error" in payload ? payload.error : "Attempt save failed");
  }

  return payload as SolveHistoryEntry;
}

export async function fetchSolveHistory(userId: string, limit = 20): Promise<SolveHistoryEntry[]> {
  const response = await fetch(`/api/solves?userId=${encodeURIComponent(userId)}&limit=${limit}`);
  const payload = (await response.json()) as SolveHistoryResponse | { error: string };
  if (!response.ok) {
    throw new Error("error" in payload ? payload.error : "History request failed");
  }

  return (payload as SolveHistoryResponse).items;
}

export async function fetchSolveHistoryDetail(
  userId: string,
  solveId: number,
): Promise<SolveHistoryDetail> {
  const response = await fetch(`/api/solves/${solveId}?userId=${encodeURIComponent(userId)}`);
  const payload = (await response.json()) as SolveHistoryDetail | { error: string };
  if (!response.ok) {
    throw new Error("error" in payload ? payload.error : "Solve detail request failed");
  }
  return payload as SolveHistoryDetail;
}

export async function saveSolveSolution(
  solveId: number,
  mode: string,
  request: SaveSolutionRequest,
): Promise<SavedSolution> {
  const response = await fetch(`/api/solves/${solveId}/solutions/${mode}`, {
    method: "PUT",
    headers: {
      "Content-Type": "application/json",
    },
    body: JSON.stringify(request),
  });
  const payload = (await response.json()) as SavedSolution | { error: string };
  if (!response.ok) {
    throw new Error("error" in payload ? payload.error : "Solution save failed");
  }
  return payload as SavedSolution;
}
