package com.spendwise.model;

/** topCategory and largest may be null when the range has no expenses. */
public record ReportSummary(long totalPaise, int count, int days, long averagePerDayPaise,
                            CategoryTotal topCategory, Expense largest) {}
