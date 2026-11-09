package com.spendwise.model;

import java.time.YearMonth;

/** categoryId == 0 is the overall budget for the month. */
public record Budget(long id, long categoryId, String categoryName, YearMonth month, long limitPaise) {
    public boolean isOverall() { return categoryId == 0; }
}
