export type HistorySearchQuery =
    | {kind: "empty"}
    | {kind: "scramble"; value: string}
    | {kind: "time"; value: string}
    | {kind: "invalid-time"};

function isValidTimeExpression(value: string): boolean {
    if (/^dnf$/i.test(value)) return true;

    const plusCount = [...value].filter((character) => character === "+").length;
    if (plusCount > 1 || (plusCount === 1 && !value.endsWith("+"))) return false;
    const expression = value.endsWith("+") ? value.slice(0, -1) : value;
    if (!expression || !/^[0-9:.*]+$/.test(expression)) return false;

    const decimalParts = expression.split(".");
    if (decimalParts.length !== 2 || !decimalParts.every(Boolean)) return false;
    const [whole, fraction] = decimalParts;
    if (!/^[0-9*]+$/.test(fraction)) return false;
    if (!fraction.includes("*") && fraction.length !== 2) return false;

    const timeParts = whole.split(":");
    if (timeParts.length > 2 || !timeParts.every(Boolean)) return false;
    if (timeParts.length === 1) {
        const seconds = timeParts[0];
        if (!/^[0-9*]+$/.test(seconds)) return false;
        return seconds.includes("*") || /^(?:0|[1-9]|[1-5][0-9])$/.test(seconds);
    }

    const [minutes, seconds] = timeParts;
    if (!/^[0-9*]+$/.test(minutes) || !/^[0-9*]+$/.test(seconds)) return false;
    if (!minutes.includes("*") && !/^[1-9][0-9]*$/.test(minutes)) return false;
    return seconds.includes("*") || (seconds.length === 2 && Number(seconds) <= 59);
}

function looksTimeShaped(value: string): boolean {
    const containsTimeSeparator = value.includes(":") || value.includes(".");
    return value.includes("*")
        || /^[0-9:.+]+$/.test(value)
        || (containsTimeSeparator && /^[a-z0-9:.+]+$/i.test(value))
        || /^dnf\+?$/i.test(value);
}

export function parseHistorySearch(rawValue: string): HistorySearchQuery {
    const value = rawValue.trim();
    if (!value) return {kind: "empty"};
    if (isValidTimeExpression(value)) return {kind: "time", value};
    if (looksTimeShaped(value)) return {kind: "invalid-time"};
    return {kind: "scramble", value};
}
