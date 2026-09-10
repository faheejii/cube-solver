package server;

import api.CreateSolveAttemptRequest;
import api.SaveSolutionApiRequest;
import api.SpringSaveSolutionRequest;
import database.CreateSolveAttemptCommand;
import database.SaveSolutionCommand;
import database.persistence.entity.SpringHistoryPersistenceService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** MVC adapter for authenticated solve history and saved solution routes. */
@RestController
@RequestMapping("/api/solves")
final class SpringHistoryController {
    private final SpringDatabaseHealth databaseHealth;
    private final SpringHistoryPersistenceService history;
    private final SolveJobManager solveJobManager;

    SpringHistoryController(
            SpringDatabaseHealth databaseHealth,
            SpringHistoryPersistenceService history,
            SolveJobManager solveJobManager
    ) {
        this.databaseHealth = databaseHealth;
        this.history = history;
        this.solveJobManager = solveJobManager;
    }

    @PostMapping
    ResponseEntity<String> createAttempt(@RequestBody CreateSolveAttemptRequest request) throws Exception {
        ensureDatabase();
        var user = SpringRequestSupport.requireUser();
        var saved = history.createAttempt(new CreateSolveAttemptCommand(
                user.externalId(),
                request.clientAttemptId(),
                request.scramble(),
                request.crossFaceRequested(),
                request.timerMs(),
                request.penalty(),
                request.officialMs(),
                request.dnf()
        ));
        return SpringRequestSupport.json(201, JsonSupport.solveHistoryEntryJson(saved));
    }

    @GetMapping
    ResponseEntity<String> list(
            @RequestParam(value = "limit", required = false) String limitValue,
            @RequestParam(value = "cursor", required = false) String cursorValue
    ) throws Exception {
        ensureDatabase();
        var user = SpringRequestSupport.requireUser();
        var limit = parseLimit(limitValue);
        var cursor = database.HistoryCursor.parse(cursorValue);
        return SpringRequestSupport.json(200, JsonSupport.solveHistoryPageJson(
                history.listPage(user.externalId(), limit, cursor)));
    }

    @GetMapping("/{solveId}")
    ResponseEntity<String> detail(@PathVariable("solveId") String solveId) throws Exception {
        ensureDatabase();
        var user = SpringRequestSupport.requireUser();
        return SpringRequestSupport.json(200, JsonSupport.solveHistoryDetailJson(
                history.findDetail(user.externalId(), parseSolveId(solveId))));
    }

    @DeleteMapping("/{solveId}")
    ResponseEntity<Void> delete(@PathVariable("solveId") String solveId) throws Exception {
        ensureDatabase();
        var user = SpringRequestSupport.requireUser();
        var id = parseSolveId(solveId);
        history.findDetail(user.externalId(), id);
        solveJobManager.cancelLinkedJobs(user.externalId(), id);
        history.deleteSolve(user.externalId(), id);
        return SpringRequestSupport.noContent();
    }

    @PutMapping("/{solveId}/solutions/{mode}")
    ResponseEntity<String> upsertSolution(
            @PathVariable("solveId") String solveId,
            @PathVariable("mode") String mode,
            @RequestBody SpringSaveSolutionRequest body
    ) throws Exception {
        ensureDatabase();
        var user = SpringRequestSupport.requireUser();
        if (!mode.equals("greedy") && !mode.equals("optimized")) {
            throw new IllegalArgumentException("Invalid F2L mode: " + mode);
        }
        var request = body.toApiRequest();
        if (!mode.equals(request.f2lMode())) {
            throw new IllegalArgumentException("Solution mode does not match request path");
        }
        var solution = combinedSolution(request);
        var saved = history.upsertSolution(new SaveSolutionCommand(
                user.externalId(),
                parseSolveId(solveId),
                mode,
                request.crossFaceRequested(),
                request.crossFaceChosen(),
                solution,
                solution,
                request.f2lSetupCaseCount(),
                request.f2lInsertCaseCount(),
                request.solvedF2LSlots(),
                request.totalMoves(),
                request.fullySolved(),
                request.solveElapsedMs(),
                request.crossAlgorithm(),
                request.crossMoves(),
                request.crossSolved(),
                request.crossStatus(),
                request.f2lAlgorithm(),
                request.f2lMoves(),
                request.f2lSolved(),
                request.f2lStatus(),
                request.ollAlgorithm(),
                request.ollMoves(),
                request.ollSolved(),
                request.ollStatus(),
                request.pllAlgorithm(),
                request.pllMoves(),
                request.pllSolved(),
                request.pllStatus(),
                SolverVersion.CURRENT,
                request.f2lTraceJson(),
                request.comparisonJson()
        ));
        return SpringRequestSupport.json(200, JsonSupport.savedSolutionJson(saved));
    }

    private void ensureDatabase() {
        if (!databaseHealth.isConfigured()) {
            throw new SpringDatabaseUnavailableException();
        }
    }

    private static int parseLimit(String value) {
        if (value == null || value.isBlank()) {
            return 20;
        }
        return Math.max(1, Math.min(Integer.parseInt(value.trim()), 100));
    }

    private static long parseSolveId(String value) {
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("Invalid solve ID");
        }
    }

    private static String combinedSolution(SaveSolutionApiRequest request) {
        var builder = new StringBuilder();
        appendAlgorithm(builder, request.crossAlgorithm());
        appendAlgorithm(builder, request.f2lAlgorithm());
        appendAlgorithm(builder, request.ollAlgorithm());
        appendAlgorithm(builder, request.pllAlgorithm());
        return builder.toString().trim();
    }

    private static void appendAlgorithm(StringBuilder builder, String algorithm) {
        if (algorithm == null || algorithm.isBlank()) {
            return;
        }
        if (builder.length() > 0) {
            builder.append(' ');
        }
        builder.append(algorithm.trim());
    }
}
