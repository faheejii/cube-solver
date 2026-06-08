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
- legacy F2L DB mode for comparison
- OLL solving from seeded sticker-orientation signatures
- PLL solving from seeded last-layer permutation signatures, including final AUF handling
- validation-by-execution after DB lookup in F2L, OLL, and PLL
- Java HTTP API and Vite/React frontend
- 3D cube playback in the frontend through `cubing.js`

Known limitations:

- F2L is still experimental. The 2-phase setup/insert databases currently have partial case coverage.
- The legacy F2L DB remains available via `-Df2l.legacy=true`, but it has known signature collisions and should not be treated as authoritative.
- The frontend currently exposes one-scramble solve requests only.

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
- `GET /api/health`
- static frontend serving from `frontend/dist` when a frontend build exists

The default port is `8080`. Override it with:

```bash
mvn -q compile exec:java -Dexec.mainClass=server.ApiServerMain -Dserver.port=9090
```

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

Supported cross-face arguments are `D`, `U`, `F`, `B`, `L`, and `R`.

Use the legacy F2L DB path:

```bash
mvn -q clean compile exec:java -Dexec.mainClass=solver.SolverMain -Df2l.legacy=true
```

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
- [`src/main/java/algorithms/F2LCaseDatabase.java`](src/main/java/algorithms/F2LCaseDatabase.java)
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

Frontend:

- [`frontend/src/App.tsx`](frontend/src/App.tsx)
- [`frontend/src/CubeAnimator.tsx`](frontend/src/CubeAnimator.tsx)
- [`frontend/src/api.ts`](frontend/src/api.ts)
- [`frontend/vite.config.ts`](frontend/vite.config.ts)

## F2L

Default F2L flow:

1. Skip slots already solved after cross.
2. Try insert DB directly with prefixes: no prefix, AUF, and `y/y'/y2` plus AUF.
3. If insert misses, try a validated setup+insert DB path.
4. If no combined case works, try setup DB as a standalone phase.
5. Fall back to bounded one-slot search when no phase DB case works.

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

OLL signatures are sticker-orientation patterns, not cubie permutations. OLL lookup tries `""`, `U`, `U2`, and `U'`, then validates the candidate by execution.

PLL signatures track the logical last-layer corner and edge pieces occupying the four logical U-layer positions. PLL seeds each algorithm under the four possible final-AUF setup signatures, then the solver validates with possible post-AUF.

Add cases here:

- OLL: [`src/main/java/algorithms/OLLCaseDatabase.java`](src/main/java/algorithms/OLLCaseDatabase.java)
- PLL: [`src/main/java/algorithms/PLLCaseDatabase.java`](src/main/java/algorithms/PLLCaseDatabase.java)

## API Contract

Request:

```json
{
  "scramble": "R D R' D2 R D' R'",
  "crossFace": "U",
  "useLegacyF2L": false
}
```

Response fields include:

- selected cross face and F2L mode
- per-stage algorithm, move count, solved flag, and status
- solved F2L slot summary
- total move count
- elapsed time in milliseconds

Example:

```json
{
  "scramble": "R D R' D2 R D' R'",
  "crossFace": "U",
  "useLegacyF2L": false,
  "f2lMode": "two-phase DB + fallback",
  "cross": { "algorithm": "z2", "moveCount": 0, "solved": true, "status": "ok" },
  "f2l": { "algorithm": "y2 R U R' U2 R U' R'", "moveCount": 7, "solved": true, "status": "ok" },
  "oll": { "algorithm": "", "moveCount": 0, "solved": true, "status": "ok" },
  "pll": { "algorithm": "", "moveCount": 0, "solved": true, "status": "ok" },
  "fullySolved": true,
  "totalMoveCount": 7
}
```

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
