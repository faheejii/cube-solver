package server;

import org.slf4j.Logger;

import java.util.Map;

final class StructuredLog {
    private StructuredLog() {}

    static void info(Logger logger, String event, Map<String, ?> fields) {
        logger.info(json(event, fields));
    }

    static void error(Logger logger, String event, Throwable error, Map<String, ?> fields) {
        var enriched = new java.util.LinkedHashMap<String, Object>(fields);
        enriched.put("error", error.getClass().getSimpleName());
        enriched.put("message", error.getMessage());
        logger.error(json(event, enriched));
    }

    private static String json(String event, Map<String, ?> fields) {
        var builder = new StringBuilder("{\"event\":\"").append(escape(event)).append('"');
        fields.forEach((key, value) -> builder.append(',')
                .append('"').append(escape(key)).append("\":")
                .append(value instanceof Number || value instanceof Boolean
                        ? String.valueOf(value)
                        : '"' + escape(String.valueOf(value)) + '"'));
        return builder.append('}').toString();
    }

    private static String escape(String value) {
        return value == null ? "" : value.replace("\\", "\\\\").replace("\"", "\\\"")
                .replace("\n", "\\n").replace("\r", "\\r");
    }
}
