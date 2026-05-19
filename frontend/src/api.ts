import type { SolveRequest, SolveResponse } from "./types";

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
