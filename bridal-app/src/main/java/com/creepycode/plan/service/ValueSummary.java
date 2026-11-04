package com.creepycode.plan.service;

import java.util.List;

/**
 * AI が整理した価値観の要点（VALUE_SUMMARY の中身）。
 * important は大事にしていること、removable は削ってもよい部分、removalOrder は削ってよい順。
 */
public record ValueSummary(List<Point> important, List<Point> removable, List<String> removalOrder) {

    /** 要点1つ分。item は中身、reason は理由。 */
    public record Point(String item, String reason) {
    }
}
