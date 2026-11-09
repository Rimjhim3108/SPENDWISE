package com.spendwise.db;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * SQLite database bootstrap. Money columns are INTEGER paise; dates are ISO-8601 text.
 *
 * ER summary:
 *   users 1---* categories 1---* expenses
 *   users 1---* expenses
 *   users 1---* budgets   (category_id = 0 means the overall monthly budget)
 */
public final class Database {
    private final String url;

    public Database(Path file) {
        try {
            Class.forName("org.sqlite.JDBC");
            Path parent = file.toAbsolutePath().getParent();
            if (parent != null) Files.createDirectories(parent);
        } catch (ClassNotFoundException | IOException e) {
            throw new DataException("Cannot initialise database: " + e.getMessage(), e);
        }
        this.url = "jdbc:sqlite:" + file.toAbsolutePath();
        createSchema();
    }

    public static Database defaultDatabase() {
        return new Database(Path.of(System.getProperty("user.home"), ".spendwise", "spendwise.db"));
    }

    public Connection connect() throws SQLException {
        Connection c = DriverManager.getConnection(url);
        try (Statement st = c.createStatement()) {
            st.execute("PRAGMA foreign_keys = ON");
            st.execute("PRAGMA busy_timeout = 5000");
        }
        return c;
    }

    private void createSchema() {
        String[] ddl = {
            "CREATE TABLE IF NOT EXISTS users ("
                + "id INTEGER PRIMARY KEY AUTOINCREMENT,"
                + "username TEXT NOT NULL UNIQUE COLLATE NOCASE,"
                + "full_name TEXT NOT NULL,"
                + "password_hash TEXT NOT NULL,"
                + "salt TEXT NOT NULL,"
                + "created_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP)",
            "CREATE TABLE IF NOT EXISTS categories ("
                + "id INTEGER PRIMARY KEY AUTOINCREMENT,"
                + "user_id INTEGER NOT NULL REFERENCES users(id) ON DELETE CASCADE,"
                + "name TEXT NOT NULL,"
                + "color TEXT NOT NULL,"
                + "UNIQUE(user_id, name COLLATE NOCASE))",
            "CREATE TABLE IF NOT EXISTS expenses ("
                + "id INTEGER PRIMARY KEY AUTOINCREMENT,"
                + "user_id INTEGER NOT NULL REFERENCES users(id) ON DELETE CASCADE,"
                + "category_id INTEGER NOT NULL REFERENCES categories(id),"
                + "amount INTEGER NOT NULL CHECK(amount > 0),"
                + "description TEXT NOT NULL DEFAULT '',"
                + "expense_date TEXT NOT NULL,"
                + "payment_method TEXT NOT NULL DEFAULT 'Cash',"
                + "created_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP)",
            "CREATE TABLE IF NOT EXISTS budgets ("
                + "id INTEGER PRIMARY KEY AUTOINCREMENT,"
                + "user_id INTEGER NOT NULL REFERENCES users(id) ON DELETE CASCADE,"
                + "category_id INTEGER NOT NULL DEFAULT 0,"
                + "month TEXT NOT NULL,"
                + "limit_amount INTEGER NOT NULL CHECK(limit_amount > 0),"
                + "UNIQUE(user_id, category_id, month))",
            "CREATE INDEX IF NOT EXISTS idx_expenses_user_date ON expenses(user_id, expense_date)",
            "CREATE INDEX IF NOT EXISTS idx_expenses_category ON expenses(category_id)"
        };
        try (Connection c = connect(); Statement st = c.createStatement()) {
            for (String sql : ddl) st.execute(sql);
        } catch (SQLException e) {
            throw new DataException("Cannot create schema: " + e.getMessage(), e);
        }
    }
}
