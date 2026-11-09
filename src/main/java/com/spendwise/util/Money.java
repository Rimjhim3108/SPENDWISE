package com.spendwise.util;

import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.Locale;

/** Money is stored as whole paise (long) to avoid floating-point rounding errors. */
public final class Money {
    public static final String SYMBOL = "\u20B9"; // Indian rupee sign

    private Money() {}

    /** Parses user input such as "250", "1,200.50" or "₹99.5" into paise. */
    public static long parse(String text) {
        if (text == null || text.isBlank()) {
            throw new IllegalArgumentException("Amount is required.");
        }
        String s = text.trim().replace(",", "").replace(SYMBOL, "").trim();
        BigDecimal value;
        try {
            value = new BigDecimal(s);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Enter a valid amount, e.g. 250 or 99.50");
        }
        if (value.scale() > 2) {
            throw new IllegalArgumentException("Amount can have at most 2 decimal places.");
        }
        try {
            return value.movePointRight(2).longValueExact();
        } catch (ArithmeticException e) {
            throw new IllegalArgumentException("Amount is too large.");
        }
    }

    public static String format(long paise) {
        DecimalFormat df = new DecimalFormat("#,##0.00", DecimalFormatSymbols.getInstance(Locale.US));
        return (paise < 0 ? "-" : "") + SYMBOL + df.format(BigDecimal.valueOf(Math.abs(paise), 2));
    }

    /** Plain decimal string for CSV export, e.g. 1250.50 */
    public static String plain(long paise) {
        return BigDecimal.valueOf(paise, 2).toPlainString();
    }

    /** Short label for chart axes: 850, 2.5k, 1.2L */
    public static String compact(long paise) {
        double r = paise / 100.0;
        if (r >= 100_000) return String.format(Locale.US, "%.1fL", r / 100_000);
        if (r >= 1_000) return String.format(Locale.US, "%.1fk", r / 1_000);
        return String.format(Locale.US, "%.0f", r);
    }
}
