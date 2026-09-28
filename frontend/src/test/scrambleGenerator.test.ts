import {afterEach, beforeEach, describe, expect, it, vi} from "vitest";
import type {ScrambleResponse} from "../scrambleWorkerProtocol";

type ScrambleGenerator = typeof import("../scrambleGenerator");

class MockWorker {
    static instances: MockWorker[] = [];
    readonly messages: Array<{id: number}> = [];
    terminated = false;
    private readonly listeners = new Map<string, Array<EventListenerOrEventListenerObject>>();

    constructor(readonly url: string, readonly options?: WorkerOptions) {
        MockWorker.instances.push(this);
    }

    addEventListener(type: string, listener: EventListenerOrEventListenerObject) {
        const listeners = this.listeners.get(type) ?? [];
        listeners.push(listener);
        this.listeners.set(type, listeners);
    }

    postMessage(message: {id: number}) {
        this.messages.push(message);
    }

    terminate() {
        this.terminated = true;
    }

    respond(message: ScrambleResponse) {
        this.dispatch("message", new MessageEvent("message", {data: message}));
    }

    fail(message: string) {
        this.dispatch("error", new ErrorEvent("error", {message}));
    }

    failToDeserialize() {
        this.dispatch("messageerror", new MessageEvent("messageerror"));
    }

    private dispatch(type: string, event: Event) {
        for (const listener of this.listeners.get(type) ?? []) {
            if (typeof listener === "function") listener.call(this, event);
            else listener.handleEvent(event);
        }
    }
}

describe("scramble worker client", () => {
    let generator: ScrambleGenerator;

    beforeEach(async () => {
        MockWorker.instances = [];
        vi.stubGlobal("Worker", MockWorker as unknown as typeof Worker);
        vi.resetModules();
        generator = await import("../scrambleGenerator");
    });

    afterEach(() => {
        vi.unstubAllGlobals();
    });

    it("correlates concurrent requests with their responses", async () => {
        const first = generator.generateScramble();
        const second = generator.generateScramble();
        const [worker] = MockWorker.instances;

        expect(worker.url).toBe("/scramble-worker/scramble-worker.js");
        expect(worker.messages).toEqual([{id: 1}, {id: 2}]);

        worker.respond({id: 2, scramble: "R U R'"});
        worker.respond({id: 1, scramble: "F D F'"});

        await expect(first).resolves.toBe("F D F'");
        await expect(second).resolves.toBe("R U R'");
    });

    it("rejects all pending requests and recreates a failed worker", async () => {
        const first = generator.generateScramble();
        const second = generator.generateScramble();
        const failedWorker = MockWorker.instances[0];

        failedWorker.fail("worker boot failed");

        await expect(first).rejects.toThrow("worker boot failed");
        await expect(second).rejects.toThrow("worker boot failed");
        expect(failedWorker.terminated).toBe(true);

        const next = generator.generateScramble();
        expect(MockWorker.instances).toHaveLength(2);
        MockWorker.instances[1].respond({id: 3, scramble: "U R U'"});
        await expect(next).resolves.toBe("U R U'");
    });

    it("rejects pending requests when a response cannot be deserialized", async () => {
        const pending = generator.generateScramble();
        const [worker] = MockWorker.instances;

        worker.failToDeserialize();

        await expect(pending).rejects.toThrow("Could not read a response from the scramble worker");
        expect(worker.terminated).toBe(true);
    });

    it("rejects a failed scramble request without discarding the worker", async () => {
        const pending = generator.generateScramble();
        const [worker] = MockWorker.instances;
        worker.respond({id: 1, error: "scramble source unavailable"});

        await expect(pending).rejects.toThrow("scramble source unavailable");
        expect(worker.terminated).toBe(false);
    });
});
