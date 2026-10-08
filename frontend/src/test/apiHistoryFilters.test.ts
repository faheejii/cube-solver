import {afterEach, describe, expect, it, vi} from "vitest";
import {fetchSolveHistory} from "../api";

describe("fetchSolveHistory filters", () => {
    afterEach(() => vi.unstubAllGlobals());

    it("serializes the literal search, penalty, cursor, and limit parameters", async () => {
        const fetchMock = vi.fn().mockResolvedValue({
            ok: true,
            status: 200,
            json: async () => ({items: [], nextCursor: null, totalCount: 3}),
        });
        vi.stubGlobal("fetch", fetchMock);

        const page = await fetchSolveHistory(20, "after:17", {q: "R U +", penalty: "+2"});

        const requestedUrl = new URL(fetchMock.mock.calls[0][0] as string, window.location.origin);
        expect(requestedUrl.pathname).toBe("/api/solves");
        expect(requestedUrl.searchParams.get("limit")).toBe("20");
        expect(requestedUrl.searchParams.get("cursor")).toBe("after:17");
        expect(requestedUrl.searchParams.get("q")).toBe("R U +");
        expect(requestedUrl.searchParams.get("penalty")).toBe("+2");
        expect(page).toMatchObject({totalCount: 3, items: [], nextCursor: null});
    });

    it("serializes the time filter while preserving the cursor", async () => {
        const fetchMock = vi.fn().mockResolvedValue({
            ok: true,
            status: 200,
            json: async () => ({items: [], nextCursor: null, totalCount: 2}),
        });
        vi.stubGlobal("fetch", fetchMock);

        await fetchSolveHistory(20, "after:9", {time: "*.21"});

        const requestedUrl = new URL(fetchMock.mock.calls[0][0] as string, window.location.origin);
        expect(requestedUrl.searchParams.get("time")).toBe("*.21");
        expect(requestedUrl.searchParams.get("cursor")).toBe("after:9");
        expect(requestedUrl.searchParams.has("q")).toBe(false);
    });

    it("rejects conflicting scramble and time filters before making a request", async () => {
        const fetchMock = vi.fn();
        vi.stubGlobal("fetch", fetchMock);

        await expect(fetchSolveHistory(20, null, {q: "R U", time: "0.21"}))
            .rejects.toThrow("cannot be filtered by scramble and time");
        expect(fetchMock).not.toHaveBeenCalled();
    });

    it("omits filter parameters for the shared unfiltered history request", async () => {
        const fetchMock = vi.fn().mockResolvedValue({
            ok: true,
            status: 200,
            json: async () => ({items: [], nextCursor: null}),
        });
        vi.stubGlobal("fetch", fetchMock);

        await fetchSolveHistory(20);

        const requestedUrl = new URL(fetchMock.mock.calls[0][0] as string, window.location.origin);
        expect(requestedUrl.searchParams.has("q")).toBe(false);
        expect(requestedUrl.searchParams.has("penalty")).toBe(false);
    });
});
