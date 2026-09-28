import assert from "node:assert/strict";
import {mkdtemp, mkdir, readFile, readdir, rm, writeFile} from "node:fs/promises";
import {tmpdir} from "node:os";
import {join} from "node:path";
import {test} from "node:test";
import {buildScrambleWorker} from "./build-scramble-worker.mjs";

test("build replaces stale generated assets and emits cubing's nested worker", async (context) => {
    const temporaryRoot = await mkdtemp(join(tmpdir(), "cube-solver-worker-build-"));
    context.after(() => rm(temporaryRoot, {recursive: true, force: true}));
    const generatedDirectory = join(temporaryRoot, "public", "scramble-worker");
    const siblingDirectory = join(temporaryRoot, "public", "other-assets");
    await mkdir(generatedDirectory, {recursive: true});
    await mkdir(siblingDirectory, {recursive: true});
    await writeFile(join(generatedDirectory, "stale-chunk.js"), "obsolete hashed chunk");
    await writeFile(join(siblingDirectory, "keep.txt"), "unrelated public asset");

    await buildScrambleWorker(generatedDirectory);

    await assert.rejects(readFile(join(generatedDirectory, "stale-chunk.js")), {code: "ENOENT"});
    await readFile(join(generatedDirectory, "scramble-worker.js"));
    const chunks = await readdir(join(generatedDirectory, "chunks"));
    assert.ok(chunks.some((name) => name.startsWith("search-worker-entry-")));
    assert.equal(await readFile(join(siblingDirectory, "keep.txt"), "utf8"), "unrelated public asset");
});
