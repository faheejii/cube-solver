import type {
    AuthUser,
    CreateSolveAttemptRequest,
    SolveJob,
    SolveJobRequest,
    SaveSolutionRequest,
    SavedSolution,
    SolveHistoryDetail,
    SolveHistoryEntry,
    SolveHistoryResponse,
    SolveStatistics,
    LoginRequest,
    RegisterRequest,
    AlgorithmCatalogResponse,
} from "./types";

const REQUEST_TIMEOUT_MS = 12_000;

export const SESSION_EXPIRED_EVENT = "cube-solver:session-expired";

export class ApiError extends Error {
    constructor(message: string, readonly status: number) {
        super(message);
        this.name = "ApiError";
    }
}

async function requestJson<T>(
    input: RequestInfo | URL,
    init?: RequestInit,
    notifySessionExpiry = true,
): Promise<T> {
    const controller = new AbortController();
    const timeoutId = window.setTimeout(() => controller.abort(), REQUEST_TIMEOUT_MS);

    try {
        const response = await fetch(input, {
            ...init,
            credentials: "include",
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
                const message = (payload as { error: string }).error;
                if (response.status === 401 && notifySessionExpiry) {
                    window.dispatchEvent(new Event(SESSION_EXPIRED_EVENT));
                }
                throw new ApiError(message, response.status);
            }
            if (response.status === 401 && notifySessionExpiry) {
                window.dispatchEvent(new Event(SESSION_EXPIRED_EVENT));
            }
            throw new ApiError("Request failed", response.status);
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

export async function register(request: RegisterRequest): Promise<AuthUser> {
    return requestJson<AuthUser>("/api/auth/register", jsonRequest("POST", request), false);
}

export async function login(request: LoginRequest): Promise<AuthUser> {
    return requestJson<AuthUser>("/api/auth/login", jsonRequest("POST", request), false);
}

export async function logout(): Promise<void> {
    return requestJson<void>("/api/auth/logout", {method: "POST"}, false);
}

export async function fetchCurrentUser(): Promise<AuthUser | null> {
    try {
        return await requestJson<AuthUser>("/api/auth/me", undefined, false);
    } catch (error) {
        if (error instanceof ApiError && error.status === 401) {
            return null;
        }
        throw error;
    }
}

function jsonRequest(method: string, body: unknown): RequestInit {
    return {
        method,
        headers: {"Content-Type": "application/json"},
        body: JSON.stringify(body),
    };
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
    limit = 25,
    cursor?: string | null,
): Promise<SolveHistoryResponse> {
    const params = new URLSearchParams({
        limit: String(limit),
    });
    if (cursor) {
        params.set("cursor", cursor);
    }
    return requestJson<SolveHistoryResponse>(`/api/solves?${params.toString()}`);
}

export async function fetchSolveStatistics(): Promise<SolveStatistics> {
    return requestJson<SolveStatistics>("/api/stats");
}

export async function fetchSolveHistoryDetail(
    solveId: number,
): Promise<SolveHistoryDetail> {
    return requestJson<SolveHistoryDetail>(`/api/solves/${solveId}`);
}

export async function deleteSolve(solveId: number): Promise<void> {
    return requestJson<void>(`/api/solves/${solveId}`, {
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

export async function fetchAlgorithms(filters?: {
    phase?: string;
    slot?: string;
    search?: string;
    status?: string;
}): Promise<AlgorithmCatalogResponse> {
    const params = new URLSearchParams();
    if (filters?.phase) params.set("phase", filters.phase);
    if (filters?.slot) params.set("slot", filters.slot);
    if (filters?.search) params.set("q", filters.search);
    if (filters?.status) params.set("status", filters.status);
    const query = params.toString();
    return requestJson<AlgorithmCatalogResponse>(
        `/api/algorithms${query ? `?${query}` : ""}`
    );
}
