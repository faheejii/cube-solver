import {randomScrambleForEvent} from "cubing/scramble";
import {setSearchDebug} from "cubing/search";
import type {ScrambleRequest, ScrambleResponse} from "./scrambleWorkerProtocol";

setSearchDebug({prioritizeEsbuildWorkaroundForWorkerInstantiation: true});

globalThis.addEventListener("message", (event: MessageEvent<ScrambleRequest>) => {
    void generate(event.data);
});

async function generate(request: ScrambleRequest) {
    let response: ScrambleResponse;
    try {
        const scramble = await randomScrambleForEvent("333");
        response = {id: request.id, scramble: scramble.toString()};
    } catch (error) {
        response = {
            id: request.id,
            error: error instanceof Error ? error.message : "Scramble generation failed",
        };
    }
    globalThis.postMessage(response);
}
