import {dirname, resolve} from "node:path";
import {rm} from "node:fs/promises";
import {fileURLToPath, pathToFileURL} from "node:url";
import {build, context} from "esbuild";

const frontendRoot = resolve(dirname(fileURLToPath(import.meta.url)), "..");
const scrambleWorkerOutput = resolve(frontendRoot, "public/scramble-worker");

const buildOptions = {
    absWorkingDir: frontendRoot,
    entryPoints: ["src/scramble-worker.ts"],
    bundle: true,
    splitting: true,
    format: "esm",
    platform: "browser",
    target: "es2022",
    minify: true,
    outdir: scrambleWorkerOutput,
    entryNames: "scramble-worker",
    chunkNames: "chunks/[name]-[hash]",
};

async function cleanOutputDirectory(outputDirectory) {
    await rm(outputDirectory, {recursive: true, force: true});
}

export async function watchScrambleWorker() {
    await cleanOutputDirectory(scrambleWorkerOutput);
    const workerContext = await context(buildOptions);
    await workerContext.watch();
    return workerContext;
}

export async function buildScrambleWorker(outputDirectory = scrambleWorkerOutput) {
    await cleanOutputDirectory(outputDirectory);
    await build({...buildOptions, outdir: outputDirectory});
}

if (process.argv[1] && import.meta.url === pathToFileURL(resolve(process.argv[1])).href) {
    buildScrambleWorker().catch((error) => {
        console.error("Failed to build the cubing.js scramble worker:", error);
        process.exitCode = 1;
    });
}
