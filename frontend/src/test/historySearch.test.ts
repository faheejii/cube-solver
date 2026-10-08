import {describe, expect, it} from "vitest";
import {parseHistorySearch} from "../historySearch";

describe("parseHistorySearch", () => {
    it.each(["0.21", "9.00", "1:21.34", "*.21", "*:21.*", "9.*", "14.34+", "DNF", "dnf"]) (
        "recognizes valid time query %s",
        (query) => expect(parseHistorySearch(query)).toEqual({kind: "time", value: query}),
    );

    it.each(["R U R'", "F2 L2", "moves 9.00"]) (
        "keeps ordinary query %s as a scramble search",
        (query) => expect(parseHistorySearch(query)).toEqual({kind: "scramble", value: query}),
    );

    it.each(["*.2", "60.00", "09.21", "0:21.00", "01:21.00", "1:2.00", "1:2.3x", "12", "1.2+"]) (
        "rejects malformed time-shaped query %s",
        (query) => expect(parseHistorySearch(query)).toEqual({kind: "invalid-time"}),
    );

    it("trims the committed expression and recognizes an empty query", () => {
        expect(parseHistorySearch("  0.21  ")).toEqual({kind: "time", value: "0.21"});
        expect(parseHistorySearch("   ")).toEqual({kind: "empty"});
    });
});
