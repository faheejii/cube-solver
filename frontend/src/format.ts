import type { RollingAverage } from "./types";

export function formatSolveTime(elapsedMs: number): string {
  const totalCentiseconds = Math.max(0, Math.floor(elapsedMs / 10));
  const minutes = Math.floor(totalCentiseconds / 6_000);
  const seconds = Math.floor((totalCentiseconds % 6_000) / 100);
  const centiseconds = totalCentiseconds % 100;

  if (minutes > 0) {
    return `${minutes}:${String(seconds).padStart(2, "0")}.${String(centiseconds).padStart(2, "0")}`;
  }
  return `${seconds}.${String(centiseconds).padStart(2, "0")}`;
}

export function formatHistoryTime(
  officialMs: number | null,
  penalty: string,
  dnf: boolean,
): string {
  if (dnf || penalty === "dnf" || officialMs === null) {
    return "DNF";
  }
  if (penalty === "+2") {
    return `${formatSolveTime(officialMs)}+`;
  }
  return formatSolveTime(officialMs);
}

export function formatMetricTime(valueMs: number | null): string {
  return valueMs === null ? "—" : formatSolveTime(valueMs);
}

export function formatRollingAverage(average: RollingAverage | null): string {
  if (!average || average.status === "insufficient") {
    return "—";
  }
  if (average.status === "dnf") {
    return "DNF";
  }
  return formatMetricTime(average.valueMs);
}

export function f2lModeLabel(mode: string): string {
  return mode === "optimized" ? "Optimized" : "Fast";
}

export function crossFaceLabel(face: string): string {
  return face === "CN" ? "Color Neutral" : face;
}
