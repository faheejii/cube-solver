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
- browser cube previews in either 3D or a project-owned 2D six-face net

## What the solver does

The solver works on a cubie model, which means it tracks where the pieces are and how they are twisted. For runtime solving, it uses a frame-aware model:

- `OrientedCube` holds the cube together with its current frame
- `CubeOrientation` tracks which face is being treated as which logical side
- `x`, `y`, and `z` rotations change the frame instead of physically mutating the cube model

Cross, F2L, OLL, and PLL all keep the live `OrientedCube` frame. The OLL and PLL databases expand logical cases into all 24 cube orientations at startup, so last-layer lookup stays frame-aware without a runtime canonicalization bridge. Low-level physical face-turn execution still happens through `MoveApplier.applyMove`.

The application has three database-backed roles: `user`, `maintainer`, and `admin`. Everyone can solve, save, and review their own history. Maintainers can use the Algorithms workspace to author experimental revisions and opt into eligible trial algorithms. Admins additionally manage roles and review/approve shared revisions for canonical use. Each maintainer or admin can disable canonical algorithms for their own solves. Backend permissions are authoritative; hidden navigation is only a frontend convenience.

## Algorithm governance

PostgreSQL is the live source of truth for algorithm identities, immutable revisions, workflow status, ownership, and audit events. The checked-in files in `src/main/resources/algorithms/` are the one-time installation seed and offline fixture catalog. A seed importer loads the 201 bundled definitions once and records its version/checksum; restarts do not overwrite database edits or restore deprecated entries.

New UI-authored algorithms begin as private experimental revisions. Their author can opt into testing them in their own solver profile; experimental revisions are not globally deprecated. An author can withdraw a test-only revision back to experimental. Maintainers may explicitly opt into test-only revisions; an admin approves a specific revision before it becomes canonical. Only admins can globally deprecate shared test-only or canonical revisions. Deprecated revisions remain available for saved-solve provenance and inspection but are excluded from new solver contexts. Every solve job captures an immutable database catalog/profile snapshot before entering the queue, so later catalog or role changes affect only new jobs.

The seed-backed public constructors and `SolverMain` remain available to offline tools and tests. They intentionally read the checked-in JSON and do not reflect live database governance changes.

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
- Fast cross solving uses IDA* with a bundled cross-only pruning table; its B-turn restriction is configurable in Settings
- Optimized fixed-cross and color-neutral modes use advanced cross search: per face, try XXCross up to 10 face turns, then XCross up to 8, then fall back to the regular cross solver
- Optimized CN searches all six faces; regular CN builds baselines for its three shortest advanced crosses and optimizes two, while Deep CN evaluates all six completed solutions within its 120-second budget
- a bundled reverse-BFS cross-plus-FR-pair pruning table, rotationally reused for any F2L slot by the advanced cross search
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
- versioned F2L, OLL, and PLL JSON installation seeds under `src/main/resources/algorithms/`, imported once into the live PostgreSQL catalog
- maintainer/admin Algorithms workspace for experimental authoring, trial opt-in, shared testing, admin approval/deprecation, revision history, and cube previews
- admin-only user management for assigning `user`, `maintainer`, and `admin` roles, with bootstrap/last-admin protections
- AUF-only OLL and PLL lookup; all 24 frame variants are indexed at startup
- bounded Fast and Optimized solve queues with cancellation support
- a configurable 5–120 second end-to-end solver deadline with explicit timeout status
- graceful shutdown of HTTP and solver worker executors
- password-based accounts with revocable, server-side sessions
- versioned PostgreSQL schema migrations through Flyway
- pooled PostgreSQL connections and aggregate-based solve statistics
- Java HTTP API and Vite/React frontend
- browser-local Settings for the solver processing deadline, inspection behavior, deep color-neutral optimization, independent B-turn restrictions for Fast and Optimized cross search, theme, and independent 2D/3D modes for the timer cube preview and solution playback
- post-solve penalty controls in the Timer and History/solution views, allowing a saved solve to be changed between no penalty, +2, and DNF with statistics recalculated immediately; the Timer also lets users delete its displayed saved solve through the same confirmation flow as History
- application-owned cube previews and playback driven by one authoritative cubie/sticker model, with WCA/cubing.js-compatible face, wide, slice, and rotation notation; previews can use the interactive Three.js renderer or a lightweight project-owned six-face SVG net

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
- Spring Boot 4.1.1 with native Jackson 3 APIs

## Build And Test

Run the full Java test suite:

```bash
mvn -q -Dmaven.compiler.useIncrementalCompilation=false test
```

Run the focused catalog/database/API checks:

```bash
mvn -q -Dtest=F2LCaseCatalogTest,OLLCaseDatabaseTest,PLLCaseDatabaseTest test
```

Run the focused F2L trace tests:

```bash
mvn -q -Dtest=F2LTraceModelsTest,F2LGreedyTraceTest,F2LOptimizedTraceTest,F2LModeComparisonTest,F2LSolverTest test
```

Regenerate the deterministic combined cross/pair pruning table only when its coordinate or move definition changes. This developer-only BFS uses roughly 400 MiB of heap and took about 23 seconds in the implementation environment; runtime varies by machine. It is not part of application startup or the standard test suite:

```bash
mvn -q -DskipTests package
java -Xmx1g -cp target/classes solver.pruning.GenerateCombinedPruningTable
```

The generator writes the versioned, checksummed asset to `src/main/resources/solver/pruning/cross-fr.bin`. Runtime search loads this bundled table lazily. The configured overall solve deadline and cancellation still apply; the advanced cross search has no separate optimization time budget.

Fast cross IDA* uses a separate cross-only reverse-BFS table (six goal-face orientations, 190,080 states each). It is small and generated offline when the cross coordinate or face-turn semantics change:

```bash
mvn -q -DskipTests package
java -Xmx256m -cp target/classes solver.pruning.GenerateCrossPruningTable
```

The versioned, checksummed asset is `src/main/resources/solver/pruning/cross-only.bin` and is loaded lazily at runtime. BFS uses all 18 face turns for an admissible lower bound. The default Fast policy avoids B turns and tests four Y orientations; the default Optimized policy allows B turns and uses the existing unrestricted orientation. These defaults preserve previous solver behavior. Settings can independently enable B-turn avoidance for each mode.

Cross search uses outer face turns only. The solver's general notation and playback still support wide turns for algorithm data and other cube workflows; cross-search algorithms never contain them.

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

Run the complete production-stack verification locally:

```bash
mvn -q -DskipTests package
./scripts/docker-smoke-test.sh
```

The backend uses Spring Boot 4.1.1, Spring MVC, Spring Security, Spring Data
JPA, and Flyway. JSON binding uses Jackson 3 (`tools.jackson`); no legacy HTTP
runtime or Jackson 2 compatibility layer is supported. The Docker smoke test
builds and exercises the same Spring entrypoint used by production.

The smoke test starts an isolated Compose project with temporary host ports,
checks the Spring health and metrics endpoints, exercises authentication,
authorization, solve jobs, history, statistics, and static frontend serving,
then removes only that isolated project. It does not use or remove the normal
development database volume.

The frontend production build keeps Three.js and cubing.js scramble-worker
assets separate from the initial application bundle. The cubing.js worker is
bundled with esbuild, including its nested worker and split WASM assets; generated
files live under `frontend/public/scramble-worker/` and are rebuilt rather than
committed. Three.js is loaded only when 3D mode is selected for the timer
preview or solution playback and a cube is within 120 pixels of the viewport;
the project-owned 2D net is loaded separately. These modes are selected
independently in Settings. Tab views and solution playback are also loaded on demand.
The cubing.js WASM-support chunks remain separate worker assets and are not
included in Vite's application chunk-size warning.

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
authorization, Spring Data JPA owns authentication/history/statistics persistence,
and Flyway remains the schema authority. Asynchronous solve jobs retain their
existing ownership, queue, cancellation, and save-on-complete contracts behind
the Spring MVC adapter.

`server.SpringCubeApplication` is the only supported backend entrypoint. The
former standalone HTTP server and compatibility launcher are no longer part of
the project.

The server exposes:

- `POST /api/solve-jobs`
- `GET /api/solve-jobs/{id}`
- `DELETE /api/solve-jobs/{id}`
- `POST /api/solves`
- `PATCH /api/solves/{id}/penalty` with `{ "penalty": "none" | "+2" | "dnf" }`
- `GET /api/solves`
- `GET /api/solves/{id}`
- `DELETE /api/solves/{id}`
- `PUT /api/solves/{id}/solutions/{mode}`
- `GET /api/stats`
- `GET /api/algorithms`
- `DELETE /api/algorithms/{id}` for permanent deletion of an eligible private experimental algorithm lineage
- `POST /api/auth/register`
- `POST /api/auth/login`
- `POST /api/auth/logout`
- `GET /api/auth/me`
- static frontend files from `frontend/dist` when a frontend build exists

PostgreSQL is required by the application server for its live algorithm catalog and solver contexts; it also stores accounts, authenticated job ownership, history, and statistics. Set `DATABASE_URL` before starting the server. Flyway applies the initial schema and subsequent forward migrations during startup, and startup/readiness reports catalog or database initialization failures instead of falling back to JSON for production solving. Existing database volumes with a valid Flyway history are upgraded in place; do not delete the volume to apply schema updates. If a database predates Flyway tracking or has manual schema edits, inspect and back it up before upgrading. The public seed-backed solver constructors remain available to offline tools without the live catalog.

Set `ADMIN_EMAIL` to bootstrap an administrator. The matching account is promoted to `admin` during startup and newly registered accounts with that exact email are also assigned the admin role. All other registrations default to `user`; the role is never accepted from the registration request. Admins can promote other accounts to `maintainer` or `admin` in the user-management view; the bootstrap account cannot be demoted and the last admin cannot be removed.

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

Each installation seed is versioned as `{ "version": "1", "cases": [...] }`. On first startup, its 201 definitions receive deterministic catalog identities and immutable database revisions. PostgreSQL then owns live identity, definition, status, approval, and workflow history; subsequent restarts do not overwrite catalog edits. The seed files remain fixtures and the source for explicit future seed updates. Solver-index construction validates each selected snapshot and derives its signatures and orientation variants.

The Algorithms workspace is available to maintainers and admins. It supports phase, F2L slot, workflow scope/status, and text filters; copy-to-clipboard; revision details; authoring and validation; workflow actions; and `Test on cube`. Replaced Experimental or Test-only revisions become Superseded, remain in history, and cannot be enabled for new solves. An owner may permanently delete an entire algorithm lineage only if no revision was shared or approved and no saved solve manifest references it; deletion removes all revisions and workflow history and cannot be undone. Setup previews use their recorded source setup. OLL and PLL previews use generated inverse algorithms, while insert previews start from a solved cube when no setup is available. Maintainers can opt into their own experimental revisions and shared test-only revisions, and can disable canonical algorithms for their own solves. Admins can review submissions, approve revisions as canonical, globally deprecate/restore shared revisions, and manage account roles. Personal preferences use stable catalog/revision UUIDs through `GET`/`PUT /api/admin/solver-profile`; legacy `phase:name` disabled references remain accepted for compatibility. Each solve job captures its selected immutable catalog snapshot before queueing, and newly saved solutions include its revision/profile manifest.

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
mvn -q compile exec:java -Dexec.mainClass=server.SpringCubeApplication -Dserver.port=9090
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

`GET /api/solves` accepts the existing `limit` and opaque `cursor` parameters plus optional `q`, `time`, and `penalty` filters. `q` is a literal, case-insensitive substring of the scramble. `time` matches displayed official times (`S.CC` or `M:SS.CC`), supports `*` wildcards and `DNF`, and a trailing `+` restricts a match to +2 solves; `q` and `time` cannot be combined. `penalty` is `all` (default), `none`, `+2`, or `dnf`. Results remain user-scoped and ordered newest-first by timestamp and ID. Responses include `totalCount` for the active filters alongside `items` and `nextCursor`.

Solve and saved-solution responses include nullable `cross.solvedF2lPairCount`, derived by replaying the scramble and cross algorithm and counting solved F2L slots in the resulting frame. The value is `0` through `4`; the solution view labels these as Cross, XCross, XXCross, XXXCross, and Full F2L. A null value means the cross was not solved or its classification could not be derived. Saved responses derive it at read time, so existing records need no migration.

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

Database-backed tests use an isolated `PostgresTestDatabase` fixture. It creates
a unique schema, applies the Flyway migrations to that schema, and closes its
Hikari datasource during teardown; it does not depend on the production server
runtime or legacy database wrappers.

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

The Vite app proxies `/api` to `http://localhost:8080`, so run the Java API
server at the same time. `npm run dev` watches and rebuilds the separate
scramble worker alongside Vite; `npm run build` cleans and regenerates its
nested worker/WASM assets before building the app.
The cube visualization uses an application-owned Three.js renderer backed by one logical cubie/sticker model. Setup algorithms are applied from solved state before meshes are built, completed moves snap back to the logical model, and facelets are derived from that same state. Backend and frontend notation follows the WCA/cubing.js convention for face, prime, double, wide, `M/E/S`, and `x/y/z` moves. The fixed camera, sequential animation, responsive sizing, and graceful WebGL fallback remain application-owned.

Solution playback uses the application-owned Three.js cube renderer. Its code is
deferred until a preview approaches the viewport. cubing.js is used for
production scramble generation and worker support, not playback rendering.

Run the deterministic browser authentication tests with:

```bash
npm run test:e2e
```

To exercise the production bundle and scramble worker locally, build first and run:

```bash
npm run build
PLAYWRIGHT_SERVER=preview npm run test:e2e
```

The Playwright tests mock the API and cover registration, login errors, session
restoration, logout, protected history, session expiry, catalog previews,
solution/stage playback setup, scramble generation and regeneration, and
deleting a solve after opening it from the scrollable Solves rail. Production
preview also verifies cubing.js loads its nested worker from the generated asset
directory.

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
- includes a Settings view where the processing deadline can be set from 5 to 120 seconds, inspection and deep color-neutral optimization can be toggled, Fast and Optimized B-turn avoidance can be configured independently, and the timer preview and solution playback can each be switched between 3D and a 2D six-face net; preferences are stored in the current browser
- displays the current scramble and solution playback using their independently selected 3D or 2D cube modes
- calculates best time, Ao5, Ao12, mean time, solve count, and DNF count from saved attempts; the global summary is shown above History, while the Statistics modal charts the latest 50 or all solves with metrics scoped to the selected range
- saves completed attempts to Postgres and advances to the next scramble automatically
- includes cursor-paginated solve history in 20-entry pages; the Timer's scrollable Solves rail reuses the same history data and opens the saved-solution modal
- searches History by scramble text or displayed solve time (including simple `*` patterns) through the user-scoped cursor API; the query is URL-restorable and does not change Timer or Statistics datasets
- opens a solve-review modal with its solve context, penalty controls, and saved solution; deleting a solve uses an in-app confirmation that explains the saved-solution consequences
- charts Time, Ao5, or Ao12 in the Statistics modal for either the latest 50 or all solves; chart points open the exact solve and preserve chart state and focus on return
- supports permanent deletion of owned solves from the History tab or saved-solution modal, including the saved Fast and Optimized solutions
- includes an Active Solutions page with live progress, result previews, retry, and termination
- includes a role-aware Algorithms workspace for maintainers/admins and admin-only user management
- captures a database-backed algorithm catalog/profile snapshot for each new solve, while retaining backend authorization for every governance action
- reveals the solution only when requested
- keeps the Timer tab fixed to the viewport and opens animation plus solve details in an internal modal
- supports per-stage playback and playback speed changes
- includes a dark mode toggle

Timer controls:

- `Space`: begin inspection or start the solve; while a solve is running, any key stops it. Timer hotkeys are suspended while a modal dialog is open.
- Mouse clicks do not start or stop the timer
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

Optimized F2L search is bounded by both a production time budget and an internal state limit. Its best-first estimate uses the existing cross-plus-pair pruning table when all configured setup, insert, and recovery moves fit the table's outer-turn metric; unsupported move families disable that estimate for the invocation. For diagnostics, use `-Df2l.optimization.budget-seconds=60`; use a negative value only with `-Df2l.diagnostic=true` for an isolated unlimited-budget run. Search progress reports visited states, duplicate/pruned routes, frontier depth, and lookup/validation timings.

A manually invoked, deterministic benchmark compares Fast F2L, Optimized F2L, and full CFOP move totals on 100 seeded scrambles. It is excluded from normal Surefire discovery. Run it with:

```bash
mvn -q -DskipTests test-compile dependency:build-classpath -Dmdep.outputFile=/tmp/cube-solver-test-classpath.txt
java -cp "target/test-classes:target/classes:$(cat /tmp/cube-solver-test-classpath.txt)" solver.F2LOptimizationBenchmark
```

The benchmark reports F2L search time separately from integrated OLL/PLL continuation evaluation, along with full CFOP move counts, completion, candidate counts, and available search counters. It also runs separate regression fixtures for recovery, a rotated frame, and one or two already-solved F2L pairs. Results are diagnostic and should be compared on an otherwise idle machine; they are not CI performance assertions.

For CPU and allocation profiling, add `-XX:StartFlightRecording=filename=/tmp/f2l-optimization.jfr,settings=profile` to the benchmark's `java` command.

For a quick paired screen of transition breadth, run the same 100 cases with a two-second per-solve cap and each candidate limit in a fresh JVM:

```bash
for limit in 8 16 32; do
  java -Df2l.benchmark.budget-seconds=2 -Df2l.optimized.max-transitions="$limit" \
  -cp "target/test-classes:target/classes:$(cat /tmp/cube-solver-test-classpath.txt)" \
    solver.F2LOptimizationBenchmark > "/tmp/f2l-transitions-$limit.txt"
done
```

The checked-in transition limit remains 8. On the 2026-10-27 seeded 100-case screen, 8 produced a 46.64-move mean full CFOP solution (49.46 at baseline) with 100/100 F2L completions in two repeated runs; 16 produced 47.95 moves and 99/100 completions in both runs, while 32 produced 49.94 moves and 88/100 completions. The broader limits were not promoted.

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
- [`src/main/java/server/SpringCubeApplication.java`](src/main/java/server/SpringCubeApplication.java)
- [`src/main/java/server/web/`](src/main/java/server/web/) contains package-private Spring MVC route handlers, security adapters, request/response policy, JSON serialization, and server wiring.
- [`src/main/java/server/auth/`](src/main/java/server/auth/) contains authentication/session implementation behind `AuthOperations`.
- [`src/main/java/server/solve/`](src/main/java/server/solve/) contains asynchronous solve-job orchestration behind `SolveJobOperations` and its persistence/executor implementation.
- [`src/main/java/database/persistence/`](src/main/java/database/persistence/)

The production backend runs through Spring Boot, whose `server` component scan includes the server subpackages. Spring MVC owns request routing, Spring Security owns cookie authentication, Spring Data JPA owns persistence, and Flyway owns schema startup. Route handlers remain package-private in `server.web`; authentication and solve-job implementations cross package boundaries only through their focused operations contracts. Solver entry points and tightly coupled package-private search collaborators remain in `solver` to preserve their existing public names and visibility; pruning-table tooling stays in `solver.pruning`. `F2LSolver` owns F2L orchestration and trace flow, while `F2LOptimizedSearch` owns bounded optimized search and `F2LStateCodec` owns compact state encoding and frame execution. `LastLayerSolver` owns OLL/PLL stage execution and status handling.

Frontend structure:

- [`frontend/src/App.tsx`](frontend/src/App.tsx) is the authenticated dashboard composition root.
- [`frontend/src/hooks/useTimer.ts`](frontend/src/hooks/useTimer.ts) owns timer and inspection state, [`frontend/src/hooks/useHistoryData.ts`](frontend/src/hooks/useHistoryData.ts) owns history/statistics data, and [`frontend/src/hooks/useSolveProcesses.ts`](frontend/src/hooks/useSolveProcesses.ts) owns background solve jobs. [`frontend/src/StatisticsSummary.tsx`](frontend/src/StatisticsSummary.tsx) renders the shared statistics metric grid used by the timer rail and History tab.
- [`frontend/src/styles/`](frontend/src/styles/) contains feature-oriented stylesheet modules imported by [`frontend/src/styles.css`](frontend/src/styles.css); the existing global class names and responsive behavior remain stable.
