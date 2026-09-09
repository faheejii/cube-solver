package server;

import api.CreateSolveAttemptRequest;
import api.SaveSolutionApiRequest;
import database.CreateSolveAttemptCommand;
import database.DatabaseManager;
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
    private final DatabaseManager databaseManager;
    private final SpringHistoryPersistenceService history;
    private final SolveJobManager solveJobManager;

    SpringHistoryController(
            DatabaseManager databaseManager,
            SpringHistoryPersistenceService history,
            SolveJobManager solveJobManager
    ) {
        this.databaseManager = databaseManager;
        this.history = history;
        this.solveJobManager = solveJobManager;
    }

    @PostMapping
    ResponseEntity<String> createAttempt(@RequestBody String body) throws Exception {
        ensureDatabase();
        var user = SpringRequestSupport.requireUser();
        var json = SpringRequestSupport.requireJson(body);
        var request = new CreateSolveAttemptRequest(
                JsonSupport.readString(json, "clientAttemptId"),
                JsonSupport.readString(json, "scramble"),
                JsonSupport.readString(json, "crossFaceRequested"),
                JsonSupport.readInteger(json, "timerMs"),
                JsonSupport.readString(json, "penalty"),
                JsonSupport.readInteger(json, "officialMs"),
                JsonSupport.readBoolean(json, "dnf")
        );
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
    ResponseEntity<String> detail(@PathVariable String solveId) throws Exception {
        ensureDatabase();
        var user = SpringRequestSupport.requireUser();
        return SpringRequestSupport.json(200, JsonSupport.solveHistoryDetailJson(
                history.findDetail(user.externalId(), parseSolveId(solveId))));
    }

    @DeleteMapping("/{solveId}")
    ResponseEntity<Void> delete(@PathVariable String solveId) throws Exception {
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
            @PathVariable String solveId,
            @PathVariable String mode,
            @RequestBody String body
    ) throws Exception {
        ensureDatabase();
        var user = SpringRequestSupport.requireUser();
        if (!mode.equals("greedy") && !mode.equals("optimized")) {
            throw new IllegalArgumentException("Invalid F2L mode: " + mode);
        }
        var request = readSolutionRequest(SpringRequestSupport.requireJson(body));
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
                DatabaseManager.SOLVER_VERSION,
                request.f2lTraceJson(),
                request.comparisonJson()
        ));
        return SpringRequestSupport.json(200, JsonSupport.savedSolutionJson(saved));
    }

    private void ensureDatabase() {
        if (!databaseManager.isConfigured()) {
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

    private static SaveSolutionApiRequest readSolutionRequest(String body) {
        return new SaveSolutionApiRequest(
                JsonSupport.readString(body, "crossFaceRequested"),
                JsonSupport.readString(body, "crossFaceChosen"),
                JsonSupport.readString(body, "f2lMode"),
                JsonSupport.readInteger(body, "f2lSetupCaseCount"),
                JsonSupport.readInteger(body, "f2lInsertCaseCount"),
                JsonSupport.readString(body, "solvedF2LSlots"),
                JsonSupport.readInteger(body, "totalMoves"),
                JsonSupport.readBoolean(body, "fullySolved"),
                JsonSupport.readDouble(body, "solveElapsedMs"),
                JsonSupport.readString(body, "crossAlgorithm"),
                JsonSupport.readInteger(body, "crossMoves"),
                JsonSupport.readBoolean(body, "crossSolved"),
                JsonSupport.readString(body, "crossStatus"),
                JsonSupport.readString(body, "f2lAlgorithm"),
                JsonSupport.readInteger(body, "f2lMoves"),
                JsonSupport.readBoolean(body, "f2lSolved"),
                JsonSupport.readString(body, "f2lStatus"),
                JsonSupport.readString(body, "ollAlgorithm"),
                JsonSupport.readInteger(body, "ollMoves"),
                JsonSupport.readBoolean(body, "ollSolved"),
                JsonSupport.readString(body, "ollStatus"),
                JsonSupport.readString(body, "pllAlgorithm"),
                JsonSupport.readInteger(body, "pllMoves"),
                JsonSupport.readBoolean(body, "pllSolved"),
                JsonSupport.readString(body, "pllStatus"),
                JsonSupport.readRawField(body, "f2lTraceJson"),
                JsonSupport.readRawField(body, "comparisonJson")
        );
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
