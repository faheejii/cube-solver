package server;

import database.DatabaseHealth;
import database.AuthUser;
import database.SavedSolution;
import database.SolveHistoryDetail;
import database.SolveHistoryEntry;
import database.SolveHistoryPage;
import algorithms.AlgorithmCaseCatalog;
import solver.CfopSolveResult;
import solver.CfopStageResult;
import solver.F2LCaseDescription;
import solver.F2LModeComparison;
import solver.F2LModeSummary;
import solver.F2LPairStep;
import solver.F2LSelectionEvidence;
import solver.F2LSolveTrace;
import statistics.RollingAverage;
import statistics.SolveStatistics;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

final class JsonSupport {
    private static final ObjectMapper JSON = new ObjectMapper();

    private JsonSupport() {
    }

    static String readString(String json, String fieldName) {
        var field = field(json, fieldName);
        return field != null && field.isTextual() ? field.textValue() : null;
    }

    static String readRawField(String json, String fieldName) {
        var field = field(json, fieldName);
        if (field == null || field.isNull()) {
            return null;
        }
        return field.isTextual() ? field.textValue() : field.toString();
    }

    static String solveResultJson(CfopSolveResult result) {
        return "{"
                + "\"scramble\":\"" + escape(result.scramble()) + "\","
                + "\"crossFace\":\"" + escape(result.crossFace()) + "\","
                + "\"f2lMode\":\"" + escape(result.f2lMode()) + "\","
                + "\"f2lSetupCaseCount\":" + result.f2lSetupCaseCount() + ","
                + "\"f2lInsertCaseCount\":" + result.f2lInsertCaseCount() + ","
                + "\"cross\":" + stageJson(result.cross()) + ","
                + "\"f2l\":" + f2lStageJson(result.f2l(), result.f2lTrace()) + ","
                + "\"oll\":" + stageJson(result.oll()) + ","
                + "\"pll\":" + stageJson(result.pll()) + ","
                + "\"solvedF2LSlots\":\"" + escape(result.solvedF2LSlots()) + "\","
                + "\"fullySolved\":" + result.fullySolved() + ","
                + "\"totalMoveCount\":" + result.totalMoveCount() + ","
                + "\"elapsedMs\":" + String.format(java.util.Locale.US, "%.3f", result.elapsedMs()) + ","
                + "\"comparison\":" + modeComparisonJson(result.modeComparison())
                + "}";
    }

    static String errorJson(String message) {
        return "{\"error\":\"" + escape(message) + "\"}";
    }

    static String algorithmCatalogJson(java.util.List<AlgorithmCaseCatalog.CatalogEntry> entries, String version) {
        var builder = new StringBuilder("{\"version\":\"")
                .append(escape(version)).append("\",\"items\":[");
        for (int i = 0; i < entries.size(); i++) {
            if (i > 0) {
                builder.append(',');
            }
            var entry = entries.get(i);
            builder.append('{')
                    .append("\"phase\":\"").append(escape(entry.phase())).append("\",")
                    .append("\"name\":\"").append(escape(entry.name())).append("\",")
                    .append("\"slot\":").append(entry.slot() == null ? "null" : "\"" + entry.slot().name() + "\"").append(',')
                    .append("\"preservedSlots\":[");
            var preservedSlots = entry.preservedSlots() == null ? java.util.List.<cfop.F2LSlot>of() : entry.preservedSlots().slots();
            for (int slotIndex = 0; slotIndex < preservedSlots.size(); slotIndex++) {
                if (slotIndex > 0) {
                    builder.append(',');
                }
                builder.append('\"').append(preservedSlots.get(slotIndex).name()).append('\"');
            }
            builder.append("],\"signature\":").append(catalogSignatureJson(entry.signature())).append(',')
                    .append("\"algorithm\":\"").append(escape(entry.algorithm())).append("\",")
                    .append("\"sourceSetup\":").append(nullableString(entry.sourceSetup())).append(',')
                    .append("\"previewSetup\":").append(nullableString(entry.previewSetup())).append(',')
                    .append("\"status\":\"").append(escape(entry.status())).append("\",")
                    .append("\"notes\":\"").append(escape(entry.notes())).append("\"}");
        }
        return builder.append("]}").toString();
    }

    static String f2lCatalogJson(java.util.List<AlgorithmCaseCatalog.CatalogEntry> entries, String version) {
        return algorithmCatalogJson(entries.stream().filter(entry -> "setup".equals(entry.phase()) || "insert".equals(entry.phase())).toList(), version);
    }

    private static String catalogSignatureJson(Object signature) {
        if (signature instanceof cfop.F2LCaseSignature f2l) {
            return "{\"kind\":\"f2l\",\"cornerPosition\":\"" + f2l.cornerPosition()
                    + "\",\"cornerOrientation\":" + f2l.cornerOrientation()
                    + ",\"edgePosition\":\"" + f2l.edgePosition()
                    + "\",\"edgeOrientation\":" + f2l.edgeOrientation() + "}";
        }
        if (signature instanceof cfop.OLLCaseSignature oll) {
            return "{\"kind\":\"oll\",\"u0\":" + oll.u0() + ",\"u1\":" + oll.u1()
                    + ",\"u2\":" + oll.u2() + ",\"u3\":" + oll.u3() + ",\"u5\":" + oll.u5()
                    + ",\"u6\":" + oll.u6() + ",\"u7\":" + oll.u7() + ",\"u8\":" + oll.u8()
                    + ",\"f0\":" + oll.f0() + ",\"f1\":" + oll.f1() + ",\"f2\":" + oll.f2()
                    + ",\"r0\":" + oll.r0() + ",\"r1\":" + oll.r1() + ",\"r2\":" + oll.r2()
                    + ",\"b0\":" + oll.b0() + ",\"b1\":" + oll.b1() + ",\"b2\":" + oll.b2()
                    + ",\"l0\":" + oll.l0() + ",\"l1\":" + oll.l1() + ",\"l2\":" + oll.l2() + "}";
        }
        if (signature instanceof cfop.PLLCaseSignature pll) {
            return "{\"kind\":\"pll\",\"urfPiece\":\"" + pll.urfPiece()
                    + "\",\"uflPiece\":\"" + pll.uflPiece() + "\",\"ulbPiece\":\"" + pll.ulbPiece()
                    + "\",\"ubrPiece\":\"" + pll.ubrPiece() + "\",\"urPiece\":\"" + pll.urPiece()
                    + "\",\"ufPiece\":\"" + pll.ufPiece() + "\",\"ulPiece\":\"" + pll.ulPiece()
                    + "\",\"ubPiece\":\"" + pll.ubPiece() + "\"}";
        }
        return "null";
    }

    static String errorMessage(String json) {
        try {
            var root = JSON.readTree(json);
            var error = root == null ? null : root.get("error");
            return error != null && error.isTextual() ? error.textValue() : null;
        } catch (java.io.IOException exception) {
            return null;
        }
    }

    static String authUserJson(AuthUser user) {
        return "{"
                + "\"id\":\"" + escape(user.externalId()) + "\"," 
                + "\"email\":\"" + escape(user.email()) + "\"," 
                + "\"displayName\":" + nullableString(user.displayName())
                + ",\"role\":\"" + escape(user.role()) + "\""
                + "}";
    }

    static Integer readInteger(String json, String fieldName) {
        var field = field(json, fieldName);
        return field != null && field.canConvertToInt() ? field.intValue() : null;
    }

    static Long readLong(String json, String fieldName) {
        var field = field(json, fieldName);
        return field != null && field.canConvertToLong() ? field.longValue() : null;
    }

    static int requireInteger(String json, String fieldName) {
        var value = readInteger(json, fieldName);
        if (value == null) {
            throw new IllegalArgumentException(fieldName + " cannot be null");
        }
        return value;
    }

    static Double readDouble(String json, String fieldName) {
        var field = field(json, fieldName);
        return field != null && field.isNumber() ? field.doubleValue() : null;
    }

    static double requireDouble(String json, String fieldName) {
        var value = readDouble(json, fieldName);
        if (value == null) {
            throw new IllegalArgumentException(fieldName + " cannot be null");
        }
        return value;
    }

    static boolean readBoolean(String json, String fieldName) {
        var field = field(json, fieldName);
        return field != null && field.isBoolean() && field.booleanValue();
    }

    static String healthJson(DatabaseHealth health) {
        var status = "error".equals(health.status()) ? "error" : "ok";
        return "{"
                + "\"status\":\"" + status + "\","
                + "\"database\":{"
                + "\"status\":\"" + escape(health.status()) + "\","
                + "\"message\":\"" + escape(health.message()) + "\""
                + "}"
                + "}";
    }

    static String livenessJson() {
        return "{\"status\":\"ok\"}";
    }

    static String solveHistoryEntryJson(SolveHistoryEntry entry) {
        return "{"
                + "\"id\":" + entry.id() + ","
                + "\"clientAttemptId\":\"" + escape(entry.clientAttemptId()) + "\","
                + "\"scramble\":\"" + escape(entry.scramble()) + "\","
                + "\"crossFaceRequested\":\"" + escape(entry.crossFaceRequested()) + "\","
                + "\"timerMs\":" + nullableInteger(entry.timerMs()) + ","
                + "\"officialMs\":" + nullableInteger(entry.officialMs()) + ","
                + "\"penalty\":\"" + escape(entry.penalty()) + "\","
                + "\"dnf\":" + entry.dnf() + ","
                + "\"fastCrossFaceRequested\":" + nullableString(entry.fastCrossFaceRequested()) + ","
                + "\"optimizedCrossFaceRequested\":" + nullableString(entry.optimizedCrossFaceRequested()) + ","
                + "\"createdAt\":\"" + escape(entry.createdAt().toString()) + "\""
                + "}";
    }

    static String solveHistoryPageJson(SolveHistoryPage page) {
        var builder = new StringBuilder();
        builder.append("{\"items\":[");
        for (int i = 0; i < page.items().size(); i++) {
            if (i > 0) {
                builder.append(',');
            }
            builder.append(solveHistoryEntryJson(page.items().get(i)));
        }
        builder.append("],\"nextCursor\":")
                .append(nullableString(page.nextCursor()))
                .append('}');
        return builder.toString();
    }

    static String solveStatisticsJson(SolveStatistics statistics) {
        var builder = new StringBuilder();
        builder.append('{')
                .append("\"solveCount\":").append(statistics.solveCount()).append(',')
                .append("\"dnfCount\":").append(statistics.dnfCount()).append(',')
                .append("\"bestMs\":").append(nullableInteger(statistics.bestMs())).append(',')
                .append("\"averageMs\":").append(nullableInteger(statistics.averageMs())).append(',')
                .append("\"ao5\":").append(rollingAverageJson(statistics.ao5())).append(',')
                .append("\"ao12\":").append(rollingAverageJson(statistics.ao12())).append(',')
                .append("\"recentSolves\":[");
        for (int i = 0; i < statistics.recentSolves().size(); i++) {
            if (i > 0) {
                builder.append(',');
            }
            builder.append(solveHistoryEntryJson(statistics.recentSolves().get(i)));
        }
        return builder.append("]}").toString();
    }

    static String solveJobJson(SolveJobManager.JobSnapshot job) {
        return "{"
                + "\"id\":\"" + escape(job.id()) + "\","
                + "\"status\":\"" + escape(job.status()) + "\","
                + "\"statesExplored\":" + job.statesExplored() + ","
                + "\"statesPruned\":" + job.statesPruned() + ","
                + "\"duplicateStates\":" + job.duplicateStates() + ","
                + "\"bestMoves\":" + job.bestMoves() + ","
                + "\"completedCandidates\":" + job.completedCandidates() + ","
                + "\"candidatesEvaluated\":" + job.candidatesEvaluated() + ","
                + "\"bestTotalMoves\":" + job.bestTotalMoves() + ","
                + "\"phase\":\"" + escape(job.phase()) + "\","
                + "\"currentCrossFace\":\"" + escape(job.currentCrossFace()) + "\","
                + "\"completedCrosses\":" + job.completedCrosses() + ","
                + "\"totalCrosses\":" + job.totalCrosses() + ","
                + "\"optimizationCandidate\":" + job.optimizationCandidate() + ","
                + "\"totalOptimizationCandidates\":" + job.totalOptimizationCandidates() + ","
                + "\"optimizationBudgetExpired\":" + job.optimizationBudgetExpired() + ","
                + "\"result\":" + (job.result() == null ? "null" : solveResultJson(job.result())) + ","
                + "\"error\":" + nullableString(job.error())
                + "}";
    }

    static String solveHistoryDetailJson(SolveHistoryDetail detail) {
        var builder = new StringBuilder();
        builder.append('{')
                .append("\"id\":").append(detail.id()).append(',')
                .append("\"clientAttemptId\":\"").append(escape(detail.clientAttemptId())).append("\",")
                .append("\"scramble\":\"").append(escape(detail.scramble())).append("\",")
                .append("\"crossFaceRequested\":\"").append(escape(detail.crossFaceRequested())).append("\",")
                .append("\"timerMs\":").append(nullableInteger(detail.timerMs())).append(',')
                .append("\"officialMs\":").append(nullableInteger(detail.officialMs())).append(',')
                .append("\"penalty\":\"").append(escape(detail.penalty())).append("\",")
                .append("\"dnf\":").append(detail.dnf()).append(',')
                .append("\"createdAt\":\"").append(escape(detail.createdAt().toString())).append("\",")
                .append("\"solutions\":[");
        for (int i = 0; i < detail.solutions().size(); i++) {
            if (i > 0) {
                builder.append(',');
            }
            builder.append(savedSolutionJson(detail.solutions().get(i)));
        }
        return builder.append("]}").toString();
    }

    static String savedSolutionJson(SavedSolution solution) {
        return "{"
                + "\"mode\":\"" + escape(solution.mode()) + "\","
                + "\"crossFaceRequested\":\"" + escape(solution.crossFaceRequested()) + "\","
                + "\"crossFace\":\"" + escape(solution.crossFaceChosen()) + "\","
                + "\"f2lMode\":\"" + escape(solution.mode()) + "\","
                + "\"f2lSetupCaseCount\":" + solution.f2lSetupCaseCount() + ","
                + "\"f2lInsertCaseCount\":" + solution.f2lInsertCaseCount() + ","
                + "\"cross\":" + stageJson(solution.cross()) + ","
                + "\"f2l\":" + f2lStageJson(solution.f2l(), solution.f2lTraceJson()) + ","
                + "\"oll\":" + stageJson(solution.oll()) + ","
                + "\"pll\":" + stageJson(solution.pll()) + ","
                + "\"solvedF2LSlots\":\"" + escape(solution.solvedF2LSlots()) + "\","
                + "\"fullySolved\":" + solution.fullySolved() + ","
                + "\"totalMoveCount\":" + solution.totalMoves() + ","
                + "\"elapsedMs\":" + String.format(java.util.Locale.US, "%.3f", solution.elapsedMs()) + ","
                + "\"comparison\":" + (isJsonObject(solution.comparisonJson()) ? solution.comparisonJson() : "null") + ","
                + "\"solverVersion\":" + nullableString(solution.solverVersion()) + ","
                + "\"updatedAt\":\"" + escape(solution.updatedAt().toString()) + "\""
                + "}";
    }

    private static String stageJson(CfopStageResult stage) {
        return "{"
                + "\"name\":\"" + escape(stage.name()) + "\","
                + "\"algorithm\":\"" + escape(stage.algorithm()) + "\","
                + "\"moveCount\":" + stage.moveCount() + ","
                + "\"solved\":" + stage.solved() + ","
                + "\"status\":\"" + escape(stage.status()) + "\""
                + "}";
    }

    static String f2lStageJson(CfopStageResult stage, String metadataJson) {
        var metadata = isJsonObject(metadataJson)
                ? metadataJson
                : "{\"traceComplete\":false,\"pairs\":[]}";
        return appendMetadata(stageJson(stage), metadata);
    }

    private static String f2lStageJson(CfopStageResult stage, F2LSolveTrace trace) {
        return appendMetadata(stageJson(stage), f2lTraceJson(stage, trace));
    }

    static String f2lTraceJson(CfopStageResult stage, F2LSolveTrace trace) {
        if (trace == null) {
            return "{\"traceComplete\":false,\"pairs\":[]}";
        }
        var builder = new StringBuilder("{");
        builder.append("\"traceComplete\":").append(trace.hasCompletePairTrace())
                .append(",\"pairAlgorithmMatchesStage\":")
                .append(trace.algorithmMatchesPairSteps() && trace.algorithm().toString().equals(stage.algorithm()))
                .append(",\"pairs\":[");
        int moveIndex = 0;
        for (int i = 0; i < trace.pairSteps().size(); i++) {
            if (i > 0) {
                builder.append(',');
            }
            var pair = trace.pairSteps().get(i);
            builder.append(pairJson(pair, moveIndex));
            moveIndex += pair.completeMoves().size();
        }
        return builder.append("]}").toString();
    }

    private static String appendMetadata(String stageJson, String metadataJson) {
        var builder = new StringBuilder(stageJson);
        builder.setLength(builder.length() - 1);
        var metadata = metadataJson.trim();
        builder.append(',').append(metadata, 1, metadata.length() - 1);
        return builder.append('}').toString();
    }

    private static boolean isJsonObject(String value) {
        if (value == null || value.isBlank()) {
            return false;
        }
        try {
            var node = JSON.readTree(value);
            return node != null && node.isObject();
        } catch (java.io.IOException exception) {
            return false;
        }
    }

    private static String pairJson(F2LPairStep pair, int startMoveIndex) {
        var completeMoves = movesJson(pair.completeMoves());
        var endMoveIndex = startMoveIndex + pair.completeMoves().size() - 1;
        return "{"
                + "\"order\":" + pair.order() + ","
                + "\"corner\":\"" + pair.corner().name() + "\","
                + "\"edge\":\"" + pair.edge().name() + "\","
                + "\"targetSlot\":\"" + pair.targetSlot().name() + "\","
                + "\"algorithm\":\"" + escape(pair.completeAlgorithm().toString()) + "\","
                + "\"moveCount\":" + pair.moveCount() + ","
                + "\"completeMoves\":" + completeMoves + ","
                + "\"startMoveIndex\":" + startMoveIndex + ","
                + "\"endMoveIndex\":" + endMoveIndex + ","
                + "\"moveBreakdownAvailable\":" + pair.moveBreakdownAvailable() + ","
                + "\"setupAlgorithm\":" + optionalAlgorithm(pair.setupMoves(), pair.moveBreakdownAvailable()) + ","
                + "\"pairingAlgorithm\":" + optionalAlgorithm(pair.pairingMoves(), pair.moveBreakdownAvailable()) + ","
                + "\"insertionAlgorithm\":" + optionalAlgorithm(pair.insertionMoves(), pair.moveBreakdownAvailable()) + ","
                + "\"stateBefore\":" + stateJson(pair.stateBefore()) + ","
                + "\"orientationBefore\":" + orientationJson(pair.orientationBefore()) + ","
                + "\"stateAfter\":" + stateJson(pair.stateAfter()) + ","
                + "\"orientationAfter\":" + orientationJson(pair.orientationAfter()) + ","
                + "\"preservedSlots\":" + stringArrayJson(pair.preservedSlots().slots()) + ","
                + "\"case\":" + caseDescriptionJson(pair.caseDescription()) + ","
                + "\"selectionEvidence\":" + selectionEvidenceJson(pair.selectionEvidence())
                + "}";
    }

    static String modeComparisonJson(F2LModeComparison comparison) {
        if (comparison == null) {
            return "null";
        }
        return "{"
                + "\"fast\":" + modeSummaryJson(comparison.fast()) + ","
                + "\"optimized\":" + modeSummaryJson(comparison.optimized()) + ","
                + "\"f2lMoveDifference\":" + comparison.f2lMoveDifference() + ","
                + "\"ollMoveDifference\":" + comparison.ollMoveDifference() + ","
                + "\"pllMoveDifference\":" + comparison.pllMoveDifference() + ","
                + "\"totalMoveDifference\":" + comparison.totalMoveDifference() + ","
                + "\"rotationDifference\":" + comparison.rotationDifference() + ","
                + "\"pairOrderChanged\":" + comparison.pairOrderChanged() + ","
                + "\"explanationCodes\":" + enumArrayJson(comparison.explanationCodes())
                + "}";
    }

    private static String modeSummaryJson(F2LModeSummary summary) {
        return "{"
                + "\"crossFace\":\"" + escape(summary.crossFace()) + "\","
                + "\"f2lMoves\":" + summary.f2lMoves() + ","
                + "\"ollMoves\":" + summary.ollMoves() + ","
                + "\"pllMoves\":" + summary.pllMoves() + ","
                + "\"totalMoves\":" + summary.totalMoves() + ","
                + "\"rotationCount\":" + summary.rotationCount() + ","
                + "\"pairOrder\":" + enumArrayJson(summary.pairOrder()) + ","
                + "\"pairTraceComplete\":" + summary.pairTraceComplete()
                + "}";
    }

    private static String caseDescriptionJson(F2LCaseDescription description) {
        var signature = description.signature();
        return "{"
                + "\"cornerPosition\":\"" + signature.cornerPosition().name() + "\","
                + "\"cornerOrientation\":" + signature.cornerOrientation() + ","
                + "\"edgePosition\":\"" + signature.edgePosition().name() + "\","
                + "\"edgeOrientation\":" + signature.edgeOrientation() + ","
                + "\"initiallyConnected\":" + description.initiallyConnected() + ","
                + "\"cornerInTargetSlot\":" + description.cornerInTargetSlot() + ","
                + "\"edgeInMiddleLayer\":" + description.edgeInMiddleLayer()
                + "}";
    }

    private static String selectionEvidenceJson(F2LSelectionEvidence evidence) {
        return "{"
                + "\"pairMoveCount\":" + evidence.pairMoveCount() + ","
                + "\"remainingF2LMoveCount\":" + evidence.remainingF2LMoveCount() + ","
                + "\"totalRouteMoveCount\":" + evidence.totalRouteMoveCount() + ","
                + "\"rotationCount\":" + evidence.rotationCount() + ","
                + "\"preservesSolvedSlots\":" + evidence.preservesSolvedSlots() + ","
                + "\"pairWasAlreadyConnected\":" + evidence.pairWasAlreadyConnected() + ","
                + "\"shortestAvailablePair\":" + evidence.shortestAvailablePair() + ","
                + "\"selectedForGlobalRoute\":" + evidence.selectedForGlobalRoute() + ","
                + "\"reasonCodes\":" + enumArrayJson(evidence.reasonCodes())
                + "}";
    }

    private static String stateJson(cube.CubeStateSnapshot state) {
        return "{"
                + "\"cornerPerm\":" + byteArrayJson(state.cornerPerm()) + ","
                + "\"cornerOri\":" + byteArrayJson(state.cornerOri()) + ","
                + "\"edgePerm\":" + byteArrayJson(state.edgePerm()) + ","
                + "\"edgeOri\":" + byteArrayJson(state.edgeOri())
                + "}";
    }

    private static String orientationJson(cube.CubeOrientationKey orientation) {
        return "{"
                + "\"up\":\"" + orientation.up().name() + "\","
                + "\"right\":\"" + orientation.right().name() + "\","
                + "\"front\":\"" + orientation.front().name() + "\""
                + "}";
    }

    private static String optionalAlgorithm(java.util.List<cube.Move> moves, boolean available) {
        return available ? "\"" + escape(cube.Algorithm.fromMoves(moves).toString()) + "\"" : "null";
    }

    private static String movesJson(java.util.List<cube.Move> moves) {
        var builder = new StringBuilder("[");
        for (int i = 0; i < moves.size(); i++) {
            if (i > 0) {
                builder.append(',');
            }
            builder.append('"').append(escape(moves.get(i).getNotation())).append('"');
        }
        return builder.append(']').toString();
    }

    private static String byteArrayJson(byte[] values) {
        var builder = new StringBuilder("[");
        for (int i = 0; i < values.length; i++) {
            if (i > 0) {
                builder.append(',');
            }
            builder.append(values[i]);
        }
        return builder.append(']').toString();
    }

    private static String stringArrayJson(java.util.List<?> values) {
        var builder = new StringBuilder("[");
        for (int i = 0; i < values.size(); i++) {
            if (i > 0) {
                builder.append(',');
            }
            builder.append('"').append(escape(values.get(i).toString())).append('"');
        }
        return builder.append(']').toString();
    }

    private static String enumArrayJson(java.util.List<?> values) {
        return stringArrayJson(values);
    }

    private static JsonNode field(String json, String fieldName) {
        try {
            var root = JSON.readTree(json);
            if (root == null || !root.isObject()) {
                throw new IllegalArgumentException("Request body must be a JSON object");
            }
            return root.get(fieldName);
        } catch (java.io.IOException exception) {
            throw new IllegalArgumentException("Invalid JSON request body", exception);
        }
    }

    private static String escape(String value) {
        if (value == null) {
            return "";
        }
        var escaped = new StringBuilder(value.length());
        for (var character : value.toCharArray()) {
            switch (character) {
                case '\\' -> escaped.append("\\\\");
                case '"' -> escaped.append("\\\"");
                case '\n' -> escaped.append("\\n");
                case '\r' -> escaped.append("\\r");
                case '\t' -> escaped.append("\\t");
                case '\b' -> escaped.append("\\b");
                case '\f' -> escaped.append("\\f");
                default -> {
                    if (character < 0x20) {
                        escaped.append(String.format("\\u%04x", (int) character));
                    } else {
                        escaped.append(character);
                    }
                }
            }
        }
        return escaped.toString();
    }

    private static String nullableInteger(Integer value) {
        return value == null ? "null" : value.toString();
    }

    private static String nullableString(String value) {
        return value == null ? "null" : "\"" + escape(value) + "\"";
    }

    private static String rollingAverageJson(RollingAverage average) {
        return "{"
                + "\"status\":\"" + escape(average.status()) + "\","
                + "\"valueMs\":" + nullableInteger(average.valueMs())
                + "}";
    }
}
