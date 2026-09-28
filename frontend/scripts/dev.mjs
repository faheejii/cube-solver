import {spawn} from "node:child_process";
import {dirname, resolve} from "node:path";
import {fileURLToPath} from "node:url";
import {watchScrambleWorker} from "./build-scramble-worker.mjs";

const workerContext = await watchScrambleWorker();
const frontendRoot = resolve(dirname(fileURLToPath(import.meta.url)), "..");
const viteCli = resolve(frontendRoot, "node_modules/vite/bin/vite.js");
const viteProcess = spawn(process.execPath, [viteCli, ...process.argv.slice(2)], {
    cwd: frontendRoot,
    stdio: "inherit",
});

let receivedSignal;
const forwardSignal = (signal) => {
    receivedSignal = signal;
    if (viteProcess.exitCode === null) viteProcess.kill(signal);
};
process.once("SIGINT", forwardSignal);
process.once("SIGTERM", forwardSignal);

try {
    const exitCode = await new Promise((resolveExit, reject) => {
        viteProcess.once("error", reject);
        viteProcess.once("exit", (code) => resolveExit(code ?? 1));
    });
    process.exitCode = receivedSignal === "SIGINT" ? 130 : receivedSignal === "SIGTERM" ? 143 : exitCode;
} finally {
    process.off("SIGINT", forwardSignal);
    process.off("SIGTERM", forwardSignal);
    await workerContext.dispose();
}
