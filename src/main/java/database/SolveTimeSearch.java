package database;

import java.util.Locale;
import java.util.regex.Pattern;

/** A validated time-search expression compiled to the constrained SQL LIKE syntax. */
public record SolveTimeSearch(String likePattern, boolean dnfOnly, boolean plusTwoOnly) {
    private static final Pattern EXACT_SECONDS = Pattern.compile("(?:0|[1-9]|[1-5][0-9])\\.[0-9]{2}");
    private static final Pattern EXACT_MINUTES = Pattern.compile("[1-9][0-9]*:[0-5][0-9]\\.[0-9]{2}");
    private static final Pattern WILDCARD_BASE = Pattern.compile("[0-9*]+(?::[0-9*]+)?\\.[0-9*]+");
    private static final Pattern WILDCARD_COMPONENT = Pattern.compile("[0-9*]+");
    private static final Pattern CENTISECONDS = Pattern.compile("[0-9]{2}");
    private static final Pattern WILDCARD_CENTISECONDS = Pattern.compile("[0-9*]+");

    public static SolveTimeSearch parse(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("time search cannot be empty");
        }

        var expression = value.trim();
        if (expression.toUpperCase(Locale.ROOT).equals("DNF")) {
            return new SolveTimeSearch(null, true, false);
        }

        var plusTwoOnly = expression.endsWith("+");
        var timePattern = plusTwoOnly
                ? expression.substring(0, expression.length() - 1)
                : expression;
        if (timePattern.isEmpty() || timePattern.indexOf('+') >= 0) {
            throw invalidExpression();
        }

        if (timePattern.indexOf('*') >= 0) {
            validateWildcardPattern(timePattern);
        } else if (!EXACT_SECONDS.matcher(timePattern).matches()
                && !EXACT_MINUTES.matcher(timePattern).matches()) {
            throw invalidExpression();
        }

        // The parser admits only digits, separators, and '*', so SQL LIKE's
        // '%' and '_' escape syntax cannot be injected by a search expression.
        return new SolveTimeSearch(timePattern.replace('*', '%'), false, plusTwoOnly);
    }

    private static void validateWildcardPattern(String expression) {
        if (!WILDCARD_BASE.matcher(expression).matches()) {
            throw invalidExpression();
        }

        var decimal = expression.indexOf('.');
        var timePart = expression.substring(0, decimal);
        var centiseconds = expression.substring(decimal + 1);
        if (!WILDCARD_CENTISECONDS.matcher(centiseconds).matches()) {
            throw invalidExpression();
        }

        var colon = timePart.indexOf(':');
        if (colon < 0) {
            if (!WILDCARD_COMPONENT.matcher(timePart).matches()) {
                throw invalidExpression();
            }
            if (!timePart.contains("*") && !EXACT_SECONDS.matcher(timePart + ".00").matches()) {
                throw invalidExpression();
            }
        } else {
            var minutes = timePart.substring(0, colon);
            var seconds = timePart.substring(colon + 1);
            if (!WILDCARD_COMPONENT.matcher(minutes).matches()
                    || !WILDCARD_COMPONENT.matcher(seconds).matches()) {
                throw invalidExpression();
            }
            if (!minutes.contains("*") && !minutes.matches("[1-9][0-9]*")) {
                throw invalidExpression();
            }
            if (!seconds.contains("*") && !seconds.matches("[0-5][0-9]")) {
                throw invalidExpression();
            }
        }

        if (!centiseconds.contains("*") && !CENTISECONDS.matcher(centiseconds).matches()) {
            throw invalidExpression();
        }
    }

    private static IllegalArgumentException invalidExpression() {
        return new IllegalArgumentException("time must be S.CC, M:SS.CC, DNF, or a time pattern using *");
    }
}
