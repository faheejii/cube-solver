import type {ScrambleResponse} from "./scrambleWorkerProtocol";

type PendingRequest = {
    resolve: (scramble: string) => void;
    reject: (error: Error) => void;
};

let worker: Worker | null = null;
let nextRequestId = 1;
const pending = new Map<number, PendingRequest>();

export function generateScramble(): Promise<string> {
    return new Promise((resolve, reject) => {
        const id = nextRequestId++;
        pending.set(id, {resolve, reject});
        try {
            getWorker().postMessage({id});
        } catch (error) {
            pending.delete(id);
            reject(error instanceof Error ? error : new Error("Could not start the scramble worker"));
        }
    });
}

function getWorker(): Worker {
    if (worker) {
        return worker;
    }

    const instance = new Worker("/scramble-worker/scramble-worker.js", {type: "module", name: "scramble-generator"});
    instance.addEventListener("message", (event: MessageEvent<ScrambleResponse>) => {
        const request = pending.get(event.data.id);
        if (!request) {
            return;
        }
        pending.delete(event.data.id);
        if ("scramble" in event.data) {
            request.resolve(event.data.scramble);
        } else {
            request.reject(new Error(event.data.error));
        }
    });
    instance.addEventListener("error", (event) => {
        failWorker(new Error(event.message || "Scramble worker failed"));
    });
    instance.addEventListener("messageerror", () => {
        failWorker(new Error("Could not read a response from the scramble worker"));
    });
    worker = instance;
    return instance;
}

function failWorker(error: Error) {
    worker?.terminate();
    worker = null;
    for (const request of pending.values()) {
        request.reject(error);
    }
    pending.clear();
}
