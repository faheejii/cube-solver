import {beforeEach, describe, expect, it, vi} from "vitest";
import {
    fetchCurrentUser,
    fetchSolveHistory,
    login,
    SESSION_EXPIRED_EVENT,
    startSolveJob,
} from "../api";

const fetchMock = vi.fn<typeof fetch>();

describe("API session handling", () => {
    beforeEach(() => {
        fetchMock.mockReset();
        vi.stubGlobal("fetch", fetchMock);
    });

    it("sends credentials for authentication requests", async () => {
        fetchMock.mockResolvedValue(new Response(JSON.stringify({
            id: "user-1",
            email: "cube@example.com",
            displayName: "Cube",
        }), {status: 200, headers: {"Content-Type": "application/json"}}));

        await login({email: "cube@example.com", password: "password123"});

        expect(fetchMock).toHaveBeenCalledWith("/api/auth/login", expect.objectContaining({
            credentials: "include",
            method: "POST",
        }));
    });

    it("treats an unauthenticated current-user request as a signed-out session", async () => {
        fetchMock.mockResolvedValue(new Response(JSON.stringify({error: "Authentication required"}), {
            status: 401,
            headers: {"Content-Type": "application/json"},
        }));

        await expect(fetchCurrentUser()).resolves.toBeNull();
    });

    it("announces session expiry for protected API requests", async () => {
        fetchMock.mockResolvedValue(new Response(JSON.stringify({error: "Authentication required"}), {
            status: 401,
            headers: {"Content-Type": "application/json"},
        }));
        const listener = vi.fn();
        window.addEventListener(SESSION_EXPIRED_EVENT, listener);

        await expect(fetchSolveHistory()).rejects.toMatchObject({status: 401});

        expect(listener).toHaveBeenCalledOnce();
        window.removeEventListener(SESSION_EXPIRED_EVENT, listener);
    });

    it("sends the configured solve deadline with solve jobs", async () => {
        fetchMock.mockResolvedValue(new Response(JSON.stringify({id: "job-1"}), {
            status: 200,
            headers: {"Content-Type": "application/json"},
        }));

        await startSolveJob({scramble: "R", crossFace: "U", f2lMode: "greedy", deadlineSeconds: 45});

        expect(fetchMock).toHaveBeenCalledWith("/api/solve-jobs", expect.objectContaining({
            body: JSON.stringify({scramble: "R", crossFace: "U", f2lMode: "greedy", deadlineSeconds: 45}),
        }));
    });

    it("sends deep color-neutral optimization when requested", async () => {
        fetchMock.mockResolvedValue(new Response(JSON.stringify({id: "job-2"}), {
            status: 200,
            headers: {"Content-Type": "application/json"},
        }));

        await startSolveJob({
            scramble: "R",
            crossFace: "CN",
            f2lMode: "optimized",
            deadlineSeconds: 15,
            deepColorNeutral: true,
        });

        expect(fetchMock).toHaveBeenCalledWith("/api/solve-jobs", expect.objectContaining({
            body: JSON.stringify({
                scramble: "R",
                crossFace: "CN",
                f2lMode: "optimized",
                deadlineSeconds: 15,
                deepColorNeutral: true,
            }),
        }));
    });
});
