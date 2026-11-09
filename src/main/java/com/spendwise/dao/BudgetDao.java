package com.spendwise.dao;

import com.spendwise.db.DataException;
import com.spendwise.db.Database;
import com.spendwise.model.Budget;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;

public final class BudgetDao {
    private final Database db;

    public BudgetDao(Database db) { this.db = db; }

    /** Inserts or replaces the limit for (user, category, month). */
    public void upsert(long userId, long categoryId, YearMonth month, long limitPaise) {
        String sql = "INSERT INTO budgets(user_id, category_id, month, limit_amount) VALUES (?,?,?,?) "
                   + "ON CONFLICT(user_id, category_id, month) DO UPDATE SET limit_amount = excluded.limit_amount";
        try (Connection c = db.connect(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setLong(1, userId);
            ps.setLong(2, categoryId);
            ps.setString(3, month.toString());
            ps.setLong(4, limitPaise);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new DataException("Could not save budget: " + e.getMessage(), e);
        }
    }

    public List<Budget> list(long userId, YearMonth month) {
        String sql = "SELECT b.id, b.category_id, COALESCE(c.name, 'Overall (all categories)'), b.month, b.limit_amount "
                   + "FROM budgets b LEFT JOIN categories c ON c.id = b.category_id "
                   + "WHERE b.user_id = ? AND b.month = ? ORDER BY b.category_id";
        try (Connection c = db.connect(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setLong(1, userId);
            ps.setString(2, month.toString());
            List<Budget> out = new ArrayList<>();
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    out.add(new Budget(rs.getLong(1), rs.getLong(2), rs.getString(3),
                            YearMonth.parse(rs.getString(4)), rs.getLong(5)));
                }
            }
            return out;
        } catch (SQLException e) {
            throw new DataException("Could not load budgets: " + e.getMessage(), e);
        }
    }

    public void delete(long userId, long budgetId) {
        try (Connection c = db.connect();
             PreparedStatement ps = c.prepareStatement("DELETE FROM budgets WHERE id = ? AND user_id = ?")) {
            ps.setLong(1, budgetId);
            ps.setLong(2, userId);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new DataException("Could not delete budget: " + e.getMessage(), e);
        }
    }
}
