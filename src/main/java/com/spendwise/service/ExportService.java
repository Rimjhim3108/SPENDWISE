package com.spendwise.service;

import com.spendwise.model.Expense;
import com.spendwise.util.Money;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

public final class ExportService {

    /** Writes a UTF-8 CSV (with BOM so Excel shows the rupee sign correctly). */
    public void exportCsv(List<Expense> rows, Path file) throws IOException {
        try (BufferedWriter w = Files.newBufferedWriter(file, StandardCharsets.UTF_8)) {
            w.write('\uFEFF');
            w.write("Date,Category,Description,Payment Method,Amount (INR)\r\n");
            for (Expense e : rows) {
                w.write(String.join(",",
                        e.date().toString(),
                        cell(e.categoryName()),
                        cell(e.description()),
                        cell(e.paymentMethod()),
                        Money.plain(e.amountPaise())));
                w.write("\r\n");
            }
        }
    }

    /** Quotes a field and neutralises spreadsheet formula injection (=, +, -, @ prefixes). */
    static String cell(String value) {
        String v = value == null ? "" : value;
        if (!v.isEmpty() && "=+-@\t\r".indexOf(v.charAt(0)) >= 0) v = "'" + v;
        return "\"" + v.replace("\"", "\"\"") + "\"";
    }
}
