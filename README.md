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

Cross, F2L, OLL, and PLL all keep the live `OrientedCube` frame. The OLL and PLL databases expand logical cases into all 24 cube orientations at startup, so last-layer lookup stays frame-aware without a runtime canonicalization bridge. Low-level physical face-turn execution still happens through `MoveApplier.applyMove`.

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
- database-only F2L production solving with fail-fast miss diagnostics
- OLL solving from seeded sticker-orientation signatures
- PLL solving from seeded last-layer permutation signatures, including final AUF handling
- validation-by-execution after database lookup in F2L, OLL, and PLL
- AUF-only OLL and PLL lookup; all 24 frame variants are indexed at startup
- bounded Fast and Optimized solve queues with cancellation support
- a 15-second end-to-end solver deadline with explicit timeout status
- graceful shutdown of HTTP and solver worker executors
- password-based accounts with revocable, server-side sessions
- versioned PostgreSQL schema migrations through Flyway
- pooled PostgreSQL connections and aggregate-based solve statistics
- Java HTTP API and Vite/React frontend
- 3D cube playback in the frontend through `cubing.js`

Known limitations:

- Some seeded F2L algorithms are not yet optimal; future algorithm-set expansion should target move count and candidate-evaluation efficiency.
- F2L is database-only in production and fails fast with a diagnostic context when a case is missing.
- Optimized F2L can be slower than fast mode on some scrambles because it evaluates more candidate lines before choosing a result.
- Legacy anonymous history is preserved during migration but is not automatically claimed by newly registered accounts.

## Requirements

- Java 17
- Maven 3.9+
- Node.js 22.12+ and npm 10+ for frontend development and builds

## Build And Test

Run the full Java test suite:

```bash
mvn -q -Dmaven.compiler.useIncrementalCompilation=false test
```

Run the frontend unit tests and production build:

```bash
cd frontend
npm test
npm run build
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
- `POST /api/auth/register`
- `POST /api/auth/login`
- `POST /api/auth/logout`
- `GET /api/auth/me`
- static frontend files from `frontend/dist` when a frontend build exists

Postgres remains optional for anonymous solver API calls. Accounts, authenticated job ownership, history, and statistics require `DATABASE_URL`. When configured, Flyway applies versioned schema migrations during startup and the server fails clearly if migration cannot complete.

The app supports any PostgreSQL database, not just Neon. You can use:

- a local PostgreSQL server for development
- Neon for hosted deployment
- any other Postgres provider, as long as the connection string matches one of the supported formats

The server reads a root-level `.env` file if one exists. Supported database URL formats are:

```bash
DATABASE_URL='postgresql://USER:PASSWORD@HOST:5432/DB_NAME'
```

```bash
DATABASE_URL='jdbc:postgresql://HOST:5432/DB_NAME'
DATABASE_USER='USER'
DATABASE_PASSWORD='PASSWORD'
```

Examples:

```bash
DATABASE_URL='postgresql://USER:PASSWORD@HOST/neondb?sslmode=require&channel_binding=require'
```

```bash
DATABASE_URL='postgresql://postgres:postgres@localhost:5432/cube_solver'
```

If you want to support additional URL formats such as `postgres://...`, update the parser in [`src/main/java/database/DatabaseConfig.java`](src/main/java/database/DatabaseConfig.java). That class is responsible for accepting the configured URL, converting `postgresql://...` into a JDBC URL, and reading fallback credentials for `jdbc:postgresql://...`.

Configuration priority is:

1. `-Ddatabase.url=...`
2. real environment variables such as `DATABASE_URL`
3. root `.env`

The default port is `8080`. To change it:

```bash
mvn -q compile exec:java -Dexec.mainClass=server.ApiServerMain -Dserver.port=9090
```

`GET /api/health/live` reports process liveness. `GET /api/health/ready` and the compatibility endpoint `GET /api/health` report database readiness and return `503` when a configured database is unavailable.

Useful server tuning properties:

```bash
-Ddatabase.pool.size=4
-Dserver.fast.queue=16
-Dserver.optimized.queue=4
-Dserver.http.queue=128
-Dserver.auth.requestsPerMinute=20
-Dserver.cors.origin=http://localhost:5173
-Dserver.cookie.secure=false
```

Session cookies are secure by default. Set `-Dserver.cookie.secure=false` only for local HTTP development; keep the default for HTTPS production deployments.

JSON request bodies are limited to 64 KiB. A full solve queue returns `429` so the frontend can retry instead of allowing unbounded pending work.

Persisted solve jobs derive ownership from the authenticated session cookie; client-supplied user IDs are not trusted. Anonymous jobs remain available when they do not save history. Jobs that exceed the end-to-end deadline finish with `timed_out`; the frontend reports that state separately from cancellation and ordinary failures.

Solve history uses separate solve and solution records:

- `solves` stores the scramble, solve time, penalty, and timestamp
- `solve_solutions` stores generated CFOP output keyed by `(solve_id, mode)`

That lets one timed solve keep separate Fast and Optimized solutions. Each mode can also use a different cross setup and can be replaced independently.

Deleting a solve uses `DELETE /api/solves/{id}`. The API derives ownership from the active session, cancels linked jobs, removes saved solutions through database cascade, and rebuilds user statistics before returning `204 No Content`.

## Docker

Build and run the complete app with PostgreSQL:

```bash
docker compose up --build
```

The app is available at `http://localhost:8080`. Compose uses a persistent PostgreSQL volume and development-only credentials; replace them and enable secure cookies for production.

The liveness endpoint is `GET /api/health/live`, readiness is `GET /api/health/ready`, and process metrics are available at `GET /api/metrics`. CI runs [`scripts/docker-smoke-test.sh`](scripts/docker-smoke-test.sh) against the built Compose stack.

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

Run the deterministic browser authentication tests with:

```bash
npm run test:e2e
```

The Playwright tests mock the API and cover registration, login errors, session restoration, logout, protected history, and session expiry.

Current frontend behavior:

- supports registration, login, logout, session restoration, and expired-session handling
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

Missing F2L database cases fail immediately with the missing phase, slot, frame, and signature context so they can be seeded and reviewed deliberately.

## Notation Support

The parser supports:

- face turns: `R U F D L B`
- double turns: `R2`
- inverse turns: `R'`
- slice moves: `M E S`
- cube rotations: `x y z`
- lowercase wide moves: `r u f d l b`

Runtime lowercase wide moves are frame-aware moves executed by `OrientedCube`. OLL and PLL seed notation is compiled once into the face-and-slice form used by the cubie engine, so every returned algorithm text describes the same moves the solver validates and executes.

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
