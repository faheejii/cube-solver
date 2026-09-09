# CFOP Cube Solver

Java 25 and Maven project for solving a 3x3 Rubik's Cube with a CFOP pipeline:

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

The application has two database-backed roles: `user` and `admin`. Normal users can solve, save, and review their own history. Administrators additionally receive the Algorithms tab and may read the canonical algorithm catalog. Role checks are enforced by the backend; hiding the navigation item is only a frontend convenience.

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
- F2L slot selection across all currently feasible pairs, with verified unpair recovery when inserted/blocking pairs prevent every direct route
- OLL solving from seeded sticker-orientation signatures
- PLL solving from seeded last-layer permutation signatures, including final AUF handling
- validation-by-execution after database lookup in F2L, OLL, and PLL
- immutable internal F2L pair traces for greedy solves, including state/orientation snapshots, preserved-slot metadata, case descriptions, and factual selection evidence
- Fast-versus-Optimized route comparison in the Java result model, including stage move differences, continuation rotations, pair-order changes, and deterministic factual explanation codes
- solve API fields exposing F2L pair traces, move ranges, snapshots, orientations, case facts, selection evidence, and route comparisons
- frontend F2L pair cards with deterministic explanation tags and individual pair-level cube playback
- required F2L trace JSONB persistence and nullable Fast-versus-Optimized comparison metadata in saved solutions
- versioned canonical F2L, OLL, and PLL JSON corpora under `src/main/resources/algorithms/` shared by solver loading and the left-panel Algorithms tab
- read-only admin `/api/algorithms` catalog with phase, slot, status, and text filters, derived signatures, copy actions, expandable details, and cube previews
- admin-only Algorithms tab with a compact responsive row list for F2L setup/insert, OLL, and PLL cases
- AUF-only OLL and PLL lookup; all 24 frame variants are indexed at startup
- bounded Fast and Optimized solve queues with cancellation support
- a configurable 5–120 second end-to-end solver deadline with explicit timeout status
- graceful shutdown of HTTP and solver worker executors
- password-based accounts with revocable, server-side sessions
- versioned PostgreSQL schema migrations through Flyway
- pooled PostgreSQL connections and aggregate-based solve statistics
- Java HTTP API and Vite/React frontend
- browser-local Settings for the solver processing deadline, inspection behavior, deep color-neutral optimization, and theme
- application-owned Three.js cube previews and playback driven by one authoritative cubie/sticker model, with WCA/cubing.js-compatible face, wide, slice, and rotation notation, fixed-camera rendering, setup-state reconstruction, sequential animation, and a WebGL fallback
- a development-only, lazy-loaded cubing.js 2D reference with deterministic prefix stepping for comparing setup and stage playback; production renders only the custom Three.js player

Known limitations:

- Some canonical F2L algorithms are not yet optimal; future corpus expansion can target move count and candidate-evaluation efficiency.
- F2L is database-only in production and fails fast with a diagnostic context when a case is missing.
- Temporary regression-only F2L seed cases from the pre-database-fallback transition have been removed; uncovered real-world cases now surface as deliberate diagnostics for corpus review.
- Ordinary color-neutral Fast/Optimized evaluation still uses shortlisted cross baselines and can fail if every route reaches an uncovered F2L case; deep color-neutral optimization evaluates all six cross colors and returns the best valid result found before its two-minute budget expires.
- Production-like Docker/browser verification should be rerun after changes to persisted pair playback, worker assets, static serving, or the Algorithms tab.
- Optimized F2L can be slower than fast mode on some scrambles because it evaluates more candidate lines before choosing a result.

## Requirements

- Java 25
- Maven 3.9+
- Node.js 22.12+ and npm 10+ for frontend development and builds

## Build And Test

Run the full Java test suite:

```bash
mvn -q -Dmaven.compiler.useIncrementalCompilation=false test
```

Run the focused catalog/database/API checks:

```bash
mvn -q -Dtest=F2LCaseCatalogTest,OLLCaseDatabaseTest,PLLCaseDatabaseTest,ApiResponsesTest test
```

Run the focused F2L trace tests:

```bash
mvn -q -Dtest=F2LTraceModelsTest,F2LGreedyTraceTest,F2LOptimizedTraceTest,F2LModeComparisonTest,F2LSolverTest test
```

The API response includes additive F2L fields on the `f2l` stage when a trace is available:

- `traceComplete` and `pairs`
- pair identity, target slot, complete moves, and inclusive move indexes
- before/after cube snapshots and orientations
- preserved slots, structured case facts, selection metrics, and reason codes

Optimized responses also include a top-level `comparison` object with signed differences and factual explanation codes. Fast responses return `comparison: null`.

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

Run the focused Spring MVC contract test for asynchronous solve-job wiring:

```bash
mvn -q -Dtest=server.SpringSolveJobControllerTest test
```

## Run The API Server

Start the Spring Boot API server:

```bash
mvn -q compile exec:java -Dexec.mainClass=server.SpringCubeApplication
```

The Spring MVC server preserves the existing API paths, response shapes, session
cookie contract, and solver entry points. Spring Security owns request
authorization, Spring Data JPA owns migrated authentication/history/statistics
persistence, and Flyway remains the schema authority. Asynchronous solve jobs
retain their existing ownership, queue, cancellation, and save-on-complete
contracts behind the Spring MVC adapter. The original `server.ApiServerMain`
launcher and JDBC repositories remain available for compatibility and existing
focused tests while the migration proceeds.

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
- `GET /api/algorithms`
- `POST /api/auth/register`
- `POST /api/auth/login`
- `POST /api/auth/logout`
- `GET /api/auth/me`
- static frontend files from `frontend/dist` when a frontend build exists

Postgres remains optional for anonymous solver API calls. Accounts, authenticated job ownership, history, and statistics require `DATABASE_URL`. When configured, Flyway applies the initial schema during startup and the server fails clearly if initialization cannot complete. This pre-deployment schema is a fresh-start baseline: reset any prior local database before upgrading to it.

Set `ADMIN_EMAIL` to bootstrap an administrator. The matching account is promoted to `admin` during startup and newly registered accounts with that exact email are also assigned the admin role. All other registrations default to `user`; the role is never accepted from the registration request.

Algorithm resources are organized by CFOP phase under `src/main/resources/algorithms/`:

```text
algorithms/
├── f2l/
│   ├── setup-cases.json
│   └── insert-cases.json
├── oll/
│   └── cases.json
└── pll/
    └── cases.json
```

Each resource is versioned as `{ "version": "1", "cases": [...] }`. JSON is the source of algorithm identity and metadata; startup validation executes the algorithms to derive signatures and checks notation, duplicate names/signatures, preservation, completion, and orientation behavior. Production solving loads canonical cases only. Add a case to the appropriate resource, run the catalog/solver tests, review its derived metadata and cube preview in the admin Algorithms tab, and only then mark it canonical.

The Algorithms tab is visible only to administrators. It supports phase, F2L slot, status, and text filters; copy-to-clipboard; expandable source/signature details; and `Test on cube`. Setup previews use their recorded source setup. OLL and PLL previews use generated inverse algorithms, while insert previews start from a solved cube when no setup is available.

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

`GET /api/health/live` reports process liveness. `GET /api/health/ready` reports database readiness and returns `503` when a configured database is unavailable.

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

The checked-in Compose file uses `admin@admin.com` as a local development bootstrap email. Replace the app service's `ADMIN_EMAIL` value with the intended administrator address before sharing or deploying the stack. The server also accepts `ADMIN_EMAIL` from the environment when run outside this Compose configuration.

For an automatic rebuild/restart loop during development, use Docker Compose Watch:

```bash
docker compose watch
```

The Compose file watches Java sources, frontend sources, dependency manifests, the Dockerfile, and Compose configuration. Changes rebuild and recreate only the `app` service while the PostgreSQL service remains running. Because the current app image is production-oriented, this is automatic rebuild/restart rather than browser HMR; use `npm run dev` for Vite HMR when working on the frontend.

Compose Watch requires Docker Compose 2.22 or newer. Use `docker compose up -d --build` when you need an explicit production-like rebuild or after changing the image/toolchain setup.

Compose exposes PostgreSQL on host port `5433` for host-run integration tests. With the database service running, execute the full database-backed suite with:

```bash
docker compose up -d postgres
export TEST_DATABASE_URL='postgresql://cube_solver:cube_solver@localhost:5433/cube_solver'
mvn -q -Dmaven.compiler.useIncrementalCompilation=false test
```

The liveness endpoint is `GET /api/health/live`, readiness is `GET /api/health/ready`, and process metrics are available at `GET /api/metrics`. CI runs [`scripts/docker-smoke-test.sh`](scripts/docker-smoke-test.sh) against the built Compose stack. The smoke script uses isolated host ports (`18080` for the app and `55433` for Postgres by default) so it can run while the normal development stack is using `8080` and `5433`; override them with `SMOKE_APP_PORT` and `SMOKE_POSTGRES_PORT` if needed.

The smoke workflow also registers a normal user and the configured bootstrap admin, verifies anonymous/user/admin catalog access, creates and polls a solve job, and verifies authenticated history persistence. It cleans up only its isolated Compose project and volume.

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
The cube visualization uses an application-owned Three.js renderer backed by one logical cubie/sticker model. Setup algorithms are applied from solved state before meshes are built, completed moves snap back to the logical model, and facelets are derived from that same state. Backend and frontend notation follows the WCA/cubing.js convention for face, prime, double, wide, `M/E/S`, and `x/y/z` moves. The fixed camera, sequential animation, responsive sizing, and graceful WebGL fallback remain application-owned.

In development builds, solution playback also exposes a lazy-loaded cubing.js 2D reference. Reset/Previous/Next compare an identical setup plus algorithm prefix without autoplay ambiguity, while **Play custom** exercises the normal animation path. The reference panel is excluded from production; cubing.js remains a production dependency only for scramble generation and worker-safe scramble support.

Run the deterministic browser authentication tests with:

```bash
npm run test:e2e
```

To exercise the production bundle and scramble worker locally, build first and run:

```bash
npm run build
PLAYWRIGHT_SERVER=preview npm run test:e2e
```

The Playwright tests mock the API and cover registration, login errors, session restoration, logout, protected history, session expiry, catalog previews, solution/stage playback setup, and deleting a solve from the Recent solves rail.

Current frontend behavior:

- supports registration, login, logout, session restoration, and expired-session handling
- uses a responsive, utilitarian three-column timer dashboard with a compact navigation rail
- uses a joined timer workspace with centered timer/cube content, dynamically fitted single-line scrambles, and a READY solution action
- defaults to a dark theme while still supporting a light theme
- generates a WCA 3x3 scramble on first load and on demand
- computes the solve in the background for the current committed scramble
- keeps the scramble read-only by default, with an explicit edit mode
- supports fixed-face cross solving or color-neutral cross selection
- supports fast greedy F2L or optimized F2L branch search
- optionally evaluates all six cross colors for Optimized + Color Neutral solves; this deep mode is disabled by default and uses a fixed two-minute budget
- includes a timer with inspection behavior similar to common cube timers
- presents solution dialogs as near-full-screen utility inspectors with joined cube/stage panes, bottom playback controls, stage navigation, and a speed dropdown
- includes a Settings view where the processing deadline can be set from 5 to 120 seconds, inspection can be enabled or disabled, and deep color-neutral optimization can be enabled; settings apply to new solve requests and are stored in the current browser
- displays the current scramble on a 3D cube
- calculates best time, average of 5, average of 12, solve count, and DNF count from saved attempts; the same compact statistics summary is available above the History solve list
- saves completed attempts to Postgres and advances to the next scramble automatically
- includes cursor-paginated solve history with Fast/Optimized and cross-specific solution review; Recent solves entries in the right rail open the same saved-solution modal
- supports permanent deletion of owned solves from the History tab or saved-solution modal, including the saved Fast and Optimized solutions
- includes an Active Solutions page with live progress, result previews, retry, and termination
- includes an admin-only Algorithms tab with a compact responsive case list, filters, copy actions, expandable details, and cube previews
- hides admin navigation for normal users while retaining backend authorization for admin catalog access
- reveals the solution only when requested
- keeps the Timer tab fixed to the viewport and opens animation plus solve details in an internal modal
- supports per-stage playback and playback speed changes
- includes a dark mode toggle

Timer controls:

- `Space`: arm, start inspection, start the solve, or stop the solve
- inspection over 15 seconds applies `+2`
- inspection over 17 seconds applies `DNF`
- when inspection is disabled, the first timer start begins the solve immediately without inspection penalties

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

Missing F2L database cases fail immediately with the missing phase, slot, frame, and signature context so they can be seeded and reviewed deliberately. Before reporting a miss, F2L tries each slot-specific verified recovery trigger (`R U R'`, `L' U' L`, `R' U' R`, or `L U L'`) and accepts it only when it preserves the cross/protected pairs and exposes another database route.

Optimized F2L search is bounded by both a production time budget and an internal state limit. For diagnostics, use `-Df2l.optimized.budget-seconds=60`; use a negative value only with `-Df2l.diagnostic=true` for an isolated unlimited-budget run. Search progress reports visited states, duplicate/pruned routes, frontier depth, and lookup/validation timings.

## Notation Support

The parser supports:

- face turns: `R U F D L B`
- double turns: `R2`
- inverse turns: `R'`
- slice moves: `M E S`
- cube rotations: `x y z`
- lowercase wide moves: `r u f d l b`

Runtime lowercase wide moves, slice moves, and cube rotations are frame-aware moves executed natively by `OrientedCube`. OLL and PLL resource notation is preserved through database loading, solving, API responses, and frontend playback, so returned algorithm text retains native moves such as `r'`, `M'`, and `x`.

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

- [`src/main/java/algorithms/AlgorithmCaseCatalog.java`](src/main/java/algorithms/AlgorithmCaseCatalog.java)
- [`src/main/java/algorithms/F2LSetupCaseDatabase.java`](src/main/java/algorithms/F2LSetupCaseDatabase.java)
- [`src/main/java/algorithms/F2LInsertCaseDatabase.java`](src/main/java/algorithms/F2LInsertCaseDatabase.java)
- [`src/main/java/algorithms/OLLCaseDatabase.java`](src/main/java/algorithms/OLLCaseDatabase.java)
- [`src/main/java/algorithms/PLLCaseDatabase.java`](src/main/java/algorithms/PLLCaseDatabase.java)

Solvers:

- [`src/main/java/solver/CrossSolver.java`](src/main/java/solver/CrossSolver.java)
- [`src/main/java/solver/F2LSolver.java`](src/main/java/solver/F2LSolver.java)
- [`src/main/java/solver/F2LOptimizedSearch.java`](src/main/java/solver/F2LOptimizedSearch.java)
- [`src/main/java/solver/F2LStateCodec.java`](src/main/java/solver/F2LStateCodec.java)
- [`src/main/java/solver/OLLSolver.java`](src/main/java/solver/OLLSolver.java)
- [`src/main/java/solver/PLLSolver.java`](src/main/java/solver/PLLSolver.java)
- [`src/main/java/solver/LastLayerSolver.java`](src/main/java/solver/LastLayerSolver.java)
- [`src/main/java/solver/CfopSolveService.java`](src/main/java/solver/CfopSolveService.java)
- [`src/main/java/solver/SolverMain.java`](src/main/java/solver/SolverMain.java)

API and server:

- [`src/main/java/api/SolveApiRequest.java`](src/main/java/api/SolveApiRequest.java)
- [`src/main/java/server/CubeHttpServer.java`](src/main/java/server/CubeHttpServer.java)
- [`src/main/java/server/HttpServerSupport.java`](src/main/java/server/HttpServerSupport.java)
- [`src/main/java/server/AlgorithmCatalogRouteHandler.java`](src/main/java/server/AlgorithmCatalogRouteHandler.java)
- [`src/main/java/server/AuthRouteHandler.java`](src/main/java/server/AuthRouteHandler.java)
- [`src/main/java/server/SolveJobRouteHandler.java`](src/main/java/server/SolveJobRouteHandler.java)
- [`src/main/java/server/SolveRouteHandler.java`](src/main/java/server/SolveRouteHandler.java)
- [`src/main/java/server/SolveHistoryRouteHandler.java`](src/main/java/server/SolveHistoryRouteHandler.java)
- [`src/main/java/server/StatisticsRouteHandler.java`](src/main/java/server/StatisticsRouteHandler.java)
- [`src/main/java/server/ApiServerMain.java`](src/main/java/server/ApiServerMain.java)
- [`src/main/java/server/SpringCubeApplication.java`](src/main/java/server/SpringCubeApplication.java)
- [`src/main/java/server/SpringSecurityConfiguration.java`](src/main/java/server/SpringSecurityConfiguration.java)
- [`src/main/java/database/persistence/`](src/main/java/database/persistence/)

The backend keeps public solver and server facades stable while moving shared responsibilities into focused package-private collaborators. `F2LSolver` owns F2L orchestration and trace flow, while `F2LOptimizedSearch` owns bounded optimized search and `F2LStateCodec` owns compact state encoding and frame execution. `LastLayerSolver` owns OLL/PLL stage execution and status handling. `CubeHttpServer` only wires lifecycle and routes; `HttpServerSupport` centralizes request policy, and each API route has its own handler without changing endpoint paths or response shapes.

Frontend structure:

- [`frontend/src/App.tsx`](frontend/src/App.tsx) is the authenticated dashboard composition root.
- [`frontend/src/hooks/useTimer.ts`](frontend/src/hooks/useTimer.ts) owns timer and inspection state, [`frontend/src/hooks/useHistoryData.ts`](frontend/src/hooks/useHistoryData.ts) owns history/statistics data, and [`frontend/src/hooks/useSolveProcesses.ts`](frontend/src/hooks/useSolveProcesses.ts) owns background solve jobs. [`frontend/src/StatisticsSummary.tsx`](frontend/src/StatisticsSummary.tsx) renders the shared statistics metric grid used by the timer rail and History tab.
- [`frontend/src/styles/`](frontend/src/styles/) contains feature-oriented stylesheet modules imported by [`frontend/src/styles.css`](frontend/src/styles.css); the existing global class names and responsive behavior remain stable.
