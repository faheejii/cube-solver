package server;

import database.DatabaseHealth;
import database.SavedSolution;
import database.SolveHistoryDetail;
import database.SolveHistoryEntry;
import database.SolveHistoryPage;
import solver.CfopSolveResult;
import solver.CfopStageResult;
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

    static String solveResultJson(CfopSolveResult result) {
        return "{"
                + "\"scramble\":\"" + escape(result.scramble()) + "\","
                + "\"crossFace\":\"" + escape(result.crossFace()) + "\","
                + "\"f2lMode\":\"" + escape(result.f2lMode()) + "\","
                + "\"f2lSetupCaseCount\":" + result.f2lSetupCaseCount() + ","
                + "\"f2lInsertCaseCount\":" + result.f2lInsertCaseCount() + ","
                + "\"cross\":" + stageJson(result.cross()) + ","
                + "\"f2l\":" + stageJson(result.f2l()) + ","
                + "\"oll\":" + stageJson(result.oll()) + ","
                + "\"pll\":" + stageJson(result.pll()) + ","
                + "\"solvedF2LSlots\":\"" + escape(result.solvedF2LSlots()) + "\","
                + "\"fullySolved\":" + result.fullySolved() + ","
                + "\"totalMoveCount\":" + result.totalMoveCount() + ","
                + "\"elapsedMs\":" + String.format(java.util.Locale.US, "%.3f", result.elapsedMs())
                + "}";
    }

    static String errorJson(String message) {
        return "{\"error\":\"" + escape(message) + "\"}";
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
                + "\"f2l\":" + stageJson(solution.f2l()) + ","
                + "\"oll\":" + stageJson(solution.oll()) + ","
                + "\"pll\":" + stageJson(solution.pll()) + ","
                + "\"solvedF2LSlots\":\"" + escape(solution.solvedF2LSlots()) + "\","
                + "\"fullySolved\":" + solution.fullySolved() + ","
                + "\"totalMoveCount\":" + solution.totalMoves() + ","
                + "\"elapsedMs\":" + String.format(java.util.Locale.US, "%.3f", solution.elapsedMs()) + ","
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
        return value
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r");
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
