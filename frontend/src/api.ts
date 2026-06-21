import type {
  CreateSolveAttemptRequest,
  SolveJob,
  SolveJobRequest,
  SaveSolutionRequest,
  SavedSolution,
  SolveHistoryDetail,
  SolveHistoryEntry,
  SolveHistoryResponse,
  SolveStatistics,
} from "./types";

const REQUEST_TIMEOUT_MS = 12_000;

async function requestJson<T>(input: RequestInfo | URL, init?: RequestInit): Promise<T> {
  const controller = new AbortController();
  const timeoutId = window.setTimeout(() => controller.abort(), REQUEST_TIMEOUT_MS);

  try {
    const response = await fetch(input, {
      ...init,
      signal: controller.signal,
    });
    if (response.status === 204) {
      return undefined as T;
    }
    const payload = (await response.json()) as unknown;
    if (!response.ok) {
      if (
        payload
        && typeof payload === "object"
        && "error" in payload
        && typeof (payload as { error?: unknown }).error === "string"
      ) {
        throw new Error((payload as { error: string }).error);
      }
      throw new Error("Request failed");
    }
    return payload as T;
  } catch (error) {
    if (error instanceof DOMException && error.name === "AbortError") {
      throw new Error("Request timed out");
    }
    throw error;
  } finally {
    window.clearTimeout(timeoutId);
  }
}

export async function startSolveJob(request: SolveJobRequest): Promise<SolveJob> {
  return requestJson<SolveJob>("/api/solve-jobs", {
    method: "POST",
    headers: {
      "Content-Type": "application/json",
    },
    body: JSON.stringify(request),
  });
}

export async function fetchSolveJob(jobId: string): Promise<SolveJob> {
  return requestJson<SolveJob>(`/api/solve-jobs/${encodeURIComponent(jobId)}`);
}

export async function cancelSolveJob(jobId: string): Promise<SolveJob> {
  return requestJson<SolveJob>(`/api/solve-jobs/${encodeURIComponent(jobId)}`, {
    method: "DELETE",
  });
}

export async function createSolveAttempt(request: CreateSolveAttemptRequest): Promise<SolveHistoryEntry> {
  return requestJson<SolveHistoryEntry>("/api/solves", {
    method: "POST",
    headers: {
      "Content-Type": "application/json",
    },
    body: JSON.stringify(request),
  });
}

export async function fetchSolveHistory(
  userId: string,
  limit = 25,
  cursor?: string | null,
): Promise<SolveHistoryResponse> {
  const params = new URLSearchParams({
    userId,
    limit: String(limit),
  });
  if (cursor) {
    params.set("cursor", cursor);
  }
  return requestJson<SolveHistoryResponse>(`/api/solves?${params.toString()}`);
}

export async function fetchSolveStatistics(userId: string): Promise<SolveStatistics> {
  return requestJson<SolveStatistics>(`/api/stats?userId=${encodeURIComponent(userId)}`);
}

export async function fetchSolveHistoryDetail(
  userId: string,
  solveId: number,
): Promise<SolveHistoryDetail> {
  return requestJson<SolveHistoryDetail>(`/api/solves/${solveId}?userId=${encodeURIComponent(userId)}`);
}

export async function deleteSolve(userId: string, solveId: number): Promise<void> {
  return requestJson<void>(`/api/solves/${solveId}?userId=${encodeURIComponent(userId)}`, {
    method: "DELETE",
  });
}

export async function saveSolveSolution(
  solveId: number,
  mode: string,
  request: SaveSolutionRequest,
): Promise<SavedSolution> {
  return requestJson<SavedSolution>(`/api/solves/${solveId}/solutions/${mode}`, {
    method: "PUT",
    headers: {
      "Content-Type": "application/json",
    },
    body: JSON.stringify(request),
  });
}
