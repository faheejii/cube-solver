package server;

import solver.CfopSolveResult;
import solver.CfopStageResult;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class JsonSupport {
    private JsonSupport() {
    }

    static String readString(String json, String fieldName) {
        var matcher = stringFieldMatcher(json, fieldName);
        return matcher.find() ? unescape(matcher.group(1)) : null;
    }

    static boolean readBoolean(String json, String fieldName, boolean defaultValue) {
        var matcher = Pattern.compile("\"" + Pattern.quote(fieldName) + "\"\\s*:\\s*(true|false)").matcher(json);
        return matcher.find() ? Boolean.parseBoolean(matcher.group(1)) : defaultValue;
    }

    static String solveResultJson(CfopSolveResult result) {
        return "{"
                + "\"scramble\":\"" + escape(result.scramble()) + "\","
                + "\"crossFace\":\"" + escape(result.crossFace()) + "\","
                + "\"useLegacyF2L\":" + result.useLegacyF2L() + ","
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

    private static String stageJson(CfopStageResult stage) {
        return "{"
                + "\"name\":\"" + escape(stage.name()) + "\","
                + "\"algorithm\":\"" + escape(stage.algorithm()) + "\","
                + "\"moveCount\":" + stage.moveCount() + ","
                + "\"solved\":" + stage.solved() + ","
                + "\"status\":\"" + escape(stage.status()) + "\""
                + "}";
    }

    private static Matcher stringFieldMatcher(String json, String fieldName) {
        return Pattern.compile("\"" + Pattern.quote(fieldName) + "\"\\s*:\\s*\"((?:\\\\.|[^\"])*)\"").matcher(json);
    }

    private static String escape(String value) {
        return value
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r");
    }

    private static String unescape(String value) {
        return value
                .replace("\\n", "\n")
                .replace("\\r", "\r")
                .replace("\\\"", "\"")
                .replace("\\\\", "\\");
    }
}
