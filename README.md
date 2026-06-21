# CFOP Cube Solver

Java 17 and Maven project for solving a 3x3 Rubik's Cube with a CFOP pipeline:

1. `Cross`
2. `F2L`
3. `OLL`
4. `PLL`

The project includes:

- a Java cube solver and HTTP API
- a Vite + React frontend
- solve history stored in Postgres
- a 3D cube animation in the browser

## What the solver does

The solver works on a cubie model, which means it tracks where the pieces are and how they are twisted. For runtime solving, it uses a frame-aware model:

- `OrientedCube` holds the cube together with its current frame
- `CubeOrientation` tracks which face is being treated as which logical side
- `x`, `y`, and `z` rotations change the frame instead of physically mutating the cube model

That matters because cross, F2L, OLL, and PLL are all solved from the currently oriented view of the cube. Low-level move execution still happens through `MoveApplier.applyMove`.

## CFOP stages

- `Cross`: solves the four cross edges on the chosen face
- `F2L`: pairs each corner and edge, then inserts the pair into its slot
- `OLL`: orients the last-layer pieces so the top face becomes one color
- `PLL`: permutes the last-layer pieces so the cube is solved

## Current Status

Implemented:

- face turns, slice moves, cube rotations, and lowercase wide moves
- selected cross-face solving
- color-neutral cross solving
- shared CFOP orchestration through `CfopSolveService`
- default two-phase F2L solving with separate setup and insert databases
- OLL solving from seeded sticker-orientation signatures
- PLL solving from seeded last-layer permutation signatures, including final AUF handling
- validation-by-execution after database lookup in F2L, OLL, and PLL
- Java HTTP API and Vite/React frontend
- 3D cube playback in the frontend through `cubing.js`

Known limitations:

- F2L setup and insert coverage is intended to be complete enough for normal solves without falling back, but some seeded algorithms are still not optimal.
- The IDA* fallback remains as a safety net for unexpected F2L misses.
- Optimized F2L can be slower than fast mode on some scrambles because it evaluates more candidate lines before choosing a result.
- Solve history currently uses a browser-local anonymous user ID. It is useful for local persistence, but it is not a real login system.

## Requirements

- Java 17
- Maven 3.9+
- Node.js 20+ and npm 10+ for frontend development and builds

## Build And Test

Run the full Java test suite:

```bash
mvn -q -Dmaven.compiler.useIncrementalCompilation=false test
```

Compile the Java project:

```bash
mvn -q clean compile
```

## Run The API Server

Start the Java API server:

```bash
mvn -q compile exec:java -Dexec.mainClass=server.ApiServerMain
```

The server exposes:

- `POST /api/solve`
- `POST /api/solve-jobs`
- `GET /api/solve-jobs/{id}`
- `DELETE /api/solve-jobs/{id}`
- `POST /api/solves`
- `GET /api/solves`
- `GET /api/solves/{id}`
- `DELETE /api/solves/{id}`
- `PUT /api/solves/{id}/solutions/{mode}`
- `GET /api/stats`
- `GET /api/health`
- static frontend files from `frontend/dist` when a frontend build exists

Postgres is optional at startup. If `DATABASE_URL` is set, the server validates the connection and creates the history tables automatically.

The server reads a root-level `.env` file if one exists. A local example looks like this:

```bash
DATABASE_URL='postgresql://USER:PASSWORD@HOST/neondb?sslmode=require&channel_binding=require'
```

Configuration priority is:

1. `-Ddatabase.url=...`
2. real environment variables such as `DATABASE_URL`
3. root `.env`

The default port is `8080`. To change it:

```bash
mvn -q compile exec:java -Dexec.mainClass=server.ApiServerMain -Dserver.port=9090
```

`GET /api/health` reports both API status and database status.

Solve history uses separate solve and solution records:

- `solves` stores the scramble, solve time, penalty, and timestamp
- `solve_solutions` stores generated CFOP output keyed by `(solve_id, mode)`

That lets one timed solve keep separate Fast and Optimized solutions. Each mode can also use a different cross setup and can be replaced independently.

Deleting a solve uses `DELETE /api/solves/{id}?userId=...`. The API checks ownership, cancels active jobs linked to that solve, removes the solve, deletes the saved Fast and Optimized solutions through database cascade, and rebuilds the user statistics before returning `204 No Content`.

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

The Vite app proxies `/api` to `http://localhost:8080`, so run the Java API server at the same time.
The cube visualization uses `cubing.js` and can animate the full solution or individual CFOP stages.

Current frontend behavior:

- uses a responsive three-column timer dashboard
- defaults to a dark theme while still supporting a light theme
- generates a WCA 3x3 scramble on first load and on demand
- computes the solve in the background for the current committed scramble
- keeps the scramble read-only by default, with an explicit edit mode
- supports fixed-face cross solving or color-neutral cross selection
- supports fast greedy F2L or optimized F2L branch search
- includes a timer with inspection behavior similar to common cube timers
- displays the current scramble on a 3D cube
- calculates best time, average of 5, average of 12, solve count, and DNF count from saved attempts
- saves completed attempts to Postgres and advances to the next scramble automatically
- includes cursor-paginated solve history with Fast/Optimized and cross-specific solution review
- supports permanent deletion of owned solves, including the saved Fast and Optimized solutions
- includes an Active Solutions page with live progress, result previews, retry, and termination
- reveals the solution only when requested
- keeps the Timer tab fixed to the viewport and opens animation plus solve details in an internal modal
- supports per-stage playback and playback speed changes
- includes a dark mode toggle

Timer controls:

- `Space`: arm, start inspection, start the solve, or stop the solve
- inspection over 15 seconds applies `+2`
- inspection over 17 seconds applies `DNF`

Build the frontend:

```bash
cd frontend
npm run build
```

## Run SolverMain

Run the command-line solver:

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
Use `CN` to try every cross face and keep the shortest cross solution.

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

Runtime lowercase wide moves are frame-aware moves executed by `OrientedCube`. OLL and PLL databases normalize lowercase wide moves into face-plus-slice turns only for signature seeding. During lookup validation, solvers first execute the original displayed notation so wide moves remain frame-aware; legacy normalized execution is retained as a compatibility fallback for existing seeds.

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

Frame and orientation model:

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

API and server:

- [`src/main/java/api/SolveApiRequest.java`](src/main/java/api/SolveApiRequest.java)
- [`src/main/java/server/CubeHttpServer.java`](src/main/java/server/CubeHttpServer.java)
- [`src/main/java/server/ApiServerMain.java`](src/main/java/server/ApiServerMain.java)
