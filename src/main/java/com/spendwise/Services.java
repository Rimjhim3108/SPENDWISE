package com.spendwise;

import com.spendwise.db.Database;
import com.spendwise.service.AuthService;
import com.spendwise.service.ExpenseService;
import com.spendwise.service.ExportService;

/** Simple service container shared by all UI screens. */
public record Services(Database db, AuthService auth, ExpenseService expenses, ExportService export) {
    public static Services create(Database db) {
        return new Services(db, new AuthService(db), new ExpenseService(db), new ExportService());
    }
}
