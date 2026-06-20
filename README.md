# CFOP Cube Solver

Java 17 Maven Rubik's Cube solver and experimentation project built around a CFOP pipeline:

- Cross
- F2L
- OLL
- PLL

The cube model is cubie-based (`cornerPerm/cornerOri`, `edgePerm/edgeOri`). Runtime solving uses `OrientedCube` plus `CubeOrientation`, so `x/y/z` rotations are frame changes instead of physical cubie mutations. Solver and analyzer code should stay frame-aware; `MoveApplier.applyMove` is the low-level physical executor.

## Current Status

Implemented:

- face turns, slice moves, cube rotations, and lowercase wide moves
- selected cross-face solving
- shared CFOP orchestration through `CfopSolveService`
- default 2-phase F2L solving with setup and insert phase databases
- OLL solving from seeded sticker-orientation signatures
- PLL solving from seeded last-layer permutation signatures, including final AUF handling
- validation-by-execution after DB lookup in F2L, OLL, and PLL
- Java HTTP API and Vite/React frontend
- 3D cube playback in the frontend through `cubing.js`

Known limitations:

- F2L setup and insert case coverage is intended to be complete enough for normal solves without IDA* fallback, but some seeded algorithms are not yet optimal.
- The IDA* fallback remains in place as a safety net for unexpected F2L misses.
- Optimized F2L can be significantly slower than fast mode on some scrambles because it evaluates multiple DB-backed branch lines before choosing a result.
- Solve history currently uses a browser-local anonymous user ID. It is suitable for local persistence but is not a secure account or authentication system.

## Requirements

- Java 17
- Maven 3.9+
- Node.js 20+ and npm 10+ for frontend development/builds

## Build And Test

```bash
mvn -q -Dmaven.compiler.useIncrementalCompilation=false test
```

```bash
mvn -q clean compile
```

## Run The API Server

```bash
mvn -q compile exec:java -Dexec.mainClass=server.ApiServerMain
```

This starts:

- `POST /api/solve`
- `POST /api/solves`
- `GET /api/solves`
- `GET /api/solves/{id}`
- `PUT /api/solves/{id}/solutions/{mode}`
- `GET /api/health`
- static frontend serving from `frontend/dist` when a frontend build exists

Postgres is optional at startup. If `DATABASE_URL` is set, the server validates the connection and creates the initial solve-history tables automatically.

The server automatically reads a root-level `.env` file if present. A simple local setup is:

```bash
DATABASE_URL='postgresql://USER:PASSWORD@HOST/neondb?sslmode=require&channel_binding=require'
```

Configuration precedence is:

- `-Ddatabase.url=...`
- real environment variables such as `DATABASE_URL`
- root `.env`

The server also accepts `-Ddatabase.url=...` if you prefer a JVM system property.

The default port is `8080`. Override it with:

```bash
mvn -q compile exec:java -Dexec.mainClass=server.ApiServerMain -Dserver.port=9090
```

`GET /api/health` now reports database status as well as API status.

Solve history uses separate attempt and generated-solution records:

- `solves` stores the user's scramble, timer result, penalty, and timestamp.
- `solve_solutions` stores generated CFOP output keyed by `(solve_id, mode)`.

This allows one timed solve to have independent Fast and Optimized solutions. Each mode stores one requested cross configuration and can be replaced independently from the other mode. Startup migration upgrades the earlier combined `solves` schema automatically.

The frontend creates an idempotent timed attempt before advancing to the next scramble. Its current solution is then stored separately. History rows open a solution dialog where missing modes are computed on demand; changing the saved cross creates a preview that must be explicitly saved to replace that mode.

## Frontend Development

Install dependencies:

```bash
cd frontend
npm install
```

Run the Vite dev server:

```bash
npm run dev
```

The Vite app proxies `/api` to `http://localhost:8080`, so run the Java API server alongside it.
The cube visualization uses `cubing.js` and can animate the full solution or individual CFOP stages.

Current frontend behavior:

- generates a WCA 3x3 scramble on initial load and on demand
- computes the solve in the background for the currently committed scramble
- keeps the scramble read-only by default, with an explicit edit mode
- supports fixed-face cross solving or color-neutral cross selection
- supports fast greedy F2L or optimized F2L branch search
- includes a timer with inspection behavior similar to common cube timers
- saves completed attempts to Postgres and advances to the next scramble automatically
- includes persistent solve history with Fast/Optimized and cross-specific solution review
- reveals the solution only when requested
- supports per-stage playback and playback speed changes
- includes a dark mode toggle

Timer controls:

- `Space`: arm, start inspection, start solve, or stop solve
- inspection over 15 seconds applies `+2`
- inspection over 17 seconds applies `DNF`

Build the frontend:

```bash
cd frontend
npm run build
```

## Run SolverMain

```bash
mvn -q clean compile exec:java -Dexec.mainClass=solver.SolverMain
```

Pass the selected cross face as the first argument:

```bash
mvn -q clean compile exec:java -Dexec.mainClass=solver.SolverMain -Dexec.args="U"
```

Pass one or more scrambles after the face. Separate multiple scrambles with semicolons:

```bash
mvn -q clean compile exec:java -Dexec.mainClass=solver.SolverMain -Dexec.args="U R D R' D2 R D' R'"
```

```bash
mvn -q clean compile exec:java -Dexec.mainClass=solver.SolverMain -Dexec.args="U R U R'; F R U R' U' F'"
```

Supported cross-face arguments are `D`, `U`, `F`, `B`, `L`, `R`, and `CN`.
Use `CN` to try every cross face and continue with the shortest cross solution.

If no scramble arguments are passed, `SolverMain` uses its built-in default scramble list.

Enable F2L diagnostics:

```bash
mvn -q -Df2l.debug=true compile exec:java -Dexec.mainClass=solver.SolverMain
```

```bash
mvn -q -Df2l.debug=true -Df2l.debug.verbose=true compile exec:java -Dexec.mainClass=solver.SolverMain
```

## Notation Support

The parser supports:

- face turns: `R U F D L B`
- double turns: `R2`
- inverse turns: `R'`
- slice moves: `M E S`
- cube rotations: `x y z`
- lowercase wide moves: `r u f d l b`

Runtime lowercase wide moves are frame-aware moves executed by `OrientedCube`. OLL and PLL case databases additionally normalize common last-layer alg notation at seed time, expanding lowercase wide moves into face plus slice turns before extracting signatures. This keeps human LL algs such as `r U R' U'` compatible with the repository's persistent frame model.

Examples:

```text
R U R' U'
r U r'
F R U R' U' F'
x y' r U2 r'
```

## Project Layout

Core cube model:

- [`src/main/java/cube/CubeState.java`](src/main/java/cube/CubeState.java)
- [`src/main/java/cube/Move.java`](src/main/java/cube/Move.java)
- [`src/main/java/cube/MoveApplier.java`](src/main/java/cube/MoveApplier.java)
- [`src/main/java/cube/MoveTables.java`](src/main/java/cube/MoveTables.java)
- [`src/main/java/cube/Algorithm.java`](src/main/java/cube/Algorithm.java)

Frame/orientation model:

- [`src/main/java/cube/CubeOrientation.java`](src/main/java/cube/CubeOrientation.java)
- [`src/main/java/cube/OrientedCube.java`](src/main/java/cube/OrientedCube.java)
- [`src/main/java/cube/OrientationFrames.java`](src/main/java/cube/OrientationFrames.java)

CFOP analyzers:

- [`src/main/java/cfop/CrossAnalyzer.java`](src/main/java/cfop/CrossAnalyzer.java)
- [`src/main/java/cfop/F2LAnalyzer.java`](src/main/java/cfop/F2LAnalyzer.java)
- [`src/main/java/cfop/OLLAnalyzer.java`](src/main/java/cfop/OLLAnalyzer.java)
- [`src/main/java/cfop/PLLAnalyzer.java`](src/main/java/cfop/PLLAnalyzer.java)

Case databases:

- [`src/main/java/algorithms/F2LSetupCaseDatabase.java`](src/main/java/algorithms/F2LSetupCaseDatabase.java)
- [`src/main/java/algorithms/F2LInsertCaseDatabase.java`](src/main/java/algorithms/F2LInsertCaseDatabase.java)
- [`src/main/java/algorithms/OLLCaseDatabase.java`](src/main/java/algorithms/OLLCaseDatabase.java)
- [`src/main/java/algorithms/PLLCaseDatabase.java`](src/main/java/algorithms/PLLCaseDatabase.java)

Solvers:

- [`src/main/java/solver/CrossSolver.java`](src/main/java/solver/CrossSolver.java)
- [`src/main/java/solver/F2LSolver.java`](src/main/java/solver/F2LSolver.java)
- [`src/main/java/solver/OLLSolver.java`](src/main/java/solver/OLLSolver.java)
- [`src/main/java/solver/PLLSolver.java`](src/main/java/solver/PLLSolver.java)
- [`src/main/java/solver/CfopSolveService.java`](src/main/java/solver/CfopSolveService.java)
- [`src/main/java/solver/SolverMain.java`](src/main/java/solver/SolverMain.java)

API / server:

- [`src/main/java/api/SolveApiRequest.java`](src/main/java/api/SolveApiRequest.java)
- [`src/main/java/server/CubeHttpServer.java`](src/main/java/server/CubeHttpServer.java)
- [`src/main/java/server/ApiServerMain.java`](src/main/java/server/ApiServerMain.java)

Persistence:

- [`src/main/java/database/DatabaseManager.java`](src/main/java/database/DatabaseManager.java)
- [`src/main/java/database/SolveHistoryRepository.java`](src/main/java/database/SolveHistoryRepository.java)
- [`src/main/java/config/Dotenv.java`](src/main/java/config/Dotenv.java)

Frontend:

- [`frontend/src/App.tsx`](frontend/src/App.tsx)
- [`frontend/src/CubeAnimator.tsx`](frontend/src/CubeAnimator.tsx)
- [`frontend/src/api.ts`](frontend/src/api.ts)
- [`frontend/vite.config.ts`](frontend/vite.config.ts)

## Cross

Cross is solved first and remains frame-aware throughout the pipeline.

The solver accepts a selected cross face (`D`, `U`, `F`, `B`, `L`, or `R`) and normalizes that choice through `OrientationFrames`. In runtime terms, cube rotations such as `x/y/z` are not applied as physical cubie mutations. They update the solver frame through `OrientedCube`, and later stages continue working in that persistent oriented frame.

Current fixed-face cross behavior:

1. Convert the selected cross face into the solver's D-cross frame.
2. For U-cross and other non-D choices, keep that normalization as part of the returned cross algorithm.
3. Search for a cross using no-`B` face turns only.
4. Try no rotation, `y`, `y2`, and `y'` prefixes and keep the first shortest solution found by depth.
5. Hand the resulting oriented state to F2L without collapsing the frame back into a physically rotated cube.

This keeps cross output aligned with the rest of the CFOP pipeline and avoids the older class of bugs where F2L case meaning changed because global rotations were treated as physical moves instead of frame changes.

Color-neutral cross mode tries all six fixed faces, chooses the candidate with the lowest reported cross move count, and then runs the rest of CFOP using that chosen concrete face.

## F2L

Default F2L flow:

1. Skip slots already solved after cross.
2. Try insert DB directly with prefixes: no prefix, AUF, and `y/y'/y2` plus AUF.
3. If insert misses, try a validated setup+insert DB path.
4. If no combined case works, try setup DB as a standalone phase.
5. Fall back to bounded one-slot search only if no phase DB case works.

The setup and insert databases are expected to cover normal F2L solving without using the fallback path. The fallback remains deliberately available as a defensive path for unexpected signatures, invalid seed coverage, or future case-database changes.

The API and frontend expose two F2L search modes:

- `greedy`: the default fast mode, choosing the best available next slot at each step
- `optimized`: recursively tries viable DB-backed slot choices, completes each branch, and picks the shortest normalized F2L solution

Optimized mode has a branch-state limit and falls back to greedy behavior if it cannot complete a branch within that search limit. Candidate generation can still be expensive, so use fast mode when responsiveness matters.

Setup and insert cases are keyed by:

```text
(insert slot, preservation mask, F2L case signature)
```

F2L case authors should write cases in a normal D-cross human frame. Do not add global cross-normalizing rotations like `z2` to every seed. `F2LSlot.FR` in a seed means the authored FR case, even if the runtime frame after a selected cross face sees the same physical target through another visible slot.

Signatures are used as fast lookup hints, but they are not trusted as the only source of truth. When keyed lookup misses, the solver can validate seeded setup algorithms by execution and only accepts a setup+insert candidate if it solves the intended physical target slot while preserving the cross and already solved slots.

Insert seed entries take `insertSlot` and derive the preservation mask as every F2L slot except that target slot. Setup seed entries take an explicit `sourceSetup`, a setup algorithm, and one `nonPreservedSlot`; the setup database derives the preservation mask as every F2L slot except `nonPreservedSlot`.

Add 2-phase F2L cases here:

- setup cases: [`src/main/java/algorithms/F2LSetupCaseDatabase.java`](src/main/java/algorithms/F2LSetupCaseDatabase.java)
- insert cases: [`src/main/java/algorithms/F2LInsertCaseDatabase.java`](src/main/java/algorithms/F2LInsertCaseDatabase.java)

For setup cases, `sourceSetup` is applied to a solved cube to create the case signature. The setup algorithm should transform that case into an insert-ready case while only allowing `nonPreservedSlot` to be disrupted. The solver validates setup candidates by checking execution against the current frame and, for the combined path, by requiring an insert DB case to solve the next state.

For insert cases, the seed algorithm should solve `insertSlot` from an insert-ready case while only allowing that target slot to be disrupted. The database seeds by applying the inverse algorithm to a solved cube and extracting the signature.

## OLL And PLL

OLL signatures are sticker-orientation patterns, not cubie permutations. OLL lookup tries no prefix, AUF prefixes, and `y/y2/y'` plus AUF prefixes, then validates the candidate by execution. If a signature collision points at the wrong case, the solver scans all seeded OLL cases and accepts only an algorithm that preserves cross/F2L and solves OLL.

OLL seed signatures are allowed to collide because signature lookup is treated as a fast hint, not as proof. Seeded OLL algorithms are still validated when the database is built: inverse setup must preserve cross/F2L, and the algorithm must solve OLL while preserving cross/F2L.

PLL signatures track the logical last-layer corner and edge pieces occupying the four logical U-layer positions. PLL seeds each algorithm under the four possible final-AUF setup signatures, then the solver validates with possible post-AUF.

PLL seed algorithms are also normalized as last-layer notation before seeding. Each generated final-AUF setup is validated when the database is built.

Add cases here:

- OLL: [`src/main/java/algorithms/OLLCaseDatabase.java`](src/main/java/algorithms/OLLCaseDatabase.java)
- PLL: [`src/main/java/algorithms/PLLCaseDatabase.java`](src/main/java/algorithms/PLLCaseDatabase.java)

## API Contract

Request:

```json
{
  "scramble": "R D R' D2 R D' R'",
  "crossFace": "U",
  "f2lMode": "greedy"
}
```

`crossFace` is optional in practice. If it is missing or blank, the API defaults to `U`.
Use `"CN"` or `"Color Neutral"` to enable color-neutral cross selection. Responses always return the concrete face that was actually chosen.
`f2lMode` is also optional and defaults to `"greedy"`. Use `"optimized"` to run the branching F2L search.

Response fields include:

- selected cross face
- F2L setup and insert case counts
- per-stage algorithm, move count, solved flag, and status
- solved F2L slot summary
- total move count
- elapsed time in milliseconds

Example:

```json
{
  "scramble": "R D R' D2 R D' R'",
  "crossFace": "U",
  "f2lMode": "greedy",
  "f2lSetupCaseCount": 121,
  "f2lInsertCaseCount": 14,
  "cross": { "algorithm": "z2", "moveCount": 0, "solved": true, "status": "ok" },
  "f2l": { "algorithm": "y2 R U R' U2 R U' R'", "moveCount": 7, "solved": true, "status": "ok" },
  "oll": { "algorithm": "", "moveCount": 0, "solved": true, "status": "ok" },
  "pll": { "algorithm": "", "moveCount": 0, "solved": true, "status": "ok" },
  "fullySolved": true,
  "totalMoveCount": 7,
  "elapsedMs": 2.341
}
```

Error behavior:

- `POST /api/solve` returns `400` for invalid scramble or face input
- `POST /api/solve` returns `500` for unexpected internal failures
- non-`POST` requests to `/api/solve` return `405`

## Useful Targeted Tests

```bash
mvn -q -Dtest=test.F2LPhaseCaseDatabaseTest test
```

```bash
mvn -q -Dtest=test.CfopSolveServiceTest test
```

```bash
mvn -q -Dtest=test.OLLAnalyzerTest,test.OLLSolverTest test
```

```bash
mvn -q -Dtest=test.PLLAnalyzerTest,test.PLLCaseDatabaseTest,test.PLLSolverTest test
```
